package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.app.MenuState
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.config.MenuContext
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SettingsMenu
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
import io.github.hazymorning.klangbild.text.LocalTexts
import io.github.hazymorning.klangbild.text.TestTexts
import io.github.hazymorning.klangbild.ui.stage.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * What is meant to stand in the middle does, measured on the rendered pixels (1080p, 2 px = 1 dp)
 * and on what is seen: the clock's figures from their top to their baseline, every line's ink,
 * the artwork's edges, the progress bar's end, the longest line of text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class CentringTest {
    @get:Rule
    val compose = composeRule()

    private val tv = ScreenSpec.TV_1080

    @Test
    fun theRestClockStandsInTheMiddleAndItsLinesUnderIt() {
        // Times whose figures have flat tops: the top of the ink is the top of the figures
        // (round ones overshoot it a little, as they should).
        val cases = listOf(
            RestReason.IDLE to Fixtures.at(17, 57),
            RestReason.PAUSED to Fixtures.at(15, 17),
            RestReason.OFFLINE to Fixtures.at(11, 55),
            RestReason.ENTITY_MISSING to Fixtures.at(7, 57),
        )
        // In German with 24 hours, and in English with 12 (the clock shows the figures alone).
        for (t in listOf(Fixtures.texts, TestTexts.english())) for ((reason, at) in cases) {
            val ink = render {
                CompositionLocalProvider(LocalTexts provides t) {
                    RestScreen(reason, "Wohnzimmer", "media_player.wohnzimer", at, zone = Fixtures.zone)
                }
            }
            val lines = ink.lines()
            val clock = lines.first()
            val case = "$reason in ${t.locale}"
            assertWithMessage("$case: the clock across").that(abs(clock.centreX - 960f)).isAtMost(1.5f)
            assertWithMessage("$case: the clock up and down (figures' top to baseline)").that(abs((clock.top + clock.baseline) / 2f - 540f)).isAtMost(2f)
            for (line in lines.drop(1)) {
                assertWithMessage("$case: a line under the clock, at ${line.top}").that(abs(line.centreX - 960f)).isAtMost(3f)
            }
        }
    }

    @Test
    fun theStageHasAsMuchRoomOnEachSide() {
        // A light cover on black: its square is the brightest thing on the left.
        val ink = render { Fixtures.Still { Stage(Fixtures.model(Fixtures.track(SyntheticCover.BAUHAUS), next = Fixtures.spiegel)) } }
        val art = ink.box(0, 0, 960, 1080)
        // The progress bar's track is quiet: found with a lower threshold.
        val right = ink.box(960, 0, 1920, 1080, threshold = 24)
        assertWithMessage("above and below the artwork").that(abs(art.top - (1079 - art.bottom))).isAtMost(1)
        assertWithMessage("left of the artwork, right of the column").that(abs(art.left - (1919 - right.right))).isAtMost(2)
    }

    @Test
    fun theMenuHasAsMuchRoomOnEachSide() {
        val settings = Settings(haUrl = "http://192.168.1.10:8123", haToken = "t", entityId = "media_player.wohnzimmer")
        val rows = SettingsMenu.rows(settings, MenuContext(LinkState.CONNECTED), Fixtures.texts)
        val ink = render { Fixtures.Still { MenuScreen(MenuState(rows, 1)) } }
        val all = ink.box(0, 0, 1920, 1080)
        assertWithMessage("left of the heading, right of the values").that(abs(all.left - (1919 - all.right))).isAtMost(4)
    }

    @Test
    fun theSetupCodeAndItsTextStandInTheMiddle() {
        val info = SetupInfo(url = "http://192.168.1.23:8765", code = "482913", qr = SetupServer.qrMatrix("http://192.168.1.23:8765/?code=482913"))
        for (reason in SetupReason.entries) {
            val ink = render { Fixtures.Still { SetupScreen(reason, info, SetupArt.scene(info, tv)) } }
            val all = ink.box(0, 0, 1920, 1080)
            val card = ink.box(0, 0, 960, 1080)
            assertWithMessage("$reason: left of the code, right of the longest line").that(abs(card.left - (1919 - all.right))).isAtMost(6)
            assertWithMessage("$reason: above and below the code").that(abs(card.top - (1079 - card.bottom))).isAtMost(1)
        }
    }

    // --- measuring ---------------------------------------------------------------------------

    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun render(content: @Composable () -> Unit): Ink {
        if (shown == null) compose.setContent { shown?.invoke() }
        shown = content
        compose.waitForIdle()
        return Ink(compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** A line of text: its ink across, its top and its baseline (where most of its columns end). */
    private class Line(val left: Int, val right: Int, val top: Int, val baseline: Int) {
        val centreX get() = (left + right + 1) / 2f
    }

    /** The bright pixels of a dark screen (text, artwork, the code). The lamp and the glow stay below. */
    private class Ink(bmp: Bitmap) {
        private val w = bmp.width
        private val h = bmp.height
        private val value = IntArray(w * h)
        private val lit = BooleanArray(w * h)

        init {
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            for (i in px.indices) {
                val p = px[i]
                value[i] = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                lit[i] = value[i] > THRESHOLD
            }
        }

        fun box(x0: Int, y0: Int, x1: Int, y1: Int, threshold: Int = THRESHOLD): Box {
            var l = Int.MAX_VALUE
            var t = Int.MAX_VALUE
            var r = -1
            var b = -1
            for (y in y0 until y1) for (x in x0 until x1) if (value[y * w + x] > threshold) {
                l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
            }
            return Box(l, t, r, b)
        }

        /** Rows of ink separated by empty rows, top to bottom. */
        fun lines(): List<Line> {
            val rows = (0 until h).map { y -> (0 until w).any { lit[y * w + it] } }
            val out = ArrayList<Line>()
            var y = 0
            while (y < h) {
                if (!rows[y]) {
                    y++
                    continue
                }
                val top = y
                while (y < h && rows[y]) y++
                val bottom = y - 1
                val box = box(0, top, w, bottom + 1)
                // Where most columns start and end: the top of the figures and the baseline
                // (descenders and round overshoots are few).
                val starts = IntArray(h)
                val ends = IntArray(h)
                for (x in box.left..box.right) {
                    var first = -1
                    var last = -1
                    for (yy in top..bottom) if (lit[yy * w + x]) {
                        if (first < 0) first = yy
                        last = yy
                    }
                    if (first >= 0) starts[first]++
                    if (last >= 0) ends[last]++
                }
                out += Line(box.left, box.right, starts.indices.maxBy { starts[it] }, ends.indices.maxBy { ends[it] })
            }
            return out
        }
    }

    private companion object {
        const val THRESHOLD = 90
    }
}
