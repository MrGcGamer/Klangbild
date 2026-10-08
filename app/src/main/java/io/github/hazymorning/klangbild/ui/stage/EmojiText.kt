package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em

/**
 * Colour emoji at display size shout over the typography. In titles they are set a little
 * smaller, and a leading or trailing emoji is glued to its word with a no-break space, so
 * a ✨ never ends up alone on its own line.
 */
object EmojiText {
    private const val NBSP = '\u00A0'

    fun isEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || cp == 0x20E3 || cp in 0xE0020..0xE007F

    fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isEmoji(cp) && cp != 0x200D && cp != 0xFE0F) return true
            i += Character.charCount(cp)
        }
        return false
    }

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

    fun annotate(text: String, scale: Float = 0.74f): AnnotatedString {
        if (!containsEmoji(text)) return AnnotatedString(text)
        val parts = runs(text)
        return buildAnnotatedString {
            parts.forEachIndexed { index, (emoji, run) ->
                var r = run
                if (!emoji) {
                    // glue leading/trailing emoji to their word (a lone ✨ on its own line looks lost)
                    if (index == 1 && parts[0].first && r.startsWith(' ')) r = NBSP + r.drop(1)
                    if (index == parts.lastIndex - 1 && parts[index + 1].first && r.endsWith(' ')) r = r.dropLast(1) + NBSP
                    append(r)
                } else {
                    val start = length
                    append(r)
                    addStyle(SpanStyle(fontSize = scale.em), start, length)
                }
            }
        }
    }
}
