package io.github.hazymorning.klangbild.lights

import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A colour as Hue lights take it: CIE 1931 xy (brightness goes separately). */
data class Xy(val x: Float, val y: Float) {
    fun distance(o: Xy): Float = kotlin.math.hypot(x - o.x, y - o.y)
}

/**
 * The screen's colours as light for the bars behind the TV. Home Assistant's own conversion
 * from RGB oversaturates and drops the intensity, so the colour goes as xy, worked out here.
 *
 * Light on a wall looks far paler than the same colour on a panel, and a soft colour that is
 * plain on the screen is next to white as light – a pale sky blue, a muted green: the light
 * takes the colour's hue at its purest (the edge of what a screen can show) and goes a good
 * part of the way from white towards it, further the more colourful the colour is. Only real
 * greys become white: a neutral one by day, warmer at night – never the amber of a 2700 K
 * bulb, which reads as orange. Very dark colours keep their hue but give it softly.
 */
object HueColor {
    /** Gamut C – the Hue Play's and most current Hue lights' (Home Assistant's own test data). */
    private val RED = Xy(0.6915f, 0.3083f)
    private val GREEN = Xy(0.17f, 0.70f)
    private val BLUE = Xy(0.1532f, 0.0475f)

    /** Below this chroma (Oklab) a colour is a grey: white light instead … */
    const val GREY_CHROMA = 0.012f

    /** … fully a colour from this one on. */
    const val COLOUR_CHROMA = 0.035f

    /** How far from white towards the hue's purest colour the light goes: at least … */
    const val SATURATION_MIN = 0.42f

    /** … plus this much per unit of chroma, up to [SATURATION_MAX]. */
    const val SATURATION_GAIN = 3.2f
    const val SATURATION_MAX = 0.92f

    /** The white for greys: by day (K) … and at night. */
    const val WHITE_DAY_K = 4_300f
    const val WHITE_NIGHT_K = 3_000f

    /** The colour for the light from [rgb] (linear sRGB, 0..1); [warmth] 0 by day … 1 at night. */
    fun of(rgb: FloatArray, warmth: Float): Xy {
        val lab = Oklab.fromLinear(rgb[0].coerceAtLeast(0f), rgb[1].coerceAtLeast(0f), rgb[2].coerceAtLeast(0f))
        val lch = lab.toLch()
        val white = whiteXy(WHITE_DAY_K + (WHITE_NIGHT_K - WHITE_DAY_K) * warmth.coerceIn(0f, 1f))
        val pure = pureXy(lch.h) ?: return white
        // Very dark colours give their hue softly: no glowing navy from a black cover.
        val reach = min(SATURATION_MIN + lch.c * SATURATION_GAIN, if (lch.l < DARK_L) DARK_SATURATION else SATURATION_MAX)
        val colour = clamp(Xy(white.x + (pure.x - white.x) * reach, white.y + (pure.y - white.y) * reach))
        // Greys become white, blended on the way to colour.
        val c = smoothstep(GREY_CHROMA, COLOUR_CHROMA, lch.c)
        return Xy(white.x + (colour.x - white.x) * c, white.y + (colour.y - white.y) * c)
    }

    /** xy of the most colourful colour of hue [h] (Oklab, °) a screen can show; null if there is none. */
    fun pureXy(h: Float): Xy? {
        var best: Lab? = null
        var bestC = -1f
        for (l in CUSP_LIGHTNESS) {
            val lab = Oklab.gamutMap(Lch(l, 0.4f, h).toLab())
            val c = lab.toLch().c
            if (c > bestC) {
                bestC = c
                best = lab
            }
        }
        return best?.let { xyOf(it) }
    }

    /** How bright the light may be for this colour (a share of the level): dark covers give less light. */
    fun dimFor(rgb: FloatArray): Float {
        val l = Oklab.fromLinear(rgb[0].coerceAtLeast(0f), rgb[1].coerceAtLeast(0f), rgb[2].coerceAtLeast(0f)).l
        return 0.5f + 0.5f * smoothstep(0.08f, 0.35f, l)
    }

    /** xy of an Oklab colour; null for black. */
    fun xyOf(lab: Lab): Xy? {
        val rgb = Oklab.toLinear(lab)
        val r = rgb[0].coerceIn(0f, 1f)
        val g = rgb[1].coerceIn(0f, 1f)
        val b = rgb[2].coerceIn(0f, 1f)
        val x = 0.4124f * r + 0.3576f * g + 0.1805f * b
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = 0.0193f * r + 0.1192f * g + 0.9505f * b
        val sum = x + y + z
        return if (sum <= 1e-6f) null else Xy(x / sum, y / sum)
    }

    /** White light of [kelvin] (1,667–25,000 K) on the Planckian locus (Kim et al.). */
    fun whiteXy(kelvin: Float): Xy {
        val t = kelvin.coerceIn(1_667f, 25_000f).toDouble()
        val x = if (t <= 4_000) {
            -0.2661239e9 / (t * t * t) - 0.2343589e6 / (t * t) + 0.8776956e3 / t + 0.179910
        } else {
            -3.0258469e9 / (t * t * t) + 2.1070379e6 / (t * t) + 0.2226347e3 / t + 0.240390
        }
        val y = when {
            t <= 2_222 -> -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683
            t <= 4_000 -> -0.9549476 * x * x * x - 1.37418593 * x * x + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x * x * x - 5.87338670 * x * x + 3.75112997 * x - 0.37001483
        }
        return Xy(x.toFloat(), y.toFloat())
    }

    /** [p] if the lights can show it, else the nearest colour they can. */
    fun clamp(p: Xy): Xy {
        if (inside(p)) return p
        return listOf(nearest(p, RED, GREEN), nearest(p, GREEN, BLUE), nearest(p, BLUE, RED)).minBy { it.distance(p) }
    }

    /** Home Assistant's brightness (1–255) from a share of full brightness; never below [FLOOR]. */
    fun brightness(share: Float): Int = max(FLOOR, (share * 255f).roundToInt()).coerceAtMost(255)

    /** The least brightness sent while on: Hue lights treat 1 as off. */
    const val FLOOR = 8

    private const val DARK_L = 0.18f
    private const val DARK_SATURATION = 0.45f

    /** Where to look for a hue's most colourful colour: blue peaks dark, yellow light. */
    private val CUSP_LIGHTNESS = floatArrayOf(0.45f, 0.55f, 0.65f, 0.75f, 0.85f, 0.95f)

    private fun cross(o: Xy, a: Xy, b: Xy) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun inside(p: Xy): Boolean {
        val d1 = cross(RED, GREEN, p)
        val d2 = cross(GREEN, BLUE, p)
        val d3 = cross(BLUE, RED, p)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    private fun nearest(p: Xy, a: Xy, b: Xy): Xy {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return Xy(a.x + dx * t, a.y + dy * t)
    }

    private fun smoothstep(e0: Float, e1: Float, v: Float): Float {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
