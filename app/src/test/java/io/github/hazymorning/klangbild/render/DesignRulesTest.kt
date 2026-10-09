package io.github.hazymorning.klangbild.render

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import org.junit.Test
import kotlin.random.Random

/** Design principles as tests, so polishing never quietly breaks them. */
class DesignRulesTest {
    private val screen = ScreenSpec.TV_1080
    private val layout = HaloLayout(screen)
    private val shape = PrintShape(layout.coverRect(layout.slot))

    private fun thumb(vararg parts: Pair<Int, Float>): IntArray {
        val out = IntArray(48 * 48)
        var i = 0
        for ((argb, share) in parts) {
            val n = (share * out.size).toInt()
            repeat(n) { if (i < out.size) out[i++] = argb }
        }
        while (i < out.size) out[i++] = parts.first().first
        return out
    }

    private fun grey(px: Int) = px and 0xFF

    @Test
    fun `no light at all near the screen edges`() {
        for (mask in listOf(HaloLight.holeMask(shape, screen, night = 0f), HaloLight.openMask(screen))) {
            for (y in 0 until HaloLight.MAP_H) for (x in 0 until HaloLight.MAP_W) {
                val xDp = (x + 0.5f) / HaloLight.MAP_W * screen.widthDp
                val yDp = (y + 0.5f) / HaloLight.MAP_H * screen.heightDp
                val edge = minOf(xDp, screen.widthDp - xDp, yDp, screen.heightDp - yDp)
                if (edge < HaloLight.EDGE_CLEAR_DP - 1f) assertWithMessage("($x, $y)").that(grey(mask[y * HaloLight.MAP_W + x])).isEqualTo(0)
            }
        }
    }

    @Test
    fun `the far wall is pure black`() {
        val p = HaloLight.profile(shape.rect.width, 0f)
        // The pool is gone (below the black threshold) well within the sprite's margin.
        assertThat(HaloLight.pool(110f, p)).isLessThan(0.0005f)
        val sprite = HaloLight.glowSprite(shape, night = 0f, seed = 1)
        val size = HaloLight.glowSize(shape)
        for (i in 0 until size) {
            assertThat(grey(sprite[i])).isEqualTo(0) // top row
            assertThat(grey(sprite[i * size])).isEqualTo(0) // left column
        }
    }

    @Test
    fun `dark behind the print, a little shaded at its edge, brightest a few dp out`() {
        val p = HaloLight.profile(shape.rect.width, 0f)
        fun light(d: Float) = HaloLight.pool(d, p) * HaloLight.shade(d, inside = false, p = p)
        assertThat(HaloLight.shade(0f, inside = true, p = p)).isEqualTo(0f)
        val profile = (1..60).map { it to light(it.toFloat()) }
        val (peakAt, peak) = profile.maxBy { it.second }
        assertThat(peakAt).isIn(8..18)
        assertThat(light(1f) / peak).isIn(Range.closed(0.45f, 0.62f))
        assertThat(light(70f)).isLessThan(peak * 0.02f)
    }

    @Test
    fun `the lamp sits off centre, and the sun's side is clearly the lit one`() {
        assertThat(HaloLight.lampOffset(320f)).isIn(Range.closed(20f, 32f))
        for (sun in listOf(225f, 90f, -45f, 10f)) {
            val lit = Oklab.linearToSrgb(HaloLight.maskAt(sun, sun))
            assertThat(lit / Oklab.linearToSrgb(HaloLight.maskAt(sun + 90f, sun))).isAtLeast(3.8f)
            assertThat(lit / Oklab.linearToSrgb(HaloLight.maskAt(sun + 180f, sun))).isAtLeast(3.8f)
        }
        val stops = HaloLight.maskStops()
        assertThat(Oklab.srgbToLinear(stops.first() and 0xFF)).isWithin(0.01f).of(1f)
        assertThat(Oklab.srgbToLinear(stops[stops.size / 2] and 0xFF)).isWithin(0.01f).of(HaloLight.UNLIT)
    }

    @Test
    fun `the sun rises lower left, stands above at half time and sets lower right`() {
        assertThat(HaloLight.sunAngle(0f, 0)).isEqualTo(225f)
        assertThat(HaloLight.sunAngle(0.5f, 0)).isEqualTo(90f)
        assertThat(HaloLight.sunAngle(1f, 0)).isEqualTo(-45f)
    }

    @Test
    fun `night turns the lamp right down and takes its colour`() {
        val vivid = CoverPalette.analyze(thumb(0xFFFF2E88.toInt() to 0.4f, 0xFF2B0A4A.toInt() to 0.6f))
        val color = HaloLight.lightColor(vivid)
        val day = HaloLight.lightTint(color, 0.05f, Lighting.DAY)
        val night = HaloLight.lightTint(color, 0.05f, Lighting.NIGHT)
        assertThat(Oklab.relativeLuminance(night)).isAtMost(Oklab.relativeLuminance(day) * 0.29f)
        assertThat(HaloLight.hsvSaturation(night)).isAtMost(0.5f)
    }

    @Test
    fun `dimmed light loses colour faster than brightness`() {
        val tint = Lch(0.42f, 0.09f, 300f).toArgb()
        val dim = HaloLight.dimTint(tint, 0.3f)
        assertThat(Oklab.relativeLuminance(dim)).isWithin(0.004f).of(Oklab.relativeLuminance(tint) * 0.3f)
        assertThat(Oklab.fromArgb(dim).chroma).isLessThan(Oklab.fromArgb(tint).chroma * 0.5f)
    }

    @Test
    fun `a violet cover keeps its violet light`() {
        // Mostly deep violet sky, a small orange sun, a pink horizon.
        val a = CoverPalette.analyze(
            thumb(0xFF2A1450.toInt() to 0.62f, 0xFFF4A340.toInt() to 0.12f, 0xFFE0457B.toInt() to 0.1f, 0xFF0A0610.toInt() to 0.16f),
        )
        assertThat(HaloLight.lightHue(a)!!).isIn(Range.closed(270f, 335f))
    }

    @Test
    fun `warm light is tungsten, never rust`() {
        val a = CoverPalette.analyze(thumb(0xFFC2552A.toInt() to 0.7f, 0xFF3B2414.toInt() to 0.3f))
        val c = HaloLight.lightColor(a)
        assertThat(c.h).isIn(Range.closed(64f, 85f))
        assertThat(c.c).isAtMost(0.045f)
        val grey = CoverPalette.analyze(thumb(0xFF808080.toInt() to 1f))
        assertThat(HaloLight.lightColor(grey)).isEqualTo(HaloLight.LAMP)
    }

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
    fun `a small print gets a passe-partout that fills the slot`() {
        val print = IntArray(10 * 10) { 0xFFFF0000.toInt() }
        val out = SceneBuilder.matted(print, 10, 30, density = 2f)
        assertThat(out.size).isEqualTo(900)
        assertThat(out[15 * 30 + 15]).isEqualTo(0xFFFF0000.toInt()) // the print, centred
        assertThat(out[0]).isEqualTo(0xFF191715.toInt()) // mat board
        val bevel = out[9 * 30 + 15] // just above the print
        assertThat((bevel shr 16) and 0xFF).isGreaterThan(0x19)
    }

    @Test
    fun `covers are never shown larger than their resolution allows`() {
        assertThat(HaloLayout.coverDp(layout, 300)).isWithin(0.01f).of(165f)
        assertThat(HaloLayout.coverDp(layout, 640)).isWithin(0.01f).of(layout.slot) // 352 → capped at 320
        assertThat(HaloLayout.coverDp(layout, null)).isEqualTo(layout.slot)
        for (native in listOf(100, 240, 300, 500, 580, 640, 1000, 3000)) {
            val dp = HaloLayout.coverDp(layout, native)
            assertThat(dp * 2f / native).isAtMost(HaloLayout.MAX_SOURCE_SCALE_1080P + 1e-4f)
        }
    }

    @Test
    fun `the grid keeps equal outer margins and mirrors cleanly`() {
        val l = HaloLayout(screen)
        val m = HaloLayout(screen, mirrored = true)
        assertThat(l.slotRect.left).isEqualTo(l.margin)
        assertThat(screen.widthDp - (l.columnLeft + l.columnWidth)).isWithin(0.01f).of(l.margin)
        assertThat(screen.widthDp - m.slotRect.right).isWithin(0.01f).of(m.margin)
        assertThat(m.columnLeft).isEqualTo(m.margin)
        assertThat(l.gutter).isWithin(0.01f).of(64f)
    }

    @Test
    fun `light colours morph through OKLCH without going grey`() {
        val violet = Lch(0.45f, 0.12f, 300f).toArgb()
        val amber = Lch(0.45f, 0.12f, 50f).toArgb()
        for (t in listOf(0.25f, 0.5f, 0.75f)) {
            assertThat(Oklab.fromArgb(HaloLight.mixOklch(violet, amber, t)).chroma).isAtLeast(0.1f)
        }
    }
}
