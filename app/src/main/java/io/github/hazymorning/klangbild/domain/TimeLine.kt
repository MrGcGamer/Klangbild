package io.github.hazymorning.klangbild.domain

/** Times as text, and the one separator every line uses. */
object TimeLine {
    /** The one separator used everywhere: a middle dot, glued to the word before it. */
    const val SEPARATOR = " · "

    /** "0:07", "3:41", "1:02:05" – as a clock shows it, no leading zero. */
    fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
