package io.github.hazymorning.klangbild.art

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Artwork for music without a cover (radio, local files): a Chladni figure – the pattern
 * sand forms on a vibrating plate. Literally a picture of sound, which is what the app's
 * name means. Every station or album gets its own stable figure and colours.
 *
 * Pure Kotlin (returns ARGB pixels), so it renders identically on the JVM and the device
 * and can be produced at the exact on-screen pixel size – no upscaling, ever.
 */
object ChladniArt {

    data class Spec(val n: Int, val m: Int, val mix: Float, val plate: Int, val sand: Int)

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
        val n = 2 + rnd.nextInt(4)
        val m = n + 2 + rnd.nextInt(4)
        val mix = if (rnd.nextBoolean()) 1f else -1f
        val (plate, sand) = palettes[rnd.nextInt(palettes.size)]
        return Spec(n, m, mix, plate, sand)
    }

    fun render(spec: Spec, size: Int, seed: Int = 1): IntArray {
        val out = IntArray(size * size)
        val rnd = Random(seed)
        val pr = (spec.plate shr 16) and 0xFF
        val pg = (spec.plate shr 8) and 0xFF
        val pb = spec.plate and 0xFF
        val sr = (spec.sand shr 16) and 0xFF
        val sg = (spec.sand shr 8) and 0xFF
        val sb = spec.sand and 0xFF
        val n = spec.n.toFloat()
        val m = spec.m.toFloat()
        val pi = PI.toFloat()
        // Line width in plate units; dividing |f| by |∇f| gives the distance to the nodal
        // line, so lines keep an even width instead of blooming where they cross.
        val lineWidth = 1.6f / size + 0.0016f
        for (y in 0 until size) {
            val v = (y + 0.5f) / size
            for (x in 0 until size) {
                val u = (x + 0.5f) / size
                // inset the plate a little so the figure breathes inside the square
                val px = 0.06f + u * 0.88f
                val py = 0.06f + v * 0.88f
                val cnx = cos(n * pi * px)
                val cmx = cos(m * pi * px)
                val cny = cos(n * pi * py)
                val cmy = cos(m * pi * py)
                val f = cnx * cmy + spec.mix * cmx * cny
                val fx = -n * pi * sin(n * pi * px) * cmy - spec.mix * m * pi * sin(m * pi * px) * cny
                val fy = -m * pi * cnx * sin(m * pi * py) - spec.mix * n * pi * cmx * sin(n * pi * py)
                val dist = abs(f) / max(hypot(fx, fy), 0.35f)
                val line = exp(-(dist / lineWidth) * (dist / lineWidth))
                val halo = exp(-(dist / (lineWidth * 4f)) * (dist / (lineWidth * 4f)))
                // sand: stippled grains, denser on the nodal lines
                val grain = if (rnd.nextFloat() < line * 0.92f) 1f else 0f
                val dust = if (rnd.nextFloat() < halo * 0.07f) 0.6f else 0f
                // plate: soft vignette and a faint brushed-metal texture
                val d = hypot(u - 0.5f, v - 0.46f)
                val vignette = 1.08f - 0.55f * d * d
                val tex = 1f + (rnd.nextFloat() - 0.5f) * 0.05f
                val k = (grain * 0.88f + dust + line * 0.12f).coerceAtMost(1f)
                val r = (pr * vignette * tex) * (1f - k) + sr * k
                val g = (pg * vignette * tex) * (1f - k) + sg * k
                val b = (pb * vignette * tex) * (1f - k) + sb * k
                out[y * size + x] = (0xFF shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
        return out
    }
}
