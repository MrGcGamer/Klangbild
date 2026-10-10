package io.github.hazymorning.klangbild.vista

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.color.Oklab
import org.junit.Test
import kotlin.math.abs

class PaperLightTest {
    private val seasons = listOf(SeasonMix.SPRING, SeasonMix.SUMMER, SeasonMix.AUTUMN, SeasonMix.WINTER)

    @Test
    fun `the label keeps 4,5 to 1 on its bank in every light, sky and season`() {
        var worst = Float.MAX_VALUE
        for (season in seasons) for (cover in listOf(0f, 0.45f, 1f)) for (az in listOf(100f, 180f, 260f)) {
            var el = -24f
            while (el <= 62f) {
                for (moon in listOf(0f, 1f)) {
                    val look = PaperLight.look(SkyState(el, az, moonElevation = 30f, moonAzimuth = 170f, moonFraction = moon, cover = cover, season = season))
                    val bank = look.inks(SheetId.FRONT_LEFT).base
                    for (ink in look.label) worst = minOf(worst, PaperLight.contrast(ink, bank))
                }
                el += 0.5f
            }
        }
        assertThat(worst).isAtLeast(4.5f)
    }

    @Test
    fun `the label's levels stay apart`() {
        for (el in listOf(-20f, -4f, 2f, 20f, 50f)) {
            val l = PaperLight.look(SkyState(el, 200f)).label.map { Oklab.fromArgb(it).l }
            assertThat(l[0] - l[1]).isAtLeast(0.09f)
            assertThat(l[1] - l[2]).isAtLeast(0.09f)
        }
    }

    @Test
    fun `the light never jumps as the sun moves`() {
        // The colours are worked out anew every ten seconds of the sky's time; the sun climbs at
        // most a quarter of a degree a minute. A twentieth of a degree is one or two of those
        // steps: each changes no colour by more than two levels of 255 – nothing to see.
        for (az in listOf(95f, 180f, 265f)) {
            var prev = PaperLight.look(SkyState(-24f, az))
            var el = -23.95f
            while (el <= 62f) {
                val next = PaperLight.look(SkyState(el, az))
                for (id in listOf(SheetId.SKY, SheetId.RANGE_FAR, SheetId.WOOD_LEFT, SheetId.MEADOW, SheetId.FRONT_LEFT)) {
                    assertWithMessage("$id at $el° (azimuth $az°)").that(step(prev.inks(id).base, next.inks(id).base)).isAtMost(2)
                }
                prev = next
                el += 0.05f
            }
        }
    }

    @Test
    fun `morning and evening pass into each other around noon without a jump`() {
        var prev = PaperLight.look(SkyState(30f, 150f))
        var az = 150.5f
        while (az <= 210f) {
            val next = PaperLight.look(SkyState(30f, az))
            assertThat(step(prev.inks(SheetId.SKY).third, next.inks(SheetId.SKY).third)).isAtMost(4)
            prev = next
            az += 0.5f
        }
    }

    @Test
    fun `night stays night, and the moon lights it a little`() {
        val dark = PaperLight.look(SkyState(-25f, 0f))
        val moonlit = PaperLight.look(SkyState(-25f, 0f, moonElevation = 35f, moonAzimuth = 180f, moonFraction = 1f))
        val l = Oklab.fromArgb(dark.inks(SheetId.MEADOW).base).l
        val lm = Oklab.fromArgb(moonlit.inks(SheetId.MEADOW).base).l
        assertThat(l).isLessThan(0.3f)
        assertThat(lm).isGreaterThan(l)
        assertThat(lm).isLessThan(0.4f)
        assertThat(dark.stars).isGreaterThan(0.9f)
    }

    @Test
    fun `overcast mutes the colours without darkening the picture`() {
        val clear = PaperLight.look(SkyState(30f, 200f))
        val grey = PaperLight.look(SkyState(30f, 200f, cover = 1f))
        val c = Oklab.fromArgb(clear.inks(SheetId.MEADOW).base)
        val g = Oklab.fromArgb(grey.inks(SheetId.MEADOW).base)
        assertThat(g.chroma).isLessThan(c.chroma * 0.6f)
        assertThat(abs(g.l - c.l)).isLessThan(0.08f)
    }

    @Test
    fun `autumn colours the broad-leaved trees and the meadow by day`() {
        val summer = PaperLight.look(SkyState(25f, 200f, season = SeasonMix.SUMMER))
        val autumn = PaperLight.look(SkyState(25f, 200f, season = SeasonMix.AUTUMN))
        fun hue(argb: Int) = Oklab.fromArgb(argb).hue
        // The crown turns from green to the warm side; the firs' evergreen stays.
        assertThat(hue(autumn.crown.base)).isLessThan(80f)
        assertThat(hue(summer.crown.base)).isGreaterThan(110f)
        assertThat(step(summer.inks(SheetId.WOOD_LEFT).second, autumn.inks(SheetId.WOOD_LEFT).second)).isAtMost(3)
    }

    private fun step(a: Int, b: Int): Int =
        maxOf(abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)), abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF)))
}
