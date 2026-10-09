package io.github.hazymorning.klangbild.vista

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The performance prototype's picture: as many layers, clouds and water masks as the real
 * Fensterblick will have, with plain shapes. Only for measuring the rendering approach.
 */
object ProtoScene {
    class Layer(val x: Int, val y: Int, val w: Int, val h: Int, val argb: IntArray)

    class Baked(
        val widthPx: Int,
        val heightPx: Int,
        val horizonPx: Int,
        val land: List<Layer>,
        val clouds: List<Layer>,
        val ripples: List<Layer>,
        val grain: Layer,
        val bakeMs: Long,
    )

    fun bake(widthPx: Int, heightPx: Int): Baked {
        val started = System.nanoTime()
        val horizon = (heightPx * 0.61f).roundToInt()
        val land = (0 until 5).map { i -> ridge(widthPx, heightPx, horizon, i) }
        val clouds = (0 until 8).map { i -> cloud(widthPx, i) }
        val ripples = (0 until 3).map { i -> ripples(widthPx, heightPx - horizon, i) }
        val grain = grain()
        return Baked(widthPx, heightPx, horizon, land, clouds, ripples, grain, (System.nanoTime() - started) / 1_000_000)
    }

    private fun ridge(w: Int, h: Int, horizon: Int, i: Int): Layer {
        val top = (h * (0.18f + 0.07f * i)).roundToInt()
        val lh = horizon - top
        val raster = InkRaster(w, lh, seed = 3 + i)
        val heights = FloatArray(w) { x ->
            val u = x / w.toDouble()
            val n = Noise.fbm(u * (2.5 + i) + i * 3.1, 6)
            (lh * (0.35 + 0.45 * (0.5 + 0.5 * n))).toFloat()
        }
        for (x in 0 until w) {
            val ridgeY = lh - heights[x]
            val slope = (heights[min(w - 1, x + 2)] - heights[max(0, x - 2)]) / 4f
            val east = (0.5f + slope * 2f).coerceIn(0f, 1f)
            val start = ridgeY.toInt().coerceAtLeast(0)
            for (y in start until lh) {
                val cov = (y + 1 - ridgeY).coerceIn(0f, 1f)
                val depth = (y - ridgeY) / heights[x].coerceAtLeast(1f)
                val haze = (depth * depth * 0.8f).coerceIn(0f, 1f)
                raster.over(x, y, east * (1f - haze), (1f - east) * (1f - haze) * 0.6f, haze, cov, dither = true)
            }
        }
        return Layer(0, top, w, lh, raster.pixels)
    }

    private fun cloud(screenW: Int, i: Int): Layer {
        val cw = (screenW * (0.16f + 0.05f * (i % 3))).roundToInt()
        val ch = (cw * 0.28f).roundToInt()
        val raster = InkRaster(cw, ch, seed = 11 + i)
        for (y in 0 until ch) for (x in 0 until cw) {
            val u = (x + 0.5f) / cw * 2f - 1f
            val v = (y + 0.5f) / ch * 2f - 1f
            val n = Noise.fbm(u * 3.0 + i * 7.0, v * 2.0, 4).toFloat()
            val d = sqrt(u * u + (v * 1.6f) * (v * 1.6f)) + n * 0.35f
            val cov = ((1f - d) * 6f).coerceIn(0f, 1f)
            if (cov > 0f) raster.over(x, y, (0.5f - v * 0.5f).coerceIn(0f, 1f), (0.5f + u * 0.3f).coerceIn(0f, 1f), (abs(v)).coerceIn(0f, 1f) * 0.3f, cov)
        }
        return Layer(0, 0, cw, ch, raster.pixels)
    }

    private fun ripples(w: Int, h: Int, i: Int): Layer {
        val raster = InkRaster(w, h, seed = 21 + i)
        var s = 1234567 + i * 7919
        fun rnd(): Float {
            s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
            return (s ushr 8) / 16777216f
        }
        repeat(260) {
            val y = (rnd() * h).toInt()
            val depth = y / h.toFloat()
            val len = (w * (0.02f + 0.08f * rnd()) * (0.4f + depth)).roundToInt()
            val x0 = (rnd() * w).toInt()
            val thick = 1 + (depth * 3f).toInt()
            for (dy in 0 until thick) for (dx in 0 until len) {
                val e = 1f - abs(dx - len / 2f) / (len / 2f)
                raster.over(x0 + dx, y + dy, 1f, 0f, 0f, e.coerceIn(0f, 1f) * 0.8f)
            }
        }
        return Layer(0, 0, w, h, raster.pixels)
    }

    private fun grain(): Layer {
        val side = 128
        var s = 99
        val px = IntArray(side * side) {
            s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
            val v = 255 - ((s ushr 8) % 9)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Layer(0, 0, side, side, px)
    }

    /** A soft radial falloff used for the sun's glow, 0..1. */
    fun glow(d: Float): Float = exp(-d * d * 3f)
}
