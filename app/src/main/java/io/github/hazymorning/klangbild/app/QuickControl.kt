package io.github.hazymorning.klangbild.app

import io.github.hazymorning.klangbild.config.BarLevel
import io.github.hazymorning.klangbild.config.FavoriteState
import io.github.hazymorning.klangbild.config.Picture
import io.github.hazymorning.klangbild.config.QuickContext
import io.github.hazymorning.klangbild.config.QuickItem
import io.github.hazymorning.klangbild.config.QuickMenu
import io.github.hazymorning.klangbild.config.QuickRow
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.domain.TrackInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The quick settings while they are open (down on the remote): which entry the cursor is on,
 * and what OK does to it. While they are open, the window view stays as it was unless they
 * change it. Untouched, they close after [IDLE_MS].
 */
internal class QuickControl(private val host: ControllerHost) {
    /** The entry the cursor is on, by what it is, so entries coming and going never move it. */
    @Volatile private var cursor: QuickItem? = null
    @Volatile private var touchedAt = 0L

    /** Whether the window view showed when the quick settings opened: it stays as it was while they are open. */
    @Volatile private var hold: Boolean? = null

    /** A view chosen with OK, taken once the presses stop (so a step through the views never shows the one between). */
    @Volatile private var pendingPicture: Picture? = null
    private var pictureJob: Job? = null

    /** The lights' level before they were switched off from the quick settings. */
    private var lightsBefore = BarLevel.DEFAULT

    /** The current song's way into the favorites (by the song's key). */
    @Volatile private var favorite: Pair<String, FavoriteState>? = null

    /** Shuffle just asked for, and until when it is shown before the player has reported it. */
    @Volatile private var shuffleWanted: Pair<Boolean, Long>? = null

    val isOpen: Boolean get() = cursor != null

    /** Opens the quick settings on the view entry. */
    fun open(menuOpen: Boolean) {
        synchronized(host.lock) {
            if (menuOpen || cursor != null) return
            cursor = QuickItem.PICTURE
            touchedAt = host.now()
            hold = host.ui.vista != null
            host.recompute()
        }
    }

    /** Closes the quick settings (up, back, after a while alone); a view still waiting is taken now. */
    fun close() {
        applyPicture()
        synchronized(host.lock) {
            cursor = null
            hold = null
        }
        host.recompute()
    }

    /** A key in the quick settings that changes nothing: they stay open a while longer. */
    fun touch() {
        touchedAt = host.now()
    }

    /** Left (-1) or right (+1) along the quick settings; it stops at their ends. */
    fun move(delta: Int) {
        synchronized(host.lock) {
            val c = cursor ?: return
            val items = rows().map { it.item }
            cursor = items[(indexOf(items, c) + delta).coerceIn(0, items.size - 1)]
            touchedAt = host.now()
            host.recompute()
        }
    }

    /** OK on the entry under the cursor: its next value, or what it says. */
    fun change() {
        val row = synchronized(host.lock) {
            val c = cursor ?: return
            touchedAt = host.now()
            val rows = rows()
            rows.getOrNull(indexOf(rows.map { it.item }, c))
        } ?: return
        when (row.item) {
            QuickItem.PICTURE -> synchronized(host.lock) {
                val shown = pendingPicture ?: QuickMenu.picture(host.settings, context())
                pendingPicture = QuickMenu.step(shown, +1, wrap = true)
                pictureJob?.cancel()
                pictureJob = host.scope.launch {
                    delay(SETTLE_MS)
                    applyPicture()
                }
                host.recompute()
            }
            QuickItem.FAVORITE -> addFavorite()
            QuickItem.BARS -> {
                // On and off: off remembers the level, on brings it back.
                val s = host.settings
                val level = if (s.barLevel == BarLevel.OFF) lightsBefore else BarLevel.OFF
                if (s.barLevel != BarLevel.OFF) lightsBefore = s.barLevel
                host.applySettings(s.copy(barLevel = level))
            }
            QuickItem.SHUFFLE -> toggleShuffle()
        }
    }

    /**
     * Closes quick settings nobody has touched for [IDLE_MS], or that the settings menu replaced
     * (called under the lock).
     */
    fun expire(t: Long, menuOpen: Boolean) {
        if (cursor != null && (t - touchedAt > IDLE_MS || menuOpen)) {
            cursor = null
            hold = null
            if (pendingPicture != null) host.scope.launch { applyPicture() }
        }
    }

    /** Whether the window view shows while the quick settings are open; null when they leave it to its delays. */
    val vistaHeld: Boolean? get() = hold?.takeIf { cursor != null }

    /** The quick settings as they show for [screen], null while they are closed (called under the lock). */
    fun state(screen: Screen, vista: Boolean): QuickState? {
        val c = cursor ?: return null
        val rows = rows(context(screen, vista))
        return QuickState(rows, indexOf(rows.map { it.item }, c))
    }

    /** Takes the view chosen in the quick settings: the window view, or one of the stage's two views. */
    private fun applyPicture() {
        val next = synchronized(host.lock) {
            pictureJob?.cancel()
            pictureJob = null
            pendingPicture.also { pendingPicture = null }
        } ?: return
        val s = host.settings
        if (next == Picture.VISTA) {
            if (hold != null) hold = true
            host.showVista()
            return
        }
        if (hold != null) hold = false
        host.hideVista()
        val view = if (next == Picture.FLOW) StageView.FLOW else StageView.SINGLE
        if (view != s.view) host.applySettings(s.copy(view = view))
    }

    /** Adds what plays to Music Assistant's favorites; the quick settings say how it went. */
    private fun addFavorite() {
        val track = (host.ui.screen as? Screen.Stage)?.track ?: return
        val src = host.source ?: return
        synchronized(host.lock) {
            val state = favoriteState(track)
            if (state == FavoriteState.BUSY || state == FavoriteState.DONE) return
            favorite = track.key to FavoriteState.BUSY
        }
        host.recompute()
        host.scope.launch {
            val ok = src.favorite()
            synchronized(host.lock) {
                if (favorite?.first == track.key) favorite = track.key to if (ok) FavoriteState.DONE else FavoriteState.FAILED
            }
            host.recompute()
        }
    }

    /**
     * Turns shuffle the other way: shown at once and sent to the player, which reports it back in
     * a moment. Until then (a few seconds at most) the quick settings show what was asked for.
     */
    private fun toggleShuffle() {
        val src = host.source ?: return
        val on = synchronized(host.lock) {
            val now = shuffleShown() ?: return
            shuffleWanted = !now to host.now() + SHUFFLE_TRUST_MS
            !now
        }
        host.recompute()
        host.scope.launch {
            if (!src.shuffle(on)) synchronized(host.lock) { shuffleWanted = null }
            host.recompute()
        }
    }

    /** Shuffle as the quick settings show it: what was just asked for, else what the player says. */
    private fun shuffleShown(): Boolean? {
        val reported = host.ui.source.player?.shuffle ?: return null
        val wanted = shuffleWanted
        if (wanted == null || host.now() > wanted.second || wanted.first == reported) {
            shuffleWanted = null
            return reported
        }
        return wanted.first
    }

    /** How far [track]'s way into the favorites has come: asked here, or known from Music Assistant. */
    private fun favoriteState(track: TrackInfo): FavoriteState =
        favorite?.takeIf { it.first == track.key }?.second ?: if (track.favorite) FavoriteState.DONE else FavoriteState.NONE

    /** What the quick settings need to know: by default what the screen shows now, while working out the screen what it is about to show. */
    private fun context(screen: Screen = host.ui.screen, vista: Boolean = host.ui.vista != null): QuickContext {
        val track = (screen as? Screen.Stage)?.track
        return QuickContext(
            vista = vista,
            pending = pendingPicture,
            music = track != null,
            favorite = track?.takeIf { host.ui.source.favorites }?.let { favoriteState(it) },
            shuffle = shuffleShown(),
        )
    }

    private fun rows(ctx: QuickContext = context()): List<QuickRow> = QuickMenu.rows(host.settings, ctx)

    /** Where [item] stands among [items]; once it is gone (the favorite when the music stops), the entry that took its place. */
    private fun indexOf(items: List<QuickItem>, item: QuickItem): Int =
        items.indexOf(item).takeIf { it >= 0 } ?: items.indexOfFirst { it.ordinal > item.ordinal }.takeIf { it >= 0 } ?: (items.size - 1)

    companion object {
        /** Untouched quick settings close after this long: they are for a moment's choice. */
        const val IDLE_MS = 20_000L

        /** A view chosen in the quick settings is taken this long after the last press. */
        const val SETTLE_MS = 900L

        /** Shuffle just asked for is shown this long before the player has reported it. */
        const val SHUFFLE_TRUST_MS = 6_000L
    }
}
