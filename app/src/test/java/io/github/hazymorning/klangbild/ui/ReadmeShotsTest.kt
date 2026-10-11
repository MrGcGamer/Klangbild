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
import io.github.hazymorning.klangbild.text.TestTexts
import io.github.hazymorning.klangbild.ui.flow.FlowStage
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.vista.VistaScreen
import io.github.hazymorning.klangbild.vista.VistaClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * The README's three pictures, in English. When the look changes they are rendered again
 * and saved as docs/screenshots/now-playing.jpg, cover-flow.jpg and window-view.jpg.
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
    private val clock = en.clock(Fixtures.at(21, 47), Fixtures.zone)

    private fun cover(c: SyntheticCover) = Catalogue.SCHEME + c.name

    private val saltWater = TrackInfo(
        "Salt Water (feat. Ida Holm) - Radio Edit", listOf("Northern Light Collective"), "Tides", 2023, 214_000,
        artworkUrl = cover(SyntheticCover.SALT_WATER),
    )
    private val spiegel = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/screenshots/readme/$name.png") { Fixtures.Still(en, content) }

    @Test
    fun nowPlaying() {
        val scene = Fixtures.scene(saltWater, coverField = true)
        shot("now-playing") {
            Stage(Fixtures.model(saltWater, next = spiegel, clock = clock, group = "Living room", scene = scene, t = en))
        }
    }

    @Test
    fun coverFlow() {
        val strip = listOf(
            TrackInfo("Desert Song", listOf("Amal Haddad"), "Cairo Nights", 2016, 289_000, artworkUrl = cover(SyntheticCover.TERRACOTTA)),
            TrackInfo("Neon over the Motorway", listOf("Lumen Drive"), "Night Drive", 2024, 251_000, artworkUrl = cover(SyntheticCover.NIGHT_DRIVE)),
            TrackInfo("White Noise", listOf("Hanna Stein"), "Empty Space", 2017, 400_000, artworkUrl = cover(SyntheticCover.WHITE_SPACE)),
            TrackInfo("Form and Color", listOf("Studio Weimar"), "Bauhaus", 2019, 198_000, artworkUrl = cover(SyntheticCover.BAUHAUS)),
        )
        val scenes = Fixtures.flowScenes(strip, Appearance.DARK)
        shot("cover-flow") {
            FlowStage(
                Fixtures.model(strip[1], next = strip[2], clock = clock, group = "Living room + 5", scene = scenes[1], t = en),
                Fixtures.restingCards(scenes, 1),
            )
        }
    }

    @Test
    fun windowView() {
        val at = java.time.ZonedDateTime.of(2026, 10, 10, 18, 20, 0, 0, Fixtures.zone).toInstant().toEpochMilli()
        val track = TrackInfo("Under the Moss", listOf("Forest House"), durationMs = 213_000, artworkUrl = cover(SyntheticCover.MOSS))
        val ui = UiState(
            screen = Screen.Stage(track, PlayerStatus.PAUSED, null),
            stage = Fixtures.stage(track),
            source = SourceState(LinkState.CONNECTED, place = 52.52 to 13.40, weather = "partlycloudy"),
            vista = VistaUi(track, PlayerStatus.PAUSED, MiniPlayer.LEFT),
        )
        val vistaClock = VistaClock(now = { at })
        shot("window-view") {
            VistaScreen(ui, ui.vista!!, vistaClock, positionNow = { 213_000L / 3 }, now = { at + 60_000L }, modifier = Modifier.fillMaxSize())
        }
    }
}
