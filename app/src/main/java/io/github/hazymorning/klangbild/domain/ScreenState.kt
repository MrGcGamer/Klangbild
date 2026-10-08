package io.github.hazymorning.klangbild.domain

import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.SourceState

/** Why nothing is on stage. Each reason has its own quiet line of text. */
enum class RestReason {
    /** Nothing plays (idle, off, stopped). */
    IDLE,
    /** Paused for a while; the stage has made way for the rest screen. */
    PAUSED,
    /** First connection attempt in progress. */
    CONNECTING,
    /** Connection lost for longer than the grace period. */
    OFFLINE,
    /** The speaker entity reports `unavailable`. */
    UNAVAILABLE,
    /** The configured entity does not exist in Home Assistant. */
    ENTITY_MISSING,
}

enum class SetupReason { FIRST_RUN, AUTH_FAILED, REQUESTED }

/** What the screen shows. */
sealed interface Screen {
    data class Stage(
        val track: TrackInfo,
        val status: PlayerStatus,
        val next: TrackInfo?,
        /** The connection is briefly gone; the stage keeps going for a grace period. */
        val linkDown: Boolean = false,
    ) : Screen

    data class Rest(val reason: RestReason, val speaker: String? = null) : Screen

    data class Setup(val reason: SetupReason) : Screen
}

/** Settings the state machine needs (subset of the app settings). */
data class Timing(
    /** After this long without music the screen is released (or the app closes). */
    val sleepAfterMs: Long = 10 * 60_000L,
    /** A paused stage gives way to the rest screen after this long. */
    val pauseToRestMs: Long = 3 * 60_000L,
    /** Keep showing the stage this long after the connection dropped. */
    val linkGraceMs: Long = 20_000L,
)

data class ScreenDecision(
    val screen: Screen,
    /** Hold FLAG_KEEP_SCREEN_ON. */
    val keepScreenOn: Boolean,
    /** The sleep timeout has passed. */
    val asleep: Boolean,
)

/**
 * Turns the source state plus time into what the screen shows. Pure apart from the one
 * piece of memory it needs: since when no music has been playing.
 */
class ScreenStateMachine(private val timing: Timing = Timing()) {
    private var notPlayingSince: Long? = null
    private var setupRequested = false

    fun requestSetup(show: Boolean) {
        setupRequested = show
    }

    val isSetupRequested: Boolean get() = setupRequested

    fun decide(source: SourceState, now: Long): ScreenDecision {
        val screen = screenFor(source, now)
        val playing = screen is Screen.Stage &&
            (screen.status == PlayerStatus.PLAYING || screen.status == PlayerStatus.BUFFERING)
        notPlayingSince = if (playing) null else (notPlayingSince ?: now)
        val idleFor = notPlayingSince?.let { now - it } ?: 0L
        // Setup gets more time (someone is typing on a phone), but never forever (OLED).
        val limit = if (screen is Screen.Setup) timing.sleepAfterMs * 3 else timing.sleepAfterMs
        val asleep = !playing && idleFor >= limit
        // A paused stage that has waited long enough becomes the rest screen.
        val shown = if (screen is Screen.Stage && screen.status == PlayerStatus.PAUSED && idleFor >= timing.pauseToRestMs) {
            Screen.Rest(RestReason.PAUSED)
        } else {
            screen
        }
        return ScreenDecision(
            screen = shown,
            keepScreenOn = !asleep,
            asleep = asleep,
        )
    }

    private fun screenFor(s: SourceState, now: Long): Screen {
        if (setupRequested) return Screen.Setup(SetupReason.REQUESTED)
        when (s.link) {
            LinkState.UNCONFIGURED -> return Screen.Setup(SetupReason.FIRST_RUN)
            LinkState.AUTH_FAILED -> return Screen.Setup(SetupReason.AUTH_FAILED)
            else -> Unit
        }
        val player = s.player
        val speaker = player?.friendlyName
        val linkDown = s.link == LinkState.CONNECTING || s.link == LinkState.RECONNECTING
        if (linkDown) {
            if (player == null) return Screen.Rest(RestReason.CONNECTING)
            val downFor = s.linkDownSinceMs?.let { now - it } ?: 0L
            val track = player.track
            val wasOnStage = track != null && player.status in stagedStatuses
            return if (wasOnStage && downFor < timing.linkGraceMs) {
                Screen.Stage(track!!, player.status, s.next, linkDown = true)
            } else {
                Screen.Rest(RestReason.OFFLINE, speaker)
            }
        }
        if (s.entityMissing) return Screen.Rest(RestReason.ENTITY_MISSING)
        if (player == null) return Screen.Rest(RestReason.CONNECTING)
        if (player.status == PlayerStatus.UNAVAILABLE) return Screen.Rest(RestReason.UNAVAILABLE, speaker)
        val track = player.track
        if (track != null && player.status in stagedStatuses) {
            return Screen.Stage(track, player.status, s.next)
        }
        return Screen.Rest(RestReason.IDLE, speaker)
    }

    companion object {
        private val stagedStatuses = setOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.BUFFERING)
    }
}
