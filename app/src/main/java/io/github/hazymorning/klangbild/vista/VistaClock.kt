package io.github.hazymorning.klangbild.vista

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The moment whose sky the Fensterblick shows. Normally the real one; the preview can set an
 * hour, a time of year and a sky, or let a whole day pass in [TIMELAPSE_DAY_MS]. Whatever is
 * set, the picture glides there – the light never jumps, not even in the preview.
 */
class VistaClock(private val now: () -> Long = System::currentTimeMillis, private val zone: () -> ZoneId = ZoneId::systemDefault) {

    /** What the preview has set; null fields follow the real world. */
    data class Preview(
        /** Minutes after midnight, or null for the real time of day. */
        val minuteOfDay: Int? = null,
        /** Day of the year (northern; 0 = 1 January), or null for the real date. */
        val dayOfYear: Int? = null,
        /** The sky's cover 0..1, or null for the weather's. */
        val cover: Float? = null,
        /** A day in two minutes. */
        val timelapse: Boolean = false,
    ) {
        val active: Boolean get() = minuteOfDay != null || dayOfYear != null || cover != null || timelapse
    }

    @Volatile
    var preview: Preview = Preview()
        private set

    // The glide from where the picture was to where it is going (sky time, ms).
    private var glideFrom = 0L
    private var glideTo = 0L
    private var glideStart = 0L
    private var glideMs = 0L
    private var timelapseAnchorSky = 0L
    private var timelapseAnchorWall = 0L

    /** Sets the preview; the picture glides from what it shows now to the new moment. */
    @Synchronized
    fun set(p: Preview) {
        val wall = now()
        val from = skyAt(wall)
        preview = p
        val to = target(wall)
        glideFrom = from
        glideTo = to
        glideStart = wall
        // Longer for a bigger turn of the clock, never abrupt, never long.
        glideMs = (1_200L + abs(to - from) / 3_600_000L * 160L).coerceAtMost(3_200L)
        if (p.timelapse) {
            timelapseAnchorSky = to
            timelapseAnchorWall = wall + glideMs
        }
    }

    /** Back to the real world, gliding there. */
    fun real() = set(Preview())

    /** The moment whose sky is shown now (epoch ms). */
    @Synchronized
    fun sky(): Long = skyAt(now())

    private fun skyAt(wall: Long): Long {
        if (glideMs > 0L && wall < glideStart + glideMs) {
            val t = ((wall - glideStart).toFloat() / glideMs).coerceIn(0f, 1f)
            val e = t * t * (3 - 2 * t)
            // While gliding the target itself moves on (real time or the time-lapse): follow it.
            val to = target(wall)
            return glideFrom + ((to - glideFrom) * e).toLong()
        }
        return target(wall)
    }

    private fun target(wall: Long): Long {
        val p = preview
        if (p.timelapse && timelapseAnchorWall > 0L) {
            return timelapseAnchorSky + ((wall - timelapseAnchorWall).coerceAtLeast(0L) * (DAY_MS / TIMELAPSE_DAY_MS.toDouble())).toLong()
        }
        if (p.minuteOfDay == null && p.dayOfYear == null) return wall
        var date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wall), zone())
        p.dayOfYear?.let { d -> date = date.withDayOfYear((d + 1).coerceIn(1, date.toLocalDate().lengthOfYear())) }
        p.minuteOfDay?.let { m -> date = date.withHour(m / 60 % 24).withMinute(m % 60).withSecond(0).withNano(0) }
        return date.toInstant().toEpochMilli()
    }

    /**
     * The sky's cover to show for the weather's [real] cover: the preview's if it set one,
     * else the weather's, else a fair sky.
     */
    fun cover(real: Float?): Float = preview.cover ?: real ?: WeatherMood.FAIR

    companion object {
        const val DAY_MS = 86_400_000L

        /** In the time-lapse a day passes in two minutes. */
        const val TIMELAPSE_DAY_MS = 120_000L
    }
}
