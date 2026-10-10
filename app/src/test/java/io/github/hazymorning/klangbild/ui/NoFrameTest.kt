package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.ui.flow.FlowStage
import io.github.hazymorning.klangbild.ui.stage.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Nothing frames a cover on paper – no shadow, no line, no lighter or darker box: round the
 * single print and above and below the Cover Flow's cards the paper is the paper, measured on
 * rendered pixels.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class NoFrameTest {
    @get:Rule
    val compose = createComposeRule()

    private var shown by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit): Bitmap {
        if (shown == null) compose.setContent { shown?.let { Fixtures.Still(it) } }
        shown = content
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    /** The largest difference (levels, any channel) from the paper in the boxes (px: x0, y0, x1, y1). */
    private fun deviation(bmp: Bitmap, boxes: List<IntArray>): Int {
        val paper = bmp.getPixel(boxes[0][0], boxes[0][1])
        var worst = 0
        for (b in boxes) for (y in b[1] until b[3]) for (x in b[0] until b[2]) {
            val p = bmp.getPixel(x, y)
            for (shift in intArrayOf(16, 8, 0)) worst = maxOf(worst, abs(((p shr shift) and 0xFF) - ((paper shr shift) and 0xFF)))
        }
        return worst
    }

    @Test
    fun `round the single print the paper is plain`() {
        for (cover in listOf(SyntheticCover.NIGHT_DRIVE, SyntheticCover.WHITE_SPACE, SyntheticCover.BAUHAUS)) {
            val bmp = render { Stage(Fixtures.model(Fixtures.track(cover), next = Fixtures.spiegel, group = "Wohnzimmer", appearance = Appearance.LIGHT)) }
            // The artwork: 368 dp at 88 dp from the left, centred – 2 px a dp. A ring from 3 to 40 dp round it.
            val l = 176; val t = 172; val r = 912; val b = 908
            val boxes = listOf(
                intArrayOf(l - 80, t - 80, r + 80, t - 6),
                intArrayOf(l - 80, b + 6, r + 80, b + 80),
                intArrayOf(l - 80, t, l - 6, b),
                intArrayOf(r + 6, t, r + 80, b),
            )
            assertWithMessage("$cover: paper round the print").that(deviation(bmp, boxes)).isAtMost(3)
        }
    }

    @Test
    fun `above and below the Cover Flow's cards the paper is plain`() {
        val strip: List<TrackInfo> = listOf(
            Fixtures.track(SyntheticCover.TERRACOTTA),
            Fixtures.radio,
            Fixtures.track(SyntheticCover.NIGHT_DRIVE),
            Fixtures.track(SyntheticCover.WHITE_SPACE),
            Fixtures.track(SyntheticCover.BAUHAUS),
        )
        val bmp = render {
            val scenes = Fixtures.flowScenes(strip, Appearance.LIGHT, io.github.hazymorning.klangbild.color.Lighting.DAY)
            FlowStage(Fixtures.model(strip[2], next = strip[3], group = "Wohnzimmer", scene = scenes[2]), Fixtures.restingCards(scenes, 2))
        }
        // The middle card: 300 dp, its middle 303 dp from the left and in the middle of the height; the column from 598 dp.
        val top = 240; val bottom = 840
        val boxes = listOf(
            intArrayOf(80, top - 90, 1110, top - 6),
            intArrayOf(80, bottom + 6, 1110, bottom + 90),
        )
        assertWithMessage("paper above and below the cards").that(deviation(bmp, boxes)).isAtMost(3)
    }
}
