package io.github.hazymorning.klangbild.data.ma

import io.github.hazymorning.klangbild.data.fetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** How the optional link to Music Assistant stands, for the menu, never as raw errors. */
enum class MaLink {
    CONNECTED,

    /** Music Assistant rejected the token (expired after a year, or deleted). */
    AUTH_FAILED,

    /** No answer, or not the answer of a Music Assistant server. */
    UNREACHABLE,
}

/** Music Assistant rejected the token. */
class MaAuthException : IOException("Music Assistant rejected the token")

/**
 * Music Assistant's own API, for the one thing Home Assistant does not pass on: the whole
 * queue, meaning what was played before the current song and what follows it. Optional: without
 * an address and a token (from the setup page) Klangbild works from Home Assistant alone.
 *
 * One HTTP call per command (`POST <base>/api`, the long-lived token as bearer), only to the
 * address set up; the cleartext guard of the shared client keeps plain HTTP inside the home
 * network.
 */
class MaClient(private val http: OkHttpClient, val baseUrl: String, private val token: String) {

    /** The items of queue [queueId] from [offset], at most [limit], in play order. */
    suspend fun items(queueId: String, offset: Int, limit: Int): JsonArray {
        val result = command(
            "player_queues/items",
            buildJsonObject {
                put("queue_id", queueId)
                put("offset", offset)
                put("limit", limit)
            },
        )
        return result as? JsonArray ?: throw IOException("Unexpected answer to player_queues/items")
    }

    /**
     * Adds what [playerId] plays to the favorites (a station's song looked up as a song);
     * throws when Music Assistant cannot.
     */
    suspend fun favoriteNowPlaying(playerId: String) {
        command("players/add_currently_playing_to_favorites", buildJsonObject { put("player_id", playerId) })
    }

    /** Whether address and token work, as the menu and the setup page say it. */
    suspend fun check(): MaLink = try {
        if (command("player_queues/all", JsonObject(emptyMap())) is JsonArray) MaLink.CONNECTED else MaLink.UNREACHABLE
    } catch (_: MaAuthException) {
        MaLink.AUTH_FAILED
    } catch (_: Exception) {
        MaLink.UNREACHABLE
    }

    /** One command; canceling the coroutine cancels the request. */
    private suspend fun command(name: String, args: JsonObject): JsonElement {
        val body = buildJsonObject {
            put("message_id", name)
            put("command", name)
            put("args", args)
        }
        val request = Request.Builder()
            .url("$baseUrl/api")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val text = http.fetch(request, CALL_TIMEOUT_MS) { response ->
            if (response.code == 401 || response.code == 403) throw MaAuthException()
            if (!response.isSuccessful) throw IOException("Music Assistant answered ${response.code}")
            response.body.source().let { src ->
                if (!src.request(MAX_ANSWER_BYTES)) src.readUtf8() else throw IOException("Answer too large")
            }
        }
        return withContext(Dispatchers.Default) { Json.parseToJsonElement(text) }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        private val proxyLink = Regex("""^(https?://[^/?#]+)/imageproxy[/?]""")

        /** Music Assistant's address as one of its picture links gives it away (`http://host:8095/imageproxy/…`). */
        fun addressIn(pictureUrl: String?): String? = pictureUrl?.let { proxyLink.find(it)?.groupValues?.get(1) }

        /** A queue window is a few hundred kilobytes at most; anything far larger is not ours. */
        const val MAX_ANSWER_BYTES = 4L * 1024 * 1024

        /** One command gets this long, reading included. */
        const val CALL_TIMEOUT_MS = 10_000L
    }
}
