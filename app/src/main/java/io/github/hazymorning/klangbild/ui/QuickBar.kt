package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.app.QuickState
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.FavoriteState
import io.github.hazymorning.klangbild.config.QuickItem
import io.github.hazymorning.klangbild.config.QuickMenu
import io.github.hazymorning.klangbild.config.SettingsMenu
import io.github.hazymorning.klangbild.domain.MediaKind
import io.github.hazymorning.klangbild.domain.SleepChoice
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.ImageOps
import io.github.hazymorning.klangbild.render.LinearImage
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.ui.stage.BEYOND_EDGES_DP
import io.github.hazymorning.klangbild.ui.theme.textType
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** Geometry of the quick settings (dp, on the 960 × 540 grid). */
object QuickGrid {
    /** The row starts where the stage's artwork starts, and ends where its text column ends. */
    const val LEFT = 88f
    const val RIGHT = 872f

    /** The values' baseline – well inside the screen's safe area – and the labels' above it. */
    const val VALUE_BASELINE = 495f
    const val LABEL_BASELINE = 468f

    /** The veil that lets the row read over whatever shows: from clear here … */
    const val VEIL_TOP = 372f

    /** … to its full strength here, down to the edge – nothing behind the row shows through. */
    const val VEIL_FULL = 448f

    /** Room between two entries. */
    const val GAP = 64f

    /**
     * The light under the chosen entry, a Gaussian: across, σ is this share of half the entry's
     * width plus a little (dp) – it lights the entry, not its neighbours; up and down, σ in dp.
     */
    const val GLOW_SHARE = 0.6f
    const val GLOW_PAD = 8f
    const val GLOW_SIGMA_Y = 15f

    /** How far the row rises when it comes. */
    const val RISE = 22f

    const val LABEL_SP = 15f
    const val VALUE_SP = 20f
}

/**
 * The quick settings along the bottom edge: they rise a little and fade in when down is
 * pressed, and sink back when they close. Each entry is its name in the quiet ink with its value
 * under it; the chosen one is lit from below by the same soft band of light as the menu's
 * chosen line, its words at full strength – no frame, no chip, no icon. The entries keep their
 * places whatever their values say, so nothing jumps while a value changes.
 */
@Composable
fun QuickBarOverlay(quick: QuickState?, lighting: Lighting, appearance: Appearance, modifier: Modifier = Modifier) {
    // The last state stays while the row sinks away.
    val last = remember { arrayOfNulls<QuickState>(1) }
    if (quick != null) last[0] = quick
    val density = LocalDensity.current.density
    val shown = remember { Animatable(0f) }
    LaunchedEffect(quick != null) {
        shown.animateTo(if (quick != null) 1f else 0f, tween(if (quick != null) 360 else 280, easing = Motion.Dimmer))
    }
    if (quick == null && shown.value <= 0f) return
    val state = quick ?: last[0] ?: return
    // It rises as it fades in – faded draw by draw, not through a layer of the screen's size,
    // which would cut its ground off at the screen's edges while it moves.
    QuickBar(state, lighting, appearance, modifier.graphicsLayer {
        val v = shown.value
        alpha = v
        translationY = (1f - v) * QuickGrid.RISE * density
        compositingStrategy = CompositingStrategy.ModulateAlpha
    })
}

/** The quick settings' voices: like the menu's, near-neutral, on black or on paper. */
private class QuickColors(
    val veil: Color,
    val band: Color,
    val label: Color,
    val labelFocus: Color,
    val value: Color,
    val valueFocus: Color,
) {
    companion object {
        fun of(lighting: Lighting, appearance: Appearance): QuickColors {
            fun c(l: Float, chroma: Float = 0.004f) = Color(lighting.applyArgb(Lch(l, chroma, HaloLight.LAMP_HUE).toArgb()))
            return if (appearance == Appearance.DARK) {
                QuickColors(
                    veil = Color.Black,
                    band = Color(HaloLight.lightTint(HaloLight.LAMP, GLOW_PEAK, lighting)),
                    label = c(0.56f),
                    labelFocus = c(0.74f),
                    value = c(0.66f),
                    valueFocus = c(0.93f),
                )
            } else {
                QuickColors(
                    veil = c(CoverPalette.PAPER, 0.006f),
                    band = Color(0xFF0D0C0B),
                    label = c(0.52f),
                    labelFocus = c(0.42f),
                    value = c(0.45f),
                    valueFocus = c(0.24f),
                )
            }
        }
    }
}

/**
 * Every value an entry can show, so its place is as wide as the widest of them (the last entry
 * needs no place kept: nothing stands after it).
 */
private fun valuesOf(item: QuickItem): List<String> = when (item) {
    QuickItem.PICTURE -> QuickMenu.PICTURES.map(QuickMenu::pictureValue)
    QuickItem.FAVORITE -> FavoriteState.entries.map(QuickMenu::favoriteValue)
    QuickItem.BARS -> listOf("An", "Aus")
    QuickItem.SLEEP -> SleepChoice.entries.flatMap { c -> listOf(MediaKind.MUSIC, MediaKind.PODCAST, MediaKind.AUDIOBOOK).map { SettingsMenu.sleepValue(c, it) } }.distinct()
}

@Composable
fun QuickBar(quick: QuickState, lighting: Lighting, appearance: Appearance, modifier: Modifier = Modifier) {
    // Like the menu: asking for a choice, it takes only a little of the evening's dimming.
    val light = remember(lighting) { Lighting.DAY.lerp(lighting, 0.35f) }
    val colors = remember(light, appearance) { QuickColors.of(light, appearance) }
    val labelStyle = remember { textType(QuickGrid.LABEL_SP, 460) }
    val valueStyle = remember { textType(QuickGrid.VALUE_SP, 480) }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val items = quick.rows.map { it.item }
    val values = quick.rows.map { it.value }
    // Each entry's place (dp): as wide as its name or its widest value, so a value that changes
    // never moves the others – only the last one is as wide as what it says now.
    val density = LocalDensity.current.density
    val widths = remember(items, values.last(), density) {
        items.mapIndexed { i, item ->
            fun w(text: String, style: androidx.compose.ui.text.TextStyle) = measurer.measure(AnnotatedString(text), style, maxLines = 1).size.width
            val value = if (i == items.lastIndex) w(values[i], valueStyle) else valuesOf(item).maxOf { w(it, valueStyle) }
            max(w(item.label, labelStyle), value) / density
        }
    }
    val lefts = widths.runningFold(QuickGrid.LEFT) { x, w -> x + w + QuickGrid.GAP }.dropLast(1)
    val cursor = quick.cursor.coerceIn(0, items.size - 1)
    // The light glides to the chosen entry.
    val glowX by animateFloatAsState(lefts[cursor] + widths[cursor] / 2f, tween(280, easing = Motion.Dimmer), label = "quick-glow-x")
    val glowSigma by animateFloatAsState(widths[cursor] / 2f * QuickGrid.GLOW_SHARE + QuickGrid.GLOW_PAD, tween(280, easing = Motion.Dimmer), label = "quick-glow-w")
    val glow = rememberOffMain(colors.band) { glowBlob(SceneBuilder.ditherFor(colors.band.toArgb())) }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            // The veil reaches beyond the screen's sides and bottom (and below, where the row rises
            // from): seamless to the edges, whatever the drift against burn-in.
            val m = BEYOND_EDGES_DP * d
            drawRect(
                Brush.verticalGradient(
                    0f to colors.veil.copy(alpha = 0f),
                    ((QuickGrid.VEIL_FULL - QuickGrid.VEIL_TOP) / (540f - QuickGrid.VEIL_TOP)) to colors.veil,
                    1f to colors.veil,
                    startY = QuickGrid.VEIL_TOP * d,
                    endY = size.height,
                ),
                topLeft = Offset(-m, QuickGrid.VEIL_TOP * d),
                size = Size(size.width + 2 * m, size.height - QuickGrid.VEIL_TOP * d + m + QuickGrid.RISE * d),
            )
            val g = glow ?: return@Canvas
            // The band of light, centred on the value's middle line; it adds to the veil. The
            // blob holds ±3 σ.
            val cy = (QuickGrid.VALUE_BASELINE - QuickGrid.VALUE_SP * 0.35f) * d
            val hw = glowSigma * 3f * d
            val hh = QuickGrid.GLOW_SIGMA_Y * 3f * d
            drawImage(
                g,
                dstOffset = IntOffset((glowX * d - hw).roundToInt(), (cy - hh).roundToInt()),
                dstSize = IntSize((2 * hw).roundToInt(), (2 * hh).roundToInt()),
                colorFilter = ColorFilter.tint(colors.band, BlendMode.Modulate),
                filterQuality = FilterQuality.Low,
                blendMode = BlendMode.Plus,
            )
        }
        quick.rows.forEachIndexed { i, row ->
            val focused = i == cursor
            val labelColor by animateColorAsState(if (focused) colors.labelFocus else colors.label, tween(240, easing = Motion.Dimmer), label = "quick-label")
            val valueColor by animateColorAsState(if (focused) colors.valueFocus else colors.value, tween(240, easing = Motion.Dimmer), label = "quick-value")
            Entry(x = lefts[i], labelBaseline = QuickGrid.LABEL_BASELINE, valueBaseline = QuickGrid.VALUE_BASELINE, width = widths[i]) {
                BasicText(row.item.label, style = labelStyle, color = { labelColor }, maxLines = 1)
                Crossfade(targetState = row.value, animationSpec = tween(220, easing = Motion.Dimmer), label = "quick-value-text") { v ->
                    BasicText(v, style = valueStyle, color = { valueColor }, maxLines = 1)
                }
            }
        }
    }
}

/** An entry: its name with its baseline at [labelBaseline], its value with its baseline at [valueBaseline] (dp), from [x]. */
@Composable
private fun Entry(x: Float, labelBaseline: Float, valueBaseline: Float, width: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val c = Constraints(maxWidth = ((width + 8f) * density).roundToInt())
        val label = measurables[0].measure(c)
        val value = measurables[1].measure(c)
        fun androidx.compose.ui.layout.Placeable.first(): Int = this[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: 0
        layout(constraints.maxWidth, constraints.maxHeight) {
            val left = (x * density).roundToInt()
            label.place(left, (labelBaseline * density).roundToInt() - label.first())
            value.place(left, (valueBaseline * density).roundToInt() - value.first())
        }
    }
}

/** The band's brightest point (≈ L* 10 on black), like the menu's. */
private const val GLOW_PEAK = 0.011f

private const val BLOB_W = 96
private const val BLOB_H = 48

/** A soft oval of light (a Gaussian, ±3 σ across the bitmap) – greyscale, dithered, tinted when drawn. */
private fun glowBlob(ditherLsb: Float): androidx.compose.ui.graphics.ImageBitmap {
    val img = LinearImage(BLOB_W, BLOB_H)
    for (y in 0 until BLOB_H) for (x in 0 until BLOB_W) {
        val u = ((x + 0.5f) / BLOB_W * 2f - 1f) * 3f
        val v = ((y + 0.5f) / BLOB_H * 2f - 1f) * 3f
        val value = exp(-0.5f * (u * u + v * v))
        val o = y * BLOB_W + x
        img.r[o] = value
        img.g[o] = value
        img.b[o] = value
    }
    val px = ImageOps.toArgbDithered(img, seed = 7, ditherLsb = ditherLsb, blackBelow = 0.0005f)
    return Bitmap.createBitmap(px, BLOB_W, BLOB_H, Bitmap.Config.ARGB_8888).asImageBitmap()
}
