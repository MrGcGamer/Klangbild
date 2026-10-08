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

    private fun assertReadable(p: ScenePalette) {
        assertWithMessage("ink $p").that(Oklab.contrastRatio(p.ink, p.base)).isAtLeast(10f)
        assertWithMessage("inkMuted $p").that(Oklab.contrastRatio(p.inkMuted, p.base)).isAtLeast(6f)
        assertWithMessage("accent $p").that(Oklab.contrastRatio(p.accent, p.base)).isAtLeast(4.5f)
        // never harsh white
        val ink = Oklab.fromArgb(p.ink)
        assertThat(ink.l).isAtMost(0.95f)
    }

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
    fun `palettes stay readable and tame for every edge case`() {
        val covers = listOf(
            solid(0xFF000000.toInt()),
            solid(0xFFFFFFFF.toInt()),
            solid(0xFF808080.toInt()),
            mix(0xFFFF00FF.toInt() to 0.5f, 0xFF00FFFF.toInt() to 0.5f),
            mix(0xFFEEE7D8.toInt() to 0.7f, 0xFFD8512E.toInt() to 0.2f, 0xFF2E5BA6.toInt() to 0.1f),
        )
        for (pixels in covers) {
            for (lighting in listOf(Lighting.DAY, Lighting.EVENING, Lighting.NIGHT)) {
                val p = CoverPalette.derive(CoverPalette.analyze(pixels), lighting)
                if (lighting == Lighting.NIGHT) {
                    // at night everything is dimmer, but text must still read
                    assertWithMessage("night ink $p").that(Oklab.contrastRatio(p.ink, p.base)).isAtLeast(4.5f)
                } else {
                    assertReadable(p)
                }
                // neon stays in check
                assertThat(Oklab.fromArgb(p.accent).chroma).isAtMost(0.13f)
                p.glow.forEach { assertThat(Oklab.fromArgb(it).chroma).isAtMost(0.14f) }
            }
        }
    }

    @Test
    fun `neutral covers get a warm brass accent, colourful ones their own hue`() {
        val grey = CoverPalette.derive(CoverPalette.analyze(solid(0xFF777777.toInt())))
        assertThat(grey.tintHue).isNull()
        val accentHue = Oklab.fromArgb(grey.accent).hue
        assertThat(accentHue).isWithin(15f).of(CoverPalette.WARM_HUE)

        val blue = CoverPalette.derive(CoverPalette.analyze(solid(0xFF2E5BA6.toInt())))
        assertThat(blue.tintHue).isNotNull()
        assertThat(Oklab.fromArgb(blue.accent).hue).isWithin(12f).of(Oklab.fromArgb(0xFF2E5BA6.toInt()).hue)
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
}
