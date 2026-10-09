package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.ui.Motion
import kotlinx.coroutines.flow.conflate

/** How two consecutive stages differ, which decides the choreography. */
enum class Change {
    /** New track with a new picture: the new artwork settles over the old one. */
    FULL,

    /** New track, same picture (the next song of an album): only the text changes. */
    TEXT,

    /** Same track, new scene (better cover, time-of-day light): a quiet cross-fade. */
    SOFT,

    /** Same track on the other wall (light ↔ dark): the same cross-fade, slower, like the light outside. */
    WALL,
    ;

    /** A cross-fade of the same track. */
    val isSoft get() = this == SOFT || this == WALL
}

/**
 * The stage with its transitions. On a track change the old artwork goes dark first, then
 * the new one comes up and settles from a hair larger to its size – the two only overlap
 * while both are faint, so no double image – while the light on the wall turns from the old
 * colours to the new. The old text leaves first; the new lines rise into place one after
 * another once the new picture stands. A pause dims light, artwork and text; nothing moves.
 *
 * Every animated value is read inside draw/layer lambdas, so animations never recompose.
 *
 * @param modelFor builds the model for a stage (title, progress, status …).
 * @param lightFactor the light's strength from the settings (1 = as designed, 0 = off).
 */
@Composable
fun StageTheatre(
    stage: StageData,
    status: PlayerStatus,
    modelFor: (StageData) -> StageModel,
    modifier: Modifier = Modifier,
    lightFactor: () -> Float = { 1f },
    /** The link is down: the stage dims like a pause. */
    dimmed: Boolean = false,
) {
    var current by remember { mutableStateOf(stage) }
    var previous by remember { mutableStateOf<StageData?>(null) }
    var change by remember { mutableStateOf(Change.FULL) }
    val t = remember { Animatable(1f) }
    val latest by rememberUpdatedState(stage)

    // One change at a time: what arrives meanwhile waits until the running change has landed,
    // and of several waiting only the newest is shown – a quick skip never cuts a change short.
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect { target ->
            val old = current
            if (target.scene === old.scene && target.track == old.track) return@collect
            val kind = when {
                target.track.key == old.track.key && target.scene.appearance != old.scene.appearance -> Change.WALL
                target.track.key == old.track.key -> Change.SOFT
                samePicture(old.scene, target.scene) -> Change.TEXT
                else -> Change.FULL
            }
            t.snapTo(0f)
            change = kind
            previous = old
            current = target
            t.animateTo(1f, tween(durationOf(kind), easing = LinearEasing))
            previous = null
        }
    }

    val quiet = status == PlayerStatus.PAUSED || dimmed
    val pausedLevel by animateFloatAsState(
        targetValue = if (quiet) Motion.PAUSED_LIGHT else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-light",
    )
    val coverDim by animateFloatAsState(
        targetValue = when {
            !quiet -> 1f
            stage.scene.appearance == Appearance.LIGHT -> Motion.PAUSED_COVER_ON_PAPER
            else -> Motion.PAUSED_COVER
        },
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-cover",
    )
    val textDim by animateFloatAsState(
        targetValue = if (quiet) Motion.PAUSED_TEXT else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-text",
    )
    val breathing: State<Float> = if (status == PlayerStatus.BUFFERING) {
        rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 1f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(1_600, easing = Motion.Dimmer), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val cur = current
    val prev = previous
    val kind = change
    val total = durationOf(kind)
    fun p(startMs: Int, endMs: Int, easing: androidx.compose.animation.core.Easing = Motion.Dimmer) =
        Motion.phaseMs(t.value, startMs, endMs, total, easing)

    val curModel = remember(cur, modelFor) { modelFor(cur) }
    val prevModel = remember(prev, modelFor) { prev?.let(modelFor) }
    val curPlan = rememberTextPlan(curModel)
    val prevPlan = prevModel?.let { rememberTextPlan(it) }

    val level: () -> Float = { pausedLevel * breathing.value * lightFactor() }
    val pictureChanges = prev != null && kind != Change.TEXT
    // A new picture: the old one goes dark, then the new one comes up (a soft swap of the same
    // track's picture simply cross-fades).
    val oldPicture: () -> Float = {
        if (kind == Change.FULL) 1f - p(0, Motion.ART_OUT_MS, Motion.Accelerate) else 1f
    }
    val newPicture: () -> Float = {
        when {
            !pictureChanges -> 1f
            kind == Change.FULL -> p(Motion.ART_IN_START_MS, Motion.ART_IN_END_MS, Motion.Decelerate)
            else -> p(0, total, Motion.Sine)
        }
    }
    // The light on the wall turns from the old colours to the new over the whole change.
    val newGlow: () -> Float = {
        when {
            !pictureChanges -> 1f
            kind == Change.FULL -> p(0, Motion.GLOW_MS, Motion.Sine)
            else -> p(0, total, Motion.Sine)
        }
    }
    val settle: () -> Float = {
        if (prev == null || kind != Change.FULL) 1f else 1f + 0.015f * (1f - p(Motion.ART_IN_START_MS, total, Motion.Decelerate))
    }
    // The text: the old block leaves first, the new lines follow one after another.
    val textStart = if (kind == Change.FULL) Motion.TEXT_IN_MS else Motion.TEXT_IN_SAME_PICTURE_MS
    val textIn: (TextLine) -> Float = { line ->
        if (prev == null || kind.isSoft) 1f else p(textStart + line.stagger, textStart + line.stagger + Motion.TEXT_LINE_MS, Motion.Decelerate)
    }
    val textRise: (TextLine) -> Float = { line ->
        if (prev == null || kind.isSoft) 0f else 10f * (1f - textIn(line))
    }

    Box(modifier.fillMaxSize()) {
        BaseWall(cur.scene.palette.wall.asColor())
        if (prev != null && pictureChanges) WallLayer(prev.scene, level = level)
        WallLayer(cur.scene, alpha = newGlow, level = level)
        if (prev != null && pictureChanges) ArtworkLayer(prev.scene, alpha = oldPicture, dim = { coverDim })
        ArtworkLayer(cur.scene, alpha = newPicture, dim = { coverDim }, scale = settle)
        if (prevModel != null && prevPlan != null) {
            val out: () -> Float = { if (kind.isSoft) 1f - p(0, total) else 1f - p(0, Motion.TEXT_OUT_MS) }
            TextLayer(
                prevModel,
                plan = prevPlan,
                alpha = { out() },
                rise = { if (kind.isSoft) 0f else -6f * p(0, Motion.TEXT_OUT_MS) },
                dim = { textDim },
            )
        }
        TextLayer(curModel, plan = curPlan, alpha = textIn, rise = textRise, dim = { textDim })
        // The status line belongs to the room, not to the track: it simply stays.
        StatusLayer(curModel)
    }
}

private fun durationOf(change: Change) = when (change) {
    Change.FULL -> Motion.TRACK_CHANGE_MS
    Change.TEXT -> Motion.TEXT_CHANGE_MS
    Change.SOFT -> Motion.SOFT_SWAP_MS
    Change.WALL -> Motion.WALL_CHANGE_MS
}

/** Same picture: consecutive songs of one album. */
internal fun samePicture(a: ArtworkScene, b: ArtworkScene): Boolean =
    a.shape == b.shape && a.coverGenerated == b.coverGenerated && a.thumb.contentEquals(b.thumb)
