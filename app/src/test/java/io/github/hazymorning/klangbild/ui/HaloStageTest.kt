package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.ui.Fixtures.pärt
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.stage.StageModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stage for a range of covers and texts (static, no transition). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class HaloStageTest {
    private fun shot(name: String, model: StageModel) =
        captureRoboImage("build/screenshots/stage/$name.png") { Fixtures.Still { Stage(model) } }

    @Test fun neon() = shot("neon", Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt, group = "Wohnzimmer + 5"))
    @Test fun classical() = shot("classical", Fixtures.model(Fixtures.track(SyntheticCover.NEBELLAND), next = pärt))
    @Test fun light() = shot("light_bauhaus", Fixtures.model(Fixtures.track(SyntheticCover.BAUHAUS)))
    @Test fun dark() = shot("dark_japanese", Fixtures.model(Fixtures.track(SyntheticCover.NOCTURNE), next = pärt))
    @Test fun radio() = shot("radio_generated", Fixtures.model(Fixtures.radio, group = "Küche"))
    @Test fun arabic() = shot("arabic", Fixtures.model(Fixtures.track(SyntheticCover.TERRAKOTTA), next = pärt))
    @Test fun longClassical() = shot("long_classical", Fixtures.model(Fixtures.track(SyntheticCover.KAMMERMUSIK), next = pärt))
    @Test fun lowRes() = shot("lowres_emoji", Fixtures.model(Fixtures.track(SyntheticCover.PASTELL)))
    @Test fun noAlbum() = shot("no_album", Fixtures.model(Fixtures.track(SyntheticCover.MOOS), next = pärt))
    @Test fun busy() = shot("busy", Fixtures.model(Fixtures.track(SyntheticCover.KONFETTI), next = pärt))
    @Test fun white() = shot("white", Fixtures.model(Fixtures.track(SyntheticCover.LEERRAUM)))
    @Test fun featVersion() = shot("feat_version", Fixtures.model(Fixtures.track(SyntheticCover.SALZWASSER), next = pärt))
}
