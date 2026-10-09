package io.github.hazymorning.klangbild.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * True while rendering a still (screenshots): nothing runs a loop of its own, so the
 * composition can come to rest.
 */
val LocalStill = staticCompositionLocalOf { false }

/**
 * Motion vocabulary. Everything is driven by the display's frames and eased; nothing
 * bounces, nothing slides, nothing jumps. A track change takes about a second – long enough
 * to be seen, short enough to never be waited for.
 */
object Motion {
    /** Soft in-out, like a dimmer turned by hand. */
    val Dimmer = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

    /** A sine in-out: the gentlest way for a picture to come up. */
    val Sine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

    /** Quick start, long settle: for things arriving at their place. */
    val Decelerate = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /**
     * A full track change: the new artwork fades in over the old one (600 ms) and settles
     * from 2 % larger over the whole change while its light morphs on the wall (900 ms); the
     * old text leaves (250 ms), the new lines rise into place one after another from 300 ms.
     */
    const val TRACK_CHANGE_MS = 1_100
    const val ART_IN_MS = 600
    const val GLOW_MS = 900
    const val TEXT_OUT_MS = 250
    const val TEXT_IN_MS = 300

    /** New track, same picture (next song of the album): only the text changes. */
    const val TEXT_CHANGE_MS = 1_100

    /** Same track, better cover or new time-of-day light. */
    const val SOFT_SWAP_MS = 900

    /** On pause the artwork recedes a little and dims with its light and the text. */
    const val PAUSE_MS = 600

    /** A line that changes its content (status, station, notice) cross-fades. */
    const val LINE_FADE_MS = 400

    /** Switching between stage, rest and setup screens. */
    const val SCREEN_CHANGE_MS = 1_600

    /** Light, artwork, its size and the text while paused. */
    const val PAUSED_LIGHT = 0.45f
    const val PAUSED_COVER = 0.72f
    const val PAUSED_SCALE = 0.965f
    const val PAUSED_TEXT = 0.7f

    /** Eased progress of a phase that runs from [start] to [end] of the overall 0..1 [t]. */
    fun phase(t: Float, start: Float, end: Float, easing: Easing = Dimmer): Float {
        val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
        return easing.transform(x)
    }

    /** [phase] with the window given in milliseconds of an animation lasting [totalMs]. */
    fun phaseMs(t: Float, startMs: Int, endMs: Int, totalMs: Int, easing: Easing = Dimmer): Float =
        phase(t, startMs.toFloat() / totalMs, endMs.toFloat() / totalMs, easing)

    /**
     * OLED care: the whole composition drifts on a slow Lissajous path (±[amplitudePx]),
     * moving at most about one pixel every few seconds – nobody notices, the panel does.
     * Whole pixels only, so hairlines never shimmer.
     */
    fun drift(wallClockMs: Long, amplitudePx: Float): Pair<Int, Int> {
        val t = wallClockMs / 1000.0
        val x = sin(2 * PI * t / (37 * 60)) * amplitudePx
        val y = sin(2 * PI * t / (29 * 60) + 1.3) * amplitudePx * 0.8
        return x.roundToInt() to y.roundToInt()
    }
}
