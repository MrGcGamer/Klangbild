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
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.render.WallGlow
import io.github.hazymorning.klangbild.ui.stage.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OLED: the band next to the screen edges stays black – checked on the rendered pixels of
 * every test scene, not on the light model.
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
        val models = Fixtures.tracks.map { Fixtures.model(it, screen = mdpi) }
        var model by mutableStateOf(models.first())
        compose.setContent { Fixtures.Still { Stage(model) } }
        val band = WallGlow.EDGE_BLACK_DP.toInt()
        for (m in models) {
            model = m
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            var brightest = 0
            for (y in 0 until h) for (x in 0 until w) {
                if (x >= band && x < w - band && y >= band && y < h - band) continue
                val p = px[y * w + x]
                brightest = maxOf(brightest, (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            }
            assertWithMessage(m.text.title).that(brightest).isEqualTo(0)
        }
    }
}
