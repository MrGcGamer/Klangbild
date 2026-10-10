package io.github.hazymorning.klangbild.color

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class CoverPaletteTest {
    private fun solid(argb: Int, n: Int = 48 * 48) = IntArray(n) { argb }

    private fun mix(vararg parts: Pair<Int, Float>): IntArray {
        val out = ArrayList<Int>()
        for ((c, share) in parts) repeat((share * 2304).toInt()) { out += c }
        return out.toIntArray()
    }

    private val covers = listOf(
        solid(0xFF000000.toInt()),
        solid(0xFFFFFFFF.toInt()),
        solid(0xFF808080.toInt()),
        mix(0xFFFF00FF.toInt() to 0.5f, 0xFF00FFFF.toInt() to 0.5f),
        mix(0xFFEEE7D8.toInt() to 0.7f, 0xFFD8512E.toInt() to 0.2f, 0xFF2E5BA6.toInt() to 0.1f),
    )

    @Test
    fun `oklab round trip`() {
        for (c in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF2E5BA6.toInt(), 0xFF7F7F7F.toInt())) {
            assertThat(Oklab.fromArgb(c).toArgb()).isEqualTo(c)
        }
    }

    @Test
    fun `classifies edge cases`() {
        assertThat(CoverPalette.analyze(solid(0xFF070708.toInt())).kind).isEqualTo(CoverKind.DARK)
        assertThat(CoverPalette.analyze(solid(0xFF8A8A8A.toInt())).kind).isEqualTo(CoverKind.MONOCHROME)
        assertThat(CoverPalette.analyze(mix(0xFF0A0A0D.toInt() to 0.95f, 0xFFE9DDBA.toInt() to 0.05f)).kind)
            .isEqualTo(CoverKind.DARK)
        assertThat(CoverPalette.analyze(mix(0xFFF6F5F1.toInt() to 0.97f, 0xFF4060A0.toInt() to 0.03f)).kind)
            .isEqualTo(CoverKind.LIGHT)
        assertThat(CoverPalette.analyze(mix(0xFFFF00FF.toInt() to 0.5f, 0xFF00FFFF.toInt() to 0.5f)).kind)
            .isEqualTo(CoverKind.VIVID)
    }

    @Test
    fun `text stays readable on both walls for every edge case`() {
        for (pixels in covers) for (appearance in Appearance.entries) {
            for (lighting in listOf(Lighting.DAY, Lighting.EVENING, Lighting.NIGHT)) {
                val p = CoverPalette.derive(CoverPalette.analyze(pixels), lighting, appearance)
                val title = contrastRatio(p.ink, p.wall)
                val quiet = contrastRatio(p.inkQuiet, p.wall)
                if (lighting == Lighting.NIGHT) {
                    // at night everything is dimmer, but nothing is left out: every line must read
                    assertWithMessage("night title $p").that(title).isAtLeast(4.5f)
                } else {
                    assertWithMessage("title $p").that(title).isAtLeast(if (appearance == Appearance.DARK) 10f else 9f)
                }
                assertWithMessage("quiet $p $lighting").that(quiet).isAtLeast(4.5f)
            }
        }
    }

    @Test
    fun `the walls are black and warm paper, never white`() {
        for (pixels in covers) {
            val dark = CoverPalette.derive(CoverPalette.analyze(pixels), Lighting.DAY, Appearance.DARK)
            assertThat(dark.wall).isEqualTo(0xFF000000.toInt())
            val light = CoverPalette.derive(CoverPalette.analyze(pixels), Lighting.DAY, Appearance.LIGHT)
            val paper = Oklab.fromArgb(light.wall)
            assertThat(paper.l).isIn(com.google.common.collect.Range.closed(0.88f, 0.93f))
            // a hint of the cover's color at most
            assertThat(paper.chroma).isAtMost(0.02f)
            // ink never pure black
            assertThat(Oklab.fromArgb(light.ink).l).isAtLeast(0.2f)
        }
    }

    @Test
    fun `the light wall takes a hint of a warm cover's color and stays warm paper for a cool one`() {
        val red = CoverPalette.derive(CoverPalette.analyze(solid(0xFFB0412E.toInt())), Lighting.DAY, Appearance.LIGHT)
        val blue = CoverPalette.derive(CoverPalette.analyze(solid(0xFF2E5BA6.toInt())), Lighting.DAY, Appearance.LIGHT)
        val teal = CoverPalette.derive(CoverPalette.analyze(solid(0xFF1F7A78.toInt())), Lighting.DAY, Appearance.LIGHT)
        val grey = CoverPalette.derive(CoverPalette.analyze(solid(0xFF777777.toInt())), Lighting.DAY, Appearance.LIGHT)
        assertThat(red.lead).isNotNull()
        assertThat(grey.lead).isNull()
        assertThat(Oklab.fromArgb(red.wall).chroma).isGreaterThan(Oklab.fromArgb(grey.wall).chroma)
        // paper is never mint or blue: a cool cover gives it less color, not its hue
        for (p in listOf(red, blue, teal, grey)) {
            assertThat(Oklab.fromArgb(p.wall).hue).isIn(com.google.common.collect.Range.closed(45f, 95f))
        }
        assertThat(Oklab.fromArgb(blue.wall).chroma).isAtMost(Oklab.fromArgb(red.wall).chroma)
        assertThat(Oklab.fromArgb(grey.wall).hue).isWithin(20f).of(CoverPalette.WARM_HUE)
        // the Intensity setting turned off leaves plain paper
        val plain = CoverPalette.derive(CoverPalette.analyze(solid(0xFF2E5BA6.toInt())), Lighting.DAY, Appearance.LIGHT, wallTint = 0f)
        assertThat(Oklab.fromArgb(plain.wall).chroma).isAtMost(Oklab.fromArgb(grey.wall).chroma)
    }

    @Test
    fun `night is darker and warmer than day`() {
        val day = Lighting.fromSunElevation(40.0)
        val dusk = Lighting.fromSunElevation(2.0)
        val night = Lighting.fromSunElevation(-25.0)
        assertThat(day).isEqualTo(Lighting.DAY)
        assertThat(night).isEqualTo(Lighting.NIGHT)
        assertThat(dusk.brightness).isLessThan(day.brightness)
        assertThat(dusk.warmth).isGreaterThan(day.warmth)
        assertThat(night.brightness).isLessThan(dusk.brightness)
        val noon = Lighting.fromLocalTime(13, 0)
        val midnight = Lighting.fromLocalTime(0, 30)
        assertThat(noon.brightness).isGreaterThan(midnight.brightness)
    }

    @Test
    fun `the wall turns with the sun and holds steady near the horizon`() {
        assertThat(Appearance.bySun(30.0)).isEqualTo(Appearance.LIGHT)
        assertThat(Appearance.bySun(-6.0)).isEqualTo(Appearance.DARK)
        // sunset is at −0.833°: just above it is still day …
        assertThat(Appearance.bySun(-0.5)).isEqualTo(Appearance.LIGHT)
        // … and a value wobbling around the line does not flip the wall back and forth
        assertThat(Appearance.bySun(-0.6, previous = Appearance.DARK)).isEqualTo(Appearance.DARK)
        assertThat(Appearance.bySun(-1.0, previous = Appearance.LIGHT)).isEqualTo(Appearance.LIGHT)
        assertThat(Appearance.bySun(0.0, previous = Appearance.DARK)).isEqualTo(Appearance.LIGHT)
        // without Home Assistant the clock decides: day at noon, night at midnight
        assertThat(Appearance.bySun(Lighting.approximateElevation(12, 0))).isEqualTo(Appearance.LIGHT)
        assertThat(Appearance.bySun(Lighting.approximateElevation(23, 30))).isEqualTo(Appearance.DARK)
    }
}
