package io.github.hazymorning.klangbild.ui

import androidx.compose.animation.core.CubicBezierEasing
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

    /** A full track change (light, cover, text). */
    const val TRACK_CHANGE_MS = 3_600

    /** Same track, better cover or new time-of-day light. */
    const val SOFT_SWAP_MS = 1_400

    /** Light dims on pause, comes back on play. */
    const val PAUSE_MS = 2_400

    /** Switching between stage, rest and setup screens. */
    const val SCREEN_CHANGE_MS = 2_200

    /** Light level while paused. */
    const val PAUSED_LIGHT = 0.45f
    const val PAUSED_COVER = 0.78f

    /** Eased progress of a phase that runs from [start] to [end] of the overall 0..1 [t]. */
    fun phase(t: Float, start: Float, end: Float): Float {
        val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
        return Dimmer.transform(x)
    }

    /**
     * OLED care: the whole composition drifts on a slow Lissajous path (±[amplitudePx]),
     * moving at most about one pixel every few seconds – nobody notices, the panel does.
     */
    fun drift(wallClockMs: Long, amplitudePx: Float): Pair<Int, Int> {
        val t = wallClockMs / 1000.0
        val x = sin(2 * PI * t / (37 * 60)) * amplitudePx
        val y = sin(2 * PI * t / (29 * 60) + 1.3) * amplitudePx * 0.8
        return x.roundToInt() to y.roundToInt()
    }
}
