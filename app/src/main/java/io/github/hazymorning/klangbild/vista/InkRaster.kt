package io.github.hazymorning.klangbild.vista

import kotlin.math.roundToInt

/**
 * A layer while it is being baked: per pixel the weights of the three inks (see [Inks]) in
 * red, green and blue and the coverage in alpha, packed like the bitmap the screen draws
 * (unpremultiplied ARGB, four bytes a pixel – a 4K layer stays affordable). Shapes are laid on
 * it like paper cut-outs: what comes later covers what is below, anti-aliased by its
 * coverage. Weights given with [dither] get a triangular dither of about one level, so slow
 * ramps do not band (fixed seed: the same picture every time).
 */
class InkRaster(val width: Int, val height: Int, seed: Int = 1) {
    val pixels = IntArray(width * height)
    private var s = seed * -0x61c88647 or 1

    private fun rnd(): Float {
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        return (s ushr 8) / 16777216f
    }

    /** Lays weights ([wr], [wg], [wb]) over pixel ([x], [y]) with [coverage] 0..1. */
    fun over(x: Int, y: Int, wr: Float, wg: Float, wb: Float, coverage: Float, dither: Boolean = false) {
        if (coverage <= 0.001f || x < 0 || y < 0 || x >= width || y >= height) return
        val i = y * width + x
        val c = coverage.coerceAtMost(1f)
        val p = pixels[i]
        val a0 = (p ushr 24) / 255f
        val out = c + a0 * (1f - c)
        // Weights are colour-like: they mix by coverage, like paint over paint.
        val k0 = a0 * (1f - c) / out
        val k1 = c / out
        val d = if (dither) 255f else 0f
        fun mix(old: Int, w: Float): Int {
            val v = old * k0 + w * 255f * k1 + if (d > 0f) rnd() - rnd() else 0f
            return v.roundToInt().coerceIn(0, 255)
        }
        val alpha = (out * 255f).roundToInt().coerceIn(0, 255)
        pixels[i] = (alpha shl 24) or (mix(p shr 16 and 0xFF, wr) shl 16) or (mix(p shr 8 and 0xFF, wg) shl 8) or mix(p and 0xFF, wb)
    }

    /** Multiplies the coverage of every pixel by [mask] at it. */
    fun cut(mask: (x: Int, y: Int) -> Float) {
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            val p = pixels[i]
            if (p ushr 24 == 0) continue
            val m = mask(x, y).coerceIn(0f, 1f)
            pixels[i] = (((p ushr 24) * m).roundToInt() shl 24) or (p and 0xFFFFFF)
        }
    }

    /** The smallest rectangle (x, y, width, height) holding everything with coverage, or null when empty. */
    fun bounds(): IntArray? {
        var x0 = width
        var y0 = height
        var x1 = -1
        var y1 = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] ushr 24 != 0) {
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    y1 = y
                }
            }
        }
        return if (x1 < 0) null else intArrayOf(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }
}
