package io.github.hazymorning.klangbild.lights

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import io.github.hazymorning.klangbild.render.CoverField
import io.github.hazymorning.klangbild.render.FieldScene
import io.github.hazymorning.klangbild.vista.PaperLight
import io.github.hazymorning.klangbild.vista.SkyState
import org.junit.Test
import kotlin.math.sqrt

/** Three bars, three colours: from the cover, and in the Fensterblick from its light. */
class BarColoursTest {
    private fun field(vararg colours: Int): FieldScene {
        val n = 48
        // The colours side by side, from left to right.
        val thumb = IntArray(n * n) { i -> colours[(i % n) * colours.size / n] }
        return CoverField.build(thumb, n, CoverPalette.analyze(thumb), Lighting.DAY, Appearance.DARK, 1f, layout = null)
    }

    private fun lab(rgb: FloatArray): Lab = Oklab.fromLinear(rgb[0].coerceAtLeast(0f), rgb[1].coerceAtLeast(0f), rgb[2].coerceAtLeast(0f))

    private fun distance(a: Lab, b: Lab) = sqrt((a.l - b.l) * (a.l - b.l) + (a.a - b.a) * (a.a - b.a) + (a.b - b.b) * (a.b - b.b))

    private fun bars(c: BarColours, t: Double = 0.0) = BarSlot.entries.map { s -> FloatArray(3).also { c.colour(s, t, it) } }

    @Test
    fun `every bar shows a colour of its own, placed as it sits in the cover`() {
        val red = 0xFFC8321E.toInt()
        val blue = 0xFF1E46B4.toInt()
        val yellow = 0xFFE0B020.toInt()
        val (l, c, r) = bars(CoverBars(field(red, yellow, blue), moving = false)).map(::lab)
        for ((a, b) in listOf(l to c, c to r, l to r)) assertThat(distance(a, b)).isGreaterThan(0.09f)
        // Red sits on the left of the cover, blue on its right.
        assertWithMessage("left bar red").that(l.a).isGreaterThan(0.08f)
        assertWithMessage("right bar blue").that(r.b).isLessThan(-0.08f)
    }

    @Test
    fun `a cover of one colour gives that colour and two of its neighbours, a grey one three whites`() {
        val teal = bars(CoverBars(field(0xFF1E8C8C.toInt()), moving = false)).map(::lab)
        for ((a, b) in teal.zipWithNext()) assertThat(distance(a, b)).isGreaterThan(0.04f)
        val grey = bars(CoverBars(field(0xFF808080.toInt()), moving = false)).map(::lab)
        for (g in grey) assertThat(sqrt(g.a * g.a + g.b * g.b)).isLessThan(0.05f)
    }

    @Test
    fun `with Bewegung the colours move on from bar to bar, resting in between`() {
        val bars = CoverBars(field(0xFFC8321E.toInt(), 0xFFE0B020.toInt(), 0xFF1E46B4.toInt()), moving = true)
        val start = bars(bars, 0.0).map(::lab)
        // Resting: a little later nothing has changed.
        val rest = bars(bars, CoverBars.ROTATE_S * 0.5).map(::lab)
        for ((a, b) in start.zip(rest)) assertThat(distance(a, b)).isLessThan(0.001f)
        // A whole step later each bar shows the colour its neighbour showed.
        val next = bars(bars, CoverBars.ROTATE_S).map(::lab)
        assertThat(distance(next[0], start[1])).isLessThan(0.001f)
        assertThat(distance(next[1], start[2])).isLessThan(0.001f)
    }

    @Test
    fun `the Fensterblick's light - the sun's glow on its side at dusk, the fire's warmth on its side at night`() {
        // Sunset in the west: to the right of a window looking south.
        val dusk = bars(VistaBars(PaperLight.look(SkyState(sunElevation = 1f, sunAzimuth = 260f)), fireOnRight = true)).map(::lab)
        assertWithMessage("the sun's side warmer").that(dusk[2].b).isGreaterThan(dusk[0].b + 0.02f)
        // Night, the fire on the left: that bar is the warm one, the sky above blue.
        val night = bars(VistaBars(PaperLight.look(SkyState(sunElevation = -30f, sunAzimuth = 0f)), fireOnRight = false)).map(::lab)
        assertWithMessage("the fire's side warm").that(night[0].b).isGreaterThan(night[2].b + 0.02f)
        assertWithMessage("the sky above blue").that(night[1].b).isLessThan(0f)
    }

    @Test
    fun `at noon the Fensterblick gives three colours of its own - sky, clearing, mountains - none of them amber`() {
        val noon = bars(VistaBars(PaperLight.look(SkyState(sunElevation = 40f, sunAzimuth = 180f)), fireOnRight = true))
        val labs = noon.map(::lab)
        for ((a, b) in listOf(labs[0] to labs[1], labs[1] to labs[2], labs[0] to labs[2])) assertThat(distance(a, b)).isGreaterThan(0.05f)
        // As light: each clearly a colour, and the sky above the TV blue.
        val amber = HueColor.whiteXy(2700f)
        for (c in noon) assertThat(HueColor.of(c, 0f).distance(amber)).isGreaterThan(0.06f)
        val sky = HueColor.of(noon[1], 0f)
        assertWithMessage("the sky's light blue").that(sky.y).isLessThan(0.3f)
    }
}
