package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import kotlin.math.pow

/**
 * Everything the Halo stage draws for one track. Time-dependent values are providers so
 * that ticking them never recomposes the stage.
 */
@Immutable
class StageModel(
    val scene: ArtworkScene,
    val text: TrackText,
    val status: PlayerStatus,
    val durationMs: Long?,
    /** Smoothed playback position "now" (null without duration information). */
    val positionMs: () -> Long?,
    val timeLine: TimeLine.Line?,
    val clock: String?,
    /** Night: only cover, title and artist remain. */
    val night: Boolean = false,
) {
    fun progress(): Float? {
        val d = durationMs ?: return null
        val p = positionMs() ?: return null
        return if (d > 0) p.toFloat() / d else null
    }
}

fun Int.asColor() = Color(this)

/**
 * Colour matrix for the cover: the time-of-day lighting (perceptual dimming, only a touch
 * warmer at night – a grey gains at most 0.004 of chroma, towards hue 62°, since dim light
 * loses colour rather than gaining it) and a gentle highlight compression – even a white
 * cover never glares (white becomes a warm paper tone around L* 88 instead of #FFFFFF – a
 * full-screen-bright print would also make the TV's brightness limiter dim everything).
 */
fun coverFilter(lighting: Lighting, dim: Float = 1f): ColorFilter {
    val k = lighting.brightness.pow(1.36f) * dim
    val w = lighting.warmth
    val r = k * 0.92f * (1f + 0.0375f * w)
    val g = k * 0.905f * (1f - 0.0066f * w)
    val b = k * 0.876f * (1f - 0.045f * w)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
