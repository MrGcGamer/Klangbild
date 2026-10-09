package io.github.hazymorning.klangbild.config

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.data.LinkState
import org.junit.Test

class SettingsMenuTest {
    private val configured = Settings(haUrl = "http://192.168.1.10:8123", haToken = "t", entityId = "media_player.wz")

    @Test
    fun `switches flip with any key`() {
        val s = Settings()
        assertThat(SettingsMenu.step(MenuEntry.CLOCK, s, +1)!!.showClock).isFalse()
        assertThat(SettingsMenu.step(MenuEntry.CLOCK, s, -1)!!.showClock).isFalse()
        assertThat(SettingsMenu.step(MenuEntry.ALBUM, s, +1, wrap = true)!!.showAlbum).isFalse()
        assertThat(SettingsMenu.step(MenuEntry.UP_DOWN, s, +1)!!.volumeKeys).isTrue()
        assertThat(SettingsMenu.step(MenuEntry.SLEEP_ACTION, s, +1)!!.sleepAction).isEqualTo(SleepAction.CLOSE)
    }

    @Test
    fun `scales stop at their ends with left and right and go round with OK`() {
        val normal = Settings()
        assertThat(SettingsMenu.step(MenuEntry.LIGHT, normal, +1)!!.light).isEqualTo(LightLevel.STRONG)
        assertThat(SettingsMenu.step(MenuEntry.LIGHT, normal, -1)!!.light).isEqualTo(LightLevel.SUBTLE)
        val strong = normal.copy(light = LightLevel.STRONG)
        assertThat(SettingsMenu.step(MenuEntry.LIGHT, strong, +1)!!.light).isEqualTo(LightLevel.STRONG)
        assertThat(SettingsMenu.step(MenuEntry.LIGHT, strong, +1, wrap = true)!!.light).isEqualTo(LightLevel.OFF)
        assertThat(SettingsMenu.step(MenuEntry.VOLUME, normal, -1)!!.volumeDisplay).isEqualTo(VolumeDisplay.OFF)
        assertThat(SettingsMenu.step(MenuEntry.VOLUME, normal, +1)!!.volumeDisplay).isEqualTo(VolumeDisplay.ALWAYS)
    }

    @Test
    fun `sleep time steps through the offered values, also from odd ones`() {
        assertThat(SettingsMenu.nextSleep(10, +1)).isEqualTo(15)
        assertThat(SettingsMenu.nextSleep(10, -1)).isEqualTo(5)
        assertThat(SettingsMenu.nextSleep(7, +1)).isEqualTo(10)
        assertThat(SettingsMenu.nextSleep(7, -1)).isEqualTo(5)
        assertThat(SettingsMenu.nextSleep(120, +1)).isEqualTo(120)
        assertThat(SettingsMenu.nextSleep(120, +1, wrap = true)).isEqualTo(5)
        assertThat(SettingsMenu.nextSleep(5, -1)).isEqualTo(5)
        assertThat(SettingsMenu.minutes(10)).isEqualTo("Zehn Minuten")
        assertThat(SettingsMenu.minutes(45)).isEqualTo("45 Minuten")
        assertThat(SettingsMenu.minutes(60)).isEqualTo("Eine Stunde")
        assertThat(SettingsMenu.minutes(120)).isEqualTo("Zwei Stunden")
    }

    @Test
    fun `every line has a value and an explanation, actions have no step`() {
        for (s in listOf(Settings(), configured, configured.copy(light = LightLevel.OFF, volumeKeys = true))) {
            for (ctx in listOf(MenuContext(), MenuContext(LinkState.CONNECTED), MenuContext(LinkState.AUTH_FAILED))) {
                for (row in SettingsMenu.rows(s, ctx)) {
                    assertThat(row.value).isNotEmpty()
                    assertThat(row.description).endsWith(".")
                    assertThat(SettingsMenu.step(row.entry, s, +1) == null).isEqualTo(SettingsMenu.isAction(row.entry))
                }
            }
        }
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, Settings(), MenuContext())).isEqualTo("Einrichten")
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, configured, MenuContext(LinkState.CONNECTED))).isEqualTo("Verbunden")
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, configured, MenuContext(LinkState.AUTH_FAILED))).isEqualTo("Token abgelehnt")
    }

    @Test
    fun `sections stay together in menu order`() {
        val sections = SettingsMenu.entries.map { it.section }
        assertThat(sections).isInOrder(compareBy<MenuSection> { it.ordinal })
    }
}
