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
 * The cover's colours as light for the bars behind the TV. Home Assistant's own conversion
 * from RGB oversaturates and drops the intensity, so the colour goes as xy, worked out here:
 * linear sRGB → XYZ → xy, pulled into the lights' gamut; a little more vivid than on the
 * screen (light on a wall looks paler than the same colour on a panel); greys become a warm
 * white (warmer in the evening) instead of a cold blue-ish grey, and very dark colours keep
 * their hue but little of their colour.
 */
object HueColor {
    /** Gamut C – the Hue Play's and most current Hue lights' (Home Assistant's own test data). */
    private val RED = Xy(0.6915f, 0.3083f)
    private val GREEN = Xy(0.17f, 0.70f)
    private val BLUE = Xy(0.1532f, 0.0475f)

    /** Below this chroma (Oklab) a colour is a grey: warm white instead … */
    const val GREY_CHROMA = 0.03f

    /** … fully a colour from this one on. */
    const val COLOUR_CHROMA = 0.08f

    /** How much more vivid the light is than the colour it comes from. */
    const val VIVID = 1.3f

    /** The colour for the light from [rgb] (linear sRGB, 0..1); [warmth] is the hour's (0 by day, ~0.8 at night). */
    fun of(rgb: FloatArray, warmth: Float): Xy {
        val lab = Oklab.fromLinear(rgb[0].coerceAtLeast(0f), rgb[1].coerceAtLeast(0f), rgb[2].coerceAtLeast(0f))
        val lch = lab.toLch()
        val white = whiteXy(if (warmth > 0.3f) 2_300f else 2_700f)
        // Very dark colours keep their hue but give it softly: no glowing navy from a black cover.
        val chroma = min(lch.c * VIVID, if (lch.l < DARK_L) DARK_CHROMA else MAX_CHROMA)
        val vivid = Oklab.gamutMap(Lch(max(lch.l, 0.45f), chroma, lch.h).toLab())
        val colour = clamp(xyOf(vivid) ?: white)
        // Greys become warm white, blended on the way to colour.
        val c = smoothstep(GREY_CHROMA, COLOUR_CHROMA, lch.c)
        return Xy(white.x + (colour.x - white.x) * c, white.y + (colour.y - white.y) * c)
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

    private const val DARK_L = 0.2f
    private const val DARK_CHROMA = 0.05f
    private const val MAX_CHROMA = 0.25f

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
