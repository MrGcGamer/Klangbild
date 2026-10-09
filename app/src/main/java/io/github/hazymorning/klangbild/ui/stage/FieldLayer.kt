package io.github.hazymorning.klangbild.ui.stage

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.CoverField
import io.github.hazymorning.klangbild.render.FieldMotion
import io.github.hazymorning.klangbild.ui.LocalStageTick
import io.github.hazymorning.klangbild.ui.LocalStill
import io.github.hazymorning.klangbild.ui.TICK_MS
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** The colour field's clock as the stage reads it: its time ([now], seconds) and whether it runs. */
@Stable
class FieldTime(val now: () -> Double, val moving: Boolean) {
    companion object {
        val STILL = FieldTime({ 0.0 }, false)
    }
}

/** The field's clock for everything below (the stage provides it; stills and tests get [FieldTime.STILL]). */
val LocalFieldTime = staticCompositionLocalOf { FieldTime.STILL }

/**
 * The cover's colours as the wall ([CoverField], [FieldMotion]). The layers are drawn into a
 * layer a quarter of the screen's size and scaled up – they are nothing but soft shapes –, so
 * even while they move a frame costs a few small textured quads and one scaled copy. Over it,
 * at full resolution, a grain of a level or two (soft dark ramps would otherwise show bands)
 * and the veil: the wall towards the edges, the shade under the text, the artwork's shadow.
 * While the field moves it is redrawn about 30 times a second, nothing else.
 *
 * [alpha] fades it during a change, [level] dims it (pause, a lost link).
 */
@Composable
fun FieldLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    level: () -> Float = { 1f },
) {
    val field = scene.field ?: return
    val texture = remember(field) { field.texture?.asImageBitmap() } ?: return
    val veil = remember(field) { field.veil?.asImageBitmap() }
    val base = remember(field) { Color(field.base) }
    val dark = scene.appearance == Appearance.DARK
    val time = LocalFieldTime.current
    // The stage's one tick, shared with the progress bar; alone (in a test) a loop of its own.
    val shared = LocalStageTick.current
    val own = remember { mutableLongStateOf(0L) }
    if (time.moving && shared == null && !LocalStill.current) {
        LaunchedEffect(time) {
            while (true) {
                withFrameNanos { own.longValue = it }
                delay(TICK_MS)
            }
        }
    }
    val frame = shared?.frame ?: own
    val places = remember { FloatArray(FieldMotion.LAYERS * FieldMotion.STRIDE) }
    val grain = remember { ShaderBrush(ImageShader(Grain.image, TileMode.Repeated, TileMode.Repeated)) }
    val side = CoverField.SIZE.toFloat()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val lw = max(1, (w + DOWN - 1) / DOWN)
        val lh = max(1, (h + DOWN - 1) / DOWN)
        Canvas(
            Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(Constraints.fixed(lw, lh))
                    layout(lw, lh) { p.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = w / lw.toFloat()
                    scaleY = h / lh.toFloat()
                    transformOrigin = TransformOrigin(0f, 0f)
                    // Drawn small, then scaled: the layers never touch the full resolution.
                    compositingStrategy = CompositingStrategy.Offscreen
                    this.alpha = alpha().coerceIn(0f, 1f)
                    // A little more vivid once mixed: where opposite colours meet they stay colour, not grey.
                    colorFilter = VIVID
                },
        ) {
            if (time.moving) frame.value // a new frame while the field moves
            FieldMotion.layers(time.now(), places)
            drawRect(base)
            val diagonal = hypot(size.width, size.height)
            for (i in 0 until FieldMotion.LAYERS) {
                val o = i * FieldMotion.STRIDE
                val scale = places[o + 3] * diagonal / side
                withTransform({
                    translate(places[o] * size.width, places[o + 1] * size.height)
                    rotate(places[o + 2], pivot = Offset.Zero)
                    scale(scale, scale, pivot = Offset.Zero)
                    translate(-side / 2f, -side / 2f)
                }) {
                    drawImage(texture, alpha = places[o + 4], filterQuality = FilterQuality.Low)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val a = alpha().coerceIn(0f, 1f)
            if (a <= 0.002f) return@Canvas
            drawRect(grain, alpha = a, blendMode = BlendMode.Plus)
            veil?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = a, filterQuality = FilterQuality.Low) }
            // In the dark a pause (or a lower light setting) dims the colours; paper stays paper, like the plain wall.
            val dim = if (dark) 1f - level().coerceIn(0f, 1f) else 0f
            if (dim > 0.002f) drawRect(Color.Black, alpha = a * dim)
        }
    }
}

/** How much smaller than the screen the layers are drawn. */
private const val DOWN = 4

/** Saturation after the layers have mixed ([CoverField.SATURATION]). */
private val VIVID = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(CoverField.SATURATION) })

/** A tile of fine noise (0–2 levels): added to the field, it breaks up the bands of dark ramps. */
private object Grain {
    private const val SIDE = 64

    val image by lazy {
        val r = Random(23)
        val px = IntArray(SIDE * SIDE) {
            val v = r.nextInt(3)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(px, SIDE, SIDE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
