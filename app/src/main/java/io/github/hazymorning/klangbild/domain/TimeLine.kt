package io.github.hazymorning.klangbild.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Times as text, and the one separator every line uses. */
object TimeLine {
    /** The one separator used everywhere: a middle dot, glued to the word before it. */
    const val SEPARATOR = " · "

    /** "2:13", "21:47" – no leading zero, as a clock on a wall would show it. */
    private val clockFormat = DateTimeFormatter.ofPattern("H:mm", Locale.GERMAN)

    /** The time of day at [epochMs]. */
    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = clockFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "Tempo 1,5×" for spoken word that plays faster or slower than it was read; null at its own pace. */
    fun speed(speed: Float): String? {
        val hundredths = kotlin.math.round(speed * 100f).toInt()
        if (hundredths == 100) return null
        val whole = hundredths / 100
        val rest = hundredths % 100
        val text = when {
            rest == 0 -> "$whole"
            rest % 10 == 0 -> "$whole,${rest / 10}"
            else -> "$whole,${"%02d".format(rest)}"
        }
        return "Tempo\u00A0$text×"
    }

    /** "0:07", "3:41", "1:02:05" – as a clock shows it, no leading zero. */
    fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
