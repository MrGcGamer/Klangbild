package io.github.hazymorning.klangbild.render

import kotlin.math.min

/** Physical screen the scene is prepared for. */
data class ScreenSpec(val widthPx: Int, val heightPx: Int, val density: Float) {
    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density

    companion object {
        val TV_1080 = ScreenSpec(1920, 1080, 2f)
        val TV_4K = ScreenSpec(3840, 2160, 4f)
    }
}

/** A rectangle in dp. */
data class RectDp(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
    val centerX get() = left + width / 2f
    val centerY get() = top + height / 2f
}

/** The visual directions. Only polished styles stay selectable; see docs/DECISIONS.md. */
enum class StageStyle { HALO, GALERIE, FEUILLETON }

/** Where the cover sits for a style. Everything else is laid out by the composables. */
object StageLayout {
    /** The largest the cover may ever be drawn per style (dp, on a 960 × 540 dp screen). */
    fun maxCoverDp(style: StageStyle, screen: ScreenSpec): Float = when (style) {
        StageStyle.HALO -> screen.heightDp * 0.56f
        StageStyle.GALERIE -> screen.heightDp * 0.47f
        StageStyle.FEUILLETON -> screen.heightDp * 0.56f
    }

    /**
     * Honest cover size: never more than ~1.1 source pixels per 1080p pixel. At 4K that is
     * a 2.2× Lanczos upscale – below what the eye resolves from the sofa, while a 300 px
     * radio thumbnail stays small and crisp instead of becoming a blurry poster.
     */
    fun coverDp(style: StageStyle, screen: ScreenSpec, nativePx: Int?, generated: Boolean): Float {
        val max = maxCoverDp(style, screen)
        if (generated || nativePx == null) return max
        val honest = nativePx * MAX_SOURCE_SCALE_1080P / 2f // 1 dp = 2 px at 1080p
        return min(max, honest)
    }

    const val MAX_SOURCE_SCALE_1080P = 1.1f

    fun coverRect(style: StageStyle, screen: ScreenSpec, coverDp: Float): RectDp {
        val w = screen.widthDp
        val h = screen.heightDp
        return when (style) {
            StageStyle.HALO -> {
                val cx = w * 0.285f
                val cy = h * 0.485f
                RectDp(cx - coverDp / 2f, cy - coverDp / 2f, coverDp, coverDp)
            }
            StageStyle.GALERIE -> {
                val cx = w * 0.40f
                val cy = h * 0.47f
                RectDp(cx - coverDp / 2f, cy - coverDp / 2f, coverDp, coverDp)
            }
            StageStyle.FEUILLETON -> {
                val right = w - 84f
                val cy = h * 0.5f
                RectDp(right - coverDp, cy - coverDp / 2f, coverDp, coverDp)
            }
        }
    }
}
