package io.github.hazymorning.klangbild.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverStripTest {
    private fun song(title: String, album: String) = TrackInfo(title, listOf("A"), album = album)

    private val a1 = song("a1", "Alpha")
    private val a2 = song("a2", "Alpha")
    private val b1 = song("b1", "Beta")
    private val c1 = song("c1", "Gamma")
    private val d1 = song("d1", "Delta")

    private fun Strip.albums() = cards.map { it.track.album }

    @Test
    fun `the songs of one album are one card and keep it`() {
        val strip = CoverStrip()
        val first = strip.update(a1, coming = listOf(a2))
        assertThat(first.albums()).containsExactly("Alpha")
        val second = strip.update(a2, coming = listOf(b1))
        assertThat(second.middle.id).isEqualTo(first.middle.id)
        assertThat(second.middle.track).isEqualTo(a2)
        assertThat(second.albums()).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun `the next picture arriving is the card that stood to the right`() {
        val strip = CoverStrip()
        val before = strip.update(a1, coming = listOf(b1))
        val nextId = before.cards[before.current + 1].id
        val after = strip.update(b1, coming = listOf(c1))
        assertThat(after.middle.id).isEqualTo(nextId)
        // what played stands to the left now, with its old id
        assertThat(after.cards[after.current - 1].id).isEqualTo(before.middle.id)
        assertThat(after.albums()).containsExactly("Alpha", "Beta", "Gamma").inOrder()
    }

    @Test
    fun `a step back brings back the card from the left`() {
        val strip = CoverStrip()
        strip.update(a1, coming = listOf(b1))
        val forward = strip.update(b1, coming = listOf(c1))
        val back = strip.update(a1, coming = listOf(b1))
        assertThat(back.middle.id).isEqualTo(forward.cards[forward.current - 1].id)
        // the card that was in the middle steps to the right again, still the same card
        assertThat(back.cards[back.current + 1].id).isEqualTo(forward.middle.id)
    }

    @Test
    fun `a jump brings a new card and keeps what played`() {
        val strip = CoverStrip()
        val first = strip.update(a1, coming = listOf(b1))
        val jump = strip.update(d1)
        assertThat(jump.middle.id).isNotEqualTo(first.cards.last().id)
        assertThat(jump.albums()).containsExactly("Alpha", "Delta").inOrder()
    }

    @Test
    fun `at most two cards on each side, the queue's played part replaces the memory`() {
        val strip = CoverStrip()
        for (t in listOf(a1, b1, c1, d1)) strip.update(t)
        val s = strip.update(song("e1", "Epsilon"), coming = listOf(a1, b1, c1))
        assertThat(s.current).isEqualTo(2)
        assertThat(s.cards).hasSize(5)
        val fromQueue = strip.update(song("e1", "Epsilon"), before = listOf(c1, b1))
        assertThat(fromQueue.albums().take(fromQueue.current)).containsExactly("Gamma", "Beta").inOrder()
    }

    @Test
    fun `while the queue is not known yet, the cards behind the next one stay – after a jump none do`() {
        val strip = CoverStrip()
        val first = strip.update(a1, coming = listOf(b1, c1))
        val forward = strip.update(b1)
        assertThat(forward.albums()).containsExactly("Alpha", "Beta", "Gamma").inOrder()
        assertThat(forward.cards.last().id).isEqualTo(first.cards.last().id)
        val back = strip.update(a1)
        assertThat(back.albums()).containsExactly("Alpha", "Beta", "Gamma").inOrder()
        val jump = strip.update(d1)
        assertThat(jump.albums()).containsExactly("Alpha", "Delta").inOrder()
    }

    @Test
    fun `a radio station is one card while its songs change`() {
        val strip = CoverStrip()
        val s1 = strip.update(TrackInfo("Song 1", listOf("X"), artworkUrl = "http://ha/logo.png", source = "Radio"))
        val s2 = strip.update(TrackInfo("Song 2", listOf("Y"), artworkUrl = "http://ha/logo.png", source = "Radio"))
        assertThat(s2.middle.id).isEqualTo(s1.middle.id)
    }
}
