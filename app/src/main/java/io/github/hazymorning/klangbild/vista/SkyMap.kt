package io.github.hazymorning.klangbild.vista

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A star where the picture shows it (dp), its dot's radius (dp) and how bright it is (0..1). */
class StarPlace(val x: Float, val y: Float, val size: Float, val brightness: Float)

/** Where the sun, the moon and the stars stand in the picture (dp), and the moon's phase. */
class SkyPlaces(
    val sunX: Float,
    val sunY: Float,
    val moonX: Float,
    val moonY: Float,
    val moonFraction: Float,
    val stars: List<StarPlace>,
)

/**
 * The sky as the picture shows it: looking south, east on the left and west on the right,
 * [DP_PER_DEGREE_X] dp for every degree of azimuth and [DP_PER_DEGREE_Y] for every degree of
 * elevation above the horizon – a very wide view, so the sun's whole day crosses the picture
 * and even a summer evening's sun sets inside it.
 */
object SkyMap {
    const val DP_PER_DEGREE_X = 3.0f
    const val DP_PER_DEGREE_Y = 5.0f

    fun x(azimuth: Float): Float = PaperMotif.WIDTH / 2f + (azimuth - 180f) * DP_PER_DEGREE_X
    fun y(elevation: Float): Float = PaperMotif.HORIZON - elevation * DP_PER_DEGREE_Y

    /** The places for [s], the stars turned for local sidereal time [lstDeg] at latitude [latDeg]. */
    fun places(s: SkyState, lstDeg: Double = 0.0, latDeg: Double = 51.0): SkyPlaces =
        SkyPlaces(x(s.sunAzimuth), y(s.sunElevation), x(s.moonAzimuth), y(s.moonElevation), s.moonFraction, stars(lstDeg, latDeg))

    /**
     * A fixed field of stars on the celestial sphere (the same every night), turned to the
     * hour: they rise on the left and set on the right, as the real ones do.
     */
    fun stars(lstDeg: Double, latDeg: Double): List<StarPlace> {
        val out = ArrayList<StarPlace>(field.size)
        val lat = Math.toRadians(latDeg)
        for (st in field) {
            val ha = Math.toRadians(lstDeg - st.ra)
            val dec = Math.toRadians(st.dec)
            val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(ha)
            val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
            if (alt < Math.toRadians(1.0)) continue
            val az = atan2(-cos(dec) * sin(ha), sin(dec) * cos(lat) - cos(dec) * sin(lat) * cos(ha))
            val azDeg = (Math.toDegrees(az) + 360.0) % 360.0
            val px = x(azDeg.toFloat())
            val py = y(Math.toDegrees(alt).toFloat())
            if (px < -PaperMotif.OVERSCAN || px > PaperMotif.WIDTH + PaperMotif.OVERSCAN || py < -PaperMotif.OVERSCAN) continue
            out += StarPlace(px, py, st.size, st.brightness)
        }
        return out
    }

    private class Star(val ra: Double, val dec: Double, val size: Float, val brightness: Float)

    /**
     * Stars spread evenly over the sphere (a golden-angle spiral, so no clumps and no gaps),
     * mostly faint, a few bright – like the real distribution, without its constellations.
     */
    private val field: List<Star> = run {
        val n = 420
        val golden = PI * (3.0 - sqrt(5.0))
        List(n) { i ->
            val z = 1.0 - 2.0 * (i + 0.5) / n
            val theta = golden * i
            val dec = Math.toDegrees(asin(z))
            val ra = Math.toDegrees(theta) % 360.0
            // Magnitude classes by a fixed scramble of the index: 1 in 14 bright, 1 in 4 medium.
            val h = (i * 2654435761L ushr 7) % 56
            val (size, bright) = when {
                h < 4 -> 1.55f to 1f
                h < 18 -> 1.15f to 0.78f
                else -> 0.85f to 0.52f
            }
            Star(ra, dec, size, bright)
        }
    }
}
