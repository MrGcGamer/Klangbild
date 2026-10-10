package io.github.hazymorning.klangbild.vista

import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** What the sky is doing right now – the only inputs the colours depend on. */
data class SkyState(
    /** The sun's elevation and azimuth (°, azimuth from north through east). */
    val sunElevation: Float,
    val sunAzimuth: Float,
    val moonElevation: Float = -30f,
    val moonAzimuth: Float = 180f,
    /** The moon's lit fraction, 0 (new) to 1 (full). */
    val moonFraction: Float = 0f,
    /** Cloud cover as a mood: 0 clear, about 0.4 a few clouds, 1 overcast. */
    val cover: Float = 0f,
    /** Where the year stands. */
    val season: SeasonMix = SeasonMix.SUMMER,
)

/** How much of each season colours the land now (the four add up to 1); they pass into each other over weeks. */
data class SeasonMix(val spring: Float, val summer: Float, val autumn: Float, val winter: Float) {
    companion object {
        val SPRING = SeasonMix(1f, 0f, 0f, 0f)
        val SUMMER = SeasonMix(0f, 1f, 0f, 0f)
        val AUTUMN = SeasonMix(0f, 0f, 1f, 0f)
        val WINTER = SeasonMix(0f, 0f, 0f, 1f)
    }
}

/**
 * One hour's printing plate, as a printer of colour woodcuts or a cutter of lit paper would
 * mix it: the sky's four colours, and one colour for each sheet of land, from the farthest
 * range (closest to the sky) to the near bank (the deepest) – a ramp on which every sheet is
 * one step. Plus the lights: sun, glow, snow, water, clouds.
 */
class Plate(
    val elevation: Float,
    val sky: IntArray,
    val land: IntArray,
    val waterFar: Int,
    val waterNear: Int,
    val snow: Int,
    val sunDisc: Int,
    val glow: Int,
    val glowAlpha: Float,
    val horizonGlow: Int,
    val horizonGlowAlpha: Float,
    val cloudLit: Int,
    val cloudShade: Int,
    val shadow: Int,
    val stars: Float,
) {
    companion object {
        /** The sheets of land in [land]'s order. */
        val LAND = listOf(
            SheetId.RANGE_FAR, SheetId.RANGE_NEAR, SheetId.SHORE, SheetId.WOOD_LEFT, SheetId.WOOD_RIGHT, SheetId.MEADOW, SheetId.FRONT_LEFT,
        )
    }
}

/** The colours of everything in the picture for one moment (sRGB ARGB ints) and where the light falls from. */
class PaperLook(
    /** The inks of every sheet; both near banks share [SheetId.FRONT_LEFT]'s. */
    val sheets: Map<SheetId, Inks>,
    val trunk: Inks,
    val crown: Inks,
    /** The broad-leaved trees among each wood's firs: base, second and third colour, shade. */
    val accents: Map<SheetId, Inks>,
    val cloud: Inks,
    val sunDisc: Int,
    /** How much of the sun's and the moon's discs shows: none behind an overcast sky. */
    val discAlpha: Float,
    val glow: Int,
    val glowAlpha: Float,
    val horizonGlow: Int,
    val horizonGlowAlpha: Float,
    val moonLit: Int,
    val moonDark: Int,
    val stars: Float,
    val shadow: Int,
    val shadowAlpha: Float,
    /** The sheets' shadows fall this far (dp) – away from the light, longer when it is low. */
    val shadowDx: Float,
    val shadowDy: Float,
    /** The light on the water under the sun or moon. */
    val glint: Int,
    val glintAlpha: Float,
    /**
     * The music's label on the near bank: title; artist and the bar's fill; everything else –
     * near-neutral with a breath of the bank's hue, each keeping 4.5:1 on it.
     */
    val label: IntArray,
    /** How bright the label's cover is: at night it dims with everything else. */
    val labelCover: Float,
) {
    fun inks(id: SheetId): Inks = sheets[id] ?: sheets.getValue(SheetId.FRONT_LEFT)
}

/**
 * The colours of the picture: a series of plates through the day – the morning's and the
 * evening's their own, the night shared – blended by the sun's elevation; the moon lights the
 * night, clouds mute everything, the seasons turn the woods and the meadow. Every sheet takes
 * its step of the plate's ramp; its foot leans towards the sheet behind it, as if light lay
 * between the papers.
 */
object PaperLight {
    private fun c(hex: Long) = hex.toInt()
    private fun ints(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    // --- the plates: sky (top, high, low, horizon) and land (far range … near bank) ------------

    private val NIGHT = Plate(
        -16f, ints(0xFF080E20, 0xFF0E1730, 0xFF16223F, 0xFF233353), ints(0xFF18233F, 0xFF141E37, 0xFF111A30, 0xFF0F172B, 0xFF0D1427, 0xFF0B1222, 0xFF05080F),
        waterFar = c(0xFF1A2744), waterNear = c(0xFF0C1427), snow = c(0xFF2E3C5E), sunDisc = c(0xFFFFE9C8), glow = c(0xFF8090C0), glowAlpha = 0f,
        horizonGlow = c(0xFF2A3560), horizonGlowAlpha = 0f, cloudLit = c(0xFF222E4A), cloudShade = c(0xFF161F38), shadow = c(0xFF03050C), stars = 1f,
    )
    private val MOONLIT = Plate(
        -16f, ints(0xFF0E1730, 0xFF152340, 0xFF1F3153, 0xFF2D4266), ints(0xFF2A3B5E, 0xFF233353, 0xFF1D2B49, 0xFF18243F, 0xFF16213A, 0xFF141D34, 0xFF090D1C),
        waterFar = c(0xFF2C3E61), waterNear = c(0xFF141E36), snow = c(0xFF6D7EA6), sunDisc = c(0xFFFFE9C8), glow = c(0xFF8090C0), glowAlpha = 0f,
        horizonGlow = c(0xFF2A3560), horizonGlowAlpha = 0f, cloudLit = c(0xFF4A5A80), cloudShade = c(0xFF26324E), shadow = c(0xFF04060E), stars = 0.55f,
    )
    private val BLUE_EVENING = Plate(
        -7.5f, ints(0xFF1C2553, 0xFF2E3A71, 0xFF55588D, 0xFF7B6E9D), ints(0xFF5B5D8F, 0xFF4D5183, 0xFF414575, 0xFF353A67, 0xFF2F335D, 0xFF2C3056, 0xFF13152D),
        waterFar = c(0xFF5B5C8D), waterNear = c(0xFF2E3260), snow = c(0xFF8384B4), sunDisc = c(0xFFFFD7B0), glow = c(0xFFB08AB8), glowAlpha = 0f,
        horizonGlow = c(0xFFC98A98), horizonGlowAlpha = 0.25f, cloudLit = c(0xFF6E6894), cloudShade = c(0xFF3E4274), shadow = c(0xFF0C0C24), stars = 0.35f,
    )
    private val DUSK = Plate(
        -3f, ints(0xFF2C3A77, 0xFF5D5D9B, 0xFFB889A5, 0xFFEFA68F), ints(0xFFA68BAF, 0xFF8A76A1, 0xFF6D628F, 0xFF544D7B, 0xFF47426D, 0xFF494367, 0xFF1D1935),
        waterFar = c(0xFFC28FA8), waterNear = c(0xFF5C5585), snow = c(0xFFC7ACCA), sunDisc = c(0xFFFFC08E), glow = c(0xFFF2A07E), glowAlpha = 0.2f,
        horizonGlow = c(0xFFFFA27A), horizonGlowAlpha = 0.5f, cloudLit = c(0xFFE7A4A4), cloudShade = c(0xFF7E6E9E), shadow = c(0xFF1A1236), stars = 0f,
    )
    private val SUNSET = Plate(
        1f, ints(0xFF4D5D9D, 0xFF9F8BB7, 0xFFF1A88B, 0xFFFFD099), ints(0xFFE7A893, 0xFFCA8C8D, 0xFFA27489, 0xFF755D7D, 0xFF624E6F, 0xFF6D5765, 0xFF2D2137),
        waterFar = c(0xFFF6C29F), waterNear = c(0xFF8F6F8E), snow = c(0xFFF8CABA), sunDisc = c(0xFFFFE3B8), glow = c(0xFFFFB880), glowAlpha = 0.55f,
        horizonGlow = c(0xFFFFB070), horizonGlowAlpha = 0.55f, cloudLit = c(0xFFFFC7A0), cloudShade = c(0xFFB386A6), shadow = c(0xFF24163A), stars = 0f,
    )
    private val GOLDEN = Plate(
        6.5f, ints(0xFF6C8DC1, 0xFFB2B8CE, 0xFFEFD1A7, 0xFFF8E1B3), ints(0xFFE6C9AE, 0xFFD4B096, 0xFFAE977E, 0xFF7A7458, 0xFF68644B, 0xFFA08C52, 0xFF36322A),
        waterFar = c(0xFFF2D8B3), waterNear = c(0xFF9E8384), snow = c(0xFFFCEAD8), sunDisc = c(0xFFFFF2D2), glow = c(0xFFFFD9A0), glowAlpha = 0.45f,
        horizonGlow = c(0xFFFFD49A), horizonGlowAlpha = 0.32f, cloudLit = c(0xFFFFEFD6), cloudShade = c(0xFFD6BBB4), shadow = c(0xFF2E2236), stars = 0f,
    )
    private val AFTERNOON = Plate(
        18f, ints(0xFF78A8D2, 0xFFA5C3DC, 0xFFD5E0E2, 0xFFEDE9DA), ints(0xFFCAD2D5, 0xFFABBCBF, 0xFF8DA49D, 0xFF668976, 0xFF567B68, 0xFF7E9C62, 0xFF294434),
        waterFar = c(0xFFD2DFE2), waterNear = c(0xFF7C9A9A), snow = c(0xFFF7F6F0), sunDisc = c(0xFFFFF8EA), glow = c(0xFFFFF1D8), glowAlpha = 0.32f,
        horizonGlow = c(0xFFF6EAD0), horizonGlowAlpha = 0.1f, cloudLit = c(0xFFFFFFFC), cloudShade = c(0xFFDCE4E6), shadow = c(0xFF1C3040), stars = 0f,
    )
    private val DAY = Plate(
        40f, ints(0xFF7EB1D7, 0xFFA6CAE3, 0xFFCEE1EB, 0xFFE4EEEE), ints(0xFFC2D4DE, 0xFFA2BECA, 0xFF85A8AC, 0xFF5D8C85, 0xFF4E7E74, 0xFF6D996A, 0xFF23443C),
        waterFar = c(0xFFC8DCE5), waterNear = c(0xFF6F98A2), snow = c(0xFFF5F8F9), sunDisc = c(0xFFFFFBF2), glow = c(0xFFFFF6E4), glowAlpha = 0.3f,
        horizonGlow = c(0xFFE8F0F2), horizonGlowAlpha = 0f, cloudLit = c(0xFFFFFFFF), cloudShade = c(0xFFDCE8EF), shadow = c(0xFF173348), stars = 0f,
    )
    private val FORENOON = Plate(
        18f, ints(0xFF77A7D4, 0xFFA2C1DF, 0xFFCFDFE8, 0xFFE6ECEC), ints(0xFFC6D2DB, 0xFFA6BAC6, 0xFF88A3A7, 0xFF5F8780, 0xFF507A70, 0xFF729A68, 0xFF26443A),
        waterFar = c(0xFFCFDEE6), waterNear = c(0xFF7698A2), snow = c(0xFFF6F7F8), sunDisc = c(0xFFFFFAF0), glow = c(0xFFFFF4E4), glowAlpha = 0.32f,
        horizonGlow = c(0xFFF0EEE6), horizonGlowAlpha = 0.08f, cloudLit = c(0xFFFFFFFF), cloudShade = c(0xFFDAE4EC), shadow = c(0xFF19324A), stars = 0f,
    )
    private val MORNING = Plate(
        6.5f, ints(0xFF7292C6, 0xFFAEBFDB, 0xFFE9D5DB, 0xFFF6E3D5), ints(0xFFD8D2DE, 0xFFBDBFD1, 0xFF9EA9B7, 0xFF6F898B, 0xFF5F7B7D, 0xFF869D77, 0xFF2B3D3D),
        waterFar = c(0xFFE2D9E0), waterNear = c(0xFF8E9DB0), snow = c(0xFFF4EEF2), sunDisc = c(0xFFFFF2DE), glow = c(0xFFFFE4C8), glowAlpha = 0.4f,
        horizonGlow = c(0xFFFFD8C0), horizonGlowAlpha = 0.3f, cloudLit = c(0xFFFFF4EE), cloudShade = c(0xFFD8CCDC), shadow = c(0xFF262A52), stars = 0f,
    )
    private val SUNRISE = Plate(
        1f, ints(0xFF5969A7, 0xFFA89BC5, 0xFFF0BEB1, 0xFFFFDCB9), ints(0xFFE3B5B5, 0xFFC89EAD, 0xFFA589A3, 0xFF776D8D, 0xFF675E81, 0xFF6D6777, 0xFF2B273F),
        waterFar = c(0xFFF4CDBC), waterNear = c(0xFF8F86A2), snow = c(0xFFFAD9D0), sunDisc = c(0xFFFFE6C4), glow = c(0xFFFFC6A0), glowAlpha = 0.48f,
        horizonGlow = c(0xFFFFB896), horizonGlowAlpha = 0.55f, cloudLit = c(0xFFFFD2C0), cloudShade = c(0xFFB59CC0), shadow = c(0xFF261C48), stars = 0f,
    )
    private val DAWN = Plate(
        -3f, ints(0xFF293973, 0xFF565E9B, 0xFFA78DB5, 0xFFE5A9A7), ints(0xFF9B8DB3, 0xFF8178A5, 0xFF676493, 0xFF4F4F7D, 0xFF434471, 0xFF454469, 0xFF1C1C37),
        waterFar = c(0xFFB39CBE), waterNear = c(0xFF555584), snow = c(0xFFC2B2D2), sunDisc = c(0xFFFFCCA8), glow = c(0xFFE8A6A0), glowAlpha = 0.18f,
        horizonGlow = c(0xFFF8A898), horizonGlowAlpha = 0.48f, cloudLit = c(0xFFD8A4B4), cloudShade = c(0xFF6E6C9E), shadow = c(0xFF16143A), stars = 0f,
    )
    private val BLUE_MORNING = Plate(
        -7.5f, BLUE_EVENING.sky.copyOf().also { it[3] = c(0xFF746FA2) }, BLUE_EVENING.land, BLUE_EVENING.waterFar, BLUE_EVENING.waterNear, BLUE_EVENING.snow,
        BLUE_EVENING.sunDisc, BLUE_EVENING.glow, 0f, c(0xFFB890B0), 0.22f, BLUE_EVENING.cloudLit, BLUE_EVENING.cloudShade, BLUE_EVENING.shadow, 0.35f,
    )

    private val evening = listOf(NIGHT, BLUE_EVENING, DUSK, SUNSET, GOLDEN, AFTERNOON, DAY)
    private val morning = listOf(NIGHT, BLUE_MORNING, DAWN, SUNRISE, MORNING, FORENOON, DAY)

    private val TRUNK = c(0xFF4A4038)

    /** The colours of everything for [s]. */
    fun look(s: SkyState): PaperLook {
        val moon = moonlight(s)
        val clear = plateFor(s.sunElevation, s.sunAzimuth).let { if (moon > 0f) blend(it, moonlitOf(it), moon) else it }
        val cover = s.cover.coerceIn(0f, 1f)
        val plate = if (cover > 0.01f) overcast(clear, cover) else clear
        val season = s.season

        fun inks(id: SheetId, index: Int): Inks {
            val base = seasoned(plate.land[index], id, season, plate)
            // The foot leans towards the sheet behind it (or the horizon's light, for the farthest).
            val behind = if (index == 0) plate.sky[3] else seasoned(plate.land[index - 1], Plate.LAND[index - 1], season, plate)
            val foot = when (id) {
                SheetId.MEADOW -> shift(base, -0.05f)
                SheetId.FRONT_LEFT -> base
                else -> mixOk(base, behind, 0.42f)
            }
            // The woods' firs (and the far shore's tree line) keep the evergreen of the hour, whatever
            // the season does to the slopes around them – darker in winter, against the snow; other
            // sheets get a lit paper edge.
            val evergreen = id == SheetId.WOOD_LEFT || id == SheetId.WOOD_RIGHT || id == SheetId.SHORE
            val edge = if (evergreen) shift(winterOnly(plate.land[index], season), -0.025f - 0.07f * season.winter) else shift(base, if (index <= 2) 0.035f else 0.05f)
            // Snow stays light on every sheet; nearer, it takes a little more of the sky's shade.
            val snow = mixOk(plate.snow, plate.sky[1], 0.03f * index)
            return Inks(base, foot, edge, snow)
        }

        val sheets = HashMap<SheetId, Inks>()
        Plate.LAND.forEachIndexed { i, id -> sheets[id] = inks(id, i) }
        sheets[SheetId.SKY] = Inks(plate.sky[0], plate.sky[1], plate.sky[2], plate.sky[3])
        sheets[SheetId.LAKE] = Inks(plate.waterFar, mixOk(plate.waterFar, plate.waterNear, 0.55f), plate.waterNear, plate.waterFar)

        val meadow = sheets.getValue(SheetId.MEADOW).base
        val day = smooth(2f, 12f, s.sunElevation) * (1f - 0.5f * cover)
        val crownColour = crownOf(season, sheets.getValue(SheetId.WOOD_RIGHT).base, day)
        // The blossom shows by day; towards night it sinks into the crown with everything else.
        val crown = Inks(crownColour, shift(crownColour, -0.07f), crownColour, mixOk(BLOSSOM, crownColour, 0.12f + 0.8f * (1f - day)))
        val trunk = mixOk(TRUNK, sheets.getValue(SheetId.FRONT_LEFT).base, 0.55f)
        val accents = listOf(SheetId.WOOD_LEFT, SheetId.WOOD_RIGHT).associateWith { id ->
            val evergreen = sheets.getValue(id).second
            val tones = ACCENTS.map { own -> accentOf(season, own, evergreen, day) }
            Inks(tones[0], tones[1], tones[2], shift(evergreen, -0.02f))
        }

        // Shadows fall away from the light: sideways by its azimuth, longer when it is low.
        val sunUp = smooth(-1.5f, 1.5f, s.sunElevation)
        val lightAz = if (sunUp > 0f) s.sunAzimuth else s.moonAzimuth
        val lightEl = if (sunUp > 0f) s.sunElevation else s.moonElevation
        val shadowAlpha = (if (sunUp > 0f) 0.3f * sunUp else 0.22f * moon) * (1f - 0.8f * cover)
        val side = sin(Math.toRadians((lightAz - 180f).toDouble())).toFloat() // −1 east (left) … +1 west (right)
        val low = 1f - smooth(8f, 50f, lightEl)
        val shadowDx = -side * (1.2f + 4.5f * low)
        val shadowDy = 1.6f + 1.8f * low

        val moonLit = mixOk(c(0xFFF6EED8), plate.sky[3], 0.06f + 0.4f * cover)
        // The moon's unlit part a breath lighter than the sky: the whole disc reads, as in a print.
        val moonDark = mixOk(plate.sky[1], moonLit, 0.16f)
        val glintAlpha = if (sunUp > 0f) 0.85f * sunUp * (1f - smooth(16f, 36f, s.sunElevation)) * (1f - cover) else 0.7f * moon * (1f - cover)

        val label = labelInks(sheets.getValue(SheetId.FRONT_LEFT).base, s.sunElevation)
        // Behind a thick sky the sun is only a brightness: its disc and its glow fade out, the moon's too.
        val veiled = Shapes.smoothstep(0.55f, 0.95f, cover)
        return PaperLook(
            discAlpha = 1f - veiled,
            label = label,
            labelCover = 1f - 0.3f * (1f - smooth(-8f, 2f, s.sunElevation)),
            sheets = sheets, trunk = Inks.flat(trunk), crown = crown, accents = accents,
            cloud = Inks(plate.cloudLit, plate.cloudShade, plate.cloudLit, plate.cloudLit),
            sunDisc = plate.sunDisc, glow = plate.glow, glowAlpha = plate.glowAlpha,
            horizonGlow = plate.horizonGlow, horizonGlowAlpha = plate.horizonGlowAlpha,
            moonLit = moonLit, moonDark = moonDark, stars = plate.stars * (1f - cover),
            shadow = plate.shadow, shadowAlpha = shadowAlpha, shadowDx = shadowDx, shadowDy = shadowDy,
            glint = if (sunUp > 0f) plate.sunDisc else moonLit, glintAlpha = glintAlpha,
        )
    }

    /** How brightly the moon lights the land, 0..1: its phase, its height, and the night around it. */
    fun moonlight(s: SkyState): Float {
        val up = smooth(-2f, 14f, s.moonElevation)
        val dark = 1f - smooth(-9f, -3f, s.sunElevation)
        return s.moonFraction.coerceIn(0f, 1f).pow(1.3f) * up * dark
    }

    /** The plate for the sun's [elevation]: the morning's before noon, the evening's after, blended around noon. */
    fun plateFor(elevation: Float, azimuth: Float): Plate {
        val m = along(morning, elevation)
        val ev = along(evening, elevation)
        return blend(m, ev, smooth(165f, 195f, azimuth))
    }

    /** The night's moonlit plate, used as far as the moon lights the land. */
    private fun moonlitOf(p: Plate): Plate = if (p.elevation <= NIGHT.elevation) MOONLIT else blend(p, MOONLIT, 1f - smooth(-9f, -3f, p.elevation))

    private fun along(series: List<Plate>, elevation: Float): Plate {
        if (elevation <= series.first().elevation) return series.first()
        if (elevation >= series.last().elevation) return series.last()
        for (i in 0 until series.size - 1) {
            val a = series[i]
            val b = series[i + 1]
            if (elevation <= b.elevation) {
                val t = (elevation - a.elevation) / (b.elevation - a.elevation)
                return blend(a, b, t * t * (3 - 2 * t))
            }
        }
        return series.last()
    }

    private fun blend(a: Plate, b: Plate, t: Float): Plate {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun m(x: Int, y: Int) = mixOk(x, y, t)
        fun f(x: Float, y: Float) = x + (y - x) * t
        return Plate(
            f(a.elevation, b.elevation), IntArray(a.sky.size) { m(a.sky[it], b.sky[it]) }, IntArray(a.land.size) { m(a.land[it], b.land[it]) },
            m(a.waterFar, b.waterFar), m(a.waterNear, b.waterNear), m(a.snow, b.snow), m(a.sunDisc, b.sunDisc), m(a.glow, b.glow),
            f(a.glowAlpha, b.glowAlpha), m(a.horizonGlow, b.horizonGlow), f(a.horizonGlowAlpha, b.horizonGlowAlpha), m(a.cloudLit, b.cloudLit),
            m(a.cloudShade, b.cloudShade), m(a.shadow, b.shadow), f(a.stars, b.stars),
        )
    }

    /**
     * Clouds over everything: the colours give way to soft greys of the same hour, the values
     * draw together, the glows and the sun's warmth fade – a quieter print, never a darker one.
     */
    private fun overcast(p: Plate, cover: Float): Plate {
        fun mute(argb: Int, toward: Float): Int {
            val lab = Oklab.fromArgb(argb)
            val l = lab.l + (toward - lab.l) * 0.3f * cover
            val k = 1f - 0.65f * cover
            return Lab(l, lab.a * k, lab.b * k).toArgb()
        }
        val skyMid = Oklab.fromArgb(p.sky[2]).l
        val landMid = Oklab.fromArgb(p.land[3]).l
        return Plate(
            p.elevation, IntArray(p.sky.size) { mute(p.sky[it], skyMid) }, IntArray(p.land.size) { mute(p.land[it], landMid) },
            mute(p.waterFar, skyMid), mute(p.waterNear, landMid), mute(p.snow, skyMid), mute(p.sunDisc, skyMid), p.glow, p.glowAlpha * (1f - 0.8f * cover),
            p.horizonGlow, p.horizonGlowAlpha * (1f - 0.75f * cover), mute(p.cloudLit, skyMid), mute(p.cloudShade, skyMid), p.shadow, p.stars,
        )
    }

    /**
     * The season's turn on a sheet: spring makes the woods and the meadow fresher; autumn turns
     * the meadow to straw and only warms the woods a little (firs, most of them – the broad
     * tree carries the season's colour); winter cools and quietens everything. Always within
     * the hour's light, so night stays night.
     */
    private fun seasoned(argb: Int, id: SheetId, s: SeasonMix, p: Plate): Int {
        // The season's colours show by daylight; in the blue hour and at night they fade into the hour's own.
        val light = 0.15f + 0.85f * smooth(-6f, 8f, p.elevation)
        var out = Oklab.fromArgb(argb)
        val woods = id == SheetId.WOOD_LEFT || id == SheetId.WOOD_RIGHT
        val meadow = id == SheetId.MEADOW
        val green = woods || meadow || id == SheetId.SHORE || id == SheetId.FRONT_LEFT
        if (green && s.autumn > 0f) {
            val lch = out.toLch()
            out = if (meadow) {
                val target = Lch(lch.l + 0.035f, max(lch.c, 0.02f + 0.06f * light), 86f).toLab()
                out.lerp(target, s.autumn * light * 0.8f)
            } else {
                turned(out, 95f, s.autumn * light * if (woods) 0.15f else if (id == SheetId.SHORE) 0.2f else 0.1f)
            }
        }
        if (green && s.spring > 0f) {
            val lch = out.toLch()
            val target = Lch(lch.l + 0.025f, lch.c * 1.2f + 0.01f, 128f).toLab()
            out = out.lerp(target, s.spring * light * if (meadow) 0.6f else 0.3f)
        }
        if (s.winter > 0f) {
            // The near banks keep their dark colour (and the label its ground); the rest goes cool and quiet.
            val k = s.winter * if (id == SheetId.FRONT_LEFT) 0.4f else 1f
            val o = out.toLch()
            out = Lch(o.l, o.c * (1f - 0.45f * k), o.h + hueStep(o.h, 235f) * 0.35f * k).toLab()
        }
        return out.toArgb()
    }

    /** [lab]'s hue turned towards [hue] by the share [k] along the shorter way; lightness and chroma kept. */
    private fun turned(lab: Lab, hue: Float, k: Float): Lab {
        val o = lab.toLch()
        return Lch(o.l, o.c, o.h + hueStep(o.h, hue) * k.coerceIn(0f, 1f)).toLab()
    }

    /** The signed shorter way from hue [from] to hue [to] (degrees). */
    private fun hueStep(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /** Only winter's cooling, for what stays green all year (the firs). */
    private fun winterOnly(argb: Int, s: SeasonMix): Int {
        if (s.winter <= 0f) return argb
        val o = Oklab.fromArgb(argb).toLch()
        return Lch(o.l, o.c * (1f - 0.15f * s.winter), o.h + hueStep(o.h, 235f) * 0.15f * s.winter).toArgb()
    }

    /**
     * The broad tree's crown for the season: its own colour by daylight ([day] 1), sinking
     * into the hour's colour of the woods behind it towards evening and night ([day] 0).
     */
    private fun crownOf(s: SeasonMix, woods: Int, day: Float): Int {
        val own = seasonal(s, CROWN_SPRING, CROWN_SUMMER, CROWN_AUTUMN, CROWN_WINTER)
        return mixOk(woods, own, 0.12f + 0.78f * day)
    }

    /**
     * One of the colours the broad-leaved trees among the firs take: beside the evergreen in
     * summer, gold, orange or rust in autumn, fresh in spring – by daylight; towards night the
     * evergreen of the hour.
     */
    private fun accentOf(s: SeasonMix, own: IntArray, evergreen: Int, day: Float): Int {
        val colour = seasonal(s, own[0], own[1], own[2], own[3])
        val summerLike = s.summer + s.winter
        // Summer's broad-leaved trees are only a little lighter than the firs around them.
        val t = (0.1f + 0.75f * day) * (1f - 0.55f * summerLike)
        return mixOk(evergreen, colour, t)
    }

    /** The four seasons' colours, mixed in Oklab by [s]. */
    private fun seasonal(s: SeasonMix, spring: Int, summer: Int, autumn: Int, winter: Int): Int {
        val a = Oklab.fromArgb(spring)
        val b = Oklab.fromArgb(summer)
        val c = Oklab.fromArgb(autumn)
        val d = Oklab.fromArgb(winter)
        return Lab(
            a.l * s.spring + b.l * s.summer + c.l * s.autumn + d.l * s.winter,
            a.a * s.spring + b.a * s.summer + c.a * s.autumn + d.a * s.winter,
            a.b * s.spring + b.b * s.summer + c.b * s.autumn + d.b * s.winter,
        ).toArgb()
    }

    private val CROWN_SPRING = c(0xFF9CC47A)
    private val CROWN_SUMMER = c(0xFF5E8F55)
    private val CROWN_AUTUMN = c(0xFFC8682E)
    private val CROWN_WINTER = c(0xFF7A6E66)
    private val BLOSSOM = c(0xFFF4CCD8)

    /** The broad-leaved trees' three colours through the year: spring, summer, autumn, winter each. */
    private val ACCENTS = listOf(
        ints(0xFFA6CC7E, 0xFF6E9A62, 0xFFE0A33C, 0xFF6F7A74),
        ints(0xFFEFE2EA, 0xFF79A06A, 0xFFD5762F, 0xFF6F7A74),
        ints(0xFF8CBF84, 0xFF638F60, 0xFFB24A2C, 0xFF6F7A74),
    )

    /**
     * The label's three inks on the near bank [ground]: like white at 93 / 80 / 66.5 % (a little
     * lower at night, so the text does not outshine the picture), with a breath of the bank's
     * hue – and never darker than keeps 4.5:1 on it, the steps between the levels kept.
     */
    fun labelInks(ground: Int, sunElevation: Float): IntArray {
        val g = Oklab.fromArgb(ground).toLch()
        val night = 1f - smooth(-8f, 0f, sunElevation)
        val t = 1f - 0.1f * night
        val levels = floatArrayOf(0.93f * t, 0.8f * t, 0.665f * t)
        val floor = darkestLightInkOn(ground)
        val chroma = minOf(0.009f, g.c * 0.15f + 0.002f)
        return IntArray(3) { i ->
            val l = maxOf(levels[i], CoverPalette.NIGHT_INK_FLOOR[i], floor + 0.1f * (2 - i)).coerceAtMost(0.97f)
            Lch(l, chroma, g.h).toArgb()
        }
    }

    /** The darkest light, near-neutral ink (Oklab L) that keeps 4.5:1 (with a little margin) on [ground]. */
    fun darkestLightInkOn(ground: Int): Float {
        val y = relativeLuminance(ground)
        return Math.cbrt((4.6 * (y + 0.05) - 0.05).coerceAtLeast(0.0)).toFloat()
    }

    /** WCAG relative luminance of an sRGB colour. */
    fun relativeLuminance(argb: Int): Float =
        0.2126f * Oklab.srgbToLinear(argb shr 16) + 0.7152f * Oklab.srgbToLinear(argb shr 8) + 0.0722f * Oklab.srgbToLinear(argb)

    /** WCAG contrast ratio of two colours. */
    fun contrast(a: Int, b: Int): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    // --- colour helpers -------------------------------------------------------------------

    fun mixOk(x: Int, y: Int, t: Float): Int = Oklab.fromArgb(x).lerp(Oklab.fromArgb(y), t.coerceIn(0f, 1f)).toArgb()

    /** Lighter (positive) or darker by [d] in Oklab lightness. */
    private fun shift(argb: Int, d: Float): Int = Oklab.fromArgb(argb).let { Lab((it.l + d).coerceIn(0f, 1f), it.a, it.b).toArgb() }

    private fun smooth(e0: Float, e1: Float, x: Float): Float = Shapes.smoothstep(e0, e1, x)
}
