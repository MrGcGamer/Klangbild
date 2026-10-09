package io.github.hazymorning.klangbild.dream

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.hazymorning.klangbild.app.graph
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.KlangbildRoot
import io.github.hazymorning.klangbild.ui.RootHooks

/**
 * Klangbild as the system screensaver: when the TV would otherwise show its ambient mode,
 * it shows what is playing – or the quiet clock. Any key ends the dream (non-interactive).
 * On Google TV this can only be selected via ADB, see the README.
 */
class KlangbildDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        val controller = graph.controller
        val spec = screenSpec()
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@KlangbildDream)
                setViewTreeSavedStateRegistryOwner(this@KlangbildDream)
                setContent {
                    val ui by controller.ui.collectAsStateWithLifecycle()
                    KlangbildRoot(ui, spec, RootHooks(positionNow = controller::positionNow, fieldTime = controller::fieldTime))
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        graph.controller.attach(screenSpec())
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        graph.controller.detach()
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun screenSpec(): ScreenSpec {
        val m = resources.displayMetrics
        return ScreenSpec(maxOf(m.widthPixels, m.heightPixels), minOf(m.widthPixels, m.heightPixels), m.density)
    }
}
