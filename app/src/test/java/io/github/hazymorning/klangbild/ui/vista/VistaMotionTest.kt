package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.vista.Place
import io.github.hazymorning.klangbild.vista.VistaClock
import io.github.hazymorning.klangbild.vista.Weather
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Fensterblick in motion, as sequences of stills: a few seconds frame by frame (the grass
 * and the crown sway, the clouds drift, nothing jumps), a minute in steps of ten seconds, and
 * the time-lapse – a day in two minutes – in steps of five seconds.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class VistaMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val berlin = Place(52.52, 13.40)

    private fun run(name: String, hour: Int, minute: Int, frames: Int, stepMs: Long, timelapse: Boolean = false, cover: Float = 0.6f) {
        val start = java.time.ZonedDateTime.of(2026, 10, 10, hour, minute, 0, 0, Fixtures.zone).toInstant().toEpochMilli()
        var wall = start
        val clock = VistaClock(now = { wall })
        if (timelapse) clock.timelapse(true)
        val feed = VistaFeed(place = { berlin }, weather = { Weather(cover = cover) }, clock = clock, zone = { Fixtures.zone }, wall = { wall })
        compose.mainClock.autoAdvance = false
        compose.setContent { Fensterblick(feed, Modifier.fillMaxSize()) }
        // The picture is baked off the main thread: give it the time it takes, then let it fade in.
        repeat(8) {
            Thread.sleep(400)
            compose.mainClock.advanceTimeBy(16)
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/screenshots/vista/motion/${name}_00.png")
        for (i in 1..frames) {
            wall += stepMs
            compose.mainClock.advanceTimeBy(stepMs)
            compose.onRoot().captureRoboImage("build/screenshots/vista/motion/${name}_%02d.png".format(i))
        }
    }

    @Test
    fun breeze() = run("breeze", 15, 30, frames = 12, stepMs = 250)

    @Test
    fun minute() = run("minute", 17, 40, frames = 6, stepMs = 10_000)

    @Test
    fun timelapse() = run("timelapse", 6, 0, frames = 12, stepMs = 5_000, timelapse = true, cover = 0.3f)
}
