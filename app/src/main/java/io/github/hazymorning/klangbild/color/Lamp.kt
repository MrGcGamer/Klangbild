package io.github.hazymorning.klangbild.color

/**
 * The light every color on screen is seen in: a lamp by day, a breath warm. It shifts colors a
 * little toward amber in proportion to their lightness, so blacks stay black. The stage looks
 * the same at every hour; only the wall and the lights behind the TV follow the sun.
 */
object Lamp {
    /** How warm the lamp is (0..1). */
    const val WARMTH = 0.06f

    fun apply(lab: Lab): Lab {
        val w = WARMTH * lab.l
        return Lab(
            lab.l,
            lab.a * (1f - 0.12f * WARMTH) + 0.012f * w,
            lab.b * (1f - 0.18f * WARMTH) + 0.050f * w,
        )
    }

    fun applyArgb(argb: Int): Int = apply(Oklab.fromArgb(argb)).toArgb(argb ushr 24)
}
