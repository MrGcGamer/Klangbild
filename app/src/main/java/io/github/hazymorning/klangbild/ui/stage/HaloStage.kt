package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * HALO – the cover as a backlit object in a dark room. Its own colours spill onto the
 * wall as soft light; the typography sits quietly at the edge of that light.
 */
@Composable
fun HaloStage(model: StageModel, modifier: Modifier = Modifier) {
    val scene = model.scene
    val p = scene.palette
    val (backdrop, cover) = rememberSceneImages(scene)
    val rect = scene.coverRect
    val screenW = scene.screen.widthDp
    val maxCover = io.github.hazymorning.klangbild.render.StageLayout.maxCoverDp(scene.style, scene.screen)
    // The text column keeps its position whatever the cover size: calm transitions.
    val columnLeft = rect.centerX + maxCover / 2f + 64f
    val columnWidth = screenW - columnLeft - 88f
    val coverTop = rect.centerY - maxCover / 2f

    Box(modifier.fillMaxSize()) {
        Backdrop(backdrop)
        CoverPlate(cover, rect, scene.lighting)

        // Progress: a hairline beneath the cover, as wide as the cover.
        ProgressHairline(
            progress = model::progress,
            track = p.inkFaint.asColor().copy(alpha = 0.22f),
            fill = p.accent.asColor().copy(alpha = 0.9f),
            modifier = Modifier
                .offset(rect.left.dp, (rect.bottom + 26f).dp)
                .width(rect.width.dp)
                .height(4.dp),
            thickness = 1.5.dp,
        )
        if (model.durationMs != null) {
            Row(
                Modifier
                    .offset(rect.left.dp, (rect.bottom + 36f).dp)
                    .width(rect.width.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val figures = TextStyle(
                    fontFamily = Fonts.Sans,
                    fontWeight = FontWeight(400),
                    fontSize = 11.sp,
                    letterSpacing = 0.08.em,
                    color = p.inkFaint.asColor(),
                    fontFeatureSettings = "tnum",
                )
                BasicText(formatTime(model.positionMs()), style = figures)
                BasicText(formatTime(model.durationMs), style = figures)
            }
        }

        Column(
            Modifier
                .offset(columnLeft.dp, coverTop.dp)
                .width(columnWidth.dp)
                .height(maxCover.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val t = model.text
            if (t.work != null) {
                BasicText(
                    t.work,
                    style = TextStyle(
                        fontFamily = Fonts.Text,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight(380),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = p.inkMuted.asColor(),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }
            FittingText(
                text = t.title,
                style = TextStyle(
                    fontFamily = Fonts.Display,
                    fontWeight = FontWeight(360),
                    color = p.ink.asColor(),
                    letterSpacing = (-0.01).em,
                ),
                sizes = listOf(50.sp, 44.sp, 38.sp, 33.sp, 29.sp),
                maxLines = 3,
                lineHeight = 1.06f,
            )
            Spacer(Modifier.height(18.dp))
            if (t.artists != null) {
                SpacedLabel(
                    t.artists,
                    TextStyle(
                        fontFamily = Fonts.Sans,
                        fontWeight = FontWeight(500),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = p.inkMuted.asColor(),
                    ),
                    maxLines = 2,
                )
            }
            if (t.album != null || t.version != null) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    listOfNotNull(t.album, t.version).joinToString("  ·  "),
                    style = TextStyle(
                        fontFamily = Fonts.Text,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight(380),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = p.inkFaint.asColor(),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        model.clock?.let { clock ->
            BasicText(
                clock,
                style = TextStyle(
                    fontFamily = Fonts.Sans,
                    fontWeight = FontWeight(300),
                    fontSize = 20.sp,
                    letterSpacing = 0.06.em,
                    color = p.inkFaint.asColor(),
                    fontFeatureSettings = "tnum",
                ),
                modifier = Modifier.align(Alignment.TopEnd).offset((-88).dp, 52.dp),
            )
        }

        model.nextUp?.let { next ->
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(columnLeft.dp, (-52).dp)
                    .width(columnWidth.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpacedLabel(
                    "Danach",
                    TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 11.sp, color = p.inkFaint.asColor()),
                    tracking = 0.22.em,
                )
                Spacer(Modifier.size(14.dp))
                BasicText(
                    listOfNotNull(next.title, next.artists).joinToString("  ·  "),
                    style = TextStyle(
                        fontFamily = Fonts.Text,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        color = p.inkFaint.asColor(),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
