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

    fun translate(dx: Float, dy: Float) = RectDp(left + dx, top + dy, width, height)
}

/**
 * The Halo grid on a 960 × 540 dp screen: two blocks with equal outer margins.
 *
 *  - Cover slot: 320 dp square, 108 dp from the edge, vertically centred.
 *  - Text column: 360 dp wide, 108 dp from the other edge, 64 dp gutter... (see below)
 *  - Fixed baselines in the column: clock cap height at the cover's top edge, the artist
 *    line at 350 dp (titles grow upwards from 44 dp above it), the time line on the cover's
 *    bottom edge.
 *
 * [mirrored] swaps the sides (cover right, text left); used per album against burn-in.
 */
data class HaloLayout(
    val screen: ScreenSpec,
    val mirrored: Boolean = false,
) {
    private val scaleX = screen.widthDp / 960f
    private val scaleY = screen.heightDp / 540f

    val margin = 108f * scaleX
    val slot = 320f * scaleY
    val slotTop = (screen.heightDp - slot) / 2f
    val columnWidth = 360f * scaleX
    val gutter = screen.widthDp - 2 * margin - slot - columnWidth

    val slotRect: RectDp
        get() = if (!mirrored) RectDp(margin, slotTop, slot, slot)
        else RectDp(screen.widthDp - margin - slot, slotTop, slot, slot)

    val columnLeft: Float
        get() = if (!mirrored) margin + slot + gutter else margin

    /** Baselines and edges of the text column (dp from the top). */
    val clockTop get() = slotTop
    val artistBaseline get() = slotTop + 240f * scaleY
    val titleBaseline get() = artistBaseline - 44f * scaleY
    val albumGap get() = 24f * scaleY
    val timeLineBaseline get() = slotTop + slot

    /** The cover rect for a cover of [coverDp], centred in its slot. */
    fun coverRect(coverDp: Float): RectDp {
        val s = slotRect
        val size = min(coverDp, s.width)
        return RectDp(s.left + (s.width - size) / 2f, s.top + (s.height - size) / 2f, size, size)
    }

    companion object {
        /**
         * Honest cover size: never more than ~1.1 source pixels per 1080p pixel. At 4K that is
         * a 2.2× Lanczos upscale – below what the eye resolves from the sofa – while a 300 px
         * radio logo stays small and crisp instead of becoming a blurry poster.
         */
        const val MAX_SOURCE_SCALE_1080P = 1.1f

        fun coverDp(layout: HaloLayout, nativePx: Int?): Float {
            val max = layout.slot
            if (nativePx == null) return max
            return min(max, nativePx * MAX_SOURCE_SCALE_1080P / 2f) // 1 dp = 2 px at 1080p
        }
    }
}
