package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sundial: the same scene at the start, middle and end of a track. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class SundialTest {
    private val mdpi = io.github.hazymorning.klangbild.render.ScreenSpec(960, 540, 1f)

    private fun at(fraction: Float, name: String) {
        val track = Fixtures.track(SyntheticCover.MOOS)
        val model = Fixtures.model(track, positionMs = (track.durationMs!! * fraction).toLong(), screen = mdpi)
        captureRoboImage("build/screenshots/sundial/$name.png") { HaloStage(model, wallClockMs = { 0L }) }
    }

    @Test fun start() = at(0.03f, "a_start")
    @Test fun middle() = at(0.5f, "b_middle")
    @Test fun end() = at(0.97f, "c_end")
}
