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
) {
    val isConfigured: Boolean get() = !haUrl.isNullOrBlank() && !haToken.isNullOrBlank() && !entityId.isNullOrBlank()

    val display: DisplayOptions
        get() = DisplayOptions(
            clock = showClock,
            timeLine = showTimeLine,
            next = showNext,
            album = showAlbum,
            group = showGroup,
            volume = volumeDisplay,
            light = light,
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
        }
    }
}

/**
 * `am start` extras. Configuration extras (`ha_url`, `token`, `entity`, `sleep_minutes`,
 * `sleep_action`, `clock` and the display options) are stored; `setup` and `debug` only
 * apply to this launch.
 *
 * ```
 * adb shell am start -n io.github.hazymorning.klangbild/.MainActivity \
 *     --es entity media_player.wohnzimmer --es light dezent
 * ```
 */
object LaunchExtras {
    const val HA_URL = "ha_url"
    const val TOKEN = "token"
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
    const val SETUP = "setup"
    const val DEBUG = "debug"

    private val stored = listOf(
        HA_URL, TOKEN, ENTITY, SLEEP_MINUTES, SLEEP_ACTION, CLOCK, TIME_LINE, NEXT, ALBUM, GROUP, VOLUME, LIGHT,
        VOLUME_KEYS, DAY_NIGHT, REMOTE, APPEARANCE,
    )

    /** Applies stored-setting extras to [s]. Unknown or invalid values are ignored. */
    fun applyTo(s: Settings, extras: Map<String, Any?>): Settings {
        var out = s
        (extras[HA_URL] as? String)?.let { raw -> HaUrls.normalizeBase(raw)?.let { out = out.copy(haUrl = it) } }
        (extras[TOKEN] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { out = out.copy(haToken = it) }
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
     * What a link may set: display options and the sleep time – never the address, token or
     * speaker. Any page in a browser on the TV could open such a link.
     */
    private val linkKeys = setOf(
        CLOCK, TIME_LINE, NEXT, ALBUM, GROUP, VOLUME, LIGHT, VOLUME_KEYS, DAY_NIGHT, REMOTE, SLEEP_MINUTES, SLEEP_ACTION,
        APPEARANCE,
    )

    /** The query parameters of a `klangbild://` link as extras, unsafe keys dropped. */
    fun fromLink(params: Map<String, String?>): Map<String, Any?> = params.filterKeys { it in linkKeys }

    fun isEntityId(s: String) = Regex("^[a-z_]+\\.[a-z0-9_]+$").matches(s)

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
