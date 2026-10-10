package io.github.hazymorning.klangbild.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.app.QuickState
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.app.VistaUi
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.BarLevel
import io.github.hazymorning.klangbild.config.FavoriteState
import io.github.hazymorning.klangbild.config.LightBars
import io.github.hazymorning.klangbild.config.MiniPlayer
import io.github.hazymorning.klangbild.config.QuickContext
import io.github.hazymorning.klangbild.config.QuickItem
import io.github.hazymorning.klangbild.config.QuickMenu
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.SleepChoice
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.vista.VistaScreen
import io.github.hazymorning.klangbild.vista.VistaClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** The quick settings along the bottom edge: over the stage on black and on paper, and over the Fensterblick. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class QuickBarTest {
    @Before
    fun berlin() {
        TimeZone.setDefault(TimeZone.getTimeZone(Fixtures.zone))
    }

    private val withBars = Settings(bars = LightBars(left = "light.links", center = "light.mitte", right = "light.rechts"), barLevel = BarLevel.P50)

    private fun state(focus: QuickItem, ctx: QuickContext, s: Settings = withBars): QuickState {
        val rows = QuickMenu.rows(s, ctx)
        return QuickState(rows, rows.indexOfFirst { it.item == focus })
    }

    private val playing = QuickContext(music = true, favorite = FavoriteState.NONE)

    @Test
    fun overTheStage() {
        val track = Fixtures.track(SyntheticCover.NACHTFAHRT)
        captureRoboImage("build/screenshots/quick/stage_dark.png") {
            Fixtures.Still {
                Box(Modifier.fillMaxSize()) {
                    Stage(Fixtures.model(track, next = Fixtures.pärt))
                    QuickBar(state(QuickItem.PICTURE, playing), Lighting.DAY, Appearance.DARK)
                }
            }
        }
        captureRoboImage("build/screenshots/quick/stage_dark_favorite.png") {
            Fixtures.Still {
                Box(Modifier.fillMaxSize()) {
                    Stage(Fixtures.model(track, next = Fixtures.pärt))
                    QuickBar(state(QuickItem.FAVORITE, playing.copy(favorite = FavoriteState.DONE, sleep = SleepChoice.ITEM_END)), Lighting.DAY, Appearance.DARK)
                }
            }
        }
        val light = Fixtures.track(SyntheticCover.BAUHAUS)
        captureRoboImage("build/screenshots/quick/stage_light.png") {
            Fixtures.Still {
                Box(Modifier.fillMaxSize()) {
                    Stage(Fixtures.model(light, next = Fixtures.pärt, appearance = Appearance.LIGHT))
                    QuickBar(state(QuickItem.SLEEP, playing), Lighting.DAY, Appearance.LIGHT)
                }
            }
        }
        // Without light bars and without favourites (no Music Assistant): two entries.
        captureRoboImage("build/screenshots/quick/stage_few.png") {
            Fixtures.Still {
                Box(Modifier.fillMaxSize()) {
                    Stage(Fixtures.model(track, next = Fixtures.pärt))
                    QuickBar(state(QuickItem.PICTURE, QuickContext(music = true), Settings()), Lighting.EVENING, Appearance.DARK)
                }
            }
        }
    }

    @Test
    fun overTheFensterblick() {
        val t = java.time.ZonedDateTime.of(2026, 10, 10, 13, 0, 0, 0, Fixtures.zone).toInstant().toEpochMilli()
        val track = Fixtures.track(SyntheticCover.BAUHAUS)
        val ui = UiState(
            screen = Screen.Stage(track, PlayerStatus.PLAYING, null),
            stage = Fixtures.stage(track),
            source = SourceState(LinkState.CONNECTED, place = 52.52 to 13.40, weather = "partlycloudy"),
            vista = VistaUi(track, PlayerStatus.PLAYING, MiniPlayer.LEFT),
        )
        val clock = VistaClock(now = { t })
        captureRoboImage("build/screenshots/quick/vista.png") {
            Fixtures.Still {
                Box(Modifier.fillMaxSize()) {
                    VistaScreen(ui, ui.vista!!, clock, positionNow = { 60_000L }, now = { t }, modifier = Modifier.fillMaxSize())
                    QuickBar(state(QuickItem.PICTURE, playing.copy(vista = true)), Lighting.DAY, Appearance.DARK)
                }
            }
        }
    }
}
