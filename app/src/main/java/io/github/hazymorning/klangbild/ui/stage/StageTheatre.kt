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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.ui.Motion

/** How two consecutive stages differ, which decides the choreography. */
enum class Change {
    /** New track with a new picture: the new artwork settles over the old one. */
    FULL,

    /** New track, same picture (the next song of an album): only the text changes. */
    TEXT,

    /** Same track, new scene (better cover, time-of-day light): a quiet cross-fade. */
    SOFT,
}

/**
 * The stage with its transitions. On a track change the new artwork fades in over the old
 * one and settles from a hair larger to its size, while its light morphs on the wall; the
 * old text leaves first, then the new lines rise into place one after another. Opaque
 * pictures are never cross-faded against each other (that would let the wall shine through
 * half-way) – the new one simply covers the old. On pause the artwork recedes a little and
 * dims with its light and the text.
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
    /** Muted or the link is down: the stage dims like a pause. */
    dimmed: Boolean = false,
) {
    var current by remember { mutableStateOf(stage) }
    var previous by remember { mutableStateOf<StageData?>(null) }
    var change by remember { mutableStateOf(Change.FULL) }
    val t = remember { Animatable(1f) }

    LaunchedEffect(stage) {
        if (stage.scene !== current.scene || stage.track != current.track) {
            change = when {
                stage.track.key == current.track.key -> Change.SOFT
                samePicture(current.scene, stage.scene) -> Change.TEXT
                else -> Change.FULL
            }
            previous = current
            current = stage
            t.snapTo(0f)
            t.animateTo(1f, tween(durationOf(change), easing = LinearEasing))
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
        targetValue = if (quiet) Motion.PAUSED_COVER else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-cover",
    )
    // Paused, the artwork recedes a little with its light – only a real pause, not a muted
    // or disconnected stage, which merely dims.
    val recede by animateFloatAsState(
        targetValue = if (status == PlayerStatus.PAUSED) Motion.PAUSED_SCALE else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Decelerate),
        label = "pause-scale",
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
    val newPicture: () -> Float = {
        when {
            !pictureChanges -> 1f
            kind == Change.FULL -> p(0, Motion.ART_IN_MS, Motion.Sine)
            else -> p(0, total, Motion.Sine)
        }
    }
    // The light on the wall morphs a little slower than the picture changes.
    val newGlow: () -> Float = {
        when {
            !pictureChanges -> 1f
            kind == Change.FULL -> p(0, Motion.GLOW_MS, Motion.Sine)
            else -> p(0, total, Motion.Sine)
        }
    }
    val settle: () -> Float = { if (prev == null || kind != Change.FULL) 1f else 1f + 0.02f * (1f - p(0, total, Motion.Decelerate)) }
    // The text: the old block leaves first, the new lines follow one after another.
    val textIn: (TextLine) -> Float = { line ->
        if (prev == null || kind == Change.SOFT) 1f else p(Motion.TEXT_IN_MS + stagger(line), Motion.TEXT_IN_MS + stagger(line) + 500, Motion.Decelerate)
    }
    val textRise: (TextLine) -> Float = { line ->
        if (prev == null || kind == Change.SOFT) 0f else 10f * (1f - textIn(line))
    }

    Box(modifier.fillMaxSize()) {
        BaseWall()
        if (prev != null && pictureChanges) GlowLayer(prev.scene, level = level, scale = { recede })
        GlowLayer(cur.scene, alpha = newGlow, level = level, scale = { recede })
        if (prev != null && pictureChanges) ArtworkLayer(prev.scene, dim = { coverDim }, scale = { recede })
        ArtworkLayer(
            cur.scene,
            alpha = newPicture,
            dim = { coverDim },
            scale = { settle() * recede },
        )
        if (prevModel != null && prevPlan != null) {
            val out: () -> Float = { if (kind == Change.SOFT) 1f - p(0, total) else 1f - p(0, Motion.TEXT_OUT_MS) }
            TextLayer(
                prevModel,
                plan = prevPlan,
                alpha = { out() },
                rise = { if (kind == Change.SOFT) 0f else -6f * p(0, Motion.TEXT_OUT_MS) },
                dim = { textDim },
            )
        }
        TextLayer(curModel, plan = curPlan, alpha = textIn, rise = textRise, dim = { textDim })
        // The status line belongs to the room, not to the track: it simply stays.
        StatusLayer(curModel)
    }
}

/** Each line of the new text starts a little after the one above it. */
private fun stagger(line: TextLine): Int = when (line) {
    TextLine.WORK, TextLine.TITLE -> 0
    TextLine.ARTIST -> 70
    TextLine.ALBUM -> 130
    TextLine.BOTTOM -> 190
    TextLine.NEXT -> 240
}

private fun durationOf(change: Change) = when (change) {
    Change.FULL -> Motion.TRACK_CHANGE_MS
    Change.TEXT -> Motion.TEXT_CHANGE_MS
    Change.SOFT -> Motion.SOFT_SWAP_MS
}

/** Same picture: consecutive songs of one album. */
private fun samePicture(a: ArtworkScene, b: ArtworkScene): Boolean =
    a.shape == b.shape && a.coverGenerated == b.coverGenerated && a.thumb.contentEquals(b.thumb)
