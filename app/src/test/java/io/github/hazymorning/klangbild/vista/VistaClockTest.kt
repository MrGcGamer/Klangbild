package io.github.hazymorning.klangbild.vista

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class VistaClockTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val start = ZonedDateTime.of(2026, 10, 10, 13, 37, 0, 0, zone).toInstant().toEpochMilli()
    private var wall = start
    private val clock = VistaClock(now = { wall }, zone = { zone })

    @Test
    fun `without a preview the sky is the real one`() {
        assertThat(clock.sky()).isEqualTo(start)
        wall += 60_000L
        assertThat(clock.sky()).isEqualTo(start + 60_000L)
    }

    @Test
    fun `a preview glides to the hour and day it sets, never jumps`() {
        clock.set(VistaClock.Preview(minuteOfDay = 21 * 60, dayOfYear = 195))
        var prev = clock.sky()
        assertThat(prev).isEqualTo(start)
        var biggest = 0L
        repeat(200) {
            wall += 16L
            val now = clock.sky()
            biggest = maxOf(biggest, abs(now - prev))
            prev = now
        }
        val target = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock.sky()), zone)
        assertThat(target.hour).isEqualTo(21)
        assertThat(target.dayOfYear).isEqualTo(196)
        // The glide's largest step per frame is a small part of the way.
        assertThat(biggest).isLessThan(abs(clock.sky() - start) / 40)
    }

    @Test
    fun `the time-lapse lets a day pass in two minutes`() {
        clock.set(VistaClock.Preview(timelapse = true))
        wall += 4_000L
        val a = clock.sky()
        wall += 60_000L
        val b = clock.sky()
        assertThat((b - a).toDouble()).isWithin(1_000.0).of(VistaClock.DAY_MS / 2.0)
    }

    @Test
    fun `back to the real world it glides too`() {
        clock.set(VistaClock.Preview(minuteOfDay = 2 * 60))
        wall += 5_000L
        clock.real()
        val right = clock.sky()
        wall += 16L
        assertThat(abs(clock.sky() - right)).isLessThan(VistaClock.DAY_MS / 100)
        wall += 5_000L
        assertThat(clock.sky()).isEqualTo(wall)
    }

    @Test
    fun `the preview's sky wins over the weather's, which wins over a fair sky`() {
        assertThat(clock.cover(null)).isEqualTo(WeatherMood.FAIR)
        assertThat(clock.cover(0.8f)).isEqualTo(0.8f)
        clock.set(VistaClock.Preview(cover = 0f))
        assertThat(clock.cover(0.8f)).isEqualTo(0f)
    }
}
