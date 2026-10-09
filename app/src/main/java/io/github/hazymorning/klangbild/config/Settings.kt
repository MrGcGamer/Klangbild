package io.github.hazymorning.klangbild.config

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.hazymorning.klangbild.data.ha.HaUrls
import io.github.hazymorning.klangbild.domain.StageView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What to do once the sleep timeout has passed without music. */
enum class SleepAction {
    /** Clear "keep screen on": the TV's own screensaver and sleep timer take over. */
    RELEASE,
    /** Close the app (back to the Google TV home screen). */
    CLOSE,
}

/** When the speaker's volume appears on the stage. */
enum class VolumeDisplay {
    /** For a few seconds after it changed (wherever it was changed). */
    ON_CHANGE,
    /** Always, next to the clock. */
    ALWAYS,
    OFF,
}

/** How strongly the artwork lights the wall around it (a factor on the light's luminance). */
enum class LightLevel(val factor: Float) {
    STRONG(1.45f),
    NORMAL(1f),
    SUBTLE(0.5f),
    OFF(0f),
}

/** Light or dark wall: by the sun, or always the one. */
enum class AppearanceMode {
    /** Light from sunrise to sunset, dark at night. */
    AUTO,
    LIGHT,
    DARK,
}

/** What stands behind the picture. */
enum class BackgroundMode {
    /** The wall: black with the artwork's light on it, or paper with its shadow. */
    WALL,

    /** The cover's own colours, softly blurred, fill the screen. */
    COVER,
}

/** How the colours behind the picture move – on the screen and in the light bars; [speed] scales the drift. */
enum class MotionLevel(val speed: Float) {
    OFF(0f),
    GENTLE(0.3f),
    MEDIUM(0.6f),
    LIVELY(1f),
}

/** How brightly the light bars behind the TV glow, as a share of their full brightness. */
enum class BarLevel(val brightness: Float) {
    OFF(0f),
    SUBTLE(0.1f),
    NORMAL(0.2f),
    STRONG(0.4f),
}

/** The colour lights behind the TV (Home Assistant light entities), from the left; any may be missing. */
data class LightBars(val left: String? = null, val center: String? = null, val right: String? = null) {
    /** Each light once, from the left. */
    val ids: List<String> get() = listOfNotNull(left, center, right).distinct()

    val isSet: Boolean get() = ids.isNotEmpty()
}

/** What the stage shows – the part of the settings the UI needs. */
data class DisplayOptions(
    val clock: Boolean = true,
    /** The progress bar (named after the time line it replaced; stored under that name). */
    val timeLine: Boolean = true,
    /** What comes next, on the artwork's bottom edge. */
    val next: Boolean = true,
    val album: Boolean = true,
    /** "Wohnzimmer + 5" while other speakers play along. */
    val group: Boolean = true,
    val volume: VolumeDisplay = VolumeDisplay.ON_CHANGE,
    val light: LightLevel = LightLevel.NORMAL,
    val background: BackgroundMode = BackgroundMode.WALL,
    val motion: MotionLevel = MotionLevel.OFF,
)

/** Persistent settings. Secrets live only in app-private storage (no backup, see manifest). */
data class Settings(
    val haUrl: String? = null,
    val haToken: String? = null,
    val entityId: String? = null,
    val sunEntity: String = "sun.sun",
    val sleepAfterMinutes: Int = 10,
    val sleepAction: SleepAction = SleepAction.RELEASE,
    val showClock: Boolean = true,
    val remoteControl: Boolean = true,
    /** Light by the time of day: warmer in the evening, dimmer at night – it never hides anything. */
    val dayNight: Boolean = true,
    val showTimeLine: Boolean = true,
    val showNext: Boolean = true,
    val showAlbum: Boolean = true,
    val showGroup: Boolean = true,
    val volumeDisplay: VolumeDisplay = VolumeDisplay.ON_CHANGE,
    val light: LightLevel = LightLevel.NORMAL,
    /** Up and down on the remote change the speaker's volume (instead of showing the exact time). */
    val volumeKeys: Boolean = false,
    val appearance: AppearanceMode = AppearanceMode.AUTO,
    val view: StageView = StageView.SINGLE,
    /** Music Assistant itself (optional): for the whole queue in Cover Flow. */
    val maUrl: String? = null,
    val maToken: String? = null,
    val background: BackgroundMode = BackgroundMode.WALL,
    val motion: MotionLevel = MotionLevel.OFF,
    /** The colour lights behind the TV and how brightly they join in. */
    val bars: LightBars = LightBars(),
    val barLevel: BarLevel = BarLevel.NORMAL,
    /** With an audiobook or a podcast, left and right jump back 15 and ahead 30 seconds. */
    val spokenSkip: Boolean = true,
) {
    val isConfigured: Boolean get() = !haUrl.isNullOrBlank() && !haToken.isNullOrBlank() && !entityId.isNullOrBlank()

    val hasMusicAssistant: Boolean get() = !maUrl.isNullOrBlank() && !maToken.isNullOrBlank()

    /**
     * [haUrl] set to [url]; a token only ever goes to the address it was made for, so a new
     * address without [token] drops the old one.
     */
    fun withHa(url: String, token: String?): Settings = copy(haUrl = url, haToken = token ?: haToken.takeIf { url == haUrl })

    /** The same for Music Assistant; no address clears both. */
    fun withMa(url: String?, token: String?): Settings =
        if (url == null) copy(maUrl = null, maToken = null) else copy(maUrl = url, maToken = token ?: maToken.takeIf { url == maUrl })

    val display: DisplayOptions
        get() = DisplayOptions(
            clock = showClock,
            timeLine = showTimeLine,
            next = showNext,
            album = showAlbum,
            group = showGroup,
            volume = volumeDisplay,
            light = light,
            background = background,
            motion = motion,
        )
}

/** Per-launch options from `am start` extras that are not stored. */
data class SessionOptions(
    val showSetup: Boolean = false,
    val debugOverlay: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val url = stringPreferencesKey("ha_url")
        val token = stringPreferencesKey("ha_token")
        val entity = stringPreferencesKey("entity_id")
        val sun = stringPreferencesKey("sun_entity")
        val sleepMinutes = intPreferencesKey("sleep_minutes")
        val sleepAction = stringPreferencesKey("sleep_action")
        val clock = booleanPreferencesKey("show_clock")
        val remote = booleanPreferencesKey("remote_control")
        val dayNight = booleanPreferencesKey("day_night")
        val timeLine = booleanPreferencesKey("show_time_line")
        val next = booleanPreferencesKey("show_next")
        val album = booleanPreferencesKey("show_album")
        val group = booleanPreferencesKey("show_group")
        val volume = stringPreferencesKey("volume_display")
        val light = stringPreferencesKey("light_level")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val appearance = stringPreferencesKey("appearance")
        val view = stringPreferencesKey("view")
        val maUrl = stringPreferencesKey("ma_url")
        val maToken = stringPreferencesKey("ma_token")
        val background = stringPreferencesKey("background")
        val motion = stringPreferencesKey("motion")
        val barsLeft = stringPreferencesKey("bars_left")
        val barsCenter = stringPreferencesKey("bars_center")
        val barsRight = stringPreferencesKey("bars_right")
        val barLevel = stringPreferencesKey("bar_level")
        val spokenSkip = booleanPreferencesKey("spoken_skip")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            haUrl = p[Keys.url],
            haToken = p[Keys.token],
            entityId = p[Keys.entity],
            sunEntity = p[Keys.sun] ?: "sun.sun",
            sleepAfterMinutes = p[Keys.sleepMinutes] ?: 10,
            sleepAction = p[Keys.sleepAction]?.let { runCatching { SleepAction.valueOf(it) }.getOrNull() } ?: SleepAction.RELEASE,
            showClock = p[Keys.clock] ?: true,
            remoteControl = p[Keys.remote] ?: true,
            dayNight = p[Keys.dayNight] ?: true,
            showTimeLine = p[Keys.timeLine] ?: true,
            showNext = p[Keys.next] ?: true,
            showAlbum = p[Keys.album] ?: true,
            showGroup = p[Keys.group] ?: true,
            volumeDisplay = p[Keys.volume]?.let { runCatching { VolumeDisplay.valueOf(it) }.getOrNull() } ?: VolumeDisplay.ON_CHANGE,
            light = p[Keys.light]?.let { runCatching { LightLevel.valueOf(it) }.getOrNull() } ?: LightLevel.NORMAL,
            volumeKeys = p[Keys.volumeKeys] ?: false,
            appearance = p[Keys.appearance]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.AUTO,
            view = p[Keys.view]?.let { runCatching { StageView.valueOf(it) }.getOrNull() } ?: StageView.SINGLE,
            maUrl = p[Keys.maUrl],
            maToken = p[Keys.maToken],
            background = p[Keys.background]?.let { runCatching { BackgroundMode.valueOf(it) }.getOrNull() } ?: BackgroundMode.WALL,
            motion = p[Keys.motion]?.let { runCatching { MotionLevel.valueOf(it) }.getOrNull() } ?: MotionLevel.OFF,
            bars = LightBars(p[Keys.barsLeft], p[Keys.barsCenter], p[Keys.barsRight]),
            barLevel = p[Keys.barLevel]?.let { runCatching { BarLevel.valueOf(it) }.getOrNull() } ?: BarLevel.NORMAL,
            spokenSkip = p[Keys.spokenSkip] ?: true,
        )
    }

    suspend fun update(transform: (Settings) -> Settings, current: Settings) {
        val s = transform(current)
        context.dataStore.edit { p ->
            fun putOrRemove(key: Preferences.Key<String>, value: String?) {
                if (value.isNullOrBlank()) p.remove(key) else p[key] = value
            }
            putOrRemove(Keys.url, s.haUrl)
            putOrRemove(Keys.token, s.haToken)
            putOrRemove(Keys.entity, s.entityId)
            p[Keys.sun] = s.sunEntity
            p[Keys.sleepMinutes] = s.sleepAfterMinutes
            p[Keys.sleepAction] = s.sleepAction.name
            p[Keys.clock] = s.showClock
            p[Keys.remote] = s.remoteControl
            p[Keys.dayNight] = s.dayNight
            p[Keys.timeLine] = s.showTimeLine
            p[Keys.next] = s.showNext
            p[Keys.album] = s.showAlbum
            p[Keys.group] = s.showGroup
            p[Keys.volume] = s.volumeDisplay.name
            p[Keys.light] = s.light.name
            p[Keys.volumeKeys] = s.volumeKeys
            p[Keys.appearance] = s.appearance.name
            p[Keys.view] = s.view.name
            putOrRemove(Keys.maUrl, s.maUrl)
            putOrRemove(Keys.maToken, s.maToken)
            p[Keys.background] = s.background.name
            p[Keys.motion] = s.motion.name
            putOrRemove(Keys.barsLeft, s.bars.left)
            putOrRemove(Keys.barsCenter, s.bars.center)
            putOrRemove(Keys.barsRight, s.bars.right)
            p[Keys.barLevel] = s.barLevel.name
            p[Keys.spokenSkip] = s.spokenSkip
        }
    }
}

/**
 * `am start` extras. Configuration extras (`ha_url`, `token`, `ma_url`, `ma_token`, `entity`,
 * `sleep_minutes`, `sleep_action`, `clock` and the display options) are stored; `setup` and
 * `debug` only apply to this launch. A new address without its token drops the stored one:
 * no other app on the TV can redirect a token to an address of its choosing.
 *
 * ```
 * adb shell am start -n io.github.hazymorning.klangbild/.MainActivity \
 *     --es entity media_player.wohnzimmer --es light dezent
 * ```
 */
object LaunchExtras {
    const val HA_URL = "ha_url"
    const val TOKEN = "token"
    const val MA_URL = "ma_url"
    const val MA_TOKEN = "ma_token"
    const val ENTITY = "entity"
    const val SLEEP_MINUTES = "sleep_minutes"
    const val SLEEP_ACTION = "sleep_action"
    const val CLOCK = "clock"
    const val TIME_LINE = "time_line"
    const val NEXT = "next"
    const val ALBUM = "album"
    const val GROUP = "group"
    const val VOLUME = "volume"
    const val LIGHT = "light"
    const val VOLUME_KEYS = "volume_keys"
    const val DAY_NIGHT = "day_night"
    const val REMOTE = "remote"
    const val APPEARANCE = "appearance"
    const val VIEW = "view"
    const val BACKGROUND = "background"
    const val MOTION = "motion"
    const val BARS_LEFT = "bars_left"
    const val BARS_CENTER = "bars_center"
    const val BARS_RIGHT = "bars_right"
    const val BAR_LEVEL = "bar_level"
    const val SPOKEN_SKIP = "spoken_skip"
    const val SETUP = "setup"
    const val DEBUG = "debug"

    private val stored = listOf(
        HA_URL, TOKEN, MA_URL, MA_TOKEN, ENTITY, SLEEP_MINUTES, SLEEP_ACTION, CLOCK, TIME_LINE, NEXT, ALBUM, GROUP, VOLUME, LIGHT,
        VOLUME_KEYS, DAY_NIGHT, REMOTE, APPEARANCE, VIEW, BACKGROUND, MOTION, BARS_LEFT, BARS_CENTER, BARS_RIGHT, BAR_LEVEL,
        SPOKEN_SKIP,
    )

    /** Applies stored-setting extras to [s]. Unknown or invalid values are ignored. */
    fun applyTo(s: Settings, extras: Map<String, Any?>): Settings {
        var out = s
        val token = (extras[TOKEN] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val haUrl = (extras[HA_URL] as? String)?.let { HaUrls.normalizeBase(it) }
        if (haUrl != null) out = out.withHa(haUrl, token) else if (token != null) out = out.copy(haToken = token)
        val maToken = (extras[MA_TOKEN] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val maUrl = (extras[MA_URL] as? String)?.let { HaUrls.normalizeBase(it) }
        if (maUrl != null) out = out.withMa(maUrl, maToken) else if (maToken != null && out.maUrl != null) out = out.copy(maToken = maToken)
        (extras[ENTITY] as? String)?.trim()?.takeIf { isEntityId(it) }?.let { out = out.copy(entityId = it) }
        intOf(extras[SLEEP_MINUTES])?.takeIf { it in 1..720 }?.let { out = out.copy(sleepAfterMinutes = it) }
        (extras[SLEEP_ACTION] as? String)?.let { a ->
            when (a.lowercase()) {
                "release", "freigeben" -> out = out.copy(sleepAction = SleepAction.RELEASE)
                "close", "beenden" -> out = out.copy(sleepAction = SleepAction.CLOSE)
            }
        }
        boolOf(extras[CLOCK])?.let { out = out.copy(showClock = it) }
        boolOf(extras[TIME_LINE])?.let { out = out.copy(showTimeLine = it) }
        boolOf(extras[NEXT])?.let { out = out.copy(showNext = it) }
        boolOf(extras[ALBUM])?.let { out = out.copy(showAlbum = it) }
        boolOf(extras[GROUP])?.let { out = out.copy(showGroup = it) }
        boolOf(extras[VOLUME_KEYS])?.let { out = out.copy(volumeKeys = it) }
        boolOf(extras[DAY_NIGHT])?.let { out = out.copy(dayNight = it) }
        boolOf(extras[REMOTE])?.let { out = out.copy(remoteControl = it) }
        (extras[VOLUME] as? String)?.let { v ->
            when (v.trim().lowercase()) {
                "on_change", "change", "aenderung", "änderung", "bei_aenderung", "bei_änderung" -> VolumeDisplay.ON_CHANGE
                "always", "immer" -> VolumeDisplay.ALWAYS
                "off", "aus", "never", "nie" -> VolumeDisplay.OFF
                else -> null
            }?.let { out = out.copy(volumeDisplay = it) }
        }
        (extras[LIGHT] as? String)?.let { v ->
            when (v.trim().lowercase()) {
                "strong", "kraeftig", "kräftig" -> LightLevel.STRONG
                "normal" -> LightLevel.NORMAL
                "subtle", "dezent" -> LightLevel.SUBTLE
                "off", "aus" -> LightLevel.OFF
                else -> null
            }?.let { out = out.copy(light = it) }
        }
        (extras[APPEARANCE] as? String)?.let { v ->
            when (v.trim().lowercase()) {
                "auto", "automatisch" -> AppearanceMode.AUTO
                "light", "hell" -> AppearanceMode.LIGHT
                "dark", "dunkel" -> AppearanceMode.DARK
                else -> null
            }?.let { out = out.copy(appearance = it) }
        }
        (extras[VIEW] as? String)?.let { v ->
            when (v.trim().lowercase().replace(" ", "").replace("_", "")) {
                "single", "einzelbild", "bild" -> StageView.SINGLE
                "flow", "coverflow" -> StageView.FLOW
                else -> null
            }?.let { out = out.copy(view = it) }
        }
        (extras[BACKGROUND] as? String)?.let { v ->
            when (v.trim().lowercase().replace(" ", "").replace("_", "")) {
                "wall", "wand" -> BackgroundMode.WALL
                "cover", "coverfarben", "colours", "colors", "farben" -> BackgroundMode.COVER
                else -> null
            }?.let { out = out.copy(background = it) }
        }
        (extras[MOTION] as? String)?.let { v ->
            when (v.trim().lowercase()) {
                "off", "aus" -> MotionLevel.OFF
                "gentle", "sanft" -> MotionLevel.GENTLE
                "medium", "mittel" -> MotionLevel.MEDIUM
                "lively", "lebhaft" -> MotionLevel.LIVELY
                else -> null
            }?.let { out = out.copy(motion = it) }
        }
        (extras[BAR_LEVEL] as? String)?.let { v ->
            when (v.trim().lowercase()) {
                "off", "aus" -> BarLevel.OFF
                "subtle", "dezent" -> BarLevel.SUBTLE
                "normal" -> BarLevel.NORMAL
                "strong", "kraeftig", "kräftig" -> BarLevel.STRONG
                else -> null
            }?.let { out = out.copy(barLevel = it) }
        }
        // A light given as "" or "none" is removed; anything but a light entity is ignored.
        fun bar(key: String, current: String?): String? = when (val v = (extras[key] as? String)?.trim()) {
            null -> current
            "", "none", "keine" -> null
            else -> v.takeIf { isLightId(it) } ?: current
        }
        if (extras.keys.any { it == BARS_LEFT || it == BARS_CENTER || it == BARS_RIGHT }) {
            out = out.copy(bars = LightBars(bar(BARS_LEFT, out.bars.left), bar(BARS_CENTER, out.bars.center), bar(BARS_RIGHT, out.bars.right)))
        }
        boolOf(extras[SPOKEN_SKIP])?.let { out = out.copy(spokenSkip = it) }
        return out
    }

    fun session(extras: Map<String, Any?>) = SessionOptions(
        showSetup = boolOf(extras[SETUP]) ?: false,
        debugOverlay = boolOf(extras[DEBUG]) ?: false,
    )

    fun hasStoredExtras(extras: Map<String, Any?>) = stored.any { extras.containsKey(it) }

    fun fromIntent(intent: Intent?): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        intent?.extras?.let { b ->
            for (key in b.keySet()) {
                @Suppress("DEPRECATION")
                out[key] = b.get(key)
            }
        }
        intent?.data?.takeIf { it.scheme == DEEP_LINK_SCHEME && it.isHierarchical }?.let { uri ->
            out += fromLink(uri.queryParameterNames.associateWith { uri.getQueryParameter(it) })
        }
        return out
    }

    /** `klangbild://open?light=dezent&album=aus` */
    const val DEEP_LINK_SCHEME = "klangbild"

    /**
     * What a link may set: display options and the sleep time – never the address, token,
     * speaker or the lights to control. Any page in a browser on the TV could open such a link.
     */
    private val linkKeys = setOf(
        CLOCK, TIME_LINE, NEXT, ALBUM, GROUP, VOLUME, LIGHT, VOLUME_KEYS, DAY_NIGHT, REMOTE, SLEEP_MINUTES, SLEEP_ACTION,
        APPEARANCE, VIEW, BACKGROUND, MOTION, BAR_LEVEL, SPOKEN_SKIP,
    )

    /** The query parameters of a `klangbild://` link as extras, unsafe keys dropped. */
    fun fromLink(params: Map<String, String?>): Map<String, Any?> = params.filterKeys { it in linkKeys }

    fun isEntityId(s: String) = Regex("^[a-z_]+\\.[a-z0-9_]+$").matches(s)

    /** A Home Assistant light (`light.wohnzimmer_tv_links`). */
    fun isLightId(s: String) = s.startsWith("light.") && isEntityId(s)

    private fun intOf(v: Any?): Int? = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    private fun boolOf(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> when (v.trim().lowercase()) {
            "true", "1", "yes", "ja", "on", "an" -> true
            "false", "0", "no", "nein", "off", "aus" -> false
            else -> null
        }
        is Int -> v != 0
        else -> null
    }
}
