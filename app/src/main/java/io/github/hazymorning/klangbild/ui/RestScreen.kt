package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.ImageOps
import io.github.hazymorning.klangbild.render.LinearImage
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.ui.stage.BaseWall
import io.github.hazymorning.klangbild.ui.stage.SpacedLabel
import io.github.hazymorning.klangbild.ui.theme.Fonts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Quiet colours for screens without a cover: warm ivory on black, dimmed with the day. */
data class QuietColors(val primary: Color, val secondary: Color, val label: Color) {
    companion object {
        fun of(lighting: Lighting): QuietColors {
            fun c(l: Float, ch: Float) = Color(lighting.applyArgb(Lch(l, ch, HaloLight.LAMP_HUE).toArgb()))
            return QuietColors(
                primary = c(0.80f, 0.016f),
                secondary = c(0.66f, 0.018f),
                label = c(0.60f, 0.016f),
            )
        }
    }
}

object RestTexts {
    /** What the rest screen says besides the time. */
    data class Lines(
        /** Short label in spaced capitals ("WOHNZIMMER · PAUSIERT"). */
        val label: String?,
        /** A calm sentence, only when something needs explaining. */
        val sentence: String?,
    )

    fun lines(reason: RestReason, speaker: String?, entityId: String?): Lines = when (reason) {
        RestReason.IDLE -> Lines(speaker, null)
        RestReason.PAUSED -> Lines(listOfNotNull(speaker, "Pausiert").joinToString(TimeLine.SEPARATOR), null)
        RestReason.CONNECTING -> Lines(speaker, "Verbindung wird hergestellt …")
        RestReason.OFFLINE -> Lines("Home Assistant", "Verbindung unterbrochen.")
        RestReason.UNAVAILABLE -> Lines(speaker ?: "Lautsprecher", "Gerade nicht erreichbar.")
        RestReason.ENTITY_MISSING -> Lines(
            null,
            "„${entityId ?: "Lautsprecher"}“ kennt Home Assistant nicht.\nZum Einrichten OK lange drücken.",
        )
    }

    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    fun date(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}

/**
 * The rest screen's light and composition: a large, soft wash of lamp light on the wall
 * whose brightest part wanders with the real sun (left in the morning, high at noon, right
 * in the evening; almost nothing at night), and the clock block, which is re-hung every
 * quarter of an hour so no stroke stays in place.
 */
object RestLight {
    const val MAP_W = 480
    const val MAP_H = 270

    /** Centre of the light (dp) for the local time of day. */
    fun center(hourOfDay: Float, screenW: Float = 960f): Pair<Float, Float> {
        val day = ((hourOfDay - 6f) / 12f).coerceIn(-0.08f, 1.08f) // 06:00 → 0, 18:00 → 1
        val x = screenW / 2f + (day - 0.5f) * 2f * 120f
        val y = 262f - 24f * sin(PI * day.coerceIn(0f, 1f)).toFloat()
        return x to y
    }

    /**
     * Greyscale light field for the whole screen (sRGB-encoded, dithered), brightest at
     * [cx], [cy] (dp), nothing near the screen edges.
     */
    fun field(cx: Float, cy: Float, screen: ScreenSpec, ditherLsb: Float): IntArray {
        val img = LinearImage(MAP_W, MAP_H)
        val sx = screen.widthDp / MAP_W
        val sy = screen.heightDp / MAP_H
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val px = (x + 0.5f) * sx
            val py = (y + 0.5f) * sy
            // An oval of light that has faded out long before the screen edges.
            val u = (px - cx) / 280f
            val v = (py - cy) / 175f
            val value = exp(-(u * u + v * v) * 2.2f) * HaloLight.edgeWindow(px, py, screen)
            val o = y * MAP_W + x
            img.r[o] = value; img.g[o] = value; img.b[o] = value
        }
        return ImageOps.toArgbDithered(img, seed = 11, ditherLsb = ditherLsb, blackBelow = 0.0005f)
    }

    /** Brightest point of the light (relative luminance): a breath by day, almost nothing at night. */
    fun peak(lighting: Lighting): Float {
        val k = ((lighting.brightness - 0.55f) / 0.45f).coerceIn(0f, 1f)
        return (0.016f * Math.pow(k.toDouble(), 1.6).toFloat()).coerceAtLeast(0.003f)
    }

    /** Offsets (dp) of the clock block, one per quarter hour. */
    private val positions = listOf(0f to 0f, -150f to -26f, 140f to 18f, -70f to 34f, 110f to -30f, -160f to 12f)

    fun blockOffset(epochMs: Long): Pair<Float, Float> {
        val slot = epochMs / (15 * 60_000L)
        return positions[Math.floorMod(stableHash(slot.toString()), positions.size)]
    }
}

/**
 * Nothing is playing: a quiet clock on a dim, lamp-lit wall, the date, and one line that
 * says why – never an error code. Night leaves only the clock (and a problem, if any).
 */
@Composable
fun RestScreen(
    reason: RestReason,
    speaker: String?,
    entityId: String?,
    lighting: Lighting,
    nowMs: Long,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = QuietColors.of(lighting)
    val lines = RestTexts.lines(reason, speaker, entityId)
    val night = lighting.brightness < 0.7f
    val local = Instant.ofEpochMilli(nowMs).atZone(zone)
    val hour = local.hour + local.minute / 60f

    val tint = remember(lighting) {
        HaloLight.lightTint(HaloLight.LAMP, RestLight.peak(lighting), 1f, lighting)
    }
    // The light moves with the sun; re-rendered at most every quarter of an hour.
    val quarter = (hour * 4f).toInt()
    val screen = ScreenSpec(960, 540, 1f)
    val field = remember(tint, quarter) {
        val (cx, cy) = RestLight.center(quarter / 4f)
        val px = RestLight.field(cx, cy, screen, SceneBuilder.ditherFor(tint))
        Bitmap.createBitmap(px, RestLight.MAP_W, RestLight.MAP_H, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    Box(modifier.fillMaxSize()) {
        BaseWall()
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                field,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = ColorFilter.tint(Color(tint), BlendMode.Modulate),
                filterQuality = FilterQuality.Low,
            )
        }
        val slot = nowMs / (15 * 60_000L)
        Crossfade(targetState = slot, animationSpec = tween(2_400, easing = Motion.Dimmer), label = "re-hang") { s ->
            val (dx, dy) = RestLight.blockOffset(s * 15 * 60_000L)
            Box(Modifier.fillMaxSize().offset(dx.dp, (dy - 18f).dp), contentAlignment = Alignment.Center) {
                RestBlock(
                    time = RestTexts.time(nowMs, zone),
                    date = if (night) null else RestTexts.date(nowMs, zone),
                    label = if (night) null else lines.label,
                    sentence = lines.sentence,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun RestBlock(time: String, date: String?, label: String?, sentence: String?, colors: QuietColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            time,
            style = TextStyle(
                fontFamily = Fonts.Display,
                fontWeight = FontWeight(300),
                fontSize = 48.sp,
                letterSpacing = 0.01.em,
                color = colors.primary,
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
            ),
        )
        if (date != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                date,
                style = TextStyle(
                    fontFamily = Fonts.Text,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (label != null) {
            Spacer(Modifier.height(26.dp))
            SpacedLabel(
                label,
                TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 10.sp, color = colors.label),
                tracking = 0.22.em,
            )
        }
        if (sentence != null) {
            Spacer(Modifier.height(if (label != null) 10.dp else 26.dp))
            BasicText(
                sentence,
                style = TextStyle(
                    fontFamily = Fonts.Text,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                    lineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default),
                ),
                modifier = Modifier.widthIn(max = 440.dp),
            )
        }
    }
}
