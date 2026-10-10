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
import kotlinx.serialization.json.JsonPrimitive
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
 * often. Quick skips, players without a queue, jumps, a book's chapters and speed.
 */
class HaSourceTest {
    private lateinit var ha: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val calls = CopyOnWriteArrayList<JsonObject>()
    private val maCommands = CopyOnWriteArrayList<String>()

    /** The player's attributes as Home Assistant first reports them, and how it answers `get_queue`. */
    private var player = """{"media_title":"Erstes Lied","media_artist":"A","app_id":"music_assistant","mass_player_type":"player"}"""
    private var queueAnswer: (() -> String)? = { """{"media_player.wz":{"queue_id":"q1","current_index":0,"current_item":{"name":"Erstes Lied","media_item":{"media_type":"track","name":"Erstes Lied"}},"next_item":null}}""" }
    private var subscription: String? = null
    private var changes = 0

    /** The entity registry as listed for display. */
    private var registry = """[{"ei":"media_player.wz","pl":"music_assistant","di":"d1"}]"""

    @Before
    fun setUp() {
        ha = MockWebServer()
        ha.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path == "/api/websocket" -> MockResponse.Builder().webSocketUpgrade(FakeHa()).build()
                    path == "/api/states/media_player.wz" -> MockResponse.Builder().body(playerState(Json.parseToJsonElement(player).jsonObject)).build()
                    else -> MockResponse.Builder().code(404).body("""{"message":"Entity not found."}""").build()
                }
            }
        }
        ha.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        ha.close()
    }

    @Synchronized
    private fun playerState(attributes: JsonObject): String {
        val time = "2026-10-10T18:00:%02d+00:00".format(changes++ % 60)
        return """{"entity_id":"media_player.wz","state":"playing","attributes":$attributes,
            "last_changed":"$time","last_updated":"$time","context":{"id":"x","parent_id":null,"user_id":null}}"""
    }

    private inner class FakeHa : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets += webSocket
            webSocket.send("""{"type":"auth_required","ha_version":"2026.10.0"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            val id = msg["id"]?.jsonPrimitive?.content
            fun ok(result: String = "null") = webSocket.send("""{"id":$id,"type":"result","success":true,"result":$result}""")
            when (msg["type"]?.jsonPrimitive?.content) {
                "auth" -> webSocket.send("""{"type":"auth_ok","ha_version":"2026.10.0"}""")
                "subscribe_trigger" -> {
                    if (subscription == null) subscription = id
                    ok()
                }
                "config/entity_registry/list_for_display" -> ok("""{"entity_categories":{},"entities":$registry}""")
                "get_config" -> ok("""{"latitude":52.52,"longitude":13.4}""")
                "unsubscribe_events" -> ok()
                "call_service" -> {
                    calls += msg
                    val answer = if (msg["service"]?.jsonPrimitive?.content == "get_queue") queueAnswer?.invoke() else "null"
                    if (answer == null) {
                        webSocket.send("""{"id":$id,"type":"result","success":false,"error":{"code":"home_assistant_error","message":"no queue"}}""")
                    } else {
                        ok("""{"context":{"id":"c"},"response":$answer}""")
                    }
                }
                "ping" -> webSocket.send("""{"id":$id,"type":"pong"}""")
            }
        }
    }

    /** Home Assistant reports a new title. */
    private fun play(title: String) {
        val attributes = JsonObject(Json.parseToJsonElement(player).jsonObject + ("media_title" to JsonPrimitive(title)))
        sockets.last().send(
            """{"id":$subscription,"type":"event","event":{"variables":{"trigger":{"platform":"state","entity_id":"media_player.wz",
                "from_state":null,"to_state":${playerState(attributes)}}},"context":null}}""",
        )
    }

    private fun queueCalls() = calls.count { it["service"]?.jsonPrimitive?.content == "get_queue" }

    private fun source(ma: MaClient? = null, refreshMs: Long = 60_000L) = HaSource(
        http,
        scope,
        HaConfig(HaAuth(http, ha.url("/").toString().trimEnd('/'), HaCredentials.LongLived("good"))),
        "media_player.wz",
        ma = ma,
        queueRefreshMs = refreshMs,
    )

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
    fun `a jump goes to Home Assistant in seconds`() = runBlocking {
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        s.seek(61_700)
        withTimeout(5_000) { while (calls.none { it["service"]?.jsonPrimitive?.content == "media_seek" }) delay(20) }
        val seek = calls.first { it["service"]?.jsonPrimitive?.content == "media_seek" }
        assertThat(seek["service_data"].toString()).isEqualTo("""{"seek_position":61.7}""")
        assertThat(seek["target"].toString()).isEqualTo("""{"entity_id":"media_player.wz"}""")
        s.stop()
    }

    @Test
    fun `only what the player supports is sent`() = runBlocking {
        // Play, pause and next only (supported_features 1 + 32 + 16384): no seek, no shuffle, no volume.
        player = """{"media_title":"Erstes Lied","media_artist":"A","supported_features":16417}"""
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        s.seek(10_000)
        assertThat(s.shuffle(true)).isFalse()
        s.send(io.github.hazymorning.klangbild.data.RemoteCommand.VOLUME_UP)
        s.send(io.github.hazymorning.klangbild.data.RemoteCommand.PREVIOUS)
        s.send(io.github.hazymorning.klangbild.data.RemoteCommand.PLAY_PAUSE)
        s.send(io.github.hazymorning.klangbild.data.RemoteCommand.NEXT)
        withTimeout(5_000) { while (calls.size < 2) delay(20) }
        delay(200)
        assertThat(calls.map { it["service"]!!.jsonPrimitive.content }).containsExactly("media_play_pause", "media_next_track").inOrder()
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
            val track = p.track!!
            assertThat(track.kind).isEqualTo(MediaKind.AUDIOBOOK)
            assertThat(track.album).isNull()
            assertThat(track.artists).containsExactly("Franz Kafka")
            assertThat(track.chapters.map { it.name }).containsExactly("Verhaftung", "Gespräch mit Frau Grubach").inOrder()
            assertThat(p.speed).isWithin(1e-6f).of(1.25f)
            assertThat(maCommands).contains("player_queues/items")
            s.stop()
        } finally {
            ma.close()
        }
    }

    @Test
    fun `a station keeps the song's picture from Home Assistant and names itself in the bar's place`() = runBlocking {
        player = """{"media_title":"Cold Little Heart","media_artist":"Michael Kiwanuka","media_content_id":"library://radio/7",
            "media_album_name":"Radio Paradise","entity_picture":"/api/media_player_proxy/media_player.wz?token=t&cache=song",
            "app_id":"music_assistant","mass_player_type":"player","active_queue":"q1"}"""
        queueAnswer = {
            """{"media_player.wz":{"queue_id":"q1","current_index":0,
               "current_item":{"name":"Radio Paradise","stream_title":"Michael Kiwanuka - Cold Little Heart",
                 "media_item":{"media_type":"radio","name":"Radio Paradise","image":"https://cdn.example/station.png"}},
               "next_item":{"name":"SWR2","media_item":{"media_type":"radio","name":"SWR2","image":"https://cdn.example/swr2.png"}}}}"""
        }
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.queueKnown } }
        val t = s.state.value.player!!.track!!
        assertThat(t.kind).isEqualTo(MediaKind.RADIO)
        assertThat(t.source).isEqualTo("Radio Paradise")
        assertThat(t.album).isNull()
        // The song's picture as Home Assistant has it, never the station's logo instead.
        assertThat(t.artworkUrl).endsWith("cache=song")
        s.stop()
    }

    @Test
    fun `a station's song picture from Music Assistant's own queue goes first`() = runBlocking {
        player = """{"media_title":"Cold Little Heart","media_artist":"Michael Kiwanuka","media_content_id":"library://radio/7",
            "media_album_name":"Radio Paradise","entity_picture":"/api/media_player_proxy/media_player.wz?token=t&cache=station",
            "app_id":"music_assistant","mass_player_type":"player","active_queue":"q1"}"""
        queueAnswer = {
            """{"media_player.wz":{"queue_id":"q1","current_index":0,
               "current_item":{"name":"Radio Paradise","stream_title":"Michael Kiwanuka - Cold Little Heart",
                 "media_item":{"media_type":"radio","name":"Radio Paradise","image":"https://cdn.example/station.png"}},
               "next_item":null}}"""
        }
        val ma = MockWebServer()
        ma.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val command = Json.parseToJsonElement(request.body!!.utf8()).jsonObject["command"]!!.jsonPrimitive.content
                maCommands += command
                return when (command) {
                    "player_queues/items" -> MockResponse.Builder().body(
                        """[{"queue_id":"q1","queue_item_id":"a","name":"Radio Paradise",
                            "streamdetails":{"stream_title":"Michael Kiwanuka - Cold Little Heart",
                              "stream_metadata":{"title":"Cold Little Heart","artist":"Michael Kiwanuka","album":"Love & Hate",
                                "image_url":"https://img.example/love-and-hate.jpg"}},
                            "media_item":{"media_type":"radio","name":"Radio Paradise",
                              "image":{"path":"https://cdn.example/station.png","provider":"radiobrowser","remotely_accessible":true}}}]""",
                    ).build()
                    else -> MockResponse.Builder().body("[]").build()
                }
            }
        }
        ma.start()
        try {
            val s = source(ma = MaClient(http, ma.url("/").toString().trimEnd('/'), "ma-token"))
            s.start()
            val t = withTimeout(5_000) { s.state.first { it.player?.track?.artworkUrl?.contains("love-and-hate") == true } }.player!!.track!!
            assertThat(t.album).isEqualTo("Love & Hate")
            assertThat(t.source).isEqualTo("Radio Paradise")
            s.stop()
        } finally {
            ma.close()
        }
    }

    @Test
    fun `the favorite goes through the speaker's own button`() = runBlocking {
        // Found by its translation key on the speaker's device, whatever its entity id.
        registry = """[{"ei":"media_player.wz","pl":"music_assistant","di":"d1"},
            {"ei":"button.wz_favorite_current_song","pl":"music_assistant","di":"d1","tk":"favorite_now_playing"},
            {"ei":"button.kueche_favorite_current_song","pl":"music_assistant","di":"d2","tk":"favorite_now_playing"}]"""
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.favorites } }
        assertThat(s.favorite()).isTrue()
        val press = calls.last { it["domain"]?.jsonPrimitive?.content == "button" }
        assertThat(press["service"]!!.jsonPrimitive.content).isEqualTo("press")
        assertThat(press["target"]!!.jsonObject["entity_id"]!!.jsonPrimitive.content).isEqualTo("button.wz_favorite_current_song")
        s.stop()
    }

    @Test
    fun `without a button and without Music Assistant there are no favorites`() = runBlocking {
        val s = source()
        s.start()
        withTimeout(5_000) { s.state.first { it.player != null } }
        delay(300)
        assertThat(s.state.value.favorites).isFalse()
        assertThat(s.favorite()).isFalse()
        s.stop()
    }
}
