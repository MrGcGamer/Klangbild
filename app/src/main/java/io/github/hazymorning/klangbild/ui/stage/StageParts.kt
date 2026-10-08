package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/** True if the text uses only scripts where capitals, italics and letter-spacing make sense. */
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
