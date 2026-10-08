package io.github.hazymorning.klangbild.data.ha

import io.github.hazymorning.klangbild.data.Backoff
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Where and how to reach Home Assistant. */
data class HaConfig(val baseUrl: String, val token: String)

/** Connection state as the rest of the app sees it – never raw exceptions. */
sealed interface HaStatus {
    data object Stopped : HaStatus
    data class Connecting(val attempt: Int) : HaStatus
    data object Connected : HaStatus
    /** The token was rejected. No automatic retries until the config changes (or [HaConnection.retryNow]). */
    data class AuthFailed(val message: String?) : HaStatus
    /** Lost or failed; the next attempt happens in [retryInMs]. */
    data class Waiting(val attempt: Int, val retryInMs: Long, val reason: String?) : HaStatus
}

/** A Home Assistant error answer to a command. */
class HaCommandException(val code: String, message: String) : Exception(message)

/**
 * The WebSocket connection to Home Assistant: authenticates, subscribes to the given
 * entities with `subscribe_entities`, keeps the connection alive with pings and reconnects
 * with exponential backoff. State is exposed as [StateFlow]s.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HaConnection(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val pingIntervalMs: Long = 30_000,
    private val pongTimeoutMs: Long = 10_000,
    private val handshakeTimeoutMs: Long = 15_000,
    private val backoffFactory: () -> Backoff = { Backoff() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<HaStatus>(HaStatus.Stopped)
    val status: StateFlow<HaStatus> = _status.asStateFlow()

    private val _entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val entities: StateFlow<Map<String, EntityState>> = _entities.asStateFlow()

    /** Local wall-clock time minus server time, estimated from event timestamps. */
    val clockSkew = ClockSkew()

    private var loop: Job? = null
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var session: Session? = null

    fun start(config: HaConfig, entityIds: List<String>) {
        stop()
        loop = scope.launch { runLoop(config, entityIds) }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        session?.socket?.cancel()
        session = null
        _status.value = HaStatus.Stopped
        _entities.value = emptyMap()
    }

    /** Skip the remaining backoff (e.g. the network just came back). */
    fun retryNow() {
        wakeUp.trySend(Unit)
    }

    /**
     * Calls a service. With [returnResponse] the `response` object is returned.
     * Throws [HaCommandException] on HA errors, IllegalStateException when offline.
     */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        returnResponse: Boolean = false,
        data: JsonObject? = null,
        timeoutMs: Long = 10_000,
    ): JsonObject? {
        val s = session ?: error("not connected")
        val result = s.command(timeoutMs) { id ->
            buildJsonObject {
                put("id", id)
                put("type", "call_service")
                put("domain", domain)
                put("service", service)
                if (entityId != null) put("target", buildJsonObject { put("entity_id", entityId) })
                if (data != null) put("service_data", data)
                if (returnResponse) put("return_response", true)
            }
        }
        return (result as? JsonObject)?.get("response") as? JsonObject
    }

    private suspend fun runLoop(config: HaConfig, entityIds: List<String>) {
        val backoff = backoffFactory()
        while (scope.isActive) {
            _status.value = HaStatus.Connecting(backoff.attempt)
            val end = try {
                runSession(config, entityIds) { backoff.reset() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SessionEnd.Lost(e.message ?: e.javaClass.simpleName)
            } finally {
                session = null
            }
            when (end) {
                is SessionEnd.AuthInvalid -> {
                    _status.value = HaStatus.AuthFailed(end.message)
                    _entities.value = emptyMap()
                    // Wait for an explicit retry; a wrong token will not fix itself.
                    wakeUp.receive()
                }
                is SessionEnd.Lost -> {
                    val delayMs = backoff.nextDelayMs()
                    _status.value = HaStatus.Waiting(backoff.attempt, delayMs, end.reason)
                    select<Unit> {
                        wakeUp.onReceive { }
                        onTimeout(delayMs) { }
                    }
                }
            }
        }
    }

    private sealed interface SessionEnd {
        data class AuthInvalid(val message: String?) : SessionEnd
        data class Lost(val reason: String?) : SessionEnd
    }

    private sealed interface WsEvent {
        data class Text(val text: String) : WsEvent
        data class Closed(val code: Int, val reason: String) : WsEvent
        data class Failure(val error: Throwable) : WsEvent
    }

    private inner class Session(val socket: WebSocket) {
        private val nextId = AtomicInteger(1)
        val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement?>>()
        @Volatile var lastPong = 0L

        fun send(obj: JsonObject): Boolean = socket.send(obj.toString())

        suspend fun command(timeoutMs: Long, build: (Int) -> JsonObject): JsonElement? {
            val id = nextId.getAndIncrement()
            val deferred = CompletableDeferred<JsonElement?>()
            pending[id] = deferred
            try {
                if (!send(build(id))) throw IllegalStateException("socket closed")
                return withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pending.remove(id)
            }
        }

        fun nextId(): Int = nextId.getAndIncrement()
    }

    private suspend fun runSession(config: HaConfig, entityIds: List<String>, onConnected: () -> Unit): SessionEnd = coroutineScope {
        val events = Channel<WsEvent>(Channel.UNLIMITED)
        val request = Request.Builder().url(HaUrls.webSocketUrl(config.baseUrl)).build()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                events.trySend(WsEvent.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                events.trySend(WsEvent.Closed(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(WsEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(WsEvent.Failure(t))
            }
        })
        val s = Session(socket)
        try {
            // --- handshake -------------------------------------------------------------
            val first = withTimeoutOrNull(handshakeTimeoutMs) { nextMessage(events) }
                ?: return@coroutineScope SessionEnd.Lost("no auth_required")
            if (first is WsEvent.Failure || first is WsEvent.Closed) return@coroutineScope lost(first)
            val firstMsg = (first as WsEvent.Text).let { parseMessages(it.text) }.firstOrNull()
            if (firstMsg?.type() != "auth_required") return@coroutineScope SessionEnd.Lost("unexpected greeting")
            s.send(buildJsonObject {
                put("type", "auth")
                put("access_token", config.token)
            })
            val authAnswer = withTimeoutOrNull(handshakeTimeoutMs) { nextMessage(events) }
                ?: return@coroutineScope SessionEnd.Lost("auth timeout")
            if (authAnswer !is WsEvent.Text) return@coroutineScope lost(authAnswer)
            val auth = parseMessages(authAnswer.text).firstOrNull()
            when (auth?.type()) {
                "auth_ok" -> Unit
                "auth_invalid" -> return@coroutineScope SessionEnd.AuthInvalid(auth.str("message"))
                else -> return@coroutineScope SessionEnd.Lost("unexpected auth answer")
            }

            // --- subscribe ---------------------------------------------------------------
            val store = EntityStore()
            val subId = s.nextId()
            s.send(buildJsonObject {
                put("id", subId)
                put("type", "subscribe_entities")
                put("entity_ids", buildJsonArray { entityIds.forEach { add(JsonPrimitive(it)) } })
            })
            session = s
            _status.value = HaStatus.Connected
            onConnected()
            s.lastPong = System.currentTimeMillis()

            val pinger = launch {
                while (isActive) {
                    delay(pingIntervalMs)
                    val id = s.nextId()
                    val sentAt = System.currentTimeMillis()
                    s.send(buildJsonObject {
                        put("id", id)
                        put("type", "ping")
                    })
                    delay(pongTimeoutMs)
                    if (s.lastPong < sentAt) {
                        socket.cancel() // dead connection: triggers onFailure → reconnect
                        break
                    }
                }
            }

            // --- message loop ------------------------------------------------------------
            try {
                while (true) {
                    when (val ev = events.receive()) {
                        is WsEvent.Text -> for (msg in parseMessages(ev.text)) {
                            val id = (msg["id"] as? JsonPrimitive)?.intOrNull
                            when (msg.type()) {
                                "event" -> if (id == subId) {
                                    val event = msg["event"] as? JsonObject ?: continue
                                    val changed = store.apply(event)
                                    if (changed.isNotEmpty()) {
                                        recordSkew(event)
                                        _entities.value = store.snapshot()
                                    }
                                }
                                "result" -> {
                                    val ok = (msg["success"] as? JsonPrimitive)?.booleanOrNull == true
                                    val waiter = id?.let { s.pending[it] }
                                    if (waiter != null) {
                                        if (ok) {
                                            waiter.complete(msg["result"])
                                        } else {
                                            val err = msg["error"] as? JsonObject
                                            waiter.completeExceptionally(
                                                HaCommandException(err?.str("code") ?: "unknown", err?.str("message") ?: "error"),
                                            )
                                        }
                                    } else if (id == subId && !ok) {
                                        return@coroutineScope SessionEnd.Lost("subscribe failed")
                                    }
                                }
                                "pong" -> s.lastPong = System.currentTimeMillis()
                            }
                        }
                        is WsEvent.Closed, is WsEvent.Failure -> return@coroutineScope lost(ev)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                SessionEnd.Lost(null)
            } finally {
                pinger.cancel()
                s.pending.values.forEach { it.cancel() }
            }
        } finally {
            socket.cancel()
        }
    }

    private fun recordSkew(event: JsonObject) {
        val now = System.currentTimeMillis()
        // The newest server timestamp in this event (lu/lc of changed entities).
        var newest = 0.0
        fun scan(obj: JsonObject?) {
            obj ?: return
            for ((_, v) in obj) {
                val st = v as? JsonObject ?: continue
                val plus = st["+"] as? JsonObject ?: st
                listOf("lu", "lc").forEach { k ->
                    (plus[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { if (it > newest) newest = it }
                }
            }
        }
        scan(event["c"] as? JsonObject)
        if (newest > 0) clockSkew.addSample(now - (newest * 1000).toLong())
    }

    private fun lost(ev: WsEvent): SessionEnd = when (ev) {
        is WsEvent.Closed -> SessionEnd.Lost("closed ${ev.code}")
        is WsEvent.Failure -> SessionEnd.Lost(ev.error.message ?: ev.error.javaClass.simpleName)
        is WsEvent.Text -> SessionEnd.Lost(null)
    }

    private suspend fun nextMessage(events: Channel<WsEvent>): WsEvent = events.receive()

    /** HA may coalesce several messages into one JSON array frame. */
    private fun parseMessages(text: String): List<JsonObject> = try {
        when (val el = json.parseToJsonElement(text)) {
            is JsonObject -> listOf(el)
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun JsonObject.type(): String? = str("type")
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * Estimates how far the local clock is ahead of Home Assistant's. Each event carries the
 * server time of the change; receive time minus that is skew plus network latency, so the
 * smallest recent sample is the best estimate. Without it, a TV whose clock is a few
 * seconds off would show a progress bar that is always a few seconds wrong.
 */
class ClockSkew(private val window: Int = 24) {
    private val samples = ArrayDeque<Long>()

    @Synchronized
    fun addSample(localMinusServerMs: Long) {
        samples.addLast(localMinusServerMs)
        while (samples.size > window) samples.removeFirst()
    }

    /** Local minus server time in ms (0 until the first sample). */
    @get:Synchronized
    val offsetMs: Long get() = samples.minOrNull() ?: 0L
}
