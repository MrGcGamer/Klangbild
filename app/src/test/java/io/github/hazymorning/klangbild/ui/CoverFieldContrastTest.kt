package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.contrastRatio
import io.github.hazymorning.klangbild.color.relativeLuminance
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.stage.FieldLayer
import io.github.hazymorning.klangbild.ui.stage.FieldTime
import io.github.hazymorning.klangbild.ui.stage.LocalFieldTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On the cover's colours the quietest text keeps 4.5:1 – against the lightest pixel the
 * rendered field shows anywhere in the text column, wherever its layers stand, at every hour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class CoverFieldContrastTest {
    @get:Rule
    val compose = createComposeRule()

    private val mdpi = ScreenSpec(960, 540, 1f)

    private val hours = listOf(
        Lighting.DAY,
        Lighting.DAY.lerp(Lighting.EVENING, 0.5f),
        Lighting.EVENING,
        Lighting.EVENING.lerp(Lighting.NIGHT, 0.5f),
        Lighting.NIGHT,
    )

    @Test
    fun theQuietestTextKeepsItsContrastOnTheDarkField() = check(Appearance.DARK)

    @Test
    fun theQuietestTextKeepsItsContrastOnThePaleField() = check(Appearance.LIGHT)

    private fun check(appearance: Appearance) {
        val first = Fixtures.scene(Fixtures.tracks.first(), mdpi, appearance = appearance, coverField = true)
        var scene by mutableStateOf(first)
        var at by mutableStateOf(0.0)
        compose.setContent {
            Fixtures.Still {
                CompositionLocalProvider(LocalFieldTime provides FieldTime({ at }, false)) { FieldLayer(scene) }
            }
        }
        for (track in Fixtures.tracks) for (lighting in hours) {
            scene = Fixtures.scene(track, mdpi, lighting, appearance, coverField = true)
            for (t in listOf(0.0, 47.0, 133.0, 260.0)) {
                at = t
                val worst = worstBehindText(scene, darkText = appearance == Appearance.LIGHT)
                assertWithMessage("${track.title}, $lighting, $appearance, t = $t")
                    .that(contrastRatio(scene.palette.inkQuiet, worst))
                    .isAtLeast(4.5f)
            }
        }
    }

    /** The field's pixel in the text column that the quietest text stands out from least. */
    private fun worstBehindText(scene: ArtworkScene, darkText: Boolean): Int {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val l = scene.layout
        val x0 = l.columnLeft.toInt()
        val x1 = (l.columnLeft + l.columnWidth).toInt()
        val y0 = l.statusTop.toInt() - 4
        val y1 = l.footerBaseline.toInt() + 4
        var worst = if (darkText) -1 else 0xFF000000.toInt()
        var worstY = if (darkText) 2f else -1f
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = bmp.getPixel(x, y)
            val lum = relativeLuminance(p)
            if (if (darkText) lum < worstY else lum > worstY) {
                worstY = lum
                worst = p
            }
        }
        return worst
    }
}
