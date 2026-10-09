package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.app.MenuState
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.LightLevel
import io.github.hazymorning.klangbild.config.MenuContext
import io.github.hazymorning.klangbild.config.MenuEntry
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SettingsMenu
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.SpeakerGroup
import io.github.hazymorning.klangbild.domain.SpeakerLabel
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.ui.stage.HaloStage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The adjustable parts: the speaker group in the dateline, volume, hidden lines, light, menu. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class OptionsTest {
    @get:org.junit.Rule
    val compose = androidx.compose.ui.test.junit4.createComposeRule()

    private val pärt = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))
    private val everywhere = SpeakerLabel.group(SpeakerGroup("Wohnzimmer", listOf("Küche", "Bad", "Büro", "Flur", "Terrasse")))
    private val two = SpeakerLabel.group(SpeakerGroup("Wohnzimmer Lautsprecher", listOf("Küche")))
    private val configured = Settings(haUrl = "http://192.168.1.10:8123", haToken = "t", entityId = "media_player.wohnzimmer")

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/screenshots/options/$name.png", content = content)

    @Test fun groupOfSix() = shot("stage_group") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NACHTFAHRT), next = pärt, capExtra = everywhere), wallClockMs = { 0L })
    }

    @Test fun groupOfTwoMirrored() = shot("stage_group_two") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.SALZWASSER), next = pärt, capExtra = two), wallClockMs = { 0L })
    }

    @Test fun groupAndVolumeAlways() = shot("stage_group_volume") {
        val extra = listOfNotNull(everywhere, SpeakerLabel.volume(0.35f, false)).joinToString(TimeLine.SEPARATOR)
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.MOOS), next = pärt, capExtra = extra), wallClockMs = { 0L })
    }

    @Test fun volumeFlash() = shot("stage_volume_flash") {
        HaloStage(
            Fixtures.model(Fixtures.track(SyntheticCover.BAUHAUS), next = pärt, capExtra = everywhere, timeLine = TimeLine.volume(0.4f, false)),
            wallClockMs = { 0L },
        )
    }

    @Test fun groupWithoutClock() = shot("stage_group_no_clock") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.TERRAKOTTA), next = pärt, clock = null, capExtra = everywhere), wallClockMs = { 0L })
    }

    @Test fun onlyTitleAndArtist() = shot("stage_minimal") {
        HaloStage(Fixtures.model(Fixtures.track(SyntheticCover.NEBELLAND), clock = null, timeLine = null, album = false), wallClockMs = { 0L })
    }

    @Test fun lightStrong() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.KONFETTI))
        shot("light_strong") { Fixtures.Theatre(stage, PlayerStatus.PLAYING, lightFactor = LightLevel.STRONG.factor) }
    }

    @Test fun lightSubtle() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.KONFETTI))
        shot("light_subtle") { Fixtures.Theatre(stage, PlayerStatus.PLAYING, lightFactor = LightLevel.SUBTLE.factor) }
    }

    @Test fun lightOff() {
        val stage = Fixtures.stage(Fixtures.track(SyntheticCover.KONFETTI))
        shot("light_off") { Fixtures.Theatre(stage, PlayerStatus.PLAYING, lightFactor = LightLevel.OFF.factor) }
    }

    @Test fun restGrouped() = shot("rest_group") {
        RestScreen(RestReason.PAUSED, everywhere, null, Lighting.EVENING, Fixtures.at(18, 40), zone = Fixtures.zone)
    }

    private fun menu(entry: MenuEntry, settings: Settings = configured, ctx: MenuContext = MenuContext(LinkState.CONNECTED)) =
        MenuState(SettingsMenu.rows(settings, ctx), SettingsMenu.entries.indexOf(entry))

    @Test fun menuTop() = shot("menu_clock") { MenuScreen(menu(MenuEntry.CLOCK), Lighting.DAY) }

    @Test fun menuGroup() = shot("menu_group") { MenuScreen(menu(MenuEntry.GROUP), Lighting.DAY) }

    @Test fun menuLight() = shot("menu_light") {
        MenuScreen(menu(MenuEntry.LIGHT, configured.copy(light = LightLevel.SUBTLE)), Lighting.DAY)
    }

    @Test fun menuEveningSleep() = shot("menu_sleep_evening") { MenuScreen(menu(MenuEntry.SLEEP_AFTER), Lighting.EVENING) }

    /** The list glides through the light when the cursor moves down by one line, then by three. */
    @Test fun menuGlide() {
        var state by androidx.compose.runtime.mutableStateOf(menu(MenuEntry.ALBUM))
        compose.mainClock.autoAdvance = false
        compose.setContent { MenuScreen(state, Lighting.DAY) }
        compose.mainClock.advanceTimeBy(100)
        var frame = 0
        fun grab() = compose.onRoot().captureRoboImage("build/screenshots/options/menu_glide_%02d.png".format(frame++))
        grab()
        state = menu(MenuEntry.GROUP)
        repeat(6) { compose.mainClock.advanceTimeBy(70); grab() }
        state = menu(MenuEntry.DAY_NIGHT)
        repeat(6) { compose.mainClock.advanceTimeBy(70); grab() }
    }

    @Test fun menuConnection() = shot("menu_connection") {
        MenuScreen(menu(MenuEntry.DEMO, Settings(), MenuContext(LinkState.UNCONFIGURED)), Lighting.DAY)
    }
}
