package io.github.hazymorning.klangbild.ui.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.hazymorning.klangbild.render.FlowGeometry
import io.github.hazymorning.klangbild.ui.stage.BaseWall
import io.github.hazymorning.klangbild.ui.stage.StageModel
import io.github.hazymorning.klangbild.ui.stage.StatusLayer
import io.github.hazymorning.klangbild.ui.stage.TextLayer
import io.github.hazymorning.klangbild.ui.stage.WallLayer
import io.github.hazymorning.klangbild.ui.stage.asColor
import io.github.hazymorning.klangbild.ui.stage.rememberTextPlan

/**
 * A complete, static cover flow stage for stills: the wall, the cards at rest (the current one
 * in the middle at position 0) and the text column. The app's stage ([FlowTheatre]) composes
 * the same layers with its transitions.
 */
@Composable
fun FlowStage(model: StageModel, cards: List<CardMotion>, modifier: Modifier = Modifier) {
    val scene = model.scene
    val geometry = remember(scene.layout) { FlowGeometry(scene.layout) }
    val plan = rememberTextPlan(model)
    Box(modifier.fillMaxSize()) {
        BaseWall(scene.palette.wall.asColor())
        WallLayer(scene)
        FlowStrip(cards, geometry, scene.appearance, scene.lighting, scene.palette.wall.asColor(), edges = !scene.coverField)
        TextLayer(model, plan = plan)
        StatusLayer(model)
    }
}
