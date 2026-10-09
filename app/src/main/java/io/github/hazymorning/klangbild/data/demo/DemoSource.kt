package io.github.hazymorning.klangbild.data.demo

import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.domain.PlayerSnapshot
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.SpeakerGroup
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Demo mode: a little concert of invented releases with procedurally painted covers. It
 * deliberately walks through the hard cases – classical work titles, many performers,
 * Japanese and Arabic script, emoji, radio without a cover, a missing album, almost black
 * and almost white covers – so the app can be judged right after installation.
 *
 * Each title plays its last [SEGMENT_MS] so that "Danach" and the transition appear soon.
 */
class DemoSource(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : NowPlayingSource {

    private val _state = MutableStateFlow(SourceState(link = LinkState.DEMO))
    override val state: StateFlow<SourceState> = _state.asStateFlow()

    private var job: Job? = null
    private var index = 0
    private var segmentStart = 0L
    private var pausedAt: Long? = null
    private var pausedTotal = 0L
    private var volume = 0.35f

    override fun start() {
        segmentStart = now()
        publish()
        job = scope.launch {
            while (isActive) {
                delay(500)
                tick()
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }

    override suspend fun send(command: RemoteCommand) {
        when (command) {
            RemoteCommand.NEXT -> advance(+1)
            RemoteCommand.PREVIOUS -> advance(-1)
            RemoteCommand.VOLUME_UP, RemoteCommand.VOLUME_DOWN -> {
                val step = if (command == RemoteCommand.VOLUME_UP) VOLUME_STEP else -VOLUME_STEP
                volume = ((volume + step) * 100).roundToInt().coerceIn(0, 100) / 100f
                publish()
            }
            RemoteCommand.PLAY_PAUSE -> {
                val t = now()
                val p = pausedAt
                if (p == null) {
                    pausedAt = t
                } else {
                    pausedTotal += t - p
                    pausedAt = null
                }
                publish()
            }
        }
    }

    private fun tick() {
        val t = now()
        val elapsed = t - segmentStart - pausedTotal - (pausedAt?.let { t - it } ?: 0)
        if (pausedAt == null && elapsed >= SEGMENT_MS) advance(+1)
    }

    private fun advance(step: Int) {
        index = Math.floorMod(index + step, TRACKS.size)
        segmentStart = now()
        pausedAt = null
        pausedTotal = 0
        publish()
    }

    private fun publish() {
        val t = now()
        val track = TRACKS[index]
        val duration = track.durationMs
        val start = duration?.let { (it - SEGMENT_MS).coerceAtLeast(0) } ?: 0L
        val elapsed = t - segmentStart - pausedTotal - (pausedAt?.let { t - it } ?: 0)
        val others = groupFor(index)
        _state.value = SourceState(
            link = LinkState.DEMO,
            player = PlayerSnapshot(
                status = if (pausedAt != null) PlayerStatus.PAUSED else PlayerStatus.PLAYING,
                track = track,
                positionMs = if (duration != null) start + elapsed else null,
                positionAtEpochMs = t,
                friendlyName = SPEAKER,
                groupMembers = others.map { "media_player." + it.lowercase() },
                volume = volume,
            ),
            next = TRACKS[(index + 1) % TRACKS.size],
            receivedAtMs = t,
            group = others.takeIf { it.isNotEmpty() }?.let { SpeakerGroup(SPEAKER, it) },
        )
    }

    companion object {
        const val SEGMENT_MS = 42_000L
        const val SCHEME = "demo://cover/"
        const val SPEAKER = "Wohnzimmer"
        const val VOLUME_STEP = 0.05f

        /** The demo house: first everything plays together, then two rooms, then one. */
        fun groupFor(index: Int): List<String> = when (index) {
            in 0..5 -> listOf("Küche", "Bad", "Arbeitszimmer", "Schlafzimmer", "Terrasse")
            in 6..8 -> listOf("Küche")
            else -> emptyList()
        }

        private fun cover(c: SyntheticCover) = SCHEME + c.name

        val TRACKS: List<TrackInfo> = listOf(
            TrackInfo(
                "Neonlicht über der A40", listOf("Lumen Drive"), "Nachtfahrt", 2024, 251_000,
                artworkUrl = cover(SyntheticCover.NACHTFAHRT),
            ),
            TrackInfo(
                "Suite bergamasque, L. 75: III. Clair de lune", listOf("Clara Wendt"),
                "Debussy: Préludes & Suite bergamasque", 2019, 302_000,
                artworkUrl = cover(SyntheticCover.NEBELLAND),
            ),
            TrackInfo(
                "Kreis, Quadrat, Linie", listOf("Ensemble Dessau"), "Formen im Raum", 1998, 312_000,
                artworkUrl = cover(SyntheticCover.BAUHAUS),
            ),
            TrackInfo(
                "夜に駆ける", listOf("月島ミナ"), "夜光", 2021, 261_000,
                artworkUrl = cover(SyntheticCover.NOCTURNE),
            ),
            TrackInfo(
                "Klaviersonate Nr. 11 A-Dur, KV 331: III. Alla Turca", listOf("Wolfgang Amadeus Mozart"),
                source = "Radio Morgenlicht",
            ),
            TrackInfo(
                "أنت عمري", listOf("فرقة النيل"), "ليالي القاهرة", 2016, 289_000,
                artworkUrl = cover(SyntheticCover.TERRAKOTTA),
            ),
            TrackInfo(
                "Salzwasser (feat. Ida Holm) - Radio Edit", listOf("Nordlicht Kollektiv"), "Gezeiten", 2023, 214_000,
                artworkUrl = cover(SyntheticCover.SALZWASSER),
            ),
            TrackInfo(
                "Symphonie Nr. 9 d-Moll, op. 125: IV. Presto – Allegro assai – Allegro assai vivace (alla marcia) – Andante maestoso",
                listOf(
                    "Kammerphilharmonie an der Spree", "Johanna Reiter", "Mira Lindqvist",
                    "Tomasz Wierzbicki", "Anselm Krüger", "Chor der Stadtkirche",
                ),
                "Beethoven: Die Sinfonien", 2020, 1_451_000,
                artworkUrl = cover(SyntheticCover.KAMMERMUSIK),
            ),
            TrackInfo(
                "🌙 Mondlicht-Tape ✨", listOf("lofi stube"), "Nachtschicht", 2022, 167_000,
                artworkUrl = cover(SyntheticCover.PASTELL),
            ),
            TrackInfo(
                "Unter Moos", listOf("Waldhaus"), album = null, year = null, durationMs = 213_000,
                artworkUrl = cover(SyntheticCover.MOOS),
            ),
            TrackInfo(
                "Konfetti im Kopf", listOf("Die Bunten", "Kalle Sommer"), "Sommerfest", 2025, 185_000,
                artworkUrl = cover(SyntheticCover.KONFETTI),
            ),
            TrackInfo(
                "Weißes Rauschen", listOf("Hanna Stein"), "Leerraum", 2017, 400_000,
                artworkUrl = cover(SyntheticCover.LEERRAUM),
            ),
        )
    }
}
