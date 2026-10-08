package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.art.SyntheticCovers
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.render.StageStyle
import io.github.hazymorning.klangbild.ui.stage.EditorialStage
import io.github.hazymorning.klangbild.ui.stage.GalleryStage
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import io.github.hazymorning.klangbild.ui.stage.StageModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 2: the three design directions side by side, same content. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class DirectionsTest {

    private val samples = listOf(
        SyntheticCover.NACHTFAHRT to TrackInfo("Neonlicht über der A40", listOf("Lumen Drive"), "Nachtfahrt", durationMs = 251_000),
        SyntheticCover.BAUHAUS to TrackInfo("Kreis, Quadrat, Linie", listOf("Ensemble Dessau"), "Formen im Raum", durationMs = 312_000),
        SyntheticCover.NEBELLAND to TrackInfo(
            "Suite bergamasque, L. 75: III. Clair de lune",
            listOf("Clara Wendt"),
            "Debussy: Préludes & Suite bergamasque",
            durationMs = 302_000,
        ),
    )

    private fun render(style: StageStyle, index: Int) {
        val (cover, track) = samples[index]
        val bmp = SyntheticCovers.render(cover)
        val scene = SceneBuilder.build(track.key, bmp, track.artSeed, style, ScreenSpec.TV_1080, Lighting.DAY)
        val model = StageModel(
            scene = scene,
            text = TrackText.from(track),
            durationMs = track.durationMs,
            positionMs = { 97_000 },
            clock = "21:47",
            nextUp = TrackText.from(TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))),
        )
        captureRoboImage("build/outputs/directions/${style.name.lowercase()}_$index.png") {
            when (style) {
                StageStyle.HALO -> HaloStage(model)
                StageStyle.GALERIE -> GalleryStage(model)
                StageStyle.FEUILLETON -> EditorialStage(model)
            }
        }
    }

    @Test fun halo0() = render(StageStyle.HALO, 0)
    @Test fun halo1() = render(StageStyle.HALO, 1)
    @Test fun halo2() = render(StageStyle.HALO, 2)
    @Test fun galerie0() = render(StageStyle.GALERIE, 0)
    @Test fun galerie1() = render(StageStyle.GALERIE, 1)
    @Test fun galerie2() = render(StageStyle.GALERIE, 2)
    @Test fun feuilleton0() = render(StageStyle.FEUILLETON, 0)
    @Test fun feuilleton1() = render(StageStyle.FEUILLETON, 1)
    @Test fun feuilleton2() = render(StageStyle.FEUILLETON, 2)
}
