package io.github.hazymorning.klangbild.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.hazymorning.klangbild.data.ha.HaUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** What the TV shows while setup is open. */
data class SetupInfo(
    /** Address to type in, e.g. `http://192.168.1.23:8765`; null without network. */
    val url: String?,
    val code: String,
    /** QR modules (true = dark), square. Encodes [url] with the code. */
    val qr: Array<BooleanArray>?,
)

/**
 * Serves the setup page on the local network while the setup screen is visible.
 * Plain sockets (no web framework): one short request per connection.
 */
class SetupServer(
    private val scope: CoroutineScope,
    private val handler: SetupHandler,
    private val addressProvider: () -> InetAddress? = ::guessLanAddress,
) {
    private val _info = MutableStateFlow<SetupInfo?>(null)
    val info: StateFlow<SetupInfo?> = _info.asStateFlow()

    private var socket: ServerSocket? = null
    private var job: Job? = null

    val isRunning: Boolean get() = socket != null

    fun start() {
        if (socket != null) return
        handler.rotateCode()
        val server = openSocket() ?: run {
            _info.value = SetupInfo(null, handler.code, null)
            return
        }
        socket = server
        handler.onCodeChanged = { publish(server.localPort) }
        publish(server.localPort)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                launch { serve(client) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        _info.value = null
    }

    private fun publish(port: Int) {
        val host = addressProvider()?.hostAddress
        val url = host?.let { "http://$it:$port" }
        _info.value = SetupInfo(url, handler.code, url?.let { qrMatrix("$it/?code=${handler.code}") })
    }

    private fun openSocket(): ServerSocket? {
        for (port in PORTS) {
            try {
                return ServerSocket(port, 16).apply { reuseAddress = true }
            } catch (_: IOException) {
                // port taken: try the next one
            }
        }
        return runCatching { ServerSocket(0) }.getOrNull()
    }

    private suspend fun serve(client: Socket) = withContext(Dispatchers.IO) {
        client.use { s ->
            s.soTimeout = 10_000
            val response = try {
                val req = MiniHttp.read(s.getInputStream())
                handler.handle(req)
            } catch (e: MiniHttp.BadRequest) {
                MiniHttp.Response.text(400, "Ungültige Anfrage")
            } catch (e: IOException) {
                return@use
            } catch (e: Exception) {
                MiniHttp.Response.json(500, """{"ok":false,"message":"Interner Fehler"}""")
            }
            runCatching { MiniHttp.write(s.getOutputStream(), response) }
        }
    }

    companion object {
        val PORTS = listOf(8765, 8766, 8767, 8768, 8769)

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
         * Asks Home Assistant for its media players (REST `/api/states`). Translates every
         * failure into one calm German sentence for the setup page.
         */
        fun discover(http: OkHttpClient, url: String, token: String): DiscoverResult {
            val request = Request.Builder()
                .url("$url/api/states")
                .header("Authorization", "Bearer $token")
                .build()
            return try {
                http.newCall(request).execute().use { r ->
                    when {
                        r.code == 401 || r.code == 403 -> DiscoverResult.Failed("Home Assistant hat den Zugangsschlüssel abgelehnt.")
                        !r.isSuccessful -> DiscoverResult.Failed("Home Assistant antwortet nicht wie erwartet (HTTP ${r.code}).")
                        else -> parseSpeakers(r.body.string())
                    }
                }
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                if (msg.contains("only allowed in the local network")) {
                    DiscoverResult.Failed("Unverschlüsseltes http:// ist nur im Heimnetz erlaubt. Bitte https:// verwenden.")
                } else {
                    DiscoverResult.Failed("Home Assistant ist unter ${HaUrls.normalizeBase(url) ?: url} nicht erreichbar.")
                }
            } catch (e: IllegalArgumentException) {
                DiscoverResult.Failed("Die Adresse ist ungültig.")
            }
        }

        fun parseSpeakers(body: String): DiscoverResult {
            val arr = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull()
                ?: return DiscoverResult.Failed("Die Antwort von Home Assistant war unverständlich.")
            val speakers = arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = (o["entity_id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                if (!id.startsWith("media_player.")) return@mapNotNull null
                val attrs = o["attributes"] as? JsonObject
                val name = (attrs?.get("friendly_name") as? JsonPrimitive)?.content ?: id
                val state = (o["state"] as? JsonPrimitive)?.content ?: "unknown"
                val ma = attrs?.get("mass_player_type") != null ||
                    (attrs?.get("app_id") as? JsonPrimitive)?.content == "music_assistant"
                val display = (attrs?.get("device_class") as? JsonPrimitive)?.content == "tv" && ma
                if (display) null else SpeakerOption(id, name, germanState(state), ma)
            }.sortedWith(compareByDescending<SpeakerOption> { it.musicAssistant }.thenBy { it.name.lowercase() })
            return DiscoverResult.Ok(speakers)
        }

        private fun germanState(state: String) = when (state) {
            "playing" -> "spielt"
            "paused" -> "pausiert"
            "idle" -> "bereit"
            "off" -> "aus"
            "unavailable" -> "nicht erreichbar"
            "buffering" -> "lädt"
            else -> state
        }
    }
}
