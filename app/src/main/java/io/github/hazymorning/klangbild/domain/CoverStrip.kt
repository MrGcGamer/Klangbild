package io.github.hazymorning.klangbild.domain

/** A card of the cover flow: one picture (an album, a station) and the track that stands for it. */
data class StripCard(val id: Long, val track: TrackInfo) {
    val picture: String get() = track.pictureKey
}

/** The cover flow in play order: the card at [current] is in the middle. */
data class Strip(val cards: List<StripCard>, val current: Int) {
    val middle: StripCard get() = cards[current]
}

/**
 * Keeps the cover flow's strip as the music moves on: the pictures played before, the one
 * playing, those to come – distinct pictures, so the songs of one album are one card. A card
 * keeps its id from the moment it appears until it leaves the strip, so the UI can animate
 * between two strips card by card: the next picture arriving is the card that stood to the
 * right, a step back brings back the card from the left, anything else is a new card.
 */
class CoverStrip(private val side: Int = SIDE) {
    private var lastId = 0L
    private val history = ArrayList<StripCard>()
    private var current: StripCard? = null
    private var upcoming: List<StripCard> = emptyList()

    /**
     * [track] plays now, [coming] is what follows in the queue (as far as known), [before] what
     * the queue played before it (most recent last; empty when unknown – then the strip
     * remembers on its own).
     */
    fun update(track: TrackInfo, coming: List<TrackInfo> = emptyList(), before: List<TrackInfo> = emptyList()): Strip {
        val picture = track.pictureKey
        val cur = current
        var spare: StripCard? = null
        current = when {
            cur == null -> card(track)
            cur.picture == picture -> cur.copy(track = track)
            upcoming.firstOrNull()?.picture == picture -> {
                remember(cur)
                upcoming.first().copy(track = track)
            }
            history.lastOrNull()?.picture == picture -> {
                spare = cur
                history.removeAt(history.lastIndex).copy(track = track)
            }
            else -> {
                remember(cur)
                card(track)
            }
        }
        val now = current!!
        if (before.isNotEmpty()) {
            val known = (history + listOfNotNull(spare)).associateBy { it.picture }
            val played = distinct(before.asReversed(), now.picture).take(KEEP).map { t -> known[t.pictureKey]?.copy(track = t) ?: card(t) }
            history.clear()
            history.addAll(played.asReversed())
        }
        val reuse = (upcoming + listOfNotNull(spare)).associateBy { it.picture }
        upcoming = distinct(coming, now.picture).take(side).map { t -> reuse[t.pictureKey]?.copy(track = t) ?: card(t) }
        val shown = history.takeLast(side)
        return Strip(shown + now + upcoming, shown.size)
    }

    /** Forgets everything (another speaker, another view). */
    fun clear() {
        history.clear()
        current = null
        upcoming = emptyList()
    }

    private fun card(track: TrackInfo) = StripCard(++lastId, track)

    private fun remember(card: StripCard) {
        if (history.lastOrNull()?.picture != card.picture) history += card
        while (history.size > KEEP) history.removeAt(0)
    }

    /** [tracks] with consecutive repeats of a picture merged, the current picture left out. */
    private fun distinct(tracks: List<TrackInfo>, currentPicture: String): List<TrackInfo> {
        val out = ArrayList<TrackInfo>()
        for (t in tracks) {
            val p = t.pictureKey
            if (p == currentPicture && out.isEmpty()) continue
            if (out.lastOrNull()?.pictureKey == p) continue
            out += t
        }
        return out
    }

    companion object {
        /** Cards shown on each side of the middle. */
        const val SIDE = 2

        /** Pictures remembered behind the current one. */
        const val KEEP = 6
    }
}
