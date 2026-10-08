package io.github.hazymorning.klangbild.domain

/** Playback state of the speaker, independent of Home Assistant's string values. */
enum class PlayerStatus { PLAYING, PAUSED, BUFFERING, IDLE, OFF, UNAVAILABLE, UNKNOWN }

/**
 * One consistent view of the speaker. [positionMs] was valid at [positionAtEpochMs]
 * (wall clock of the *server*); the UI extrapolates from there.
 */
data class PlayerSnapshot(
    val status: PlayerStatus,
    val track: TrackInfo?,
    val positionMs: Long? = null,
    val positionAtEpochMs: Long? = null,
    val friendlyName: String? = null,
    /** Music Assistant queue id when MA drives the player (for get_queue enrichment). */
    val activeQueue: String? = null,
    val isMusicAssistant: Boolean = false,
)

/** What Music Assistant's queue adds on top of the player entity. */
data class QueueInfo(
    /** Identity of the current item, to match it against the player state. */
    val currentTitle: String?,
    val current: TrackInfo?,
    val next: TrackInfo?,
)
