package io.github.hazymorning.klangbild.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeakerLabelTest {
    @Test
    fun `a group is this speaker plus the others, like Sonos`() {
        val all = SpeakerGroup("Wohnzimmer", listOf("Küche", "Bad", "Büro", "Flur", "Terrasse"))
        assertThat(SpeakerLabel.group(all)).isEqualTo("Wohnzimmer + 5")
        // Just one more: both names.
        assertThat(SpeakerLabel.group(SpeakerGroup("Wohnzimmer", listOf("Küche")))).isEqualTo("Wohnzimmer + Küche")
        // Its name not known yet: counted.
        assertThat(SpeakerLabel.group(SpeakerGroup("Wohnzimmer", listOf(null)))).isEqualTo("Wohnzimmer + 1")
        assertThat(SpeakerLabel.group(SpeakerGroup(null, listOf("Küche", "Bad")))).isEqualTo("Drei Lautsprecher")
        // Alone: nothing to say – the screen hangs in that room.
        assertThat(SpeakerLabel.group(SpeakerGroup("Wohnzimmer", emptyList()))).isNull()
        assertThat(SpeakerLabel.group(null)).isNull()
        // Every speaker of the house: Sonos' "Überall".
        assertThat(SpeakerLabel.group(SpeakerGroup("Wohnzimmer", listOf("Küche", "Bad"), everywhere = true))).isEqualTo("Überall")
    }

    @Test
    fun `a group with a name of its own is called by it`() {
        assertThat(SpeakerLabel.group(SpeakerGroup("Wohnzimmer", emptyList(), name = "Alle Lautsprecher"))).isEqualTo("Alle Lautsprecher")
        assertThat(SpeakerLabel.group(SpeakerGroup(null, listOf("Küche", "Bad"), name = "Erdgeschoss"))).isEqualTo("Erdgeschoss")
        // The queue of a speaker playing alone carries its own name, in whatever spelling.
        assertThat(SpeakerLabel.isOtherName("Wohnzimmer", "Wohnzimmer Lautsprecher")).isFalse()
        assertThat(SpeakerLabel.isOtherName("Sonos Wohnzimmer", "Wohnzimmer")).isFalse()
        assertThat(SpeakerLabel.isOtherName("wohnzimmer", "Wohnzimmer (MA)")).isFalse()
        assertThat(SpeakerLabel.isOtherName("Erdgeschoss", "Wohnzimmer")).isTrue()
        assertThat(SpeakerLabel.isOtherName("Alle", null)).isTrue()
        assertThat(SpeakerLabel.isOtherName(" ", "Wohnzimmer")).isFalse()
    }

    @Test
    fun `room names lose the device words`() {
        assertThat(SpeakerLabel.roomName("Wohnzimmer Lautsprecher")).isEqualTo("Wohnzimmer")
        assertThat(SpeakerLabel.roomName("Sonos One Küche")).isEqualTo("Küche")
        assertThat(SpeakerLabel.roomName("Wohnzimmer (MA)")).isEqualTo("Wohnzimmer")
        assertThat(SpeakerLabel.roomName("Sonos One (Bad)")).isEqualTo("Bad")
        assertThat(SpeakerLabel.roomName("Wohnzimmerlautsprecher")).isEqualTo("Wohnzimmer")
        assertThat(SpeakerLabel.roomName("Bad-Speaker")).isEqualTo("Bad")
        assertThat(SpeakerLabel.roomName("Home Office")).isEqualTo("Home Office")
        assertThat(SpeakerLabel.roomName("Musikzimmer")).isEqualTo("Musikzimmer")
        // Nothing but device words: the full name stays.
        assertThat(SpeakerLabel.roomName("Sonos One")).isEqualTo("Sonos One")
        assertThat(SpeakerLabel.roomName("  ")).isNull()
    }

    @Test
    fun `volume in figures with a no-break space`() {
        assertThat(SpeakerLabel.volume(0.35f, muted = false)).isEqualTo("35 %")
        assertThat(SpeakerLabel.volume(1.2f, muted = false)).isEqualTo("100 %")
        assertThat(SpeakerLabel.volume(0.35f, muted = true)).isEqualTo("stumm")
        assertThat(SpeakerLabel.volume(null, muted = false)).isNull()
    }
}
