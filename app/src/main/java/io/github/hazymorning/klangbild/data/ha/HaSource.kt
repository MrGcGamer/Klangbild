package io.github.hazymorning.klangbild.data.ha

import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.data.ma.MaAuthException
import io.github.hazymorning.klangbild.data.ma.MaClient
import io.github.hazymorning.klangbild.data.ma.MaLink
import io.github.hazymorning.klangbild.data.ma.MaQueue
import io.github.hazymorning.klangbild.data.ma.QueueWindow
import io.github.hazymorning.klangbild.lights.LightBridge
import io.github.hazymorning.klangbild.domain.MediaKind
import io.github.hazymorning.klangbild.domain.PlayerSnapshot
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.QueueInfo
import io.github.hazymorning.klangbild.domain.SpeakerGroup
import io.github.hazymorning.klangbild.domain.SpeakerLabel
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.text.Normalizer

/**
 * Home Assistant as the source: the configured media_player plus `sun.sun`, enriched with
 * Music Assistant's queue (next track, full-size artwork, individual artist names) – and,
 * when Music Assistant itself is set up ([ma]), with the whole queue around the current track.
 */
class HaSource(
    http: OkHttpClient,
    private val scope: CoroutineScope,
    private val config: HaConfig,
    private val playerEntity: String,
    private val sunEntity: String = "sun.sun",
    private val now: () -> Long = System::currentTimeMillis,
    private val ma: MaClient? = null,
    /** The weather entities to read the sky's cover from, the first that exists counts (none: no weather). */
    private val weatherEntities: List<String> = emptyList(),
    /** How often the queue is read again while music plays (shorter in tests). */
    private val queueRefreshMs: Long = QUEUE_REFRESH_MS,
) : NowPlayingSource, LightBridge {

    private val connection = HaConnection(http, scope)
    private val _state = MutableStateFlow(SourceState(link = LinkState.CONNECTING))
    override val state: StateFlow<SourceState> = _state.asStateFlow()

    private var collector: Job? = null
    private var queuePoller: Job? = null

    /** The one queue read under way: a newer track cancels it (quick skips leave nothing running). */
    private var queueJob: Job? = null
    @Volatile private var queue: QueueInfo? = null
    @Volatile private var window: QueueWindow? = null
    @Volatile private var queueForKey: String? = null
    @Volatile private var maLink: MaLink? = null
    @Volatile private var lastPlayerKey: String? = null

    /** Home Assistant answered the last `get_queue` – a player without a queue is not asked again until its track changes. */
    @Volatile private var queueWorks = false

    /** Music Assistant's queue was last read at this time, for what it was then (id, place, next). */
    private var windowReadAt = 0L
    private var windowFor: Triple<String?, Int?, String?>? = null
    private var linkDownSince: Long? = null
    private var lastSnapshot: PlayerSnapshot? = null
    private var receivedAt = 0L
    private var watched: Set<String> = emptySet()

    /** The colour lights behind the TV, followed for [LightSync] alongside the group's speakers. */
    @Volatile private var lightIds: Set<String> = emptySet()
    private val _lights = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val lights: StateFlow<Map<String, EntityState>> = _lights.asStateFlow()

    override fun follow(ids: Set<String>) {
        if (ids == lightIds) return
        lightIds = ids
        publish(connection.status.value, connection.entities.value)
    }

    override suspend fun callLight(service: String, entityId: String, data: JsonObject?): String? = try {
        connection.callServiceForContext("light", service, entityId, data)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override fun start() {
        connection.start(config, listOf(playerEntity, sunEntity) + weatherEntities)
        // How many Music Assistant speakers there are: a group of all of them is "everywhere".
        connection.watchTemplate(SPEAKER_COUNT_TEMPLATE)
        collector = scope.launch {
            combine(connection.status, connection.entities, connection.templateResult, connection.config) { st, ents, _, _ -> st to ents }
                .collect { (st, ents) -> publish(st, ents) }
        }
        queuePoller = scope.launch {
            ma?.let {
                maLink = it.check()
                publish(connection.status.value, connection.entities.value)
            }
            // Light periodic refresh: the queue can change without the track changing – asked
            // only of a Music Assistant player that has a queue, and only while it plays.
            while (isActive) {
                delay(queueRefreshMs)
                val p = _state.value.player
                if (p?.status == PlayerStatus.PLAYING && p.isMusicAssistant && queueWorks) startQueueRead(force = true)
            }
        }
    }

    override fun stop() {
        collector?.cancel()
        queuePoller?.cancel()
        queueJob?.cancel()
        connection.stop()
    }

    /** The network came back: do not wait for the backoff timer. */
    fun networkAvailable() = connection.retryNow()

    override suspend fun send(command: RemoteCommand) {
        val service = when (command) {
            RemoteCommand.PLAY_PAUSE -> "media_play_pause"
            RemoteCommand.PLAY -> "media_play"
            RemoteCommand.PAUSE -> "media_pause"
            RemoteCommand.NEXT -> "media_next_track"
            RemoteCommand.PREVIOUS -> "media_previous_track"
            RemoteCommand.VOLUME_UP -> "volume_up"
            RemoteCommand.VOLUME_DOWN -> "volume_down"
        }
        runCatching { connection.callService("media_player", service, playerEntity) }
    }

    /** Home Assistant passes whole seconds on to Music Assistant, whatever the speed: the item's own time. */
    override suspend fun seek(positionMs: Long) {
        val data = buildJsonObject { put("seek_position", positionMs.coerceAtLeast(0) / 1000) }
        runCatching { connection.callService("media_player", "media_seek", playerEntity, data = data) }
    }

    /** Called from the status/entity collector and after queue refreshes – serialised. */
    @Synchronized
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
            if (player.isMusicAssistant) startQueueRead(force = false)
        }

        val enriched = player?.let { enrich(it) }
        val sun = entities[sunEntity]?.number("elevation")
        val weather = weatherEntities.firstNotNullOfOrNull { entities[it] }
        // The other speakers of a group are followed too, for their names – and the light bars.
        val others = player?.groupMembers.orEmpty()
        val follow = others.toSet() + lightIds
        if (follow != watched) {
            watched = follow
            connection.watch(watched)
        }
        val lightStates = lightIds.mapNotNull { id -> entities[id]?.let { id to it } }.toMap()
        if (lightStates != _lights.value) _lights.value = lightStates
        val queueKnown = key != null && queueForKey == key
        val currentQueue = queue?.takeIf { queueKnown }
        val currentWindow = window?.takeIf { queueKnown }
        _state.value = SourceState(
            link = link,
            player = enriched,
            entityMissing = connectedAndLoaded && entity == null,
            next = currentQueue?.next,
            sunElevation = sun ?: _state.value.sunElevation,
            receivedAtMs = receivedAt,
            skewMs = connection.clockSkew.offsetMs,
            linkDownSinceMs = linkDownSince,
            group = player?.let { groupOf(it, others.map { id -> entities[id]?.string("friendly_name") }, currentQueue) }
                ?.let { g -> if (isEverywhere(g)) g.copy(everywhere = true) else g },
            queueKnown = queueKnown,
            coming = currentWindow?.coming.orEmpty(),
            played = currentWindow?.played.orEmpty(),
            ma = if (ma == null) null else maLink,
            place = placeOf(connection.config.value) ?: _state.value.place,
            weather = weather?.state?.takeIf { it != "unavailable" && it != "unknown" } ?: _state.value.weather.takeIf { weather == null },
            cloudCoverage = weather?.number("cloud_coverage") ?: _state.value.cloudCoverage.takeIf { weather == null },
        )
    }

    /** Home Assistant's latitude and longitude from its configuration. */
    private fun placeOf(cfg: JsonObject?): Pair<Double, Double>? {
        val lat = (cfg?.get("latitude") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
        val lon = (cfg["longitude"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
        return if (lat == 0.0 && lon == 0.0) null else lat to lon
    }

    /**
     * Who plays along: a sync group lists its members in `group_members` (on the leader and
     * on every member); a Music Assistant group player lists only its children, and its
     * members list nobody – they only play from the group's queue, which has its name.
     */
    private fun groupOf(p: PlayerSnapshot, otherNames: List<String?>, q: QueueInfo?): SpeakerGroup? = when {
        p.isGroupPlayer -> SpeakerGroup(self = null, others = otherNames, name = p.friendlyName)
        otherNames.isNotEmpty() -> SpeakerGroup(self = p.friendlyName, others = otherNames)
        else -> q?.name?.takeIf { SpeakerLabel.isOtherName(it, p.friendlyName) }
            ?.let { SpeakerGroup(self = p.friendlyName, others = emptyList(), name = it) }
    }

    /**
     * Prefer the queue's richer data when it describes the same track; Music Assistant's own
     * queue adds a book's authors, an episode's show, the chapters and the speed.
     */
    private fun enrich(p: PlayerSnapshot): PlayerSnapshot {
        val track = p.track ?: return p
        val q = queue ?: return p
        val current = q.current ?: return p
        if (queueForKey != trackKey(track) || !sameTitle(current.title, track.title)) return p
        val w = window?.takeIf { w -> w.current?.let { sameTitle(it.title, track.title) } == true }
        val mine = w?.current
        // Only Music Assistant's own queue knows the album's year.
        return p.copy(
            track = track.copy(
                artists = current.artists.ifEmpty { mine?.artists.orEmpty() }.ifEmpty { track.artists },
                album = track.album ?: current.album,
                year = current.year ?: mine?.year ?: track.year,
                artworkUrl = current.artworkUrl ?: track.artworkUrl,
                artworkFallbackUrl = track.artworkUrl ?: track.artworkFallbackUrl,
                source = current.source ?: track.source,
                kind = listOf(track.kind, mine?.kind, current.kind).firstOrNull { it != null && it != MediaKind.MUSIC } ?: MediaKind.MUSIC,
                chapters = mine?.chapters.orEmpty(),
            ),
            speed = w?.speed ?: p.speed,
        )
    }

    /** Starts reading the queue anew; a read under way for an older track is cancelled, a periodic one waits its turn. */
    private fun startQueueRead(force: Boolean) {
        synchronized(this) {
            if (force && queueJob?.isActive == true) return
            queueJob?.cancel()
            queueJob = scope.launch { refreshQueue(force) }
        }
    }

    private suspend fun refreshQueue(force: Boolean) {
        val key = lastPlayerKey ?: return
        if (!force && queueForKey == key) return
        delay(350) // let a burst of state changes settle
        if (lastPlayerKey != key) return
        val response = try {
            connection.callService("music_assistant", "get_queue", playerEntity, returnResponse = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        queueWorks = response != null
        // No answer (a source without a queue, like Spotify Connect): nothing is known to follow.
        // A periodic refresh that fails keeps what is known.
        val again = force && queueForKey == key
        val parsed = response?.let { PlayerParser.queue(it, playerEntity, config.baseUrl) }
        if (parsed == null && again) return
        // Music Assistant's queue is read again only when something moved – or for spoken word,
        // whose speed can change at any time.
        val read = parsed?.let { q -> if (!again || windowStale(q)) readWindow(q) else window } ?: window.takeIf { again }
        if (lastPlayerKey != key) return
        queue = parsed
        window = read
        queueForKey = key
        publish(connection.status.value, connection.entities.value)
        // A book's or an episode's chapters may follow a moment after it starts (Music Assistant 2.11).
        val current = read?.current
        if (!again && current != null && current.kind.spoken && current.chapters.isEmpty()) {
            delay(CHAPTERS_AGAIN_MS)
            if (lastPlayerKey == key) refreshQueue(force = true)
        }
    }

    /** Whether Music Assistant's queue is worth reading again: it moved, it is old, or it is spoken word. */
    private fun windowStale(q: QueueInfo): Boolean =
        Triple(q.queueId, q.currentIndex, q.next?.key) != windowFor ||
            now() - windowReadAt > WINDOW_MAX_AGE_MS ||
            q.current?.kind?.spoken == true ||
            window?.current?.kind?.spoken == true

    /**
     * The whole queue around the current item, from Music Assistant itself (if set up) – only
     * if it is the same item Home Assistant reports (the queue may have moved in between).
     */
    private suspend fun readWindow(q: QueueInfo): QueueWindow? {
        val client = ma ?: return null
        val id = q.queueId ?: lastSnapshot?.activeQueue ?: return null
        val index = q.currentIndex ?: return null
        val (offset, limit) = MaQueue.range(index)
        return try {
            val w = MaQueue.window(client.items(id, offset, limit), offset, index, client.baseUrl)
            maLink = MaLink.CONNECTED
            windowReadAt = now()
            windowFor = Triple(q.queueId, q.currentIndex, q.next?.key)
            w.takeIf { a -> val b = q.current; a.current != null && b != null && sameTitle(a.current.title, b.title) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: MaAuthException) {
            maLink = MaLink.AUTH_FAILED
            null
        } catch (_: Exception) {
            maLink = MaLink.UNREACHABLE
            null
        }
    }

    /** All of the house's speakers play together (and there are at least three). */
    private fun isEverywhere(g: SpeakerGroup): Boolean {
        val all = connection.templateResult.value?.trim()?.toIntOrNull() ?: return false
        return g.name == null && all >= 3 && g.others.size + 1 >= all
    }

    private fun trackKey(t: TrackInfo) = norm(t.title) + "|" + norm(t.artists.joinToString())

    companion object {
        const val QUEUE_REFRESH_MS = 60_000L

        /** Music Assistant's queue is read again after this long even when nothing seems to have moved. */
        const val WINDOW_MAX_AGE_MS = 5 * 60_000L

        /** A book without chapters is asked once more after this long. */
        const val CHAPTERS_AGAIN_MS = 10_000L

        /** Music Assistant speakers (not groups) that are not unavailable. */
        const val SPEAKER_COUNT_TEMPLATE =
            "{{ states.media_player | selectattr('attributes.mass_player_type', 'eq', 'player') " +
                "| rejectattr('state', 'eq', 'unavailable') | list | count }}"

        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).filter { it.isLetterOrDigit() }

        fun sameTitle(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            return x == y || (x.isNotEmpty() && y.isNotEmpty() && (x.startsWith(y) || y.startsWith(x)))
        }
    }
}
