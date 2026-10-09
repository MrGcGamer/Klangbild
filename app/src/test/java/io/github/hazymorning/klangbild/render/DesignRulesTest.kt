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

    private fun forEachMapPixel(block: (x: Int, y: Int, xDp: Float, yDp: Float) -> Unit) {
        for (y in 0 until HaloLight.MAP_H) for (x in 0 until HaloLight.MAP_W) {
            block(x, y, (x + 0.5f) / HaloLight.MAP_W * screen.widthDp, (y + 0.5f) / HaloLight.MAP_H * screen.heightDp)
        }
    }

    /** Light of the pool [d] dp outside the print, straight out in direction [deg] (maths angle). */
    private fun lightOutside(pose: HaloLight.LampPose, deg: Float, d: Float): Float {
        val rad = Math.toRadians(deg.toDouble())
        val ux = kotlin.math.cos(rad).toFloat()
        val uy = -kotlin.math.sin(rad).toFloat()
        val boundary = shape.radius / maxOf(kotlin.math.abs(ux), kotlin.math.abs(uy))
        val r = boundary + d
        return HaloLight.lampAt(pose, shape.centerX + ux * r, shape.centerY + uy * r)
    }

    @Test
    fun `no light at all near the screen edges`() {
        for (mask in listOf(HaloLight.holeMask(shape, screen), HaloLight.openMask(screen), HaloLight.rimMap(shape, screen))) {
            forEachMapPixel { x, y, xDp, yDp ->
                val edge = minOf(xDp, screen.widthDp - xDp, yDp, screen.heightDp - yDp)
                if (edge < HaloLight.EDGE_CLEAR_DP - 1f) assertWithMessage("($x, $y)").that(grey(mask[y * HaloLight.MAP_W + x])).isEqualTo(0)
            }
        }
    }

    @Test
    fun `dark behind the print`() {
        val hole = HaloLight.holeMask(shape, screen)
        val rim = HaloLight.rimMap(shape, screen)
        forEachMapPixel { x, y, xDp, yDp ->
            if (shape.contains(xDp, yDp)) {
                assertWithMessage("hole ($x, $y)").that(grey(hole[y * HaloLight.MAP_W + x])).isEqualTo(0)
                assertWithMessage("rim ($x, $y)").that(grey(rim[y * HaloLight.MAP_W + x])).isEqualTo(0)
            }
        }
    }

    @Test
    fun `the far wall is pure black`() {
        assertThat(HaloLight.lampProfile(1f)).isEqualTo(0f)
        val size = 128
        val sprite = HaloLight.lampSprite(ditherLsb = 4f, seed = 1, size = size)
        for (i in 0 until size) {
            assertThat(grey(sprite[i])).isEqualTo(0) // top row
            assertThat(grey(sprite[i * size])).isEqualTo(0) // left column
        }
        // The side away from the sun gets nothing at all.
        val pose = HaloLight.lampPose(shape, 90f, screen)
        assertThat(lightOutside(pose, -90f, 0f)).isEqualTo(0f)
    }

    @Test
    fun `a round pool, brightest at the print's edge, about half at 25 dp, gone by 70 dp`() {
        val pose = HaloLight.lampPose(shape, 90f, screen)
        val edge = lightOutside(pose, 90f, 0f)
        assertThat(edge).isWithin(0.001f).of(HaloLight.EDGE_STRENGTH)
        // falls off steadily – no band, no ledge
        var last = edge
        for (d in 1..80) {
            val v = lightOutside(pose, 90f, d.toFloat())
            assertThat(v).isAtMost(last)
            last = v
        }
        // the profile is lightness: on the wall it is multiplied by the tint in sRGB
        val tint = Oklab.linearToSrgb(HaloLight.lampPeak(0.054f))
        fun luminance(v: Float) = Oklab.srgbToLinear(v * tint)
        assertThat(luminance(edge)).isWithin(0.001f).of(0.054f)
        assertThat(luminance(lightOutside(pose, 90f, 25f)) / luminance(edge)).isIn(Range.closed(0.38f, 0.55f))
        assertThat(luminance(lightOutside(pose, 90f, 50f)) / luminance(edge)).isLessThan(0.12f)
        // it ends softly: almost nothing just before the end
        assertThat(lightOutside(pose, 90f, 66f) / edge).isLessThan(0.03f)
        assertThat(lightOutside(pose, 90f, 70f)).isEqualTo(0f)
        // a pool, not a frame: the sides away from the sun get nothing
        assertThat(lightOutside(pose, 0f, 0f)).isEqualTo(0f)
    }

    @Test
    fun `the pool fits into the free wall, so the edge band never cuts it`() {
        for (mirrored in listOf(false, true)) {
            val l = HaloLayout(screen, mirrored = mirrored)
            val print = PrintShape(l.coverRect(l.slot))
            for (sun in 0 until 360 step 5) {
                val pose = HaloLight.lampPose(print, sun.toFloat(), screen)
                var y = 0f
                while (y < screen.heightDp) {
                    var x = 0f
                    while (x < screen.widthDp) {
                        val edge = minOf(x, screen.widthDp - x, y, screen.heightDp - y)
                        if (edge < HaloLight.EDGE_CLEAR_DP + HaloLight.EDGE_FADE_DP) {
                            assertWithMessage("sun $sun at ($x, $y)").that(HaloLight.lampAt(pose, x, y)).isEqualTo(0f)
                        }
                        x += 2f
                    }
                    y += 2f
                }
            }
        }
    }

    @Test
    fun `the lamp follows the sun, and the sun's side is clearly the lit one`() {
        for (sun in listOf(225f, 160f, 90f, 10f, -45f)) {
            val pose = HaloLight.lampPose(shape, sun, screen)
            val lit = lightOutside(pose, sun, 4f)
            assertThat(lit).isGreaterThan(0.5f)
            assertThat(lightOutside(pose, sun + 180f, 4f)).isEqualTo(0f)
            assertThat(lit / maxOf(lightOutside(pose, sun + 90f, 4f), 1e-6f)).isAtLeast(3.8f)
            assertThat(lit / maxOf(lightOutside(pose, sun - 90f, 4f), 1e-6f)).isAtLeast(3.8f)
            // the lamp stands behind the print, inside its outline
            assertThat(shape.contains(pose.x, pose.y)).isTrue()
        }
    }

    @Test
    fun `a dark print gets a faint rim all around, a bright one none`() {
        assertThat(HaloLight.rimLuminance(0.002f)).isWithin(1e-4f).of(0.008f)
        assertThat(HaloLight.rimLuminance(0.006f)).isWithin(1e-4f).of(0.012f)
        assertThat(HaloLight.rimLuminance(0.03f)).isAtMost(0.015f)
        assertThat(HaloLight.rimLuminance(0.2f)).isEqualTo(0f)
        val rim = HaloLight.rimMap(shape, screen)
        fun at(xDp: Float, yDp: Float): Float {
            val x = (xDp / screen.widthDp * HaloLight.MAP_W).toInt()
            val y = (yDp / screen.heightDp * HaloLight.MAP_H).toInt()
            return Oklab.srgbToLinear(grey(rim[y * HaloLight.MAP_W + x]))
        }
        // all four sides, fading out over about 20 dp
        for ((x, y) in listOf(shape.rect.left - 1.5f to shape.centerY, shape.rect.right + 1.5f to shape.centerY, shape.centerX to shape.rect.top - 1.5f, shape.centerX to shape.rect.bottom + 1.5f)) {
            assertThat(at(x, y)).isGreaterThan(0.6f)
        }
        assertThat(at(shape.rect.right + 22f, shape.centerY)).isLessThan(0.06f)
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
    fun `warm light is tungsten, never rust, and warmer than a neutral cover's`() {
        val a = CoverPalette.analyze(thumb(0xFFC2552A.toInt() to 0.7f, 0xFF3B2414.toInt() to 0.3f))
        val c = HaloLight.lightColor(a)
        assertThat(c.h).isIn(Range.closed(64f, 72f))
        // about 3300 K: chroma relative to lightness as in rgb(76, 55, 33)
        assertThat(c.c / c.l).isIn(Range.closed(0.08f, 0.13f))
        val grey = CoverPalette.analyze(thumb(0xFF808080.toInt() to 1f))
        val lamp = HaloLight.lightColor(grey)
        assertThat(lamp).isEqualTo(HaloLight.LAMP)
        assertThat(c.c / c.l).isAtLeast(lamp.c / lamp.l * 1.3f)
    }

    @Test
    fun `cool light is held back, so it never reads as an LED strip`() {
        val a = CoverPalette.analyze(thumb(0xFF2A1450.toInt() to 0.7f, 0xFF4B2C9A.toInt() to 0.3f))
        val c = HaloLight.lightColor(a)
        assertThat(c.c / c.l).isAtMost(0.1f)
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

    @Test
    fun `a small print gets a passe-partout that fills the slot`() {
        val print = IntArray(10 * 10) { 0xFFFF0000.toInt() }
        val out = SceneBuilder.matted(print, 10, 30, density = 2f)
        assertThat(out.size).isEqualTo(900)
        assertThat(out[15 * 30 + 15]).isEqualTo(0xFFFF0000.toInt()) // the print, centred
        // grained mat board
        assertThat((out[0] shr 16) and 0xFF).isIn(Range.closed(((SceneBuilder.MAT shr 16) and 0xFF) - 2, ((SceneBuilder.MAT shr 16) and 0xFF) + 2))
        assertThat(out[9 * 30 + 15]).isEqualTo(SceneBuilder.BEVEL) // the bevel just above the print
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
