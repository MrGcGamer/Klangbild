package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
import io.github.hazymorning.klangbild.ui.flow.FlowStage
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.stage.StageModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The curated set of renderings for the README (tools/make_readme_images.py turns them
 * into docs/screens/). Same code paths as the app, test content only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ReadmeShotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val pärt = Fixtures.pärt
    private fun out(name: String) = "build/screenshots/readme/$name.png"
    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage(out(name)) { Fixtures.Still(content) }
    private fun stage(name: String, model: StageModel) = shot(name) { Stage(model) }

    @Test fun s01() = stage("01-spielt", Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt, group = "Wohnzimmer"))

    @Test fun s02() = stage("02-klassik", Fixtures.model(Fixtures.track(SyntheticCover.KAMMERMUSIK), next = pärt))

    @Test fun s03() = stage("03-japanisch", Fixtures.model(Fixtures.track(SyntheticCover.NOCTURNE), next = Fixtures.track(SyntheticCover.TERRAKOTTA)))

    @Test fun s04() = stage("04-radio", Fixtures.model(Fixtures.radio, group = "Küche"))

    @Test fun s05() = stage("05-niedrig-aufgeloest", Fixtures.model(Fixtures.track(SyntheticCover.PASTELL), next = Fixtures.track(SyntheticCover.MOOS)))

    @Test fun s06() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.BAUHAUS))
        shot("06-pausiert") { Fixtures.Theatre(stage, PlayerStatus.PAUSED, group = "Wohnzimmer") }
    }

    @Test fun s07() = stage(
        "07-abends",
        Fixtures.model(Fixtures.track(SyntheticCover.SALZWASSER), next = pärt, lighting = Lighting.EVENING, clock = "20:41"),
    )

    @Test fun s08() = stage(
        "08-nachts",
        Fixtures.model(Fixtures.track(SyntheticCover.NEBELLAND), next = pärt, lighting = Lighting.NIGHT, clock = "23:12"),
    )

    @Test fun s09() = shot("09-ruhe") {
        RestScreen(RestReason.IDLE, "Wohnzimmer", null, Lighting.EVENING, Fixtures.at(19, 14), zone = Fixtures.zone)
    }

    @Test fun s10() = shot("10-verbindung") {
        RestScreen(RestReason.OFFLINE, "Wohnzimmer", null, Lighting.DAY, Fixtures.at(15, 47), zone = Fixtures.zone)
    }

    @Test fun s11() {
        val info = SetupInfo("http://192.168.1.50:8765", "482913", SetupServer.qrMatrix("http://192.168.1.50:8765/?code=482913"))
        shot("11-einrichtung") {
            SetupScreen(SetupReason.FIRST_RUN, info, SetupArt.scene(info, ScreenSpec.TV_1080, Lighting.DAY))
        }
    }

    @Test fun s12() = stage(
        "12-gruppe",
        Fixtures.model(
            Fixtures.track(SyntheticCover.MOOS),
            next = pärt,
            group = io.github.hazymorning.klangbild.domain.SpeakerLabel.group(
                io.github.hazymorning.klangbild.domain.SpeakerGroup("Wohnzimmer", listOf("Küche", "Bad", "Arbeitszimmer", "Schlafzimmer", "Terrasse")),
            ),
        ),
    )

    @Test fun s13() = shot("13-einstellungen") {
        val settings = io.github.hazymorning.klangbild.config.Settings(
            haUrl = "http://192.168.1.10:8123",
            haToken = "t",
            entityId = "media_player.wohnzimmer",
            view = StageView.FLOW,
        )
        val rows = io.github.hazymorning.klangbild.config.SettingsMenu.rows(
            settings,
            io.github.hazymorning.klangbild.config.MenuContext(io.github.hazymorning.klangbild.data.LinkState.CONNECTED),
        )
        val cursor = io.github.hazymorning.klangbild.config.SettingsMenu.entries.indexOf(io.github.hazymorning.klangbild.config.MenuEntry.VIEW)
        MenuScreen(io.github.hazymorning.klangbild.app.MenuState(rows, cursor), Lighting.DAY)
    }

    private fun flow(name: String, appearance: Appearance) = shot(name) {
        val strip = listOf(
            Fixtures.track(SyntheticCover.TERRAKOTTA),
            Fixtures.track(SyntheticCover.MOOS),
            Fixtures.track(SyntheticCover.NACHTFAHRT),
            Fixtures.track(SyntheticCover.LEERRAUM),
            Fixtures.track(SyntheticCover.BAUHAUS),
        )
        val scenes = Fixtures.flowScenes(strip, appearance)
        FlowStage(
            Fixtures.model(strip[2], next = strip[3], group = "Wohnzimmer + 5", scene = scenes[2], clock = if (appearance == Appearance.LIGHT) "11:24" else "21:47"),
            Fixtures.restingCards(scenes, 2),
        )
    }

    @Test fun s14() = flow("14-cover-flow", Appearance.DARK)

    @Test fun s15() = flow("15-cover-flow-hell", Appearance.LIGHT)

    @Test fun s16() = stage(
        "16-tag",
        Fixtures.model(Fixtures.track(SyntheticCover.SALZWASSER), next = pärt, group = "Wohnzimmer", clock = "11:24", appearance = Appearance.LIGHT),
    )

    private fun field(name: String, cover: SyntheticCover, appearance: Appearance, clock: String) {
        val track = Fixtures.track(cover)
        val scene = Fixtures.scene(track, appearance = appearance, coverField = true)
        stage(name, Fixtures.model(track, next = pärt, group = "Wohnzimmer", scene = scene, clock = clock, appearance = appearance))
    }

    @Test fun s17() = field("17-coverfarben", SyntheticCover.TERRAKOTTA, Appearance.DARK, "21:47")

    @Test fun s18() = field("18-coverfarben-hell", SyntheticCover.SALZWASSER, Appearance.LIGHT, "11:24")

    @Test fun s19() = shot("19-coverfarben-cover-flow") {
        val strip = listOf(
            Fixtures.track(SyntheticCover.TERRAKOTTA),
            Fixtures.track(SyntheticCover.MOOS),
            Fixtures.track(SyntheticCover.NACHTFAHRT),
            Fixtures.track(SyntheticCover.LEERRAUM),
            Fixtures.track(SyntheticCover.BAUHAUS),
        )
        val scenes = Fixtures.flowScenes(strip, coverField = true)
        FlowStage(Fixtures.model(strip[2], next = strip[3], group = "Wohnzimmer + 5", scene = scenes[2]), Fixtures.restingCards(scenes, 2))
    }

    @Test fun s20() = stage(
        "20-hoerbuch",
        Fixtures.spokenModel(Catalogue.book, 2_554_000L, io.github.hazymorning.klangbild.domain.TimeLine.speed(1.25f)),
    )

    @Test fun s21() = stage(
        "21-podcast",
        Fixtures.spokenModel(
            Catalogue.episode, 1_163_000L,
            io.github.hazymorning.klangbild.ui.stage.StageTexts.sleep(Fixtures.at(22, 30), Fixtures.zone),
            next = Catalogue.nextEpisode,
        ),
    )

    @Test fun s22() = shot("22-einstellungen-effekte") {
        val settings = io.github.hazymorning.klangbild.config.Settings(
            haUrl = "http://192.168.1.10:8123",
            haToken = "t",
            entityId = "media_player.wohnzimmer",
            background = io.github.hazymorning.klangbild.config.BackgroundMode.COVER,
            motion = io.github.hazymorning.klangbild.config.MotionLevel.GENTLE,
            bars = io.github.hazymorning.klangbild.config.LightBars("light.play_links", "light.play_mitte", "light.play_rechts"),
        )
        val rows = io.github.hazymorning.klangbild.config.SettingsMenu.rows(
            settings,
            io.github.hazymorning.klangbild.config.MenuContext(io.github.hazymorning.klangbild.data.LinkState.CONNECTED),
        )
        val cursor = io.github.hazymorning.klangbild.config.SettingsMenu.entries.indexOf(io.github.hazymorning.klangbild.config.MenuEntry.BARS)
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
        // 1.1 s transition at 10 fps, then a hold on the new picture
        while (frame < 8 + 24) {
            compose.mainClock.advanceTimeBy(100)
            compose.onRoot().captureRoboImage("build/screenshots/readme-motion/frame_%03d.png".format(frame))
            frame++
        }
    }

    @Test
    fun coverFlowFrames() {
        val mdpi = ScreenSpec(960, 540, 1f)
        val covers = listOf(SyntheticCover.TERRAKOTTA, SyntheticCover.MOOS, SyntheticCover.NACHTFAHRT, SyntheticCover.LEERRAUM, SyntheticCover.BAUHAUS, SyntheticCover.SALZWASSER)
        val scenes = covers.mapIndexed { i, c ->
            i.toLong() to Fixtures.scene(Fixtures.track(c), mdpi, view = StageView.FLOW)
        }.toMap()
        var flow by mutableStateOf(Fixtures.flow(scenes, listOf(0, 1, 2, 3, 4), 2))
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixtures.FlowTheatreOf(flow) }
        compose.mainClock.advanceTimeBy(50)
        for (i in 0 until 8) {
            compose.onRoot().captureRoboImage("build/screenshots/readme-flow/frame_%03d.png".format(i))
            compose.mainClock.advanceTimeBy(100)
        }
        flow = Fixtures.flow(scenes, listOf(1, 2, 3, 4, 5), 3)
        // 1.3 s turn at 10 fps, then a hold on the new picture
        for (frame in 8 until 8 + 24) {
            compose.mainClock.advanceTimeBy(100)
            compose.onRoot().captureRoboImage("build/screenshots/readme-flow/frame_%03d.png".format(frame))
        }
    }
}
