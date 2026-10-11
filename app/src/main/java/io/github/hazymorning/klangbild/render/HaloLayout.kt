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
 * clearly shorter (at most about 60 % of the artwork's height) and centered on it, measured
 * from the first line's cap height to the last baseline. Two quiet lines hang on the
 * artwork's edges: the status line (time, where the music plays) from its top edge, and what
 * comes next on its bottom edge. Every line in the column starts on one left edge.
 *
 * The group stands exactly in the middle of the screen: as much room above the artwork as
 * below it, as much left of it as right of the column (the progress bar's end).
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │            88            368          64       352     88 │
 *  │        ╭──────────────╮      21:47 · Everywhere          │  ← artwork top
 *  │        │              │      Title                       │
 *  │        │   artwork    │      Artist                      │  ← artwork center
 *  │        │              │      Album · Year                │
 *  │        │              │      ━━━━━━━━━━━━──────────      │
 *  │        │              │      1:35                4:11    │
 *  │        ╰──────────────╯      Up next  …                  │  ← artwork bottom
 *  └──────────────────────────────────────────────────────────┘
 *
 * Cover Flow ([StageView.FLOW]) keeps every rule but makes room for the cards beside the
 * middle one: the middle cover is 300 dp with its center 303 dp from the left, so the strip's
 * outer edge (255 dp beside it, see [FlowGeometry]) keeps 48 dp from the screen's edge. That is
 * as much as the column (from 598 dp) keeps on the right.
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

    /** Center line of the text block (from its first cap height to its last baseline). */
    val textCenter get() = artTop + art / 2f

    /** The status line's cap height sits on the artwork's top edge. */
    val statusTop get() = artTop

    /** The "Up next" line's baseline sits on the artwork's bottom edge. */
    val footerBaseline get() = artTop + art

    /** Corner radius of a square artwork (dp): a hair over 2 % of its side. */
    val radius get() = RADIUS * art / SINGLE.art

    /** The cover rect for a cover of [coverDp], centered in the artwork's square. */
    fun coverRect(coverDp: Float): RectDp {
        val s = artRect
        val size = min(coverDp, s.width)
        return RectDp(s.left + (s.width - size) / 2f, s.top + (s.height - size) / 2f, size, size)
    }

    /** Margins, artwork and gap of a view at 960 × 540 dp. */
    private class Grid(val margin: Float, val art: Float, val gap: Float, val rightMargin: Float)

    companion object {
        /** The grid's width (dp): every measure here is for a screen this wide, scaled to the real one. */
        const val GRID_WIDTH = 960f

        /** Between the artwork and the text (dp at 960 × 540). */
        const val ART_TEXT_GAP = 64f

        private val SINGLE = Grid(margin = 88f, art = 368f, gap = ART_TEXT_GAP, rightMargin = 88f)
        private val FLOW = Grid(margin = 153f, art = 300f, gap = 145f, rightMargin = 48f)

        /** Corner radius of square artwork (dp at 960 × 540, for the single view's 368 dp). */
        const val RADIUS = 8f

    }
}
