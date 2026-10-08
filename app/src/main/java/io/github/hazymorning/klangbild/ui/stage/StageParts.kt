package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** True if the text uses only scripts where caps and letter-spacing make sense. */
fun isSpacingFriendly(text: String): Boolean = text.none { ch ->
    val c = ch.code
    c in 0x0590..0x08FF || // Hebrew, Arabic, Syriac, Thaana …
        c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF || // presentation forms
        c in 0x0E00..0x0E7F || // Thai
        c in 0x0900..0x0DFF || // Indic scripts
        c in 0x3040..0x30FF || c in 0x3400..0x4DBF || c in 0x4E00..0x9FFF || c in 0xAC00..0xD7AF ||
        c in 0xF900..0xFAFF || c in 0xFF00..0xFFEF
}

/**
 * A label in letter-spaced capitals – only where the script allows it. Arabic must stay
 * connected and CJK has no case, so those keep their natural form.
 */
@Composable
fun SpacedLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    tracking: TextUnit = 0.16.em,
    maxLines: Int = 1,
) {
    val friendly = isSpacingFriendly(text)
    BasicText(
        text = if (friendly) text.uppercase() else text,
        style = style.merge(
            TextStyle(
                letterSpacing = if (friendly) tracking else 0.em,
                textDirection = TextDirection.Content,
            ),
        ),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Text that picks the largest of [sizes] at which it fits into [maxLines]; only the
 * smallest size may finally ellipsize. Line breaks are balanced (heading style), so a
 * two-line title does not end with a lonely word.
 */
@Composable
fun FittingText(
    text: String,
    style: TextStyle,
    sizes: List<TextUnit>,
    maxLines: Int,
    modifier: Modifier = Modifier,
    lineHeight: Float = 1.1f,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val chosen = remember(text, style, sizes, maxLines, maxWidth) {
            sizes.firstOrNull { size ->
                val r = measurer.measure(
                    text = text,
                    style = style.merge(TextStyle(fontSize = size, lineHeight = (size.value * lineHeight).sp)),
                    constraints = Constraints(maxWidth = maxWidth),
                    maxLines = maxLines + 1,
                )
                r.lineCount <= maxLines && !r.hasVisualOverflow
            } ?: sizes.last()
        }
        BasicText(
            text = text,
            style = style.merge(
                TextStyle(
                    fontSize = chosen,
                    lineHeight = (chosen.value * lineHeight).sp,
                    lineBreak = LineBreak.Heading,
                    textDirection = TextDirection.Content,
                    textAlign = style.textAlign.takeUnless { it == TextAlign.Unspecified } ?: TextAlign.Left,
                ),
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A hairline progress rule. [progress] is read in the draw phase only (no recomposition). */
@Composable
fun ProgressHairline(
    progress: () -> Float,
    track: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
) {
    Canvas(modifier) {
        val y = size.height / 2f
        val stroke = thickness.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) drawLine(fill, Offset(0f, y), Offset(size.width * p, y), stroke, StrokeCap.Round)
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
