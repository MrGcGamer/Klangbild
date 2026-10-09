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
    /** Entity ids of the other speakers playing in sync with this one (this one excluded). */
    val groupMembers: List<String> = emptyList(),
    /** Volume 0..1, null when the player does not report one (a group: the members' average). */
    val volume: Float? = null,
    val muted: Boolean = false,
    /** The entity is a Music Assistant group player ("Erdgeschoss"), not a speaker. */
    val isGroupPlayer: Boolean = false,
)

/** The speakers playing together with the configured one. */
data class SpeakerGroup(
    /** Name of the configured speaker. */
    val self: String?,
    /** Names of the others, in Home Assistant's order; null where a name is not known (yet). */
    val others: List<String?>,
    /** The group's own name, when it has one (a Music Assistant group player). */
    val name: String? = null,
)

/** What Music Assistant's queue adds on top of the player entity. */
data class QueueInfo(
    /** Identity of the current item, to match it against the player state. */
    val currentTitle: String?,
    val current: TrackInfo?,
    val next: TrackInfo?,
    /**
     * Name of the queue the speaker plays from: its own – or that of a group player it
     * plays in, whose members do not list each other in `group_members`.
     */
    val name: String? = null,
)
