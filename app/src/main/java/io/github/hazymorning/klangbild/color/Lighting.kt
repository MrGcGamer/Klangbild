package io.github.hazymorning.klangbild.color

import kotlin.math.cos

/**
 * Time-of-day lighting applied to every color of the scene, like a dimmable warm lamp.
 *
 * @param brightness multiplier on perceptual lightness (1 = day).
 * @param warmth 0..1, shifts colors toward amber in proportion to their lightness,
 *               so blacks stay black and highlights glow warm.
 */
data class Lighting(val brightness: Float, val warmth: Float) {

    fun apply(lab: Lab): Lab {
        // Highlights dim a little less than shadows, so text keeps its contrast at night.
        val l = lab.l * (brightness + (1f - brightness) * 0.3f * lab.l)
        val w = warmth * l
        return Lab(
            l,
            lab.a * (1f - 0.12f * warmth) + 0.012f * w,
            lab.b * (1f - 0.18f * warmth) + 0.050f * w,
        )
    }

    fun applyArgb(argb: Int): Int = apply(Oklab.fromArgb(argb)).toArgb(argb ushr 24)

    fun lerp(o: Lighting, t: Float) = Lighting(
        brightness + (o.brightness - brightness) * t,
        warmth + (o.warmth - warmth) * t,
    )

    companion object {
        val DAY = Lighting(1f, 0.06f)
        val EVENING = Lighting(0.86f, 0.5f)
        val NIGHT = Lighting(0.6f, 0.78f)

        /**
         * Maps the sun's elevation (degrees, from Home Assistant's `sun.sun`) to lighting:
         * full daylight above ~12°, golden hour and dusk warm up, twilight dims, and real
         * night (sun below −12°) is very restrained.
         */
        fun fromSunElevation(elevation: Double): Lighting {
            val e = elevation.toFloat()
            return when {
                e >= 12f -> DAY
                e >= -4f -> DAY.lerp(EVENING, smoothstep(12f, -4f, e))
                e >= -12f -> EVENING.lerp(NIGHT, smoothstep(-4f, -12f, e))
                else -> NIGHT
            }
        }

        /**
         * Fallback without Home Assistant: a smooth day curve from the local clock
         * (approximate solar elevation peaking at 13:00).
         */
        fun fromLocalTime(hour: Int, minute: Int): Lighting = fromSunElevation(approximateElevation(hour, minute))

        /** The sun's elevation (°) as a smooth day curve peaking at 13:00, up from about 7:40 to 18:20. */
        fun approximateElevation(hour: Int, minute: Int): Double {
            val t = hour + minute / 60f
            return 50.0 * cos((t - 13.0) / 24.0 * 2.0 * Math.PI) - 8.0
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
