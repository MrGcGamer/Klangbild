package io.github.hazymorning.klangbild.data

import io.github.hazymorning.klangbild.domain.PlayerSnapshot
import io.github.hazymorning.klangbild.domain.SpeakerGroup
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.coroutines.flow.StateFlow

/** How healthy the link to the music is – phrased for the UI, never as raw errors. */
enum class LinkState {
    /** No Home Assistant configured yet. */
    UNCONFIGURED,
    CONNECTING,
    CONNECTED,
    /** Lost the connection, retrying with backoff. */
    RECONNECTING,
    /** Home Assistant rejected the token. */
    AUTH_FAILED,
}

/** Everything a source knows right now. Pure data; time-based logic lives in the reducer. */
data class SourceState(
    val link: LinkState,
    /** Null until the entity has been seen. */
    val player: PlayerSnapshot? = null,
    /** True when connected but the configured entity does not exist. */
    val entityMissing: Boolean = false,
    val next: TrackInfo? = null,
    val sunElevation: Double? = null,
    /** Local time the player snapshot was received (for extrapolation without timestamps). */
    val receivedAtMs: Long = 0,
    /** Local minus server clock. */
    val skewMs: Long = 0,
    /** Local time since which the link is not [LinkState.CONNECTED] (for grace periods). */
    val linkDownSinceMs: Long? = null,
    /** The speakers playing in sync with this one; null while it plays alone. */
    val group: SpeakerGroup? = null,
)

enum class RemoteCommand { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN }

interface NowPlayingSource {
    val state: StateFlow<SourceState>
    fun start()
    fun stop()
    suspend fun send(command: RemoteCommand)
}
