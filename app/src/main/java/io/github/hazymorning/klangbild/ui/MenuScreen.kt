package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hazymorning.klangbild.app.MenuState
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.MenuRow
import io.github.hazymorning.klangbild.config.MenuSection
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.HaloLayout
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.ImageOps
import io.github.hazymorning.klangbild.render.LinearImage
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.theme.Fonts
import io.github.hazymorning.klangbild.ui.theme.textType
import io.github.hazymorning.klangbild.ui.theme.titleType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.exp

/** Geometry of the menu (dp): the line under the cursor always sits at the screen's middle. */
object MenuGrid {
    /** Pitch of the lines. */
    const val ROW = 40f

    /** A section title with the space above it. */
    const val HEADER = 46f

    /** Lines fade with their distance from the cursor's line … */
    const val FADE_FROM = 56f

    /** … and are gone well before the black band along the screen's edges. */
    const val FADE_TO = 226f

    /**
     * The light behind the cursor's line: a soft, low band (Gaussian σ across and along the
     * line, dp) that lights this line only, and its brightest point (≈ L* 10).
     */
    const val GLOW_SIGMA_X = 120f
    const val GLOW_SIGMA_Y = 12f
    const val GLOW_PEAK = 0.011f

    /** Opacity of a line at [distance] dp from the cursor's line. */
    fun fade(distance: Float): Float = 1f - HaloLight.smoothstep(FADE_FROM, FADE_TO, abs(distance))
}

/**
 * The settings menu over whatever is on screen. It fades in over a dark wall; the stage
 * behind it keeps running and is there again the moment the menu closes.
 */
@Composable
fun MenuOverlay(menu: MenuState?, lighting: Lighting, modifier: Modifier = Modifier) {
    // The last menu stays while it fades out.
    val last = remember { arrayOfNulls<MenuState>(1) }
    if (menu != null) last[0] = menu
    AnimatedVisibility(
        visible = menu != null,
        modifier = modifier,
        enter = fadeIn(tween(450, easing = Motion.Dimmer)),
        exit = fadeOut(tween(600, easing = Motion.Dimmer)),
        label = "menu",
    ) {
        (menu ?: last[0])?.let { MenuScreen(it, lighting) }
    }
}

private sealed interface MenuLine {
    val top: Float

    data class Header(val section: MenuSection, override val top: Float) : MenuLine
    data class Item(val index: Int, override val top: Float) : MenuLine
}

private fun menuLines(rows: List<MenuRow>): List<MenuLine> {
    val out = ArrayList<MenuLine>()
    var y = 0f
    var section: MenuSection? = null
    rows.forEachIndexed { i, row ->
        if (row.entry.section != section) {
            section = row.entry.section
            out += MenuLine.Header(row.entry.section, y)
            y += MenuGrid.HEADER
        }
        out += MenuLine.Item(i, y)
        y += MenuGrid.ROW
    }
    return out
}

/** The menu's voices: near-neutral white on black, a little dimmer in the evening. */
private class MenuColors(
    val heading: Color,
    val description: Color,
    val hint: Color,
    val header: Color,
    val label: Color,
    val labelFocus: Color,
    val value: Color,
    val valueFocus: Color,
) {
    companion object {
        fun of(lighting: Lighting): MenuColors {
            fun c(l: Float) = Color(lighting.applyArgb(Lch(l, 0.004f, HaloLight.LAMP_HUE).toArgb()))
            return MenuColors(
                heading = c(0.90f),
                description = c(0.72f),
                hint = c(0.60f),
                header = c(0.60f),
                label = c(0.64f),
                labelFocus = c(0.93f),
                value = c(0.60f),
                valueFocus = c(0.82f),
            )
        }
    }
}

/**
 * The menu itself, on the stage's grid: the heading and the chosen line's explanation hang
 * where the print would be; the lines stand in the text column. The line under the cursor
 * stays in the middle of the screen, lit by a lamp; the list glides through that light and
 * fades with distance – no frame, no scroll bar, no check box. Values are words.
 */
@Composable
fun MenuScreen(menu: MenuState, lighting: Lighting, modifier: Modifier = Modifier) {
    // A menu asks for action: it takes only a little of the evening's dimming.
    val light = remember(lighting) { Lighting.DAY.lerp(lighting, 0.35f) }
    val colors = remember(light) { MenuColors.of(light) }
    val lines = remember(menu.rows.map { it.entry }) { menuLines(menu.rows) }
    val itemTops = remember(lines) { lines.filterIsInstance<MenuLine.Item>().associate { it.index to it.top } }
    val cursor = menu.cursor.coerceIn(0, menu.rows.size - 1)
    val row = menu.rows[cursor]

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current.density
        val screen = ScreenSpec((maxWidth.value * density).roundToInt(), (maxHeight.value * density).roundToInt(), density)
        val layout = HaloLayout(screen)
        val focusY = maxHeight.value / 2f
        // The list moves so that the chosen line comes to rest in the light.
        val target = focusY - (itemTops.getValue(cursor) + MenuGrid.ROW / 2f)
        val offset = remember { Animatable(target) }
        LaunchedEffect(target) { offset.animateTo(target, tween(340, easing = Motion.Dimmer)) }

        val glowCx = layout.columnLeft + layout.columnWidth / 2f
        val field = remember(screen, light) {
            val tint = HaloLight.lightTint(HaloLight.LAMP, MenuGrid.GLOW_PEAK, light)
            val px = glowField(glowCx, focusY, screen.widthDp, screen.heightDp, SceneBuilder.ditherFor(tint))
            Bitmap.createBitmap(px, GLOW_MAP_W, GLOW_MAP_H, Bitmap.Config.ARGB_8888).asImageBitmap() to tint
        }
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                field.first,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = ColorFilter.tint(Color(field.second), BlendMode.Modulate),
                filterQuality = FilterQuality.Low,
            )
        }

        // Left: the heading at the print's top edge, the explanation level with the chosen line.
        val headingStyle = titleType(36f, 420, colors.heading)
        BaselineAt(x = layout.margin, baseline = layout.artTop + 36f * Fonts.CAP_HEIGHT, width = layout.art) {
            BasicText("Einstellungen", style = headingStyle)
        }
        Crossfade(
            targetState = row.description,
            animationSpec = tween(320, easing = Motion.Dimmer),
            label = "menu-description",
        ) { text ->
            BaselineAt(x = layout.margin, baseline = focusY + LABEL_BASELINE_FROM_CENTRE, width = layout.art - 24f) {
                BasicText(
                    text,
                    style = textType(18f, 420, colors.description, lineHeight = 26f).merge(
                        TextStyle(lineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default)),
                    ),
                )
            }
        }
        BaselineAt(x = layout.margin, baseline = layout.artTop + layout.art, width = layout.art) {
            BasicText("OK ändert" + TimeLine.SEPARATOR + "Zurück schließt", style = textType(15f, 500, colors.hint, tracking = 0.01f))
        }

        // Right: the lines in the text column.
        val labelStyle = textType(21f, 480)
        val valueStyle = textType(17f, 440)
        val headerStyle = textType(15f, 600, colors.header, tracking = 0.01f)
        for (line in lines) {
            val height = if (line is MenuLine.Header) MenuGrid.HEADER else MenuGrid.ROW
            Box(
                Modifier
                    .offset(x = layout.columnLeft.dp)
                    .width(layout.columnWidth.dp)
                    .height(height.dp)
                    .graphicsLayer {
                        val y = line.top + offset.value
                        translationY = (y * this.density).roundToInt().toFloat()
                        alpha = MenuGrid.fade(y + height / 2f - focusY)
                    },
                contentAlignment = if (line is MenuLine.Header) Alignment.BottomStart else Alignment.CenterStart,
            ) {
                when (line) {
                    is MenuLine.Header -> BasicText(
                        line.section.title,
                        style = headerStyle,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    is MenuLine.Item -> MenuItem(menu.rows[line.index], line.index == cursor, labelStyle, valueStyle, colors)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(row: MenuRow, focused: Boolean, labelStyle: TextStyle, valueStyle: TextStyle, colors: MenuColors) {
    val labelColor by animateColorAsState(if (focused) colors.labelFocus else colors.label, tween(260, easing = Motion.Dimmer), label = "label")
    val valueColor by animateColorAsState(if (focused) colors.valueFocus else colors.value, tween(260, easing = Motion.Dimmer), label = "value")
    Row(Modifier.fillMaxWidth()) {
        BasicText(row.entry.label, Modifier.alignByBaseline(), style = labelStyle, color = { labelColor }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Crossfade(
            targetState = row.value,
            modifier = Modifier.alignByBaseline(),
            animationSpec = tween(260, easing = Motion.Dimmer),
            label = "menu-value",
        ) { value ->
            BasicText(value, style = valueStyle.copy(color = valueColor), maxLines = 1)
        }
    }
}

/** Places [content] so that its first baseline lies at [baseline] (dp from the top). */
@Composable
private fun BaselineAt(x: Float, baseline: Float, width: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val p = measurables.first().measure(Constraints(maxWidth = (width * density).roundToInt()))
        val b = p[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: 0
        layout(constraints.maxWidth, constraints.maxHeight) {
            p.place((x * density).roundToInt(), (baseline * density).roundToInt() - b)
        }
    }
}

/** From a line's centre to its label's baseline (21 sp in a 40 dp line). */
private const val LABEL_BASELINE_FROM_CENTRE = 7f

private const val GLOW_MAP_W = 480
private const val GLOW_MAP_H = 270

/**
 * The light behind the chosen line: a soft, low band around ([cx], [cy]) dp that lights
 * this line and not its neighbours – greyscale, dithered, tinted when drawn. It never moves;
 * the list moves through it.
 */
fun glowField(cx: Float, cy: Float, widthDp: Float, heightDp: Float, ditherLsb: Float): IntArray {
    val img = LinearImage(GLOW_MAP_W, GLOW_MAP_H)
    val sx = widthDp / GLOW_MAP_W
    val sy = heightDp / GLOW_MAP_H
    for (y in 0 until GLOW_MAP_H) for (x in 0 until GLOW_MAP_W) {
        val u = ((x + 0.5f) * sx - cx) / MenuGrid.GLOW_SIGMA_X
        val v = ((y + 0.5f) * sy - cy) / MenuGrid.GLOW_SIGMA_Y
        val value = exp(-0.5f * (u * u + v * v))
        val o = y * GLOW_MAP_W + x
        img.r[o] = value
        img.g[o] = value
        img.b[o] = value
    }
    return ImageOps.toArgbDithered(img, seed = 5, ditherLsb = ditherLsb, blackBelow = 0.0005f)
}
