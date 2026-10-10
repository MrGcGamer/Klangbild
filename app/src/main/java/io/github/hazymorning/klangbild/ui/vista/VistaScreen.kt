package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.app.VistaUi
import io.github.hazymorning.klangbild.config.MiniPlayer
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.vista.LabelSide
import io.github.hazymorning.klangbild.vista.Place
import io.github.hazymorning.klangbild.vista.VistaClock
import io.github.hazymorning.klangbild.vista.WeatherMood
import io.github.hazymorning.klangbild.vista.World
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Fensterblick as a screen of the app: the picture for Home Assistant's place and weather
 * (or a place guessed from the time zone, and a fair sky), with the music's label on the near
 * bank – or, while the preview is open, the moment it shows.
 */
@Composable
fun VistaScreen(ui: UiState, vista: VistaUi, clock: VistaClock, positionNow: () -> Long?, now: () -> Long, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(ui)
    val feed = remember(clock) {
        VistaFeed(
            place = { latest.source.place?.let { (lat, lon) -> Place(lat, lon) } ?: World.guessPlace(ZoneId.systemDefault()) },
            cover = { WeatherMood.cover(latest.source.weather, latest.source.cloudCoverage) },
            clock = clock,
            wall = now,
        )
    }
    val preview = vista.preview
    // The preview's caption needs a bank to lie on, even with the label switched off.
    val side = when (vista.miniPlayer) {
        MiniPlayer.LEFT -> LabelSide.LEFT
        MiniPlayer.RIGHT -> LabelSide.RIGHT
        MiniPlayer.OFF -> if (preview != null) LabelSide.LEFT else LabelSide.NONE
    }
    val caption = if (preview != null) rememberPreviewCaption(preview, clock) else null
    Fensterblick(feed, modifier, label = side) { look ->
        val text: LabelText? = when {
            caption != null -> caption
            side == LabelSide.NONE -> null
            else -> vista.track?.let { track ->
                LabelText.Music(track, artworkFor(ui, track.pictureKey), vista.status == PlayerStatus.PLAYING, positionNow)
            }
        }
        if (text != null) VistaLabel(text, side, inks = { look().label }, coverLight = { look().labelCover })
    }
}

/** The scene with [pictureKey]'s cover, from the stage or the cover flow, once it is ready. */
private fun artworkFor(ui: UiState, pictureKey: String): ArtworkScene? =
    ui.stage?.takeIf { it.track.pictureKey == pictureKey }?.scene
        ?: ui.flow?.cards?.firstOrNull { it.track.pictureKey == pictureKey }?.scene

/**
 * The preview's caption: the hour and the day it shows, its sky, and how to leave – kept
 * up to date once a second while a day passes in the time-lapse.
 */
@Composable
private fun rememberPreviewCaption(p: VistaClock.Preview, clock: VistaClock): LabelText.Preview {
    val moment by produceState(initialValue = clock.sky(), p) {
        while (true) {
            value = clock.sky()
            delay(if (p.timelapse) 250L else 1_000L)
        }
    }
    val zone = ZoneId.systemDefault()
    val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(moment), zone)
    // The set hour and day as the glide reaches them; the running time in the time-lapse.
    val minute = if (p.timelapse) at.hour * 60 + at.minute else p.minuteOfDay ?: (at.hour * 60 + at.minute)
    val day = if (p.timelapse) at.dayOfYear - 1 else p.dayOfYear ?: (at.dayOfYear - 1)
    val time = "%d:%02d".format(minute / 60, minute % 60)
    val date = at.withDayOfYear((day + 1).coerceIn(1, at.toLocalDate().lengthOfYear())).let { "${it.dayOfMonth}.\u00A0${MONTHS[it.monthValue - 1]}" }
    val sky = when (p.cover) {
        null -> "Wetter wie draußen"
        0f -> "Klarer Himmel"
        1f -> "Bedeckt"
        else -> "Leicht bewölkt"
    } + if (p.timelapse) TimeLine.SEPARATOR + "Ein Tag in zwei\u00A0Minuten" else ""
    return LabelText.Preview(time + TimeLine.SEPARATOR + date, sky, "Zurück beendet die\u00A0Vorschau.")
}

private val MONTHS = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
