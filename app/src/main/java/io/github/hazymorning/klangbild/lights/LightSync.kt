package io.github.hazymorning.klangbild.lights

import io.github.hazymorning.klangbild.config.BarLevel
import io.github.hazymorning.klangbild.config.LightBars
import io.github.hazymorning.klangbild.data.ha.EntityState
import io.github.hazymorning.klangbild.render.FieldModel
import io.github.hazymorning.klangbild.render.FieldScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

/** The colour lights as Home Assistant has them, and the one thing done with them: a light service call. */
interface LightBridge {
    /** The states of the lights asked for with [follow]. */
    val lights: StateFlow<Map<String, EntityState>>

    /** Which lights to keep up with. */
    fun follow(ids: Set<String>)

    /** `light.<service>` for [entityId]; the call's context id, null when it failed. */
    suspend fun callLight(service: String, entityId: String, data: JsonObject?): String?
}

/**
 * The bars behind the TV in the cover's colours. Each bar takes the colour of the field
 * ([FieldModel]) at the screen's edge it stands by – left, top, right – so with "Bewegung" they
 * drift with the screen; without it they change only with the music. Gentle on the Zigbee network and
 * on whoever else uses the lights:
 *
 *  - before it takes the bars over it notes how they were, and gives that back when the
 *    music has ended for a while, when the option is switched off and when the app closes;
 *  - one call per bar at most every [MIN_GAP_MS], the newest wins; nothing is sent for a
 *    change too small to see; a failed call is tried once more;
 *  - a new song is shown once it has played for [SETTLE_MS] (quick skips cost nothing), in a
 *    [TRACK_TRANSITION_MS] fade; drifting colours move in long fades every [DRIFT_MS];
 *  - a pause dims the bars to half, the night to [NIGHT] of the level;
 *  - a bar someone else changes (switched off, set to white, much brighter or darker, another
 *    colour from Home Assistant) is left alone until the next time music starts.
 *
 * All its state lives on one thread; [update] and [release] only hand over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LightSync(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val context: CoroutineContext = Dispatchers.Default.limitedParallelism(1),
) {
    /** What the bars are to show now – set on every tick; null while nothing is on stage. */
    data class Target(
        val bars: LightBars,
        val level: BarLevel,
        /** The current scene's colours (its light texture); the bars follow it. */
        val field: FieldScene,
        /** The song: a new one changes the bars once it has played a moment. */
        val song: String,
        val playing: Boolean,
        /** 0 by day … 1 at night: the bars dim with the evening. */
        val night: Float,
        /** The hour's warmth: greys become a warmer white in the evening. */
        val warmth: Float,
        /** How fast the field moves (0: it stands still). */
        val motion: Float,
        /** The field's time (seconds), shared with the screen. */
        val fieldTime: () -> Double,
    )

    private class Saved(val on: Boolean, val brightness: Int?, val mode: String?, val xy: List<Float>?, val hs: List<Float>?, val kelvin: Int?)

    private class Bar(val id: String, val position: Pair<Float, Float>) {
        var sentXy: Xy? = null
        var sentBri = 0
        var sentAt = 0L
        var transitionMs = 0L
        var inFlight = false
        var retryAt = 0L
        var takenOver = false

        /** The last state change already judged (Home Assistant's time stamp). */
        var checkedUpdate = 0.0
        val contexts = ArrayDeque<String>()
    }

    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var target: Target? = null

    /** The last target there was: when the music has ended the bars keep its colours, dimmed. */
    @Volatile private var lastTarget: Target? = null
    @Volatile private var bridge: LightBridge? = null
    private var loop: Job? = null

    // Confined to [context]:
    private val bars = LinkedHashMap<String, Bar>()

    /** The bars asked for when they were taken over (some may have been unavailable). */
    private var activeFor: Set<String>? = null
    private var saved: Map<String, Saved> = emptyMap()
    private var savedAt = 0L
    private var shownSong: String? = null
    private var shownField: FieldScene? = null
    private var waitingSong: String? = null
    private var waitingSince = 0L
    private var idleSince: Long? = null
    private var pausedSince: Long? = null
    private var shownPaused = false
    private var lastStepAt = 0L
    private var shownShare = 0f
    private val rgb = FloatArray(3)


    /** The Home Assistant link the calls go through (a new one after a new connection). */
    fun attach(bridge: LightBridge?) {
        this.bridge = bridge
        bridge?.follow(followed(target))
        ensureLoop()
        wake.trySend(Unit)
    }

    fun update(target: Target?) {
        val old = this.target
        this.target = target
        if (target != null) lastTarget = target
        if (old?.bars != target?.bars || (old?.level == BarLevel.OFF) != (target?.level == BarLevel.OFF)) bridge?.follow(followed(target))
        ensureLoop()
        if (old?.song != target?.song || old?.playing != target?.playing || old?.level != target?.level || old == null || target == null) wake.trySend(Unit)
    }

    /**
     * Gives the bars back as they were, through the link they were taken over with (the app
     * closes, or another server is set up); the returned job ends when the calls are out.
     */
    fun release(): Job {
        val b = bridge
        target = null
        return scope.launch(context) {
            loop?.cancel()
            loop = null
            restore(b)
        }
    }

    @Synchronized
    private fun ensureLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch(context) {
            while (isActive) {
                step(now())
                withTimeoutOrNull(TICK_MS) { wake.receive() }
            }
        }
    }

    private suspend fun step(t: Long) {
        val b = bridge ?: return
        val tg = target
        val states = b.lights.value
        if (tg == null || tg.level == BarLevel.OFF || !tg.bars.isSet) {
            if (activeFor == null) return
            // The option switched off, or the bars set up anew: given back at once; the music
            // ended: dimmed like a pause, and given back after a while.
            if (tg != null) return restore(b)
            val since = idleSince ?: t.also { idleSince = it }
            if (t - since >= IDLE_RELEASE_MS) return restore(b)
            if (!shownPaused && t - since >= PAUSE_DELAY_MS) lastTarget?.let { send(b, it, t, PAUSE_TRANSITION_MS, force = true, paused = true) }
            return
        }
        idleSince = null
        val wanted = positions(tg.bars)
        // Bars no longer wanted are given back; new ones are noted first.
        if (wanted.keys != activeFor) {
            if (activeFor != null) restore(b)
            activate(wanted, states, t)
        }
        watchForOthers(states, t)
        val paused = !tg.playing
        pausedSince = if (paused) (pausedSince ?: t) else null
        when {
            tg.song != shownSong -> {
                if (tg.song != waitingSong) {
                    waitingSong = tg.song
                    waitingSince = t
                }
                // The first song after taking over is shown at once.
                if (shownSong == null || t - waitingSince >= SETTLE_MS) {
                    shownSong = tg.song
                    shownField = tg.field
                    send(b, tg, t, if (lastStepAt == 0L) FIRST_TRANSITION_MS else TRACK_TRANSITION_MS, force = true, paused = paused)
                }
            }
            paused && !shownPaused && t - (pausedSince ?: t) >= PAUSE_DELAY_MS -> send(b, tg, t, PAUSE_TRANSITION_MS, force = true, paused = true)
            !paused && shownPaused -> send(b, tg, t, RESUME_TRANSITION_MS, force = true, paused = false)
            tg.field !== shownField -> {
                shownField = tg.field
                send(b, tg, t, SOFT_TRANSITION_MS, force = false, paused = shownPaused)
            }
            // A new level or a later hour (a pause is dimmed above, after its delay).
            share(tg, shownPaused) != shownShare -> send(b, tg, t, SOFT_TRANSITION_MS, force = false, paused = shownPaused)
            !paused && tg.motion > 0f && t - lastStepAt >= DRIFT_MS -> send(b, tg, t, DRIFT_TRANSITION_MS, force = false, paused = false)
        }
        // Calls that had to wait (a bar still busy, a failure to try again).
        val due = bars.values.any { !it.takenOver && !it.inFlight && it.retryAt in 1..t }
        if (due) send(b, tg, t, SOFT_TRANSITION_MS, force = true, paused = shownPaused)
    }

    /** Notes how the bars are, then takes them over. Bars that were off are switched on dark first, in the new colour – never a flash of their old one. */
    private suspend fun activate(wanted: Map<String, Pair<Float, Float>>, states: Map<String, EntityState>, t: Long) {
        // Taken back within a minute (the screen came back): the earlier note still holds.
        if (t - savedAt > REUSE_SNAPSHOT_MS || !wanted.keys.all { it in saved }) {
            saved = wanted.keys.mapNotNull { id -> states[id]?.let { id to savedOf(it) } }.toMap()
            savedAt = t
        }
        bars.clear()
        activeFor = wanted.keys.toSet()
        for ((id, pos) in wanted) {
            val s = states[id] ?: continue
            if (s.state == "unavailable") continue
            bars[id] = Bar(id, pos).also { it.checkedUpdate = s.lastUpdated }
        }
        shownSong = null
        waitingSong = null
        shownPaused = false
        lastStepAt = 0L
    }

    /** The colour and brightness for each bar, sent where they differ enough from what it shows. */
    private fun send(b: LightBridge, target: Target, t: Long, transitionMs: Long, force: Boolean, paused: Boolean) {
        // Where the field will stand when the fade has ended.
        val ft = target.fieldTime() + transitionMs / 1000.0 * target.motion
        val share = share(target, paused)
        for (bar in bars.values) {
            if (bar.takenOver) continue
            val state = b.lights.value[bar.id]
            if (state == null || state.state == "unavailable") continue
            FieldModel.light(target.field, bar.position.first, bar.position.second, ft, rgb)
            val xy = HueColor.of(rgb, target.warmth)
            val bri = HueColor.brightness(share * HueColor.dimFor(rgb))
            val small = bar.sentXy?.let { it.distance(xy) < MIN_XY_STEP && abs(bri - bar.sentBri) < MIN_BRI_STEP } == true
            if (small && !force && bar.retryAt == 0L) continue
            if (bar.inFlight || t - bar.sentAt < MIN_GAP_MS) {
                bar.retryAt = maxOf(t + 1, bar.sentAt + MIN_GAP_MS)
                continue
            }
            val wasOff = state.state == "off"
            bar.inFlight = true
            bar.retryAt = 0L
            bar.sentAt = t
            bar.sentXy = xy
            bar.sentBri = bri
            bar.transitionMs = transitionMs + if (wasOff) PRE_ON_MS else 0L
            scope.launch(context) {
                if (wasOff) {
                    // On in the new colour, at the very least – then up.
                    remember(bar, b.callLight("turn_on", bar.id, turnOn(xy, 2, 0)))
                    delay(PRE_ON_MS)
                }
                val ctx = b.callLight("turn_on", bar.id, turnOn(xy, bri, transitionMs))
                remember(bar, ctx)
                bar.inFlight = false
                if (ctx == null && !bar.takenOver) bar.retryAt = now() + RETRY_MS
            }
        }
        lastStepAt = t
        shownPaused = paused
        shownShare = share
    }

    /** The share of full brightness for [tg]: the level, the night, a pause. */
    private fun share(tg: Target, paused: Boolean): Float =
        tg.level.brightness * (1f - (1f - NIGHT) * tg.night.coerceIn(0f, 1f)) * (if (paused) PAUSED else 1f)

    private fun remember(bar: Bar, ctx: String?) {
        if (ctx == null) return
        bar.contexts.addLast(ctx)
        while (bar.contexts.size > 12) bar.contexts.removeFirst()
    }

    /**
     * A bar someone else has changed since it settled – switched off, set to white light, made
     * much brighter or darker, or given another colour from Home Assistant – is theirs again.
     */
    private fun watchForOthers(states: Map<String, EntityState>, t: Long) {
        for (bar in bars.values) {
            if (bar.takenOver) continue
            val s = states[bar.id] ?: continue
            // Only what changed since the bar was taken over (or last judged) counts.
            if (s.lastUpdated <= bar.checkedUpdate || s.state == "unavailable") continue
            val ours = s.context in bar.contexts || s.contextParent in bar.contexts
            if (ours || s.context == null) {
                bar.checkedUpdate = s.lastUpdated
                continue
            }
            // Klangbild never switches a bar off or to white light: whoever did, it is theirs.
            if (s.state == "off" || s.string("color_mode") == "color_temp") {
                bar.takenOver = true
                continue
            }
            // Brightness and colour are judged once the bar has settled (a fade reports steps on the way).
            if (bar.inFlight || t - bar.sentAt < bar.transitionMs + SETTLE_REPORT_MS) continue
            bar.checkedUpdate = s.lastUpdated
            val byPerson = s.contextUser != null || s.contextParent != null
            val bri = (s.attr("brightness") as? JsonPrimitive)?.intOrNull
            val xy = (s.attr("xy_color") as? JsonArray)?.let { a ->
                val x = (a.getOrNull(0) as? JsonPrimitive)?.floatOrNull
                val y = (a.getOrNull(1) as? JsonPrimitive)?.floatOrNull
                if (x != null && y != null) Xy(x, y) else null
            }
            val changed = (bri != null && abs(bri - bar.sentBri) > OTHERS_BRI_STEP) ||
                (xy != null && bar.sentXy != null && xy.distance(bar.sentXy!!) > (if (byPerson) OTHERS_XY_PERSON else OTHERS_XY))
            if (changed) bar.takenOver = true
        }
    }

    /** Gives every bar not taken over back as it was: on in its colour, or off (dimmed first, then off without a fade – a faded off leaves a Hue light at 1 %). */
    private suspend fun restore(b: LightBridge?) {
        val toRestore = bars.values.filter { !it.takenOver }
        bars.clear()
        activeFor = null
        shownSong = null
        idleSince = null
        if (b == null) return
        val offs = ArrayList<String>()
        for (bar in toRestore) {
            val was = saved[bar.id] ?: continue
            if (was.on) {
                b.callLight("turn_on", bar.id, restoreOn(was))
            } else {
                b.callLight("turn_on", bar.id, buildJsonObject { put("brightness", 2); put("transition", OFF_FADE_MS / 1000.0) })
                offs += bar.id
            }
        }
        if (offs.isNotEmpty()) {
            delay(OFF_FADE_MS + 300)
            for (id in offs) b.callLight("turn_off", id, null)
        }
    }

    private fun savedOf(s: EntityState): Saved {
        fun floats(name: String) = (s.attr(name) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }?.takeIf { it.size == 2 }
        return Saved(
            on = s.state == "on",
            brightness = (s.attr("brightness") as? JsonPrimitive)?.doubleOrNull?.toInt(),
            mode = s.string("color_mode"),
            xy = floats("xy_color"),
            hs = floats("hs_color"),
            kelvin = (s.attr("color_temp_kelvin") as? JsonPrimitive)?.doubleOrNull?.toInt(),
        )
    }

    private fun restoreOn(was: Saved) = buildJsonObject {
        was.brightness?.let { put("brightness", it.coerceIn(2, 255)) }
        when {
            was.mode == "color_temp" && was.kelvin != null -> put("color_temp_kelvin", was.kelvin)
            was.mode == "hs" && was.hs != null -> putJsonArray("hs_color") { was.hs.forEach { add(it) } }
            was.xy != null -> putJsonArray("xy_color") { was.xy.forEach { add(it) } }
            was.kelvin != null -> put("color_temp_kelvin", was.kelvin)
        }
        put("transition", RESTORE_TRANSITION_MS / 1000.0)
    }

    private fun turnOn(xy: Xy, brightness: Int, transitionMs: Long) = buildJsonObject {
        putJsonArray("xy_color") {
            add(Math.round(xy.x * 10_000) / 10_000.0)
            add(Math.round(xy.y * 10_000) / 10_000.0)
        }
        put("brightness", brightness)
        put("transition", transitionMs / 1000.0)
    }

    /** The lights to keep up with: the bars, while they are to join in. */
    private fun followed(t: Target?): Set<String> = if (t == null || t.level == BarLevel.OFF) emptySet() else t.bars.ids.toSet()

    companion object {
        /**
         * Where each bar takes its colour from (shares of the screen): the screen's edge next to
         * it – the left and right edges, and for the bar behind the middle the top edge, where
         * its light shows above the TV.
         */
        fun positions(bars: LightBars): Map<String, Pair<Float, Float>> = buildMap {
            bars.left?.let { put(it, 0.02f to 0.5f) }
            bars.center?.let { if (it !in this) put(it, 0.5f to 0.04f) }
            bars.right?.let { if (it !in this) put(it, 0.98f to 0.5f) }
        }

        const val TICK_MS = 1_000L
        const val MIN_GAP_MS = 1_000L
        const val SETTLE_MS = 1_500L
        const val FIRST_TRANSITION_MS = 2_000L
        const val TRACK_TRANSITION_MS = 2_500L
        const val DRIFT_MS = 10_000L
        const val DRIFT_TRANSITION_MS = 8_000L
        const val SOFT_TRANSITION_MS = 1_500L
        const val PAUSE_DELAY_MS = 2_000L
        const val PAUSE_TRANSITION_MS = 2_000L
        const val RESUME_TRANSITION_MS = 1_000L
        const val RESTORE_TRANSITION_MS = 3_000L
        const val OFF_FADE_MS = 2_000L
        const val PRE_ON_MS = 1_000L
        const val RETRY_MS = 5_000L
        const val IDLE_RELEASE_MS = 5 * 60_000L
        const val REUSE_SNAPSHOT_MS = 60_000L
        const val SETTLE_REPORT_MS = 2_000L

        /** A pause dims to half; the night to this share of the level. */
        const val PAUSED = 0.5f
        const val NIGHT = 0.6f

        /** Changes smaller than these are not sent (they would not be seen). */
        const val MIN_XY_STEP = 0.004f
        const val MIN_BRI_STEP = 5

        /** What counts as someone else's change. */
        const val OTHERS_BRI_STEP = 64
        const val OTHERS_XY = 0.08f
        const val OTHERS_XY_PERSON = 0.02f
    }
}
