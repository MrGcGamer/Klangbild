package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import io.github.hazymorning.klangbild.ui.LocalStill
import io.github.hazymorning.klangbild.ui.Motion
import io.github.hazymorning.klangbild.ui.TICK_MS
import io.github.hazymorning.klangbild.ui.rememberOffMain
import io.github.hazymorning.klangbild.vista.CloudField
import io.github.hazymorning.klangbild.vista.CloudPlace
import io.github.hazymorning.klangbild.vista.LabelSide
import io.github.hazymorning.klangbild.vista.PaperBake
import io.github.hazymorning.klangbild.vista.PaperLight
import io.github.hazymorning.klangbild.vista.PaperLook
import io.github.hazymorning.klangbild.vista.Place
import io.github.hazymorning.klangbild.vista.SeasonShapes
import io.github.hazymorning.klangbild.vista.SkyMap
import io.github.hazymorning.klangbild.vista.SkyPlaces
import io.github.hazymorning.klangbild.vista.SkyState
import io.github.hazymorning.klangbild.vista.VistaClock
import io.github.hazymorning.klangbild.vista.World
import kotlinx.coroutines.delay
import java.lang.ref.SoftReference
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** What the Fensterblick needs from the world outside: where it is, the weather's cover, its clock. */
@Stable
class VistaFeed(
    val place: () -> Place,
    /** The weather's cloud cover 0..1, or null when there is no weather entity. */
    val cover: () -> Float?,
    val clock: VistaClock,
    val zone: () -> ZoneId = ZoneId::systemDefault,
    /** The wall clock (ms) for what moves by itself: the clouds, the fire, the stars. */
    val wall: () -> Long = System::currentTimeMillis,
)

/**
 * The Fensterblick: the paper diorama, its light following [feed]'s clock, its clouds and its
 * campfire moving with the wall clock. The picture is baked once for the screen, the season's
 * shapes and the bank cut wide for the [label]; when the season changes the shapes (snow,
 * leaves, blossom – every few days) a new bake fades in over [SEASON_FADE_MS]. [content] lies on
 * top (the music's label), reading the colours of the moment.
 */
@Composable
fun Fensterblick(
    feed: VistaFeed,
    modifier: Modifier = Modifier,
    label: LabelSide = LabelSide.LEFT,
    content: @Composable BoxScope.(look: () -> PaperLook) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val density = LocalDensity.current.density
        val still = LocalStill.current
        val state = remember(feed) { VistaFrameState(feed) }
        val frame = remember { mutableLongStateOf(0L) }
        // The light moves on only every few seconds: what is drawn from it alone waits for that.
        val light = remember { mutableIntStateOf(0) }
        var shapes by remember { mutableStateOf(state.shapesNow()) }
        if (!still) {
            LaunchedEffect(state) {
                while (true) {
                    withFrameNanos { ns ->
                        if (state.advance()) light.intValue++
                        frame.longValue = ns
                    }
                    // The season's shapes are looked at now and then; they change over days.
                    if (state.frames % SHAPES_EVERY == 0L) state.shapesNow().let { if (it != shapes) shapes = it }
                    // Everything here moves slowly: twenty pictures a second are smooth – the
                    // preview's gliding light gets the stage's thirty.
                    delay(if (feed.clock.preview.active) TICK_MS else VISTA_TICK_MS)
                }
            }
        } else {
            state.advance()
        }
        val images = rememberOffMain(w, h, density, shapes, label) {
            if (w > 0 && h > 0) PaperCache.get(w, h, density, shapes, label) else null
        }
        // A new season's bake fades in over the old one.
        var shown by remember { mutableStateOf<PaperImages?>(null) }
        var leaving by remember { mutableStateOf<PaperImages?>(null) }
        val fade = remember { Animatable(1f) }
        // The whole Fensterblick – picture and label – comes up out of the dark once baked.
        val appear = remember { Animatable(if (still) 1f else 0f) }
        LaunchedEffect(images) {
            val next = images ?: return@LaunchedEffect
            val old = shown
            if (still) {
                shown = next
                return@LaunchedEffect
            }
            if (old == null) {
                shown = next
                appear.animateTo(1f, tween(FIRST_FADE_MS, easing = Motion.Dimmer))
                return@LaunchedEffect
            }
            leaving = old
            shown = next
            fade.snapTo(0f)
            fade.animateTo(1f, tween(if (feed.clock.preview.active) PREVIEW_SEASON_FADE_MS else SEASON_FADE_MS, easing = Motion.Sine))
            leaving = null
        }
        val look: () -> PaperLook = { light.intValue; state.look }
        val places: () -> SkyPlaces = { light.intValue; state.places }
        val motion: () -> PaperMotion = { frame.longValue; state.motion }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }) {
            leaving?.let { PaperView(it, look, places, motion, Modifier.fillMaxSize()) }
            shown?.let { s ->
                PaperView(s, look, places, motion, Modifier.fillMaxSize().graphicsLayer { alpha = if (leaving != null) fade.value else 1f })
            }
            if (shown != null) content(look)
        }
    }
}

/**
 * The last bake, kept for the next time the Fensterblick comes (the next pause, most often the
 * same screen and season): softly – the system takes it back whenever it needs the memory.
 */
private object PaperCache {
    private class Entry(val key: List<Any>, val images: PaperImages)

    private var last: SoftReference<Entry>? = null

    @Synchronized
    fun get(w: Int, h: Int, density: Float, shapes: SeasonShapes, label: LabelSide): PaperImages {
        val key = listOf(w, h, density, shapes, label)
        last?.get()?.takeIf { it.key == key }?.let { return it.images }
        val images = PaperImages(PaperBake.bake(w, h, density, shapes, label))
        last = SoftReference(Entry(key, images))
        return images
    }
}

/** How often (frames) the season's shapes are checked: every few seconds. */
private const val SHAPES_EVERY = 60L

/** The picture's tick (ms): about twenty frames a second. */
const val VISTA_TICK_MS = 50L

/** The first picture fades in this long once it is baked. */
private const val FIRST_FADE_MS = 1_200

/** A new season's shapes fade in this slowly (a day's change, seen by nobody) – and quicker in the preview. */
private const val SEASON_FADE_MS = 20_000
private const val PREVIEW_SEASON_FADE_MS = 1_500

/**
 * What the picture shows at this frame, worked out once per frame (or less): the colours, the
 * sun's and moon's places, the clouds. The colours are only worked out anew when the sky has
 * moved on by [LOOK_EVERY_MS] of its own time – each step far below a visible level – except
 * while the preview glides or runs the time-lapse, where every frame counts.
 */
private class VistaFrameState(private val feed: VistaFeed) {
    var frames = 0L
        private set
    var look: PaperLook = PaperLight.look(SkyState(30f, 180f))
        private set
    var places: SkyPlaces = SkyMap.places(SkyState(30f, 180f))
        private set
    var motion: PaperMotion = PaperMotion(0.0, emptyList())
        private set

    private var lookSky = Long.MIN_VALUE
    private var lookCover = -1f
    private var cover = -1f
    private var lastWall = 0L
    private val clouds = ArrayList<CloudPlace>()

    fun shapesNow(): SeasonShapes {
        val sky = feed.clock.sky()
        val place = feed.place()
        val day = feed.clock.preview.dayOfYear
        val shapes = if (day != null) World.seasonOnDay(day.toFloat()).second else World.season(sky, feed.zone(), place.latitude).second
        return quantised(shapes)
    }

    /** Moves on to this frame; true when the light (colours, sun, moon) was worked out anew. */
    fun advance(): Boolean {
        frames++
        val wall = feed.wall()
        val sky = feed.clock.sky()
        // The cover glides towards the weather's (or the preview's) over a few minutes.
        val target = feed.clock.cover(feed.cover())
        val dt = if (lastWall == 0L) Double.MAX_VALUE else (wall - lastWall).coerceAtLeast(0L) / 1000.0
        lastWall = wall
        val tau = if (feed.clock.preview.cover != null) PREVIEW_COVER_TAU_S else COVER_TAU_S
        cover = if (cover < 0f || dt == Double.MAX_VALUE) target else cover + (target - cover) * (1f - exp(-dt / tau).toFloat())
        val moving = feed.clock.preview.active
        val relit = moving || abs(sky - lookSky) >= LOOK_EVERY_MS || abs(cover - lookCover) > 0.01f
        if (relit) {
            lookSky = sky
            lookCover = cover
            val place = feed.place()
            val heavens = World.heavens(sky, place)
            val day = feed.clock.preview.dayOfYear
            val season = if (day != null) World.seasonOnDay(day.toFloat()).first else World.season(sky, feed.zone(), place.latitude).first
            val s = SkyState(
                heavens.sunElevation, heavens.sunAzimuth, heavens.moonElevation, heavens.moonAzimuth, heavens.moonFraction,
                cover = cover, season = season,
            )
            look = PaperLight.look(s)
            places = SkyMap.places(s, heavens.siderealDeg, heavens.latitude)
        }
        val seconds = (wall - MOTION_EPOCH_MS) / 1000.0
        motion = PaperMotion(seconds, ArrayList(CloudField.places(seconds, CloudField.density(cover), clouds)))
        return relit
    }

    private fun quantised(s: SeasonShapes) = SeasonShapes(q(s.snow), q(s.peaks), q(s.leaves), q(s.blossom))

    /** Steps of 1/16: a new bake only when a shape has visibly changed. */
    private fun q(v: Float) = (v * 16f).roundToInt() / 16f

    companion object {
        /** The colours are worked out anew after this much of the sky's own time. */
        const val LOOK_EVERY_MS = 10_000L

        /** How fast the cover follows the weather (s), and the preview's choice. */
        const val COVER_TAU_S = 150.0
        const val PREVIEW_COVER_TAU_S = 0.8

        /** Origin of the clock for what moves by itself: 1 January 2026 (keeps the numbers small). */
        const val MOTION_EPOCH_MS = 1_767_225_600_000L
    }
}
