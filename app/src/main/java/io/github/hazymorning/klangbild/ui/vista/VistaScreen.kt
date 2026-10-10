package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.app.VistaUi
import io.github.hazymorning.klangbild.config.MiniPlayer
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.vista.LabelSide
import io.github.hazymorning.klangbild.vista.Place
import io.github.hazymorning.klangbild.vista.VistaClock
import io.github.hazymorning.klangbild.vista.WeatherMood
import io.github.hazymorning.klangbild.vista.World
import java.time.ZoneId

/**
 * The Fensterblick as a screen of the app: the picture for Home Assistant's place and weather
 * (or a place guessed from the time zone, and a fair sky), with the music's label on the near
 * bank.
 */
@Composable
fun VistaScreen(ui: UiState, vista: VistaUi, clock: VistaClock, positionNow: () -> Long?, now: () -> Long, modifier: Modifier = Modifier) {
    val feed = rememberVistaFeed(ui, clock, now)
    val side = labelSide(vista.miniPlayer)
    Fensterblick(feed, modifier, label = side) { look ->
        val text = vista.track?.takeIf { side != LabelSide.NONE }?.let { track ->
            LabelText.Music(track, artworkFor(ui, track.pictureKey), vista.status == PlayerStatus.PLAYING, positionNow)
        }
        if (text != null) VistaLabel(text, side, inks = { look().label }, coverLight = { look().labelCover })
    }
}

/** The Fensterblick's view of the world: Home Assistant's place (or one guessed from the time zone) and weather. */
@Composable
fun rememberVistaFeed(ui: UiState, clock: VistaClock, now: () -> Long): VistaFeed {
    val latest by rememberUpdatedState(ui)
    return remember(clock) {
        VistaFeed(
            place = { latest.source.place?.let { (lat, lon) -> Place(lat, lon) } ?: World.guessPlace(ZoneId.systemDefault()) },
            weather = { WeatherMood.of(latest.source.weather, latest.source.cloudCoverage, latest.source.windKmh) },
            clock = clock,
            wall = now,
        )
    }
}

/** The label's side for the mini-player setting. */
fun labelSide(miniPlayer: MiniPlayer): LabelSide = when (miniPlayer) {
    MiniPlayer.LEFT -> LabelSide.LEFT
    MiniPlayer.RIGHT -> LabelSide.RIGHT
    MiniPlayer.OFF -> LabelSide.NONE
}

/** The scene with [pictureKey]'s cover, from the stage or the cover flow, once it is ready. */
private fun artworkFor(ui: UiState, pictureKey: String): ArtworkScene? =
    ui.stage?.takeIf { it.track.pictureKey == pictureKey }?.scene
        ?: ui.flow?.cards?.firstOrNull { it.track.pictureKey == pictureKey }?.scene
