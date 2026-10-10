package io.github.hazymorning.klangbild

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
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
    private val peekUntil = mutableLongStateOf(0L)
    private var view: ComposeView? = null
    private var centerLongPressed = false
    private var sideLongPressed = false

    /** The key that woke the screen from the Fensterblick: its repeats and its release do nothing more. */
    private var wakingKey: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.applyLaunch(LaunchExtras.fromIntent(intent))
        // Predictive back (targetSdk 36) no longer delivers onBackPressed/KEYCODE_BACK.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
        // The composition lives while the activity is visible (see onStart/onStop).
        setContentView(ComposeView(this).also { view = it })
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
        val spec = screenSpec()
        view?.setContent {
            val ui by controller.ui.collectAsStateWithLifecycle()
            val hooks = remember { RootHooks(positionNow = controller::positionNow, peekUntil = peekUntil, fieldTime = controller::fieldTime, vistaClock = controller.vistaClock) }
            KlangbildRoot(ui, spec, hooks)
            if (ui.session.debugOverlay) {
                // Frames are only counted while the overlay shows them.
                DisposableEffect(Unit) {
                    FrameStats.attach(window)
                    onDispose { FrameStats.detach(window) }
                }
                DebugOverlay()
            }
        }
    }

    override fun onStop() {
        controller.detach()
        // Out of sight nothing is kept: the screens' scenes and bitmaps go with the composition,
        // and coming back fades in from black, built anew.
        view?.disposeComposition()
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

    private fun peek() {
        peekUntil.longValue = System.currentTimeMillis() + PEEK_MS
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (controller.previewShowing) return previewKey(keyCode, event) || super.onKeyDown(keyCode, event)
        if (wakingKey == keyCode) return true
        // Any key ends the Fensterblick and starts its delays again. The key is spent on that –
        // only the media keys still do what they say.
        if (event.repeatCount == 0 && keyCode in REMOTE_KEYS && controller.interaction() && keyCode !in MEDIA_KEYS) {
            wakingKey = keyCode
            return true
        }
        if (controller.menuOpen && menuKey(keyCode)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    centerLongPressed = false
                    event.startTracking()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // A held key repeats: it changes the track once, not twenty times.
                if (event.repeatCount == 0 && onStage()) {
                    if (controller.spokenKeys) {
                        // A book or an episode: a press jumps (on release), a held key goes by chapters.
                        sideLongPressed = false
                        event.startTracking()
                    } else {
                        controller.send(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) RemoteCommand.PREVIOUS else RemoteCommand.NEXT)
                        peek()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event.repeatCount == 0 && onStage()) {
                    controller.send(if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) RemoteCommand.PREVIOUS else RemoteCommand.NEXT)
                    peek()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.repeatCount == 0 && onStage()) {
                    controller.skip(if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -1 else +1)
                    peek()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.repeatCount == 0) {
                    controller.send(
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteCommand.PLAY
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteCommand.PAUSE
                            else -> RemoteCommand.PLAY_PAUSE
                        },
                    )
                    peek()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (controller.ui.value.screen !is Screen.Setup && controller.volumeKeys) {
                    controller.send(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) RemoteCommand.VOLUME_UP else RemoteCommand.VOLUME_DOWN)
                } else {
                    peek()
                    // Held down, the arrow brings the Fensterblick (with volume keys it keeps turning the volume).
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.repeatCount == 0) event.startTracking()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                controller.openMenu()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Keys while the settings menu is open; false lets the key through (media keys). */
    private fun menuKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> controller.menuMove(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> controller.menuMove(+1)
            KeyEvent.KEYCODE_DPAD_LEFT -> controller.menuChange(-1, wrap = false)
            KeyEvent.KEYCODE_DPAD_RIGHT -> controller.menuChange(+1, wrap = false)
            // OK acts on release, like everywhere else (a long press must not also change a line).
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Unit
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> controller.closeMenu()
            else -> return false
        }
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            centerLongPressed = true
            // In the preview a held OK lets a day pass in two minutes; elsewhere it opens the menu.
            if (controller.previewShowing) controller.previewTimelapse() else if (!controller.menuOpen) controller.openMenu()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !controller.volumeKeys && !controller.menuOpen && !controller.previewShowing) {
            controller.showVista()
            return true
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && !controller.menuOpen) {
            sideLongPressed = true
            controller.jump(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else +1)
            peek()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (wakingKey == keyCode) {
            wakingKey = null
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!centerLongPressed && !event.isCanceled) onCenterPressed()
            centerLongPressed = false
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // Only a press that started a jump (spoken word) acts on release.
            if (event.isTracking && !sideLongPressed && !event.isCanceled && onStage()) {
                controller.skip(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else +1)
                peek()
            }
            sideLongPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Keys while the Fensterblick's preview is open: left and right the hour, up and down the
     * month, OK the sky (held: a day in two minutes, like the play key), Menu back to the menu.
     * False lets the key through (volume, home).
     */
    private fun previewKey(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> controller.previewHour(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> controller.previewHour(+1)
            KeyEvent.KEYCODE_DPAD_UP -> controller.previewMonth(+1)
            KeyEvent.KEYCODE_DPAD_DOWN -> controller.previewMonth(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.repeatCount == 0) {
                // Acts on release; held, it starts the time-lapse instead.
                centerLongPressed = false
                event.startTracking()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                if (event.repeatCount == 0) controller.previewTimelapse()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> controller.closePreview(toMenu = true)
            in REMOTE_KEYS -> Unit
            else -> return false
        }
        return true
    }

    private fun onCenterPressed() {
        if (controller.previewShowing) {
            controller.previewSky()
            return
        }
        if (controller.menuOpen) {
            controller.menuChange(+1, wrap = true)
            return
        }
        when (val s = controller.ui.value.screen) {
            is Screen.Setup -> when (s.reason) {
                SetupReason.FIRST_RUN -> Unit
                SetupReason.REQUESTED -> controller.requestSetup(false)
                SetupReason.AUTH_FAILED -> Unit
            }
            is Screen.Stage -> {
                controller.send(RemoteCommand.PLAY_PAUSE)
                peek()
            }
            is Screen.Rest -> controller.send(RemoteCommand.PLAY_PAUSE)
        }
    }

    /**
     * Back closes the app – or just the menu or the setup code opened on top of the music, the
     * preview (back to the menu) or the Fensterblick.
     */
    private fun onBack() {
        val s = controller.ui.value.screen
        if (controller.previewShowing) {
            controller.closePreview(toMenu = true)
        } else if (controller.interaction()) {
            // The Fensterblick goes; the app stays.
        } else if (controller.menuOpen) {
            controller.closeMenu()
        } else if (s is Screen.Setup && s.reason == SetupReason.REQUESTED) {
            controller.requestSetup(false)
        } else {
            finish()
        }
    }

    private fun onStage() = controller.ui.value.screen is Screen.Stage

    companion object {
        const val PEEK_MS = 6_000L

        /** The keys that only end the Fensterblick when it shows. */
        private val REMOTE_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        )

        /** Keys that end the Fensterblick and still do their work: the music's own. */
        private val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        )
    }
}
