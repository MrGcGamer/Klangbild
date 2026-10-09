package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OLED: the band next to the screen edges stays black – checked on the rendered pixels of
 * every demo scene at the start, middle and end of its track, not on the light model.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class EdgeBandTest {
    @get:Rule
    val compose = createComposeRule()

    private val mdpi = ScreenSpec(960, 540, 1f)

    @Test
    fun theScreenEdgesStayBlack() {
        val models = Fixtures.tracks.flatMap { track ->
            val scene = Fixtures.scene(track, mdpi)
            listOf(0.02f, 0.5f, 0.98f).map { f ->
                Fixtures.model(track, positionMs = track.durationMs?.let { (it * f).toLong() }, screen = mdpi, scene = scene)
            }
        }
        var model by mutableStateOf(models.first())
        compose.setContent { HaloStage(model, wallClockMs = { 0L }) }
        val band = HaloLight.EDGE_CLEAR_DP.toInt()
        for (m in models) {
            model = m
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            var brightest = 0
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                if (x >= band && x < bmp.width - band && y >= band && y < bmp.height - band) continue
                val p = bmp.getPixel(x, y)
                brightest = maxOf(brightest, (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            }
            assertWithMessage("${m.text.title} at ${m.progress()}").that(brightest).isEqualTo(0)
        }
    }
}
