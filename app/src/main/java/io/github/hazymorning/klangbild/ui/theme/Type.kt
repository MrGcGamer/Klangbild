package io.github.hazymorning.klangbild.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.hazymorning.klangbild.R

/**
 * Typefaces (all SIL Open Font License, licences in assets/licenses):
 *  - Newsreader (Production Type): a calm, literary serif with optical sizes. The display
 *    cut carries titles; the text cut keeps small italics sturdy at TV distance.
 *  - Jost (indestructible type*): a Futura-like geometric sans – the voice of good hi-fi
 *    equipment – for letter-spaced labels, figures and the clock.
 */
object Fonts {
    private fun newsreader(weight: Int, opsz: Float, italic: Boolean = false) = Font(
        resId = if (italic) R.font.newsreader_italic else R.font.newsreader,
        weight = FontWeight(weight),
        style = if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", opsz),
        ),
    )

    private fun jost(weight: Int) = Font(
        resId = R.font.jost,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** Titles: Newsreader at display optical size. */
    val Display = FontFamily(
        newsreader(300, 72f),
        newsreader(360, 72f),
        newsreader(420, 72f),
        newsreader(300, 72f, italic = true),
        newsreader(360, 72f, italic = true),
    )

    /** Smaller serif text (album, work line): sturdier text optical size. */
    val Text = FontFamily(
        newsreader(380, 20f),
        newsreader(450, 20f),
        newsreader(380, 20f, italic = true),
        newsreader(420, 20f, italic = true),
    )

    /** Labels, figures, clock. */
    val Sans = FontFamily(jost(300), jost(400), jost(500))
}
