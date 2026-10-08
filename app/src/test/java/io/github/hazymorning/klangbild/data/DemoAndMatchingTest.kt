package io.github.hazymorning.klangbild.data

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.data.demo.DemoSource
import io.github.hazymorning.klangbild.data.ha.HaSource
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Progress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoAndMatchingTest {
    @Test
    fun `demo plays the last seconds of each title and moves on`() {
        val scope = TestScope()
        var now = 1_000_000L
        val demo = DemoSource(scope) { now }
        demo.start()
        scope.runCurrent()
        val first = demo.state.value
        assertThat(first.link).isEqualTo(LinkState.DEMO)
        val track = first.player!!.track!!
        assertThat(track).isEqualTo(DemoSource.TRACKS[0])
        assertThat(first.next).isEqualTo(DemoSource.TRACKS[1])
        // starts SEGMENT_MS before the end, so "Gleich" and the transition come soon
        assertThat(first.player!!.positionMs).isEqualTo(track.durationMs!! - DemoSource.SEGMENT_MS)

        now += 10_000
        val pos = Progress.positionAt(first.player!!, now, 0, first.receivedAtMs)
        assertThat(pos).isEqualTo(track.durationMs!! - DemoSource.SEGMENT_MS + 10_000)

        now += DemoSource.SEGMENT_MS
        scope.advanceTimeBy(DemoSource.SEGMENT_MS + 1_000)
        assertThat(demo.state.value.player!!.track).isEqualTo(DemoSource.TRACKS[1])
        demo.stop()
    }

    @Test
    fun `demo pause and skip`() = kotlinx.coroutines.test.runTest {
        var now = 0L
        val scope = TestScope(testScheduler)
        val demo = DemoSource(scope) { now }
        demo.start()
        demo.send(RemoteCommand.PLAY_PAUSE)
        assertThat(demo.state.value.player!!.status).isEqualTo(PlayerStatus.PAUSED)
        now += 120_000
        scope.advanceTimeBy(120_000)
        // paused: does not advance
        assertThat(demo.state.value.player!!.track).isEqualTo(DemoSource.TRACKS[0])
        demo.send(RemoteCommand.PLAY_PAUSE)
        assertThat(demo.state.value.player!!.status).isEqualTo(PlayerStatus.PLAYING)
        demo.send(RemoteCommand.PREVIOUS)
        assertThat(demo.state.value.player!!.track).isEqualTo(DemoSource.TRACKS.last())
        demo.stop()
    }

    @Test
    fun `demo catalogue covers the hard cases`() {
        val t = DemoSource.TRACKS
        assertThat(t.any { it.artworkUrl == null && it.source != null }).isTrue() // radio without cover
        assertThat(t.any { it.title.contains(": ") && it.artists.size > 4 }).isTrue() // classical, many artists
        assertThat(t.any { it.album == null }).isTrue()
        assertThat(t.any { it.title.any { c -> c.code in 0x3040..0x9FFF } }).isTrue() // Japanese
        assertThat(t.any { it.title.any { c -> c.code in 0x0600..0x06FF } }).isTrue() // Arabic
        assertThat(t.any { it.title.contains("🌙") }).isTrue()
        assertThat(t.map { it.key }.toSet()).hasSize(t.size)
    }

    @Test
    fun `player and queue titles are matched loosely`() {
        assertThat(HaSource.sameTitle("Bohemian Rhapsody (Remastered 2011)", "Bohemian Rhapsody (Remastered 2011)")).isTrue()
        assertThat(HaSource.sameTitle("Café del Mar", "Cafe del Mar")).isTrue()
        assertThat(HaSource.sameTitle("Heroes", "Heroes (Remastered 2017)")).isTrue()
        assertThat(HaSource.sameTitle("Heroes", "Changes")).isFalse()
    }
}
