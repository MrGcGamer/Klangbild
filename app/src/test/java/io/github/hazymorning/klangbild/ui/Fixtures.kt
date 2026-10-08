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

/** Test content: the demo catalogue plus helpers to turn it into scenes and models. */
object Fixtures {
    val tracks = DemoSource.TRACKS

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
    ): StageModel {
        val nextText = next?.let { TrackText.from(it) }
        return StageModel(
            scene = scene,
            text = TrackText.from(track),
            status = status,
            durationMs = track.durationMs,
            positionMs = { positionMs },
            timeLine = TimeLine.compute(status, positionMs, track.durationMs, nextText, track.source, exact),
            clock = clock,
            night = night,
        )
    }
}
