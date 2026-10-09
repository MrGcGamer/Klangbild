package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
 * The light of a Halo scene: a lamp hidden behind the print, close to its sun-side edge,
 * throwing a soft round pool of light onto the wall.
 *
 *  - The **lamp** ([lampSprite], [lampPose]) is one radial pool whose lightness falls as
 *    (1 − (r/R)²)^1.5: brightest at the print's edge on the sun's side, about half the
 *    luminance 25 dp out, gone with zero slope at 70 dp – it fits into the 72 dp of free
 *    wall between the print and the black band at the screen edge, so nothing ever clips
 *    it. The far side of the print stays dark. It follows the sundial's sun around the
 *    print – per frame the sprite is only moved, rotated and stretched.
 *  - The **wall mask** ([holeMask]) is static: the print hides the wall behind it, and the
 *    36 dp next to the screen edges stay black. While a print is taken down the hole fades
 *    with it ([openMask]) – the empty wall shows the lamp's pool, nothing else.
 *  - A dark print gets a faint **rim** of wall light all around ([rimMap]), so its far
 *    edges never vanish into the black wall.
 *  - The **colour** ([lightTint]) is applied when drawing, so it can morph and dim.
 *
 * Far from the print the wall is pure black (OLED: no grey mura, no posterised near-black).
 */
object HaloLight {
    const val MAP_W = 960
    const val MAP_H = 540

    /** No light at all within this distance of the screen edges (dp)… */
    const val EDGE_CLEAR_DP = 36f

    /**
     * …and full light from this much further in. Only a safety net: the pool is sized to
     * end before it, so the band never cuts a visible shape.
     */
    const val EDGE_FADE_DP = 4f

    /** Side of the lamp sprite (px); the pool is a disc of radius [LAMP_SPRITE] / 2. */
    const val LAMP_SPRITE = 512

    /** Radius of the pool, as a share of the print's size (110 dp for a 320 dp print). */
    const val LAMP_RADIUS = 0.34375f

    /** The pool is stretched along the print's edge by this much (not at its corners). */
    const val LAMP_STRETCH = 1.25f

    /** How far inside the print's sun-side edge the lamp sits (share of size: 40 dp). */
    const val LAMP_INSET = 0.125f

    /**
     * The pool's profile at [u] = r / R as perceived lightness: 1 at the lamp, falling to 0
     * with zero slope at the rim. The sprite stores it sRGB-encoded and the GPU multiplies
     * it by the tint as such, so the lightness on the wall follows this curve. (A profile
     * in linear light falls in a straight line of lightness and reads as a fuzzy disc.)
     */
    fun lampProfile(u: Float): Float {
        if (u >= 1f) return 0f
        val q = 1f - u * u
        return q * sqrt(q)
    }

    /** Lightness of the pool where it leaves the print on the sun's side (≈ 0.81). */
    val EDGE_STRENGTH: Float get() = lampProfile(LAMP_INSET / LAMP_RADIUS)

    /**
     * Luminance of the lamp itself for a pool that has [edgeLuminance] where it leaves the
     * print. The sprite multiplies the tint in sRGB, so the division happens there.
     */
    fun lampPeak(edgeLuminance: Float): Float =
        Oklab.srgbToLinear(Oklab.linearToSrgb(edgeLuminance) / EDGE_STRENGTH)

    /** Where and how the lamp's pool lies for the sun at [sunDeg] (dp, screen coordinates). */
    data class LampPose(val x: Float, val y: Float, val alongEdge: Float, val towardsSun: Float, val rotationDeg: Float)

    fun lampPose(shape: PrintShape, sunDeg: Float, screen: ScreenSpec): LampPose {
        val size = shape.rect.width
        val rad = Math.toRadians(sunDeg.toDouble())
        val ux = cos(rad).toFloat()
        val uy = -sin(rad).toFloat()
        // distance from the centre to the outline along the sun's direction
        val boundary = if (shape.round) shape.radius else shape.radius / max(abs(ux), abs(uy))
        val d = boundary - LAMP_INSET * size
        val r = LAMP_RADIUS * size
        // Stretched along a side; at a corner the stretch would point out across the wall.
        val side = if (shape.round) 1f else cos(2 * rad).toFloat().let { it * it }
        val along = r * (1f + (LAMP_STRETCH - 1f) * side)
        val rotation = 90f - sunDeg
        // The whole pool stays clear of the band at the screen edges: near an outer edge the
        // lamp moves deeper behind the print, so its light there is dimmer, never cut off.
        val phi = Math.toRadians(rotation.toDouble())
        val halfW = hypot(along * cos(phi).toFloat(), r * sin(phi).toFloat())
        val halfH = hypot(along * sin(phi).toFloat(), r * cos(phi).toFloat())
        val safe = EDGE_CLEAR_DP + EDGE_FADE_DP
        val x = (shape.centerX + ux * d).coerceIn(min(safe + halfW, shape.centerX), max(screen.widthDp - safe - halfW, shape.centerX))
        val y = (shape.centerY + uy * d).coerceIn(min(safe + halfH, shape.centerY), max(screen.heightDp - safe - halfH, shape.centerY))
        return LampPose(x, y, alongEdge = along, towardsSun = r, rotationDeg = rotation)
    }

    /**
     * The pool's strength (0..1) at a point (dp) for the lamp at [pose] – the same shape the
     * sprite takes when it is drawn with the pose's transform.
     */
    fun lampAt(pose: LampPose, xDp: Float, yDp: Float): Float {
        val r = Math.toRadians(pose.rotationDeg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = xDp - pose.x
        val dy = yDp - pose.y
        // back into the pool's own axes: the inverse of the drawing rotation
        val along = dx * c + dy * s
        val towards = -dx * s + dy * c
        return lampProfile(hypot(along / pose.alongEdge, towards / pose.towardsSun))
    }

    /**
     * The pool as a greyscale disc sprite holding [lampProfile] as sRGB values. [ditherLsb]
     * is the dither amplitude in grey levels: the tint scales the sprite down, so the
     * dither must be proportionally stronger to still break up bands in the final output.
     */
    fun lampSprite(ditherLsb: Float = 1f, seed: Int = 3, size: Int = LAMP_SPRITE): IntArray {
        val out = IntArray(size * size)
        val rnd = Dither(seed)
        val c = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val u = hypot(x + 0.5f - c, y + 0.5f - c) / c
            out[y * size + x] = grey(lampProfile(u), rnd, ditherLsb)
        }
        return out
    }

    /** 0 within [EDGE_CLEAR_DP] of any screen edge, 1 from [EDGE_CLEAR_DP] + [EDGE_FADE_DP] on. */
    fun edgeWindow(xDp: Float, yDp: Float, screen: ScreenSpec): Float {
        val e = min(min(xDp, screen.widthDp - xDp), min(yDp, screen.heightDp - yDp))
        return smoothstep(EDGE_CLEAR_DP, EDGE_CLEAR_DP + EDGE_FADE_DP, e)
    }

    /** The static wall mask for a print (960 × 540): dark behind it, clear of the screen edges. */
    fun holeMask(shape: PrintShape, screen: ScreenSpec): IntArray =
        mask(screen) { x, y -> min(1f, shape.distance(x, y) * 2f) * edgeWindow(x, y, screen) }

    /** The wall mask without a print: only the clearance at the screen edges. */
    fun openMask(screen: ScreenSpec): IntArray = mask(screen) { x, y -> edgeWindow(x, y, screen) }

    /** Width of the rim around a dark print (dp). */
    const val RIM_DP = 20f

    /**
     * The faint rim of wall light around a dark print, as lightness like the pool: 1 at its
     * edge, gone with zero slope [RIM_DP] out.
     */
    fun rimMap(shape: PrintShape, screen: ScreenSpec): IntArray = mask(screen, encoded = true) { x, y ->
        if (shape.contains(x, y)) {
            0f
        } else {
            val q = (1f - (shape.distance(x, y) / RIM_DP).let { it * it }).coerceAtLeast(0f)
            q * sqrt(q) * edgeWindow(x, y, screen)
        }
    }

    /**
     * Brightness (relative luminance) of the rim for a print whose outer edge has
     * [edgeLuminance]; 0 when the print stands out against the black wall on its own.
     */
    fun rimLuminance(edgeLuminance: Float): Float =
        if (edgeLuminance > 0.03f) 0f else max(0.008f, 2f * edgeLuminance).coerceAtMost(0.015f)

    /** A greyscale map of the screen; [value] is linear light unless it is [encoded] already. */
    private inline fun mask(screen: ScreenSpec, encoded: Boolean = false, value: (Float, Float) -> Float): IntArray {
        val sx = screen.widthDp / MAP_W
        val sy = screen.heightDp / MAP_H
        val out = IntArray(MAP_W * MAP_H)
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val v = value((x + 0.5f) * sx, (y + 0.5f) * sy).coerceIn(0f, 1f)
            val g = ((if (encoded) v else Oklab.linearToSrgb(v)) * 255f).roundToInt()
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

    /** Opaque grey for the sRGB value [v]; pure black (no dither noise) where there is no light. */
    private fun grey(v: Float, rnd: Dither, amplitude: Float): Int {
        val g = if (v < 0.004f) 0 else (v * 255f + (rnd.next() - rnd.next()) * amplitude).roundToInt().coerceIn(0, 255)
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
            // ~3300 K: clearly warm, still light – never below hue 60° (rust).
            Lch(0.70f, (lead.chroma * 0.8f).coerceIn(0.06f, 0.083f), hue.coerceIn(64f, 72f))
        } else {
            // Cool and violet light reads as an LED strip: it is drawn towards the lamp's warm
            // white (in OKLab, so it passes through neutral, never through green) and kept
            // to little chroma – a hint of the cover's colour in lamp light.
            val own = Lch(0.66f, (lead.chroma * 1.25f).coerceIn(0.035f, 0.06f), hue).toLab()
            val mixed = own.lerp(Lch(0.66f, LAMP.c, LAMP.h).toLab(), COOL_TO_LAMP).toLch()
            Lch(mixed.l, mixed.c.coerceAtMost(COOL_CHROMA), mixed.h)
        }
    }

    /** How far cool light is drawn towards the lamp's warm white. */
    private const val COOL_TO_LAMP = 0.4f

    /** Chroma limit of cool light at the reference lightness (≈ 0.02 at the glow's level). */
    private const val COOL_CHROMA = 0.034f

    /** Light for neutral covers (black and white, greys): warm white, like a 2700–3000 K bulb. */
    val LAMP = Lch(0.70f, 0.045f, 72f)

    /** Hue of plain lamp light. */
    const val LAMP_HUE = 72f

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
        // After sunset the light warms towards the lamp's white – in OKLab, so cool light
        // passes through neutral on its way, never through green.
        val base = color.toLab().lerp(Lch(color.l, LAMP.c, LAMP.h).toLab(), 0.35f * warm).toLch()
        var chroma = base.c * (1f - 1.25f * night)
        val luminance = peak * (1f - 1.8f * night).coerceAtLeast(0.28f)
        val maxSaturation = 1f - 1.25f * night
        var argb = scaledTo(Lch(base.l, chroma, base.h), luminance)
        repeat(12) {
            if (hsvSaturation(argb) <= maxSaturation + 1e-3f) return argb
            chroma *= 0.85f
            argb = scaledTo(Lch(base.l, chroma, base.h), luminance)
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
