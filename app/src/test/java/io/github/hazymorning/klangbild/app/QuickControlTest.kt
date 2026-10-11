package io.github.hazymorning.klangbild.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.config.QuickAction
import io.github.hazymorning.klangbild.config.QuickItem
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.text.AppLanguage
import io.github.hazymorning.klangbild.text.TestTexts
import io.github.hazymorning.klangbild.text.Texts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The quick settings' own buttons on the TV: OK does what a button says, once at a time, and the
 * row says how it went; a switch shows its new state at once; a button whose entity cannot be
 * used is left out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QuickControlTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)

    private val ungroup = QuickAction("Ungroup", "script.ungroup_speakers")
    private val party = QuickAction("Party", "input_boolean.party")
    private val gone = QuickAction("Gone", "scene.gone")

    /** Runs what it is asked; answers when told to. */
    private inner class FakeSource : NowPlayingSource {
        override val state: StateFlow<SourceState> = MutableStateFlow(SourceState(LinkState.CONNECTED))
        val ran = mutableListOf<String>()
        var answer = CompletableDeferred<Boolean>()
        override fun start() = Unit
        override fun stop() = Unit
        override suspend fun send(command: RemoteCommand) = Unit
        override suspend fun seek(positionMs: Long) = Unit
        override suspend fun run(action: QuickAction): Boolean {
            ran += action.entity
            return answer.await()
        }
    }

    private val source = FakeSource()
    private var ui = UiState(
        screen = Screen.Stage(TrackInfo("Song", listOf("A")), PlayerStatus.PLAYING, null),
        source = SourceState(LinkState.CONNECTED, actions = mapOf(ungroup.entity to "off", party.entity to "off", gone.entity to "unavailable")),
    )

    private val host = object : ControllerHost {
        override val lock: Any = this
        override val scope = test
        override val now: () -> Long = { test.testScheduler.currentTime }
        override val settings = Settings(quickActions = listOf(ungroup, gone, party))
        override val ui: UiState get() = this@QuickControlTest.ui
        override val source: NowPlayingSource = this@QuickControlTest.source
        override val texts: Texts = TestTexts.english()
        override var language: AppLanguage = AppLanguage.ENGLISH
        override fun recompute() = Unit
        override fun applySettings(updated: Settings) = Unit
        override fun send(command: RemoteCommand) = Unit
        override fun requestSetup(show: Boolean) = Unit
        override fun showVista() = Unit
        override fun hideVista() = false
    }

    private val quick = QuickControl(host)

    private fun rows() = quick.state(ui.screen, vista = false)!!.rows

    private fun value(entity: String) = rows().first { it.item == QuickItem.ACTION && host.settings.quickActions[it.key.action].entity == entity }.value

    /** Opens the quick settings and goes right to the button for [entity]. */
    private fun choose(entity: String) {
        if (!quick.isOpen) quick.open(menuOpen = false)
        repeat(rows().size) {
            val s = quick.state(ui.screen, vista = false)!!
            val row = s.rows[s.cursor]
            if (row.item == QuickItem.ACTION && host.settings.quickActions[row.key.action].entity == entity) return
            quick.move(+1)
        }
        error("no button for $entity")
    }

    @Test
    fun `a button runs once at a time, says how it went, then what it does again`() {
        choose(ungroup.entity)
        assertThat(value(ungroup.entity)).isEqualTo("Run")
        quick.change()
        quick.change()
        test.runCurrent()
        assertThat(source.ran).containsExactly(ungroup.entity)
        source.answer.complete(true)
        test.runCurrent()
        assertThat(value(ungroup.entity)).isEqualTo("Done")
        test.advanceTimeBy(QuickControl.RESULT_MS + 100)
        assertThat(value(ungroup.entity)).isEqualTo("Run")
        // Pressed again: it runs again.
        source.answer = CompletableDeferred(false)
        quick.change()
        test.runCurrent()
        assertThat(source.ran).hasSize(2)
        assertThat(value(ungroup.entity)).isEqualTo("Failed")
    }

    @Test
    fun `a switch shows its new state at once and keeps it until Home Assistant says so`() {
        choose(party.entity)
        assertThat(value(party.entity)).isEqualTo("Off")
        quick.change()
        assertThat(value(party.entity)).isEqualTo("On")
        source.answer.complete(true)
        test.runCurrent()
        assertThat(value(party.entity)).isEqualTo("On")
        ui = ui.copy(source = ui.source.copy(actions = ui.source.actions + (party.entity to "on")))
        assertThat(value(party.entity)).isEqualTo("On")
        // Home Assistant could not switch it: it says so, then shows what Home Assistant reports.
        source.answer = CompletableDeferred(false)
        quick.change()
        assertThat(value(party.entity)).isEqualTo("Off")
        test.runCurrent()
        assertThat(value(party.entity)).isEqualTo("Failed")
        test.advanceTimeBy(QuickControl.RESULT_MS + 100)
        assertThat(value(party.entity)).isEqualTo("On")
    }

    @Test
    fun `a button whose entity cannot be used is left out, and comes when it can`() {
        quick.open(menuOpen = false)
        val shown = rows().filter { it.item == QuickItem.ACTION }.map { it.label }
        assertThat(shown).containsExactly("Ungroup", "Party").inOrder()
        ui = ui.copy(source = ui.source.copy(actions = ui.source.actions + (gone.entity to "2026-10-10T20:00:00+00:00")))
        assertThat(rows().filter { it.item == QuickItem.ACTION }.map { it.label }).containsExactly("Ungroup", "Gone", "Party").inOrder()
        ui = ui.copy(source = ui.source.copy(actions = emptyMap()))
        assertThat(rows().none { it.item == QuickItem.ACTION }).isTrue()
    }
}
