package io.github.hazymorning.klangbild.ui

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Frame timing for checking smoothness on the real device (`--ez debug true`). Uses the
 * platform's FrameMetrics: per second, how many frames were drawn, how long the slowest
 * took and how many missed a 60 Hz deadline. Nothing is drawn when nothing changes – a
 * quiet screen shows "0 Bilder", which is the point.
 */
object FrameStats {
    data class Second(val frames: Int, val worstMs: Float, val janky: Int)

    private val _stats = MutableStateFlow(Second(0, 0f, 0))
    val stats: StateFlow<Second> = _stats.asStateFlow()

    private var frames = 0
    private var worst = 0f
    private var janky = 0
    private var windowStart = 0L
    private var attached: Window? = null

    fun attach(window: Window) {
        if (attached === window) return
        attached = window
        val thread = HandlerThread("frame-stats").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000f
            val now = System.nanoTime()
            if (windowStart == 0L) windowStart = now
            frames++
            if (ms > worst) worst = ms
            if (ms > 16.7f) janky++
            if (now - windowStart >= 1_000_000_000L) {
                _stats.value = Second(frames, worst, janky)
                frames = 0
                worst = 0f
                janky = 0
                windowStart = now
            }
        }, Handler(thread.looper))
    }
}

@Composable
fun DebugOverlay() {
    val s by FrameStats.stats.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
        BasicText(
            "${s.frames} Bilder/s · langsamstes ${"%.1f".format(s.worstMs)} ms · ${s.janky} zu spät",
            style = TextStyle(color = Color(0xFFB0A090), fontSize = 12.sp),
            modifier = Modifier.background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
