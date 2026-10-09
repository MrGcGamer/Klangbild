package io.github.hazymorning.klangbild.app

import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.AppearanceMode
import io.github.hazymorning.klangbild.config.DisplayOptions
import io.github.hazymorning.klangbild.config.LaunchExtras
import io.github.hazymorning.klangbild.config.MenuContext
import io.github.hazymorning.klangbild.config.MenuEntry
import io.github.hazymorning.klangbild.config.MenuRow
import io.github.hazymorning.klangbild.config.SessionOptions
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.config.SettingsMenu
import io.github.hazymorning.klangbild.config.SettingsStore
import io.github.hazymorning.klangbild.config.SleepAction
import io.github.hazymorning.klangbild.config.VolumeDisplay
import io.github.hazymorning.klangbild.data.ArtworkLoader
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.NowPlayingSource
import io.github.hazymorning.klangbild.data.RemoteCommand
import io.github.hazymorning.klangbild.data.SourceState
import io.github.hazymorning.klangbild.data.ha.HaConfig
import io.github.hazymorning.klangbild.data.ha.HaSource
import io.github.hazymorning.klangbild.data.ma.MaClient
import io.github.hazymorning.klangbild.domain.CoverStrip
import io.github.hazymorning.klangbild.domain.Progress
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.ScreenDecision
import io.github.hazymorning.klangbild.domain.ScreenStateMachine
import io.github.hazymorning.klangbild.domain.SmoothedPosition
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.domain.Timing
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupHandler
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
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
    fun attach(spec: ScreenSpec) {
        viewers++
        scenes.frame = scenes.frame.copy(screen = spec)
        if (viewers == 1) start()
    }

    fun detach() {
        viewers = (viewers - 1).coerceAtLeast(0)
        if (viewers == 0) stop()
    }

    private fun start() {
        settingsJob = scope.launch {
            settingsStore.settings.collect { s ->
                // While the menu's own changes are being written, it already shows them.
                if (localEdits.get() == 0) onSettings(s)
            }
        }
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                recompute()
            }
        }
        setupJob = scope.launch {
            setupServer.info.collect { info -> _ui.value = _ui.value.copy(setup = info) }
        }
    }

    private fun stop() {
        settingsJob?.cancel()
        ticker?.cancel()
        setupJob?.cancel()
        setupServer.stop()
        sourceJob?.cancel()
        source?.stop()
        source = null
        sourceKey = null
    }

    /** Extras from `am start` (cold start or onNewIntent). */
    fun applyLaunch(extras: Map<String, Any?>) {
        session.value = LaunchExtras.session(extras)
        machine.requestSetup(session.value.showSetup)
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

    fun networkAvailable() {
        (source as? HaSource)?.networkAvailable()
    }

    fun send(command: RemoteCommand) {
        val volume = command == RemoteCommand.VOLUME_UP || command == RemoteCommand.VOLUME_DOWN
        val allowed = if (volume) settings.value.volumeKeys else settings.value.remoteControl
        if (!allowed) return
        if (volume) {
            // Answer the key at once; the new value follows from Home Assistant.
            volumeFlashUntil = now() + VOLUME_FLASH_MS
            recompute()
        }
        scope.launch { source?.send(command) }
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
        when (val entry = SettingsMenu.entries[c]) {
            MenuEntry.CONNECTION, MenuEntry.MUSIC_ASSISTANT -> {
                menuCursor = null
                requestSetup(true)
            }
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

    private fun menuState(s: Settings, appearance: Appearance): MenuState? {
        val cursor = menuCursor ?: return null
        val ctx = MenuContext(link = _ui.value.source.link, appearance = appearance, ma = _ui.value.source.ma)
        return MenuState(SettingsMenu.rows(s, ctx), cursor)
    }

    /** Smoothed playback position for the current track, for the UI's draw phase. */
    fun positionNow(): Long? {
        val s = _ui.value.source
        val p = s.player ?: return null
        val t = now()
        val raw = Progress.positionAt(p, t, s.skewMs, s.receivedAtMs)
        return smooth.display(raw, t)
    }

    @Synchronized
    private fun reconcileSource() {
        val s = settings.value
        val key = if (s.isConfigured) listOf("ha", s.haUrl, s.haToken, s.entityId, s.sunEntity, s.maUrl, s.maToken).joinToString("|") else "unconfigured"
        if (key == sourceKey || viewers == 0) return
        sourceKey = key
        sourceJob?.cancel()
        source?.stop()
        // Another speaker (or another server) has its own history.
        strip.clear()
        val ma = if (s.hasMusicAssistant) MaClient(http, s.maUrl!!, s.maToken!!) else null
        val newSource: NowPlayingSource? =
            if (s.isConfigured) HaSource(http, scope, HaConfig(s.haUrl!!, s.haToken!!), s.entityId!!, s.sunEntity, now, ma) else null
        source = newSource
        if (newSource == null) {
            _ui.value = _ui.value.copy(source = SourceState(LinkState.UNCONFIGURED))
            return
        }
        newSource.start()
        lastVolume = null
        sourceJob = scope.launch {
            newSource.state.collect { st ->
                val p = st.player
                val reportKey = Triple(p?.track?.key, p?.positionMs, p?.status)
                if (reportKey != lastReportKey) {
                    lastReportKey = reportKey
                    val raw = p?.let { Progress.positionAt(it, now(), st.skewMs, st.receivedAtMs) }
                    smooth.onReport(p?.track?.key, raw, now())
                }
                // A new volume – set here, on a phone or anywhere else – is shown for a moment.
                val volume = p?.volume?.let { it to p.muted }
                if (volume != null) {
                    if (lastVolume != null && volume != lastVolume) volumeFlashUntil = now() + VOLUME_FLASH_MS
                    lastVolume = volume
                }
                _ui.value = _ui.value.copy(source = st)
                recompute()
            }
        }
    }

    private var lastVolume: Pair<Float, Boolean>? = null
    @Volatile private var volumeFlashUntil = 0L

    private fun timingFor(s: Settings) = Timing(sleepAfterMs = s.sleepAfterMinutes * 60_000L)

    @Synchronized
    private fun recompute() {
        val s = settings.value
        val src = _ui.value.source
        val t = now()
        // A menu nobody uses closes by itself – it must not stand still on the OLED.
        if (menuCursor != null && t - menuTouchedAt > MENU_IDLE_MS) menuCursor = null
        val decision: ScreenDecision = machine.decide(src, t)
        val lighting = lightingFor(s, src)
        val appearance = appearanceFor(s, src)
        val look = Look(lighting, appearance, s.light.factor)
        val previous = _ui.value
        val menu = menuState(s, appearance)
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
        )
        val stage = decision.screen as? Screen.Stage
        if (stage != null) {
            if (s.view == StageView.FLOW) showFlow(stage, look) else showStage(stage, look)
        }
        // The setup page is only reachable while the setup screen is visible.
        if (decision.screen is Screen.Setup && viewers > 0) setupServer.start() else setupServer.stop()
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
    }
}
