package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import io.github.hazymorning.klangbild.color.ScenePalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The light of a Halo scene, precomputed once per track (and when the time of day changes).
 *
 * Two parts:
 *  - the **wall**: a dark, faintly tinted wall with a tight glow hugging the cover, as if
 *    a lamp were hidden behind a picture. Computed analytically from the distance to the
 *    cover edge (no blur needed), in linear light, dithered → [backdrop].
 *  - the **sun**: a soft directional light behind the cover that wanders over the course of
 *    the track like the sun over a sundial ([sunAngle]). It is a small greyscale sprite,
 *    tinted and positioned at draw time – moving it costs one transformed quad.
 */
object HaloLight {
    const val BACKDROP_W = 960
    const val BACKDROP_H = 540
    const val SPRITE = 384

    /** Distance (dp) over which the glow falls off; it is gone after ~3σ. */
    const val GLOW_SIGMA_DP = 44f

    /** The colour of the light: the cover's leading colour, tamed; amber for neutral covers. */
    fun lightColor(analysis: CoverAnalysis, palette: ScenePalette): Int {
        val hue = palette.tintHue
        if (hue == null || analysis.kind == CoverKind.MONOCHROME) {
            return Lch(0.74f, 0.075f, CoverPalette.WARM_HUE).toArgb() // ~2700 K lamp light
        }
        val lead = analysis.clusters
            .filter { it.lab.chroma >= 0.035f && it.weight >= 0.02f }
            .maxByOrNull { it.weight.toDouble().pow(0.55) * it.lab.chroma }
        val c = (lead?.lab?.chroma ?: 0.08f).coerceIn(0.05f, 0.13f)
        return Lch(0.72f, c, hue).toArgb()
    }

    /**
     * Peak brightness of the glow at the cover edge as relative luminance. Brighter covers
     * already throw light themselves and get less; dark covers still get a visible rim.
     */
    fun glowPeak(analysis: CoverAnalysis): Float =
        (0.072f - 0.032f * analysis.meanL).coerceIn(0.03f, 0.072f)

    /**
     * Renders the wall with the glow around [cover] (dp), for a screen of [screen].
     */
    fun backdrop(
        palette: ScenePalette,
        light: Int,
        peak: Float,
        cover: RectDp,
        screen: ScreenSpec,
        lighting: Lighting,
        seed: Int,
        outW: Int = BACKDROP_W,
        outH: Int = BACKDROP_H,
    ): IntArray {
        val base = Oklab.toLinear(Oklab.fromArgb(palette.base))
        val lit = Oklab.toLinear(lighting.apply(Oklab.fromArgb(light)))
        val lum = 0.2126f * lit[0] + 0.7152f * lit[1] + 0.0722f * lit[2]
        val k = if (lum > 0f) peak / lum else 0f
        val night = (1f - lighting.brightness).coerceIn(0f, 1f) // dims the light more than the text
        val gain = k * (1f - 1.4f * night).coerceAtLeast(0.25f)
        val img = LinearImage(outW, outH)
        val sx = screen.widthDp / outW
        val sy = screen.heightDp / outH
        for (y in 0 until outH) {
            val py = (y + 0.5f) * sy
            val dy = max(max(cover.top - py, py - cover.bottom), 0f)
            for (x in 0 until outW) {
                val px = (x + 0.5f) * sx
                val dx = max(max(cover.left - px, px - cover.right), 0f)
                val d = sqrt(dx * dx + dy * dy)
                // Behind the cover the wall stays dark: when a cover fades during a
                // transition it dims to the wall instead of revealing a glowing patch.
                val inside = px > cover.left + 1f && px < cover.right - 1f && py > cover.top + 1f && py < cover.bottom - 1f
                val g = if (inside) 0f else exp(-(d / GLOW_SIGMA_DP).toDouble().pow(1.35)).toFloat() * gain
                val o = y * outW + x
                img.r[o] = base[0] + lit[0] * g
                img.g[o] = base[1] + lit[1] * g
                img.b[o] = base[2] + lit[2] * g
            }
        }
        return ImageOps.toArgbDithered(img, seed)
    }

    /**
     * A soft round light (greyscale, white = full intensity) with a Gaussian-like core and a
     * long shoulder. Drawn tinted, additively, under the cover.
     */
    fun sunSprite(size: Int = SPRITE, seed: Int = 7): IntArray {
        val img = LinearImage(size, size)
        val alpha = FloatArray(size * size)
        val c = (size - 1) / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val r = sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c // 0..1 at the edge
            val v = if (r >= 1f) 0f else exp(-(r / 0.42f) * (r / 0.42f)) * (1f - r * r)
            val o = y * size + x
            img.r[o] = v; img.g[o] = v; img.b[o] = v
            alpha[o] = 1f
        }
        return ImageOps.toArgbDithered(img, seed, alpha)
    }

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

    /** Centre of the sun (dp) for [angleDeg] around [cover]. */
    fun sunCenter(cover: RectDp, angleDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val reach = cover.width * 0.36f
        return (cover.centerX + (cos(rad) * reach).toFloat()) to (cover.centerY - (sin(rad) * reach).toFloat())
    }

    /** Diameter of the sun sprite on screen (dp). */
    fun sunDiameter(cover: RectDp) = cover.width * 1.6f

    /** sRGB lift at the centre of the sun (0..1 of full scale) by day. */
    const val SUN_LIFT = 0.11f
}
