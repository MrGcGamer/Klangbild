package io.github.hazymorning.klangbild.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.text.TestTexts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The quick settings' own buttons: what each kind of entity does, how they are kept, and how
 * they stand in the row (after the built-in entries, only while their entity can be used).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QuickActionsTest {
    private val en = TestTexts.english()
    private val ungroup = QuickAction("Ungroup", "script.ungroup_speakers")
    private val movie = QuickAction("Movie light", "scene.movie")
    private val party = QuickAction("Party mode", "input_boolean.party")

    @Test
    fun `what a button does follows from its entity`() {
        assertThat(ActionKind.of("script.ungroup")).isEqualTo(ActionKind.RUN)
        assertThat(ActionKind.of("automation.night")).isEqualTo(ActionKind.RUN)
        assertThat(ActionKind.of("scene.movie")).isEqualTo(ActionKind.ACTIVATE)
        assertThat(ActionKind.of("button.restart")).isEqualTo(ActionKind.PRESS)
        assertThat(ActionKind.of("input_button.doorbell")).isEqualTo(ActionKind.PRESS)
        assertThat(ActionKind.of("switch.socket")).isEqualTo(ActionKind.TOGGLE)
        assertThat(ActionKind.of("input_boolean.party")).isEqualTo(ActionKind.TOGGLE)
        assertThat(ActionKind.of("light.floor")).isEqualTo(ActionKind.TOGGLE)
        // Nothing that locks, opens or plays.
        for (other in listOf("lock.front_door", "cover.blinds", "media_player.tv", "sensor.x", "nonsense")) {
            assertThat(ActionKind.of(other)).isNull()
        }
    }

    @Test
    fun `buttons are kept as names and entities, and what cannot be read is left out`() {
        val text = QuickActions.encode(listOf(ungroup, movie))
        assertThat(QuickActions.decode(text)).containsExactly(ungroup, movie).inOrder()
        assertThat(QuickActions.decode("""[{"name":"Front door","entity":"lock.front"},{"name":"Go","entity":"script.go"},7,"x"]"""))
            .containsExactly(QuickAction("Go", "script.go"))
        assertThat(QuickActions.decode("not json")).isEmpty()
        assertThat(QuickActions.decode(null)).isEmpty()
    }

    @Test
    fun `a name is plain and short, and without one the entity's own words stand`() {
        assertThat(QuickActions.valid("  Ungroup   all  ", "script.ungroup")).isEqualTo(QuickAction("Ungroup all", "script.ungroup"))
        assertThat(QuickActions.valid("", "script.ungroup_speakers")).isEqualTo(QuickAction("Ungroup speakers", "script.ungroup_speakers"))
        assertThat(QuickActions.valid("x".repeat(50), "scene.a")!!.name).hasLength(QuickActions.MAX_NAME)
        assertThat(QuickActions.valid("Open", "lock.front")).isNull()
        assertThat(QuickActions.valid("Odd", "script.Not Valid")).isNull()
    }

    @Test
    fun `the store keeps the buttons`(): Unit = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = SettingsStore(context)
        store.update({ it.copy(quickActions = listOf(ungroup, party)) }, Settings())
        assertThat(store.settings.first().quickActions).containsExactly(ungroup, party).inOrder()
        store.update({ it.copy(quickActions = emptyList()) }, store.settings.first())
        assertThat(store.settings.first().quickActions).isEmpty()
    }

    @Test
    fun `the buttons come after the built-in entries, only while their entity can be used`() {
        val s = Settings(quickActions = listOf(ungroup, movie, party))
        val ctx = QuickContext(music = true, actions = mapOf(ungroup.entity to ActionState.READY, party.entity to ActionState.ON))
        val rows = QuickMenu.rows(s, ctx, en)
        assertThat(rows.map { it.item }).containsExactly(QuickItem.PICTURE, QuickItem.ACTION, QuickItem.ACTION).inOrder()
        // The scene's entity is not there (unavailable): its button is left out, the others keep their places.
        assertThat(rows.drop(1).map { it.key.action }).containsExactly(0, 2).inOrder()
        assertThat(rows[1].label).isEqualTo("Ungroup")
        assertThat(rows[1].value).isEqualTo("Run")
        assertThat(rows[2].value).isEqualTo("On")
        // Every value a button can show, so its place never changes width.
        assertThat(rows[1].values).containsExactly("Run", "Done", "Failed").inOrder()
        assertThat(rows[2].values).containsExactly("On", "Off", "Failed").inOrder()
    }

    @Test
    fun `a button says what it does, then how it went`() {
        val t = TestTexts.german
        assertThat(QuickMenu.actionValue(ActionKind.RUN, ActionState.READY, en)).isEqualTo("Run")
        assertThat(QuickMenu.actionValue(ActionKind.ACTIVATE, ActionState.READY, en)).isEqualTo("Activate")
        assertThat(QuickMenu.actionValue(ActionKind.PRESS, ActionState.READY, en)).isEqualTo("Press")
        assertThat(QuickMenu.actionValue(ActionKind.RUN, ActionState.DONE, en)).isEqualTo("Done")
        assertThat(QuickMenu.actionValue(ActionKind.ACTIVATE, ActionState.FAILED, en)).isEqualTo("Failed")
        assertThat(QuickMenu.actionValue(ActionKind.RUN, ActionState.READY, t)).isEqualTo("Ausführen")
        assertThat(QuickMenu.actionValue(ActionKind.TOGGLE, ActionState.OFF, t)).isEqualTo("Aus")
    }
}
