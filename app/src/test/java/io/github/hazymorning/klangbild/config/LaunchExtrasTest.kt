package io.github.hazymorning.klangbild.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LaunchExtrasTest {
    @Test
    fun `stored extras update settings, invalid values are ignored`() {
        val s = LaunchExtras.applyTo(
            Settings(),
            mapOf(
                "ha_url" to "192.168.1.5:8123",
                "token" to "  abc.def  ",
                "entity" to "media_player.wohnzimmer",
                "sleep_minutes" to "15",
                "sleep_action" to "beenden",
                "clock" to "false",
            ),
        )
        assertThat(s.haUrl).isEqualTo("http://192.168.1.5:8123")
        assertThat(s.haToken).isEqualTo("abc.def")
        assertThat(s.entityId).isEqualTo("media_player.wohnzimmer")
        assertThat(s.sleepAfterMinutes).isEqualTo(15)
        assertThat(s.sleepAction).isEqualTo(SleepAction.CLOSE)
        assertThat(s.showClock).isFalse()
        assertThat(s.isConfigured).isTrue()

        val unchanged = LaunchExtras.applyTo(s, mapOf("entity" to "Wohnzimmer!", "sleep_minutes" to 0, "ha_url" to ""))
        assertThat(unchanged).isEqualTo(s)
    }

    @Test
    fun `a token never follows an address it was not made for`() {
        val s = Settings(haUrl = "http://192.168.1.5:8123", haToken = "ha", maUrl = "http://192.168.1.5:8095", maToken = "ma")
        // Another app on the TV must not be able to send the stored tokens elsewhere.
        val moved = LaunchExtras.applyTo(s, mapOf("ha_url" to "http://192.168.1.66:8123", "ma_url" to "192.168.1.66:8095"))
        assertThat(moved.haUrl).isEqualTo("http://192.168.1.66:8123")
        assertThat(moved.haToken).isNull()
        assertThat(moved.maUrl).isEqualTo("http://192.168.1.66:8095")
        assertThat(moved.maToken).isNull()
        assertThat(LaunchExtras.applyTo(s, mapOf("ha_url" to "192.168.1.5:8123")).haToken).isEqualTo("ha")
        val both = LaunchExtras.applyTo(s, mapOf("ma_url" to "mass.local:8095", "ma_token" to "neu"))
        assertThat(both.maUrl).isEqualTo("http://mass.local:8095")
        assertThat(both.maToken).isEqualTo("neu")
        assertThat(both.hasMusicAssistant).isTrue()
    }

    @Test
    fun `display options from extras`() {
        val s = LaunchExtras.applyTo(
            Settings(),
            mapOf(
                "time_line" to "aus",
                "next" to false,
                "album" to "0",
                "group" to "nein",
                "volume" to "immer",
                "light" to "dezent",
                "volume_keys" to "ja",
                "day_night" to false,
                "remote" to "off",
            ),
        )
        assertThat(s.showTimeLine).isFalse()
        assertThat(s.showNext).isFalse()
        assertThat(s.showAlbum).isFalse()
        assertThat(s.showGroup).isFalse()
        assertThat(s.volumeDisplay).isEqualTo(VolumeDisplay.ALWAYS)
        assertThat(s.light).isEqualTo(LightLevel.SUBTLE)
        assertThat(s.volumeKeys).isTrue()
        assertThat(s.dayNight).isFalse()
        assertThat(s.remoteControl).isFalse()
        assertThat(LaunchExtras.applyTo(s, mapOf("light" to "kräftig", "volume" to "bei_änderung")).let { it.light to it.volumeDisplay })
            .isEqualTo(LightLevel.STRONG to VolumeDisplay.ON_CHANGE)
        assertThat(LaunchExtras.applyTo(s, mapOf("light" to "grell", "volume" to "laut"))).isEqualTo(s)
        assertThat(LaunchExtras.hasStoredExtras(mapOf("light" to "aus"))).isTrue()
    }

    @Test
    fun `a link may set display options, never the connection`() {
        val extras = LaunchExtras.fromLink(
            mapOf("light" to "dezent", "album" to "aus", "ha_url" to "http://evil", "token" to "x", "entity" to "media_player.x", "setup" to "true"),
        )
        assertThat(extras.keys).containsExactly("light", "album")
        val s = LaunchExtras.applyTo(Settings(), extras)
        assertThat(s.light).isEqualTo(LightLevel.SUBTLE)
        assertThat(s.showAlbum).isFalse()
        assertThat(s.haUrl).isNull()
        assertThat(LaunchExtras.session(extras)).isEqualTo(SessionOptions())
    }

    @Test
    fun `session options`() {
        assertThat(LaunchExtras.session(mapOf("setup" to true))).isEqualTo(SessionOptions(showSetup = true))
        assertThat(LaunchExtras.session(mapOf("debug" to 1, "setup" to "ja")))
            .isEqualTo(SessionOptions(showSetup = true, debugOverlay = true))
        assertThat(LaunchExtras.hasStoredExtras(mapOf("debug" to true))).isFalse()
        assertThat(LaunchExtras.hasStoredExtras(mapOf("entity" to "media_player.x"))).isTrue()
    }
}
