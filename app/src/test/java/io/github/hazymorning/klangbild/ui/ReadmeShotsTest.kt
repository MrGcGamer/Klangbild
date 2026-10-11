package io.github.hazymorning.klangbild.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.app.VistaUi
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.config.MiniPlayer
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.lights.CoverLightColors
import io.github.hazymorning.klangbild.lights.LightColor
import io.github.hazymorning.klangbild.lights.LightColors
import io.github.hazymorning.klangbild.lights.LightSlot
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.text.TestTexts
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.vista.VistaScreen
import io.github.hazymorning.klangbild.vista.VistaClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.shredzone.commons.suncalc.SunTimes
import java.io.File
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * The screens in the README's pictures, in English; beside each song the colors the lights
 * behind the TV would show (a JSON file). `tools/make_readme_pictures.py` sets them in a room
 * and saves the pictures to docs/screenshots/ when the look changes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ReadmeShotsTest {
    @Before
    fun berlin() {
        TimeZone.setDefault(TimeZone.getTimeZone(Fixtures.zone))
    }

    private val en = TestTexts.english()

    private fun cover(c: SyntheticCover) = Catalogue.SCHEME + c.name

    private val polarNight = TrackInfo("Polar Night", listOf("Ida Holm"), "Northern Lights", 2025, 258_000, artworkUrl = cover(SyntheticCover.AURORA))
    private val saltWater = TrackInfo("Salt Water", listOf("Northern Light Collective"), "Tides", 2023, 214_000, artworkUrl = cover(SyntheticCover.SALT_WATER))
    private val desertSong = TrackInfo("Desert Song", listOf("Amal Haddad"), "Cairo Nights", 2016, 289_000, artworkUrl = cover(SyntheticCover.TERRACOTTA))

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/screenshots/readme/$name.png") { Fixtures.Still(en, content) }

    /** The lights' colors for a picture: left, middle (above the TV), right; linear sRGB and the light's own xy. */
    private fun lights(name: String, colors: LightColors, warmth: Float) {
        val rgb = FloatArray(3)
        val json = LightSlot.entries.joinToString(",\n", "{\"lights\": [\n", "\n]}\n") { slot ->
            colors.color(slot, 0.0, rgb)
            val xy = LightColor.of(rgb, warmth)
            "  {\"slot\": \"${slot.name}\", \"rgb\": [${rgb[0]}, ${rgb[1]}, ${rgb[2]}], \"xy\": [${xy.x}, ${xy.y}], \"dim\": ${LightColor.dimFor(rgb)}}"
        }
        File("build/screenshots/readme/$name.json").apply { parentFile?.mkdirs() }.writeText(json)
    }

    private fun coverLights(name: String, scene: ArtworkScene, warmth: Float) = lights(name, CoverLightColors(scene.field!!, moving = false), warmth)

    /** The first picture: a song at night on the dark wall, the room lit in its colors. */
    @Test
    fun nowPlaying() {
        val scene = Fixtures.scene(polarNight)
        shot("now-playing") {
            Stage(Fixtures.model(polarNight, next = saltWater, positionMs = 94_000L, clock = en.clock(Fixtures.at(21, 47), Fixtures.zone), group = "Living room", scene = scene, t = en))
        }
        coverLights("now-playing", scene, warmth = 1f)
    }

    /** The window view at dusk in spring, the mini player bottom left. */
    @Test
    fun windowView() {
        val date = ZonedDateTime.of(2026, 4, 26, 0, 0, 0, 0, Fixtures.zone)
        val set = SunTimes.compute().on(date).at(52.52, 13.40).execute().set!!.withZoneSameInstant(Fixtures.zone)
        val at = set.plusMinutes(8).toInstant().toEpochMilli()
        val track = TrackInfo("Under the Moss", listOf("Forest House"), durationMs = 213_000, artworkUrl = cover(SyntheticCover.MOSS))
        val ui = UiState(
            screen = Screen.Stage(track, PlayerStatus.PAUSED, null),
            stage = Fixtures.stage(track),
            source = SourceState(LinkState.CONNECTED, place = 52.52 to 13.40, weather = "sunny"),
            vista = VistaUi(track, PlayerStatus.PAUSED, MiniPlayer.LEFT),
        )
        val vistaClock = VistaClock(now = { at })
        shot("window-view") {
            VistaScreen(ui, ui.vista!!, vistaClock, positionNow = { 213_000L / 3 }, now = { at }, modifier = Modifier.fillMaxSize())
        }
    }

    /** One song twice: on the light wall by day and on the dark wall at night. */
    @Test
    fun dayAndNight() {
        val day = Fixtures.scene(desertSong, appearance = Appearance.LIGHT)
        shot("day") {
            Stage(Fixtures.model(desertSong, next = polarNight, positionMs = 61_000L, clock = en.clock(Fixtures.at(14, 15), Fixtures.zone), group = "Living room", scene = day, t = en, appearance = Appearance.LIGHT))
        }
        val night = Fixtures.scene(desertSong)
        shot("night") {
            Stage(Fixtures.model(desertSong, next = polarNight, positionMs = 61_000L, clock = en.clock(Fixtures.at(22, 30), Fixtures.zone), group = "Living room", scene = night, t = en))
        }
        coverLights("day", day, warmth = 0f)
        coverLights("night", night, warmth = 1f)
    }
}
