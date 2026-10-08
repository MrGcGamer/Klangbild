package io.github.hazymorning.klangbild.domain

import kotlin.math.roundToLong

/**
 * The time line replaces a progress bar: time in words, set like a label, followed by
 * what comes next – "In drei Minuten · Spiegel im Spiegel · Arvo Pärt". It changes at most
 * once a minute. Exact figures appear only briefly after a key press.
 */
object TimeLine {
    /** [label] is set in spaced capitals, [detail] in italics. */
    data class Line(val label: String, val detail: String? = null, val figures: Boolean = false)

    /** The one separator used everywhere: a middle dot between no-break-glued spaces. */
    const val SEPARATOR = " · "

    private val words = listOf(
        "", "eine", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
    )

    fun compute(
        status: PlayerStatus,
        positionMs: Long?,
        durationMs: Long?,
        next: TrackText?,
        source: String?,
        showExact: Boolean,
        linkDown: Boolean = false,
    ): Line? {
        if (linkDown) return Line("Verbindung unterbrochen", "wird wiederhergestellt …")
        val nextText = next?.let { listOfNotNull(it.title, it.shortArtists).joinToString(SEPARATOR) }
        if (showExact && positionMs != null) {
            val figures = if (durationMs != null) "${formatClock(positionMs)} / ${formatClock(durationMs)}" else formatClock(positionMs)
            return Line(figures, figures = true)
        }
        when (status) {
            PlayerStatus.PAUSED -> return Line("Pausiert")
            PlayerStatus.BUFFERING -> return Line("Einen Moment")
            else -> Unit
        }
        if (positionMs == null || durationMs == null || durationMs <= 0) {
            return source?.let { Line(it) } ?: nextText?.let { Line("Danach", it) }
        }
        val remaining = (durationMs - positionMs).coerceAtLeast(0)
        if (remaining <= 45_000) {
            return if (nextText != null) Line("Gleich", nextText) else Line("Letzte Takte")
        }
        val minutes = (remaining / 60_000.0).roundToLong().coerceAtLeast(1)
        return if (nextText != null) {
            Line("In ${minutesPhrase(minutes, dative = true)}", nextText)
        } else {
            Line("Noch ${minutesPhrase(minutes, dative = false)}")
        }
    }

    /** "einer Minute" / "drei Minuten" / "14 Minuten" – numbers up to twelve in words. */
    fun minutesPhrase(minutes: Long, dative: Boolean): String = when {
        minutes == 1L -> if (dative) "einer Minute" else "eine Minute"
        minutes <= 12 -> "${words[minutes.toInt()]} Minuten"
        else -> "$minutes Minuten"
    }

    fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
