package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.ui.stage.HaloLightLayer
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import org.junit.Rule
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

    @get:Rule
    val compose = createComposeRule()

    /** The light's shaders are made once per scene: a moving sun must still move the pool. */
    @Test
    fun theLampFollowsTheSunWithinOneComposition() {
        val model = Fixtures.model(Fixtures.track(SyntheticCover.MOOS), screen = mdpi)
        var sun by mutableFloatStateOf(225f)
        compose.setContent { HaloLightLayer(model.scene, sunAngle = { sun }) }
        fun centreOfLight(): Pair<Float, Float> {
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            var sx = 0.0
            var sy = 0.0
            var sw = 0.0
            for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
                val g = (bmp.getPixel(x, y) shr 8) and 0xFF
                sx += x.toDouble() * g
                sy += y.toDouble() * g
                sw += g
            }
            return (sx / sw).toFloat() to (sy / sw).toFloat()
        }
        val print = model.scene.shape
        val (x1, y1) = centreOfLight()
        assertThat(x1).isLessThan(print.centerX) // rising: lower left
        assertThat(y1).isGreaterThan(print.centerY)
        sun = -45f
        compose.waitForIdle()
        val (x2, y2) = centreOfLight()
        assertThat(x2).isGreaterThan(print.centerX) // setting: lower right
        assertThat(y2).isGreaterThan(print.centerY)
    }
}
