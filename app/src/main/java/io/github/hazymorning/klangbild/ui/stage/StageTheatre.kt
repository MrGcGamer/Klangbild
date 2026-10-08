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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.ui.Motion

/**
 * The stage with its transitions. On a track change the old light cools down to a dim
 * amber and goes out like a filament while the new one warms up; the cover cross-fades,
 * the old text leaves upwards and the new text settles in from below.
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
    var soft by remember { mutableStateOf(false) }
    val t = remember { Animatable(1f) }

    LaunchedEffect(stage) {
        if (stage.scene !== current.scene || stage.track != current.track) {
            soft = stage.track.key == current.track.key
            previous = current
            current = stage
            t.snapTo(0f)
            t.animateTo(1f, tween(if (soft) Motion.SOFT_SWAP_MS else Motion.TRACK_CHANGE_MS, easing = LinearEasing))
            previous = null
        }
    }

    val pausedLevel by animateFloatAsState(
        targetValue = if (status == PlayerStatus.PAUSED) Motion.PAUSED_LIGHT else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-light",
    )
    val coverDim by animateFloatAsState(
        targetValue = if (status == PlayerStatus.PAUSED) Motion.PAUSED_COVER else 1f,
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-cover",
    )
    val breathing: State<Float> = if (status == PlayerStatus.BUFFERING) {
        rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 1f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = Motion.Dimmer), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val cur = current
    val prev = previous
    val curModel = remember(cur, modelFor) { modelFor(cur) }
    val prevModel = remember(prev, modelFor) { prev?.let(modelFor) }

    fun angle(s: StageData) = { HaloLight.sunAngle(progressFor(s), wallClock()) }

    Box(modifier.fillMaxSize()) {
        BaseWall(cur.scene)
        if (prev != null) {
            HaloLightLayer(
                prev.scene,
                sunAngle = angle(prev),
                lightLevel = { pausedLevel * (1f - Motion.phase(t.value, 0f, 0.85f)) },
                cooling = { if (soft) 0f else Motion.phase(t.value, 0f, 0.55f) },
            )
        }
        HaloLightLayer(
            cur.scene,
            sunAngle = angle(cur),
            alpha = { if (prev == null) 1f else Motion.phase(t.value, if (soft) 0f else 0.12f, if (soft) 1f else 0.9f) },
            lightLevel = { pausedLevel * breathing.value },
            cooling = { if (prev == null || soft) 0f else 1f - Motion.phase(t.value, 0.3f, 0.95f) },
        )
        if (prev != null) {
            HaloCoverLayer(
                prev.scene,
                alpha = { 1f - Motion.phase(t.value, if (soft) 0.3f else 0.18f, if (soft) 1f else 0.62f) },
                dim = { coverDim },
            )
        }
        HaloCoverLayer(
            cur.scene,
            alpha = { if (prev == null) 1f else Motion.phase(t.value, if (soft) 0f else 0.32f, if (soft) 0.7f else 0.72f) },
            dim = { coverDim },
            scale = { if (prev == null || soft) 1f else 0.985f + 0.015f * Motion.phase(t.value, 0.3f, 1f) },
        )
        if (prevModel != null && !soft) {
            HaloTextLayer(
                prevModel,
                alpha = { 1f - Motion.phase(t.value, 0f, 0.22f) },
                offsetY = { -6f * Motion.phase(t.value, 0f, 0.22f) },
            )
        }
        HaloTextLayer(
            curModel,
            alpha = { if (prev == null || soft) 1f else Motion.phase(t.value, 0.45f, 0.8f) },
            offsetY = { if (prev == null || soft) 0f else 8f * (1f - Motion.phase(t.value, 0.45f, 0.85f)) },
        )
    }
}
