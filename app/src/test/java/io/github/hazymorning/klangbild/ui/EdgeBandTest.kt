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
import io.github.hazymorning.klangbild.ui.flow.FlowStage
import io.github.hazymorning.klangbild.ui.stage.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OLED: the band next to the screen edges stays black – checked on the rendered pixels of
 * every test scene, in both views and behind the menu, not on the light model.
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
        for (m in models) {
            model = m
            assertWithMessage(m.text.title).that(brightestInBand()).isEqualTo(0)
        }
    }

    @Test
    fun theScreenEdgesStayBlackInCoverFlow() {
        // Every test cover once, five to a strip: the cards beside the middle reach into the band.
        val strips = Fixtures.tracks.chunked(5).filter { it.size == 5 }
        val scenes = strips.map { Fixtures.flowScenes(it, screen = mdpi) }
        var strip by mutableStateOf(0)
        compose.setContent {
            Fixtures.Still {
                val s = scenes[strip]
                FlowStage(Fixtures.model(strips[strip][2], screen = mdpi, scene = s[2]), Fixtures.restingCards(s, 2))
            }
        }
        for (i in strips.indices) {
            strip = i
            assertWithMessage(strips[i][2].title).that(brightestInBand()).isEqualTo(0)
        }
    }

    @Test
    fun theScreenEdgesStayBlackBehindTheMenu() {
        val settings = io.github.hazymorning.klangbild.config.Settings(haUrl = "http://192.168.1.10:8123", haToken = "t", entityId = "media_player.wohnzimmer")
        val rows = io.github.hazymorning.klangbild.config.SettingsMenu.rows(settings, io.github.hazymorning.klangbild.config.MenuContext())
        var cursor by mutableStateOf(0)
        compose.setContent { Fixtures.Still { MenuScreen(io.github.hazymorning.klangbild.app.MenuState(rows, cursor), io.github.hazymorning.klangbild.color.Lighting.DAY) } }
        for (c in listOf(0, rows.size / 2, rows.size - 1)) {
            cursor = c
            assertWithMessage(rows[c].entry.label).that(brightestInBand()).isEqualTo(0)
        }
    }

    /** The brightest channel value in the band along the screen edges. */
    private fun brightestInBand(): Int {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val band = WallGlow.EDGE_BLACK_DP.toInt()
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        var brightest = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x >= band && x < w - band && y >= band && y < h - band) continue
            val p = px[y * w + x]
            brightest = maxOf(brightest, (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        }
        return brightest
    }
}
