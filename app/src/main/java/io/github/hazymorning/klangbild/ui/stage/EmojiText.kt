package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import io.github.hazymorning.klangbild.domain.Emoji

/**
 * Colour emoji at display size shout over the typography. Emoji at the edges of a title are
 * already gone (see [io.github.hazymorning.klangbild.domain.TrackText]); the ones inside a
 * title are set smaller and dimmed, and glued to their neighbours with no-break spaces, so
 * an emoji never ends up alone on its own line.
 */
object EmojiText {
    private const val NBSP = ' '

    fun isEmoji(cp: Int): Boolean = Emoji.isEmoji(cp)

    fun containsEmoji(text: String): Boolean = Emoji.containsEmoji(text)

    /** Splits [text] into runs of (isEmoji, run). */
    private fun runs(text: String): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        val sb = StringBuilder()
        var current: Boolean? = null
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val e = isEmoji(cp)
            if (current != null && e != current) {
                out += current to sb.toString()
                sb.setLength(0)
            }
            current = e
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        if (current != null && sb.isNotEmpty()) out += current to sb.toString()
        return out
    }

    /**
     * @param dimTo if set, emoji are drawn in this colour's alpha (colour glyphs keep their
     *              colours but become translucent on the dark wall, i.e. dimmer).
     */
    fun annotate(text: String, scale: Float = 0.74f, dimTo: Color? = null): AnnotatedString {
        if (!containsEmoji(text)) return AnnotatedString(text)
        val parts = runs(text)
        return buildAnnotatedString {
            parts.forEachIndexed { index, (emoji, run) ->
                var r = run
                if (!emoji) {
                    // glue emoji to the words around them (a lone ✨ on its own line looks lost)
                    if (index > 0 && parts[index - 1].first && r.startsWith(' ')) r = NBSP + r.drop(1)
                    if (index < parts.lastIndex && parts[index + 1].first && r.endsWith(' ')) r = r.dropLast(1) + NBSP
                    append(r)
                } else {
                    val start = length
                    append(r)
                    addStyle(SpanStyle(fontSize = scale.em, color = dimTo ?: Color.Unspecified), start, length)
                }
            }
        }
    }
}
