package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every designed state at 1080p, plus 4K samples. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class StatesTest {
    private val pärt = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/screenshots/states/$name.png", content = content)

    @Test fun playing() = shot("stage_playing") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt), wallClockMs = { 0L })
    }

    @Test fun paused() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.TERRAKOTTA))
        shot("stage_paused") { Fixtures.Theatre(stage, PlayerStatus.PAUSED) }
    }


    @Test fun exactTime() = shot("stage_exact_time") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.MOOS), next = pärt, exact = true), wallClockMs = { 0L })
    }

    @Test fun evening() = shot("stage_evening") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.BAUHAUS), next = pärt, lighting = Lighting.EVENING), wallClockMs = { 0L })
    }

    @Test fun night() = shot("stage_night") {
        HaloStage(
            Fixtures.model(Fixtures.track(SyntheticCover.NEBELLAND), next = pärt, lighting = Lighting.NIGHT, night = true),
            wallClockMs = { 0L },
        )
    }

    @Test fun restIdle() = shot("rest_idle") {
        RestScreen(RestReason.IDLE, "Wohnzimmer", null, Lighting.DAY, Fixtures.at(10, 12), zone = Fixtures.zone)
    }

    @Test fun restPaused() = shot("rest_paused") {
        RestScreen(RestReason.PAUSED, "Wohnzimmer", null, Lighting.EVENING, Fixtures.at(18, 40), zone = Fixtures.zone)
    }

    @Test fun restNight() = shot("rest_night") {
        RestScreen(RestReason.IDLE, "Wohnzimmer", null, Lighting.NIGHT, Fixtures.at(2, 13, day = 9), zone = Fixtures.zone)
    }

    @Test fun connecting() = shot("rest_connecting") {
        RestScreen(RestReason.CONNECTING, null, null, Lighting.DAY, Fixtures.at(13, 5), zone = Fixtures.zone)
    }

    @Test fun offline() = shot("rest_offline") {
        RestScreen(RestReason.OFFLINE, "Wohnzimmer", null, Lighting.DAY, Fixtures.at(16, 20), zone = Fixtures.zone)
    }

    @Test fun unavailable() = shot("rest_unavailable") {
        RestScreen(RestReason.UNAVAILABLE, "Wohnzimmer", null, Lighting.DAY, Fixtures.at(11, 50), zone = Fixtures.zone)
    }

    @Test fun entityMissing() = shot("rest_entity_missing") {
        RestScreen(RestReason.ENTITY_MISSING, null, "media_player.wohnzimer", Lighting.DAY, Fixtures.at(9, 30), zone = Fixtures.zone)
    }

    private val setupInfo = SetupInfo(
        url = "http://192.168.1.23:8765",
        code = "482913",
        qr = SetupServer.qrMatrix("http://192.168.1.23:8765/?code=482913"),
    )

    @Test fun setupFirstRun() = shot("setup_first_run") {
        SetupScreen(SetupReason.FIRST_RUN, setupInfo, SetupArt.scene(setupInfo, ScreenSpec.TV_1080, Lighting.DAY))
    }

    @Test fun setupAuthFailed() = shot("setup_auth_failed") {
        SetupScreen(SetupReason.AUTH_FAILED, setupInfo, SetupArt.scene(setupInfo, ScreenSpec.TV_1080, Lighting.EVENING))
    }
}

/** The same at 3840 × 2160 (xxxhdpi), to check crispness and the honest cover scale. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xxxhdpi")
class States4kTest {
    @Test fun playing4k() = captureRoboImage("build/screenshots/states/4k_stage_classical.png") {
        HaloStage(
            Fixtures.model(Fixtures.track(SyntheticCover.KAMMERMUSIK), next = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt")), screen = ScreenSpec.TV_4K),
            wallClockMs = { 0L },
        )
    }

    @Test fun lowRes4k() = captureRoboImage("build/screenshots/states/4k_stage_lowres.png") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.PASTELL), screen = ScreenSpec.TV_4K), wallClockMs = { 0L })
    }

    @Test fun radio4k() = captureRoboImage("build/screenshots/states/4k_stage_radio.png") {
        HaloStage(Fixtures.model(Fixtures.radio, screen = ScreenSpec.TV_4K), wallClockMs = { 0L })
    }
}
