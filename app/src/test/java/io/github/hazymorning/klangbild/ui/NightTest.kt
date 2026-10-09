package io.github.hazymorning.klangbild.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.contrastRatio
import io.github.hazymorning.klangbild.color.relativeLuminance
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.render.FlowGeometry
import io.github.hazymorning.klangbild.render.ScreenSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The night dims, it never leaves anything out: deep in the night the stage still shows the
 * album and what comes next, Cover Flow its cards beside the middle, and the rest screen its
 * date and room – the whole way from the app's state to the screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class NightTest {
    @get:Rule
    val compose = createComposeRule()

    private val mdpi = ScreenSpec(960, 540, 1f)
    private val hooks = RootHooks(positionNow = { 60_000L }, now = { 0L })

    private fun show(ui: UiState) {
        compose.mainClock.autoAdvance = false
        compose.setContent { KlangbildRoot(ui, mdpi, hooks) }
        compose.mainClock.advanceTimeBy(6_000) // the quiet start and the entrance fade
    }

    @Test
    fun theStageAtNightKeepsEveryLine() {
        val track = Fixtures.track(SyntheticCover.NEBELLAND)
        val stage = Fixtures.stage(track, mdpi, Lighting.NIGHT)
        show(UiState(screen = Screen.Stage(track, PlayerStatus.PLAYING, Fixtures.pärt), stage = stage, lighting = Lighting.NIGHT))
        compose.onNodeWithText("Préludes", substring = true).assertExists()
        compose.onNodeWithText("Als Nächstes", substring = true).assertExists()
    }

    @Test
    fun coverFlowAtNightKeepsTheCardsBesideTheMiddle() {
        val covers = listOf(SyntheticCover.TERRAKOTTA, SyntheticCover.MOOS, SyntheticCover.NACHTFAHRT, SyntheticCover.LEERRAUM, SyntheticCover.BAUHAUS)
        val scenes = covers.mapIndexed { i, c -> i.toLong() to Fixtures.scene(Fixtures.track(c), mdpi, Lighting.NIGHT, Appearance.DARK, StageView.FLOW) }.toMap()
        val flow = Fixtures.flow(scenes, listOf(0, 1, 2, 3, 4), 2)
        val middle = flow.middle
        show(
            UiState(
                screen = Screen.Stage(middle.track, PlayerStatus.PLAYING, flow.cards[3].track),
                view = StageView.FLOW,
                flow = flow,
                lighting = Lighting.NIGHT,
            ),
        )
        compose.onNodeWithText("Als Nächstes", substring = true).assertExists()
        // The middle of the card to the left (dp): it is there, not gone into the dark.
        val corners = FloatArray(8).also { FlowGeometry(middle.scene.layout).corners(-1f, it) }
        val cx = (corners[0] + corners[2] + corners[4] + corners[6]) / 4f
        val cy = (corners[1] + corners[3] + corners[5] + corners[7]) / 4f
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        var sum = 0f
        var n = 0
        for (y in (cy - 8f).toInt()..(cy + 8f).toInt()) for (x in (cx - 4f).toInt()..(cx + 4f).toInt()) {
            sum += relativeLuminance(shot.getPixel(x, y))
            n++
        }
        assertThat(sum / n).isGreaterThan(0.01f)
    }

    @Test
    fun theQuietScreensStillReadAtNight() {
        for (appearance in Appearance.entries) for (lighting in listOf(Lighting.DAY, Lighting.EVENING, Lighting.NIGHT)) {
            val c = QuietColors.of(lighting, appearance)
            val wall = c.wall.toArgb()
            for (ink in listOf(c.primary, c.secondary, c.label)) {
                assertWithMessage("$appearance $lighting").that(contrastRatio(ink.toArgb(), wall)).isAtLeast(4.5f)
            }
        }
    }

    @Test
    fun theRestScreenAtNightKeepsDateAndRoom() {
        val at = Fixtures.at(2, 13, day = 9)
        compose.setContent {
            RestScreen(RestReason.PAUSED, "Wohnzimmer", null, Lighting.NIGHT, at, zone = Fixtures.zone)
        }
        compose.onNodeWithText("Wohnzimmer", substring = true).assertExists()
        compose.onNodeWithText(RestTexts.date(at, Fixtures.zone)).assertExists()
    }
}
