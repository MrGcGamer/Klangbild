package io.github.hazymorning.klangbild.app

import io.github.hazymorning.klangbild.color.Lighting
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
import io.github.hazymorning.klangbild.data.demo.DemoSource
import io.github.hazymorning.klangbild.data.ha.HaConfig
import io.github.hazymorning.klangbild.data.ha.HaSource
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.Progress
import io.github.hazymorning.klangbild.domain.Screen
import io.github.hazymorning.klangbild.domain.ScreenDecision
import io.github.hazymorning.klangbild.domain.ScreenStateMachine
import io.github.hazymorning.klangbild.domain.SmoothedPosition
import io.github.hazymorning.klangbild.domain.Timing
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupHandler
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.setup.SetupServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.math.abs

/** A scene that is ready to be shown for a track. */
data class StageData(val track: TrackInfo, val scene: ArtworkScene)

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
    val lighting: Lighting = Lighting.DAY,
    val night: Boolean = false,
    /** What the stage shows (clock, time line, album …) and how strong its light is. */
    val display: DisplayOptions = DisplayOptions(),
    val source: SourceState = SourceState(LinkState.CONNECTING),
    val session: SessionOptions = SessionOptions(),
    val entityId: String? = null,
    val demo: Boolean = false,
    /** Address, code and QR modules while the setup screen is open. */
    val setup: SetupInfo? = null,
    /** The settings menu, null while it is closed. */
    val menu: MenuState? = null,
    /** The volume just changed: the time line shows it for a moment. */
    val volumeFlash: Boolean = false,
)

/**
 * The app's single source of truth: picks the data source (Home Assistant or demo), runs
 * the screen state machine on every update and once a second, prepares scenes (cover,
 * palette, light) off the main thread and pre-builds the next track's scene.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    private var screenSpec = ScreenSpec.TV_1080

    private var source: NowPlayingSource? = null
    private var sourceKey: String? = null
    private var sourceJob: Job? = null
    private var ticker: Job? = null
    private var settingsJob: Job? = null
    private var machine = ScreenStateMachine()
    private val smooth = SmoothedPosition()
    private var lastReportKey: Any? = null

    private val artwork = ArtworkLoader(http, { settings.value.haUrl }, { settings.value.haToken })
    private val sceneDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var sceneJob: Job? = null
    private var buildingKey: String? = null
    private val prepared = LinkedHashMap<String, ArtworkScene>()

    private var viewers = 0

    private val setupHandler = SetupHandler(
        settings = { settings.value },
        discover = { url, token -> withContext(Dispatchers.IO) { SetupServer.discover(http, url, token) } },
        save = { updated ->
            settingsStore.update({ updated }, settings.value)
            // Leaving setup: the new settings start a fresh connection.
            session.value = session.value.copy(demo = false, showSetup = false)
            machine.requestSetup(false)
        },
    )
    private val setupServer = SetupServer(scope, setupHandler)
    private var setupJob: Job? = null

    /** A screen (activity or dream) starts showing the controller. */
    fun attach(spec: ScreenSpec) {
        viewers++
        if (spec != screenSpec) {
            screenSpec = spec
            synchronized(prepared) { prepared.clear() }
        }
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

    fun startDemo() {
        session.value = session.value.copy(demo = true)
        machine.requestSetup(false)
        reconcileSource()
        recompute()
    }

    fun stopDemo() {
        session.value = session.value.copy(demo = false)
        reconcileSource()
        recompute()
    }

    fun networkAvailable() {
        (source as? HaSource)?.networkAvailable()
    }

    fun send(command: RemoteCommand) {
        val volume = command == RemoteCommand.VOLUME_UP || command == RemoteCommand.VOLUME_DOWN
        val allowed = source is DemoSource || if (volume) settings.value.volumeKeys else settings.value.remoteControl
        if (!allowed) return
        if (volume) {
            // Answer the key at once; the new value follows from Home Assistant.
            volumeFlashUntil = now() + VOLUME_FLASH_MS
            recompute()
        }
        scope.launch { source?.send(command) }
    }

    suspend fun saveSettings(transform: (Settings) -> Settings) {
        settingsStore.update(transform, settingsStore.settings.first())
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
     * round ([wrap]). The two action lines open the setup code or start/stop the demo.
     */
    fun menuChange(direction: Int, wrap: Boolean) {
        val c = menuCursor ?: return
        menuTouchedAt = now()
        when (val entry = SettingsMenu.entries[c]) {
            MenuEntry.CONNECTION -> {
                menuCursor = null
                if (source is DemoSource) session.value = session.value.copy(demo = false)
                reconcileSource()
                requestSetup(true)
            }
            MenuEntry.DEMO -> if (source is DemoSource) stopDemo() else startDemo()
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

    private fun onSettings(s: Settings) {
        settings.value = s
        val timing = timingFor(s)
        if (timing != machine.timing) {
            val setup = machine.isSetupRequested
            machine = ScreenStateMachine(timing).also { it.requestSetup(setup) }
        }
        reconcileSource()
        recompute()
    }

    private fun menuState(s: Settings): MenuState? {
        val cursor = menuCursor ?: return null
        val ctx = MenuContext(link = _ui.value.source.link, demo = source is DemoSource)
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

    private fun reconcileSource() {
        val s = settings.value
        val demo = session.value.demo
        val key = when {
            demo -> "demo"
            s.isConfigured -> "ha|${s.haUrl}|${s.haToken}|${s.entityId}|${s.sunEntity}"
            else -> "unconfigured"
        }
        if (key == sourceKey || viewers == 0) return
        sourceKey = key
        sourceJob?.cancel()
        source?.stop()
        val newSource: NowPlayingSource? = when {
            demo -> DemoSource(scope, now)
            s.isConfigured -> HaSource(http, scope, HaConfig(s.haUrl!!, s.haToken!!), s.entityId!!, s.sunEntity, now)
            else -> null
        }
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
        val night = s.dayNight && lighting.brightness < 0.7f
        val previous = _ui.value
        val menu = menuState(s)
        _ui.value = previous.copy(
            screen = decision.screen,
            keepScreenOn = decision.keepScreenOn || menu != null,
            asleep = decision.asleep && menu == null,
            sleepAction = s.sleepAction,
            lighting = lighting,
            night = night,
            display = s.display,
            session = session.value,
            entityId = s.entityId,
            demo = source is DemoSource,
            menu = menu,
            volumeFlash = s.volumeDisplay == VolumeDisplay.ON_CHANGE && t < volumeFlashUntil,
        )
        val stage = decision.screen as? Screen.Stage
        if (stage != null) ensureScene(stage.track, stage.next, lighting)
        relightIfNeeded(lighting)
        // The setup page is only reachable while the setup screen is visible.
        if (decision.screen is Screen.Setup && viewers > 0) setupServer.start() else setupServer.stop()
    }

    private fun lightingFor(s: Settings, src: SourceState): Lighting {
        if (!s.dayNight) return Lighting.DAY
        src.sunElevation?.let { return Lighting.fromSunElevation(it) }
        val c = Calendar.getInstance().apply { timeInMillis = now() }
        return Lighting.fromLocalTime(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun ensureScene(track: TrackInfo, next: TrackInfo?, lighting: Lighting) {
        val current = _ui.value.stage
        if (current?.track?.key == track.key && current.track == track) return
        val cached = synchronized(prepared) { prepared[track.key] }
        if (cached != null && sameLight(cached.lighting, lighting)) {
            _ui.value = _ui.value.copy(stage = StageData(track, cached))
            prefetch(next, lighting)
            return
        }
        if (buildingKey == track.key) return
        buildingKey = track.key
        sceneJob?.cancel()
        sceneJob = scope.launch {
            val scene = try {
                buildScene(track, lighting)
            } catch (e: kotlinx.coroutines.CancellationException) {
                buildingKey = null
                throw e
            } catch (e: Throwable) {
                // Broken image, out of memory …: the generated figure always works.
                withContext(sceneDispatcher) { buildScene(track, lighting, withCover = false) }
            }
            buildingKey = null
            synchronized(prepared) { remember(track.key, scene) }
            val screen = _ui.value.screen
            if (screen is Screen.Stage && screen.track.key == track.key) {
                _ui.value = _ui.value.copy(stage = StageData(track, scene))
            }
            prefetch(next, lighting)
        }
    }

    private fun prefetch(next: TrackInfo?, lighting: Lighting) {
        next ?: return
        if (synchronized(prepared) { prepared.containsKey(next.key) }) return
        scope.launch {
            runCatching { buildScene(next, lighting) }.getOrNull()?.let { scene ->
                synchronized(prepared) { remember(next.key, scene) }
            }
        }
    }

    private suspend fun buildScene(track: TrackInfo, lighting: Lighting, withCover: Boolean = true): ArtworkScene {
        // Wait at most a few seconds for the cover; then the Chladni figure steps in.
        val bitmap = if (!withCover) null else withTimeoutOrNull(COVER_TIMEOUT_MS) { runCatching { artwork.load(track) }.getOrNull() }
        return withContext(sceneDispatcher) {
            SceneBuilder.build(
                key = track.key,
                source = bitmap,
                artSeed = track.artSeed,
                screen = screenSpec,
                lighting = lighting,
                layoutSeed = track.album ?: track.source ?: track.title,
            )
        }
    }

    private fun remember(key: String, scene: ArtworkScene) {
        prepared[key] = scene
        while (prepared.size > 3) prepared.remove(prepared.keys.first())
    }

    private var relighting = false

    private fun relightIfNeeded(lighting: Lighting) {
        val stage = _ui.value.stage ?: return
        if (relighting || sameLight(stage.scene.lighting, lighting)) return
        relighting = true
        scope.launch {
            val scene = withContext(sceneDispatcher) { SceneBuilder.relight(stage.scene, lighting) }
            synchronized(prepared) { remember(stage.track.key, scene) }
            val now = _ui.value.stage
            if (now?.track?.key == stage.track.key) _ui.value = _ui.value.copy(stage = StageData(now.track, scene))
            relighting = false
        }
    }

    private fun sameLight(a: Lighting, b: Lighting) =
        abs(a.brightness - b.brightness) < 0.03f && abs(a.warmth - b.warmth) < 0.05f

    companion object {
        const val COVER_TIMEOUT_MS = 6_000L

        /** How long a new volume stays in the time line. */
        const val VOLUME_FLASH_MS = 3_500L

        /** An untouched menu closes after this long. */
        const val MENU_IDLE_MS = 60_000L

        fun isPlaying(s: Screen) = s is Screen.Stage && s.status == PlayerStatus.PLAYING
    }
}
