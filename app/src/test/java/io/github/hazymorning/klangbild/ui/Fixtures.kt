package io.github.hazymorning.klangbild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.hazymorning.klangbild.app.FlowData
import io.github.hazymorning.klangbild.app.FlowEntry
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.art.SyntheticCovers
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.ui.flow.CardMotion
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.ui.stage.StageTexts
import io.github.hazymorning.klangbild.ui.stage.StageTheatre
import io.github.hazymorning.klangbild.ui.stage.StatusLine

/** Test content: the test catalogue plus helpers to turn it into scenes and models. */
object Fixtures {
    val tracks = Catalogue.tracks

    /** A 4K panel with the UI at 4K (xxxhdpi), to check crispness and the honest cover scale. */
    val TV_4K = ScreenSpec(3840, 2160, 4f)

    /** Renders use Berlin time on 8 October 2026. */
    val zone: java.time.ZoneId = java.time.ZoneId.of("Europe/Berlin")

    fun at(hour: Int, minute: Int, day: Int = 8): Long =
        java.time.ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    fun track(cover: SyntheticCover): TrackInfo = tracks.first { it.artworkUrl == Catalogue.SCHEME + cover.name }

    val radio: TrackInfo get() = tracks.first { it.artworkUrl == null }

    /** What comes next in most shots. */
    val pärt = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))

    fun scene(
        track: TrackInfo,
        screen: ScreenSpec = ScreenSpec.TV_1080,
        lighting: Lighting = Lighting.DAY,
        appearance: Appearance = Appearance.DARK,
        view: StageView = StageView.SINGLE,
    ): ArtworkScene {
        val cover = track.artworkUrl?.removePrefix(Catalogue.SCHEME)?.let { SyntheticCover.valueOf(it) }
        val bmp = cover?.let { SyntheticCovers.render(it) }
        return SceneBuilder.build(track.key, bmp, track.artSeed, screen, lighting, appearance, view = view)
    }

    /** Cover Flow scenes for [tracks] (the middle one is the current track). */
    fun flowScenes(tracks: List<TrackInfo>, appearance: Appearance = Appearance.DARK, lighting: Lighting = Lighting.DAY, screen: ScreenSpec = ScreenSpec.TV_1080) =
        tracks.map { scene(it, screen, lighting, appearance, StageView.FLOW) }

    /** The cards of [scenes] at rest, the one at [current] in the middle. */
    fun restingCards(scenes: List<ArtworkScene>, current: Int) =
        scenes.mapIndexed { i, sc -> CardMotion(i.toLong(), sc.card!!, (i - current).toFloat(), (i - current).toFloat()) }

    fun model(
        track: TrackInfo,
        next: TrackInfo? = null,
        status: PlayerStatus = PlayerStatus.PLAYING,
        positionMs: Long? = track.durationMs?.let { it / 3 },
        screen: ScreenSpec = ScreenSpec.TV_1080,
        lighting: Lighting = Lighting.DAY,
        clock: String? = "21:47",
        group: String? = null,
        lead: String? = StageTexts.lead(status, muted = false, volumeFlash = false, volume = null, group = group, volumeAlways = false),
        appearance: Appearance = Appearance.DARK,
        scene: ArtworkScene = scene(track, screen, lighting, appearance),
        album: Boolean = true,
        progress: Boolean = true,
        linkDown: Boolean = false,
    ): StageModel {
        val text = TrackText.from(track)
        return StageModel(
            scene = scene,
            text = if (album) text else text.copy(album = null),
            status = status,
            durationMs = track.durationMs,
            positionMs = { positionMs },
            year = track.year.takeIf { album },
            progress = progress,
            line = StageTexts.line(linkDown, track),
            next = StageTexts.next(next),
            nextTitle = StageTexts.nextTitle(next),
            statusLine = StatusLine(lead, clock),
        )
    }

    /** Content rendered as a still: no loops of its own, so the capture can wait for idle. */
    @Composable
    fun Still(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalStill provides true, content = content)
    }

    fun stage(
        track: TrackInfo,
        screen: ScreenSpec = ScreenSpec.TV_1080,
        lighting: Lighting = Lighting.DAY,
        appearance: Appearance = Appearance.DARK,
    ) = StageData(track, scene(track, screen, lighting, appearance))

    /** A cover flow of [scenes] (ids as given), the card with id [middle] in the middle. */
    fun flow(scenes: Map<Long, ArtworkScene>, ids: List<Long>, middle: Long): FlowData {
        val cards = ids.map { id ->
            val sc = scenes.getValue(id)
            FlowEntry(id, tracks.first { it.key == sc.key }, sc)
        }
        return FlowData(cards, cards.indexOfFirst { it.id == middle })
    }

    /** The animated cover flow for one strip; what comes next is the card to the right. */
    @Composable
    fun FlowTheatreOf(flow: FlowData, status: PlayerStatus = PlayerStatus.PLAYING) {
        val lead = StageTexts.lead(status, false, volumeFlash = false, volume = null, group = "Wohnzimmer + 5", volumeAlways = false)
        val next = flow.cards.getOrNull(flow.current + 1)?.track
        io.github.hazymorning.klangbild.ui.flow.FlowTheatre(
            flow = flow,
            status = status,
            modelFor = { s -> model(s.track, next = next, status = status, scene = s.scene, lead = lead) },
        )
    }

    /** The animated stage (pause dimming, buffering breath) for one stage, frozen in time. */
    @Composable
    fun Theatre(
        stage: StageData,
        status: PlayerStatus,
        next: TrackInfo? = null,
        lightFactor: Float = 1f,
        group: String? = null,
        muted: Boolean = false,
        linkDown: Boolean = false,
    ) {
        val lead = StageTexts.lead(status, muted, volumeFlash = false, volume = null, group = group, volumeAlways = false)
        StageTheatre(
            stage = stage,
            status = status,
            modelFor = { s -> model(s.track, next = next, status = status, scene = s.scene, lead = lead) },
            lightFactor = { lightFactor },
            dimmed = linkDown,
        )
    }
}
