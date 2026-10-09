package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The outline of the print on the wall: a square, or a disc (generated sand plates hang on
 * the wall without a square around them).
 */
data class PrintShape(val rect: RectDp, val round: Boolean = false) {
    val centerX get() = rect.centerX
    val centerY get() = rect.centerY

    /** Half the size (dp): for a disc its radius. */
    val radius get() = rect.width / 2f

    /** Distance (dp) of a point from the outline; 0 inside. */
    fun distance(xDp: Float, yDp: Float): Float =
        if (round) {
            max(0f, hypot(xDp - centerX, yDp - centerY) - radius)
        } else {
            val dx = max(max(rect.left - xDp, xDp - rect.right), 0f)
            val dy = max(max(rect.top - yDp, yDp - rect.bottom), 0f)
            sqrt(dx * dx + dy * dy)
        }

    fun contains(xDp: Float, yDp: Float): Boolean =
        if (round) hypot(xDp - centerX, yDp - centerY) < radius
        else xDp > rect.left && xDp < rect.right && yDp > rect.top && yDp < rect.bottom
}

/**
 * The light of a Halo scene: a lamp hidden behind the print, a little off centre towards
 * the sundial's sun, lighting the wall around it.
 *
 *  - The **pool** ([glowSprite]) is the lamp's light on the wall: flat behind the print,
 *    fading out a little beyond it. It is drawn shifted towards the sun ([lampOffset]), so
 *    the lit side reaches further than the unlit one.
 *  - The **sundial mask** ([maskStops]) makes the light clearly come from one direction:
 *    a cos³ lobe, rotated around the print at draw time.
 *  - The **wall mask** ([holeMask]) is static: the print hides the wall behind it and
 *    shades its own edge a little (the light peaks 8–14 dp out), and no light comes
 *    closer than 36 dp to the screen edges. While a print fades out, the hole fades with
 *    it ([openMask]) – the empty wall is one continuous pool of light.
 *  - The **colour** ([lightTint]) is applied when drawing, so it can morph and dim.
 *
 * Far from the print the wall is pure black (OLED: no grey mura, no posterised near-black).
 * Per frame this is one rectangle with a composed shader – no image processing.
 */
object HaloLight {
    const val MAP_W = 960
    const val MAP_H = 540

    /** No light at all within this distance of the screen edges (dp)… */
    const val EDGE_CLEAR_DP = 36f

    /** …and full light only from here on; in between it fades out. */
    const val EDGE_FADE_DP = 48f

    /**
     * Floor of the sundial mask (linear light): the side away from the sun keeps only this
     * much – in 8-bit values the lit side is about four times brighter.
     */
    const val UNLIT = 0.05f

    /** Exponent of the cos lobe: the light clearly comes from one direction. */
    private const val LOBE = 3.0

    /** Margin of the pool sprite around the print (dp); the light is gone well before. */
    const val GLOW_MARGIN_DP = 150f

    /** Where the light peaks (dp from the print's edge) and how fast it fades. */
    data class Profile(val peakDp: Float, val decayDp: Float)

    fun profile(sizeDp: Float, night: Float): Profile = Profile(
        peakDp = (0.045f * sizeDp).coerceIn(8f, 14f),
        decayDp = (0.032f * sizeDp).coerceIn(6f, 10.5f) * (1f - 0.4f * night),
    )

    /** How far the lamp sits off the print's centre, towards the sun (dp). */
    fun lampOffset(sizeDp: Float): Float = 0.075f * sizeDp

    /** Share of the peak right at the print's edge (the print shades its own light). */
    const val EDGE_SHADE = 0.5f

    /**
     * The lamp's pool at [d] dp from the print's outline: full strength behind the print
     * and up to the peak, then an exponential fall-off with a rounded start.
     */
    fun pool(d: Float, p: Profile): Float {
        if (d <= p.peakDp) return 1f
        val x = d - p.peakDp
        val soft = 6f
        return exp(-(sqrt(x * x + soft * soft) - soft) / p.decayDp)
    }

    /** The print's shading of its own light: 0 behind it, half at its edge, full at the peak. */
    fun shade(d: Float, inside: Boolean, p: Profile): Float =
        if (inside) 0f else EDGE_SHADE + (1f - EDGE_SHADE) * smoothstep(0f, p.peakDp, d)

    /** 0 within [EDGE_CLEAR_DP] of any screen edge, 1 from [EDGE_CLEAR_DP] + [EDGE_FADE_DP] on. */
    fun edgeWindow(xDp: Float, yDp: Float, screen: ScreenSpec): Float {
        val e = min(min(xDp, screen.widthDp - xDp), min(yDp, screen.heightDp - yDp))
        return smoothstep(EDGE_CLEAR_DP, EDGE_CLEAR_DP + EDGE_FADE_DP, e)
    }

    /** Side of the square pool sprite in pixels (1 px per dp). */
    fun glowSize(shape: PrintShape): Int = (2f * (shape.radius + GLOW_MARGIN_DP)).roundToInt()

    /**
     * The lamp's pool as a greyscale sprite centred on the print (1 px per dp), sRGB-encoded
     * so that multiplying by an sRGB tint on the GPU gives the right result. [ditherLsb] is
     * the dither amplitude in grey levels: the tint scales the sprite down, so the dither
     * must be proportionally stronger to still break up bands in the final 8-bit output.
     */
    fun glowSprite(shape: PrintShape, night: Float, seed: Int, ditherLsb: Float = 1f): IntArray {
        val p = profile(shape.rect.width, night)
        val size = glowSize(shape)
        val origin = size / 2f
        val local = PrintShape(RectDp(origin - shape.radius, origin - shape.radius, shape.rect.width, shape.rect.height), shape.round)
        val out = IntArray(size * size)
        val rnd = Dither(seed)
        for (y in 0 until size) {
            val py = y + 0.5f
            for (x in 0 until size) {
                val v = pool(local.distance(x + 0.5f, py), p)
                out[y * size + x] = grey(v, rnd, ditherLsb)
            }
        }
        return out
    }

    /**
     * The static wall mask for a print (960 × 540, smooth, no dither): the print's shadow on
     * its own light and the clearance at the screen edges.
     */
    fun holeMask(shape: PrintShape, screen: ScreenSpec, night: Float): IntArray {
        val p = profile(shape.rect.width, night)
        return mask(screen) { x, y -> shade(shape.distance(x, y), shape.contains(x, y), p) * edgeWindow(x, y, screen) }
    }

    /** The wall mask without a print: only the clearance at the screen edges. */
    fun openMask(screen: ScreenSpec): IntArray = mask(screen) { x, y -> edgeWindow(x, y, screen) }

    private inline fun mask(screen: ScreenSpec, value: (Float, Float) -> Float): IntArray {
        val sx = screen.widthDp / MAP_W
        val sy = screen.heightDp / MAP_H
        val out = IntArray(MAP_W * MAP_H)
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val v = value((x + 0.5f) * sx, (y + 0.5f) * sy)
            val g = (Oklab.linearToSrgb(v.coerceIn(0f, 1f)) * 255f).roundToInt()
            out[y * MAP_W + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return out
    }

    private class Dither(seed: Int) {
        private var state = seed * 0x9E3779B9.toInt() or 1
        fun next(): Float {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return (state ushr 8) / 16777216f
        }
    }

    /** Opaque grey for linear [v]; pure black (no dither noise) where there is no light. */
    private fun grey(v: Float, rnd: Dither, amplitude: Float): Int {
        val g = if (v < 0.0005f) 0 else (Oklab.linearToSrgb(v) * 255f + (rnd.next() - rnd.next()) * amplitude).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    /**
     * The hue of the light: the cover's own leading colour. Clusters of similar hue count
     * together (a sky in two shades of violet is one colour), and area counts more than
     * brightness – the deep violet sky of a synthwave cover, not its small orange sun.
     * Null for neutral covers (they get warm lamp light).
     */
    fun lightHue(analysis: CoverAnalysis): Float? = leadGroup(analysis)?.hue

    private class HueGroup(val hue: Float, val chroma: Float)

    private fun leadGroup(analysis: CoverAnalysis): HueGroup? {
        if (analysis.kind == CoverKind.MONOCHROME) return null
        val chromatic = analysis.clusters.filter { it.lab.chroma >= 0.035f && it.weight >= 0.02f && it.lab.l > 0.12f }
        if (chromatic.isEmpty()) return null
        val groups = ArrayList<MutableList<io.github.hazymorning.klangbild.color.ColorCluster>>()
        for (c in chromatic.sortedByDescending { it.weight * it.lab.chroma }) {
            val g = groups.firstOrNull { hueDistance(it[0].lab.hue, c.lab.hue) <= 35f }
            if (g != null) g += c else groups += mutableListOf(c)
        }
        val best = groups.maxBy { g ->
            val w = g.sumOf { it.weight.toDouble() }.toFloat()
            val chroma = g.sumOf { (it.weight * it.lab.chroma).toDouble() }.toFloat() / w
            val l = g.sumOf { (it.weight * it.lab.l).toDouble() }.toFloat() / w
            w * chroma * (0.6f + 0.4f * lightnessFactor(l))
        }
        var x = 0.0
        var y = 0.0
        for (c in best) {
            val k = (c.weight * c.lab.chroma).toDouble()
            val r = Math.toRadians(c.lab.hue.toDouble())
            x += k * cos(r)
            y += k * sin(r)
        }
        val hue = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        val w = best.sumOf { it.weight.toDouble() }.toFloat()
        return HueGroup(hue, best.sumOf { (it.weight * it.lab.chroma).toDouble() }.toFloat() / w)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun lightnessFactor(l: Float) = (1f - (2f * l - 1.1f).pow(2)).coerceIn(0f, 1f)

    /**
     * Daylight colour of the light (reference lightness; only its chromaticity matters, the
     * brightness comes from [glowPeak]). At the low brightness of a glow, any saturated warm
     * colour reads as rust or tobacco, not as light: warm hues become tungsten light (hue
     * 64–85°, little chroma). Cool and violet hues keep the cover's colour.
     */
    fun lightColor(analysis: CoverAnalysis): Lch {
        val lead = leadGroup(analysis) ?: return LAMP
        val hue = lead.hue
        return if (hue in 20f..105f) {
            Lch(0.70f, (lead.chroma * 0.4f).coerceIn(0.03f, 0.045f), hue.coerceIn(64f, 85f))
        } else {
            Lch(0.66f, (lead.chroma * 1.25f).coerceIn(0.05f, 0.12f), hue)
        }
    }

    /** Light for neutral covers (black and white, greys): warm white, like a 2700 K bulb. */
    val LAMP = Lch(0.70f, 0.038f, 74f)

    /** Hue of plain lamp light. */
    const val LAMP_HUE = 74f

    /**
     * Peak brightness (relative luminance) of the light by day. Bright covers throw light
     * themselves and get a little less; small prints get less. Never so dim that it turns
     * from light into a dark stain.
     */
    fun glowPeak(analysis: CoverAnalysis, coverDp: Float, slotDp: Float): Float {
        val byCover = (0.054f - 0.016f * analysis.meanL).coerceIn(0.04f, 0.054f)
        return byCover * (0.75f + 0.25f * (coverDp / slotDp).coerceIn(0f, 1f))
    }

    /**
     * The sRGB tint for the pool: [color] at [peak] luminance, adjusted for the time of
     * day. The lamp gets warmer only after sunset; at night it is turned right down
     * (0.28 × daylight) and loses saturation (≤ 50 %), so a grey picture never gets an
     * orange frame.
     */
    fun lightTint(color: Lch, peak: Float, lighting: Lighting): Int {
        val night = nightOf(lighting)
        val warm = lighting.warmth.coerceIn(0f, 1f)
        val hue = mixHue(color.h, LAMP_HUE, 0.35f * warm)
        var chroma = color.c * (1f - 1.25f * night)
        val luminance = peak * (1f - 1.8f * night).coerceAtLeast(0.28f)
        val maxSaturation = 1f - 1.25f * night
        var argb = scaledTo(Lch(color.l, chroma, hue), luminance)
        repeat(12) {
            if (hsvSaturation(argb) <= maxSaturation + 1e-3f) return argb
            chroma *= 0.85f
            argb = scaledTo(Lch(color.l, chroma, hue), luminance)
        }
        return argb
    }

    /**
     * [tint] dimmed to [k] of its brightness (linear light). Dim light also loses colour –
     * chroma falls with lightness squared – so a dimmed warm lamp never turns brown.
     */
    fun dimTint(tint: Int, k: Float): Int {
        if (k >= 0.999f) return tint
        if (k <= 0.001f) return 0xFF000000.toInt()
        val lab = Oklab.fromArgb(tint)
        val f = Math.cbrt(k.toDouble()).toFloat()
        val c = lab.chroma * f * f
        return Lch(lab.l * f, c, lab.hue).toArgb()
    }

    /** 0 by day, 0.4 at deep night (from the lighting's brightness). */
    fun nightOf(lighting: Lighting) = (1f - lighting.brightness).coerceIn(0f, 1f)

    /** [c] scaled in linear light to the given relative luminance (keeps its chromaticity). */
    private fun scaledTo(c: Lch, luminance: Float): Int {
        val lin = Oklab.toLinear(Oklab.gamutMap(c.toLab()))
        val y = 0.2126f * lin[0] + 0.7152f * lin[1] + 0.0722f * lin[2]
        val k = if (y > 0f) luminance / y else 0f
        fun ch(v: Float) = (Oklab.linearToSrgb((v * k).coerceIn(0f, 1f)) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(lin[0]) shl 16) or (ch(lin[1]) shl 8) or ch(lin[2])
    }

    fun hsvSaturation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        return if (mx == 0) 0f else (mx - mn).toFloat() / mx
    }

    /** Interpolates two sRGB colours through OKLCH (hue along the shorter arc). */
    fun mixOklch(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val la = Oklab.fromArgb(a)
        val lb = Oklab.fromArgb(b)
        val ca = la.chroma
        val cb = lb.chroma
        val l = la.l + (lb.l - la.l) * t
        val c = ca + (cb - ca) * t
        // A grey end has no hue: keep the other one's.
        val h = when {
            ca < 0.01f -> lb.hue
            cb < 0.01f -> la.hue
            else -> mixHue(la.hue, lb.hue, t)
        }
        return Lch(l, c, h).toArgb()
    }

    private fun mixHue(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return ((a + d * t) % 360f + 360f) % 360f
    }

    /**
     * The sundial mask as sweep-gradient stops (sRGB grey, opaque), lit side at angle 0
     * (pointing right): a cos³ lobe over a floor of [UNLIT].
     */
    fun maskStops(steps: Int = 72): IntArray = IntArray(steps + 1) { i ->
        val m = maskValue(2.0 * PI * i / steps)
        val g = (Oklab.linearToSrgb(m) * 255f).roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    private fun maskValue(radians: Double): Float {
        val lobe = max(0.0, cos(radians)).pow(LOBE).toFloat()
        return UNLIT + (1f - UNLIT) * lobe
    }

    /** The mask value (linear light) for a point at maths angle [pointDeg] with the sun at [sunDeg]. */
    fun maskAt(pointDeg: Float, sunDeg: Float): Float = maskValue(Math.toRadians((pointDeg - sunDeg).toDouble()))

    /** Maths angle (degrees, 0 = right, 90 = up) of a point relative to [cover]'s centre. */
    fun angleOf(cover: RectDp, xDp: Float, yDp: Float): Float =
        Math.toDegrees(atan2((cover.centerY - yDp).toDouble(), (xDp - cover.centerX).toDouble())).toFloat()

    /**
     * Sundial: the angle (degrees, maths convention, 0° = right, 90° = up) of the light for a
     * track [progress] 0..1 – rising lower left, over the top, setting lower right. Without a
     * duration (radio) the light swings slowly with the wall clock instead.
     */
    fun sunAngle(progress: Float?, wallClockMs: Long): Float {
        if (progress == null) {
            val t = (wallClockMs % RADIO_PERIOD_MS).toDouble() / RADIO_PERIOD_MS
            return (90.0 + 70.0 * sin(2 * PI * t)).toFloat()
        }
        return 225f - progress.coerceIn(0f, 1f) * 270f
    }

    private const val RADIO_PERIOD_MS = 20 * 60 * 1000L

    internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Distance (dp) of a point from [cover]'s edge; 0 inside. */
    fun distanceTo(cover: RectDp, xDp: Float, yDp: Float): Float = PrintShape(cover).distance(xDp, yDp)
}
