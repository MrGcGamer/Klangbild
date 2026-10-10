package io.github.hazymorning.klangbild.vista

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class PaperBakeTest {
    private val summer = PaperBake.bake(960, 540, 1f)
    private val winter = PaperBake.bake(960, 540, 1f, SeasonShapes(snow = 1f, peaks = 1f, leaves = 0f), LabelSide.RIGHT)

    @Test
    fun `every sheet is cut, and nothing is left of the leaves in winter`() {
        assertThat(summer.sheets.keys).containsAtLeastElementsIn(SheetId.entries.toList())
        assertThat(winter.sheets.keys).doesNotContain(SheetId.LEAVES)
        assertThat(winter.sheets.keys).contains(SheetId.SNOW)
        for ((id, s) in summer.sheets) {
            assertWithMessage("$id").that(s.pixels.any { it ushr 24 != 0 }).isTrue()
            assertWithMessage("$id").that(s.width * s.height).isEqualTo(s.pixels.size)
        }
    }

    @Test
    fun `the label lies on the bank's own dark paper, not on snow`() {
        for ((baked, side) in listOf(summer to LabelSide.LEFT, winter to LabelSide.RIGHT)) {
            val id = if (side == LabelSide.LEFT) SheetId.FRONT_LEFT else SheetId.FRONT_RIGHT
            val s = baked.sheets.getValue(id)
            val left = if (side == LabelSide.LEFT) PaperMotif.LABEL_LEFT_X else PaperMotif.LABEL_RIGHT_X
            val top = PaperMotif.LABEL_BOTTOM - PaperMotif.LABEL_HEIGHT
            var y = top
            while (y < PaperMotif.LABEL_BOTTOM) {
                var x = left
                while (x < left + PaperMotif.LABEL_MAX_WIDTH) {
                    val px = baked.px(x).toInt() - s.left
                    val py = baked.px(y).toInt() - s.top
                    val p = s.pixels[py * s.width + px]
                    assertWithMessage("$side at $x, $y").that(p ushr 24).isEqualTo(255)
                    // No snow (the second ink) under the label: the bank keeps its own colour.
                    assertWithMessage("$side at $x, $y").that(p shr 8 and 0xFF).isLessThan(8)
                    x += 8f
                }
                y += 4f
            }
        }
    }

    @Test
    fun `the paper has a grain, but no sheet is blotched`() {
        // The near meadow where it shows: between the banks, below its grass.
        val s = summer.sheets.getValue(SheetId.MEADOW_NEAR)
        val t = ArrayList<Float>()
        for (y in summer.px(506f).toInt() until summer.px(534f).toInt()) for (x in summer.px(520f).toInt() until summer.px(760f).toInt()) {
            val p = s.pixels[(y - s.top) * s.width + x - s.left]
            if (p ushr 24 == 255) t += (p and 0xFF) / 255f * Inks.T_SCALE
        }
        val mean = t.average()
        val spread = Math.sqrt(t.sumOf { (it - mean) * (it - mean) } / t.size)
        assertThat(mean).isWithin(0.08).of(1.0)
        assertThat(spread).isGreaterThan(0.01)
        assertThat(spread).isLessThan(0.06)
    }

    @Test
    fun `the fire stands where the motif puts it, its flames tallest first`() {
        val fg = PaperMotif.foreground(LabelSide.LEFT)
        assertThat(summer.fireX).isWithin(1f).of(summer.px(fg.fireX))
        val heights = summer.flames.map { it.sheet.height }
        assertThat(heights.first()).isEqualTo(heights.max())
    }
}
