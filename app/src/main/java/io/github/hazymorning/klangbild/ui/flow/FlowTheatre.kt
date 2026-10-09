package io.github.hazymorning.klangbild.ui.flow

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
import io.github.hazymorning.klangbild.app.FlowData
import io.github.hazymorning.klangbild.app.StageData
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.render.FlowGeometry
import io.github.hazymorning.klangbild.ui.Motion
import io.github.hazymorning.klangbild.ui.stage.BaseWall
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.ui.stage.StatusLayer
import io.github.hazymorning.klangbild.ui.stage.TextLayer
import io.github.hazymorning.klangbild.ui.stage.TextLine
import io.github.hazymorning.klangbild.ui.stage.WallLayer
import io.github.hazymorning.klangbild.ui.stage.asColor
import io.github.hazymorning.klangbild.ui.stage.rememberTextPlan
import io.github.hazymorning.klangbild.ui.stage.samePicture
import kotlinx.coroutines.flow.conflate

/** How two consecutive strips differ, which decides the choreography. */
private enum class FlowChange {
    /** Another card came to the middle: the strip turns over. */
    MOVE,

    /** The next song of the same picture: the text changes (the picture only if it differs after all). */
    TEXT,

    /** Only cards beside the middle came or went (the queue changed). */
    SIDES,

    /** Same track, new scene (better cover, time-of-day light): a quiet cross-fade. */
    SOFT,

    /** Same track on the other wall (light ↔ dark): the same, slower. */
    WALL,
}

/**
 * Cover Flow with its transitions. When another picture comes to the middle the strip turns
 * over – the leaving cover swings away to the side as the next one turns to face the room –
 * in one unhurried, eased movement; the old text leaves first, the new lines rise once the
 * new cover arrives, and the light above the cards takes its colour. New cards roll in opaque
 * from beyond the end of the strip, cards that are no longer wanted dissolve before anything
 * passes them ([between]). A pause dims the light, the cards and the text; nothing moves.
 *
 * One change at a time: what arrives meanwhile waits until the running change has landed, and
 * of several waiting only the newest is shown – quick skips never cut a movement short.
 *
 * Every animated value is read inside draw/layer lambdas, so animations never recompose.
 */
@Composable
fun FlowTheatre(
    flow: FlowData,
    status: PlayerStatus,
    modelFor: (StageData) -> StageModel,
    modifier: Modifier = Modifier,
    lightFactor: () -> Float = { 1f },
    /** The link is down: the stage dims like a pause. */
    dimmed: Boolean = false,
    /** Night: the cards beside the middle step back into the dark. */
    night: Boolean = false,
) {
    var current by remember { mutableStateOf(flow) }
    var previous by remember { mutableStateOf<FlowData?>(null) }
    var change by remember { mutableStateOf(FlowChange.MOVE) }
    var motions by remember { mutableStateOf(resting(flow)) }
    val t = remember { Animatable(1f) }
    val latest by rememberUpdatedState(flow)

    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect { target ->
            val old = current
            if (target == old) return@collect
            val kind = changeOf(old, target)
            val next = between(old, target)
            current = target
            if (kind == FlowChange.SIDES && next.all { it.from == it.to && it.fromAlpha == it.toAlpha }) {
                // Only a card's scene was renewed (the same picture in the same light): nothing to watch.
                motions = next
                return@collect
            }
            t.snapTo(0f)
            change = kind
            motions = next
            previous = old
            t.animateTo(1f, tween(durationOf(kind), easing = LinearEasing))
            previous = null
            motions = resting(target)
        }
    }

    val middle = current.middle
    val appearance = middle.scene.appearance
    val quiet = status == PlayerStatus.PAUSED || dimmed
    val pausedLevel by animateFloatAsState(if (quiet) Motion.PAUSED_LIGHT else 1f, tween(Motion.PAUSE_MS, easing = Motion.Dimmer), label = "pause-light")
    val cardDim by animateFloatAsState(
        targetValue = when {
            !quiet -> 1f
            appearance == Appearance.LIGHT -> Motion.PAUSED_COVER_ON_PAPER
            else -> Motion.PAUSED_COVER
        },
        animationSpec = tween(Motion.PAUSE_MS, easing = Motion.Dimmer),
        label = "pause-cards",
    )
    val textDim by animateFloatAsState(if (quiet) Motion.PAUSED_TEXT else 1f, tween(Motion.PAUSE_MS, easing = Motion.Dimmer), label = "pause-text")
    val sides by animateFloatAsState(if (night) 0f else 1f, tween(Motion.NIGHT_SIDES_MS, easing = Motion.Dimmer), label = "night-sides")
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

    val prev = previous
    val kind = change
    val total = durationOf(kind)
    fun p(startMs: Int, endMs: Int, easing: androidx.compose.animation.core.Easing = Motion.Dimmer) =
        Motion.phaseMs(t.value, startMs, endMs, total, easing)

    val curStage = middle.stage
    val prevStage = prev?.middle?.stage
    val curModel = remember(curStage, modelFor) { modelFor(curStage) }
    val prevModel = remember(prevStage, modelFor) { prevStage?.let(modelFor) }
    val curPlan = rememberTextPlan(curModel)
    val prevPlan = prevModel?.let { rememberTextPlan(it) }
    val geometry = remember(middle.scene.layout) { FlowGeometry(middle.scene.layout) }
    val wall = middle.scene.palette.wall.asColor()
    val level: () -> Float = { pausedLevel * breathing.value * lightFactor() }
    // The text cross-fades with a new look of the same track; a new song's lines leave and rise.
    val softText = kind == FlowChange.SOFT || kind == FlowChange.WALL
    // The whole strip cross-fades when the same middle card takes a new look.
    val restrip = prev != null && (softText || (kind == FlowChange.TEXT && !samePicture(prev.middle.scene, middle.scene)))
    val newWall: () -> Float = {
        when {
            prev == null -> 1f
            kind == FlowChange.MOVE -> p(0, Motion.FLOW_LIGHT_MS, Motion.Sine)
            restrip -> p(0, total, Motion.Sine)
            else -> 1f
        }
    }
    val textStart = if (kind == FlowChange.MOVE) Motion.FLOW_TEXT_IN_MS else Motion.TEXT_IN_SAME_PICTURE_MS
    val textMoves = prev != null && (kind == FlowChange.MOVE || kind == FlowChange.TEXT)
    val textIn: (TextLine) -> Float = { line ->
        when {
            prev == null || kind == FlowChange.SIDES -> 1f
            softText -> p(0, total, Motion.Sine)
            else -> p(textStart + line.stagger, textStart + line.stagger + Motion.TEXT_LINE_MS, Motion.Decelerate)
        }
    }
    val textRise: (TextLine) -> Float = { line -> if (textMoves) 10f * (1f - textIn(line)) else 0f }

    Box(modifier.fillMaxSize()) {
        BaseWall(wall)
        if (prev != null && (kind == FlowChange.MOVE || restrip)) WallLayer(prev.middle.scene, level = level)
        WallLayer(middle.scene, alpha = newWall, level = level)
        if (prev != null && restrip) {
            // A new look of the same strip: the old cards stay underneath while the new ones come up.
            val old = prev.middle.scene
            FlowStrip(resting(prev), geometry, old.appearance, old.lighting, old.palette.wall.asColor(), dim = { cardDim }, sides = { sides })
        }
        FlowStrip(
            cards = motions,
            geometry = geometry,
            appearance = appearance,
            lighting = middle.scene.lighting,
            wall = wall,
            progress = { if (softText) 1f else Motion.Turn.transform(t.value) },
            dim = { cardDim },
            sides = { sides },
            alpha = { if (restrip) p(0, total, Motion.Sine) else 1f },
        )
        if (prevModel != null && prevPlan != null && kind != FlowChange.SIDES) {
            val out: () -> Float = { if (softText) 1f - p(0, total) else 1f - p(0, Motion.TEXT_OUT_MS) }
            TextLayer(prevModel, plan = prevPlan, alpha = { out() }, rise = { if (softText) 0f else -6f * p(0, Motion.TEXT_OUT_MS) }, dim = { textDim })
        }
        TextLayer(curModel, plan = curPlan, alpha = textIn, rise = textRise, dim = { textDim })
        StatusLayer(curModel)
    }
}

private fun changeOf(old: FlowData, new: FlowData): FlowChange {
    val a = old.middle
    val b = new.middle
    return when {
        a.id != b.id -> FlowChange.MOVE
        a.track.key != b.track.key -> FlowChange.TEXT
        a.scene !== b.scene && a.scene.appearance != b.scene.appearance -> FlowChange.WALL
        a.scene !== b.scene -> FlowChange.SOFT
        else -> FlowChange.SIDES
    }
}

/** The cards of [flow] standing still, the middle one at position 0. */
private fun resting(flow: FlowData): List<CardMotion> = flow.cards.mapIndexed { i, e ->
    val p = (i - flow.current).toFloat()
    CardMotion(e.id, e.scene.card!!, p, p)
}

/**
 * Each card's way from [old] to [new]. The strip moves away from the side new pictures come
 * from – the right going forward, the left going back – and on that side:
 *
 * - the cards that arrive roll in opaque from beyond the end of the strip, in formation, the
 *   first one from [FlowGeometry.EDGE] – so an arriving card never stands half-transparent in
 *   front of another;
 * - the cards that leave dissolve where they stand, gone before the arriving ones pass;
 * - if the order there does not simply continue (a song put in between, the queue rearranged),
 *   the whole side is replaced: what stood there dissolves, and all of its cards roll in.
 *
 * Cards in both strips move from their old place to their new one. On the other side cards
 * that leave step one place further out as they fade, cards that appear fade in where they
 * stand.
 */
private fun between(old: FlowData, new: FlowData): List<CardMotion> {
    val oldPos = HashMap<Long, Float>()
    old.cards.forEachIndexed { i, e -> oldPos[e.id] = (i - old.current).toFloat() }
    val newPos = HashMap<Long, Float>()
    new.cards.forEachIndexed { i, e -> newPos[e.id] = (i - new.current).toFloat() }
    val oldMiddle = old.middle.id
    // Forward unless the old middle card now stands to the right. Positions below are taken in
    // the strip's direction: new pictures arrive from the positive side.
    val dir = if ((newPos[oldMiddle] ?: -1f) <= 0f) 1f else -1f
    fun was(id: Long) = oldPos[id]?.times(dir)
    fun now(id: Long) = newPos.getValue(id) * dir
    // How far the cards that stay move: one place when the strip turns over, none when only its sides change.
    val shift = newPos[oldMiddle]?.times(dir) ?: -1f
    val arriving = new.cards.filter { it.id != oldMiddle && now(it.id) >= 0f }
    val staying = arriving.filter { it.id in oldPos }
    val lastStaying = staying.maxOfOrNull { now(it.id) } ?: Float.NEGATIVE_INFINITY
    val continues = staying.all { was(it.id)!! > 0f && now(it.id) == was(it.id)!! + shift } &&
        arriving.all { it.id in oldPos || now(it.id) > lastStaying }
    val rolling = (if (continues) arriving.filter { it.id !in oldPos } else arriving).mapTo(HashSet()) { it.id }
    // How far the arriving cards roll: the first one starts just out of sight.
    val run = arriving.filter { it.id in rolling }.minOfOrNull { FlowGeometry.EDGE - now(it.id) }

    val out = ArrayList<CardMotion>(new.cards.size + old.cards.size)
    for (e in new.cards) {
        val card = e.scene.card!!
        val to = now(e.id)
        val from = was(e.id)
        out += when {
            e.id in rolling -> CardMotion(e.id, card, dir * (to + run!!), dir * to)
            from != null -> CardMotion(e.id, card, dir * from, dir * to)
            else -> CardMotion(e.id, card, dir * to, dir * to, fromAlpha = 0f)
        }
    }
    for (e in old.cards) {
        if (e.id in newPos && e.id !in rolling) continue
        val card = e.scene.card!!
        val from = was(e.id)!!
        out += if (from > 0f) {
            // Dissolves where it stands before the first arriving card reaches its place.
            val by = run?.let { (DISSOLVE_LEAD * (FlowGeometry.EDGE - from) / it).coerceIn(0.15f, 1f) } ?: 1f
            CardMotion(e.id, card, dir * from, dir * from, toAlpha = 0f, fadeBy = by)
        } else {
            CardMotion(e.id, card, dir * from, dir * (from - 1f), toAlpha = 0f)
        }
    }
    return out
}

/** A card that clears the way is gone at this share of the time the arriving cards need to reach it. */
private const val DISSOLVE_LEAD = 0.85f

private fun durationOf(change: FlowChange) = when (change) {
    FlowChange.MOVE -> Motion.FLOW_MOVE_MS
    FlowChange.TEXT -> Motion.TEXT_CHANGE_MS
    FlowChange.SIDES -> Motion.FLOW_SIDES_MS
    FlowChange.SOFT -> Motion.SOFT_SWAP_MS
    FlowChange.WALL -> Motion.WALL_CHANGE_MS
}
