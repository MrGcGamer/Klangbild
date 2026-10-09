package io.github.hazymorning.klangbild.ui.stage

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TrackInfo
import org.junit.Test

class StageTextsTest {
    private fun lead(
        status: PlayerStatus = PlayerStatus.PLAYING,
        muted: Boolean = false,
        flash: Boolean = false,
        volume: String? = "35 %",
        group: String? = "Wohnzimmer",
        always: Boolean = false,
    ) = StageTexts.lead(status, muted, flash, volume, group, always)

    @Test
    fun `the place comes first, the state last`() {
        assertThat(lead(PlayerStatus.PAUSED)).isEqualTo("Wohnzimmer · Pausiert")
        assertThat(lead(muted = true)).isEqualTo("Wohnzimmer · Stumm")
        assertThat(lead(PlayerStatus.PAUSED, group = null)).isEqualTo("Pausiert")
    }

    @Test
    fun `a passing volume wins, and stays out while muted`() {
        assertThat(lead(flash = true)).isEqualTo("Lautstärke 35 %")
        assertThat(lead(flash = true, muted = true)).isEqualTo("Wohnzimmer · Stumm")
    }

    @Test
    fun `playing alone says nothing, the volume only if always shown`() {
        assertThat(lead(group = null)).isNull()
        assertThat(lead()).isEqualTo("Wohnzimmer")
        assertThat(lead(always = true)).isEqualTo("Wohnzimmer · 35 %")
    }

    @Test
    fun `what comes next, and its title alone`() {
        val next = TrackInfo("Spiegel im Spiegel", listOf("Arvo Pärt"))
        assertThat(StageTexts.next(next)).isEqualTo("Spiegel im Spiegel · Arvo Pärt")
        assertThat(StageTexts.nextTitle(next)).isEqualTo("Spiegel im Spiegel")
        assertThat(StageTexts.next(null)).isNull()
    }

    @Test
    fun `a radio shows its station, a lost link says so`() {
        val radio = TrackInfo("Morgenmusik", source = "Radio Morgenlicht")
        assertThat(StageTexts.line(false, radio)).isEqualTo("Radio Morgenlicht")
        assertThat(StageTexts.line(true, radio)).isEqualTo(StageTexts.LINK_DOWN)
        assertThat(StageTexts.line(false, radio.copy(durationMs = 1000))).isNull()
    }
}
