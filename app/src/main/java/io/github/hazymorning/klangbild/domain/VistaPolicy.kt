package io.github.hazymorning.klangbild.domain

/**
 * When the Fensterblick takes the screen – and when the quiet clock keeps it:
 *
 * - **Something is wrong** (connecting, Home Assistant away, the speaker unavailable or
 *   unknown) or setup is open: never – those screens have something to say.
 * - **Asked for** (the remote's held down key, a link, Home Assistant): at once, whatever plays,
 *   until a key is pressed.
 * - **Paused, or nothing playing:** after [pauseMs] without music and without a key pressed
 *   (0: never – then the paused cover gives way to the clock after three minutes, as before).
 * - **Playing:** after [playingMs] of music without a key pressed (0: never).
 *
 * Every key press ends it; the delays start again from that press. The rest clock is left for
 * the moments with nothing to show but the time: nothing plays and the Fensterblick is off, or
 * the delay has not passed yet.
 */
class VistaPolicy {
    private var playingSince: Long? = null
    private var quietSince: Long? = null
    private var lastKey = 0L

    /** Shown because it was asked for, until the next key. */
    var requested = false
        private set

    /** A key was pressed: the Fensterblick goes, and its delays start again. */
    fun interaction(now: Long) {
        lastKey = now
        requested = false
    }

    /** Asked for from outside or with the held key. */
    fun request(show: Boolean) {
        requested = show
    }

    /** Whether the Fensterblick shows instead of [screen] now. */
    fun shows(screen: Screen, now: Long, pauseMs: Long, playingMs: Long): Boolean {
        val playing = screen is Screen.Stage && !screen.linkDown &&
            (screen.status == PlayerStatus.PLAYING || screen.status == PlayerStatus.BUFFERING)
        playingSince = if (playing) playingSince ?: now else null
        val quiet = when (screen) {
            is Screen.Stage -> screen.status == PlayerStatus.PAUSED && !screen.linkDown
            is Screen.Rest -> screen.reason == RestReason.IDLE || screen.reason == RestReason.PAUSED
            is Screen.Setup -> false
        }
        quietSince = if (quiet) quietSince ?: now else null
        val trouble = screen is Screen.Setup || (screen is Screen.Rest && !quiet)
        if (trouble) return false
        if (requested) return true
        val since = when {
            playing -> playingSince?.takeIf { playingMs > 0 }?.let { it to playingMs }
            quiet -> quietSince?.takeIf { pauseMs > 0 }?.let { it to pauseMs }
            else -> null
        } ?: return false
        val (from, delay) = since
        return now - maxOf(from, lastKey) >= delay
    }
}
