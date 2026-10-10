package io.github.hazymorning.klangbild.vista

import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.abs
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
 * One hour's light, as a lighting designer would set it over a paper diorama: the sky's wash
 * (zenith, middle, horizon), the colour of the direct light ([key], factors per channel) and of
 * the sky's light ([fill]), how much light there is ([level]), how strongly the folds show
 * ([contrast]), how much the far sheets take of the horizon's colour ([haze]); the shadows, the
 * sun and its glows, the stars, and how much the campfire lights its clearing ([fire]).
 */
class Plate(
    val elevation: Float,
    val sky: IntArray,
    val key: FloatArray,
    val fill: FloatArray,
    val level: Float,
    val contrast: Float,
    val haze: Float,
    val shadow: Int,
    val shadowAlpha: Float,
    val sunDisc: Int,
    val glow: Int,
    val glowAlpha: Float,
    val horizonGlow: Int,
    val horizonGlowAlpha: Float,
    val stars: Float,
    val fire: Float,
)

/** The colours of everything in the picture for one moment (sRGB ARGB ints) and where the light falls from. */
class PaperLook(
    /** The inks of every sheet; both near banks share one set. */
    val sheets: Map<SheetId, Inks>,
    val cloud: Inks,
    /** The flames: yellow, orange, red – brighter at night. */
    val flame: Inks,
    val sunDisc: Int,
    /** How much of the sun's and the moon's discs shows: none behind an overcast sky. */
    val discAlpha: Float,
    val glow: Int,
    val glowAlpha: Float,
    val horizonGlow: Int,
    val horizonGlowAlpha: Float,
    val moonLit: Int,
    val moonDark: Int,
    /** How much of the moon shows: its lit part (faint by day), and its dark disc (only at night). */
    val moonAlpha: Float,
    val moonDarkAlpha: Float,
    val stars: Float,
    val shadow: Int,
    val shadowAlpha: Float,
    /** Where the shadows fall: −1 to the left … 1 to the right; and how long they are (about 1). */
    val shadowSide: Float,
    val shadowLength: Float,
    /** How strongly the campfire lights the clearing (0..1), the colour of its light and how far it reaches (dp). */
    val fire: Float,
    val fireGlow: Int,
    val fireReach: Float,
    /**
     * The music's label on the near bank: title; artist and the bar's fill; everything else –
     * near-neutral with a breath of the bank's hue, each keeping 4.5:1 on it.
     */
    val label: IntArray,
    /** How bright the label's cover is: at night it dims with everything else. */
    val labelCover: Float,
) {
    fun inks(id: SheetId): Inks = sheets[id] ?: sheets.getValue(SheetId.FRONT_LEFT)

    /** The near bank's own colour, where the label lies. */
    val bank: Int get() = inks(SheetId.FRONT_LEFT).mix(0.5f, 0f)
}

/**
 * The colours of the diorama: a series of plates through the day – the morning's and the
 * evening's their own, the night shared – blended by the sun's elevation; the moon lights the
 * night, clouds mute everything. Every sheet is a paper of the season's colour under that
 * light: the faces turned towards the sun (or the moon) lit, the others in the sky's light, the
 * far ones veiled a little by the horizon's colour.
 */
object PaperLight {
    private fun c(hex: Long) = hex.toInt()
    private fun ints(vararg v: Long) = IntArray(v.size) { v[it].toInt() }
    private fun f(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    // --- the plates ----------------------------------------------------------------------------

    private val NIGHT = Plate(
        -16f, ints(0xFF0A1124, 0xFF142040, 0xFF22355A), f(0.55f, 0.63f, 0.9f), f(0.34f, 0.42f, 0.66f), 0.52f, 0.12f, 0.6f,
        c(0xFF05070F), 0.14f, c(0xFFFFE9C8), c(0xFF8090C0), 0f, c(0xFF2A3560), 0f, stars = 1f, fire = 0.62f,
    )
    private val MOONLIT = Plate(
        -16f, ints(0xFF0E1730, 0xFF1A2A4C, 0xFF2E4468), f(0.68f, 0.78f, 1.06f), f(0.38f, 0.46f, 0.7f), 0.62f, 0.2f, 0.6f,
        c(0xFF04060E), 0.22f, c(0xFFFFE9C8), c(0xFF8090C0), 0f, c(0xFF2A3560), 0f, stars = 0.55f, fire = 0.56f,
    )
    private val BLUE_EVENING = Plate(
        -7.5f, ints(0xFF1C2553, 0xFF3C4680, 0xFF7B6E9D), f(0.66f, 0.64f, 0.88f), f(0.44f, 0.46f, 0.72f), 0.52f, 0.08f, 0.8f,
        c(0xFF0C0C24), 0.1f, c(0xFFFFD7B0), c(0xFFB08AB8), 0f, c(0xFFC98A98), 0.25f, stars = 0.35f, fire = 0.5f,
    )
    private val DUSK = Plate(
        -3f, ints(0xFF2E3A73, 0xFF8C77A6, 0xFFF0A487), f(1f, 0.8f, 0.76f), f(0.6f, 0.6f, 0.84f), 0.64f, 0.14f, 1f,
        c(0xFF1C1530), 0.18f, c(0xFFFFC08E), c(0xFFF2A07E), 0.2f, c(0xFFFFA27A), 0.5f, stars = 0f, fire = 0.3f,
    )
    private val SUNSET = Plate(
        1f, ints(0xFF4D5D9D, 0xFFB898B3, 0xFFFBBC92), f(1.14f, 0.88f, 0.7f), f(0.72f, 0.72f, 0.95f), 0.86f, 0.28f, 1f,
        c(0xFF24163A), 0.4f, c(0xFFFFE3B8), c(0xFFFFB880), 0.55f, c(0xFFFFB070), 0.55f, stars = 0f, fire = 0.18f,
    )
    private val GOLDEN = Plate(
        6.5f, ints(0xFF7090C0, 0xFFE2C6A8, 0xFFF8D7A2), f(1.14f, 0.96f, 0.76f), f(0.78f, 0.84f, 1.02f), 0.96f, 0.26f, 1f,
        c(0xFF2A1E2E), 0.42f, c(0xFFFFF2D2), c(0xFFFFD9A0), 0.45f, c(0xFFFFD49A), 0.32f, stars = 0f, fire = 0.12f,
    )
    private val AFTERNOON = Plate(
        18f, ints(0xFF78A8D2, 0xFFB3CDE0, 0xFFEDE9DA), f(1.07f, 1f, 0.9f), f(0.86f, 0.93f, 1.06f), 1f, 0.2f, 1f,
        c(0xFF1C3040), 0.36f, c(0xFFFFF8EA), c(0xFFFFF1D8), 0.32f, c(0xFFF6EAD0), 0.1f, stars = 0f, fire = 0.08f,
    )
    private val DAY = Plate(
        40f, ints(0xFF7DA4CC, 0xFFAFC9DE, 0xFFECE7D6), f(1.05f, 1f, 0.92f), f(0.88f, 0.95f, 1.08f), 1f, 0.16f, 1f,
        c(0xFF1A2533), 0.32f, c(0xFFFFFBF2), c(0xFFFFF6E4), 0.3f, c(0xFFE8F0F2), 0f, stars = 0f, fire = 0.07f,
    )
    private val FORENOON = Plate(
        18f, ints(0xFF77A7D4, 0xFFB0CCE4, 0xFFE6ECEC), f(1.06f, 1f, 0.92f), f(0.86f, 0.93f, 1.08f), 1f, 0.2f, 1f,
        c(0xFF19324A), 0.36f, c(0xFFFFFAF0), c(0xFFFFF4E4), 0.32f, c(0xFFF0EEE6), 0.08f, stars = 0f, fire = 0.08f,
    )
    private val MORNING = Plate(
        6.5f, ints(0xFF7393C6, 0xFFB4BFDA, 0xFFF4DCCF), f(1.1f, 0.98f, 0.86f), f(0.84f, 0.9f, 1.06f), 0.94f, 0.24f, 1f,
        c(0xFF262A48), 0.4f, c(0xFFFFF2DE), c(0xFFFFE4C8), 0.4f, c(0xFFFFD8C0), 0.3f, stars = 0f, fire = 0.12f,
    )
    private val SUNRISE = Plate(
        1f, ints(0xFF5969A7, 0xFFB7A6CC, 0xFFFFD2B6), f(1.12f, 0.9f, 0.8f), f(0.72f, 0.74f, 0.98f), 0.84f, 0.28f, 1f,
        c(0xFF261C48), 0.4f, c(0xFFFFE6C4), c(0xFFFFC6A0), 0.48f, c(0xFFFFB896), 0.55f, stars = 0f, fire = 0.2f,
    )
    private val DAWN = Plate(
        -3f, ints(0xFF293973, 0xFF7A78AC, 0xFFE5A9A7), f(0.96f, 0.82f, 0.84f), f(0.58f, 0.6f, 0.86f), 0.6f, 0.14f, 1f,
        c(0xFF16143A), 0.16f, c(0xFFFFCCA8), c(0xFFE8A6A0), 0.18f, c(0xFFF8A898), 0.48f, stars = 0f, fire = 0.32f,
    )
    private val BLUE_MORNING = Plate(
        -7.5f, ints(0xFF1C2553, 0xFF3C4680, 0xFF746FA2), BLUE_EVENING.key, BLUE_EVENING.fill, BLUE_EVENING.level, BLUE_EVENING.contrast, BLUE_EVENING.haze,
        BLUE_EVENING.shadow, BLUE_EVENING.shadowAlpha, BLUE_EVENING.sunDisc, BLUE_EVENING.glow, 0f, c(0xFFB890B0), 0.22f, stars = 0.35f, fire = 0.5f,
    )

    private val evening = listOf(NIGHT, BLUE_EVENING, DUSK, SUNSET, GOLDEN, AFTERNOON, DAY)
    private val morning = listOf(NIGHT, BLUE_MORNING, DAWN, SUNRISE, MORNING, FORENOON, DAY)

    // --- the papers ----------------------------------------------------------------------------

    /** The papers of one season: every sheet's colour by daylight, and the colours of crowns and bushes. */
    private class Papers(
        val far: Int, val snow: Int, val near: Int, val woodFar: Int, val woodNear: Int, val meadow: Int, val meadowNear: Int,
        val standees: Int, val front: Int, val leaves: IntArray, val bushes: IntArray,
    )

    private val SPRING = Papers(
        c(0xFFABB3CE), c(0xFFF2F0EA), c(0xFF7690A8), c(0xFF6E9086), c(0xFF58866C), c(0xFFA5BE6C), c(0xFF96B262), c(0xFF467A5C), c(0xFF264B3C),
        ints(0xFF9CC47A, 0xFFF2DCE4, 0xFFB4D28A), ints(0xFF7FB060, 0xFF9BC872),
    )
    private val SUMMER = Papers(
        c(0xFFA9B1CC), c(0xFFF2F0EA), c(0xFF7890A8), c(0xFF6D8C86), c(0xFF557E6C), c(0xFF9DAE6E), c(0xFF8CA265), c(0xFF43725A), c(0xFF24473A),
        ints(0xFF557F5E, 0xFF5F8A62, 0xFF4F7858), ints(0xFF4E7A50, 0xFF628C5A),
    )
    private val AUTUMN = Papers(
        c(0xFFA9AFC6), c(0xFFF2F0EA), c(0xFF7E90A4), c(0xFF708A80), c(0xFF5B7A66), c(0xFFB3AA6A), c(0xFFA69A5C), c(0xFF45705A), c(0xFF2C4636),
        ints(0xFFE0A33C, 0xFFD2702E, 0xFFB0492C), ints(0xFFC8732E, 0xFFE09A3A),
    )
    private val WINTER = Papers(
        c(0xFFB0B8D0), c(0xFFF4F4F2), c(0xFF8496B0), c(0xFF667F84), c(0xFF4E6C66), c(0xFFA9A884), c(0xFF9C9A78), c(0xFF3C6250), c(0xFF233F33),
        ints(0xFF8E8070, 0xFF8E8070, 0xFF8E8070), ints(0xFF8E8070, 0xFF8E8070),
    )

    /** White paper, the bark, the stones and the cut wood of the fire, the flames. */
    private val WHITE = c(0xFFF4F1E8)
    private val BARK = c(0xFF6E4A34)
    private val STONE = c(0xFF8E8A82)
    private val WOOD = c(0xFFC9AE88)
    private val FLAME = ints(0xFFFFD66E, 0xFFF4892F, 0xFFD2442A)
    private val FIRE_GLOW = c(0xFFFF8F3A)

    /** How much of the horizon's colour veils each far sheet. */
    private fun haze(id: SheetId): Float = when (id) {
        SheetId.RANGE_FAR -> 0.14f
        SheetId.SNOW -> 0.08f
        SheetId.RANGE_NEAR -> 0.12f
        SheetId.WOOD_FAR -> 0.1f
        SheetId.WOOD_NEAR, SheetId.LEAVES -> 0.05f
        else -> 0f
    }

    // --- the look --------------------------------------------------------------------------------

    /** The colours of everything for [s]. */
    fun look(s: SkyState): PaperLook {
        val moon = moonlight(s)
        val clear = plateFor(s.sunElevation, s.sunAzimuth).let { if (moon > 0f) blend(it, moonlitOf(it), moon) else it }
        val cover = s.cover.coerceIn(0f, 1f)
        val p = clear
        val papers = seasonal(s.season)

        // Where the light comes from: the sun by day (its side of the sky stays the bright one
        // through the twilight), the moon by night (−1 east, on the left … 1 west).
        val sunUp = smooth(-8f, 6f, s.sunElevation)
        val sunSide = sin(Math.toRadians((s.sunAzimuth - 180f).toDouble())).toFloat()
        val moonSide = sin(Math.toRadians((s.moonAzimuth - 180f).toDouble())).toFloat()
        val raw = sunSide * sunUp + moonSide * (1f - sunUp) * moon
        // A little light from the upper right always: at noon (sun in front) the folds still read.
        val side = (raw + 0.3f * (1f - abs(raw))).coerceIn(-1f, 1f)
        val contrast = p.contrast * (1f - 0.8f * cover)
        val night = 1f - smooth(-8f, 2f, s.sunElevation)
        // The direct light gives way to the sky's under clouds.
        val key = FloatArray(3) { p.key[it] + (p.fill[it] * 1.05f - p.key[it]) * 0.7f * cover }
        val fill = p.fill
        val horizon = p.sky[2]

        fun lit(paper: Int, n: Float, c: Float): Int {
            val k = (n * side).coerceAtLeast(0f)
            val strength = p.level * (1f - c + 2f * c * k)
            return scaled(paper, FloatArray(3) { strength * (key[it] * k + fill[it] * (1f - k)) })
        }

        fun veiled(argb: Int, id: SheetId): Int {
            val h = haze(id) * p.haze
            return if (h <= 0f) argb else mixRgb(argb, horizon, h)
        }

        fun muted(argb: Int): Int = if (cover <= 0.01f) argb else mute(argb, cover)

        // White paper sinks into the night more than the coloured ones: no margin may glow in the dark.
        val dusk = 1f - 0.4f * night
        val white = scaled(WHITE, FloatArray(3) { dusk * p.level * (key[it] * 0.5f + fill[it] * 0.5f) })
        val whiteLit = scaled(WHITE, FloatArray(3) { dusk * p.level * (1f + 0.5f * contrast) * (key[it] * 0.7f + fill[it] * 0.3f) })

        fun folded(paper: Int, id: SheetId, c: Float = contrast): Pair<Int, Int> = veiled(lit(paper, -1f, c), id) to veiled(lit(paper, 1f, c), id)

        val sheets = HashMap<SheetId, Inks>()
        fun put(id: SheetId, a: Int, b: Int, third: Int) {
            sheets[id] = Inks(muted(a), muted(b), muted(third))
        }
        folded(papers.far, SheetId.RANGE_FAR).let { (a, b) -> put(SheetId.RANGE_FAR, a, b, mixRgb(a, b, 0.5f)) }
        folded(papers.snow, SheetId.SNOW, contrast * 0.7f).let { (a, b) -> put(SheetId.SNOW, a, b, mixRgb(a, b, 0.5f)) }
        folded(papers.near, SheetId.RANGE_NEAR).let { (a, b) -> put(SheetId.RANGE_NEAR, a, b, veiled(whiteLit, SheetId.RANGE_NEAR)) }
        for ((id, paper) in listOf(SheetId.WOOD_FAR to papers.woodFar, SheetId.WOOD_NEAR to papers.woodNear, SheetId.MEADOW to papers.meadow, SheetId.MEADOW_NEAR to papers.meadowNear)) {
            val a = veiled(lit(paper, 0f, 0f), id)
            put(id, a, shift(a, 0.025f), veiled(white, id))
        }
        folded(papers.standees, SheetId.STANDEES, contrast * 1.2f).let { (a, b) -> put(SheetId.STANDEES, a, b, white) }
        folded(papers.front, SheetId.FRONT_LEFT, contrast * 1.2f).let { (a, b) ->
            put(SheetId.FRONT_LEFT, a, b, white)
            put(SheetId.FRONT_RIGHT, a, b, white)
        }
        val leaves = IntArray(3) { veiled(lit(papers.leaves[it], 0f, 0f), SheetId.LEAVES) }
        put(SheetId.LEAVES, leaves[0], leaves[1], leaves[2])
        put(SheetId.BUSHES, lit(papers.bushes[0], -1f, contrast), lit(papers.bushes[1], 1f, contrast), white)
        put(SheetId.FIREWOOD, lit(BARK, 0f, 0f), lit(STONE, 0f, 0f), lit(WOOD, 0f, 0f))
        sheets[SheetId.SKY] = Inks(muted(p.sky[0]), muted(p.sky[1]), muted(horizon))

        // The flames shine by themselves: brighter against the night.
        val flameLight = 0.92f + 0.22f * night
        val flame = Inks(scaled(FLAME[0], FloatArray(3) { flameLight }), scaled(FLAME[1], FloatArray(3) { flameLight }), scaled(FLAME[2], FloatArray(3) { flameLight }))

        // Shadows fall away from the light, longer when it is low.
        val lightEl = if (sunUp > 0.5f) s.sunElevation else s.moonElevation
        val low = 1f - smooth(8f, 50f, lightEl)
        val shadowAlpha = p.shadowAlpha * (1f - 0.8f * cover)

        val moonLit = mixOk(c(0xFFF6EED8), horizon, 0.06f + 0.4f * cover)
        // The moon's unlit part a breath lighter than the sky: the whole disc reads, as a paper disc would.
        val moonDark = mixOk(p.sky[1], moonLit, 0.16f)

        val front = sheets.getValue(SheetId.FRONT_LEFT)
        val label = labelInks(front.mix(0.5f, 0f), s.sunElevation)
        // Behind a thick sky the sun is only a brightness: its disc and its glow fade out, the moon's too.
        val veil = Shapes.smoothstep(0.55f, 0.95f, cover)
        return PaperLook(
            sheets = sheets,
            cloud = Inks(muted(whiteLit), muted(scaled(white, FloatArray(3) { 0.86f })), muted(white)),
            flame = flame,
            sunDisc = p.sunDisc, discAlpha = 1f - veil,
            glow = p.glow, glowAlpha = p.glowAlpha * (1f - 0.8f * cover),
            horizonGlow = p.horizonGlow, horizonGlowAlpha = p.horizonGlowAlpha * (1f - 0.75f * cover),
            moonLit = moonLit, moonDark = moonDark, moonAlpha = (1f - veil) * (0.5f + 0.5f * night), moonDarkAlpha = (1f - veil) * night,
            stars = p.stars * (1f - cover),
            shadow = p.shadow, shadowAlpha = shadowAlpha, shadowSide = -side, shadowLength = 0.7f + 0.5f * low,
            fire = p.fire, fireGlow = FIRE_GLOW, fireReach = 46f + 70f * night,
            label = label,
            labelCover = 1f - 0.3f * night,
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
        fun v(x: FloatArray, y: FloatArray) = FloatArray(3) { f(x[it], y[it]) }
        return Plate(
            f(a.elevation, b.elevation), IntArray(3) { m(a.sky[it], b.sky[it]) }, v(a.key, b.key), v(a.fill, b.fill),
            f(a.level, b.level), f(a.contrast, b.contrast), f(a.haze, b.haze), m(a.shadow, b.shadow), f(a.shadowAlpha, b.shadowAlpha),
            m(a.sunDisc, b.sunDisc), m(a.glow, b.glow), f(a.glowAlpha, b.glowAlpha), m(a.horizonGlow, b.horizonGlow),
            f(a.horizonGlowAlpha, b.horizonGlowAlpha), f(a.stars, b.stars), f(a.fire, b.fire),
        )
    }

    /** The season's papers, mixed in Oklab by [s]. */
    private fun seasonal(s: SeasonMix): Papers {
        fun mix(get: (Papers) -> Int): Int {
            val a = Oklab.fromArgb(get(SPRING))
            val b = Oklab.fromArgb(get(SUMMER))
            val c = Oklab.fromArgb(get(AUTUMN))
            val d = Oklab.fromArgb(get(WINTER))
            return Lab(
                a.l * s.spring + b.l * s.summer + c.l * s.autumn + d.l * s.winter,
                a.a * s.spring + b.a * s.summer + c.a * s.autumn + d.a * s.winter,
                a.b * s.spring + b.b * s.summer + c.b * s.autumn + d.b * s.winter,
            ).toArgb()
        }
        return Papers(
            mix { it.far }, mix { it.snow }, mix { it.near }, mix { it.woodFar }, mix { it.woodNear }, mix { it.meadow }, mix { it.meadowNear },
            mix { it.standees }, mix { it.front }, IntArray(3) { i -> mix { it.leaves[i] } }, IntArray(2) { i -> mix { it.bushes[i] } },
        )
    }

    /**
     * Clouds over everything: the colours give way to soft greys of the same hour, the values
     * draw together – a quieter picture, never a darker one.
     */
    private fun mute(argb: Int, cover: Float): Int {
        val lab = Oklab.fromArgb(argb)
        val l = lab.l + (0.62f - lab.l) * 0.18f * cover
        val k = 1f - 0.65f * cover
        return Lab(l, lab.a * k, lab.b * k).toArgb()
    }

    /**
     * The label's three inks on the near bank [ground]: like white at 93 / 80 / 66.5 % (a little
     * lower at night, so the text does not outshine the picture), with a breath of the bank's
     * hue – and never darker than keeps 4.5:1 on the bank's lightest paper (its grain lifts it
     * a little), the steps between the levels kept.
     */
    fun labelInks(ground: Int, sunElevation: Float): IntArray {
        val g = Oklab.fromArgb(ground).toLch()
        val night = 1f - smooth(-8f, 0f, sunElevation)
        val t = 1f - 0.1f * night
        val levels = floatArrayOf(0.93f * t, 0.8f * t, 0.665f * t)
        val floor = darkestLightInkOn(scaled(ground, FloatArray(3) { GRAIN_LIGHTEST }))
        val chroma = minOf(0.009f, g.c * 0.15f + 0.002f)
        return IntArray(3) { i ->
            val l = maxOf(levels[i], CoverPalette.NIGHT_INK_FLOOR[i], floor + 0.1f * (2 - i)).coerceAtMost(0.97f)
            Lch(l, chroma, g.h).toArgb()
        }
    }

    /** The bank's paper is this much lighter where its grain is lightest. */
    const val GRAIN_LIGHTEST = 1.12f

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

    /** Mixed in sRGB, like paint: what the sheets' inks do too. */
    private fun mixRgb(x: Int, y: Int, t: Float): Int {
        var out = 0xFF shl 24
        for (s in intArrayOf(16, 8, 0)) {
            val a = x shr s and 0xFF
            val b = y shr s and 0xFF
            out = out or ((a + (b - a) * t).toInt().coerceIn(0, 255) shl s)
        }
        return out
    }

    /** Each channel (sRGB) times its factor. */
    private fun scaled(argb: Int, k: FloatArray): Int {
        var out = 0xFF shl 24
        for ((i, s) in intArrayOf(16, 8, 0).withIndex()) out = out or (((argb shr s and 0xFF) * k[i]).toInt().coerceIn(0, 255) shl s)
        return out
    }

    /** Lighter (positive) or darker by [d] in Oklab lightness. */
    private fun shift(argb: Int, d: Float): Int = Oklab.fromArgb(argb).let { Lab((it.l + d).coerceIn(0f, 1f), it.a, it.b).toArgb() }

    private fun smooth(e0: Float, e1: Float, x: Float): Float = Shapes.smoothstep(e0, e1, x)
}
