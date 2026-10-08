package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.RectDp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Everything a stage draws. Position is a provider so ticking it never recomposes. */
@Immutable
class StageModel(
    val scene: ArtworkScene,
    val text: TrackText,
    val durationMs: Long?,
    val positionMs: () -> Long,
    val clock: String?,
    val nextUp: TrackText?,
    val paused: Boolean = false,
) {
    fun progress(): Float {
        val d = durationMs ?: return 0f
        if (d <= 0) return 0f
        return positionMs().toFloat() / d
    }
}

fun Int.asColor() = Color(this)

/** The scene's precomputed bitmaps as Compose images (cached per scene). */
@Composable
fun rememberSceneImages(scene: ArtworkScene): Pair<ImageBitmap, ImageBitmap> =
    remember(scene) { scene.backdrop.asImageBitmap() to scene.cover.asImageBitmap() }

/**
 * Time-of-day lighting for the cover itself: dims (in perceptual terms) and warms it like
 * the rest of the scene. Applied as a colour matrix while drawing – no extra pass.
 */
fun lightingFilter(lighting: Lighting): ColorFilter? {
    if (lighting.brightness >= 0.999f && lighting.warmth <= 0.07f) return null
    val k = lighting.brightness.pow(1.36f) // Oklab L multiplier → approx. sRGB multiplier
    val w = lighting.warmth
    val r = k * (1f + 0.02f * w)
    val g = k * (1f - 0.05f * w)
    val b = k * (1f - 0.20f * w)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** Draws the backdrop stretched over the whole screen (it is soft light; bilinear is ideal). */
@Composable
fun Backdrop(image: ImageBitmap, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Draws the cover 1:1 at a whole-pixel position: the bitmap already has its exact display
 * size, so no filtering softens it.
 */
@Composable
fun CoverPlate(image: ImageBitmap, rect: RectDp, lighting: Lighting, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val filter = remember(lighting) { lightingFilter(lighting) }
    Canvas(modifier.fillMaxSize()) {
        val x = (rect.left * density).roundToInt()
        val y = (rect.top * density).roundToInt()
        drawImage(
            image,
            dstOffset = IntOffset(x, y),
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.Low,
            colorFilter = filter,
            alpha = alpha,
        )
    }
}
