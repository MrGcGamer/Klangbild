package io.github.hazymorning.klangbild.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.domain.ChapterPlace
import io.github.hazymorning.klangbild.domain.Chapters
import io.github.hazymorning.klangbild.domain.MediaKind
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.ui.flow.FlowStage
import io.github.hazymorning.klangbild.ui.stage.Stage
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.ui.stage.StageTexts
import io.github.hazymorning.klangbild.ui.stage.StatusLine
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Audiobooks and podcasts on the stage: the chapter in the album's place, the bar over the chapter, speed and sleep timer in words. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SpokenStageTest {
    private fun shot(name: String, model: StageModel) =
        captureRoboImage("build/screenshots/spoken/$name.png") { Fixtures.Still { Stage(model) } }

    private val book = TrackInfo(
        title = "Der Process",
        artists = listOf("Franz Kafka"),
        durationMs = 9_000_000L,
        artworkUrl = Catalogue.SCHEME + SyntheticCover.NEBELLAND.name,
        kind = MediaKind.AUDIOBOOK,
        chapters = Chapters.of(
            listOf(
                Chapters.Raw(1, "Verhaftung", 0.0, null),
                Chapters.Raw(2, "Gespräch mit Frau Grubach – dann Fräulein Bürstner", 1_830.0, null),
                Chapters.Raw(3, "003", 3_710.0, null),
                Chapters.Raw(4, "004", 5_400.0, null),
            ),
            9_000_000L,
        ),
    )

    private val episode = TrackInfo(
        title = "Folge 112: Wie Wolken entstehen und warum sie nicht herunterfallen",
        artists = listOf("Zeit für Wissen"),
        durationMs = 2_875_000L,
        artworkUrl = Catalogue.SCHEME + SyntheticCover.SALZWASSER.name,
        kind = MediaKind.PODCAST,
    )

    private fun spokenModel(
        track: TrackInfo,
        position: Long,
        lead: String?,
        appearance: Appearance = Appearance.DARK,
        view: StageView = StageView.SINGLE,
        next: TrackInfo? = null,
    ): StageModel {
        val chapter = ChapterPlace.at(track, position)
        return StageModel(
            scene = Fixtures.scene(track, appearance = appearance, view = view),
            text = TrackText.from(track),
            status = PlayerStatus.PLAYING,
            durationMs = chapter?.let { it.endMs - it.startMs } ?: track.durationMs,
            positionMs = { position },
            next = chapter?.next ?: StageTexts.next(next),
            nextTitle = if (chapter?.next != null) null else StageTexts.nextTitle(next),
            statusLine = StatusLine(lead, "21:47"),
            chapter = chapter?.label,
            rangeStartMs = chapter?.startMs ?: 0L,
        )
    }

    private fun lead(pace: String?) =
        StageTexts.lead(PlayerStatus.PLAYING, muted = false, volumeFlash = false, volume = null, group = "Wohnzimmer", volumeAlways = false, pace = pace)

    @Test fun audiobook() = shot("audiobook", spokenModel(book, 2_554_000L, lead(TimeLine.speed(1.25f))))

    @Test fun audiobookNumbered() = shot("audiobook_numbered", spokenModel(book, 4_100_000L, lead(StageTexts.sleep(Fixtures.at(23, 10), Fixtures.zone))))

    @Test fun audiobookPaper() = shot("audiobook_paper", spokenModel(book, 2_554_000L, lead(TimeLine.speed(1.5f)), appearance = Appearance.LIGHT))

    @Test fun podcast() = shot("podcast", spokenModel(episode, 1_163_000L, lead(StageTexts.sleep(Fixtures.at(23, 10), Fixtures.zone)), next = TrackInfo("Folge 113: Der Mond", listOf("Zeit für Wissen"))))

    @Test fun podcastFlow() = captureRoboImage("build/screenshots/spoken/podcast_flow.png") {
        val scenes = Fixtures.flowScenes(listOf(Fixtures.track(SyntheticCover.MOOS), episode, Fixtures.track(SyntheticCover.TERRAKOTTA)))
        Fixtures.Still { FlowStage(spokenModel(episode, 1_163_000L, lead(null), view = StageView.FLOW), Fixtures.restingCards(scenes, 1)) }
    }
}
