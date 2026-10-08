package io.github.hazymorning.klangbild.data.ha

import io.github.hazymorning.klangbild.domain.PlayerSnapshot
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.QueueInfo
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/** Maps Home Assistant entity states and Music Assistant queue responses to domain types. */
object PlayerParser {

    fun status(state: String): PlayerStatus = when (state) {
        "playing" -> PlayerStatus.PLAYING
        "paused" -> PlayerStatus.PAUSED
        "buffering" -> PlayerStatus.BUFFERING
        "idle", "on", "standby" -> PlayerStatus.IDLE
        "off" -> PlayerStatus.OFF
        "unavailable" -> PlayerStatus.UNAVAILABLE
        else -> PlayerStatus.UNKNOWN
    }

    /** ISO-8601 → epoch millis; HA omits fractional seconds when they are zero. */
    fun parseInstant(iso: String?): Long? = try {
        iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

    fun player(entity: EntityState, baseUrl: String): PlayerSnapshot {
        val status = status(entity.state)
        val title = entity.string("media_title")?.trim().orEmpty()
        val track = if (title.isEmpty() || status == PlayerStatus.OFF || status == PlayerStatus.UNAVAILABLE) {
            null
        } else {
            val picture = HaUrls.resolve(baseUrl, entity.string("entity_picture"))
            val localPicture = HaUrls.resolve(baseUrl, entity.string("entity_picture_local"))
            val source = entity.string("media_channel") ?: entity.string("source")?.takeIf { entity.string("app_id") == "radio" }
            TrackInfo(
                title = title,
                artists = splitArtists(entity.string("media_artist")),
                album = entity.string("media_album_name")?.takeIf { it.isNotBlank() },
                durationMs = entity.number("media_duration")?.takeIf { it > 0 }?.let { (it * 1000).roundToLong() },
                artworkUrl = picture?.let { ArtworkUrls.upgrade(it) },
                artworkFallbackUrl = localPicture ?: picture,
                source = source,
            )
        }
        val isMa = entity.string("app_id") == "music_assistant" || entity.attr("mass_player_type") != null
        return PlayerSnapshot(
            status = status,
            track = track,
            positionMs = entity.number("media_position")?.let { (it * 1000).roundToLong() },
            positionAtEpochMs = parseInstant(entity.string("media_position_updated_at")),
            friendlyName = entity.string("friendly_name"),
            activeQueue = entity.string("active_queue"),
            isMusicAssistant = isMa,
        )
    }

    /** MA joins multiple artists with " / " in `media_artist`. */
    fun splitArtists(raw: String?): List<String> {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return emptyList()
        return s.split(" / ", "/ ", " /").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Parses `music_assistant.get_queue` (with `return_response`). The response is keyed by
     * entity id: `{"media_player.x": {"current_item": {...}, "next_item": {...}}}`.
     */
    fun queue(response: JsonObject, entityId: String, baseUrl: String): QueueInfo? {
        val q = (response[entityId] as? JsonObject) ?: (response.values.firstOrNull() as? JsonObject) ?: return null
        val current = q["current_item"] as? JsonObject
        val next = q["next_item"] as? JsonObject
        return QueueInfo(
            currentTitle = current?.let { (it["media_item"] as? JsonObject)?.string("name") ?: it.string("name") },
            current = current?.let { queueItem(it, baseUrl) },
            next = next?.let { queueItem(it, baseUrl) },
        )
    }

    private fun queueItem(item: JsonObject, baseUrl: String): TrackInfo? {
        val media = item["media_item"] as? JsonObject
        val name = media?.string("name") ?: item.string("name") ?: return null
        val version = media?.string("version")?.takeIf { it.isNotBlank() }
        val artists = (media?.get("artists") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("name") }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val album = media?.get("album") as? JsonObject
        val image = media?.string("image") ?: album?.string("image")
        val resolved = HaUrls.resolve(baseUrl, image)
        val year = (album?.get("year") as? JsonPrimitive)?.intOrNull
        val isRadio = media?.string("media_type") == "radio"
        // Radio: the station is the source, the stream title (if any) is what plays now.
        val streamTitle = item.string("stream_title")?.takeIf { it.isNotBlank() }
        val title = when {
            isRadio -> streamTitle ?: name
            version != null -> "$name ($version)"
            else -> name
        }
        return TrackInfo(
            title = title,
            artists = artists,
            album = album?.string("name")?.takeIf { it.isNotBlank() },
            year = year,
            durationMs = (item["duration"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { (it * 1000).roundToLong() },
            artworkUrl = resolved?.let { ArtworkUrls.upgrade(it) },
            artworkFallbackUrl = resolved,
            source = if (isRadio) name else null,
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
