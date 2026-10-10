package io.github.hazymorning.klangbild.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.hazymorning.klangbild.R
import io.github.hazymorning.klangbild.data.ha.HaAuth
import io.github.hazymorning.klangbild.data.ha.HaAuthRejectedException
import io.github.hazymorning.klangbild.data.ha.HaCredentials
import io.github.hazymorning.klangbild.data.ha.HaRest
import io.github.hazymorning.klangbild.data.ha.LocalNetwork
import io.github.hazymorning.klangbild.text.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections

/** What the TV shows while setup is open. */
data class SetupInfo(
    /** Address to type in, e.g. `http://192.168.1.23:8765`; null without network. */
    val url: String?,
    val code: String,
    /** QR modules (true = dark), square. Encodes [url] with the code. */
    val qr: Array<BooleanArray>?,
)

/**
 * Serves the setup page on the local network while the setup screen is visible. Plain sockets
 * (no web framework): one short request per connection, at most [MAX_CONNECTIONS] at a time,
 * each with [REQUEST_DEADLINE_MS] to arrive. Requests must name the page's own address as their
 * host (no other site can rebind its name to the TV), a call to the API from another origin is
 * refused, and so is one that is not JSON (a form on another page cannot send it unasked).
 */
class SetupServer(
    private val scope: CoroutineScope,
    private val handler: SetupHandler,
    private val addressProvider: () -> InetAddress? = ::guessLanAddress,
) {
    private val _info = MutableStateFlow<SetupInfo?>(null)
    val info: StateFlow<SetupInfo?> = _info.asStateFlow()

    /** The page's address while it is served (`http://192.168.1.23:8765`). */
    val origin: String? get() = _info.value?.url

    private var socket: ServerSocket? = null
    private var job: Job? = null
    private val clients = Collections.synchronizedSet(HashSet<Socket>())

    @Synchronized
    fun start() {
        if (socket != null) return
        handler.rotateCode()
        val address = addressProvider()
        val server = openSocket(address) ?: run {
            _info.value = SetupInfo(null, handler.code, null)
            return
        }
        socket = server
        handler.onCodeChanged = { publish(address, server.localPort) }
        publish(address, server.localPort)
        val slots = Semaphore(MAX_CONNECTIONS)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                // Too many at once: the newest waits for nothing.
                if (!slots.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                launch {
                    try {
                        serve(client)
                    } finally {
                        slots.release()
                    }
                }
            }
        }
    }

    /** Stops serving: the listening socket and every open connection close, and a sign-in never saved is revoked. */
    @Synchronized
    fun stop() {
        val wasServing = socket != null
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
        _info.value = null
        if (wasServing) scope.launch { handler.reset() }
    }

    private fun publish(address: InetAddress?, port: Int) {
        val url = address?.hostAddress?.let { "http://$it:$port" }
        _info.value = SetupInfo(url, handler.code, url?.let { qrMatrix("$it/?code=${handler.code}") })
    }

    /** The listening socket, on the LAN address only (or every address when it is not known). */
    private fun openSocket(address: InetAddress?): ServerSocket? {
        for (port in PORTS) {
            try {
                return ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(address, port), BACKLOG)
                }
            } catch (_: IOException) {
                // Port taken: try the next one.
            }
        }
        return runCatching { ServerSocket(0, BACKLOG, address) }.getOrNull()
    }

    private suspend fun serve(client: Socket) {
        clients += client
        // However slowly a request arrives, it has this long; then its socket closes under it.
        val deadline = scope.launch {
            delay(REQUEST_DEADLINE_MS)
            runCatching { client.close() }
        }
        try {
            client.use { s ->
                s.soTimeout = READ_TIMEOUT_MS
                val texts = handler.texts()
                val response = try {
                    val req = MiniHttp.read(s.getInputStream())
                    refusal(req, texts) ?: handler.handle(req)
                } catch (_: MiniHttp.BadRequest) {
                    MiniHttp.Response.text(400, texts[R.string.setup_error_request])
                } catch (_: IOException) {
                    return
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    MiniHttp.Response.json(500, """{"ok":false}""")
                }
                runCatching { MiniHttp.write(s.getOutputStream(), response) }
            }
        } finally {
            deadline.cancel()
            clients -= client
        }
    }

    /**
     * Why [req] is refused, or null: it must be for the page's own address; a call to the API
     * must come from the page itself and be JSON.
     */
    private fun refusal(req: MiniHttp.Request, t: Texts): MiniHttp.Response? {
        val own = origin ?: return null
        val forbidden = MiniHttp.Response.text(403, t[R.string.setup_error_not_allowed])
        if (req.headers["host"] != own.removePrefix("http://")) return forbidden
        if (req.method == "POST") {
            val from = req.headers["origin"]
            if (from != null && from != own) return forbidden
            if (req.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/json") return forbidden
        }
        return null
    }

    companion object {
        val PORTS = listOf(8765, 8766, 8767, 8768, 8769)

        /** Connections served at once; a phone filling in one form needs one or two. */
        const val MAX_CONNECTIONS = 4

        /** Connections waiting to be accepted. */
        private const val BACKLOG = 8

        /** One read may wait this long ... */
        const val READ_TIMEOUT_MS = 5_000

        /** ... and the whole request this long. */
        const val REQUEST_DEADLINE_MS = 15_000L

        fun qrMatrix(text: String): Array<BooleanArray> {
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
        }

        /** First private IPv4 address of an up, non-loopback interface (Ethernet or Wi-Fi). */
        fun guessLanAddress(): InetAddress? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()

        /**
         * Asks Home Assistant at [url] for its media players, lights and weather entities (REST
         * `/api/states`) with [credentials]. Every failure becomes one calm sentence for the
         * setup page.
         */
        suspend fun discover(http: OkHttpClient, url: String, credentials: HaCredentials, t: Texts): DiscoverResult {
            val rest = HaRest(http, HaAuth(http, url, credentials))
            return try {
                parseSpeakers(rest.allStates(), t)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: HaAuthRejectedException) {
                DiscoverResult.Failed(t[R.string.discover_rejected])
            } catch (e: IOException) {
                val code = Regex("""answered (\d{3})""").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                when {
                    code != null -> DiscoverResult.Failed(t.format(R.string.discover_unexpected, code))
                    e is LocalNetwork.CleartextRefused -> DiscoverResult.Failed(t[R.string.discover_cleartext])
                    else -> DiscoverResult.Failed(t.format(R.string.discover_unreachable, url))
                }
            } catch (_: IllegalArgumentException) {
                DiscoverResult.Failed(t[R.string.discover_invalid_address])
            }
        }

        /** The lists for the setup page from all states as JSON text. */
        fun parseSpeakers(body: String, t: Texts): DiscoverResult {
            val arr = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull()
                ?: return DiscoverResult.Failed(t[R.string.discover_garbled])
            val states = arr.mapNotNull { it as? JsonObject }
            fun JsonObject.id() = (this["entity_id"] as? JsonPrimitive)?.content
            fun JsonObject.attrs() = this["attributes"] as? JsonObject
            fun JsonObject.name() = (attrs()?.get("friendly_name") as? JsonPrimitive)?.content ?: id()
            fun JsonObject.state() = (this["state"] as? JsonPrimitive)?.content ?: "unknown"
            val speakers = states.mapNotNull { o ->
                val id = o.id()?.takeIf { it.startsWith("media_player.") } ?: return@mapNotNull null
                val attrs = o.attrs()
                val ma = attrs?.get("mass_player_type") != null ||
                    (attrs?.get("app_id") as? JsonPrimitive)?.content == "music_assistant"
                // A screen of Music Assistant's own (a TV it plays to) is no speaker for this one.
                val display = (attrs?.get("device_class") as? JsonPrimitive)?.content == "tv" && ma
                if (display) null else SpeakerOption(id, o.name() ?: id, stateWord(o.state(), t), ma)
            }.sortedWith(compareByDescending<SpeakerOption> { it.musicAssistant }.thenBy { it.name.lowercase() })
            // Every light Home Assistant controls can join in: in color, in white, or by its brightness.
            val lights = states.mapNotNull { o ->
                val id = o.id()?.takeIf { it.startsWith("light.") } ?: return@mapNotNull null
                LightOption(id, o.name() ?: id, stateWord(o.state(), t))
            }.sortedBy { it.name.lowercase() }
            // Weather entities: their clouds set the window view's sky.
            val weather = states.mapNotNull { o ->
                val id = o.id()?.takeIf { it.startsWith("weather.") } ?: return@mapNotNull null
                WeatherOption(id, o.name() ?: id, weatherWord(o.state(), t))
            }.sortedBy { it.name.lowercase() }
            return DiscoverResult.Ok(speakers, lights, weather)
        }

        /** A weather condition (https://developers.home-assistant.io/docs/core/entity/weather) in words. */
        private fun weatherWord(state: String, t: Texts) = when (state) {
            "sunny" -> t[R.string.weather_sunny]
            "clear-night" -> t[R.string.weather_clear_night]
            "partlycloudy" -> t[R.string.weather_partlycloudy]
            "cloudy" -> t[R.string.weather_cloudy]
            "fog" -> t[R.string.weather_fog]
            "rainy", "pouring" -> t[R.string.weather_rain]
            "snowy", "snowy-rainy" -> t[R.string.weather_snow]
            "hail" -> t[R.string.weather_hail]
            "lightning", "lightning-rainy" -> t[R.string.weather_thunderstorm]
            "windy", "windy-variant" -> t[R.string.weather_windy]
            "exceptional" -> t[R.string.weather_exceptional]
            else -> stateWord(state, t)
        }

        private fun stateWord(state: String, t: Texts) = when (state) {
            "playing" -> t[R.string.entity_state_playing]
            "paused" -> t[R.string.entity_state_paused]
            "idle" -> t[R.string.entity_state_idle]
            "off" -> t[R.string.entity_state_off]
            "on" -> t[R.string.entity_state_on]
            "unavailable" -> t[R.string.entity_state_unavailable]
            "buffering" -> t[R.string.entity_state_buffering]
            "standby" -> t[R.string.entity_state_standby]
            else -> t[R.string.entity_state_unknown]
        }
    }
}
