package io.github.hazymorning.klangbild.ui

import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.art.SyntheticCovers
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.data.demo.DemoSource
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.ui.stage.StageTheatre
import androidx.compose.runtime.Composable

/** Test content: the demo catalogue plus helpers to turn it into scenes and models. */
object Fixtures {
    val tracks = DemoSource.TRACKS

    /** Renders use Berlin time on 8 October 2026. */
    val zone: java.time.ZoneId = java.time.ZoneId.of("Europe/Berlin")

    fun at(hour: Int, minute: Int, day: Int = 8): Long =
        java.time.ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    fun track(cover: SyntheticCover): TrackInfo = tracks.first { it.artworkUrl == DemoSource.SCHEME + cover.name }

    val radio: TrackInfo get() = tracks.first { it.artworkUrl == null }

    fun scene(track: TrackInfo, screen: ScreenSpec = ScreenSpec.TV_1080, lighting: Lighting = Lighting.DAY): ArtworkScene {
        val cover = track.artworkUrl?.removePrefix(DemoSource.SCHEME)?.let { SyntheticCover.valueOf(it) }
        val bmp = cover?.let { SyntheticCovers.render(it) }
        return SceneBuilder.build(track.key, bmp, track.artSeed, screen, lighting, layoutSeed = track.album ?: track.source ?: track.title)
    }

    fun model(
        track: TrackInfo,
        next: TrackInfo? = null,
        status: PlayerStatus = PlayerStatus.PLAYING,
        positionMs: Long? = track.durationMs?.let { it / 3 },
        screen: ScreenSpec = ScreenSpec.TV_1080,
        lighting: Lighting = Lighting.DAY,
        clock: String? = "21:47",
        night: Boolean = false,
        exact: Boolean = false,
        scene: ArtworkScene = scene(track, screen, lighting),
        capExtra: String? = null,
        timeLine: TimeLine.Line? = TimeLine.compute(status, positionMs, track.durationMs, next?.let { TrackText.from(it) }, track.source, exact),
        album: Boolean = true,
    ): StageModel {
        return StageModel(
            scene = scene,
            text = TrackText.from(track).let { if (album) it else it.copy(album = null) },
            status = status,
            durationMs = track.durationMs,
            positionMs = { positionMs },
            timeLine = timeLine,
            clock = clock,
            night = night,
            capExtra = capExtra,
        )
    }

    fun stage(track: TrackInfo, screen: ScreenSpec = ScreenSpec.TV_1080, lighting: Lighting = Lighting.DAY) =
        StageData(track, scene(track, screen, lighting))

    /** The animated stage (pause dimming, buffering breath) for one stage, frozen in time. */
    @Composable
    fun Theatre(
        stage: StageData,
        status: PlayerStatus,
        next: TrackInfo? = null,
        night: Boolean = false,
        exact: Boolean = false,
        lightFactor: Float = 1f,
    ) {
        StageTheatre(
            stage = stage,
            status = status,
            modelFor = { s -> model(s.track, next = next, status = status, night = night, exact = exact, scene = s.scene) },
            progressFor = { s -> s.track.durationMs?.let { 1f / 3f } },
            wallClock = { 0L },
            lightFactor = { lightFactor },
        )
    }
}
