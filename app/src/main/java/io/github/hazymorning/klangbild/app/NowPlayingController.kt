package io.github.hazymorning.klangbild.app

import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.AppearanceMode
import io.github.hazymorning.klangbild.config.BackgroundMode
import io.github.hazymorning.klangbild.config.DisplayOptions
import io.github.hazymorning.klangbild.config.LaunchExtras
import io.github.hazymorning.klangbild.config.MiniPlayer
import io.github.hazymorning.klangbild.config.MenuContext
import io.github.hazymorning.klangbild.config.MenuEntry
import io.github.hazymorning.klangbild.config.MenuRow
import io.github.hazymorning.klangbild.config.SessionOptions
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SettingsMenu
import io.github.hazymorning.klangbild.config.SettingsStore
import io.github.hazymorning.klangbild.config.SleepAction
import io.github.hazymorning.klangbild.config.VistaRequest
import io.github.hazymorning.klangbild.config.VolumeDisplay
import io.github.hazymorning.klangbild.data.ArtworkLoader
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.data.ha.HaConfig
import io.github.hazymorning.klangbild.data.ha.HaSource
import io.github.hazymorning.klangbild.data.ma.MaClient
import io.github.hazymorning.klangbild.domain.ChapterPlace
import io.github.hazymorning.klangbild.domain.Chapters
import io.github.hazymorning.klangbild.domain.CoverStrip
import io.github.hazymorning.klangbild.domain.FieldClock
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Progress
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.ScreenDecision
import io.github.hazymorning.klangbild.domain.FlipGuard
import io.github.hazymorning.klangbild.domain.ScreenStateMachine
import io.github.hazymorning.klangbild.domain.SleepTimer
import io.github.hazymorning.klangbild.domain.SmoothedPosition
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.domain.Timing
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.VistaPolicy
import io.github.hazymorning.klangbild.lights.LightBridge
import io.github.hazymorning.klangbild.lights.LightSync
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupHandler
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
import io.github.hazymorning.klangbild.vista.VistaClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

/** A scene that is ready to be shown for a track. */
data class StageData(val track: TrackInfo, val scene: ArtworkScene)

/** A card of the cover flow with its scene, ready to be shown. */
data class FlowEntry(val id: Long, val track: TrackInfo, val scene: ArtworkScene) {
    val stage: StageData get() = StageData(track, scene)
}

/** The cover flow as far as it is ready: cards in play order, the one at [current] in the middle. */
data class FlowData(val cards: List<FlowEntry>, val current: Int) {
    val middle: FlowEntry get() = cards[current]
}

/** The settings menu while it is open: its lines and the one the cursor is on. */
data class MenuState(val rows: List<MenuRow>, val cursor: Int)

/** Everything the UI renders. */
data class UiState(
    val screen: Screen = Screen.Rest(io.github.hazymorning.klangbild.domain.RestReason.CONNECTING),
    val keepScreenOn: Boolean = true,
    val asleep: Boolean = false,
    val sleepAction: SleepAction = SleepAction.RELEASE,
    /** The latest scene that is ready; may lag behind [screen] while a new cover loads. */
    val stage: StageData? = null,
    /** How the stage shows what plays. */
    val view: StageView = StageView.SINGLE,
    /** The cover flow (in [StageView.FLOW]); like [stage] it may lag behind [screen]. */
    val flow: FlowData? = null,
    val lighting: Lighting = Lighting.DAY,
    /** Light wall by day, dark at night (or as set). */
    val appearance: Appearance = Appearance.DARK,
    /** What the stage shows (clock, progress, album …) and how strong its light is. */
    val display: DisplayOptions = DisplayOptions(),
    val source: SourceState = SourceState(LinkState.CONNECTING),
    val session: SessionOptions = SessionOptions(),
    val entityId: String? = null,
    /** Address, code and QR modules while the setup screen is open. */
    val setup: SetupInfo? = null,
    /** The settings menu, null while it is closed. */
    val menu: MenuState? = null,
    /** The volume just changed: the status line shows it for a moment. */
    val volumeFlash: Boolean = false,
    /** The chapter of the book or episode on stage, while it has chapters. */
    val chapter: ChapterPlace? = null,
    /** When the sleep timer pauses the music (wall clock), while it is set. */
    val sleepAt: Long? = null,
    /** The Fensterblick, while it has the screen. */
    val vista: VistaUi? = null,
    /** The Fensterblick is on and may come: its picture is baked ahead, for its label on this side. */
    val vistaAhead: MiniPlayer? = null,
)

/**
 * The Fensterblick while it shows: what plays (for the mini-player), where the label sits, and
 * – while the preview is open – what the preview has set.
 */
data class VistaUi(
    val track: TrackInfo?,
    val status: PlayerStatus?,
    val miniPlayer: MiniPlayer,
    val preview: VistaClock.Preview? = null,
)

/**
 * The app's single source of truth: runs the Home Assistant source, runs the screen state
 * machine on every update and once a second, has the scenes (cover, palette, light) prepared
 * off the main thread – the next track's ahead of time, in Cover Flow the whole strip's.
 */
class NowPlayingController(
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val settings = MutableStateFlow(Settings())
    private val session = MutableStateFlow(SessionOptions())

    private var source: NowPlayingSource? = null
    private var sourceKey: String? = null
    private var sourceJob: Job? = null
    private var ticker: Job? = null
    private var settingsJob: Job? = null
    private var machine = ScreenStateMachine()
    private val smooth = SmoothedPosition()
    private var lastReportKey: Any? = null

    /** When the Fensterblick takes the screen ([VistaPolicy]), and the time its sky shows. */
    private val vistaPolicy = VistaPolicy()
    val vistaClock = VistaClock(now)

    /** The Fensterblick's preview is open: it shows, and the keys set its hour, month and sky. */
    @Volatile private var previewOpen = false
    @Volatile private var previewTouchedAt = 0L

    /** "Einschlafen": set from the menu, it pauses the music once ([SleepTimer]). */
    private val sleepTimer = SleepTimer()
    private var sleepJob: Job? = null
    private var sleepWake: Long? = null

    /** The next chapter's start on the wall clock, so the stage turns to it on time. */
    @Volatile private var chapterEndsAt: Long? = null

    /** A jump with the remote that Home Assistant has not confirmed yet: the bar shows where it goes. */
    @Volatile private var seekOverride: SeekOverride? = null
    private var seekJob: Job? = null

    /** The colour field's time, shared by the screen and the light bars. */
    private val fieldClock = FieldClock(now)

    /** The light bars behind the TV in the cover's colours (through Home Assistant). */
    private val lightSync = LightSync(scope, now)

    /** The colour field's time now (seconds): it runs while music plays, at the motion setting's speed. */
    fun fieldTime(): Double = fieldClock.now()

    private val artwork = ArtworkLoader(http, { settings.value.haUrl }, { settings.value.haToken })
    private val scenes = Scenes(scope, artwork)
    private val strip = CoverStrip()

    private var viewers = 0

    private val setupHandler = SetupHandler(
        settings = { settings.value },
        discover = { url, token -> withContext(Dispatchers.IO) { SetupServer.discover(http, url, token) } },
        save = { updated ->
            settingsStore.update({ updated }, settings.value)
            // Leaving setup: the new settings start a fresh connection.
            session.value = session.value.copy(showSetup = false)
            machine.requestSetup(false)
        },
        checkMa = { url, token -> MaClient(http, url, token).check() },
        maGuess = {
            val track = _ui.value.source.player?.track
            MaClient.addressIn(track?.artworkUrl) ?: MaClient.addressIn(track?.artworkFallbackUrl)
        },
    )
    private val setupServer = SetupServer(scope, setupHandler)
    private var setupJob: Job? = null

    /** A screen (activity or dream) starts showing the controller. */
    @Synchronized
    fun attach(spec: ScreenSpec) {
        viewers++
        scenes.frame = scenes.frame.copy(screen = spec)
        if (viewers == 1) start()
    }

    @Synchronized
    fun detach() {
        viewers = (viewers - 1).coerceAtLeast(0)
        if (viewers == 0) stop()
    }

    private var stoppedAt = 0L
    private var releaseJob: Job? = null

    private fun start() {
        releaseJob?.cancel()
        // A sleep timer outlives a hand-over between the app and the screensaver, not a closed app.
        if (stoppedAt > 0 && now() - stoppedAt > HANDOVER_MS) sleepTimer.clear()
        settingsJob = scope.launch {
            settingsStore.settings.collect { s ->
                // While the menu's own changes are being written, it already shows them.
                if (localEdits.get() == 0) onSettings(s)
            }
        }
        ticker = scope.launch {
            while (isActive) {
                delay(tickDelay())
                recompute()
            }
        }
        setupJob = scope.launch {
            setupServer.info.collect { info -> synchronized(this@NowPlayingController) { _ui.value = _ui.value.copy(setup = info) } }
        }
    }

    private fun stop() {
        settingsJob?.cancel()
        ticker?.cancel()
        setupJob?.cancel()
        setupServer.stop()
        sourceJob?.cancel()
        // A report held back belongs to the source that sent it.
        heldJob?.cancel()
        heldJob = null
        flips = FlipGuard()
        stopSource(source)
        source = null
        sourceKey = null
        sleepJob?.cancel()
        sleepJob = null
        sleepWake = null
        seekJob?.cancel()
        seekOverride = null
        stoppedAt = now()
        // Covers and scenes are let go once it is clear that no screen comes straight back.
        releaseJob = scope.launch {
            delay(HANDOVER_MS)
            releaseMemory()
        }
        // The next start begins afresh: a pause from last night must not send a new launch to
        // sleep (or close it) before the music has been heard of.
        val setup = machine.isSetupRequested
        machine = ScreenStateMachine(machine.timing).also { it.requestSetup(setup) }
        lastReportKey = null
        lastVolume = null
        _ui.value = _ui.value.copy(
            source = SourceState(LinkState.CONNECTING),
            screen = Screen.Rest(io.github.hazymorning.klangbild.domain.RestReason.CONNECTING),
            stage = null,
            flow = null,
            asleep = false,
            keepScreenOn = true,
            chapter = null,
            sleepAt = null,
        )
    }

    /** Lets go of decoded covers and scenes while no screen shows them (or when the system asks). */
    @Synchronized
    fun releaseMemory() {
        if (viewers > 0) return
        scenes.clear()
        artwork.clear()
        strip.clear()
    }

    /** Once a second – or sooner, when a chapter ends before that. */
    private fun tickDelay(): Long = chapterEndsAt?.let { (it - now() + 20).coerceIn(20, 1_000) } ?: 1_000

    /** Extras from `am start` (cold start or onNewIntent). */
    fun applyLaunch(extras: Map<String, Any?>) {
        session.value = LaunchExtras.session(extras)
        machine.requestSetup(session.value.showSetup)
        when (session.value.vista) {
            VistaRequest.SHOW -> showVista(timelapse = false)
            VistaRequest.TIMELAPSE -> showVista(timelapse = true)
            // Asked to go: like a key press – it goes, and its delays start again.
            VistaRequest.HIDE -> synchronized(this) {
                vistaPolicy.interaction(now())
                if (!previewOpen) vistaClock.real()
            }
            null -> Unit
        }
        if (LaunchExtras.hasStoredExtras(extras)) {
            scope.launch {
                val current = settingsStore.settings.first()
                settingsStore.update({ LaunchExtras.applyTo(it, extras) }, current)
            }
        }
        reconcileSource()
        recompute()
    }

    fun requestSetup(show: Boolean) {
        machine.requestSetup(show)
        recompute()
    }

    // --- the Fensterblick ---------------------------------------------------------------------

    /** Whether the Fensterblick has the screen now (the keys end it). */
    val vistaShowing: Boolean get() = _ui.value.vista != null

    val previewShowing: Boolean get() = previewOpen

    /**
     * A key was pressed: the Fensterblick (if it shows) goes, and its delays start again.
     * Returns whether it was showing – then the key has done its work.
     */
    @Synchronized
    fun interaction(): Boolean {
        val was = _ui.value.vista != null && !previewOpen
        vistaPolicy.interaction(now())
        if (vistaClock.preview.timelapse && !previewOpen) vistaClock.real()
        if (was) recompute()
        return was
    }

    /** The Fensterblick now, whatever plays (the held key, a link); [timelapse] lets a day pass in two minutes. */
    @Synchronized
    fun showVista(timelapse: Boolean = false) {
        vistaPolicy.request(true)
        if (timelapse) vistaClock.set(VistaClock.Preview(timelapse = true)) else if (!previewOpen) vistaClock.real()
        recompute()
    }

    /** Opens the preview: the Fensterblick at the real moment, ready to be turned. */
    @Synchronized
    fun openPreview() {
        menuCursor = null
        previewOpen = true
        previewTouchedAt = now()
        val at = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now()), java.time.ZoneId.systemDefault())
        vistaClock.set(VistaClock.Preview(minuteOfDay = at.hour * 60 + at.minute, dayOfYear = at.dayOfYear - 1))
        recompute()
    }

    /** Closes the preview: the picture glides back to the real moment – and, from the remote, the menu comes back where it was. */
    @Synchronized
    fun closePreview(toMenu: Boolean = false) {
        if (!previewOpen) return
        previewOpen = false
        vistaClock.real()
        vistaPolicy.interaction(now())
        if (toMenu) {
            menuCursor = SettingsMenu.entries.indexOf(MenuEntry.VISTA_PREVIEW).coerceAtLeast(0)
            menuTouchedAt = now()
        }
        recompute()
    }

    /** Preview: an hour earlier (−1) or later (+1) – the first step lands on a full hour. */
    @Synchronized
    fun previewHour(direction: Int) {
        previewTouchedAt = now()
        val p = vistaClock.preview
        val m = p.minuteOfDay ?: 12 * 60
        val next = when {
            m % 60 == 0 -> m + 60 * direction
            direction > 0 -> (m / 60 + 1) * 60
            else -> m / 60 * 60
        }
        vistaClock.set(p.copy(minuteOfDay = Math.floorMod(next, 24 * 60), timelapse = false))
        recompute()
    }

    /** Preview: the same day a month earlier (−1) or later (+1). */
    @Synchronized
    fun previewMonth(direction: Int) {
        previewTouchedAt = now()
        val p = vistaClock.preview
        val year = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).year
        val d = java.time.LocalDate.ofYearDay(year, ((p.dayOfYear ?: 171) + 1).coerceIn(1, 365)).plusMonths(direction.toLong())
        vistaClock.set(p.copy(dayOfYear = d.withYear(year).dayOfYear - 1))
        recompute()
    }

    /** Preview: the next sky – the weather's, clear, a few clouds, overcast. */
    @Synchronized
    fun previewSky() {
        previewTouchedAt = now()
        val p = vistaClock.preview
        val next = when (p.cover) {
            null -> 0f
            0f -> 0.45f
            0.45f -> 1f
            else -> null
        }
        vistaClock.set(p.copy(cover = next))
        recompute()
    }

    /** Preview: a day in two minutes, or back to the hour it shows. */
    @Synchronized
    fun previewTimelapse() {
        previewTouchedAt = now()
        val p = vistaClock.preview
        if (p.timelapse) {
            val at = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(vistaClock.sky()), java.time.ZoneId.systemDefault())
            vistaClock.set(p.copy(timelapse = false, minuteOfDay = at.hour * 60 + at.minute))
        } else {
            vistaClock.set(p.copy(timelapse = true))
        }
        recompute()
    }

    fun networkAvailable() {
        (source as? HaSource)?.networkAvailable()
    }

    fun send(command: RemoteCommand) {
        val volume = command == RemoteCommand.VOLUME_UP || command == RemoteCommand.VOLUME_DOWN
        val allowed = if (volume) settings.value.volumeKeys else settings.value.remoteControl
        if (!allowed) return
        // Back to the item before: that return is wanted at once.
        if (command == RemoteCommand.PREVIOUS) synchronized(this) { flips.expectReturn(now()) }
        if (volume) {
            // Answer the key at once; the new value follows from Home Assistant.
            volumeFlashUntil = now() + VOLUME_FLASH_MS
            recompute()
        }
        scope.launch { source?.send(command) }
    }

    // --- spoken word: jumps and chapters ---------------------------------------------------

    /** Left and right jump within what plays (a book, an episode) instead of changing it. */
    val spokenKeys: Boolean
        get() {
            val s = settings.value
            return s.remoteControl && s.spokenSkip && (_ui.value.screen as? Screen.Stage)?.track?.kind?.spoken == true
        }

    /**
     * Jumps back ([direction] −1, [BACK_MS]) or ahead (+1, [AHEAD_MS]); presses in quick
     * succession add up to one jump, which the bar shows at once.
     */
    fun skip(direction: Int) {
        if (!settings.value.remoteControl) return
        val track = (_ui.value.screen as? Screen.Stage)?.track ?: return
        val t = now()
        val from = contentPosition(t) ?: return
        seekTo(track, from + if (direction < 0) -BACK_MS else AHEAD_MS, t)
    }

    /**
     * Held left or right: to the chapter's start (or the one before, right after a start) or to
     * the next chapter – without chapters, or past the last, to the item before or after.
     */
    fun jump(direction: Int) {
        if (!settings.value.remoteControl) return
        val track = (_ui.value.screen as? Screen.Stage)?.track ?: return
        val t = now()
        val at = contentPosition(t)?.let { Chapters.jump(track.chapters, it, direction) }
        if (at != null) seekTo(track, at, t) else send(if (direction < 0) RemoteCommand.PREVIOUS else RemoteCommand.NEXT)
    }

    /** Where [track] plays now in its own time: a jump not yet confirmed, else the reported position. */
    private fun contentPosition(t: Long): Long? {
        seekOverride?.let { return it.at(t) }
        val s = _ui.value.source
        return s.player?.let { Progress.positionAt(it, t, s.skewMs, s.receivedAtMs) }
    }

    private fun seekTo(track: TrackInfo, target: Long, t: Long) {
        val end = track.durationMs?.let { (it - END_MARGIN_MS).coerceAtLeast(0) } ?: return
        val p = _ui.value.source.player
        val o = SeekOverride(track.key, target.coerceIn(0, end), t, if (p?.status == PlayerStatus.PLAYING) p.speed else 0f)
        seekOverride = o
        seekJob?.cancel()
        // Presses close together make one jump: it is sent once they stop.
        seekJob = scope.launch {
            delay(SEEK_SETTLE_MS)
            seekOverride = o.copy(sentAt = now())
            source?.seek(o.at(now()))
            // Home Assistant answers within moments; if not, the reported position takes over again.
            delay(SEEK_TRUST_MS)
            if (seekOverride?.startedAt == o.startedAt) seekOverride = null
        }
        recompute()
    }

    /** A jump not yet confirmed: from [target] at [startedAt] it runs on at [speed] (0 while paused). */
    private data class SeekOverride(val key: String, val target: Long, val startedAt: Long, val speed: Float, val sentAt: Long? = null) {
        fun at(t: Long): Long = target + ((t - startedAt).coerceAtLeast(0) * speed).toLong()
    }

    // --- sleep timer ---------------------------------------------------------------------

    /** Steps the sleep timer along the choices that fit what plays. */
    @Synchronized
    private fun stepSleep(direction: Int, wrap: Boolean) {
        val track = (_ui.value.screen as? Screen.Stage)?.track
        val choices = sleepTimer.choices(track)
        val i = choices.indexOf(sleepTimer.choice).coerceAtLeast(0) + direction
        val next = if (wrap) choices[Math.floorMod(i, choices.size)] else choices[i.coerceIn(0, choices.size - 1)]
        if (next != sleepTimer.choice) sleepTimer.set(next, now())
        recompute()
    }

    /**
     * When the sleep timer pauses, as shown: an estimate for an end shifts by a millisecond from
     * tick to tick (speed, rounding) – only a real change (over a second) is passed on, so the
     * screen is not redrawn for nothing.
     */
    private fun steadySleepAt(at: Long?): Long? {
        val shown = _ui.value.sleepAt
        return if (at != null && shown != null && kotlin.math.abs(at - shown) <= 1_000) shown else at
    }

    /** Wakes the controller when the sleep timer expects to act (a tick could be up to a second late). */
    private fun scheduleSleep(at: Long?) {
        val old = sleepWake
        if (at == old || (at != null && old != null && kotlin.math.abs(at - old) < 250)) return
        sleepJob?.cancel()
        sleepWake = at
        sleepJob = at?.let {
            scope.launch {
                // Never sooner than a moment from now: a time already past must not spin.
                delay((it - now()).coerceAtLeast(MIN_WAKE_MS))
                synchronized(this@NowPlayingController) { sleepWake = null }
                recompute()
            }
        }
    }

    // --- settings menu -------------------------------------------------------------------

    // Keys arrive on the main thread, ticks and data on background threads.
    @Volatile private var menuCursor: Int? = null
    @Volatile private var menuTouchedAt = 0L
    private val localEdits = AtomicInteger()
    private val persistLock = Mutex()

    val menuOpen: Boolean get() = menuCursor != null

    /** Up and down change the speaker's volume. */
    val volumeKeys: Boolean get() = settings.value.volumeKeys

    @Synchronized
    fun openMenu() {
        menuCursor = menuCursor ?: 0
        menuTouchedAt = now()
        recompute()
    }

    @Synchronized
    fun closeMenu() {
        menuCursor = null
        recompute()
    }

    /** Up (−1) or down (+1) through the menu; it stops at its ends. */
    @Synchronized
    fun menuMove(delta: Int) {
        val c = menuCursor ?: return
        menuCursor = (c + delta).coerceIn(0, SettingsMenu.entries.size - 1)
        menuTouchedAt = now()
        recompute()
    }

    /**
     * Changes the line under the cursor: left (−1) or right (+1) along its scale, OK goes
     * round ([wrap]). The connection line opens the setup code.
     */
    fun menuChange(direction: Int, wrap: Boolean) {
        val c = menuCursor ?: return
        menuTouchedAt = now()
        val entry = SettingsMenu.entries[c]
        when {
            // The connections – and the light bars while none are set up – are set up on the phone.
            entry == MenuEntry.CONNECTION || entry == MenuEntry.MUSIC_ASSISTANT || (entry == MenuEntry.BARS && !settings.value.bars.isSet) -> {
                menuCursor = null
                requestSetup(true)
            }
            // The sleep timer is no setting: it lives until it has paused the music once.
            entry == MenuEntry.SLEEP_TIMER -> stepSleep(direction, wrap)
            entry == MenuEntry.VISTA_PREVIEW -> openPreview()
            else -> {
                val updated = SettingsMenu.step(entry, settings.value, direction, wrap) ?: return
                localEdits.incrementAndGet()
                onSettings(updated)
                scope.launch {
                    try {
                        // Always the newest state: quick presses may overtake each other.
                        persistLock.withLock { settingsStore.update({ settings.value }, settings.value) }
                    } finally {
                        localEdits.decrementAndGet()
                    }
                }
            }
        }
    }

    @Synchronized
    private fun onSettings(s: Settings) {
        val view = s.view
        if (view != settings.value.view || _ui.value.view != view) {
            // Another view needs other scenes; the strip starts afresh.
            scenes.frame = scenes.frame.copy(view = view)
            strip.clear()
            _ui.value = _ui.value.copy(view = view, stage = null, flow = null)
        }
        settings.value = s
        val timing = timingFor(s)
        if (timing != machine.timing) {
            val setup = machine.isSetupRequested
            machine = ScreenStateMachine(timing).also { it.requestSetup(setup) }
        }
        reconcileSource()
        recompute()
    }

    private fun menuState(s: Settings, appearance: Appearance, track: TrackInfo?): MenuState? {
        val cursor = menuCursor ?: return null
        val ctx = MenuContext(
            link = _ui.value.source.link,
            appearance = appearance,
            ma = _ui.value.source.ma,
            sleep = sleepTimer.choice,
            sleepAt = sleepTimer.pausesAt,
            kind = track?.kind,
        )
        return MenuState(SettingsMenu.rows(s, ctx), cursor)
    }

    /** Smoothed playback position for the current track, for the UI's draw phase. */
    fun positionNow(): Long? {
        val t = now()
        return smooth.display(contentPosition(t), t)
    }

    @Synchronized
    private fun reconcileSource() {
        val s = settings.value
        val key = if (s.isConfigured) listOf("ha", s.haUrl, s.haToken, s.entityId, s.sunEntity, s.maUrl, s.maToken, s.weatherEntities).joinToString("|") else "unconfigured"
        if (key == sourceKey || viewers == 0) return
        sourceKey = key
        sourceJob?.cancel()
        // A report held back belongs to the source that sent it.
        heldJob?.cancel()
        heldJob = null
        flips = FlipGuard()
        stopSource(source)
        // Another speaker (or another server) has its own history.
        strip.clear()
        val ma = if (s.hasMusicAssistant) MaClient(http, s.maUrl!!, s.maToken!!) else null
        val newSource: NowPlayingSource? =
            if (s.isConfigured) HaSource(http, scope, HaConfig(s.haUrl!!, s.haToken!!), s.entityId!!, s.sunEntity, now, ma, s.weatherEntities) else null
        source = newSource
        if (newSource == null) {
            _ui.value = _ui.value.copy(source = SourceState(LinkState.UNCONFIGURED))
            return
        }
        newSource.start()
        lightSync.attach(newSource as? LightBridge)
        lastVolume = null
        sourceJob = scope.launch {
            newSource.state.collect { st -> onSource(st) }
        }
    }

    /** A new state from the source – written under the same lock as [recompute], so neither loses the other's update. */
    @Synchronized
    private fun onSource(st: SourceState) {
        val p = st.player
        val t = now()
        // A return to the item just left is usually the player catching up after a skip: it
        // waits a moment, and is shown only if it is still what plays then.
        heldJob?.cancel()
        heldJob = null
        if (!flips.admit(p?.track?.key, t)) {
            val at = flips.passesAt ?: return
            heldJob = scope.launch {
                delay((at - now()).coerceAtLeast(0))
                onSource(st)
            }
            return
        }
        val reportKey = Triple(p?.track?.key, p?.positionMs, p?.status)
        if (reportKey != lastReportKey) {
            lastReportKey = reportKey
            val raw = p?.let { Progress.positionAt(it, t, st.skewMs, st.receivedAtMs) }
            // A jump with the remote holds until Home Assistant reports the new place (or another item).
            seekOverride?.let { o ->
                val confirmed = o.sentAt != null && raw != null && kotlin.math.abs(raw - o.at(t)) < SEEK_MATCH_MS
                if (p?.track?.key != o.key || confirmed) seekOverride = null
            }
            smooth.onReport(p?.track?.key, seekOverride?.at(t) ?: raw, t)
        }
        // A new volume – set here, on a phone or anywhere else – is shown for a moment.
        val volume = p?.volume?.let { it to p.muted }
        if (volume != null) {
            if (lastVolume != null && volume != lastVolume) volumeFlashUntil = t + VOLUME_FLASH_MS
            lastVolume = volume
        }
        _ui.value = _ui.value.copy(source = st)
        recompute()
    }

    private var flips = FlipGuard()
    private var heldJob: Job? = null

    private var lastVolume: Pair<Float, Boolean>? = null
    @Volatile private var volumeFlashUntil = 0L

    /**
     * Stops [old] – once the light bars it was lighting have been given back through it (a few
     * seconds at most): the connection must still be there for that.
     */
    private fun stopSource(old: NowPlayingSource?) {
        if (old == null) return
        val releasing = lightSync.release()
        lightSync.attach(null)
        scope.launch {
            withTimeoutOrNull(LIGHTS_RELEASE_MS) { releasing.join() }
            old.stop()
        }
    }

    /** With the Fensterblick on when paused, a paused cover waits for it instead of turning into the clock. */
    private fun timingFor(s: Settings) = Timing(
        sleepAfterMs = s.sleepAfterMinutes * 60_000L,
        pauseToRestMs = if (s.vistaPauseSeconds > 0) Long.MAX_VALUE / 4 else Timing().pauseToRestMs,
    )

    @Synchronized
    private fun recompute() {
        val s = settings.value
        val src = _ui.value.source
        val t = now()
        // A menu nobody uses closes by itself – it must not stand still on the OLED; so does a
        // preview left alone (long enough to watch a day go by in it).
        if (menuCursor != null && t - menuTouchedAt > MENU_IDLE_MS) menuCursor = null
        if (previewOpen && t - previewTouchedAt > PREVIEW_IDLE_MS) {
            previewOpen = false
            vistaClock.real()
            vistaPolicy.interaction(t)
        }
        val decision: ScreenDecision = machine.decide(src, t)
        val lighting = lightingFor(s, src)
        val appearance = appearanceFor(s, src)
        val look = Look(lighting, appearance, s.light.factor, coverField = s.background == BackgroundMode.COVER)
        val stage = decision.screen as? Screen.Stage
        val playing = stage?.status == PlayerStatus.PLAYING && !stage.linkDown
        val speed = src.player?.speed ?: 1f
        // Spoken word: the chapter that plays, and the moment the next one begins.
        val position = stage?.let { contentPosition(t) }
        val chapter = stage?.let { ChapterPlace.at(it.track, position) }
        // (A chapter that ends before its item's end and is already over waits for nothing.)
        chapterEndsAt = if (chapter != null && position != null && playing && chapter.endMs > position) {
            t + ((chapter.endMs - position) / speed.coerceAtLeast(0.25f)).toLong()
        } else {
            null
        }
        // The sleep timer: it may want the music paused right now.
        if (sleepTimer.evaluate(t, stage?.track, position, speed, playing)) scope.launch { source?.send(RemoteCommand.PAUSE) }
        scheduleSleep(sleepTimer.wakeAt)
        val previous = _ui.value
        val menu = menuState(s, appearance, stage?.track)
        // The Fensterblick waits while the menu is open (the preview is the menu's own).
        val quietLongEnough = vistaPolicy.shows(decision.screen, t, s.vistaPauseSeconds * 1000L, s.vistaPlayingSeconds * 1000L)
        val vista = if (previewOpen || (quietLongEnough && menu == null)) {
            VistaUi(
                track = stage?.track,
                status = stage?.status,
                miniPlayer = s.miniPlayer,
                preview = vistaClock.preview.takeIf { previewOpen },
            )
        } else {
            null
        }
        _ui.value = previous.copy(
            screen = decision.screen,
            keepScreenOn = decision.keepScreenOn || menu != null,
            asleep = decision.asleep && menu == null,
            sleepAction = s.sleepAction,
            lighting = lighting,
            appearance = appearance,
            display = s.display,
            session = session.value,
            entityId = s.entityId,
            menu = menu,
            volumeFlash = s.volumeDisplay == VolumeDisplay.ON_CHANGE && t < volumeFlashUntil,
            chapter = chapter,
            sleepAt = steadySleepAt(sleepTimer.pausesAt),
            vista = vista,
            vistaAhead = s.miniPlayer.takeIf { s.vistaPauseSeconds > 0 || s.vistaPlayingSeconds > 0 },
        )
        // The colours move only while music plays – on the screen and in the light bars alike.
        fieldClock.run(if (playing) s.motion.speed else 0f)
        lightSync.update(barTarget(s, stage, playing, lighting))
        if (stage != null) {
            if (s.view == StageView.FLOW) showFlow(stage, look) else showStage(stage, look)
        }
        // The setup page is only reachable while the setup screen is visible.
        if (decision.screen is Screen.Setup && viewers > 0) setupServer.start() else setupServer.stop()
    }

    /** What the light bars are to show: the shown scene's colours while something is on stage. */
    private fun barTarget(s: Settings, stage: Screen.Stage?, playing: Boolean, lighting: Lighting): LightSync.Target? {
        if (stage == null || !s.bars.isSet) return null
        val shown = if (s.view == StageView.FLOW) _ui.value.flow?.middle?.stage else _ui.value.stage
        val field = shown?.scene?.field ?: return null
        return LightSync.Target(
            bars = s.bars,
            level = s.barLevel,
            field = field,
            song = shown.track.key,
            playing = playing,
            night = ((1f - lighting.brightness) / (1f - Lighting.NIGHT.brightness)).coerceIn(0f, 1f),
            warmth = lighting.warmth,
            motion = s.motion.speed,
            fieldTime = fieldClock::now,
        )
    }

    private fun lightingFor(s: Settings, src: SourceState): Lighting {
        if (!s.dayNight) return Lighting.DAY
        src.sunElevation?.let { return Lighting.fromSunElevation(it) }
        val c = Calendar.getInstance().apply { timeInMillis = now() }
        return Lighting.fromLocalTime(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /**
     * The wall follows the setting – or, automatically, the sun: Home Assistant's elevation,
     * else the local clock. The last decision holds it steady near the horizon.
     */
    private var lastAppearance: Appearance? = null

    private fun appearanceFor(s: Settings, src: SourceState): Appearance {
        val a = when (s.appearance) {
            AppearanceMode.LIGHT -> Appearance.LIGHT
            AppearanceMode.DARK -> Appearance.DARK
            AppearanceMode.AUTO -> {
                val elevation = src.sunElevation ?: Calendar.getInstance().apply { timeInMillis = now() }
                    .let { Lighting.approximateElevation(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }
                Appearance.bySun(elevation, lastAppearance)
            }
        }
        lastAppearance = a
        return a
    }

    /** The stage shows [stage]'s track as soon as its scene is ready (the next track's is made ahead). */
    private fun showStage(stage: Screen.Stage, look: Look) {
        scenes.retain(setOfNotNull(stage.track.key, stage.next?.key))
        val scene = scenes.ready(stage.track, look)
        if (scene == null) {
            scenes.prepare(stage.track, look) { recompute() }
            return
        }
        val shown = _ui.value.stage
        if (shown?.track != stage.track || shown.scene !== scene) _ui.value = _ui.value.copy(stage = StageData(stage.track, scene))
        stage.next?.let { scenes.prepare(it, look) }
    }

    /**
     * Cover Flow: the strip around [stage]'s track; every card's scene is made (the middle one
     * first, then what comes, then what was), the strip is shown once the middle one is ready –
     * cards still on their way join it as they come. A card whose scene is being re-lit keeps
     * the one it has meanwhile; when the wall changes (light ↔ dark), the whole strip changes
     * at once, as soon as every card it shows is ready.
     */
    private fun showFlow(stage: Screen.Stage, look: Look) {
        val src = _ui.value.source
        // Music Assistant's whole queue if it is set up, else Home Assistant's next track.
        val coming = if (src.queueKnown) src.coming.ifEmpty { listOfNotNull(stage.next) } else null
        val s = strip.update(stage.track, coming, before = src.played)
        scenes.retain(s.cards.map { it.track.key }.toSet())
        val order = listOf(s.current) + (s.current + 1 until s.cards.size) + (s.current - 1 downTo 0)
        for (i in order) {
            val track = s.cards[i].track
            if (scenes.ready(track, look) == null) scenes.prepare(track, look) { recompute() }
        }
        val middle = scenes.ready(s.middle.track, look) ?: return
        val shown = _ui.value.flow?.cards.orEmpty().associateBy { it.id }
        val newWall = shown.values.any { it.scene.appearance != look.appearance }
        if (newWall && s.cards.any { it.id in shown && scenes.ready(it.track, look) == null && scenes.pending(it.track) }) return
        val entries = s.cards.mapNotNull { c ->
            val scene = scenes.ready(c.track, look) ?: shown[c.id]?.scene?.takeIf { !newWall && it.layout == middle.layout }
            scene?.let { FlowEntry(c.id, c.track, it) }
        }
        val flow = FlowData(entries, entries.indexOfFirst { it.id == s.middle.id })
        if (flow != _ui.value.flow) _ui.value = _ui.value.copy(flow = flow)
    }

    companion object {
        /** How long a new volume stays in the status line. */
        const val VOLUME_FLASH_MS = 3_500L

        /** An untouched menu closes after this long. */
        const val MENU_IDLE_MS = 60_000L

        /** The preview closes itself after this long without a key (two whole time-lapse days and more). */
        const val PREVIEW_IDLE_MS = 10 * 60_000L

        /** Spoken word: left jumps back this far, right ahead this far (like most podcast apps). */
        const val BACK_MS = 15_000L
        const val AHEAD_MS = 30_000L

        /** A jump never lands closer to the end than this (Music Assistant refuses one past it). */
        const val END_MARGIN_MS = 2_000L

        /** Presses closer together than this add up to one jump. */
        const val SEEK_SETTLE_MS = 500L

        /** How long a jump is shown before the reported position takes over again, confirmed or not. */
        const val SEEK_TRUST_MS = 6_000L

        /** A reported position this close to the jump's confirms it. */
        const val SEEK_MATCH_MS = 4_000L

        /** A stop shorter than this is a hand-over between the app and the screensaver. */
        const val HANDOVER_MS = 10_000L

        /** How long a closing connection waits for the light bars to be given back. */
        const val LIGHTS_RELEASE_MS = 4_000L

        /** The sleep timer's wake-up is never sooner than this from now. */
        const val MIN_WAKE_MS = 250L
    }
}
