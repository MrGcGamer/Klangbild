package io.github.hazymorning.klangbild.setup

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SleepAction
import io.github.hazymorning.klangbild.data.ma.MaLink
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
    private val maChecked = mutableListOf<Pair<String, String>>()
    private var maAnswer = MaLink.CONNECTED

    private val handler = SetupHandler(
        settings = { stored },
        discover = { url, token ->
            discovered += url to token
            DiscoverResult.Ok(
                listOf(SpeakerOption("media_player.wz", "Wohnzimmer", "spielt", true)),
                listOf(LightOption("light.play_links", "Play links", "an"), LightOption("light.play_rechts", "Play rechts", "aus")),
                listOf(WeatherOption("weather.forecast_home", "Forecast Home", "teils bewölkt")),
            )
        },
        save = { stored = it },
        checkMa = { url, token ->
            maChecked += url to token
            maAnswer
        },
        maGuess = { "http://192.168.1.5:8095" },
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
        assertThat(html).contains("Langlebiges Zugangstoken")
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
            """{"code":"${handler.code}","url":"192.168.1.5:8123","token":"","entity":"media_player.wz",
               "sleepMinutes":25,"sleepAction":"close","clock":false,"dayNight":true,"remote":false}""",
        )
        assertThat(ok.status).isEqualTo(200)
        assertThat(stored.haUrl).isEqualTo("http://192.168.1.5:8123")
        assertThat(stored.haToken).isEqualTo("secret-token")
        assertThat(stored.entityId).isEqualTo("media_player.wz")
        assertThat(stored.sleepAfterMinutes).isEqualTo(25)
        assertThat(stored.sleepAction).isEqualTo(SleepAction.CLOSE)
        assertThat(stored.showClock).isFalse()
        assertThat(stored.remoteControl).isFalse()
    }

    @Test
    fun `a stored token only goes to the address it was made for`() {
        val moved = post("/api/save", """{"code":"${handler.code}","url":"10.0.0.9:8123","token":"","entity":"media_player.wz"}""")
        assertThat(moved.status).isEqualTo(400)
        assertThat(stored.haUrl).isEqualTo("http://192.168.1.5:8123")
        val withToken = post("/api/save", """{"code":"${handler.code}","url":"10.0.0.9:8123","token":"new","entity":"media_player.wz"}""")
        assertThat(withToken.status).isEqualTo(200)
        assertThat(stored.haToken).isEqualTo("new")
    }

    @Test
    fun `music assistant is checked before it is stored, and leaving it empty removes it`() {
        val state = json(post("/api/state", """{"code":"${handler.code}"}"""))
        assertThat(state["maUrl"]!!.jsonPrimitive.content).isEqualTo("http://192.168.1.5:8095")
        assertThat(state["hasMaToken"]!!.jsonPrimitive.content).isEqualTo("false")

        val base = """"code":"${handler.code}","url":"192.168.1.5:8123","entity":"media_player.wz""""
        assertThat(post("/api/save", """{$base,"maUrl":"192.168.1.5:8095","maToken":""}""").status).isEqualTo(400)
        maAnswer = MaLink.AUTH_FAILED
        assertThat(post("/api/save", """{$base,"maUrl":"192.168.1.5:8095","maToken":"ma-secret"}""").status).isEqualTo(400)
        assertThat(stored.maUrl).isNull()
        maAnswer = MaLink.CONNECTED
        assertThat(post("/api/save", """{$base,"maUrl":"192.168.1.5:8095","maToken":"ma-secret"}""").status).isEqualTo(200)
        assertThat(stored.maUrl).isEqualTo("http://192.168.1.5:8095")
        assertThat(stored.maToken).isEqualTo("ma-secret")
        assertThat(maChecked.last()).isEqualTo("http://192.168.1.5:8095" to "ma-secret")
        assertThat(String(post("/api/state", """{"code":"${handler.code}"}""").body)).doesNotContain("ma-secret")

        // Saving again unchanged does not ask Music Assistant again; an empty address removes it.
        val checks = maChecked.size
        assertThat(post("/api/save", """{$base,"maUrl":"http://192.168.1.5:8095","maToken":""}""").status).isEqualTo(200)
        assertThat(maChecked).hasSize(checks)
        assertThat(post("/api/save", """{$base,"maUrl":""}""").status).isEqualTo(200)
        assertThat(stored.maUrl).isNull()
        assertThat(stored.maToken).isNull()
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
    fun `the light bars are offered, stored when the page showed them and kept when it did not`() {
        val found = json(post("/api/discover", """{"code":"${handler.code}","url":"192.168.1.5:8123","token":""}"""))
        assertThat(found["lights"].toString()).contains("light.play_links")
        val base = """"code":"${handler.code}","url":"192.168.1.5:8123","entity":"media_player.wz""""
        assertThat(post("/api/save", """{$base,"barsLeft":"light.play_links","barsCenter":"","barsRight":"light.play_rechts"}""").status).isEqualTo(200)
        assertThat(stored.bars).isEqualTo(io.github.hazymorning.klangbild.config.LightBars("light.play_links", null, "light.play_rechts"))
        val state = json(post("/api/state", """{"code":"${handler.code}"}"""))
        assertThat(state["barsRight"]!!.jsonPrimitive.content).isEqualTo("light.play_rechts")
        // Saved without the lights section (nothing found yet): the bars stay.
        assertThat(post("/api/save", """{$base}""").status).isEqualTo(200)
        assertThat(stored.bars.ids).containsExactly("light.play_links", "light.play_rechts")
        // Only lights.
        assertThat(post("/api/save", """{$base,"barsLeft":"switch.steckdose"}""").status).isEqualTo(400)
        assertThat(post("/api/save", """{$base,"barsLeft":"","barsCenter":"","barsRight":""}""").status).isEqualTo(200)
        assertThat(stored.bars.isSet).isFalse()
    }

    @Test
    fun `the Fensterblick's weather is offered and stored, automatic by default`() {
        val found = json(post("/api/discover", """{"code":"${handler.code}","url":"192.168.1.5:8123","token":""}"""))
        assertThat(found["weather"].toString()).contains("weather.forecast_home")
        val base = """"code":"${handler.code}","url":"192.168.1.5:8123","entity":"media_player.wz""""
        assertThat(post("/api/save", """{$base,"weather":"weather.forecast_home"}""").status).isEqualTo(200)
        assertThat(stored.weatherEntity).isEqualTo("weather.forecast_home")
        assertThat(json(post("/api/state", """{"code":"${handler.code}"}"""))["weather"]!!.jsonPrimitive.content).isEqualTo("weather.forecast_home")
        // Not sent: kept.
        assertThat(post("/api/save", """{$base}""").status).isEqualTo(200)
        assertThat(stored.weatherEntity).isEqualTo("weather.forecast_home")
        assertThat(post("/api/save", """{$base,"weather":"none"}""").status).isEqualTo(200)
        assertThat(stored.weatherEntities).isEmpty()
        assertThat(post("/api/save", """{$base,"weather":""}""").status).isEqualTo(200)
        assertThat(stored.weatherEntities).isEqualTo(Settings.DEFAULT_WEATHER)
        assertThat(post("/api/save", """{$base,"weather":"sensor.temperatur"}""").status).isEqualTo(400)
        val r = SetupServer.parseSpeakers("""[{"entity_id":"weather.home","state":"partlycloudy","attributes":{"friendly_name":"Zuhause"}}]""") as DiscoverResult.Ok
        assertThat(r.weather).containsExactly(WeatherOption("weather.home", "Zuhause", "teils bewölkt"))
    }

    @Test
    fun `only lights that show colours are offered for the bars`() {
        val r = SetupServer.parseSpeakers(
            """[
              {"entity_id":"light.play_links","state":"off","attributes":{"friendly_name":"Play links","supported_color_modes":["color_temp","xy"]}},
              {"entity_id":"light.flur","state":"on","attributes":{"friendly_name":"Flur","supported_color_modes":["brightness"]}},
              {"entity_id":"light.band","state":"on","attributes":{"friendly_name":"Band","supported_color_modes":["rgbww"]}},
              {"entity_id":"switch.x","state":"on","attributes":{"supported_color_modes":["xy"]}}
            ]""",
        ) as DiscoverResult.Ok
        assertThat(r.lights.map { it.entityId }).containsExactly("light.band", "light.play_links").inOrder()
        assertThat(r.lights.last().state).isEqualTo("aus")
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
