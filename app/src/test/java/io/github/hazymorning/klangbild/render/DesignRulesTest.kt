package io.github.hazymorning.klangbild.render

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import org.junit.Test

/** Design principles as tests, so polishing never quietly breaks them. */
class DesignRulesTest {
    private val screen = ScreenSpec.TV_1080
    private val layout = HaloLayout(screen)
    private val rect = layout.coverRect(layout.slot)

    private fun map(night: Float = 0f) =
        HaloLight.lightMap(rect, screen, openToRight = true, night = night, seed = 1).pixels

    /** Grey value of the map as linear light 0..1. */
    private fun lin(px: IntArray, xDp: Float, yDp: Float): Float {
        val x = (xDp / screen.widthDp * HaloLight.MAP_W).toInt().coerceIn(0, HaloLight.MAP_W - 1)
        val y = (yDp / screen.heightDp * HaloLight.MAP_H).toInt().coerceIn(0, HaloLight.MAP_H - 1)
        return Oklab.srgbToLinear(px[y * HaloLight.MAP_W + x] and 0xFF)
    }

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

    @Test
    fun `no light at all near the screen edges`() {
        val px = map()
        val clear = HaloLight.EDGE_CLEAR_DP - 1f
        for (y in 0 until HaloLight.MAP_H) for (x in 0 until HaloLight.MAP_W) {
            val xDp = (x + 0.5f) / HaloLight.MAP_W * screen.widthDp
            val yDp = (y + 0.5f) / HaloLight.MAP_H * screen.heightDp
            val edge = minOf(xDp, screen.widthDp - xDp, yDp, screen.heightDp - yDp)
            if (edge < clear) assertWithMessage("($x, $y)").that(px[y * HaloLight.MAP_W + x] and 0xFF).isEqualTo(0)
        }
    }

    @Test
    fun `the far wall is pure black`() {
        val px = map()
        // 250 dp beyond the print on the open side, and above/below the text column
        assertThat(px[(270f / screen.heightDp * HaloLight.MAP_H).toInt() * HaloLight.MAP_W + ((rect.right + 250f) / screen.widthDp * HaloLight.MAP_W).toInt()] and 0xFF).isEqualTo(0)
        assertThat(lin(px, rect.right + 400f, 270f)).isEqualTo(0f)
    }

    @Test
    fun `dark behind the print, a little shaded at its edge, brightest a few dp out`() {
        val px = map()
        assertThat(lin(px, rect.centerX, rect.centerY)).isEqualTo(0f)
        // Along the left (screen-edge) side, away from the wide layer:
        val y = rect.centerY
        val profile = (1..60).map { d -> d to lin(px, rect.left - d, y) }
        val (peakAt, peak) = profile.maxBy { it.second }
        assertThat(peakAt).isIn(8..18)
        val atEdge = lin(px, rect.left - 1.5f, y)
        assertThat(atEdge / peak).isIn(com.google.common.collect.Range.closed(0.4f, 0.62f))
        // and it falls off well before the screen edge
        assertThat(lin(px, rect.left - 70f, y)).isLessThan(peak * 0.12f)
    }

    @Test
    fun `the sundial lights one side about three times more than the other`() {
        for (sun in listOf(225f, 90f, -45f, 10f)) {
            val lit = HaloLight.maskAt(sun, sun)
            val unlit = HaloLight.maskAt(sun + 180f, sun)
            assertThat(lit / unlit).isAtLeast(2.5f)
            // …also as the eye sees it (8-bit sRGB values)
            assertThat(Oklab.linearToSrgb(lit) / Oklab.linearToSrgb(unlit)).isAtLeast(2.3f)
        }
        // The sweep-gradient stops encode the same mask (sRGB grey).
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
        val day = HaloLight.lightTint(color, 0.05f, 1f, Lighting.DAY)
        val night = HaloLight.lightTint(color, 0.05f, 1f, Lighting.NIGHT)
        assertThat(Oklab.relativeLuminance(night)).isAtMost(Oklab.relativeLuminance(day) * 0.31f)
        assertThat(HaloLight.hsvSaturation(night)).isAtMost(0.5f)
    }

    @Test
    fun `a violet cover keeps its violet light`() {
        // Mostly deep violet sky, a small orange sun, a pink horizon.
        val a = CoverPalette.analyze(
            thumb(0xFF2A1450.toInt() to 0.62f, 0xFFF4A340.toInt() to 0.12f, 0xFFE0457B.toInt() to 0.1f, 0xFF0A0610.toInt() to 0.16f),
        )
        val hue = HaloLight.lightHue(a)!!
        assertThat(hue).isIn(com.google.common.collect.Range.closed(270f, 335f))
    }

    @Test
    fun `warm light is red-amber, never brown`() {
        val a = CoverPalette.analyze(thumb(0xFF8A5A2B.toInt() to 0.7f, 0xFF3B2414.toInt() to 0.3f))
        val c = HaloLight.lightColor(a)
        assertThat(c.c).isAtLeast(0.09f)
        assertThat(c.h).isIn(com.google.common.collect.Range.closed(35f, 75f))
        // Neutral covers get the lamp itself.
        val grey = CoverPalette.analyze(thumb(0xFF808080.toInt() to 1f))
        assertThat(HaloLight.lightColor(grey)).isEqualTo(HaloLight.LAMP)
        assertThat(HaloLight.LAMP.c).isAtMost(0.06f) // warm white, not an orange frame
    }

    @Test
    fun `covers are never shown larger than their resolution allows`() {
        assertThat(HaloLayout.coverDp(layout, 300)).isWithin(0.01f).of(165f)
        assertThat(HaloLayout.coverDp(layout, 640)).isWithin(0.01f).of(layout.slot) // 352 → capped at 320
        assertThat(HaloLayout.coverDp(layout, null)).isEqualTo(layout.slot)
        // 1.1 source pixels per 1080p pixel at most
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
