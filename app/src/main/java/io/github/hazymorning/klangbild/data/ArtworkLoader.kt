package io.github.hazymorning.klangbild.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.art.SyntheticCovers
import io.github.hazymorning.klangbild.data.demo.DemoSource
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

    suspend fun load(track: TrackInfo): Bitmap? = withContext(Dispatchers.IO) {
        val candidates = ArtworkUrls.candidates(track.artworkUrl, track.artworkFallbackUrl)
        for (url in candidates) {
            synchronized(cache) { cache[url] }?.let { return@withContext it }
            val bmp = runCatching { fetch(url) }.getOrNull() ?: continue
            synchronized(cache) { cache[url] = bmp }
            return@withContext bmp
        }
        null
    }

    private fun fetch(url: String): Bitmap? {
        if (url.startsWith(DemoSource.SCHEME)) {
            val name = url.removePrefix(DemoSource.SCHEME)
            val cover = SyntheticCover.entries.firstOrNull { it.name == name } ?: return null
            return SyntheticCovers.render(cover)
        }
        val builder = Request.Builder().url(url)
        val base = haBaseUrl()
        val token = haToken()
        if (base != null && token != null && HaUrls.isHaHost(base, url)) {
            builder.header("Authorization", "Bearer $token")
        }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body.bytes()
            return decode(bytes)
        }
    }

    companion object {
        /** Larger sources are subsampled on decode; nothing on screen needs more. */
        const val MAX_DECODE_PX = 2048

        fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_PX / 2 &&
                maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_PX
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
