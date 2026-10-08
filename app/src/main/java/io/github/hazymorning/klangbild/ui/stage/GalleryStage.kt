package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.ui.theme.Fonts

/**
 * GALERIE – the cover framed behind a wide passe-partout on a museum wall at night,
 * with a small exhibition label beside it.
 */
@Composable
fun GalleryStage(model: StageModel, modifier: Modifier = Modifier) {
    val scene = model.scene
    val p = scene.palette
    val (backdrop, cover) = rememberSceneImages(scene)
    val rect = scene.coverRect
    val frame = SceneBuilder.frameRect(scene.style, rect)
    val hue = p.tintHue ?: CoverPalette.WARM_HUE
    val matColor = Color(scene.lighting.applyArgb(Lch(0.885f, 0.012f, hue).toArgb()))
    val frameColor = Color(scene.lighting.applyArgb(Lch(0.16f, 0.006f, hue).toArgb()))
    val bevelColor = Color(scene.lighting.applyArgb(Lch(0.965f, 0.006f, CoverPalette.WARM_HUE).toArgb()))

    Box(modifier.fillMaxSize()) {
        Backdrop(backdrop)
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val border = rect.width * SceneBuilder.GALLERY_FRAME * d
            // frame
            drawRect(frameColor, Offset(frame.left * d, frame.top * d), Size(frame.width * d, frame.height * d))
            // mat, lit from above
            val matL = frame.left * d + border
            val matT = frame.top * d + border
            val matW = frame.width * d - 2 * border
            val matH = frame.height * d - 2 * border
            drawRect(matColor, Offset(matL, matT), Size(matW, matH))
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.05f),
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.10f),
                    startY = matT, endY = matT + matH,
                ),
                Offset(matL, matT), Size(matW, matH),
            )
            // bevel: the white core of the cut mat board
            val bevel = 2.2f * d
            drawRect(
                bevelColor,
                Offset(rect.left * d - bevel, rect.top * d - bevel),
                Size(rect.width * d + 2 * bevel, rect.height * d + 2 * bevel),
            )
        }
        CoverPlate(cover, rect, scene.lighting)
        // the mat's edge casts a hair of shadow onto the print
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    startY = rect.top * d, endY = rect.top * d + 7f * d,
                ),
                Offset(rect.left * d, rect.top * d), Size(rect.width * d, 7f * d),
            )
        }

        val labelLeft = frame.right + 46f
        val labelWidth = scene.screen.widthDp - labelLeft - 72f
        Column(
            Modifier
                .offset(labelLeft.dp, frame.top.dp)
                .width(labelWidth.dp)
                .height(frame.height.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val t = model.text
            t.artists?.let {
                SpacedLabel(
                    it,
                    TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 18.sp, color = p.inkMuted.asColor()),
                    maxLines = 2,
                    tracking = 0.18.em,
                )
                Spacer(Modifier.height(10.dp))
            }
            t.work?.let {
                BasicText(it, style = TextStyle(fontFamily = Fonts.Text, fontSize = 15.sp, lineHeight = 20.sp, color = p.inkMuted.asColor()), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
            FittingText(
                t.title,
                TextStyle(fontFamily = Fonts.Display, fontStyle = FontStyle.Italic, fontWeight = FontWeight(360), color = p.ink.asColor()),
                sizes = listOf(30.sp, 26.sp, 23.sp, 20.sp),
                maxLines = 4,
                lineHeight = 1.12f,
            )
            Spacer(Modifier.height(10.dp))
            val meta = listOfNotNull(t.album, t.version).joinToString(" · ")
            if (meta.isNotEmpty()) {
                BasicText(meta, style = TextStyle(fontFamily = Fonts.Text, fontSize = 15.sp, lineHeight = 20.sp, color = p.inkFaint.asColor()), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(22.dp))
            ProgressHairline(
                progress = model::progress,
                track = p.inkFaint.asColor().copy(alpha = 0.25f),
                fill = p.inkMuted.asColor(),
                modifier = Modifier.width(150.dp).height(4.dp),
                thickness = 1.2.dp,
            )
            if (model.durationMs != null) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    formatTime(model.positionMs()) + "  /  " + formatTime(model.durationMs),
                    style = TextStyle(fontFamily = Fonts.Sans, fontSize = 11.sp, letterSpacing = 0.08.em, color = p.inkFaint.asColor(), fontFeatureSettings = "tnum"),
                )
            }
        }

        model.clock?.let {
            BasicText(
                it,
                style = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(300), fontSize = 18.sp, letterSpacing = 0.06.em, color = p.inkFaint.asColor(), fontFeatureSettings = "tnum"),
                modifier = Modifier.align(Alignment.TopEnd).offset((-72).dp, 48.dp),
            )
        }
        model.nextUp?.let { next ->
            Row(Modifier.align(Alignment.BottomEnd).offset((-72).dp, (-44).dp)) {
                BasicText(
                    "Danach: " + listOfNotNull(next.title, next.artists).joinToString(" · "),
                    style = TextStyle(fontFamily = Fonts.Text, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = p.inkFaint.asColor()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
