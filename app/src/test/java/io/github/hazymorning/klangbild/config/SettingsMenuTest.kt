package io.github.hazymorning.klangbild.config

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.ma.MaLink
import io.github.hazymorning.klangbild.domain.MediaKind
import io.github.hazymorning.klangbild.domain.SleepChoice
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
        val withMa = configured.copy(maUrl = "http://192.168.1.5:8095", maToken = "t")
        val withBars = configured.copy(bars = LightBars(left = "light.links", right = "light.rechts"))
        val contexts = listOf(
            MenuContext(),
            MenuContext(LinkState.CONNECTED, ma = MaLink.CONNECTED),
            MenuContext(LinkState.AUTH_FAILED, ma = MaLink.AUTH_FAILED),
            MenuContext(sleep = SleepChoice.MIN_30, sleepAt = 1_791_483_667_000L),
            MenuContext(sleep = SleepChoice.ITEM_END, kind = MediaKind.PODCAST),
        )
        for (s in listOf(Settings(), configured, configured.copy(light = LightLevel.OFF, volumeKeys = true), withMa, withBars)) {
            // The lines that act rather than set: the connections, the sleep timer – and the light bars until they are set up.
            val actions = setOfNotNull(
                MenuEntry.CONNECTION, MenuEntry.MUSIC_ASSISTANT, MenuEntry.SLEEP_TIMER, MenuEntry.BARS.takeIf { !s.bars.isSet },
            )
            for (ctx in contexts) {
                for (row in SettingsMenu.rows(s, ctx)) {
                    assertThat(row.value).isNotEmpty()
                    assertThat(row.description).endsWith(".")
                    assertThat(SettingsMenu.step(row.entry, s, +1) == null).isEqualTo(row.entry in actions)
                }
            }
        }
        assertThat(SettingsMenu.sleepValue(SleepChoice.ITEM_END, MediaKind.PODCAST)).isEqualTo("Ende der Folge")
        assertThat(SettingsMenu.sleepValue(SleepChoice.MIN_60, null)).isEqualTo("Eine Stunde")
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, Settings(), MenuContext())).isEqualTo("Einrichten")
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, configured, MenuContext(LinkState.CONNECTED))).isEqualTo("Verbunden")
        assertThat(SettingsMenu.value(MenuEntry.CONNECTION, configured, MenuContext(LinkState.AUTH_FAILED))).isEqualTo("Token abgelehnt")
        assertThat(SettingsMenu.value(MenuEntry.MUSIC_ASSISTANT, configured, MenuContext(ma = MaLink.CONNECTED))).isEqualTo("Nicht eingerichtet")
        assertThat(SettingsMenu.value(MenuEntry.MUSIC_ASSISTANT, withMa, MenuContext(ma = MaLink.UNREACHABLE))).isEqualTo("Nicht erreichbar")
    }

    @Test
    fun `the Fensterblick's delays step through the offered values, also from odd ones`() {
        val s = Settings()
        assertThat(SettingsMenu.value(MenuEntry.VISTA_PAUSE, s, MenuContext())).isEqualTo("Drei Minuten")
        assertThat(SettingsMenu.value(MenuEntry.VISTA_PLAYING, s, MenuContext())).isEqualTo("Aus")
        assertThat(SettingsMenu.step(MenuEntry.VISTA_PAUSE, s, +1)!!.vistaPauseSeconds).isEqualTo(300)
        assertThat(SettingsMenu.step(MenuEntry.VISTA_PAUSE, s.copy(vistaPauseSeconds = 0), -1)!!.vistaPauseSeconds).isEqualTo(0)
        assertThat(SettingsMenu.step(MenuEntry.VISTA_PAUSE, s.copy(vistaPauseSeconds = 600), +1, wrap = true)!!.vistaPauseSeconds).isEqualTo(0)
        // Set by a link to 100 s: the nearest offered value is the start.
        assertThat(SettingsMenu.step(MenuEntry.VISTA_PAUSE, s.copy(vistaPauseSeconds = 100), +1)!!.vistaPauseSeconds).isEqualTo(180)
        assertThat(SettingsMenu.step(MenuEntry.VISTA_PLAYING, s, +1)!!.vistaPlayingSeconds).isEqualTo(60)
        assertThat(SettingsMenu.delay(15)).isEqualTo("15 Sekunden")
        assertThat(SettingsMenu.delay(60)).isEqualTo("Eine Minute")
        assertThat(SettingsMenu.step(MenuEntry.MINI_PLAYER, s, +1)!!.miniPlayer).isEqualTo(MiniPlayer.RIGHT)
        assertThat(SettingsMenu.step(MenuEntry.MINI_PLAYER, s, -1)!!.miniPlayer).isEqualTo(MiniPlayer.OFF)
        assertThat(SettingsMenu.value(MenuEntry.MINI_PLAYER, s, MenuContext())).isEqualTo("Unten links")
    }

    @Test
    fun `sections stay together in menu order`() {
        val sections = SettingsMenu.entries.map { it.section }
        assertThat(sections).isInOrder(compareBy<MenuSection> { it.ordinal })
    }
}
