package io.github.hazymorning.klangbild.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.app.UiState
import io.github.hazymorning.klangbild.config.VolumeDisplay
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.SpeakerLabel
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.ui.stage.StageTexts
import io.github.hazymorning.klangbild.ui.stage.StatusLine
import io.github.hazymorning.klangbild.ui.stage.StageTheatre
import kotlinx.coroutines.delay

/** A wall clock that ticks every [periodMs] (the UI needs minutes, the drift whole pixels). */
@Composable
fun rememberWallClock(periodMs: Long = 5_000, now: () -> Long = System::currentTimeMillis): State<Long> =
    produceState(initialValue = now()) {
        while (true) {
            value = now()
            delay(periodMs)
        }
    }

/** What the root needs from the outside world. */
class RootHooks(
    val positionNow: () -> Long?,
    /** Local time until which hidden parts (time, place, progress) show again after a key press. */
    val peekUntil: State<Long> = mutableLongStateOf(0L),
    val now: () -> Long = System::currentTimeMillis,
)

/**
 * The whole screen. Switches between stage, rest and setup with a slow fade through the
 * dark, fades in from black on start (no white flash, ever) and lets everything drift a
 * few pixels for OLED care.
 */
@Composable
fun KlangbildRoot(ui: UiState, screen: ScreenSpec, hooks: RootHooks, modifier: Modifier = Modifier) {
    val wall = rememberWallClock(now = hooks.now)
    val density = LocalDensity.current.density
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(1_800, easing = Motion.Dimmer)) }

    // The first seconds after a start stay dark instead of flashing "connecting …":
    // usually the music is there before anyone could read that line.
    val quiet by produceState(initialValue = true) {
        delay(QUIET_START_MS)
        value = false
    }
    val content = contentKey(ui, quiet)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = entrance.value
                val (dx, dy) = Motion.drift(wall.value, 12f * density)
                translationX = dx.toFloat()
                translationY = dy.toFloat()
            },
    ) {
        // Animate between *kinds* of screens only (stage/rest/setup). The latest content
        // per kind is kept, so a track change stays inside the stage (its own transition)
        // and an outgoing screen keeps showing what it showed.
        val latest = remember { HashMap<String, Content>() }
        latest[content.key] = content
        AnimatedContent(
            targetState = content.key,
            transitionSpec = {
                fadeIn(tween(1_300, delayMillis = 900, easing = Motion.Dimmer)) togetherWith
                    fadeOut(tween(900, easing = Motion.Dimmer))
            },
            label = "screen",
        ) { k ->
            when (val c = latest[k] ?: Content.Dark) {
                is Content.StageContent -> StageContentView(ui, c, screen, hooks, wall)
                is Content.RestContent -> RestScreen(
                    reason = c.reason,
                    // A group is named here too: "Wohnzimmer + 5 · Pausiert".
                    speaker = c.speaker?.let { name -> SpeakerLabel.group(ui.source.group).takeIf { ui.display.group } ?: name },
                    entityId = ui.entityId,
                    lighting = ui.lighting,
                    nowMs = wall.value,
                    lightFactor = ui.display.light.factor,
                )
                is Content.SetupContent -> {
                    val scene = remember(ui.setup, screen, ui.lighting) { SetupArt.scene(ui.setup, screen, ui.lighting) }
                    SetupScreen(c.reason, ui.setup, scene)
                }
                Content.Dark -> Box(Modifier.fillMaxSize())
            }
        }
        // The settings menu lies over everything and fades in and out on its own.
        MenuOverlay(ui.menu, ui.lighting)
    }
}

private sealed interface Content {
    val key: String

    data class StageContent(val stage: StageData) : Content {
        // One stage view for all tracks: track changes are animated inside the theatre.
        override val key = "stage"
    }

    data class RestContent(val reason: io.github.hazymorning.klangbild.domain.RestReason, val speaker: String?) : Content {
        override val key = "rest:$reason"
    }

    data class SetupContent(val reason: io.github.hazymorning.klangbild.domain.SetupReason) : Content {
        override val key = "setup:$reason"
    }

    data object Dark : Content {
        override val key = "dark"
    }
}

private const val QUIET_START_MS = 4_000L

private fun contentKey(ui: UiState, quietStart: Boolean): Content = when (val s = ui.screen) {
    is Screen.Stage -> ui.stage?.let { Content.StageContent(it) } ?: Content.Dark
    is Screen.Rest ->
        if (quietStart && s.reason == io.github.hazymorning.klangbild.domain.RestReason.CONNECTING) Content.Dark
        else Content.RestContent(s.reason, s.speaker)
    is Screen.Setup -> Content.SetupContent(s.reason)
}

@Composable
private fun StageContentView(ui: UiState, key: Content.StageContent, screen: ScreenSpec, hooks: RootHooks, wall: State<Long>) {
    val stageScreen = ui.screen as? Screen.Stage
    // While a new cover loads, the previous stage stays; status follows the live state.
    // A lost connection (after the grace period) or a muted speaker dims the stage.
    val linkDown = stageScreen?.linkDown == true
    val status = stageScreen?.status ?: PlayerStatus.PAUSED
    val display = ui.display
    val player = ui.source.player
    val muted = player?.muted == true
    // A key press brings hidden parts back for a few seconds.
    val peekUntil = hooks.peekUntil.value
    var peek by remember { mutableStateOf(false) }
    LaunchedEffect(peekUntil) {
        val left = peekUntil - hooks.now()
        if (left > 0) {
            peek = true
            delay(left)
        }
        peek = false
    }
    val clock = if (display.clock || peek) RestTexts.time(wall.value) else null
    val group = SpeakerLabel.group(ui.source.group).takeIf { display.group || peek }
    val volume = SpeakerLabel.volume(player?.volume, muted)
    val lead = StageTexts.lead(status, muted, ui.volumeFlash, volume, group, volumeAlways = display.volume == VolumeDisplay.ALWAYS)
    val statusLine = StatusLine(lead, clock)
    val nextTrack = stageScreen?.next?.takeIf { display.next }
    val stage = key.stage
    val modelFor: (StageData) -> StageModel = remember(stage, status, nextTrack, statusLine, ui.night, linkDown, display, peek) {
        { s: StageData ->
            val text = TrackText.from(s.track)
            StageModel(
                scene = s.scene,
                text = if (display.album) text else text.copy(album = null),
                status = status,
                durationMs = s.track.durationMs,
                positionMs = { if (s.track.key == stage.track.key) hooks.positionNow() else null },
                year = s.track.year.takeIf { display.album },
                progress = display.timeLine || peek,
                line = StageTexts.line(linkDown, s.track),
                next = StageTexts.next(nextTrack),
                nextTitle = StageTexts.nextTitle(nextTrack),
                statusLine = statusLine,
                night = ui.night,
            )
        }
    }
    // Changing the light's strength in the menu turns it like a dimmer.
    val lightFactor by animateFloatAsState(
        targetValue = display.light.factor,
        animationSpec = tween(1_200, easing = Motion.Dimmer),
        label = "light-setting",
    )
    StageTheatre(
        stage = stage,
        status = status,
        modelFor = modelFor,
        lightFactor = { lightFactor },
        dimmed = muted || linkDown,
    )
}
