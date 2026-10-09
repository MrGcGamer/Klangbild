package io.github.hazymorning.klangbild.vista

/**
 * The colours of one baked layer, like the blocks of a woodblock print inked anew for every
 * hour. A layer stores, per pixel, the weights of [first], [second] and [third] in its red,
 * green and blue channels; whatever weight is left over goes to [base]. Its alpha is coverage.
 * One colour matrix turns the weights into colour, so the light can change every frame
 * without the layer being drawn anew: out = base + r·(first − base) + g·(second − base) +
 * b·(third − base). Colours are sRGB-encoded ARGB ints; the mix happens in sRGB, like ink.
 */
data class Inks(val base: Int, val first: Int, val second: Int, val third: Int) {

    /** The 4 × 5 colour matrix (Android layout, offsets in 0..255) with the layer's alpha scaled by [alpha]. */
    fun matrix(alpha: Float = 1f, out: FloatArray = FloatArray(20)): FloatArray {
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            val b = (base shr shift and 0xFF) / 255f
            val f = (first shr shift and 0xFF) / 255f
            val s = (second shr shift and 0xFF) / 255f
            val t = (third shr shift and 0xFF) / 255f
            val row = c * 5
            out[row] = f - b
            out[row + 1] = s - b
            out[row + 2] = t - b
            out[row + 3] = 0f
            out[row + 4] = b * 255f
        }
        out[15] = 0f
        out[16] = 0f
        out[17] = 0f
        out[18] = alpha
        out[19] = 0f
        return out
    }

    /** The colour a pixel with weights [r], [g], [b] (0..1) gets – what the matrix computes. */
    fun mix(r: Float, g: Float, b: Float): Int {
        var out = 0xFF shl 24
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            val v0 = (base shr shift and 0xFF).toFloat()
            val v = v0 + r * ((first shr shift and 0xFF) - v0) + g * ((second shr shift and 0xFF) - v0) + b * ((third shr shift and 0xFF) - v0)
            out = out or (v.toInt().coerceIn(0, 255) shl shift)
        }
        return out
    }

    companion object {
        /** All four the same: a flat colour. */
        fun flat(argb: Int) = Inks(argb, argb, argb, argb)
    }
}
