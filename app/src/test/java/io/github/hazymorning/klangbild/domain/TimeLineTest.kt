package io.github.hazymorning.klangbild.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeLineTest {
    private val next = TrackText("Spiegel im Spiegel", null, "Arvo Pärt", null, null)

    private fun line(pos: Long?, dur: Long?, next: TrackText? = this.next, status: PlayerStatus = PlayerStatus.PLAYING, source: String? = null, exact: Boolean = false) =
        TimeLine.compute(status, pos, dur, next, source, exact)

    @Test
    fun `speaks in minutes, numbers up to twelve in words`() {
        assertThat(line(60_000, 240_000)).isEqualTo(TimeLine.Line("In drei Minuten", "Spiegel im Spiegel · Arvo Pärt"))
        assertThat(line(0, 70_000)).isEqualTo(TimeLine.Line("In einer Minute", "Spiegel im Spiegel · Arvo Pärt"))
        assertThat(line(0, 14 * 60_000)).isEqualTo(TimeLine.Line("In 14 Minuten", "Spiegel im Spiegel · Arvo Pärt"))
        assertThat(line(0, 5 * 60_000, next = null)).isEqualTo(TimeLine.Line("Noch fünf Minuten"))
        assertThat(line(0, 70_000, next = null)).isEqualTo(TimeLine.Line("Noch eine Minute"))
    }

    @Test
    fun `last seconds announce what comes next`() {
        assertThat(line(200_000, 240_000)).isEqualTo(TimeLine.Line("Gleich", "Spiegel im Spiegel · Arvo Pärt"))
        assertThat(line(200_000, 240_000, next = null)).isEqualTo(TimeLine.Line("Letzte Takte"))
    }

    @Test
    fun `states and radio`() {
        assertThat(line(10_000, 240_000, status = PlayerStatus.PAUSED)).isEqualTo(TimeLine.Line("Pausiert"))
        assertThat(line(10_000, 240_000, status = PlayerStatus.BUFFERING)).isEqualTo(TimeLine.Line("Einen Moment"))
        assertThat(line(null, null, source = "Radio Morgenlicht")).isEqualTo(TimeLine.Line("Radio Morgenlicht"))
        assertThat(line(null, null, next = null)).isNull()
    }

    @Test
    fun `connection trouble is said calmly`() {
        assertThat(TimeLine.compute(PlayerStatus.PLAYING, 1_000, 200_000, next, null, showExact = false, linkDown = true))
            .isEqualTo(TimeLine.Line("Verbindung unterbrochen", "wird wiederhergestellt …"))
    }

    @Test
    fun `exact figures on request`() {
        assertThat(line(97_000, 251_000, exact = true)).isEqualTo(TimeLine.Line("1:37 / 4:11", figures = true))
        assertThat(line(3_725_000, null, exact = true)).isEqualTo(TimeLine.Line("1:02:05", figures = true))
    }
}
