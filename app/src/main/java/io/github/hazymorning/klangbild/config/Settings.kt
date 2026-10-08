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
) {
    val isConfigured: Boolean get() = !haUrl.isNullOrBlank() && !haToken.isNullOrBlank() && !entityId.isNullOrBlank()
}

/** Per-launch options from `am start` extras that are not stored. */
data class SessionOptions(
    val demo: Boolean = false,
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
        }
    }
}

/**
 * `am start` extras. Configuration extras (`ha_url`, `token`, `entity`, `sleep_minutes`,
 * `sleep_action`, `clock`) are stored; `demo`, `setup` and `debug` only apply to this launch.
 *
 * ```
 * adb shell am start -n io.github.hazymorning.klangbild/.MainActivity \
 *     --es entity media_player.wohnzimmer --ez demo false
 * ```
 */
object LaunchExtras {
    const val HA_URL = "ha_url"
    const val TOKEN = "token"
    const val ENTITY = "entity"
    const val SLEEP_MINUTES = "sleep_minutes"
    const val SLEEP_ACTION = "sleep_action"
    const val CLOCK = "clock"
    const val DEMO = "demo"
    const val SETUP = "setup"
    const val DEBUG = "debug"

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
        return out
    }

    fun session(extras: Map<String, Any?>) = SessionOptions(
        demo = boolOf(extras[DEMO]) ?: false,
        showSetup = boolOf(extras[SETUP]) ?: false,
        debugOverlay = boolOf(extras[DEBUG]) ?: false,
    )

    fun hasStoredExtras(extras: Map<String, Any?>) =
        listOf(HA_URL, TOKEN, ENTITY, SLEEP_MINUTES, SLEEP_ACTION, CLOCK).any { extras.containsKey(it) }

    fun fromIntent(intent: Intent?): Map<String, Any?> {
        val b = intent?.extras ?: return emptyMap()
        return b.keySet().associateWith {
            @Suppress("DEPRECATION")
            b.get(it)
        }
    }

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
            "true", "1", "yes", "ja", "on" -> true
            "false", "0", "no", "nein", "off" -> false
            else -> null
        }
        is Int -> v != 0
        else -> null
    }
}
