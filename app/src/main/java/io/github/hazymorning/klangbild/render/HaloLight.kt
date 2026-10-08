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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The light of a Halo scene: a lamp hidden behind the print, like halo-lit lettering.
 *
 *  - The **light map** is greyscale intensity, precomputed once per track at 960 × 540:
 *    the light is a little weaker right at the print's edge (the print shades it), peaks
 *    12–16 dp out and is gone long before the screen edge; a faint, wide layer leans
 *    towards the open wall. Far from the print the wall is pure black (OLED: no grey
 *    mura, no posterised near-black).
 *  - The **colour** ([lightTint]) is applied when drawing, so a track change can morph it.
 *  - The **sundial** is an angular mask ([maskStops]) rotated around the print at draw
 *    time: one side of the halo is lit about three times brighter than the other, and that
 *    side wanders around the print over the course of the track ([sunAngle]).
 *
 * Per frame this costs one textured rectangle with a sweep gradient – no image processing.
 */
object HaloLight {
    const val MAP_W = 960
    const val MAP_H = 540

    /** No light at all within this distance of the screen edges (dp)… */
    const val EDGE_CLEAR_DP = 36f

    /** …and full light only from here on; in between it fades out. */
    const val EDGE_FADE_DP = 48f

    /**
     * Brightness of the unlit side relative to the lit side (sundial mask, linear light):
     * in 8-bit values the lit side is about 2.5 × brighter, clearly a light from one side.
     */
    const val UNLIT = 0.14f

    /** Narrowness of the lit side: the light clearly comes from one direction. */
    private const val LOBE = 2.5

    /** The halo's shape for a cover of [coverDp]: where it peaks and how fast it fades. */
    data class Profile(val peakDp: Float, val decayDp: Float, val wide: Float)

    fun profile(coverDp: Float, night: Float): Profile = Profile(
        peakDp = (0.045f * coverDp).coerceIn(8f, 14f),
        decayDp = (0.05f * coverDp).coerceIn(8f, 16f) * (1f - 0.5f * night),
        wide = 0.32f * (1f - night),
    )

    /** Share of the peak right at the print's edge (the print shades its own light). */
    const val EDGE_SHADE = 0.5f

    /**
     * Halo intensity at [d] dp from the print's edge, 0..1: half strength at the edge,
     * rising to the peak, then an exponential fall-off with a rounded start – in sRGB that
     * fades steadily instead of looking like a flat band with a soft rim.
     */
    fun halo(d: Float, p: Profile): Float {
        if (d <= 0f) return EDGE_SHADE
        if (d <= p.peakDp) return EDGE_SHADE + (1f - EDGE_SHADE) * smoothstep(0f, p.peakDp, d)
        val x = d - p.peakDp
        val soft = 6f
        return exp(-(sqrt(x * x + soft * soft) - soft) / p.decayDp)
    }

    /** 0 within [EDGE_CLEAR_DP] of any screen edge, 1 from [EDGE_CLEAR_DP] + [EDGE_FADE_DP] on. */
    fun edgeWindow(xDp: Float, yDp: Float, screen: ScreenSpec): Float {
        val e = min(min(xDp, screen.widthDp - xDp), min(yDp, screen.heightDp - yDp))
        return smoothstep(EDGE_CLEAR_DP, EDGE_CLEAR_DP + EDGE_FADE_DP, e)
    }

    /** Light map plus the factor it was normalised by (its brightest value before scaling). */
    class LightMap(val pixels: IntArray, val gain: Float)

    /**
     * Greyscale intensity around [cover], normalised to 0..1 and sRGB-encoded (so that
     * multiplying by an sRGB tint on the GPU gives the right result). [ditherLsb] maps the
     * normalisation gain to the dither amplitude in grey levels: the tint scales the map
     * down, so the dither must be proportionally stronger to still break up bands in the
     * final 8-bit output.
     */
    fun lightMap(
        cover: RectDp,
        screen: ScreenSpec,
        openToRight: Boolean,
        night: Float,
        seed: Int,
        ditherLsb: (gain: Float) -> Float = { 1f },
        outW: Int = MAP_W,
        outH: Int = MAP_H,
    ): LightMap {
        val p = profile(cover.width, night)
        val sx = screen.widthDp / outW
        val sy = screen.heightDp / outH
        // The wide layer leans towards the open wall (the side of the text column).
        val wideCx = cover.centerX + (if (openToRight) 1f else -1f) * 0.15f * cover.width
        val wideCy = cover.centerY
        val wideR = 0.4f * cover.width
        val values = FloatArray(outW * outH)
        var maxV = 0f
        for (y in 0 until outH) {
            val py = (y + 0.5f) * sy
            val dy = max(max(cover.top - py, py - cover.bottom), 0f)
            for (x in 0 until outW) {
                val px = (x + 0.5f) * sx
                val dx = max(max(cover.left - px, px - cover.right), 0f)
                // Behind the print the wall stays dark, so a fading print never reveals a
                // glowing patch.
                val inside = px > cover.left + 0.5f && px < cover.right - 0.5f && py > cover.top + 0.5f && py < cover.bottom - 0.5f
                val v = if (inside) {
                    0f
                } else {
                    val d = sqrt(dx * dx + dy * dy)
                    val wx = (px - wideCx) / wideR
                    val wy = (py - wideCy) / wideR
                    (halo(d, p) + p.wide * exp(-(wx * wx + wy * wy))) * edgeWindow(px, py, screen)
                }
                values[y * outW + x] = v
                if (v > maxV) maxV = v
            }
        }
        val gain = if (maxV > 0f) maxV else 1f
        val amplitude = ditherLsb(gain)
        val out = IntArray(values.size)
        var state = seed * 0x9E3779B9.toInt() or 1
        fun rnd(): Float {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return (state ushr 8) / 16777216f
        }
        for (i in values.indices) {
            val v = values[i] / gain
            val g = if (v < 0.0005f) {
                0 // the far wall is pure black – no dither noise out there
            } else {
                (Oklab.linearToSrgb(v) * 255f + (rnd() - rnd()) * amplitude).roundToInt().coerceIn(0, 255)
            }
            out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return LightMap(out, gain)
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
     * brightness comes from [glowPeak]). Coloured light reads as light only when it is
     * fairly saturated – a dull dark orange reads as brown paint. Orange and yellow hues are
     * pulled towards a saturated red-amber; everything else keeps the cover's hue.
     */
    fun lightColor(analysis: CoverAnalysis): Lch {
        val lead = leadGroup(analysis) ?: return LAMP
        val hue = lead.hue
        return if (hue in 25f..105f) {
            Lch(0.64f, (lead.chroma * 1.3f).coerceIn(0.06f, 0.14f), LAMP_HUE + (hue - LAMP_HUE) * 0.45f)
        } else {
            Lch(0.66f, (lead.chroma * 1.25f).coerceIn(0.05f, 0.13f), hue)
        }
    }

    /** Light for neutral covers (black and white, greys): warm white, like a 2700 K bulb. */
    val LAMP = Lch(0.70f, 0.05f, 70f)

    /** Hue of plain lamp light (~2700 K on a dark wall: amber, never brown). */
    const val LAMP_HUE = 52f

    /**
     * Peak brightness (relative luminance) of the halo by day. Bright covers throw light
     * themselves and get less; small covers get less, so they don't become light bulbs.
     */
    fun glowPeak(analysis: CoverAnalysis, coverDp: Float, slotDp: Float): Float {
        val byCover = (0.052f - 0.024f * analysis.meanL).coerceIn(0.024f, 0.052f)
        return byCover * (0.7f + 0.3f * (coverDp / slotDp).coerceIn(0f, 1f))
    }

    /**
     * The sRGB tint for the light map: [color] at [peak] luminance, adjusted for the time
     * of day. The lamp gets warmer only after sunset; at night it is turned right down
     * (≤ 0.3 × daylight) and loses saturation (≤ 50 %), so a grey picture never gets an
     * orange neon frame.
     */
    fun lightTint(color: Lch, peak: Float, gain: Float, lighting: Lighting): Int {
        val night = nightOf(lighting)
        val warm = lighting.warmth.coerceIn(0f, 1f)
        val hue = mixHue(color.h, LAMP_HUE, 0.35f * warm)
        var chroma = color.c * (1f - 1.25f * night)
        val luminance = peak * gain * (1f - 1.75f * night).coerceAtLeast(0.3f)
        val maxSaturation = 1f - 1.25f * night
        var argb = scaledTo(Lch(color.l, chroma, hue), luminance)
        repeat(12) {
            if (hsvSaturation(argb) <= maxSaturation + 1e-3f) return argb
            chroma *= 0.85f
            argb = scaledTo(Lch(color.l, chroma, hue), luminance)
        }
        return argb
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
     * (pointing right). Multiplying the light map by it leaves the lit side at full
     * strength and the opposite side at [UNLIT].
     */
    fun maskStops(steps: Int = 36): IntArray = IntArray(steps + 1) { i ->
        val a = 2.0 * PI * i / steps
        val lobe = ((1.0 + cos(a)) / 2.0).pow(LOBE).toFloat()
        val m = UNLIT + (1f - UNLIT) * lobe
        val g = (Oklab.linearToSrgb(m) * 255f).roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    /** The mask value (linear light) for a point at maths angle [pointDeg] with the sun at [sunDeg]. */
    fun maskAt(pointDeg: Float, sunDeg: Float): Float {
        val a = Math.toRadians((pointDeg - sunDeg).toDouble())
        val lobe = ((1.0 + cos(a)) / 2.0).pow(LOBE).toFloat()
        return UNLIT + (1f - UNLIT) * lobe
    }

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
    fun distanceTo(cover: RectDp, xDp: Float, yDp: Float): Float {
        val dx = max(max(cover.left - xDp, xDp - cover.right), 0f)
        val dy = max(max(cover.top - yDp, yDp - cover.bottom), 0f)
        return sqrt(dx * dx + dy * dy)
    }
}
