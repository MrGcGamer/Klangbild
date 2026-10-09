package io.github.hazymorning.klangbild.domain

import kotlin.math.roundToInt

/**
 * Where the music plays and how loud – the place in the stage's dateline ("21:14 · Wohnzimmer
 * + 5"). A group is named the way Sonos and Spotify do it: this speaker plus the number of
 * the others, or both names when there is just one more. A speaker playing alone needs no
 * label – the screen hangs in its room.
 */
object SpeakerLabel {

    /**
     * "Wohnzimmer + 5", "Wohnzimmer + Küche"; a group with a name of its own is called by
     * it ("Erdgeschoss"). Null while the speaker plays alone.
     */
    fun group(group: SpeakerGroup?): String? {
        group?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val others = group?.others?.takeIf { it.isNotEmpty() } ?: return null
        val self = group.self?.let(::roomName)
        val single = others.singleOrNull()?.let(::roomName)
        return when {
            self == null -> "${countWord(others.size + 1)} Lautsprecher"
            single != null && single != self -> "$self + $single"
            else -> "$self + ${others.size}"
        }
    }

    /**
     * True if [queueName] names something else than the speaker called [ownName] – then the
     * speaker plays from another player's queue, a group's. Spellings of the same name
     * ("Wohnzimmer" and "Wohnzimmer Lautsprecher") count as the same.
     */
    fun isOtherName(queueName: String, ownName: String?): Boolean {
        val q = key(queueName)
        if (q.isEmpty()) return false
        if (ownName == null) return true
        return listOfNotNull(key(ownName), roomName(ownName)?.let(::key)).filter { it.isNotEmpty() }
            .none { own -> own == q || own.contains(q) || q.contains(own) }
    }

    private fun key(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFKD).filter { it.isLetterOrDigit() }

    /** "35 %" with a narrow no-break space, or "stumm". */
    fun volume(level: Float?, muted: Boolean): String? = when {
        muted -> "stumm"
        level == null -> null
        else -> "${(level * 100f).roundToInt().coerceIn(0, 100)} %"
    }

    /** Words that name the device rather than the room: "Wohnzimmer Lautsprecher" → "Wohnzimmer". */
    private val generic = setOf(
        "lautsprecher", "speaker", "speakers", "box", "boxen", "player", "mediaplayer", "musik", "music",
        "ma", "sonos", "homepod", "echo", "nest", "chromecast", "google", "alexa", "airplay", "snapcast",
        "squeezebox", "wiim", "heos", "musiccast", "bluesound", "raumfeld", "mini", "one",
    )

    private val note = Regex("""\s*[(\[]([^)\]]*)[)\]]""")

    /**
     * The room in a speaker's name: generic device words and notes in brackets dropped. If
     * nothing is left, the note is the room ("Sonos One (Bad)" → "Bad"), and failing that
     * the full name.
     */
    fun roomName(name: String): String? {
        val trimmed = name.trim().ifEmpty { return null }
        val main = clean(note.replace(trimmed, ""))
        if (main != null) return main
        return note.findAll(trimmed).firstNotNullOfOrNull { clean(it.groupValues[1]) } ?: trimmed
    }

    private fun clean(text: String): String? = text.trim().split(Regex("""\s+""")).mapNotNull { word ->
        word.split('-')
            .filter { it.isNotEmpty() && it.lowercase() !in generic }
            .joinToString("-") { stripSuffix(it) }
            .ifEmpty { null }
    }.joinToString(" ").ifEmpty { null }

    /** "Wohnzimmerlautsprecher" → "Wohnzimmer". */
    private fun stripSuffix(word: String): String {
        val lower = word.lowercase()
        for (suffix in listOf("lautsprecher", "speaker")) {
            if (lower.endsWith(suffix) && word.length - suffix.length >= 3) return word.dropLast(suffix.length)
        }
        return word
    }

    private val words = listOf(
        "null", "ein", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
    )

    /** Numbers up to twelve in words, capitalised for the start of a label. */
    private fun countWord(n: Int): String = words.getOrNull(n)?.replaceFirstChar { it.uppercase() } ?: n.toString()
}
