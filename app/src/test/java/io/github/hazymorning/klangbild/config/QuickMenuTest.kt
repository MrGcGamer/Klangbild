package io.github.hazymorning.klangbild.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.R
import io.github.hazymorning.klangbild.text.TestTexts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QuickMenuTest {
    private val de = TestTexts.german
    private val lights = Settings(roomLights = RoomLights(left = "light.left", center = null, right = null))

    @Test
    fun `shuffle stands where the sleep timer stood, only where the player can shuffle`() {
        val rows = QuickMenu.rows(lights, QuickContext(music = true, favorite = FavoriteState.NONE, shuffle = false), de)
        assertThat(rows.map { it.item }).containsExactly(QuickItem.PICTURE, QuickItem.FAVORITE, QuickItem.LIGHTS, QuickItem.SHUFFLE).inOrder()
        assertThat(rows.last().value).isEqualTo("Aus")
        assertThat(QuickMenu.rows(lights, QuickContext(music = true, shuffle = true), de).last().value).isEqualTo("An")
        // A player that cannot shuffle, and no music: no entry.
        assertThat(QuickMenu.rows(lights, QuickContext(music = true, shuffle = null), de).map { it.item }).doesNotContain(QuickItem.SHUFFLE)
        assertThat(QuickMenu.rows(lights, QuickContext(music = false, shuffle = true), de).map { it.item }).doesNotContain(QuickItem.SHUFFLE)
    }

    @Test
    fun `the sleep timer lives in the settings, not in the quick settings`() {
        assertThat(QuickItem.entries.map { it.label }).doesNotContain(R.string.entry_sleep_timer)
        assertThat(SettingsMenu.entries).contains(MenuEntry.SLEEP_TIMER)
    }
}
