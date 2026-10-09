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

    /** Slow start, quick end: for things leaving. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * The cover flow turning over: a gentle start, an unhurried middle – at most about twice
     * the average speed – and a long soft landing.
     */
    val Turn = CubicBezierEasing(0.35f, 0f, 0.35f, 1f)

    /**
     * A full track change: the old artwork goes dark (0–320 ms), the new one comes up
     * (300–900 ms) and settles from 1.5 % larger; the light on the wall turns to the new
     * colours (0–900 ms). The old text leaves (200 ms); the new lines rise into place one
     * after another from 560 ms, 420 ms each.
     */
    const val TRACK_CHANGE_MS = 1_250
    const val ART_OUT_MS = 320
    const val ART_IN_START_MS = 300
    const val ART_IN_END_MS = 900
    const val GLOW_MS = 900
    const val TEXT_OUT_MS = 200
    const val TEXT_IN_MS = 560
    const val TEXT_LINE_MS = 420

    /** New track, same picture (next song of the album): only the text changes, sooner. */
    const val TEXT_CHANGE_MS = 950
    const val TEXT_IN_SAME_PICTURE_MS = 250

    /**
     * Cover Flow: another picture comes to the middle – the strip turns over in 1.3 s
     * ([Turn]); the light above the cards takes the new colour (0–1100 ms); the old text has
     * left before the cards gather speed, the new lines rise from 780 ms, as the new cover
     * turns to face the room. Cards that make way for others rolling in go first (300 ms).
     */
    const val FLOW_TURN_MS = 1_300
    const val FLOW_LIGHT_MS = 1_100
    const val FLOW_TEXT_IN_MS = 780
    const val FLOW_CLEAR_MS = 300

    /** A change that something newer is waiting behind finishes within this (ms). */
    const val HURRY_MS = 350

    /** Cards beside the middle come or go (the queue changed). */
    const val FLOW_SIDES_MS = 900

    /** At night the cards beside the middle step back into the dark. */
    const val NIGHT_SIDES_MS = 1_500

    /** Same track, better cover or new time-of-day light. */
    const val SOFT_SWAP_MS = 900

    /** Light wall to dark one at sunset (and back in the morning). */
    const val WALL_CHANGE_MS = 2_400

    /** On pause the light, the artwork and the text dim; nothing moves. */
    const val PAUSE_MS = 600

    /** A line that changes its content (status, station, notice) cross-fades. */
    const val LINE_FADE_MS = 400

    /** Light, artwork and text while paused; on the light wall the artwork dims less (it would turn muddy). */
    const val PAUSED_LIGHT = 0.45f
    const val PAUSED_COVER = 0.72f
    const val PAUSED_COVER_ON_PAPER = 0.86f
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
