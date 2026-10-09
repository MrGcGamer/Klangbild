package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The curated set of renderings for the README (tools/make_readme_images.py turns them
 * into docs/screens/). Same code paths as the app, demo content only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ReadmeShotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val pärt = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))
    private fun out(name: String) = "build/screenshots/readme/$name.png"

    @Test fun s01() = captureRoboImage(out("01-spielt")) {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt), wallClockMs = { 0L })
    }

    @Test fun s02() = captureRoboImage(out("02-klassik")) {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.KAMMERMUSIK), next = pärt), wallClockMs = { 0L })
    }

    @Test fun s03() = captureRoboImage(out("03-japanisch")) {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NOCTURNE), next = Fixtures.track(SyntheticCover.TERRAKOTTA)), wallClockMs = { 0L })
    }

    @Test fun s04() = captureRoboImage(out("04-radio")) {
        HaloStage(Fixtures.model(Fixtures.radio), wallClockMs = { 0L })
    }

    @Test fun s05() = captureRoboImage(out("05-niedrig-aufgeloest")) {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.PASTELL), next = Fixtures.track(SyntheticCover.MOOS)), wallClockMs = { 0L })
    }

    @Test fun s06() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.BAUHAUS))
        captureRoboImage(out("06-pausiert")) { Fixtures.Theatre(stage, PlayerStatus.PAUSED) }
    }

    @Test fun s07() = captureRoboImage(out("07-abends")) {
        HaloStage(
            Fixtures.model(Fixtures.track(SyntheticCover.SALZWASSER), next = pärt, lighting = Lighting.EVENING, clock = "20:41"),
            wallClockMs = { 0L },
        )
    }

    @Test fun s08() = captureRoboImage(out("08-nachts")) {
        HaloStage(
            Fixtures.model(Fixtures.track(SyntheticCover.NEBELLAND), next = pärt, lighting = Lighting.NIGHT, night = true),
            wallClockMs = { 0L },
        )
    }

    @Test fun s09() = captureRoboImage(out("09-ruhe")) {
        RestScreen(RestReason.IDLE, "Wohnzimmer", null, Lighting.EVENING, Fixtures.at(19, 14), zone = Fixtures.zone)
    }

    @Test fun s10() = captureRoboImage(out("10-verbindung")) {
        RestScreen(RestReason.OFFLINE, "Wohnzimmer", null, Lighting.DAY, Fixtures.at(15, 47), zone = Fixtures.zone)
    }

    @Test fun s11() {
        val info = SetupInfo("http://192.168.1.50:8765", "482913", SetupServer.qrMatrix("http://192.168.1.50:8765/?code=482913"))
        captureRoboImage(out("11-einrichtung")) {
            SetupScreen(SetupReason.FIRST_RUN, info, SetupArt.scene(info, ScreenSpec.TV_1080, Lighting.DAY))
        }
    }

    @Test fun s12() = captureRoboImage(out("12-gruppe")) {
        val group = io.github.hazymorning.klangbild.domain.SpeakerLabel.group(
            io.github.hazymorning.klangbild.domain.SpeakerGroup("Wohnzimmer", listOf("Küche", "Bad", "Arbeitszimmer", "Schlafzimmer", "Terrasse")),
        )
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt, capExtra = group), wallClockMs = { 0L })
    }

    @Test fun s13() = captureRoboImage(out("13-einstellungen")) {
        val settings = io.github.hazymorning.klangbild.config.Settings(haUrl = "http://192.168.1.10:8123", haToken = "t", entityId = "media_player.wohnzimmer")
        val rows = io.github.hazymorning.klangbild.config.SettingsMenu.rows(
            settings,
            io.github.hazymorning.klangbild.config.MenuContext(io.github.hazymorning.klangbild.data.LinkState.CONNECTED),
        )
        val cursor = io.github.hazymorning.klangbild.config.SettingsMenu.entries.indexOf(io.github.hazymorning.klangbild.config.MenuEntry.GROUP)
        MenuScreen(io.github.hazymorning.klangbild.app.MenuState(rows, cursor), Lighting.DAY)
    }
}

/** The track change as frames for the README animation (960 × 540). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class ReadmeMotionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun trackChangeFrames() {
        val mdpi = ScreenSpec(960, 540, 1f)
        val a = Fixtures.stage(Fixtures.track(SyntheticCover.TERRAKOTTA), mdpi)
        val b = Fixtures.stage(Fixtures.track(SyntheticCover.SALZWASSER), mdpi)
        var stage by mutableStateOf(a)
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixtures.Theatre(stage, PlayerStatus.PLAYING) }
        compose.mainClock.advanceTimeBy(50)
        for (i in 0 until 8) {
            compose.onRoot().captureRoboImage("build/screenshots/readme-motion/frame_%03d.png".format(i))
            compose.mainClock.advanceTimeBy(100)
        }
        stage = b
        var frame = 8
        // 2.7 s transition at 10 fps, then a hold on the new picture
        while (frame < 8 + 44) {
            compose.mainClock.advanceTimeBy(100)
            compose.onRoot().captureRoboImage("build/screenshots/readme-motion/frame_%03d.png".format(frame))
            frame++
        }
    }
}
