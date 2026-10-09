package io.github.hazymorning.klangbild.setup

import io.github.hazymorning.klangbild.config.LaunchExtras
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SleepAction
import io.github.hazymorning.klangbild.data.ha.HaUrls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom

/** One speaker offered on the setup page. */
data class SpeakerOption(val entityId: String, val name: String, val state: String, val musicAssistant: Boolean)

sealed interface DiscoverResult {
    data class Ok(val speakers: List<SpeakerOption>) : DiscoverResult
    data class Failed(val message: String) : DiscoverResult
}

/**
 * The setup page's logic, independent of sockets: routes, the one-time code, validation.
 *
 * Security model (home network, plain HTTP):
 *  - The server only runs while the setup screen is visible on the TV.
 *  - Every API call needs the six-digit code shown on the TV (also embedded in the QR
 *    code). Wrong codes are counted; after [MAX_FAILURES] a new code is drawn.
 *  - The stored token is never sent back to the browser.
 */
class SetupHandler(
    private val settings: () -> Settings,
    private val discover: suspend (url: String, token: String) -> DiscoverResult,
    private val save: suspend (Settings) -> Unit,
    private val random: SecureRandom = SecureRandom(),
) {
    @Volatile var code: String = newCode()
        private set
    private var failures = 0

    /** Called when the code changed (the TV redraws the QR code). */
    var onCodeChanged: (String) -> Unit = {}

    private fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

    fun rotateCode() {
        code = newCode()
        failures = 0
        onCodeChanged(code)
    }

    private fun codeMatches(candidate: String?): Boolean {
        val ok = candidate != null &&
            MessageDigest.isEqual(candidate.trim().toByteArray(), code.toByteArray())
        if (!ok) {
            failures++
            if (failures >= MAX_FAILURES) rotateCode()
        } else {
            failures = 0
        }
        return ok
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(req: MiniHttp.Request): MiniHttp.Response {
        return when {
            req.method == "GET" && req.path == "/" -> MiniHttp.Response.html(SetupPage.html(prefilledCode = req.query["code"]?.takeIf { it.length == 6 && it.all(Char::isDigit) }))
            req.method == "GET" && req.path == "/favicon.ico" -> MiniHttp.Response(204, "image/x-icon", ByteArray(0))
            req.method == "POST" && req.path == "/api/state" -> withCode(req) { stateJson() }
            req.method == "POST" && req.path == "/api/discover" -> withCode(req) { body -> discoverJson(body) }
            req.method == "POST" && req.path == "/api/save" -> withCode(req) { body -> saveJson(body) }
            req.path.startsWith("/api/") -> MiniHttp.Response.json(405, error("Nicht erlaubt."))
            else -> MiniHttp.Response.text(404, "Nicht gefunden")
        }
    }

    private suspend fun withCode(req: MiniHttp.Request, block: suspend (JsonObject) -> MiniHttp.Response): MiniHttp.Response {
        val body = try {
            json.parseToJsonElement(req.bodyText.ifBlank { "{}" }).jsonObject
        } catch (_: Exception) {
            return MiniHttp.Response.json(400, error("Ungültige Anfrage."))
        }
        if (!codeMatches(body.str("code"))) {
            return MiniHttp.Response.json(403, error("Der Code stimmt nicht. Bitte den Code vom Fernseher eingeben."))
        }
        return block(body)
    }

    private fun stateJson(): MiniHttp.Response {
        val s = settings()
        return MiniHttp.Response.json(
            200,
            buildJsonObject {
                put("url", s.haUrl ?: "")
                put("hasToken", !s.haToken.isNullOrBlank())
                put("entity", s.entityId ?: "")
                put("sleepMinutes", s.sleepAfterMinutes)
                put("sleepAction", s.sleepAction.name.lowercase())
                put("clock", s.showClock)
                put("dayNight", s.dayNight)
                put("remote", s.remoteControl)
            }.toString(),
        )
    }

    private suspend fun discoverJson(body: JsonObject): MiniHttp.Response {
        val url = body.str("url")?.let { HaUrls.normalizeBase(it) }
            ?: return MiniHttp.Response.json(400, error("Bitte die Adresse von Home Assistant angeben, z. B. http://192.168.1.10:8123."))
        val token = body.str("token")?.trim()?.takeIf { it.isNotEmpty() }
            ?: settings().haToken?.takeIf { settings().haUrl == url }
            ?: return MiniHttp.Response.json(400, error("Bitte ein langlebiges Zugangstoken einfügen."))
        return when (val r = discover(url, token)) {
            is DiscoverResult.Failed -> MiniHttp.Response.json(200, buildJsonObject {
                put("ok", false)
                put("message", r.message)
            }.toString())
            is DiscoverResult.Ok -> MiniHttp.Response.json(200, buildJsonObject {
                put("ok", true)
                put("url", url)
                put("speakers", buildJsonArray {
                    r.speakers.forEach { sp ->
                        add(buildJsonObject {
                            put("id", sp.entityId)
                            put("name", sp.name)
                            put("state", sp.state)
                            put("ma", sp.musicAssistant)
                        })
                    }
                })
            }.toString())
        }
    }

    private suspend fun saveJson(body: JsonObject): MiniHttp.Response {
        val current = settings()
        val url = body.str("url")?.let { HaUrls.normalizeBase(it) }
            ?: return MiniHttp.Response.json(400, error("Die Adresse von Home Assistant fehlt oder ist ungültig."))
        val token = body.str("token")?.trim()?.takeIf { it.isNotEmpty() } ?: current.haToken
            ?: return MiniHttp.Response.json(400, error("Das Zugangstoken fehlt."))
        val entity = body.str("entity")?.trim()?.takeIf { LaunchExtras.isEntityId(it) }
            ?: return MiniHttp.Response.json(400, error("Bitte einen Lautsprecher auswählen."))
        val minutes = (body["sleepMinutes"] as? JsonPrimitive)?.intOrNull ?: body.str("sleepMinutes")?.toIntOrNull()
            ?: current.sleepAfterMinutes
        if (minutes !in 1..720) return MiniHttp.Response.json(400, error("Die Ruhezeit muss zwischen 1 und 720 Minuten liegen."))
        val action = when (body.str("sleepAction")) {
            "close" -> SleepAction.CLOSE
            "release" -> SleepAction.RELEASE
            else -> current.sleepAction
        }
        val updated = current.copy(
            haUrl = url,
            haToken = token,
            entityId = entity,
            sleepAfterMinutes = minutes,
            sleepAction = action,
            showClock = body.bool("clock") ?: current.showClock,
            dayNight = body.bool("dayNight") ?: current.dayNight,
            remoteControl = body.bool("remote") ?: current.remoteControl,
        )
        save(updated)
        return MiniHttp.Response.json(200, buildJsonObject {
            put("ok", true)
            put("message", "Gespeichert. Der Fernseher verbindet sich jetzt mit Home Assistant.")
        }.toString())
    }

    private fun error(message: String) = buildJsonObject {
        put("ok", false)
        put("message", message)
    }.toString()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    companion object {
        const val MAX_FAILURES = 8
    }
}
