package io.github.hazymorning.klangbild.data.ma

import io.github.hazymorning.klangbild.data.ha.ArtworkUrls
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToLong

/** The part of a queue around its current item: what was played (most recent last) and what comes. */
data class QueueWindow(val played: List<TrackInfo>, val current: TrackInfo?, val coming: List<TrackInfo>)

/** Reads Music Assistant's queue items (`player_queues/items`). */
object MaQueue {
    /**
     * How many items before and after the current one are read: enough to find the next two
     * albums and the last few behind, even when every album has twenty songs.
     */
    const val REACH = 40

    /** The offset and limit that read [REACH] items around [currentIndex]. */
    fun range(currentIndex: Int): Pair<Int, Int> {
        val offset = (currentIndex - REACH).coerceAtLeast(0)
        return offset to (currentIndex - offset) + 1 + REACH
    }

    /** The window around [currentIndex] from [items] read from [offset]; [baseUrl] resolves pictures. */
    fun window(items: JsonArray, offset: Int, currentIndex: Int, baseUrl: String): QueueWindow {
        val tracks = items.map { (it as? JsonObject)?.let { o -> track(o, baseUrl) } }
        val at = currentIndex - offset
        return QueueWindow(
            played = tracks.take(at.coerceIn(0, tracks.size)).filterNotNull(),
            current = tracks.getOrNull(at),
            coming = if (at < 0) emptyList() else tracks.drop(at + 1).filterNotNull(),
        )
    }

    /** One queue item as a track: title (with its version), artists, album and year, picture. */
    fun track(item: JsonObject, baseUrl: String): TrackInfo? {
        val media = item["media_item"] as? JsonObject
        val name = media?.string("name") ?: item.string("name") ?: return null
        val version = media?.string("version")?.takeIf { it.isNotBlank() }
        val album = media?.get("album") as? JsonObject
        val isRadio = media?.string("media_type") == "radio"
        val streamTitle = (item["streamdetails"] as? JsonObject)?.string("stream_title")?.takeIf { it.isNotBlank() }
        val picture = image(item["image"] as? JsonObject, baseUrl)
            ?: image(media?.get("image") as? JsonObject, baseUrl)
            ?: image(album?.get("image") as? JsonObject, baseUrl)
        return TrackInfo(
            title = when {
                isRadio -> streamTitle ?: name
                version != null -> "$name ($version)"
                else -> name
            },
            artists = (media?.get("artists") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.string("name")?.takeIf { n -> n.isNotBlank() } }
                .orEmpty(),
            album = album?.string("name")?.takeIf { it.isNotBlank() },
            year = (album?.get("year") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
            durationMs = (item["duration"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { (it * 1000).roundToLong() },
            artworkUrl = picture?.first?.let { ArtworkUrls.upgrade(it) },
            artworkFallbackUrl = picture?.second,
            source = if (isRadio) name else null,
        )
    }

    /**
     * A picture's address and its fallback: a public one (a streaming service's CDN) as it is,
     * with Music Assistant's image proxy behind it; anything else through the proxy only.
     */
    private fun image(image: JsonObject?, baseUrl: String): Pair<String, String>? {
        val path = image?.string("path")?.takeIf { it.isNotBlank() } ?: return null
        val proxy = image.string("proxy_id")?.takeIf { it.isNotBlank() }?.let { "$baseUrl/imageproxy/$it?size=$PROXY_SIZE" }
        val remote = (image["remotely_accessible"] as? JsonPrimitive)?.booleanOrNull == true &&
            (path.startsWith("https://") || path.startsWith("http://"))
        return when {
            remote -> path to (proxy ?: path)
            proxy != null -> proxy to proxy
            else -> null
        }
    }

    /** Size asked of the proxy; [ArtworkUrls.upgrade] asks for more first. */
    private const val PROXY_SIZE = 512

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
