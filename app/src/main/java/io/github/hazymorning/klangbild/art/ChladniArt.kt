package io.github.hazymorning.klangbild.art

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Artwork for music without a cover (radio, local files): a Chladni figure – the pattern
 * sand forms on a vibrating plate. Literally a picture of sound, which is what the app's
 * name means. Every station or album gets its own stable figure and colours.
 *
 * The plate is round, seen from above, lying on a dark ground: two nearly equal vibration
 * modes (Bessel functions in the radius, cosines around it) are superposed, which gives the
 * curved, flower-like figures of real sand photographs instead of a grid of lines.
 *
 * Pure Kotlin (returns ARGB pixels), so it renders identically on the JVM and the device
 * and can be produced at the exact on-screen pixel size – no upscaling, ever.
 */
object ChladniArt {

    data class Spec(
        val n1: Int,
        val k1: Float,
        val n2: Int,
        val k2: Float,
        val mix: Float,
        val turn: Float,
        val plate: Int,
        val sand: Int,
    )

    private val palettes = listOf(
        0xFF121B2B.toInt() to 0xFFEADDC2.toInt(), // ink blue / sand
        0xFF0F2320.toInt() to 0xFFDCE7D3.toInt(), // deep green / pale sage
        0xFF221426.toInt() to 0xFFF1DACB.toInt(), // aubergine / blush
        0xFF1B1716.toInt() to 0xFFE6C597.toInt(), // warm charcoal / brass
        0xFF2A1113.toInt() to 0xFFF2DECB.toInt(), // oxblood / ivory
        0xFF19212A.toInt() to 0xFFD5E2E8.toInt(), // slate / mist
    )

    fun specFor(seedText: String): Spec {
        val rnd = Random(stableHash(seedText.ifBlank { "Klangbild" }))
        val n1 = 2 + rnd.nextInt(4) // 2..5 nodal diameters
        val rings = 2 + rnd.nextInt(2) // 2..3 nodal circles
        val n2 = n1 + 2 + rnd.nextInt(3)
        // McMahon: the m-th zero of J_n lies near (m + n/2 − 1/4)·π.
        val k1 = ((rings + n1 / 2f - 0.25f) * PI).toFloat()
        val k2 = k1 * (0.94f + 0.12f * rnd.nextFloat())
        val mix = (0.45f + 0.4f * rnd.nextFloat()) * (if (rnd.nextBoolean()) 1f else -1f)
        val turn = rnd.nextFloat() * (PI / n2).toFloat()
        val (plate, sand) = palettes[rnd.nextInt(palettes.size)]
        return Spec(n1, k1, n2, k2, mix, turn, plate, sand)
    }

    /** Plate radius as a share of half the image. */
    private const val PLATE = 0.84f

    /** Radius of the central clamp (plate units). */
    private const val MOUNT = 0.035f

    fun render(spec: Spec, size: Int, seed: Int = 1): IntArray {
        val out = IntArray(size * size)
        val rnd = Random(seed)
        val j1 = besselTable(spec.n1)
        val j2 = besselTable(spec.n2)
        val phase2 = spec.n2 * spec.turn

        val pr = (spec.plate shr 16) and 0xFF
        val pg = (spec.plate shr 8) and 0xFF
        val pb = spec.plate and 0xFF
        val sr = (spec.sand shr 16) and 0xFF
        val sg = (spec.sand shr 8) and 0xFF
        val sb = spec.sand and 0xFF
        // Line width in plate units; dividing |f| by |∇f| gives the distance to the nodal
        // line, so lines keep an even width instead of blooming where they cross.
        val lineWidth = 2.2f / (PLATE / 2f * size) + 0.004f
        for (y in 0 until size) {
            val v = ((y + 0.5f) / size) * 2f - 1f
            for (x in 0 until size) {
                val u = ((x + 0.5f) / size) * 2f - 1f
                val r = hypot(u, v) / PLATE
                val o = y * size + x
                if (r >= 1f) {
                    // The ground: darker than the plate, with the plate's soft shadow.
                    val sd = hypot(u - 0.025f, v - 0.035f) / PLATE
                    val shadow = 1f - 0.45f * exp(-((sd - 1f) / 0.05f).let { it * it }) * (if (sd > 1f) 1f else 0f)
                    val tex = 1f + (rnd.nextFloat() - 0.5f) * 0.04f
                    val k = 0.52f * shadow * tex
                    out[o] = argb(pr * k, pg * k, pb * k)
                    continue
                }
                if (r < MOUNT) {
                    // The plate is clamped in its centre: a small dark bolt with a lit edge.
                    val edge = exp(-((MOUNT - r) / 0.006f).let { it * it }) * 0.5f
                    val k = 0.45f + 0.25f * (1f - r / MOUNT)
                    out[o] = argb(pr * k + 255f * edge * 0.35f, pg * k + 255f * edge * 0.35f, pb * k + 255f * edge * 0.35f)
                    continue
                }
                val theta = atan2(v, u)
                // f = A(r)·cos(n₁θ) + mix·B(r)·cos(n₂θ + φ) and its gradient in polar form
                val a = j1.at(spec.k1 * r)
                val b = j2.at(spec.k2 * r)
                val da = spec.k1 * j1.slope(spec.k1 * r)
                val db = spec.k2 * j2.slope(spec.k2 * r)
                val c1 = cos(spec.n1 * theta)
                val s1 = sin(spec.n1 * theta)
                val c2 = cos(spec.n2 * theta + phase2)
                val s2 = sin(spec.n2 * theta + phase2)
                val f = a * c1 + spec.mix * b * c2
                val fr = da * c1 + spec.mix * db * c2
                val ft = (-spec.n1 * a * s1 - spec.mix * spec.n2 * b * s2) / max(r, 1e-3f)
                val dist = abs(f) / max(hypot(fr, ft), 0.6f)
                val line = exp(-(dist / lineWidth) * (dist / lineWidth))
                val halo = exp(-(dist / (lineWidth * 4f)) * (dist / (lineWidth * 4f)))
                // sand: stippled grains, denser on the nodal lines; a little dust elsewhere
                // sand slides off the bolt: none right next to it
                val clear = ((r - MOUNT) / 0.05f).coerceIn(0f, 1f)
                val grain = if (rnd.nextFloat() < line * 0.9f * clear) 0.75f + 0.25f * rnd.nextFloat() else 0f
                val dust = if (rnd.nextFloat() < halo * 0.06f + 0.004f) 0.55f else 0f
                // plate: brushed metal, a soft light from the upper left, a bright rim
                val light = 1.06f - 0.22f * hypot(u + 0.25f, v + 0.3f)
                val tex = 1f + (rnd.nextFloat() - 0.5f) * 0.05f
                val rim = exp(-((1f - r) / 0.012f).let { it * it }) * 0.35f
                val k = (grain + dust + line * 0.1f).coerceAtMost(1f)
                val base = light * tex
                val rr = (pr * base + 255f * rim * 0.4f) * (1f - k) + sr * k
                val gg = (pg * base + 255f * rim * 0.4f) * (1f - k) + sg * k
                val bb = (pb * base + 255f * rim * 0.4f) * (1f - k) + sb * k
                out[o] = argb(rr, gg, bb)
            }
        }
        return out
    }

    private fun argb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** J_n on 0..[MAX_X] in steps of [STEP], from Bessel's integral (Simpson's rule). */
    private class BesselTable(val values: FloatArray) {
        fun at(x: Float): Float {
            val p = (x / STEP).coerceIn(0f, (values.size - 2).toFloat())
            val i = p.toInt()
            val t = p - i
            return values[i] * (1f - t) + values[i + 1] * t
        }

        /** dJ/dx by central differences on the table. */
        fun slope(x: Float): Float = (at(x + STEP) - at(x - STEP)) / (2 * STEP)
    }

    private const val STEP = 0.01f
    private const val MAX_X = 40f
    private val tables = HashMap<Int, BesselTable>()

    @Synchronized
    private fun besselTable(n: Int): BesselTable = tables.getOrPut(n) {
        val count = (MAX_X / STEP).toInt() + 2
        val steps = 64 // even, for Simpson
        val values = FloatArray(count)
        for (i in 0 until count) {
            val x = i * STEP
            var sum = 0.0
            for (s in 0..steps) {
                val tau = PI * s / steps
                val w = if (s == 0 || s == steps) 1.0 else if (s % 2 == 1) 4.0 else 2.0
                sum += w * cos(n * tau - x * sin(tau))
            }
            values[i] = (sum * (PI / steps) / 3.0 / PI).toFloat()
        }
        BesselTable(values)
    }
}
