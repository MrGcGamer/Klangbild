package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.vista.CloudPlace
import io.github.hazymorning.klangbild.vista.PaperBake
import io.github.hazymorning.klangbild.vista.PaperLight
import io.github.hazymorning.klangbild.vista.SeasonMix
import io.github.hazymorning.klangbild.vista.SeasonShapes
import io.github.hazymorning.klangbild.vista.SkyMap
import io.github.hazymorning.klangbild.vista.SkyState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Studies of the paper cut through a day: one picture per hour's plate, to judge as prints. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class PaperStudyTest {
    private val clouds = listOf(CloudPlace(0, 120f, 110f, 0.86f, 1f), CloudPlace(2, 560f, 70f, 1f, 1f), CloudPlace(4, 700f, 166f, 0.74f, 1f))

    private fun study(name: String, sky: SkyState, shapes: SeasonShapes = SeasonShapes(), lst: Double = 60.0) {
        val baked = PaperBake.bake(1920, 1080, 2f, shapes)
        val images = PaperImages(baked)
        val look = PaperLight.look(sky)
        val places = SkyMap.places(sky, lst)
        captureRoboImage("build/screenshots/vista/study/$name.png") {
            Fixtures.Still {
                PaperView(images, { look }, { places }, { PaperMotion(0.0, clouds) }, Modifier.fillMaxSize())
            }
        }
    }

    @Test
    fun day() {
        study("01_dawn", SkyState(-3f, 95f))
        study("02_sunrise", SkyState(1f, 100f))
        study("03_morning", SkyState(6.5f, 108f))
        study("04_forenoon", SkyState(24f, 140f))
        study("05_noon", SkyState(48f, 180f))
        study("06_afternoon", SkyState(26f, 225f))
        study("07_golden", SkyState(6.5f, 262f))
        study("08_sunset", SkyState(1.5f, 270f))
        study("09_dusk", SkyState(-3f, 276f))
        study("10_blue", SkyState(-7.5f, 285f))
        study("11_moon", SkyState(-25f, 330f, moonElevation = 28f, moonAzimuth = 160f, moonFraction = 0.78f), lst = 90.0)
        study("12_night", SkyState(-30f, 0f), lst = 120.0)
    }

    @Test
    fun weatherAndSeasons() {
        study("20_overcast", SkyState(30f, 200f, cover = 1f))
        study("21_partly", SkyState(30f, 200f, cover = 0.45f))
        study("22_spring", SkyState(30f, 170f, season = SeasonMix.SPRING), SeasonShapes(leaves = 0.7f, blossom = 1f, peaks = 0.6f))
        study("23_autumn", SkyState(14f, 220f, season = SeasonMix.AUTUMN), SeasonShapes(leaves = 0.8f, peaks = 0.4f))
        study("24_winter", SkyState(12f, 190f, season = SeasonMix.WINTER), SeasonShapes(snow = 1f, peaks = 1f, leaves = 0f))
    }
}
