package io.github.hazymorning.klangbild

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.hazymorning.klangbild.app.graph
import io.github.hazymorning.klangbild.config.LaunchExtras
import io.github.hazymorning.klangbild.config.SleepAction
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.DebugOverlay
import io.github.hazymorning.klangbild.ui.FrameStats
import io.github.hazymorning.klangbild.ui.KlangbildRoot
import io.github.hazymorning.klangbild.ui.RootHooks
import kotlinx.coroutines.launch

/**
 * The one and only activity. Started by Home Assistant via
 * `am start -n io.github.hazymorning.klangbild/.MainActivity [--es entity …]`; while it runs,
 * new extras arrive through [onNewIntent] without a restart.
 */
class MainActivity : ComponentActivity() {
    private val controller get() = graph.controller
    private val exactUntil = mutableLongStateOf(0L)
    private var centerLongPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.applyLaunch(LaunchExtras.fromIntent(intent))
        // Predictive back (targetSdk 36) no longer delivers onBackPressed/KEYCODE_BACK.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
        FrameStats.attach(window)
        val spec = screenSpec()
        setContent {
            val ui by controller.ui.collectAsStateWithLifecycle()
            KlangbildRoot(ui, spec, RootHooks(positionNow = controller::positionNow, exactUntil = exactUntil))
            if (ui.session.debugOverlay) DebugOverlay()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.ui.collect { ui ->
                    if (ui.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    if (ui.asleep && ui.sleepAction == SleepAction.CLOSE) finish()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.attach(screenSpec())
    }

    override fun onStop() {
        controller.detach()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.applyLaunch(LaunchExtras.fromIntent(intent))
    }

    private fun screenSpec(): ScreenSpec {
        val m = resources.displayMetrics
        val w = maxOf(m.widthPixels, m.heightPixels)
        val h = minOf(m.widthPixels, m.heightPixels)
        return ScreenSpec(w, h, m.density)
    }

    private fun flashExactTime() {
        exactUntil.longValue = System.currentTimeMillis() + EXACT_TIME_MS
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    centerLongPressed = false
                    event.startTracking()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (onStage()) {
                    controller.send(RemoteCommand.PREVIOUS)
                    flashExactTime()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (onStage()) {
                    controller.send(RemoteCommand.NEXT)
                    flashExactTime()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                controller.send(RemoteCommand.PLAY_PAUSE)
                flashExactTime()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                flashExactTime()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                controller.requestSetup(true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            centerLongPressed = true
            controller.requestSetup(true)
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!centerLongPressed && !event.isCanceled) onCenterPressed()
            centerLongPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun onCenterPressed() {
        when (val s = controller.ui.value.screen) {
            is Screen.Setup -> when (s.reason) {
                SetupReason.FIRST_RUN -> controller.startDemo()
                SetupReason.REQUESTED -> controller.requestSetup(false)
                SetupReason.AUTH_FAILED -> Unit
            }
            is Screen.Stage -> {
                controller.send(RemoteCommand.PLAY_PAUSE)
                flashExactTime()
            }
            is Screen.Rest -> controller.send(RemoteCommand.PLAY_PAUSE)
        }
    }

    /** Back closes the app – or just the settings when they were opened on top of the music. */
    private fun onBack() {
        val s = controller.ui.value.screen
        if (s is Screen.Setup && s.reason == SetupReason.REQUESTED) {
            controller.requestSetup(false)
        } else {
            finish()
        }
    }

    private fun onStage() = controller.ui.value.screen is Screen.Stage

    companion object {
        const val EXACT_TIME_MS = 6_000L
    }
}
