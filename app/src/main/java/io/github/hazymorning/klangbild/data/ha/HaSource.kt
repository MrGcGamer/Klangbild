package io.github.hazymorning.klangbild.data.ha

import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.domain.PlayerSnapshot
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.QueueInfo
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.Normalizer

/**
 * Home Assistant as the source: the configured media_player plus `sun.sun`, enriched with
 * Music Assistant's queue (next track, full-size artwork, individual artist names).
 */
class HaSource(
    http: OkHttpClient,
    private val scope: CoroutineScope,
    private val config: HaConfig,
    private val playerEntity: String,
    private val sunEntity: String = "sun.sun",
    private val now: () -> Long = System::currentTimeMillis,
) : NowPlayingSource {

    private val connection = HaConnection(http, scope)
    private val _state = MutableStateFlow(SourceState(link = LinkState.CONNECTING))
    override val state: StateFlow<SourceState> = _state.asStateFlow()

    private var collector: Job? = null
    private var queuePoller: Job? = null
    @Volatile private var queue: QueueInfo? = null
    @Volatile private var queueForKey: String? = null
    @Volatile private var lastPlayerKey: String? = null
    private var linkDownSince: Long? = null
    private var lastSnapshot: PlayerSnapshot? = null
    private var receivedAt = 0L

    override fun start() {
        connection.start(config, listOf(playerEntity, sunEntity))
        collector = scope.launch {
            combine(connection.status, connection.entities) { st, ents -> st to ents }.collect { (st, ents) ->
                publish(st, ents)
            }
        }
        queuePoller = scope.launch {
            // Light periodic refresh: the queue can change without the track changing.
            while (isActive) {
                delay(QUEUE_REFRESH_MS)
                if (_state.value.player?.status == PlayerStatus.PLAYING) refreshQueue(force = true)
            }
        }
    }

    override fun stop() {
        collector?.cancel()
        queuePoller?.cancel()
        connection.stop()
    }

    /** The network came back: do not wait for the backoff timer. */
    fun networkAvailable() = connection.retryNow()

    override suspend fun send(command: RemoteCommand) {
        val service = when (command) {
            RemoteCommand.PLAY_PAUSE -> "media_play_pause"
            RemoteCommand.NEXT -> "media_next_track"
            RemoteCommand.PREVIOUS -> "media_previous_track"
        }
        runCatching { connection.callService("media_player", service, playerEntity) }
    }

    private fun publish(status: HaStatus, entities: Map<String, EntityState>) {
        val t = now()
        val link = when (status) {
            HaStatus.Connected -> LinkState.CONNECTED
            is HaStatus.AuthFailed -> LinkState.AUTH_FAILED
            is HaStatus.Connecting -> if (lastSnapshot == null && linkDownSince == null) LinkState.CONNECTING else LinkState.RECONNECTING
            is HaStatus.Waiting -> LinkState.RECONNECTING
            HaStatus.Stopped -> LinkState.CONNECTING
        }
        linkDownSince = if (link == LinkState.CONNECTED) null else (linkDownSince ?: t)

        val entity = entities[playerEntity]
        val connectedAndLoaded = status == HaStatus.Connected && entities.isNotEmpty()
        var player = entity?.let { PlayerParser.player(it, config.baseUrl) }
        if (player != null && player != lastSnapshot) {
            receivedAt = t
            lastSnapshot = player
        }
        player = player ?: lastSnapshot.takeIf { link != LinkState.CONNECTED }

        val key = player?.track?.let { trackKey(it) }
        if (key != null && key != lastPlayerKey) {
            lastPlayerKey = key
            if (player?.isMusicAssistant == true) scope.launch { refreshQueue(force = false) }
        }

        val enriched = player?.let { enrich(it) }
        val sun = entities[sunEntity]?.number("elevation")
        _state.value = SourceState(
            link = link,
            player = enriched,
            entityMissing = connectedAndLoaded && entity == null,
            next = queue?.takeIf { queueForKey == key }?.next,
            sunElevation = sun ?: _state.value.sunElevation,
            receivedAtMs = receivedAt,
            skewMs = connection.clockSkew.offsetMs,
            linkDownSinceMs = linkDownSince,
        )
    }

    /** Prefer the queue's richer data when it describes the same track. */
    private fun enrich(p: PlayerSnapshot): PlayerSnapshot {
        val track = p.track ?: return p
        val q = queue ?: return p
        val current = q.current ?: return p
        if (queueForKey != trackKey(track) || !sameTitle(current.title, track.title)) return p
        return p.copy(
            track = track.copy(
                artists = current.artists.ifEmpty { track.artists },
                album = track.album ?: current.album,
                year = current.year ?: track.year,
                artworkUrl = current.artworkUrl ?: track.artworkUrl,
                artworkFallbackUrl = track.artworkUrl ?: track.artworkFallbackUrl,
                source = current.source ?: track.source,
            ),
        )
    }

    private suspend fun refreshQueue(force: Boolean) {
        val key = lastPlayerKey ?: return
        if (!force && queueForKey == key) return
        delay(350) // let a burst of state changes settle
        val response = runCatching {
            connection.callService("music_assistant", "get_queue", playerEntity, returnResponse = true)
        }.getOrNull() ?: return
        val parsed = PlayerParser.queue(response, playerEntity, config.baseUrl) ?: return
        queue = parsed
        queueForKey = key
        publish(connection.status.value, connection.entities.value)
    }

    private fun trackKey(t: TrackInfo) = norm(t.title) + "|" + norm(t.artists.joinToString())

    companion object {
        const val QUEUE_REFRESH_MS = 60_000L

        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).filter { it.isLetterOrDigit() }

        fun sameTitle(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            return x == y || (x.isNotEmpty() && y.isNotEmpty() && (x.startsWith(y) || y.startsWith(x)))
        }
    }
}
