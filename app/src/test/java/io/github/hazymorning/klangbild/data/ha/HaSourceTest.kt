package io.github.hazymorning.klangbild.data.ha

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.data.ma.MaClient
import io.github.hazymorning.klangbild.domain.MediaKind
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The source against a stand-in Home Assistant (and Music Assistant): what it asks, and how
 * often – quick skips, players without a queue, jumps, a book's chapters and speed.
 */
class HaSourceTest {
    private lateinit var ha: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val calls = CopyOnWriteArrayList<JsonObject>()
    private val maCommands = CopyOnWriteArrayList<String>()

    /** The player as Home Assistant first reports it, and how it answers `get_queue`. */
    private var player = """{"media_title":"Erstes Lied","media_artist":"A","app_id":"music_assistant","mass_player_type":"player"}"""
    private var queueAnswer: (() -> String)? = { """{"media_player.wz":{"queue_id":"q1","current_index":0,"current_item":{"name":"Erstes Lied","media_item":{"media_type":"track","name":"Erstes Lied"}},"next_item":null}}""" }
    private var subscription: String? = null

    @Before
    fun setUp() {
        ha = MockWebServer()
        ha.start()
        ha.enqueue(MockResponse.Builder().webSocketUpgrade(FakeHa()).build())
    }

    @After
    fun tearDown() {
        scope.cancel()
        ha.close()
    }

    private inner class FakeHa : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets += webSocket
            webSocket.send("""{"type":"auth_required","ha_version":"2026.10.0"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            val id = msg["id"]?.jsonPrimitive?.content
            when (msg["type"]?.jsonPrimitive?.content) {
                "auth" -> webSocket.send("""{"type":"auth_ok","ha_version":"2026.10.0"}""")
                "subscribe_entities" -> {
                    subscription = id
                    webSocket.send(
                        """[{"id":$id,"type":"result","success":true,"result":null},
                           {"id":$id,"type":"event","event":{"a":{"media_player.wz":{"s":"playing","a":$player,"c":"x","lc":1.0}}}}]""",
                    )
                }
                "render_template" -> webSocket.send(
                    """[{"id":$id,"type":"result","success":true,"result":null},
                       {"id":$id,"type":"event","event":{"result":3,"listeners":{"all":false,"domains":[],"entities":[],"time":false}}}]""",
                )
                "unsubscribe_events" -> webSocket.send("""{"id":$id,"type":"result","success":true,"result":null}""")
                "call_service" -> {
                    calls += msg
                    val answer = if (msg["service"]?.jsonPrimitive?.content == "get_queue") queueAnswer?.invoke() else "null"
                    if (answer == null) {
                        webSocket.send("""{"id":$id,"type":"result","success":false,"error":{"code":"home_assistant_error","message":"no queue"}}""")
                    } else {
                        webSocket.send("""{"id":$id,"type":"result","success":true,"result":{"context":{"id":"c"},"response":$answer}}""")
                    }
                }
                "ping" -> webSocket.send("""{"id":$id,"type":"pong"}""")
            }
        }
    }

    /** Home Assistant reports a new title. */
    private fun play(title: String) {
        sockets.last().send("""{"id":$subscription,"type":"event","event":{"c":{"media_player.wz":{"+":{"a":{"media_title":"$title"}}}}}}""")
    }

    private fun queueCalls() = calls.count { it["service"]?.jsonPrimitive?.content == "get_queue" }

    private fun source(ma: MaClient? = null, refreshMs: Long = 60_000L) =
        HaSource(http, scope, HaConfig(ha.url("/").toString().trimEnd('/'), "good"), "media_player.wz", ma = ma, queueRefreshMs = refreshMs)

    @Test
    fun `quick skips ask for the queue once, for where they end`() = runBlocking {
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.player?.track?.title == "Erstes Lied" } }
        for (i in 2..6) {
            play("Lied $i")
            delay(60)
        }
        withTimeout(5_000) { s.state.first { it.player?.track?.title == "Lied 6" } }
        delay(1_000)
        assertThat(queueCalls()).isEqualTo(1)
        s.stop()
    }

    @Test
    fun `a player that is not Music Assistant's is never asked for a queue`() = runBlocking {
        player = """{"media_title":"Erstes Lied","media_artist":"A"}"""
        val s = source(refreshMs = 100)
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        delay(800)
        assertThat(queueCalls()).isEqualTo(0)
        s.stop()
    }

    @Test
    fun `a player without a queue is not asked again while the track stays`() = runBlocking {
        queueAnswer = null
        val s = source(refreshMs = 100)
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        delay(1_000)
        assertThat(queueCalls()).isEqualTo(1)
        s.stop()
    }

    @Test
    fun `a jump goes to Home Assistant in whole seconds`() = runBlocking {
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        s.seek(61_700)
        withTimeout(5_000) { while (calls.none { it["service"]?.jsonPrimitive?.content == "media_seek" }) delay(20) }
        val seek = calls.first { it["service"]?.jsonPrimitive?.content == "media_seek" }
        assertThat(seek["service_data"].toString()).isEqualTo("""{"seek_position":61}""")
        assertThat(seek["target"].toString()).isEqualTo("""{"entity_id":"media_player.wz"}""")
        s.stop()
    }

    @Test
    fun `a book comes with its chapters and speed from Music Assistant`() = runBlocking {
        player = """{"media_title":"Der Process","media_artist":"Franz Kafka","media_content_id":"library://audiobook/12",
            "media_album_name":"Josef K. wird verhaftet.\n\nEin Roman.","media_duration":32400,
            "app_id":"music_assistant","mass_player_type":"player","active_queue":"q1"}"""
        queueAnswer = {
            """{"media_player.wz":{"queue_id":"q1","current_index":0,
               "current_item":{"name":"Der Process","duration":32400,"media_item":{"media_type":"audiobook","name":"Der Process","version":""}},
               "next_item":null}}"""
        }
        val ma = MockWebServer()
        ma.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val command = Json.parseToJsonElement(request.body!!.utf8()).jsonObject["command"]!!.jsonPrimitive.content
                maCommands += command
                return when (command) {
                    "player_queues/items" -> MockResponse.Builder().body(
                        """[{"queue_id":"q1","queue_item_id":"a","name":"Der Process","duration":32400,"extra_attributes":{"playback_speed":1.25},
                            "media_item":{"media_type":"audiobook","name":"Der Process","version":"","authors":["Franz Kafka"],
                              "metadata":{"chapters":[{"position":1,"name":"Verhaftung","start":0.0,"end":1834.2},
                                                      {"position":2,"name":"Gespräch mit Frau Grubach","start":1834.2,"end":null}]}}}]""",
                    ).build()
                    else -> MockResponse.Builder().body("[]").build()
                }
            }
        }
        ma.start()
        try {
            val s = source(ma = MaClient(http, ma.url("/").toString().trimEnd('/'), "ma-token"))
            s.start()
            val state = withTimeout(5_000) { s.state.first { it.player?.track?.chapters?.isNotEmpty() == true } }
            val p = state.player!!
            assertThat(p.track!!.kind).isEqualTo(MediaKind.AUDIOBOOK)
            assertThat(p.track!!.album).isNull()
            assertThat(p.track!!.artists).containsExactly("Franz Kafka")
            assertThat(p.track!!.chapters.map { it.name }).containsExactly("Verhaftung", "Gespräch mit Frau Grubach").inOrder()
            assertThat(p.speed).isWithin(1e-6f).of(1.25f)
            assertThat(maCommands).contains("player_queues/items")
            s.stop()
        } finally {
            ma.close()
        }
    }
}
