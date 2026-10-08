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
    fun `session options`() {
        assertThat(LaunchExtras.session(mapOf("demo" to true))).isEqualTo(SessionOptions(demo = true))
        assertThat(LaunchExtras.session(mapOf("demo" to "ja", "debug" to 1, "setup" to "true")))
            .isEqualTo(SessionOptions(demo = true, showSetup = true, debugOverlay = true))
        assertThat(LaunchExtras.hasStoredExtras(mapOf("demo" to true))).isFalse()
        assertThat(LaunchExtras.hasStoredExtras(mapOf("entity" to "media_player.x"))).isTrue()
    }
}
