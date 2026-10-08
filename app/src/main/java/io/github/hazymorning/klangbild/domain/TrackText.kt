package io.github.hazymorning.klangbild.domain

/**
 * Typographic preparation of track metadata. Streaming metadata is noisy; a calm display
 * needs a little editing:
 *
 *  - Classical titles like "Suite bergamasque, L. 75: III. Clair de lune" become a work
 *    line ("Suite bergamasque, L. 75") above the movement ("III. Clair de lune").
 *  - "(feat. X)" moves from the title to the artists.
 *  - Version noise ("– 2011 Remaster", "(Live)") leaves the title and becomes a small note.
 *  - Long artist lists end with "und weitere" instead of running for three lines.
 *  - Spaced hyphens become en dashes that stay with the preceding word, so no line ever
 *    starts with a dash ("Presto – / Allegro assai", not "Presto / – Allegro assai").
 */
data class TrackText(
    val title: String,
    val work: String?,
    val artists: String?,
    val album: String?,
    val version: String?,
) {
    companion object {
        private val catalogue = Regex(
            """(\b(BWV|Op\.|op\.|KV|K\.|L\.|Hob\.|D\.|RV|S\.|WoO|HWV|Sz\.|TrV|FP|BB|Nr\.|No\.|Nº)\s?[\dIVXLC]+)""",
        )
        private val movementStart = Regex("""^([IVXLC]+|\d{1,2})\.\s+\S""")
        private val featInTitle = Regex("""\s*[(\[](?:feat\.?|ft\.?|featuring|mit)\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)
        private val versionWords =
            "remaster|remastered|live|edit|version|mix|mono|stereo|demo|acoustic|akustik|instrumental|bonus track|deluxe|single"
        private val dashVersion = Regex("""\s+[-–—]\s+([^-–—]*\b(?:$versionWords)\b[^-–—]*)$""", RegexOption.IGNORE_CASE)
        private val bracketVersion = Regex("""\s*[(\[]([^()\[\]]*\b(?:$versionWords)\b[^()\[\]]*)[)\]]$""", RegexOption.IGNORE_CASE)

        const val MAX_ARTISTS_CHARS = 72

        fun from(track: TrackInfo): TrackText {
            var title = track.title.trim()
            val artists = track.artists.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

            featInTitle.find(title)?.let { m ->
                m.groupValues[1].split(",", "&", " und ", " and ").map { it.trim() }
                    .filter { it.isNotEmpty() && artists.none { a -> a.equals(it, ignoreCase = true) } }
                    .forEach { artists += it }
                title = title.removeRange(m.range).trim()
            }

            var version: String? = null
            for (rx in listOf(dashVersion, bracketVersion)) {
                val m = rx.find(title) ?: continue
                // Never strip the whole title away.
                if (m.range.first > 0) {
                    version = m.groupValues[1].trim()
                    title = title.substring(0, m.range.first).trim()
                    break
                }
            }

            var work: String? = null
            val colon = title.lastIndexOf(": ")
            if (colon > 0) {
                val head = title.substring(0, colon).trim()
                val tail = title.substring(colon + 2).trim()
                if (tail.isNotEmpty() && (catalogue.containsMatchIn(head) || movementStart.containsMatchIn(tail))) {
                    work = head
                    title = tail
                }
            }

            val album = track.album?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }
            return TrackText(
                title = dashes(title.ifEmpty { track.title.trim() }),
                work = work?.let(::dashes),
                artists = formatArtists(artists),
                album = album?.takeUnless { work != null && it.equals(work, ignoreCase = true) },
                version = version,
            )
        }

        private val spacedDash = Regex("""\s+[-–—]\s+""")

        /** Spaced hyphen/dash → en dash glued to the word before it (no-break space). */
        fun dashes(s: String): String = s.replace(spacedDash, "\u00A0– ")

        /** "A", "A & B", "A, B & C"; very long lists end with "und weitere". */
        fun formatArtists(names: List<String>): String? {
            if (names.isEmpty()) return null
            if (names.size == 1) return names[0]
            val full = names.dropLast(1).joinToString(", ") + " & " + names.last()
            if (full.length <= MAX_ARTISTS_CHARS) return full
            val kept = ArrayList<String>()
            var length = 0
            for (n in names) {
                if (kept.isNotEmpty() && length + n.length + 2 > MAX_ARTISTS_CHARS - 12) break
                kept += n
                length += n.length + 2
            }
            return kept.joinToString(", ") + " und weitere"
        }
    }
}
