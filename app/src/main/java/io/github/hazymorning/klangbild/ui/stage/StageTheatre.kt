package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
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
 * The stage with its transitions. On a track change the old print goes dark – and as it
 * goes, the lamp behind it is turned down, so the empty wall shows one dim, round pool of
 * light, not a black panel. While the wall is empty the lamp changes colour through OKLCH
 * (never through grey) and the sundial swings back to the morning; then the new print comes
 * up with the light and the new text settles in from below. Lines that stay the same (the
 * album of the next song, the clock) do not move at all.
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
            t.animateTo(1f, tween(durationOf(change), easing = LinearEasing))
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
    val total = durationOf(kind)
    fun p(startMs: Int, endMs: Int, easing: Easing = Motion.Dimmer) = Motion.phaseMs(t.value, startMs, endMs, total, easing)

    val curModel = remember(cur, modelFor) { modelFor(cur) }
    val prevModel = remember(prev, modelFor) { prev?.let(modelFor) }
    val curPlan = rememberTextPlan(curModel)
    val prevPlan = prevModel?.let { rememberTextPlan(it) }
    val steady = remember(prevModel, curModel, prevPlan, curPlan) {
        if (prevModel != null && prevPlan != null && kind != Change.SOFT) steadyLines(prevModel, prevPlan, curModel, curPlan) else emptySet()
    }
    val sameSpot = prev != null && prev.scene.shape == cur.scene.shape

    // The sundial: live angle of the current track; during a change it swings over from
    // where the old track left it.
    val angle: () -> Float = {
        val live = HaloLight.sunAngle(progressFor(cur), wallClock())
        val a = if (prev != null && kind != Change.SOFT && !fromAngle.isNaN()) {
            Motion.mixAngle(fromAngle, live, p(200, Motion.COVER_IN_END_MS))
        } else {
            live
        }
        lastAngle[0] = a
        a
    }
    // The lamp's colour morphs from the old print's to the new one's while the wall is empty.
    val tint: () -> Int = {
        when {
            prev == null || kind == Change.TEXT -> cur.scene.lightTint
            kind == Change.FULL -> HaloLight.mixOklch(prev.scene.lightTint, cur.scene.lightTint, p(500, 1_400))
            else -> HaloLight.mixOklch(prev.scene.lightTint, cur.scene.lightTint, p(0, total))
        }
    }
    val oldCover: () -> Float = { 1f - p(0, Motion.COVER_OUT_END_MS, Motion.Sine) }
    val newCover: () -> Float = { p(Motion.COVER_IN_START_MS, Motion.COVER_IN_END_MS, Motion.Sine) }
    // The lamp goes down while the prints are swapped: the empty wall shows a dim pool only.
    val swapDip: () -> Float = {
        if (prev == null || kind != Change.FULL) {
            1f
        } else {
            val down = p(0, Motion.COVER_OUT_END_MS) - p(Motion.COVER_IN_START_MS, Motion.COVER_IN_END_MS)
            1f - (1f - Motion.SWAP_LIGHT) * down
        }
    }
    val level: () -> Float = { pausedLevel * breathing.value * swapDip() }

    // The clock only moves along if its place changes. Then it stays, a little dimmed, where
    // it was through the dark moment and dissolves over to its new place as the new light
    // comes – the screen never goes entirely dark. (It is gone before the new print, which
    // now hangs where it was, comes up beneath it.)
    val clockStays = prev != null && prevPlan != null && prevPlan.showClock && curPlan.showClock &&
        prev.scene.layout.columnLeft == cur.scene.layout.columnLeft && kind != Change.SOFT
    val clockMoves = prev != null && prevPlan != null && prevPlan.showClock && curPlan.showClock &&
        !clockStays && kind == Change.FULL
    val prevTextAlpha: () -> Float = { 1f - p(0, 550) }
    val curTextAlpha: () -> Float = {
        when {
            prev == null || kind == Change.SOFT -> 1f
            kind == Change.TEXT -> p(700, 1_700)
            else -> p(Motion.TEXT_IN_START_MS, Motion.TEXT_IN_END_MS)
        }
    }
    val prevClockAlpha: () -> Float =
        if (clockMoves) ({ (1f - (1f - Motion.HELD_CLOCK) * p(0, 550)) * (1f - p(1_000, 1_300)) }) else prevTextAlpha
    val curClockAlpha: () -> Float = when {
        clockStays || prev == null -> ({ 1f })
        clockMoves -> ({ p(1_100, 1_900) })
        else -> curTextAlpha
    }
    // A clock that stays takes on the new print's colour while the lamp changes colour.
    val prevQuiet = prevModel?.scene?.palette?.inkQuiet
    val curQuiet = curModel.scene.palette.inkQuiet
    val clockColor: ColorProducer? = if (clockStays && prevQuiet != null && prevQuiet != curQuiet) {
        ColorProducer { lerp(Color(prevQuiet), Color(curQuiet), p(500, 1_400)) }
    } else {
        null
    }

    Box(modifier.fillMaxSize()) {
        BaseWall()
        if (prev != null && kind == Change.FULL && !sameSpot) {
            // The print moves to the other side: the old light hands over to the new one.
            HaloLightLayer(
                prev.scene, sunAngle = angle, lightLevel = level, tint = tint,
                alpha = { 1f - p(300, 1_150) }, holeOpen = { 1f - oldCover() },
            )
            HaloLightLayer(
                cur.scene, sunAngle = angle, lightLevel = level, tint = tint,
                alpha = { p(850, 1_700) }, holeOpen = { 1f - newCover() },
            )
        } else {
            HaloLightLayer(
                cur.scene, sunAngle = angle, lightLevel = level, tint = tint,
                // The wall opens as the old print goes and closes as the new one comes.
                holeOpen = { if (prev == null || kind != Change.FULL) 0f else minOf(1f - oldCover(), 1f - newCover()) },
            )
            if (prev != null && kind != Change.TEXT) {
                // Old light on top, fading: a true cross-fade of the two light shapes.
                HaloLightLayer(
                    prev.scene, sunAngle = angle, lightLevel = level, tint = tint,
                    alpha = { if (kind == Change.FULL) 1f - p(700, 1_200) else 1f - p(0, total) },
                    holeOpen = { if (kind == Change.FULL) 1f - oldCover() else 0f },
                )
            }
        }
        if (prev != null && kind == Change.FULL) {
            HaloCoverLayer(prev.scene, sunAngle = angle, alpha = oldCover, dim = { coverDim })
        }
        HaloCoverLayer(
            cur.scene,
            sunAngle = angle,
            alpha = { if (prev == null || kind != Change.FULL) 1f else newCover() },
            dim = { coverDim },
            scale = {
                if (prev == null || kind != Change.FULL) 1f
                else 0.99f + 0.01f * p(Motion.COVER_IN_START_MS, total)
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
                hidden = steady,
            )
            if (!clockStays) HaloClockLayer(prevModel, plan = prevPlan, alpha = prevClockAlpha)
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
                    else -> 8f * (1f - p(Motion.TEXT_IN_START_MS, total))
                }
            },
            steady = steady,
        )
        HaloClockLayer(curModel, plan = curPlan, alpha = curClockAlpha, color = clockColor)
        if (prevModel != null && prevPlan != null && kind == Change.SOFT) {
            // Same track in new light: the old text lies on top and fades, so the colours
            // (and lines that night hides) change gently instead of at once.
            val fade = { 1f - p(0, total) }
            HaloTextLayer(prevModel, plan = prevPlan, alpha = fade, dim = { textDim })
            HaloClockLayer(prevModel, plan = prevPlan, alpha = fade)
        }
    }
}

private fun durationOf(change: Change) = when (change) {
    Change.FULL -> Motion.TRACK_CHANGE_MS
    Change.TEXT -> Motion.TEXT_CHANGE_MS
    Change.SOFT -> Motion.SOFT_SWAP_MS
}

/** Same picture at the same place: consecutive songs of one album. */
private fun samePicture(a: ArtworkScene, b: ArtworkScene): Boolean =
    a.shape == b.shape && a.layout.mirrored == b.layout.mirrored &&
        a.coverGenerated == b.coverGenerated && a.thumb.contentEquals(b.thumb)
