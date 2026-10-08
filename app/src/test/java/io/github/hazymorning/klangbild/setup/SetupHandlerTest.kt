package io.github.hazymorning.klangbild.setup

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SleepAction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class SetupHandlerTest {
    private var stored = Settings(haUrl = "http://192.168.1.5:8123", haToken = "secret-token", entityId = "media_player.alt")
    private val discovered = mutableListOf<Pair<String, String>>()

    private val handler = SetupHandler(
        settings = { stored },
        discover = { url, token ->
            discovered += url to token
            DiscoverResult.Ok(listOf(SpeakerOption("media_player.wz", "Wohnzimmer", "spielt", true)))
        },
        save = { stored = it },
        random = SecureRandom(),
    )

    private fun post(path: String, body: String) = runBlocking {
        handler.handle(MiniHttp.Request("POST", path, emptyMap(), emptyMap(), body.toByteArray()))
    }

    private fun json(r: MiniHttp.Response): JsonObject = Json.parseToJsonElement(String(r.body)).jsonObject

    @Test
    fun `serves the page with the prefilled code`() = runBlocking {
        val r = handler.handle(MiniHttp.Request("GET", "/", mapOf("code" to "123456"), emptyMap(), ByteArray(0)))
        assertThat(r.status).isEqualTo(200)
        val html = String(r.body)
        assertThat(html).contains("value=\"123456\"")
        assertThat(html).contains("Langlebiger Zugangsschlüssel")
        val bad = handler.handle(MiniHttp.Request("GET", "/", mapOf("code" to "<script>"), emptyMap(), ByteArray(0)))
        assertThat(String(bad.body)).doesNotContain("<script>\"")
    }

    @Test
    fun `api calls need the code and never return the token`() {
        assertThat(post("/api/state", """{"code":"000000x"}""").status).isEqualTo(403)
        val r = post("/api/state", """{"code":"${handler.code}"}""")
        assertThat(r.status).isEqualTo(200)
        val body = String(r.body)
        assertThat(body).doesNotContain("secret-token")
        assertThat(json(r)["hasToken"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `too many wrong codes rotate the code`() {
        val first = handler.code
        repeat(SetupHandler.MAX_FAILURES) { post("/api/state", """{"code":"999999"}""") }
        // The chance that the new random code equals the old one is 1e-6.
        assertThat(handler.code).isNotEqualTo(first)
    }

    @Test
    fun `discover reuses the stored token for the same server`() {
        val r = post("/api/discover", """{"code":"${handler.code}","url":"192.168.1.5:8123","token":""}""")
        assertThat(json(r)["ok"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(discovered.single()).isEqualTo("http://192.168.1.5:8123" to "secret-token")
        val other = post("/api/discover", """{"code":"${handler.code}","url":"10.0.0.9:8123","token":""}""")
        assertThat(other.status).isEqualTo(400)
    }

    @Test
    fun `save validates and stores`() {
        val bad = post("/api/save", """{"code":"${handler.code}","url":"http://ha:8123","entity":"Wohnzimmer"}""")
        assertThat(bad.status).isEqualTo(400)
        val ok = post(
            "/api/save",
            """{"code":"${handler.code}","url":"ha.local:8123","token":"","entity":"media_player.wz",
               "sleepMinutes":25,"sleepAction":"close","clock":false,"dayNight":true,"remote":false}""",
        )
        assertThat(ok.status).isEqualTo(200)
        assertThat(stored.haUrl).isEqualTo("http://ha.local:8123")
        assertThat(stored.haToken).isEqualTo("secret-token")
        assertThat(stored.entityId).isEqualTo("media_player.wz")
        assertThat(stored.sleepAfterMinutes).isEqualTo(25)
        assertThat(stored.sleepAction).isEqualTo(SleepAction.CLOSE)
        assertThat(stored.showClock).isFalse()
        assertThat(stored.remoteControl).isFalse()
    }

    @Test
    fun `mini http parses requests and enforces limits`() {
        val raw = "POST /api/save?x=1&y=a%20b HTTP/1.1\r\nHost: tv\r\nContent-Length: 4\r\nContent-Type: application/json\r\n\r\n{\"a\"".toByteArray()
        val req = MiniHttp.read(ByteArrayInputStream(raw))
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo("/api/save")
        assertThat(req.query).containsExactly("x", "1", "y", "a b")
        assertThat(req.headers["content-type"]).isEqualTo("application/json")
        assertThat(req.bodyText).isEqualTo("{\"a\"")

        val tooBig = "POST / HTTP/1.1\r\nContent-Length: 999999\r\n\r\n".toByteArray()
        assertThat(runCatching { MiniHttp.read(ByteArrayInputStream(tooBig)) }.exceptionOrNull())
            .isInstanceOf(MiniHttp.BadRequest::class.java)
        val chunked = "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray()
        assertThat(runCatching { MiniHttp.read(ByteArrayInputStream(chunked)) }.exceptionOrNull())
            .isInstanceOf(MiniHttp.BadRequest::class.java)

        val out = ByteArrayOutputStream()
        MiniHttp.write(out, MiniHttp.Response.json(200, "{}"))
        val text = out.toString()
        assertThat(text).startsWith("HTTP/1.1 200 OK\r\n")
        assertThat(text).contains("Content-Length: 2\r\n")
        assertThat(text).contains("Cache-Control: no-store")
    }

    @Test
    fun `speaker list prefers music assistant and hides displays`() {
        val r = SetupServer.parseSpeakers(
            """[
              {"entity_id":"light.x","state":"on","attributes":{}},
              {"entity_id":"media_player.kueche","state":"idle","attributes":{"friendly_name":"Küche"}},
              {"entity_id":"media_player.wz","state":"playing","attributes":{"friendly_name":"Wohnzimmer","mass_player_type":"player"}},
              {"entity_id":"media_player.tv_display","state":"idle","attributes":{"friendly_name":"TV","mass_player_type":"display","device_class":"tv"}}
            ]""",
        ) as DiscoverResult.Ok
        assertThat(r.speakers.map { it.entityId }).containsExactly("media_player.wz", "media_player.kueche").inOrder()
        assertThat(r.speakers.first().state).isEqualTo("spielt")
    }

    @Test
    fun `qr code encodes the url`() {
        val m = SetupServer.qrMatrix("http://192.168.1.23:8765/?code=123456")
        assertThat(m.size).isAtLeast(21)
        assertThat(m.all { it.size == m.size }).isTrue()
        // finder pattern corner is dark
        assertThat(m[0][0]).isTrue()
    }
}
