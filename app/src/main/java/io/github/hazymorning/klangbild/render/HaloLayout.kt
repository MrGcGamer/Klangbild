package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.domain.StageView
import kotlin.math.min

/** Physical screen the scene is prepared for. */
data class ScreenSpec(val widthPx: Int, val heightPx: Int, val density: Float) {
    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density

    companion object {
        val TV_1080 = ScreenSpec(1920, 1080, 2f)
    }
}

/** A rectangle in dp. */
data class RectDp(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
    val centerX get() = left + width / 2f
    val centerY get() = top + height / 2f

}

/**
 * The stage's grid on a 960 × 540 dp screen. The artwork is the heaviest element and sets
 * the composition's height: 368 dp, two thirds of the screen. The text block beside it is
 * clearly shorter (at most about 60 % of the artwork's height) and centred on it, measured
 * from the first line's cap height to the last baseline. Two quiet lines hang on the
 * artwork's edges: the status line (time, where the music plays) from its top edge, what
 * comes next on its bottom edge. Every line in the column starts on one left edge.
 *
 * The group stands exactly in the middle of the screen: as much room above the artwork as
 * below it, as much left of it as right of the column (the progress bar's end).
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │            88            368          64       352     88 │
 *  │        ╭──────────────╮      21:47 · Überall            │  ← artwork top
 *  │        │              │      Title                       │
 *  │        │   artwork    │      Artist                      │  ← artwork centre
 *  │        │              │      Album · Year                │
 *  │        │              │      ━━━━━━━━━━━━──────────      │
 *  │        │              │      1:35                4:11    │
 *  │        ╰──────────────╯      Als Nächstes  …             │  ← artwork bottom
 *  └──────────────────────────────────────────────────────────┘
 *
 * Cover Flow ([StageView.FLOW]) keeps every rule but makes room for the cards beside the
 * middle one: the middle cover is 300 dp with its centre 303 dp from the left, so the strip's
 * outer edge (255 dp beside it, see [FlowGeometry]) keeps 48 dp from the screen's edge – as
 * much as the column (from 598 dp) keeps on the right.
 */
data class HaloLayout(val screen: ScreenSpec, val view: StageView = StageView.SINGLE) {
    private val sx = screen.widthDp / 960f
    private val sy = screen.heightDp / 540f
    private val grid = if (view == StageView.FLOW) FLOW else SINGLE

    /** Left margin before the artwork, and after the column on the right. */
    val margin = grid.margin * sx
    val rightMargin = grid.rightMargin * sx

    /** Side of the artwork's square. */
    val art = grid.art * sy
    val artTop = (screen.heightDp - art) / 2f
    val artRect: RectDp get() = RectDp(margin, artTop, art, art)

    /** Space between artwork and text. */
    val gap = grid.gap * sx
    val columnLeft get() = margin + art + gap
    val columnWidth get() = screen.widthDp - rightMargin - columnLeft

    /** Centre line of the text block (from its first cap height to its last baseline). */
    val textCenter get() = artTop + art / 2f

    /** The status line's cap height sits on the artwork's top edge … */
    val statusTop get() = artTop

    /** … and the "what comes next" line's baseline on its bottom edge. */
    val footerBaseline get() = artTop + art

    /** Corner radius of a square artwork (dp): a hair over 2 % of its side. */
    val radius get() = RADIUS * art / SINGLE.art

    /** The cover rect for a cover of [coverDp], centred in the artwork's square. */
    fun coverRect(coverDp: Float): RectDp {
        val s = artRect
        val size = min(coverDp, s.width)
        return RectDp(s.left + (s.width - size) / 2f, s.top + (s.height - size) / 2f, size, size)
    }

    /** Margins, artwork and gap of a view at 960 × 540 dp. */
    private class Grid(val margin: Float, val art: Float, val gap: Float, val rightMargin: Float)

    companion object {
        private val SINGLE = Grid(margin = 88f, art = 368f, gap = 64f, rightMargin = 88f)
        private val FLOW = Grid(margin = 153f, art = 300f, gap = 145f, rightMargin = 48f)

        /** Corner radius of square artwork (dp at 960 × 540, for the single view's 368 dp). */
        const val RADIUS = 8f

        /**
         * Honest cover size: at most 1.3 source pixels per 1080p pixel. A 640 px streaming cover
         * fills the square with a gentle Lanczos upscale – invisible from the sofa – while a
         * 300 px radio logo stays small and crisp in its mat instead of turning into mush.
         */
        const val MAX_SOURCE_SCALE_1080P = 1.3f

        fun coverDp(layout: HaloLayout, nativePx: Int?): Float {
            val max = layout.art
            if (nativePx == null) return max
            return min(max, nativePx * MAX_SOURCE_SCALE_1080P / 2f) // 1 dp = 2 px at 1080p
        }
    }
}
