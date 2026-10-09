package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The light wall around the artwork: paper in the wall's colour, and the print's shadow as a
 * print on a wall in daylight casts it – soft, falling below it (light comes from above), with
 * a faint contact shadow along every edge so a pale cover never dissolves into the paper.
 *
 * Computed once per scene as a dithered bitmap, like [WallGlow]; drawing it is one textured
 * rectangle that never moves.
 */
object WallShade {
    /** The cast shadow: the print's outline moved down by this much (dp) … */
    const val CAST_DROP_DP = 10f

    /** … blurred with this σ (dp) … */
    const val CAST_SIGMA_DP = 16f

    /** … and at most this much darker (multiplied, linear light). */
    const val CAST_DEPTH = 0.2f

    /** The contact shadow along every edge: σ (dp) and depth. */
    const val CONTACT_SIGMA_DP = 2.5f
    const val CONTACT_DEPTH = 0.12f

    /**
     * The wall for a print of [shape] on [screen]: [wall] (sRGB ARGB) shaded by the print's
     * shadow. Returns opaque ARGB pixels of [WallGlow.MAP_W] × [WallGlow.MAP_H] covering the
     * whole screen.
     */
    fun field(wall: Int, shape: PrintShape, screen: ScreenSpec): IntArray {
        val w = WallGlow.MAP_W
        val h = WallGlow.MAP_H
        val img = LinearImage(w, h)
        val r = Oklab.srgbToLinear(wall shr 16)
        val g = Oklab.srgbToLinear(wall shr 8)
        val b = Oklab.srgbToLinear(wall)
        val sx = screen.widthDp / w
        val sy = screen.heightDp / h
        for (y in 0 until h) {
            val py = (y + 0.5f) * sy
            for (x in 0 until w) {
                val px = (x + 0.5f) * sx
                val cast = CAST_DEPTH * cover(shape.signedDistance(px, py - CAST_DROP_DP), CAST_SIGMA_DP)
                val contact = CONTACT_DEPTH * cover(shape.signedDistance(px, py), CONTACT_SIGMA_DP)
                val k = (1f - cast) * (1f - contact)
                val o = y * w + x
                img.r[o] = r * k
                img.g[o] = g * k
                img.b[o] = b * k
            }
        }
        return ImageOps.toArgbDithered(img, seed = 17, ditherLsb = 0.8f)
    }

    /** How much of a soft edge at signed distance [d] covers a point: 1 well inside, ½ on the edge, 0 well outside. */
    internal fun cover(d: Float, sigma: Float): Float = 0.5f * erfc(d / (sigma * SQRT2))

    private val SQRT2 = sqrt(2f)

    /** Complementary error function (Abramowitz & Stegun 7.1.26, error < 1.5e-7). */
    private fun erfc(x: Float): Float {
        val z = abs(x)
        val t = 1f / (1f + 0.3275911f * z)
        val poly = t * (0.2548296f + t * (-0.28449672f + t * (1.4214138f + t * (-1.4531521f + t * 1.0614054f))))
        val e = poly * exp(-z * z)
        return if (x >= 0f) e else 2f - e
    }
}
