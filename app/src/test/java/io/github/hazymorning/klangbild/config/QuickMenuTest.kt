package io.github.hazymorning.klangbild.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuickMenuTest {
    private val bars = Settings(bars = LightBars(left = "light.links", center = null, right = null))

    @Test
    fun `shuffle stands where the sleep timer stood, only where the player can shuffle`() {
        val rows = QuickMenu.rows(bars, QuickContext(music = true, favorite = FavoriteState.NONE, shuffle = false))
        assertThat(rows.map { it.item }).containsExactly(QuickItem.PICTURE, QuickItem.FAVORITE, QuickItem.BARS, QuickItem.SHUFFLE).inOrder()
        assertThat(rows.last().value).isEqualTo("Aus")
        assertThat(QuickMenu.rows(bars, QuickContext(music = true, shuffle = true)).last().value).isEqualTo("An")
        // A player that cannot shuffle, and no music: no entry.
        assertThat(QuickMenu.rows(bars, QuickContext(music = true, shuffle = null)).map { it.item }).doesNotContain(QuickItem.SHUFFLE)
        assertThat(QuickMenu.rows(bars, QuickContext(music = false, shuffle = true)).map { it.item }).doesNotContain(QuickItem.SHUFFLE)
    }

    @Test
    fun `the sleep timer lives in the settings, not in the quick settings`() {
        assertThat(QuickItem.entries.map { it.label }).doesNotContain("Sleep-Timer")
        assertThat(SettingsMenu.entries).contains(MenuEntry.SLEEP_TIMER)
    }
}
