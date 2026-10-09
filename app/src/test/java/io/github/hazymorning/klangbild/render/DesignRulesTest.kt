package io.github.hazymorning.klangbild.render

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import io.github.hazymorning.klangbild.color.contrastRatio
import io.github.hazymorning.klangbild.color.relativeLuminance
import io.github.hazymorning.klangbild.ui.stage.BLOCK_SHARE
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Design principles as tests, so polishing never quietly breaks them. */
class DesignRulesTest {
    private val screen = ScreenSpec.TV_1080
    private val layout = HaloLayout(screen)
    private val shape = PrintShape(layout.artRect, corner = layout.radius)

    /** A 48 × 48 thumbnail: the top [split] of the rows in [top], the rest in [bottom]. */
    private fun thumb(top: Int, bottom: Int = top, split: Float = 0.5f): IntArray =
        IntArray(48 * 48) { i -> if (i / 48 < (48 * split).toInt()) top else bottom }

    private fun glow(thumb: IntArray, lighting: Lighting = Lighting.DAY) = WallGlow.field(thumb, 48, shape, screen, lighting)

    /** The glow at (x, y) dp on the 960 × 540 map. */
    private fun IntArray.at(xDp: Float, yDp: Float): Int = this[yDp.toInt() * WallGlow.MAP_W + xDp.toInt()]

    private fun luminance(argb: Int) = relativeLuminance(argb)

    private fun contrast(a: Int, b: Int) = contrastRatio(a, b)

    private val covers = listOf(
        0xFFF2EEE6.toInt(), // white paper
        0xFFE8C547.toInt(), // yellow
        0xFFE0457B.toInt(), // pink
        0xFF2A6FD6.toInt(), // blue
        0xFF0E5A66.toInt(), // teal
        0xFF101010.toInt(), // black
    )

    // --- The light on the wall ---------------------------------------------------------

    @Test
    fun `no light at all near the screen edges`() {
        for (c in covers) {
            val field = glow(thumb(c))
            for (y in 0 until WallGlow.MAP_H) for (x in 0 until WallGlow.MAP_W) {
                val edge = minOf(x + 0.5f, WallGlow.MAP_W - x - 0.5f, y + 0.5f, WallGlow.MAP_H - y - 0.5f)
                if (edge < WallGlow.EDGE_BLACK_DP - 1f) assertWithMessage("($x, $y)").that(field[y * WallGlow.MAP_W + x] and 0xFFFFFF).isEqualTo(0)
            }
        }
    }

    @Test
    fun `a tight halo, the same on every side, gone before the text column`() {
        val field = glow(thumb(0xFFF2EEE6.toInt()))
        val r = layout.artRect
        val near = luminance(field.at(r.right + 2f, r.centerY))
        assertThat(near).isIn(Range.closed(WallGlow.EDGE_LUMINANCE * 0.8f, WallGlow.EDGE_LUMINANCE * 1.1f))
        // the same at 12 dp on all four sides (no side cut short by the screen's edge)
        val sides = listOf(
            field.at(r.right + 12f, r.centerY),
            field.at(r.left - 12f, r.centerY),
            field.at(r.centerX, r.top - 12f),
            field.at(r.centerX, r.bottom + 12f),
        ).map { luminance(it) }
        for (s in sides) assertThat(s).isWithin(near * 0.12f).of(sides.first())
        assertThat(sides.first()).isLessThan(near)
        // the text column stands on black
        assertThat(field.at(layout.columnLeft + 1f, r.centerY) and 0xFFFFFF).isEqualTo(0)
        // dark behind the artwork, lit just under its edge (no dark rim around it)
        assertThat(field.at(r.centerX, r.centerY) and 0xFFFFFF).isEqualTo(0)
        assertThat(luminance(field.at(r.right - 1f, r.centerY))).isGreaterThan(near * 0.2f)
    }

    @Test
    fun `the glow takes the colour of the nearest edge`() {
        // violet above, teal below
        val field = glow(thumb(0xFF6A2BC2.toInt(), 0xFF0E8A86.toInt()))
        val above = Oklab.fromArgb(field.at(layout.artRect.centerX, layout.artRect.top - 6f))
        val below = Oklab.fromArgb(field.at(layout.artRect.centerX, layout.artRect.bottom + 6f))
        assertThat(above.hue).isIn(Range.closed(270f, 320f))
        assertThat(below.hue).isIn(Range.closed(170f, 215f))
    }

    @Test
    fun `dark edges glow little, and no glow is a neon sign`() {
        val bright = luminance(glow(thumb(0xFFE0457B.toInt())).at(layout.artRect.right + 2f, layout.artRect.centerY))
        val dark = luminance(glow(thumb(0xFF101010.toInt())).at(layout.artRect.right + 2f, layout.artRect.centerY))
        // a picture lights the wall at most as much as it is bright itself
        assertThat(dark).isLessThan(bright * 0.2f)
        for (c in covers) {
            val lab = Oklab.fromArgb(glow(thumb(c)).at(layout.artRect.right + 2f, layout.artRect.centerY))
            // chroma at the glow's own (low) lightness stays modest
            assertThat(lab.chroma).isAtMost(WallGlow.MAX_CHROMA)
        }
    }

    @Test
    fun `night turns the glow right down and takes its colour`() {
        val c = thumb(0xFFE0457B.toInt())
        val day = glow(c, Lighting.DAY).at(layout.artRect.right + 2f, layout.artRect.centerY)
        val night = glow(c, Lighting.NIGHT).at(layout.artRect.right + 2f, layout.artRect.centerY)
        assertThat(luminance(night)).isAtMost(luminance(day) * 0.3f)
        assertThat(Oklab.fromArgb(night).chroma).isLessThan(Oklab.fromArgb(day).chroma)
    }

    @Test
    fun `the quietest text stays readable on the glow`() {
        for (c in covers) {
            val field = glow(thumb(c))
            val palette = CoverPalette.derive(CoverPalette.analyze(thumb(c)), Lighting.DAY)
            // the brightest wall under the text column: its left edge, level with the artwork's centre
            val wall = field.at(layout.columnLeft + 1f, layout.textCenter)
            assertWithMessage("cover " + Integer.toHexString(c)).that(contrast(palette.inkQuiet, wall)).isAtLeast(4.5f)
            assertWithMessage("cover " + Integer.toHexString(c)).that(contrast(palette.ink, wall)).isAtLeast(10f)
        }
    }

    // --- Colour of the text -------------------------------------------------------------

    @Test
    fun `text is near-neutral, never pure white, never ivory`() {
        for (c in covers) {
            val p = CoverPalette.derive(CoverPalette.analyze(thumb(c)), Lighting.DAY)
            val ink = Oklab.fromArgb(p.ink)
            assertThat(p.ink and 0xFFFFFF).isNotEqualTo(0xFFFFFF)
            assertThat(ink.l).isIn(Range.closed(0.88f, 0.95f))
            assertThat(ink.chroma).isAtMost(0.015f)
        }
        // a neutral cover gets neutral text
        val grey = CoverPalette.derive(CoverPalette.analyze(thumb(0xFF808080.toInt())), Lighting.DAY)
        assertThat(Oklab.fromArgb(grey.ink).chroma).isAtMost(0.007f)
    }

    @Test
    fun `three clear text levels on both walls and the title keeps 4,5 to 1 at night`() {
        for (appearance in Appearance.entries) {
            val p = CoverPalette.derive(CoverPalette.analyze(thumb(0xFF2A6FD6.toInt())), Lighting.DAY, appearance)
            val tiers = listOf(p.ink, p.inkMuted, p.inkQuiet).map { Oklab.fromArgb(it).l }
            // each level clearly apart from the one before it, away from the wall
            for (i in 1 until tiers.size) assertThat(abs(tiers[i - 1] - tiers[i])).isAtLeast(0.1f)
        }
        val night = CoverPalette.derive(CoverPalette.analyze(thumb(0xFF2A6FD6.toInt())), Lighting.NIGHT)
        assertThat(contrast(night.ink, 0xFF000000.toInt())).isAtLeast(4.5f)
    }

    @Test
    fun `on the light wall the artwork casts a soft shadow below it, none above`() {
        val wall = CoverPalette.derive(CoverPalette.analyze(thumb(0xFF2A6FD6.toInt())), Lighting.DAY, Appearance.LIGHT).wall
        val field = WallShade.field(wall, shape, screen)
        val rect = layout.artRect
        val below = luminance(field.at(rect.centerX, rect.bottom + 6f))
        val above = luminance(field.at(rect.centerX, rect.top - 6f))
        val far = luminance(field.at(rect.right + 40f, rect.top + 20f))
        assertThat(below).isLessThan(far * 0.9f)
        assertThat(above).isGreaterThan(below)
        // a few dozen dp away the paper is clean
        assertThat(far).isWithin(0.004f).of(luminance(wall))
    }

    @Test
    fun `night turns the lamp right down and takes its colour`() {
        val day = HaloLight.lightTint(HaloLight.LAMP, 0.05f, Lighting.DAY)
        val night = HaloLight.lightTint(HaloLight.LAMP, 0.05f, Lighting.NIGHT)
        assertThat(luminance(night)).isAtMost(luminance(day) * 0.29f)
        assertThat(HaloLight.hsvSaturation(night)).isAtMost(0.5f)
    }

    // --- The grid -------------------------------------------------------------------------

    @Test
    fun `the artwork is the heaviest element and sets the height`() {
        // two thirds of the screen's height
        assertThat(layout.art).isWithin(0.01f).of(368f)
        // the text block takes clearly less height than the artwork
        assertThat(BLOCK_SHARE).isAtMost(0.65f)
        // the quiet lines hang inside the artwork's height, never above or below it
        assertThat(layout.statusTop).isAtLeast(layout.artRect.top)
        assertThat(layout.footerBaseline).isAtMost(layout.artRect.bottom)
        // the group sits a little above the screen's centre (optical centre)
        assertThat(screen.heightDp / 2f - layout.artRect.centerY).isWithin(0.01f).of(HaloLayout.LIFT)
        // margins: the artwork 96 dp from the left, the column 80 dp from the right, 64 dp between
        assertThat(layout.artRect.left).isWithin(0.01f).of(96f)
        assertThat(screen.widthDp - (layout.columnLeft + layout.columnWidth)).isWithin(0.01f).of(80f)
        assertThat(layout.columnLeft - layout.artRect.right).isWithin(0.01f).of(64f)
    }

    @Test
    fun `the artwork has softly rounded corners`() {
        val r = layout.radius
        assertThat(r / layout.art).isIn(Range.closed(0.015f, 0.03f))
        val rect = layout.artRect
        // the very corner of the square is outside the rounded artwork …
        assertThat(shape.contains(rect.left + 0.5f, rect.top + 0.5f)).isFalse()
        // … the middle of an edge is not
        assertThat(shape.contains(rect.centerX, rect.top + 0.5f)).isTrue()
        assertThat(shape.distance(rect.left - 10f, rect.centerY)).isWithin(0.01f).of(10f)
    }

    @Test
    fun `covers are never shown larger than their resolution allows`() {
        assertThat(HaloLayout.coverDp(layout, 300)).isWithin(0.01f).of(195f)
        assertThat(HaloLayout.coverDp(layout, 640)).isWithin(0.01f).of(layout.art) // 416 → capped at 368
        assertThat(HaloLayout.coverDp(layout, null)).isEqualTo(layout.art)
        for (native in listOf(100, 240, 300, 500, 580, 640, 1000, 3000)) {
            val dp = HaloLayout.coverDp(layout, native)
            assertThat(dp * 2f / native).isAtMost(HaloLayout.MAX_SOURCE_SCALE_1080P + 1e-4f)
        }
    }

    @Test
    fun `a small print gets a passe-partout that fills the square`() {
        val print = IntArray(10 * 10) { 0xFFFF0000.toInt() }
        val out = SceneBuilder.matted(print, 10, 30, density = 2f)
        assertThat(out.size).isEqualTo(900)
        assertThat(out[15 * 30 + 15]).isEqualTo(0xFFFF0000.toInt()) // the print, centred
        // grained mat board
        assertThat((out[0] shr 16) and 0xFF).isIn(Range.closed(((SceneBuilder.MAT shr 16) and 0xFF) - 2, ((SceneBuilder.MAT shr 16) and 0xFF) + 2))
        assertThat(out[8 * 30 + 15]).isEqualTo(SceneBuilder.BEVEL) // the bevel just above the print
        // optically centred: a little more mat below the print than above it
        val rows = (0 until 30).filter { out[it * 30 + 15] == 0xFFFF0000.toInt() }
        assertThat(29 - rows.last()).isGreaterThan(rows.first())
    }

    // --- Generated art -------------------------------------------------------------------

    @Test
    fun `sand figures are mirror-symmetric, so none can look like a hooked cross`() {
        val rnd = Random(7)
        for (seed in listOf("Radio Morgenlicht", "Jazzradio", "BR-Klassik", "FIP", "SWR2", "WDR 3", "x", "Klangbild")) {
            val spec = ChladniArt.specFor(seed)
            repeat(50) {
                val r = rnd.nextFloat()
                val t = rnd.nextFloat() * 6.283f
                assertThat(ChladniArt.field(spec, r, t)).isWithin(1e-4f).of(ChladniArt.field(spec, r, -t))
            }
        }
    }

    @Test
    fun `sand figures are wavy rings, never a cross, a wheel or a rosette`() {
        val seen = HashSet<Pair<Int, Int>>()
        for (i in 0 until 300) {
            val spec = ChladniArt.specFor("Sender $i")
            // no nodal diameters: through the centre they make crosses, wheels and petals
            assertThat(spec.n1).isEqualTo(0)
            assertThat(spec.n2).isIn(setOf(2, 3, 4, 5, 6, 8))
            // the second mode only bends the circles
            assertThat(kotlin.math.abs(spec.mix)).isAtMost(ChladniArt.MAX_MIX)
            // the middle stays free: the field there is the ring mode's
            assertThat(ChladniArt.field(spec, 0.05f, 1f)).isGreaterThan(0.8f)
            seen += Math.round(spec.k1 * 0.7f / 3f) to spec.n2
        }
        // variety: two or three circles, many lobe counts
        assertThat(seen.size).isAtLeast(8)
    }
}
