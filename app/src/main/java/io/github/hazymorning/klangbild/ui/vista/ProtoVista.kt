package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.vista.Inks
import io.github.hazymorning.klangbild.vista.Noise
import io.github.hazymorning.klangbild.vista.ProtoScene
import io.github.hazymorning.klangbild.ui.rememberOffMain
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The performance prototype (`--es fensterblick proto`): the real Fensterblick's layer count,
 * clouds, water and grain with plain shapes, drawn the way the real one will be – baked ink
 * layers coloured by a matrix each frame, moving sprites, cached groups. [timelapse] changes
 * the light every frame (the worst case); otherwise every two seconds.
 */
@Composable
fun ProtoVista(timelapse: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val scene = rememberOffMain(w, h) { if (w > 0 && h > 0) Built(ProtoScene.bake(w, h)) else null }
        if (scene != null) ProtoFrame(scene, timelapse)
    }
}

private class Built(val baked: ProtoScene.Baked) {
    fun img(l: ProtoScene.Layer): ImageBitmap = Bitmap.createBitmap(l.argb, l.w, l.h, Bitmap.Config.ARGB_8888).asImageBitmap()
    val land = baked.land.map { img(it) }
    val clouds = baked.clouds.map { img(it) }
    val ripples = baked.ripples.map { img(it) }
    val grain = ShaderBrush(ImageShader(img(baked.grain), TileMode.Repeated, TileMode.Repeated))
}

@Composable
private fun ProtoFrame(scene: Built, timelapse: Boolean) {
    val frame = remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(timelapse) {
        val start = System.nanoTime()
        var lastLight = 0L
        while (true) {
            withFrameNanos { now ->
                frame.longValue = now
                val t = (now - start) / 1e9
                if (timelapse || now - lastLight > 2_000_000_000L) {
                    lastLight = now
                    phase = ((t / 40.0) % 1.0).toFloat()
                }
            }
            delay(24)
        }
    }
    val b = scene.baked
    val horizon = b.horizonPx.toFloat()
    val inks = remember(phase) { landInks(phase) }
    val filters = remember(inks) { inks.map { ColorFilter.colorMatrix(ColorMatrix(it.matrix())) } }
    val reflection = remember(inks) { inks.map { ColorFilter.colorMatrix(ColorMatrix(darker(it).matrix())) } }
    val sky = remember(phase) { skyBrush(phase, horizon) }
    val waterSky = remember(phase) { skyBrush(phase, horizon, flipped = true, height = b.heightPx.toFloat()) }
    val cloudFilter = remember(phase) { ColorFilter.colorMatrix(ColorMatrix(cloudInks(phase).matrix())) }
    val rippleFilter = remember(phase) { ColorFilter.tint(skyColour(phase, 0.9f).copy(alpha = 0.5f), BlendMode.SrcIn) }

    Box(Modifier.fillMaxSize()) {
        // Sky: behind everything, changes with the light only.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(sky, size = Size(size.width, horizon))
            val sx = size.width * (0.2f + 0.6f * phase)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x55FFE0B0), Color.Transparent), Offset(sx, horizon * 0.5f), size.width * 0.25f),
                size.width * 0.25f, Offset(sx, horizon * 0.5f),
            )
        }
        // Clouds: move every frame.
        Canvas(Modifier.fillMaxSize()) {
            val t = frame.value / 1e9
            for (i in scene.clouds.indices) {
                val img = scene.clouds[i]
                val x = ((size.width * (i / 8f) - t * (6 + i) * 3).mod(size.width + img.width) - img.width).toFloat()
                val y = horizon * (0.08f + 0.07f * (i % 5))
                translate(x, y) { drawImage(img, colorFilter = cloudFilter) }
            }
        }
        // Land: five layers, cached together, re-drawn only when the light changes.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            for (i in scene.land.indices) {
                val l = b.land[i]
                drawImage(scene.land[i], topLeft = Offset(l.x.toFloat(), l.y.toFloat()), colorFilter = filters[i])
            }
        }
        // Water behind the reflections: mirrored sky.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(waterSky, topLeft = Offset(0f, horizon), size = Size(size.width, size.height - horizon))
        }
        // Mirrored clouds.
        Canvas(Modifier.fillMaxSize()) {
            val t = frame.value / 1e9
            clipRect(top = horizon) {
                for (i in scene.clouds.indices) {
                    val img = scene.clouds[i]
                    val x = ((size.width * (i / 8f) - t * (6 + i) * 3).mod(size.width + img.width) - img.width).toFloat()
                    val y = horizon * (0.08f + 0.07f * (i % 5))
                    withTransform({
                        translate(x, 2 * horizon - y)
                        scale(1f, -1f, pivot = Offset.Zero)
                    }) { drawImage(img, colorFilter = cloudFilter, alpha = 0.7f) }
                }
            }
        }
        // Mirrored land, cached.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            clipRect(top = horizon) {
                for (i in scene.land.indices) {
                    val l = b.land[i]
                    withTransform({
                        translate(l.x.toFloat(), 2 * horizon - l.y)
                        scale(1f, -1f, pivot = Offset.Zero)
                    }) { drawImage(scene.land[i], colorFilter = reflection[i]) }
                }
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)), startY = horizon, endY = size.height),
                    topLeft = Offset(0f, horizon),
                )
            }
        }
        // Ripples: three stroke masks drifting and cross-fading.
        Canvas(Modifier.fillMaxSize()) {
            val t = frame.value / 1e9
            for (i in scene.ripples.indices) {
                val a = (0.5f + 0.5f * Noise.wander(t, 7.0, i).toFloat()).coerceIn(0f, 1f)
                val dx = (Noise.wander(t, 23.0, 10 + i) * 30).toFloat()
                drawImage(scene.ripples[i], topLeft = Offset(dx, horizon), alpha = a, colorFilter = rippleFilter)
            }
        }
        // Paper grain over everything (straight onto what is below: a layer of its own would multiply nothing).
        Canvas(Modifier.fillMaxSize()) {
            drawRect(scene.grain, blendMode = BlendMode.Multiply)
        }
        BasicText(
            "Prototyp · ${b.widthPx}×${b.heightPx} · gebacken in ${b.bakeMs} ms" + if (timelapse) " · Zeitraffer" else "",
            style = TextStyle(color = Color(0xFFB0A090), fontSize = 12.sp),
        )
    }
}

private fun Double.mod(m: Int): Double = ((this % m) + m) % m

private fun lab(l: Float, a: Float, b: Float) = Lab(l, a, b).toArgb()

private fun mix(x: Int, y: Int, t: Float): Int {
    var out = 0xFF shl 24
    for (s in intArrayOf(16, 8, 0)) {
        val v = (x shr s and 0xFF) + ((y shr s and 0xFF) - (x shr s and 0xFF)) * t
        out = out or (v.roundToInt().coerceIn(0, 255) shl s)
    }
    return out
}

private fun landInks(phase: Float): List<Inks> {
    val dusk = (1f - kotlin.math.abs(phase * 2f - 1f))
    return (0 until 5).map { i ->
        val depth = 1f - i / 5f
        val base = mix(lab(0.30f - 0.04f * i, -0.02f, 0.0f), lab(0.20f, 0.02f, -0.04f), dusk)
        val east = mix(lab(0.55f - 0.05f * i, -0.01f, 0.04f), lab(0.45f, 0.08f, 0.06f), dusk)
        val west = mix(lab(0.42f - 0.04f * i, -0.02f, 0.01f), lab(0.30f, 0.03f, -0.02f), dusk)
        val haze = mix(lab(0.80f, -0.01f, -0.03f), lab(0.62f, 0.06f, 0.02f), dusk)
        Inks(mix(base, haze, depth * 0.5f), east, west, haze)
    }
}

private fun darker(i: Inks): Inks {
    fun d(c: Int) = mix(c, 0xFF102028.toInt(), 0.35f)
    return Inks(d(i.base), d(i.first), d(i.second), d(i.third))
}

private fun cloudInks(phase: Float): Inks {
    val dusk = (1f - kotlin.math.abs(phase * 2f - 1f))
    return Inks(
        mix(lab(0.78f, -0.01f, -0.02f), lab(0.55f, 0.06f, -0.03f), dusk),
        mix(lab(0.96f, 0.0f, 0.01f), lab(0.80f, 0.08f, 0.08f), dusk),
        mix(lab(0.88f, 0.0f, 0.0f), lab(0.70f, 0.07f, 0.05f), dusk),
        mix(lab(0.84f, -0.01f, -0.02f), lab(0.66f, 0.05f, 0.0f), dusk),
    )
}

private fun skyColour(phase: Float, height: Float): Color {
    val dusk = (1f - kotlin.math.abs(phase * 2f - 1f))
    return Color(mix(lab(0.55f + 0.3f * height, -0.03f, -0.09f + 0.06f * height), lab(0.45f + 0.3f * height, 0.04f + 0.06f * height, -0.06f + 0.14f * height), dusk))
}

private fun skyBrush(phase: Float, horizon: Float, flipped: Boolean = false, height: Float = horizon): Brush {
    val stops = (0..4).map { k -> skyColour(phase, k / 4f) }
    return if (!flipped) Brush.verticalGradient(stops, startY = 0f, endY = horizon) else Brush.verticalGradient(stops.reversed(), startY = horizon, endY = height)
}
