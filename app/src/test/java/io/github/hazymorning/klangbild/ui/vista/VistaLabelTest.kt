package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.ui.Catalogue
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.vista.LabelSide
import io.github.hazymorning.klangbild.vista.PaperMotif
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The mini-player's lines stand exactly in the middle of its cover – measured on the rendered
 * pixels (1080p, 2 px = 1 dp), on what is seen: from the title's cap height to the artist's
 * baseline (a title alone: its cap height to its baseline). The bar lies under the cover and
 * its lines, from the cover's left edge.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class VistaLabelTest {
    @get:Rule
    val compose = createComposeRule()

    // Capitals with flat tops and feet: the ink's top is the cap height, its bottom the baseline.
    private val withArtist = TrackInfo("HEIMATLIED", listOf("EMIL THEN"), durationMs = 200_000, artworkUrl = Catalogue.SCHEME + SyntheticCover.BAUHAUS.name)
    private val titleOnly = TrackInfo("HEIMATLIED", emptyList(), durationMs = 200_000, artworkUrl = Catalogue.SCHEME + SyntheticCover.BAUHAUS.name)

    @Test
    fun theLinesStandInTheMiddleOfTheCover() {
        for (side in listOf(LabelSide.LEFT, LabelSide.RIGHT)) {
            for (track in listOf(withArtist, titleOnly)) {
                val what = "$side, ${if (track.artists.isEmpty()) "title alone" else "title and artist"}"
                val ink = render(track, side)
                val left = px(if (side == LabelSide.RIGHT) PaperMotif.LABEL_RIGHT_X else PaperMotif.LABEL_LEFT_X)
                val coverSide = px(PaperMotif.LABEL_HEIGHT - 15f)
                // The cover's columns: the cover, then (after a gap) the bar.
                val coverRows = ink.blocks(left, left + coverSide)
                assertWithMessage("$what: the cover and the bar under it").that(coverRows).hasSize(2)
                val (cover, bar) = coverRows
                assertWithMessage("$what: the cover's side").that(cover.second - cover.first + 1).isEqualTo(coverSide)
                // The text column, above the bar.
                val lines = ink.lines(left + coverSide + px(4f), 1920, 0, bar.first)
                assertWithMessage("$what: lines beside the cover").that(lines).hasSize(if (track.artists.isEmpty()) 1 else 2)
                val top = lines.first().capTop
                val baseline = lines.last().baseline
                val textMiddle = (top + baseline + 1) / 2f
                val coverMiddle = (cover.first + cover.second + 1) / 2f
                assertWithMessage("$what: the lines' middle (cap height $top to baseline $baseline) on the cover's (${cover.first}–${cover.second})")
                    .that(abs(textMiddle - coverMiddle)).isAtMost(1f)
                // The bar starts on the cover's left edge, a little under it.
                val barBox = ink.box(0, bar.first, 1920, bar.second + 1)
                assertWithMessage("$what: the bar's left end on the cover's left edge").that(abs(barBox.first - left)).isAtMost(1)
                assertWithMessage("$what: the bar under the cover").that(bar.first - cover.second).isAtLeast(px(8f))
            }
        }
    }

    // --- rendering and measuring ----------------------------------------------------------------

    private fun px(dp: Float) = (dp * 2f).toInt()

    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun render(track: TrackInfo, side: LabelSide): Ink {
        val scene = Fixtures.scene(track)
        val content: @Composable () -> Unit = {
            Fixtures.Still {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    VistaLabel(
                        LabelText.Music(track, scene, playing = true, positionNow = { 100_000L }),
                        side,
                        inks = { INKS },
                    )
                }
            }
        }
        if (shown == null) compose.setContent { shown?.invoke() }
        shown = content
        compose.waitForIdle()
        return Ink(compose.onRoot().captureToImage().asAndroidBitmap())
    }

    /** A line of text: the top of most of its columns (the cap height) and where most end (the baseline). */
    private class Line(val capTop: Int, val baseline: Int)

    private class Ink(bmp: Bitmap) {
        private val w = bmp.width
        private val h = bmp.height
        private val lit = BooleanArray(w * h)

        init {
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            for (i in px.indices) {
                val p = px[i]
                lit[i] = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) > THRESHOLD
            }
        }

        private fun rowLit(y: Int, x0: Int, x1: Int) = (x0 until x1).any { lit[y * w + it] }

        /** Runs of rows with ink between columns [x0] and [x1], top to bottom (first and last row). */
        fun blocks(x0: Int, x1: Int, y0: Int = 0, y1: Int = h): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            var y = y0
            while (y < y1) {
                if (!rowLit(y, x0, x1)) {
                    y++
                    continue
                }
                val top = y
                while (y < y1 && rowLit(y, x0, x1)) y++
                out += top to y - 1
            }
            return out
        }

        /** The leftmost and rightmost column with ink in rows [y0] until [y1]. */
        fun box(x0: Int, y0: Int, x1: Int, y1: Int): Pair<Int, Int> {
            var l = Int.MAX_VALUE
            var r = -1
            for (y in y0 until y1) for (x in x0 until x1) if (lit[y * w + x]) {
                l = minOf(l, x)
                r = maxOf(r, x)
            }
            return l to r
        }

        fun lines(x0: Int, x1: Int, y0: Int, y1: Int): List<Line> = blocks(x0, x1, y0, y1).map { (top, bottom) ->
            val starts = IntArray(h)
            val ends = IntArray(h)
            for (x in x0 until x1) {
                var first = -1
                var last = -1
                for (y in top..bottom) if (lit[y * w + x]) {
                    if (first < 0) first = y
                    last = y
                }
                if (first >= 0) starts[first]++
                if (last >= 0) ends[last]++
            }
            Line(starts.indices.maxBy { starts[it] }, ends.indices.maxBy { ends[it] })
        }
    }

    private companion object {
        const val THRESHOLD = 90
        val INKS = intArrayOf(0xFFF0F0F0.toInt(), 0xFFD0D0D0.toInt(), 0xFFB0B0B0.toInt())
    }
}
