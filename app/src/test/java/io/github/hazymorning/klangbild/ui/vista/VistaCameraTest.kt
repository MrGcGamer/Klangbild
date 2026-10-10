package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.vista.CameraPose
import io.github.hazymorning.klangbild.vista.LabelSide
import io.github.hazymorning.klangbild.vista.PaperBake
import io.github.hazymorning.klangbild.vista.PaperLight
import io.github.hazymorning.klangbild.vista.SeasonShapes
import io.github.hazymorning.klangbild.vista.SkyMap
import io.github.hazymorning.klangbild.vista.SkyState
import io.github.hazymorning.klangbild.vista.VistaCamera
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The camera's slow drift: in every corner of its range, and with the screen's own drift
 * against burn-in on top, the picture fills the screen – no edge of any layer ever shows. And
 * the layers part as they should: the near ones move most (a contact sheet of the drift).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class VistaCameraTest {
    @get:Rule
    val compose = createComposeRule()

    private val images by lazy { PaperImages(PaperBake.bake(1920, 1080, 2f, SeasonShapes(flowers = 1f), LabelSide.LEFT)) }
    private val look = PaperLight.look(SkyState(sunElevation = 32f, sunAzimuth = 200f))
    private val places = SkyMap.places(SkyState(sunElevation = 32f, sunAzimuth = 200f))

    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun render(pose: CameraPose, driftX: Float = 0f, driftY: Float = 0f): Bitmap {
        val content: @Composable () -> Unit = {
            Fixtures.Still {
                Box(Modifier.fillMaxSize().background(MAGENTA)) {
                    PaperView(
                        images, { look }, { places }, { PaperMotion(0.0, emptyList(), pose) },
                        Modifier.fillMaxSize().offset(driftX.dp, driftY.dp),
                    )
                }
            }
        }
        if (shown == null) compose.setContent { shown?.invoke() }
        shown = content
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    @Test
    fun noEdgeEverShows() {
        val z = listOf(VistaCamera.ZOOM_MIN, VistaCamera.ZOOM_MAX)
        val px = listOf(-VistaCamera.PAN_X, VistaCamera.PAN_X)
        val py = listOf(-VistaCamera.PAN_Y, VistaCamera.PAN_Y)
        for (zoom in z) for (x in px) for (y in py) {
            for ((dx, dy) in listOf(-6f to -6f, 6f to 6f, -6f to 6f, 6f to -6f)) {
                val bmp = render(CameraPose(zoom, x, y), dx, dy)
                var bad = 0
                for (yy in 0 until bmp.height) for (xx in listOf(0, 1, bmp.width - 2, bmp.width - 1)) if (bmp.getPixel(xx, yy) == MAGENTA_ARGB) bad++
                for (xx in 0 until bmp.width) for (yy in listOf(0, 1, bmp.height - 2, bmp.height - 1)) if (bmp.getPixel(xx, yy) == MAGENTA_ARGB) bad++
                assertWithMessage("zoom $zoom, pan $x/$y, drift $dx/$dy: background showing at the edges").that(bad).isEqualTo(0)
            }
        }
    }

    @Test
    fun theCutOutsSwayInTheBreeze() {
        fun at(t: Double): Bitmap {
            val content: @Composable () -> Unit = {
                Fixtures.Still { PaperView(images, { look }, { places }, { PaperMotion(t, emptyList()) }, Modifier.fillMaxSize()) }
            }
            if (shown == null) compose.setContent { shown?.invoke() }
            shown = content
            compose.waitForIdle()
            return compose.onRoot().captureToImage().asAndroidBitmap()
        }
        val a = at(1_000.0)
        val b = at(1_004.0)
        // The right group of cut-outs (dp 730–850 across, 320–450 down; 2 px a dp).
        var changed = 0
        for (y in 640 until 900) for (x in 1460 until 1700) if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
        assertWithMessage("pixels that moved with the breeze").that(changed).isGreaterThan(40)
    }

    @Test
    fun theLayersDriftApart() {
        // Ten minutes of the camera, a frame a minute and a half: the near banks move most.
        for (i in 0..6) {
            val pose = VistaCamera.at(i * 90.0 + 1_000.0)
            captureRoboImage("build/screenshots/vista/camera/drift_%02d.png".format(i)) {
                Fixtures.Still { PaperView(images, { look }, { places }, { PaperMotion(0.0, emptyList(), pose) }, Modifier.fillMaxSize()) }
            }
        }
    }

    private companion object {
        val MAGENTA = Color(0xFFFF00FF)
        const val MAGENTA_ARGB = 0xFFFF00FF.toInt()
    }
}
