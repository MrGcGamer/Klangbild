package io.github.hazymorning.klangbild.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Motion vocabulary. Everything is slow and eased in and out; nothing bounces.
 * Durations are long on purpose – a track change is an event, not a page swipe.
 */
object Motion {
    /** Soft in-out, like a dimmer turned by hand. */
    val Dimmer = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

    /** A sine in-out: the gentlest way for a picture to go dark or come up. */
    val Sine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

    /**
     * A full track change: the old print goes dark (700 ms), the empty wall holds for half a
     * second while the lamp changes colour, the new print comes up (900 ms); the new text
     * follows once the print is mostly there.
     */
    const val TRACK_CHANGE_MS = 2_700
    const val COVER_OUT_END_MS = 700
    const val COVER_IN_START_MS = 1_200
    const val COVER_IN_END_MS = 2_100
    const val TEXT_IN_START_MS = COVER_IN_START_MS + (COVER_IN_END_MS - COVER_IN_START_MS) * 6 / 10
    const val TEXT_IN_END_MS = TEXT_IN_START_MS + 900

    /** New track, same picture (next song of the album): only the text changes. */
    const val TEXT_CHANGE_MS = 1_800

    /** Same track, better cover or new time-of-day light. */
    const val SOFT_SWAP_MS = 1_400

    /** Light dims on pause, comes back on play. */
    const val PAUSE_MS = 2_400

    /** Switching between stage, rest and setup screens. */
    const val SCREEN_CHANGE_MS = 2_200

    /** Light level while paused. */
    const val PAUSED_LIGHT = 0.45f
    const val PAUSED_COVER = 0.78f
    const val PAUSED_TEXT = 0.85f

    /** Eased progress of a phase that runs from [start] to [end] of the overall 0..1 [t]. */
    fun phase(t: Float, start: Float, end: Float, easing: Easing = Dimmer): Float {
        val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
        return easing.transform(x)
    }

    /** [phase] with the window given in milliseconds of an animation lasting [totalMs]. */
    fun phaseMs(t: Float, startMs: Int, endMs: Int, totalMs: Int, easing: Easing = Dimmer): Float =
        phase(t, startMs.toFloat() / totalMs, endMs.toFloat() / totalMs, easing)

    /** Interpolates angles (degrees) along the shorter arc. */
    fun mixAngle(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return a + d * t
    }

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
