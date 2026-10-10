package io.github.hazymorning.klangbild.vista

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the camera stands at one moment: how far it has moved in ([zoom], for the nearest
 * layer) and to the side ([panX], [panY], dp, for the nearest layer), about the screen's middle.
 * Each layer of the diorama takes its share by its depth ([Depth.parallax]): the near banks move
 * most, the sky hardly at all – so the window has depth even when nothing in it moves.
 */
class CameraPose(val zoom: Float, val panX: Float, val panY: Float) {
    fun scale(d: Depth): Float = 1f + (zoom - 1f) * d.parallax
    fun dx(d: Depth): Float = panX * d.parallax
    fun dy(d: Depth): Float = panY * d.parallax
}

/**
 * The camera's slow journey over the diorama, like a hand-held look through a window: it moves
 * in and out (up to [ZOOM_MAX]) and its view wanders across the picture – as far as the paper
 * reaches beyond the screen at that zoom ([PaperMotif.OVERSCAN], with the screen's own drift on
 * top), and never so far that the music's label on the near bank comes closer to the screen's
 * edges than [LABEL_MARGIN]. A swing in and out takes about two minutes, one across about a
 * minute and a half; the waves are sums of sines with incommensurate periods, so the journey
 * never repeats. The nearer a layer, the more it moves: the parallax is plain to see.
 */
object VistaCamera {
    /** The nearest layer's enlargement, from … to … */
    const val ZOOM_MIN = 1.01f
    const val ZOOM_MAX = 1.11f

    /** The view's middle wanders at most this far up and down (dp): the picture is wide, not tall. */
    const val TILT = 16f

    /** The label never comes closer than this to the screen's sides and bottom (dp). */
    const val LABEL_MARGIN = 28f

    /** The screen's own drift against burn-in, which comes on top (dp). */
    const val DRIFT = 6f

    /** The point the layers scale about (dp): the screen's middle. */
    const val PIVOT_X = PaperMotif.WIDTH / 2f
    const val PIVOT_Y = PaperMotif.HEIGHT / 2f

    /** The pose at rest: the stills and the docs show the whole picture. */
    val REST = CameraPose(ZOOM_MIN, 0f, 0f)

    /** The pose [seconds] into the wall clock's run, for a label on [label]. */
    fun at(seconds: Double, label: LabelSide = LabelSide.NONE): CameraPose {
        val z = 0.5f + 0.5f * swing(seconds, ZOOM_PERIOD_S, 0.0)
        val k = ZOOM_MIN + (ZOOM_MAX - ZOOM_MIN) * z
        val (x0, x1) = rangeX(k, label)
        val (y0, y1) = rangeY(k, label)
        val cx = x0 + (x1 - x0) * (0.5f + 0.5f * swing(seconds, PAN_PERIOD_S, 1.9))
        val cy = y0 + (y1 - y0) * (0.5f + 0.5f * swing(seconds, TILT_PERIOD_S, 4.1))
        return pose(k, cx, cy)
    }

    /** The pose that shows the picture around ([cx], [cy]) (dp) at [zoom]. */
    fun pose(zoom: Float, cx: Float, cy: Float) = CameraPose(zoom, (PIVOT_X - cx) * zoom, (PIVOT_Y - cy) * zoom)

    /** Where the view's middle may stand across at [zoom] (dp): the paper's reach, the label's margin. */
    fun rangeX(zoom: Float, label: LabelSide): Pair<Float, Float> {
        val half = PIVOT_X / zoom
        var lo = -PaperMotif.OVERSCAN + DRIFT + half
        var hi = PaperMotif.WIDTH + PaperMotif.OVERSCAN - DRIFT - half
        val reach = (PIVOT_X - LABEL_MARGIN - DRIFT) / zoom
        when (label) {
            LabelSide.LEFT -> hi = min(hi, PaperMotif.LABEL_LEFT_X + reach)
            LabelSide.RIGHT -> lo = max(lo, PaperMotif.LABEL_RIGHT_X + PaperMotif.LABEL_MAX_WIDTH - reach)
            LabelSide.NONE -> Unit
        }
        return lo to max(lo, hi)
    }

    /** Where the view's middle may stand up and down at [zoom] (dp): the paper's reach, the label's margin, the tilt. */
    fun rangeY(zoom: Float, label: LabelSide): Pair<Float, Float> {
        val half = PIVOT_Y / zoom
        val lo = -PaperMotif.OVERSCAN + DRIFT + half
        var hi = PaperMotif.HEIGHT + PaperMotif.OVERSCAN - DRIFT - half
        var low = lo
        if (label != LabelSide.NONE) low = max(lo, PaperMotif.LABEL_BOTTOM - (PIVOT_Y - LABEL_MARGIN - DRIFT) / zoom)
        hi = max(low, hi)
        // Not too far up or down: the middle of what is allowed, the tilt to either side.
        val mid = (low + hi) / 2f
        return max(low, mid - TILT) to min(hi, mid + TILT)
    }

    /**
     * A smooth wave −1..1 with a rough period of [period] seconds: two sines whose periods stand
     * in the golden ratio (they never fall into step), the second weaker.
     */
    private fun swing(t: Double, period: Double, phase: Double): Float {
        val a = sin(2 * PI * t / period + phase)
        val b = sin(2 * PI * t / (period * GOLDEN) + phase * 2.3 + 0.7)
        return (0.68 * a + 0.32 * b).toFloat()
    }

    private const val GOLDEN = 0.6180339887

    /** The rough length of one swing (s): in and out, across, up and down. */
    private const val ZOOM_PERIOD_S = 131.0
    private const val PAN_PERIOD_S = 97.0
    private const val TILT_PERIOD_S = 157.0
}
