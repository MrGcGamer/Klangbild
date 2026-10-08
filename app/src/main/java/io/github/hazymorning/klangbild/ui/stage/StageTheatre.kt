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
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.ui.Motion

/** How two consecutive stages differ, which decides the choreography. */
enum class Change {
    /** New track with a new picture: print out, wall, print in. */
    FULL,

    /** New track, same picture (the next song of an album): only the text changes. */
    TEXT,

    /** Same track, new scene (better cover, time-of-day light): a quiet cross-fade. */
    SOFT,
}

/**
 * The stage with its transitions. On a track change the old print goes dark, the empty
 * wall holds for a moment while the light changes colour (through OKLCH, so it never
 * passes through grey) and the sundial swings back to the morning, then the new print
 * comes up and the new text settles in from below. The clock does not take part.
 *
 * Every animated value is read inside draw/layer lambdas, so animations never recompose.
 *
 * @param modelFor builds the text model for a stage (title, time line, clock …).
 * @param progressFor 0..1 track progress for the sundial (null for radio).
 */
@Composable
fun StageTheatre(
    stage: StageData,
    status: PlayerStatus,
    modelFor: (StageData) -> StageModel,
    progressFor: (StageData) -> Float?,
    wallClock: () -> Long,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(stage) }
    var previous by remember { mutableStateOf<StageData?>(null) }
    var change by remember { mutableStateOf(Change.FULL) }
    var fromAngle by remember { mutableFloatStateOf(Float.NaN) }
    val lastAngle = remember { floatArrayOf(Float.NaN) }
    val t = remember { Animatable(1f) }

    LaunchedEffect(stage) {
        if (stage.scene !== current.scene || stage.track != current.track) {
            change = when {
                stage.track.key == current.track.key -> Change.SOFT
                samePicture(current.scene, stage.scene) -> Change.TEXT
                else -> Change.FULL
            }
            fromAngle = lastAngle[0]
            previous = current
            current = stage
            t.snapTo(0f)
            val duration = when (change) {
                Change.FULL -> Motion.TRACK_CHANGE_MS
                Change.TEXT -> Motion.TEXT_CHANGE_MS
                Change.SOFT -> Motion.SOFT_SWAP_MS
            }
            t.animateTo(1f, tween(duration, easing = LinearEasing))
            previous = null
        }
    }

    val paused = status == PlayerStatus.PAUSED
    val pausedLevel by animateFloatAsState(
        targetValue = if (paused) Motion.PAUSED_LIGHT else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-light",
    )
    val coverDim by animateFloatAsState(
        targetValue = if (paused) Motion.PAUSED_COVER else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-cover",
    )
    val textDim by animateFloatAsState(
        targetValue = if (paused) Motion.PAUSED_TEXT else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-text",
    )
    val breathing: State<Float> = if (status == PlayerStatus.BUFFERING) {
        rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 1f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = Motion.Dimmer), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val cur = current
    val prev = previous
    val kind = change
    val total = when (kind) {
        Change.FULL -> Motion.TRACK_CHANGE_MS
        Change.TEXT -> Motion.TEXT_CHANGE_MS
        Change.SOFT -> Motion.SOFT_SWAP_MS
    }
    fun p(startMs: Int, endMs: Int, easing: androidx.compose.animation.core.Easing = Motion.Dimmer) =
        Motion.phaseMs(t.value, startMs, endMs, total, easing)

    val curModel = remember(cur, modelFor) { modelFor(cur) }
    val prevModel = remember(prev, modelFor) { prev?.let(modelFor) }
    val curPlan = rememberTextPlan(curModel)
    val prevPlan = prevModel?.let { rememberTextPlan(it) }

    // The sundial: live angle of the current track; during a change it swings over from
    // where the old track left it.
    val angle: () -> Float = {
        val live = HaloLight.sunAngle(progressFor(cur), wallClock())
        val a = if (prev != null && kind != Change.SOFT && !fromAngle.isNaN()) {
            Motion.mixAngle(fromAngle, live, p(200, total - 600))
        } else {
            live
        }
        lastAngle[0] = a
        a
    }
    // The light's colour morphs from the old print's to the new one's.
    val tint: () -> Int = {
        if (prev == null || kind == Change.TEXT) cur.scene.lightTint
        else HaloLight.mixOklch(prev.scene.lightTint, cur.scene.lightTint, if (kind == Change.FULL) p(250, 1_600) else p(0, total))
    }
    // The lamp goes down while the prints are swapped – a scene change in the dark.
    val swapDip: () -> Float = {
        if (prev == null || kind != Change.FULL) 1f
        else 1f - 0.7f * (p(0, 900) - p(900, 2_100))
    }
    val level: () -> Float = { pausedLevel * breathing.value * swapDip() }

    // Text and clock: the clock only moves along if its place changes.
    val clockStays = prev != null && prevPlan != null && prevPlan.showClock && curPlan.showClock &&
        prev.scene.layout.columnLeft == cur.scene.layout.columnLeft && kind != Change.SOFT
    val prevTextAlpha: () -> Float = { 1f - p(0, 550) }
    val curTextAlpha: () -> Float = {
        when {
            prev == null || kind == Change.SOFT -> 1f
            kind == Change.TEXT -> p(700, 1_700)
            else -> p(1_150, 2_350)
        }
    }

    Box(modifier.fillMaxSize()) {
        BaseWall()
        HaloLightLayer(cur.scene, sunAngle = angle, lightLevel = level, tint = tint)
        if (prev != null && kind != Change.TEXT) {
            // Old light on top, fading: a true cross-fade of the two light shapes.
            HaloLightLayer(
                prev.scene,
                sunAngle = angle,
                alpha = { if (kind == Change.FULL) 1f - p(600, 1_300) else 1f - p(0, total) },
                lightLevel = level,
                tint = tint,
            )
        }
        if (prev != null && kind == Change.FULL) {
            HaloCoverLayer(
                prev.scene,
                sunAngle = angle,
                alpha = { 1f - p(0, Motion.COVER_OUT_END_MS, Motion.Out) },
                dim = { coverDim },
            )
        }
        HaloCoverLayer(
            cur.scene,
            sunAngle = angle,
            alpha = {
                if (prev == null || kind != Change.FULL) 1f
                else p(Motion.COVER_IN_START_MS, Motion.COVER_IN_END_MS, Motion.In)
            },
            dim = { coverDim },
            scale = {
                if (prev == null || kind != Change.FULL) 1f
                else 0.985f + 0.015f * p(Motion.COVER_IN_START_MS, total)
            },
        )
        if (prev != null && kind == Change.SOFT) {
            // Same track: the old print on top, fading into the new one.
            HaloCoverLayer(prev.scene, sunAngle = angle, alpha = { 1f - p(0, total) }, dim = { coverDim })
        }
        if (prevModel != null && prevPlan != null && kind != Change.SOFT) {
            HaloTextLayer(
                prevModel,
                plan = prevPlan,
                alpha = prevTextAlpha,
                dim = { textDim },
                offsetY = { -6f * p(0, 550) },
            )
            if (!clockStays) HaloClockLayer(prevModel, plan = prevPlan, alpha = prevTextAlpha)
        }
        HaloTextLayer(
            curModel,
            plan = curPlan,
            alpha = curTextAlpha,
            dim = { textDim },
            offsetY = {
                when {
                    prev == null || kind == Change.SOFT -> 0f
                    kind == Change.TEXT -> 8f * (1f - p(700, 1_800))
                    else -> 8f * (1f - p(1_150, 2_450))
                }
            },
        )
        HaloClockLayer(curModel, plan = curPlan, alpha = if (clockStays || prev == null) ({ 1f }) else curTextAlpha)
    }
}

/** Same picture at the same place: consecutive songs of one album. */
private fun samePicture(a: ArtworkScene, b: ArtworkScene): Boolean =
    a.coverRect == b.coverRect && a.layout.mirrored == b.layout.mirrored &&
        a.coverGenerated == b.coverGenerated && a.thumb.contentEquals(b.thumb)
