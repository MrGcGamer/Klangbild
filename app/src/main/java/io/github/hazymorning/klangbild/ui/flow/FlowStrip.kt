package io.github.hazymorning.klangbild.ui.flow

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.render.FlowCard
import io.github.hazymorning.klangbild.render.FlowGeometry
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.WallGlow
import io.github.hazymorning.klangbild.ui.stage.coverScale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A card during a change of the strip: it moves from position [from] to [to] (equal when it
 * stays) and fades from [fromAlpha] to [toAlpha] – a card that leaves – within the first
 * [fadeBy] of the change.
 */
@Immutable
class CardMotion(
    val id: Long,
    val card: FlowCard,
    val from: Float,
    val to: Float,
    val fromAlpha: Float = 1f,
    val toAlpha: Float = 1f,
    val fadeBy: Float = 1f,
)

/**
 * The cards of the cover flow, drawn in one perspective: every frame each card's position
 * is interpolated, its four corners computed ([FlowGeometry]) and its texture drawn as one
 * quad through a perspective matrix – farthest first (cards never cut through each other,
 * see [FlowGeometry]). Nothing is laid out or allocated per frame; colour filters are cached
 * by step.
 *
 * @param progress the change's eased progress (0..1).
 * @param dim brightness of the cards (pause).
 * @param sides presence of the side cards (night: 0).
 * @param alpha opacity of the whole strip (a new strip coming up over the old one).
 */
@Composable
fun FlowStrip(
    cards: List<CardMotion>,
    geometry: FlowGeometry,
    appearance: Appearance,
    lighting: Lighting,
    wall: Color,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 1f },
    dim: () -> Float = { 1f },
    sides: () -> Float = { 1f },
    alpha: () -> Float = { 1f },
) {
    val n = cards.size
    val pos = remember(cards) { FloatArray(n) }
    val alphas = remember(cards) { FloatArray(n) }
    val order = remember(cards) { IntArray(n) }
    val tools = remember { DrawTools() }
    val filters = remember(appearance, lighting, wall) { HashMap<Int, ColorMatrixColorFilter>() }
    val paper = wall.toArgb()
    Canvas(modifier.fillMaxSize()) {
        val t = progress().coerceIn(0f, 1f)
        val side = sides().coerceIn(0f, 1f)
        val d = dim().coerceIn(0f, 1f)
        val all = alpha().coerceIn(0f, 1f)
        for (i in 0 until n) {
            val c = cards[i]
            val p = c.from + (c.to - c.from) * t
            pos[i] = p
            val fade = c.fromAlpha + (c.toAlpha - c.fromAlpha) * (t / c.fadeBy).coerceAtMost(1f)
            val nearMiddle = 1f - (1f - side) * HaloLight.smoothstep(0.15f, 0.85f, abs(p))
            alphas[i] = fade * geometry.presence(p) * nearMiddle * all
            order[i] = i
        }
        // Farthest first (insertion sort: a handful of cards, no allocation).
        for (i in 1 until n) {
            val k = order[i]
            var j = i - 1
            while (j >= 0 && abs(pos[order[j]]) < abs(pos[k])) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = k
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (o in 0 until n) {
                val i = order[o]
                val a = alphas[i]
                if (a <= 0.004f) continue
                val card = cards[i].card
                card.shadow?.let { shadow(native, it, pos[i], a, geometry, density, tools) }
                geometry.corners(pos[i], tools.dp)
                for (k in 0 until 8) tools.dst[k] = tools.dp[k] * density
                val light = geometry.light(pos[i]) * d
                val step = (light * STEPS).roundToInt()
                val filter = filters.getOrPut(step) { ColorMatrixColorFilter(cardMatrix(lighting, step / STEPS.toFloat(), appearance, paper)) }
                // How much the card is squeezed sideways: far turned cards take the half-size texture.
                val width = hypot(tools.dst[2] - tools.dst[0], tools.dst[3] - tools.dst[1]) / card.sidePx
                val half = 1f - HaloLight.smoothstep(HALF_FROM, HALF_TO, width)
                if (half < 1f) quad(native, card, false, a, filter, tools, snap = pos[i] == 0f)
                if (half > 0f) quad(native, card, true, a * half, filter, tools, snap = false)
            }
        }
        // The cards dissolve into the wall before the screen's edges (on an OLED the band stays black).
        val band = WallGlow.EDGE_BLACK_DP * density
        val reach = band + EDGE_FADE_DP * density
        drawRect(wall, Offset.Zero, Size(band, size.height))
        drawRect(Brush.horizontalGradient(listOf(wall, wall.copy(alpha = 0f)), startX = band, endX = reach), Offset(band, 0f), Size(reach - band, size.height))
        val bottom = size.height - band
        drawRect(wall, Offset(0f, bottom), Size(size.width, band))
        drawRect(Brush.verticalGradient(listOf(wall.copy(alpha = 0f), wall), startY = bottom - EDGE_FADE_DP * density, endY = bottom), Offset(0f, bottom - EDGE_FADE_DP * density), Size(size.width, EDGE_FADE_DP * density))
    }
}

/** Reused per strip: corners, matrix and paint – nothing allocated per frame. */
private class DrawTools {
    val dp = FloatArray(8)
    val dst = FloatArray(8)
    val src = FloatArray(8)
    val matrix = Matrix()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
}

/**
 * On paper a card casts a soft shadow around itself and onto the floor at its foot – drawn
 * in the card's own plane, a little lower, from a small blurred bitmap.
 */
private fun shadow(canvas: android.graphics.Canvas, shadow: Bitmap, position: Float, alpha: Float, geometry: FlowGeometry, density: Float, tools: DrawTools) {
    val half = geometry.side / 2f
    val m = FlowCard.SHADOW_MARGIN * geometry.side
    val drop = SHADOW_DROP * geometry.side
    geometry.cornersOf(position, -half - m, -half - m + drop, half + m, half + m + drop, tools.dp)
    for (k in 0 until 8) tools.dst[k] = tools.dp[k] * density
    val n = shadow.width.toFloat()
    val s = tools.src
    s[0] = 0f; s[1] = 0f
    s[2] = n; s[3] = 0f
    s[4] = n; s[5] = n
    s[6] = 0f; s[7] = n
    tools.matrix.setPolyToPoly(s, 0, tools.dst, 0, 4)
    tools.paint.alpha = (alpha.coerceIn(0f, 1f) * SHADOW_ALPHA * 255f).roundToInt()
    tools.paint.colorFilter = null
    canvas.drawBitmap(shadow, tools.matrix, tools.paint)
}

/** Draws [card]'s texture (the half-size one if [half]) onto the quad in [tools].dst. */
private fun quad(
    canvas: android.graphics.Canvas,
    card: FlowCard,
    half: Boolean,
    alpha: Float,
    filter: ColorMatrixColorFilter,
    tools: DrawTools,
    snap: Boolean,
) {
    val bitmap = if (half) card.half else card.full
    val scale = if (half) 0.5f else 1f
    val pad = FlowCard.PAD * scale
    val w = card.sidePx * scale
    val h = card.heightPx * scale
    val s = tools.src
    s[0] = pad; s[1] = pad
    s[2] = pad + w; s[3] = pad
    s[4] = pad + w; s[5] = pad + h
    s[6] = pad; s[7] = pad + h
    val m = tools.matrix
    if (snap) {
        // Facing the room the card is drawn 1:1 on whole pixels, as crisp as the single view.
        m.setTranslate((tools.dst[0] - pad).roundToInt().toFloat(), (tools.dst[1] - pad).roundToInt().toFloat())
    } else {
        m.setPolyToPoly(s, 0, tools.dst, 0, 4)
    }
    tools.paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    tools.paint.colorFilter = filter
    canvas.drawBitmap(bitmap, m, tools.paint)
}

/**
 * The colour matrix for a card lit at [light] (1 in the middle, less to the sides and in a
 * pause): on black the card simply gets darker, on paper it fades towards the paper – the
 * way things further from the window look in daylight.
 */
private fun cardMatrix(lighting: Lighting, light: Float, appearance: Appearance, paper: Int): ColorMatrix {
    val (r, g, b) = coverScale(lighting, 1f, appearance)
    return if (appearance == Appearance.DARK) {
        ColorMatrix(
            floatArrayOf(
                r * light, 0f, 0f, 0f, 0f,
                0f, g * light, 0f, 0f, 0f,
                0f, 0f, b * light, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    } else {
        val f = (1f - light) * PAPER_FOG
        val pr = ((paper shr 16) and 0xFF) * f
        val pg = ((paper shr 8) and 0xFF) * f
        val pb = (paper and 0xFF) * f
        ColorMatrix(
            floatArrayOf(
                r * (1f - f), 0f, 0f, 0f, pr,
                0f, g * (1f - f), 0f, 0f, pg,
                0f, 0f, b * (1f - f), 0f, pb,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}

/** Steps of the cached light levels. */
private const val STEPS = 48

/** How much a card fades into the paper where the light falls off. */
private const val PAPER_FOG = 0.5f

/** The shadow on paper: how dark at most, and how far below the card it falls (share of the side). */
private const val SHADOW_ALPHA = 0.26f
private const val SHADOW_DROP = 0.035f

/** Squeezed below this share of its width a card starts to take the half-size texture, fully below [HALF_TO]. */
private const val HALF_FROM = 0.62f
private const val HALF_TO = 0.82f

/** The stretch over which the cards dissolve before the black band at the screen's edges (dp). */
private const val EDGE_FADE_DP = 70f
