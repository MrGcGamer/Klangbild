package io.github.hazymorning.klangbild.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.hazymorning.klangbild.data.ha.ArtworkUrls
import io.github.hazymorning.klangbild.data.ha.HaUrls
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.LinkedHashMap

/**
 * Fetches and decodes covers. Tries the upgraded (larger) URL first, then the original,
 * then Home Assistant's own proxy. The access token is only attached for the HA host.
 * Returns null when there is no cover at all – the scene then paints a Chladni figure.
 */
class ArtworkLoader(
    private val http: OkHttpClient,
    private val haBaseUrl: () -> String?,
    private val haToken: () -> String?,
) {
    private val cache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 4
    }

    /**
     * The cover of [track], from the first address that gives one. Cancelling stops the
     * download under way – a skipped song's cover is not fetched to the end.
     */
    suspend fun load(track: TrackInfo): Bitmap? {
        val candidates = ArtworkUrls.candidates(track.artworkUrl, track.artworkFallbackUrl)
        for (url in candidates) {
            synchronized(cache) { cache[url] }?.let { return it }
            val bytes = try {
                fetch(url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue
            val bmp = withContext(Dispatchers.Default) { decode(bytes) } ?: continue
            synchronized(cache) { cache[url] = bmp }
            return bmp
        }
        return null
    }

    /** Forgets the decoded covers (the screen is gone, or memory is short). */
    fun clear() = synchronized(cache) { cache.clear() }

    private suspend fun fetch(url: String): ByteArray? {
        val builder = Request.Builder().url(url)
        val base = haBaseUrl()
        val token = haToken()
        if (base != null && token != null && HaUrls.isHaHost(base, url)) {
            builder.header("Authorization", "Bearer $token")
        }
        return http.fetch(builder.build(), CALL_TIMEOUT_MS) { response ->
            if (!response.isSuccessful) return@fetch null
            // A cover is a few megabytes at most; anything far larger is not read at all.
            if (response.body.contentLength() > MAX_BYTES) return@fetch null
            val source = response.body.source()
            if (source.request(MAX_BYTES + 1)) return@fetch null
            source.readByteArray()
        }
    }

    companion object {
        /** Larger sources are subsampled on decode; nothing on screen needs more. */
        const val MAX_DECODE_PX = 2048

        /** The most a cover download may weigh (bytes). */
        const val MAX_BYTES = 16L * 1024 * 1024

        /** One address gets this long, reading included. */
        const val CALL_TIMEOUT_MS = 8_000L

        fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val w = bounds.outWidth
            val h = bounds.outHeight
            var sample = 1
            while (minOf(w, h) / (sample * 2) >= MAX_DECODE_PX / 2 && maxOf(w, h) / sample > MAX_DECODE_PX) sample *= 2
            // However odd its shape, no picture takes more memory than a square of MAX_DECODE_PX.
            while ((w / sample).toLong() * (h / sample) > MAX_DECODE_PX.toLong() * MAX_DECODE_PX) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
