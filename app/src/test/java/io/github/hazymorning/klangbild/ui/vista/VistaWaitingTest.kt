package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.ui.QuietColors
import io.github.hazymorning.klangbild.ui.composeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign that the window view is on its way: a ring in the very middle of the screen in the
 * quiet ink, measured on rendered pixels. It comes only when the first picture keeps one
 * waiting, turns, and is gone before the picture is up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class VistaWaitingTest {
    @get:Rule
    val compose = composeRule()

    @Test
    fun theRingStandsInTheMiddleInQuietInk() {
        compose.setContent { Fixtures.Still { Box(Modifier.fillMaxSize().background(Color.Black)) { VistaWaiting(ready = false) } } }
        compose.onRoot().captureRoboImage("build/screenshots/vista/waiting.png")
        val bmp = capture()
        var x0 = bmp.width
        var y0 = bmp.height
        var x1 = 0
        var y1 = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            if (brightness(bmp.getPixel(x, y)) == 0) continue
            x0 = minOf(x0, x)
            y0 = minOf(y0, y)
            x1 = maxOf(x1, x + 1)
            y1 = maxOf(y1, y + 1)
        }
        // 32 dp across on the line's middle, the line 3 dp: 35 dp in all, at 2 px a dp.
        assertThat(x1 - x0).isWithin(2).of(70)
        assertThat(y1 - y0).isWithin(2).of(70)
        assertThat((x0 + x1) / 2f).isWithin(0.5f).of(bmp.width / 2f)
        assertThat((y0 + y1) / 2f).isWithin(0.5f).of(bmp.height / 2f)
        // The arc in the rest screen's quiet ink, never brighter.
        val ink = brightness(QuietColors.of(Lighting.DAY).secondary.toArgb())
        assertThat(brightest(bmp)).isIn(com.google.common.collect.Range.closed(ink - 6, ink + 1))
    }

    @Test
    fun itComesOnlyWhenThePictureKeepsOneWaitingAndGoesBeforeItIsUp() {
        var ready by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { VistaWaiting(ready) } }
        compose.mainClock.advanceTimeBy(900)
        assertThat(brightest(capture())).isEqualTo(0)
        compose.mainClock.advanceTimeBy(1_000)
        val a = capture()
        assertThat(brightest(a)).isGreaterThan(100)
        // It turns.
        compose.mainClock.advanceTimeBy(350)
        assertThat(a.sameAs(capture())).isFalse()
        // The picture is ready: the ring goes while the picture is still coming up.
        ready = true
        compose.mainClock.advanceTimeBy(400)
        assertThat(brightest(capture())).isEqualTo(0)
    }

    @Test
    fun aPictureThatComesQuicklyComesWithoutIt() {
        var ready by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { VistaWaiting(ready) } }
        compose.mainClock.advanceTimeBy(600)
        ready = true
        repeat(10) {
            compose.mainClock.advanceTimeBy(200)
            assertThat(brightest(capture())).isEqualTo(0)
        }
    }

    private fun capture(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun brightness(p: Int) = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    private fun brightest(bmp: Bitmap): Int {
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        return px.maxOf { brightness(it) }
    }
}
