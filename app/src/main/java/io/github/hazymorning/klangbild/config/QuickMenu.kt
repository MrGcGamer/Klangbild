package io.github.hazymorning.klangbild.config

import androidx.annotation.StringRes
import io.github.hazymorning.klangbild.R
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.text.Texts

/** What the screen shows: one of the two views of the music, or the window view. */
enum class Picture { SINGLE, FLOW, VISTA }

/** How far the current song's way into the favorites has come. */
enum class FavoriteState {
    /** Not asked yet (or not known to be one). */
    NONE,

    /** Asked, no answer yet. */
    BUSY,

    /** It is one: added just now, or Music Assistant says so. */
    DONE,

    /** Music Assistant could not find what plays (a station between songs, a song it does not know). */
    FAILED,
}

/** One entry of the quick settings, in their order along the bottom edge. */
enum class QuickItem(@StringRes val label: Int) {
    PICTURE(R.string.quick_view),
    FAVORITE(R.string.quick_favorite),
    LIGHTS(R.string.quick_lights),
    SHUFFLE(R.string.quick_shuffle),
}

/** An entry as the quick settings show it: its name and its value in words. */
data class QuickRow(val item: QuickItem, val label: String, val value: String)

/** What the quick settings need to know besides the settings. */
data class QuickContext(
    /** The screen shows the window view. */
    val vista: Boolean = false,
    /** A view chosen with OK that is taken once the presses stop. */
    val pending: Picture? = null,
    /** Something plays (or is paused) on the stage. */
    val music: Boolean = false,
    /**
     * Favorites can be asked for (a Music Assistant player, with a way to reach it), and how far
     * the current song's way has come.
     */
    val favorite: FavoriteState? = null,
    /** Shuffle on or off as far as known; null when the player cannot shuffle. */
    val shuffle: Boolean? = null,
)

/**
 * The quick settings: a row of a few entries that rises from the bottom edge when down is
 * pressed, for what is wanted now and then while watching and should not hide in the settings
 * (those stay on OK held). Left and right choose an entry, OK changes it (or does what it
 * says), up leaves the row. Entries that cannot do anything now are left out (no favorite
 * without music, no lights before they are set up, no shuffle on a player that cannot). The
 * sleep timer lives in the settings.
 */
object QuickMenu {
    fun rows(s: Settings, ctx: QuickContext, t: Texts): List<QuickRow> = buildList {
        fun row(item: QuickItem, value: String) = add(QuickRow(item, t[item.label], value))
        row(QuickItem.PICTURE, pictureValue(ctx.pending ?: picture(s, ctx), t))
        if (ctx.music && ctx.favorite != null) row(QuickItem.FAVORITE, favoriteValue(ctx.favorite, t))
        if (s.roomLights.isSet) row(QuickItem.LIGHTS, t[if (s.roomLightLevel == RoomLightLevel.OFF) R.string.off else R.string.on])
        if (ctx.music && ctx.shuffle != null) row(QuickItem.SHUFFLE, t[if (ctx.shuffle) R.string.on else R.string.off])
    }

    /** What the screen shows now. */
    fun picture(s: Settings, ctx: QuickContext): Picture = when {
        ctx.vista -> Picture.VISTA
        s.view == StageView.FLOW -> Picture.FLOW
        else -> Picture.SINGLE
    }

    fun pictureValue(p: Picture, t: Texts): String = t[
        when (p) {
            Picture.SINGLE -> R.string.view_single
            Picture.FLOW -> R.string.view_flow
            Picture.VISTA -> R.string.view_vista
        },
    ]

    fun favoriteValue(f: FavoriteState, t: Texts): String = t[
        when (f) {
            FavoriteState.NONE -> R.string.favorite_add
            FavoriteState.BUSY -> R.string.favorite_adding
            FavoriteState.DONE -> R.string.favorite_added
            FavoriteState.FAILED -> R.string.favorite_failed
        },
    ]

    /**
     * Every value [item] can show, so that its place is as wide as the widest of them and
     * nothing moves when a value changes.
     */
    fun values(item: QuickItem, t: Texts): List<String> = when (item) {
        QuickItem.PICTURE -> PICTURES.map { pictureValue(it, t) }
        QuickItem.FAVORITE -> FavoriteState.entries.map { favoriteValue(it, t) }
        QuickItem.LIGHTS, QuickItem.SHUFFLE -> listOf(t[R.string.on], t[R.string.off])
    }

    /** From the music's own picture outward: the cover, the strip of covers, the landscape. */
    val PICTURES = listOf(Picture.SINGLE, Picture.FLOW, Picture.VISTA)

    /** The picture after one step from [p] (−1 or +1); [wrap] goes round. */
    fun step(p: Picture, direction: Int, wrap: Boolean): Picture {
        val i = PICTURES.indexOf(p) + direction
        return if (wrap) PICTURES[Math.floorMod(i, PICTURES.size)] else PICTURES[i.coerceIn(0, PICTURES.size - 1)]
    }
}
