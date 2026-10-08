package io.github.hazymorning.klangbild.data.ha

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.data.Backoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Talks to a scripted fake Home Assistant over a real local WebSocket: handshake,
 * subscription diffs, service calls with responses, wrong token and reconnects.
 */
class HaConnectionTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val received = CopyOnWriteArrayList<JsonObject>()
    private val upgrades = AtomicInteger()

    /** Behaviour of the fake server for each connection. */
    private var acceptToken = "good"
    private var closeAfterSubscribe = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun enqueueHa(times: Int = 1) = repeat(times) {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(FakeHa()).build())
    }

    private inner class FakeHa : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            upgrades.incrementAndGet()
            sockets += webSocket
            webSocket.send("""{"type":"auth_required","ha_version":"2026.10.0"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            val id = msg["id"]?.jsonPrimitive?.content
            when (msg["type"]?.jsonPrimitive?.content) {
                "auth" -> if (msg["access_token"]?.jsonPrimitive?.content == acceptToken) {
                    webSocket.send("""{"type":"auth_ok","ha_version":"2026.10.0"}""")
                } else {
                    webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
                    webSocket.close(1000, null)
                }
                "subscribe_entities" -> {
                    // result + initial snapshot coalesced into one frame (HA may do that)
                    webSocket.send(
                        """[{"id":$id,"type":"result","success":true,"result":null},
                           {"id":$id,"type":"event","event":{"a":{"media_player.wz":{"s":"playing","a":{"media_title":"Erstes Lied","media_artist":"A"},"c":"x","lc":1.0}}}}]""",
                    )
                    if (closeAfterSubscribe) webSocket.close(1001, "restart")
                }
                "call_service" -> webSocket.send(
                    """{"id":$id,"type":"result","success":true,"result":{"context":{"id":"c"},"response":{"media_player.wz":{"next_item":{"name":"Zweites Lied"}}}}}""",
                )
                "ping" -> webSocket.send("""{"id":$id,"type":"pong"}""")
            }
        }
    }

    private fun connection() = HaConnection(
        http, scope,
        backoffFactory = { Backoff(baseMs = 20, maxMs = 100, random = Random(3)) },
    )

    private fun config(token: String = "good") = HaConfig(server.url("/").toString().trimEnd('/'), token)

    @Test
    fun `authenticates, subscribes, applies diffs and calls services`() = runBlocking {
        enqueueHa()
        val conn = connection()
        conn.start(config(), listOf("media_player.wz", "sun.sun"))
        withTimeout(5_000) { conn.status.first { it == HaStatus.Connected } }
        val first = withTimeout(5_000) { conn.entities.first { it.containsKey("media_player.wz") } }
        assertThat(first.getValue("media_player.wz").string("media_title")).isEqualTo("Erstes Lied")

        val sub = received.first { it["type"]?.jsonPrimitive?.content == "subscribe_entities" }
        assertThat(sub["entity_ids"].toString()).isEqualTo("""["media_player.wz","sun.sun"]""")

        val subId = sub["id"]!!.jsonPrimitive.content
        sockets.last().send("""{"id":$subId,"type":"event","event":{"c":{"media_player.wz":{"+":{"s":"paused","lu":5.0}}}}}""")
        val changed = withTimeout(5_000) { conn.entities.first { it["media_player.wz"]?.state == "paused" } }
        assertThat(changed.getValue("media_player.wz").string("media_title")).isEqualTo("Erstes Lied")

        val response = conn.callService("music_assistant", "get_queue", "media_player.wz", returnResponse = true)
        assertThat(response!!["media_player.wz"]!!.jsonObject["next_item"]!!.jsonObject["name"])
            .isEqualTo(JsonPrimitive("Zweites Lied"))
        val call = received.first { it["type"]?.jsonPrimitive?.content == "call_service" }
        assertThat(call["return_response"]).isEqualTo(JsonPrimitive(true))
        assertThat(call["target"].toString()).isEqualTo("""{"entity_id":"media_player.wz"}""")
        conn.stop()
    }

    @Test
    fun `a rejected token stops retrying until asked`() = runBlocking {
        enqueueHa(3)
        val conn = connection()
        conn.start(config(token = "wrong"), listOf("media_player.wz"))
        val status = withTimeout(5_000) { conn.status.first { it is HaStatus.AuthFailed } }
        assertThat((status as HaStatus.AuthFailed).message).isEqualTo("Invalid access token or password")
        delay(400)
        assertThat(upgrades.get()).isEqualTo(1)
        // After fixing the token on the server side, an explicit retry connects.
        acceptToken = "wrong"
        conn.retryNow()
        withTimeout(5_000) { conn.status.first { it == HaStatus.Connected } }
        assertThat(upgrades.get()).isEqualTo(2)
        conn.stop()
    }

    @Test
    fun `reconnects with backoff after the server goes away`() = runBlocking {
        closeAfterSubscribe = true
        enqueueHa(1)
        val conn = connection()
        conn.start(config(), listOf("media_player.wz"))
        withTimeout(5_000) { conn.status.first { it is HaStatus.Waiting } }
        // Home Assistant is back.
        closeAfterSubscribe = false
        enqueueHa(1)
        withTimeout(5_000) {
            conn.status.first { it == HaStatus.Connected && upgrades.get() == 2 }
        }
        assertThat(upgrades.get()).isEqualTo(2)
        conn.stop()
    }

    @Test
    fun `unreachable server keeps trying with growing delays`() = runBlocking {
        val port = server.port
        server.close()
        val conn = connection()
        conn.start(HaConfig("http://127.0.0.1:$port", "good"), listOf("media_player.wz"))
        val waits = mutableListOf<HaStatus.Waiting>()
        withTimeout(5_000) {
            conn.status.first { st ->
                if (st is HaStatus.Waiting && waits.none { it.attempt == st.attempt }) waits += st
                waits.size >= 3
            }
        }
        assertThat(waits.map { it.attempt }).containsExactly(1, 2, 3).inOrder()
        conn.stop()
    }
}
