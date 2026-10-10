package io.github.hazymorning.klangbild.config

import io.github.hazymorning.klangbild.domain.StageView

/** What the screen shows: one of the two views of the music, or the Fensterblick. */
enum class Picture { SINGLE, FLOW, VISTA }

/** How far the current song's way into the favourites has come. */
enum class FavoriteState {
    /** Not asked yet (or not known to be one). */
    NONE,

    /** Asked, no answer yet. */
    BUSY,

    /** It is one – added just now, or Music Assistant says so. */
    DONE,

    /** Music Assistant could not find what plays (a station between songs, a song it does not know). */
    FAILED,
}

/** One entry of the quick settings, in their order along the bottom edge. */
enum class QuickItem(val label: String) {
    PICTURE("Ansicht"),
    FAVORITE("Favorit"),
    BARS("Lichtleisten"),
    SHUFFLE("Zufallswiedergabe"),
}

/** An entry as the quick settings show it: its value in words. */
data class QuickRow(val item: QuickItem, val value: String)

/** What the quick settings need to know besides the settings. */
data class QuickContext(
    /** The screen shows the Fensterblick. */
    val vista: Boolean = false,
    /** A view chosen with OK that is taken once the presses stop. */
    val pending: Picture? = null,
    /** Something plays (or is paused) on the stage. */
    val music: Boolean = false,
    /** Favourites can be asked for (a Music Assistant player, with a way to reach it), and how far the current song's way has come. */
    val favorite: FavoriteState? = null,
    /** Shuffle on or off as far as known – null when the player cannot shuffle. */
    val shuffle: Boolean? = null,
)

/**
 * The quick settings: a row of a few entries that rises from the bottom edge when down is
 * pressed – for what is wanted now and then while watching, and should not hide in the
 * settings (those stay on OK held). Left and right choose an entry, OK changes it (or does what
 * it says), up leaves the row. Entries that cannot do anything now are left out (no favourite
 * without music, no light bars before they are set up, no shuffle on a player that cannot).
 * The sleep timer lives in the settings.
 */
object QuickMenu {
    fun rows(s: Settings, ctx: QuickContext): List<QuickRow> = buildList {
        add(QuickRow(QuickItem.PICTURE, pictureValue(ctx.pending ?: picture(s, ctx))))
        if (ctx.music && ctx.favorite != null) add(QuickRow(QuickItem.FAVORITE, favoriteValue(ctx.favorite)))
        if (s.bars.isSet) add(QuickRow(QuickItem.BARS, if (s.barLevel == BarLevel.OFF) "Aus" else "An"))
        if (ctx.music && ctx.shuffle != null) add(QuickRow(QuickItem.SHUFFLE, if (ctx.shuffle) "An" else "Aus"))
    }

    /** What the screen shows now. */
    fun picture(s: Settings, ctx: QuickContext): Picture = when {
        ctx.vista -> Picture.VISTA
        s.view == StageView.FLOW -> Picture.FLOW
        else -> Picture.SINGLE
    }

    fun pictureValue(p: Picture): String = when (p) {
        Picture.SINGLE -> "Einzelnes Cover"
        Picture.FLOW -> "Cover Flow"
        Picture.VISTA -> "Fensterblick"
    }

    fun favoriteValue(f: FavoriteState): String = when (f) {
        FavoriteState.NONE -> "Hinzufügen"
        FavoriteState.BUSY -> "Hinzufügen …"
        FavoriteState.DONE -> "Hinzugefügt"
        FavoriteState.FAILED -> "Nicht gefunden"
    }

    /** From the music's own picture outwards: the cover, the strip of covers, the landscape. */
    val PICTURES = listOf(Picture.SINGLE, Picture.FLOW, Picture.VISTA)

    /** The picture after one step from [p] (−1 or +1); [wrap] goes round. */
    fun step(p: Picture, direction: Int, wrap: Boolean): Picture {
        val i = PICTURES.indexOf(p) + direction
        return if (wrap) PICTURES[Math.floorMod(i, PICTURES.size)] else PICTURES[i.coerceIn(0, PICTURES.size - 1)]
    }
}
