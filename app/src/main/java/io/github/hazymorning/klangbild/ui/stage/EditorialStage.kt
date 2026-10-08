package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.ui.theme.Fonts

/**
 * FEUILLETON – the screen as a magazine spread: big condensed serif, a small caps
 * byline in the accent colour, the cover as the plate on the right page.
 */
@Composable
fun EditorialStage(model: StageModel, modifier: Modifier = Modifier) {
    val scene = model.scene
    val p = scene.palette
    val (backdrop, cover) = rememberSceneImages(scene)
    val rect = scene.coverRect
    val left = 84f
    val columnWidth = rect.left - 64f - left

    Box(modifier.fillMaxSize()) {
        Backdrop(backdrop)
        CoverPlate(cover, rect, scene.lighting)
        Column(
            Modifier
                .offset(left.dp, rect.top.dp)
                .width(columnWidth.dp)
                .height(rect.height.dp),
        ) {
            val t = model.text
            Box(Modifier.width(36.dp).height(2.dp).background(p.accent.asColor()))
            Spacer(Modifier.height(14.dp))
            t.artists?.let {
                SpacedLabel(
                    it,
                    TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp, color = p.accent.asColor()),
                    tracking = 0.2.em,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(16.dp))
            t.work?.let {
                BasicText(it, style = TextStyle(fontFamily = Fonts.Text, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 23.sp, color = p.inkMuted.asColor()), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            FittingText(
                t.title,
                TextStyle(fontFamily = Fonts.Condensed, color = p.ink.asColor(), letterSpacing = (-0.005).em),
                sizes = listOf(84.sp, 72.sp, 62.sp, 54.sp, 46.sp, 40.sp),
                maxLines = 3,
                lineHeight = 0.98f,
            )
            Spacer(Modifier.height(14.dp))
            val meta = listOfNotNull(t.album, t.version).joinToString(" · ")
            if (meta.isNotEmpty()) {
                BasicText(meta, style = TextStyle(fontFamily = Fonts.Text, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 24.sp, color = p.inkMuted.asColor()), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            ProgressHairline(
                progress = model::progress,
                track = p.inkFaint.asColor().copy(alpha = 0.3f),
                fill = p.ink.asColor().copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth().height(4.dp),
                thickness = 1.dp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val small = TextStyle(fontFamily = Fonts.Sans, fontSize = 12.sp, letterSpacing = 0.1.em, color = p.inkFaint.asColor(), fontFeatureSettings = "tnum")
                BasicText(if (model.durationMs != null) formatTime(model.positionMs()) + " / " + formatTime(model.durationMs) else "", style = small)
                model.clock?.let { BasicText(it, style = small) }
            }
        }
        model.nextUp?.let { next ->
            BasicText(
                "Danach  " + listOfNotNull(next.title, next.artists).joinToString(" · "),
                style = TextStyle(fontFamily = Fonts.Text, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = p.inkFaint.asColor()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(rect.left.dp, (rect.bottom + 18f).dp).width(rect.width.dp),
            )
        }
    }
}
