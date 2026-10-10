package io.github.hazymorning.klangbild.vista

import kotlin.math.ceil
import kotlin.math.floor

/** A cloud strip in the sky right now: which design, its left end and its top (dp), its scale and how much of it is there. */
class CloudPlace(val design: Int, val x: Float, val y: Float, val scale: Float, val alpha: Float)

/**
 * The clouds: four lanes at different heights, each an endless procession of paper strips
 * drifting from west to east – right to left, as the weather comes in from the Atlantic –
 * the high lanes faster, the low ones (farther away) slower and smaller. Every strip's design,
 * place and the cover it needs come from its number, never from a random draw, and no two
 * lanes share a rhythm: the sky never repeats. When the cover changes, strips dissolve or form
 * where they are, as fast as the cover itself changes (slowly) – nothing appears from nowhere.
 */
object CloudField {
    private class Lane(val y: Float, val speed: Float, val spacing: Float, val scale: Float, val seed: Int)

    private val lanes = listOf(
        Lane(y = 76f, speed = 1.25f, spacing = 430f, scale = 1f, seed = 11),
        Lane(y = 124f, speed = 0.95f, spacing = 380f, scale = 0.86f, seed = 23),
        Lane(y = 172f, speed = 0.72f, spacing = 340f, scale = 0.74f, seed = 37),
        Lane(y = 216f, speed = 0.52f, spacing = 300f, scale = 0.62f, seed = 51),
    )

    /** How full the sky is for a [cover] (see [WeatherMood]): a clear day keeps a strip now and then, an overcast one is full. */
    fun density(cover: Float): Float = cover.coerceIn(0f, 1f).let { 0.07f + 0.86f * it * it }

    /** The strips in the picture at [seconds] (a steady clock, any origin) for a sky [density] 0..1. */
    fun places(seconds: Double, density: Float, out: MutableList<CloudPlace> = ArrayList()): List<CloudPlace> {
        out.clear()
        val left = -PaperMotif.OVERSCAN - 420f
        val right = PaperMotif.WIDTH + PaperMotif.OVERSCAN + 20f
        for (lane in lanes) {
            // The lane's procession has moved this far to the left.
            val shift = seconds * lane.speed
            val first = floor((left + shift) / lane.spacing).toLong() - 1
            val last = ceil((right + shift) / lane.spacing).toLong()
            for (k in first..last) {
                val h = hash(k, lane.seed)
                val need = (h and 0xFFFF) / 65535f
                val alpha = Shapes.smoothstep(need - 0.06f, need + 0.06f, density)
                if (alpha <= 0.003f) continue
                val design = ((h ushr 16) and 0xFF) % PaperBake.CLOUD_DESIGNS
                val jitterX = (((h ushr 24) and 0x7F) / 127f - 0.5f) * lane.spacing * 0.5f
                val jitterY = ((hash(k, lane.seed + 7) and 0xFF) / 255f - 0.5f) * 18f
                val x = (k * lane.spacing + jitterX - shift).toFloat()
                if (x > right || x < left) continue
                out += CloudPlace(design, x, lane.y + jitterY, lane.scale, alpha)
            }
        }
        return out
    }

    /** A well-mixed hash of a strip's number and its lane. */
    private fun hash(k: Long, seed: Int): Int {
        var x = k * -0x61c8864680b583ebL + seed * 0x9E3779B97F4A7C15uL.toLong()
        x = (x xor (x ushr 33)) * -0xae502812aa7333L
        x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
        return (x xor (x ushr 33)).toInt()
    }
}
