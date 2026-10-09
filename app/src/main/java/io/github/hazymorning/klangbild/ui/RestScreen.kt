package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
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
import io.github.hazymorning.klangbild.ui.theme.textType
import io.github.hazymorning.klangbild.ui.theme.titleType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Quiet colours for screens without a cover: soft white on black, or near-black on warm
 * paper – dimmed with the day either way, but however late it is every line still reads
 * (the quietest at least 4.5:1).
 */
data class QuietColors(val wall: Color, val primary: Color, val secondary: Color, val label: Color) {
    companion object {
        fun of(lighting: Lighting, appearance: Appearance = Appearance.DARK): QuietColors {
            fun lit(l: Float, ch: Float) = lighting.apply(Lch(l, ch, HaloLight.LAMP_HUE).toLab())
            return if (appearance == Appearance.DARK) {
                fun c(l: Float, least: Float) = lit(l, 0.004f).let { Color(Lab(maxOf(it.l, least), it.a, it.b).toArgb()) }
                QuietColors(wall = Color.Black, primary = c(0.84f, 0.70f), secondary = c(0.68f, 0.62f), label = c(0.62f, 0.57f))
            } else {
                val wall = lit(CoverPalette.PAPER, 0.006f).toArgb()
                val most = CoverPalette.lightestInkOn(wall)
                fun c(l: Float, step: Float) = lit(l, 0.004f).let { Color(Lab(minOf(it.l, most - step), it.a, it.b).toArgb()) }
                QuietColors(wall = Color(wall), primary = c(0.28f, 0.16f), secondary = c(0.42f, 0.08f), label = c(0.5f, 0f))
            }
        }
    }
}

object RestTexts {
    /** What the rest screen says besides the time. */
    data class Lines(
        /** A short label ("Wohnzimmer · Pausiert"). */
        val label: String?,
        /** A calm sentence, only when something needs explaining. */
        val sentence: String?,
    )

    fun lines(reason: RestReason, speaker: String?, entityId: String?): Lines = when (reason) {
        RestReason.IDLE -> Lines(speaker, null)
        RestReason.PAUSED -> Lines(listOfNotNull(speaker, "Pausiert").joinToString(TimeLine.SEPARATOR), null)
        RestReason.CONNECTING -> Lines(speaker, "Verbindung wird hergestellt …")
        RestReason.OFFLINE -> Lines(null, "Home\u00A0Assistant ist nicht erreichbar.")
        RestReason.UNAVAILABLE -> Lines(speaker ?: "Lautsprecher", "Gerade nicht erreichbar.")
        RestReason.ENTITY_MISSING -> Lines(
            null,
            "Home\u00A0Assistant kennt „${entityId ?: "diesen Lautsprecher"}“ nicht.\nZum Einrichten OK lange drücken.",
        )
    }

    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    fun date(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "2:13", "21:47" – no leading zero, as a clock on a wall would show it. */
    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = TimeLine.clock(epochMs, zone)
}

/**
 * The rest screen's light and composition: the clock hangs on the wall where a lamp lights
 * it – a soft oval of warm light just above the clock block, both re-hung together every
 * quarter of an hour so no stroke stays in place. By day the lamp is a breath of light, in
 * the evening and at night less – turned down, never off.
 */
object RestLight {
    const val MAP_W = 480
    const val MAP_H = 270

    /** Radii of the pool of light (dp); it reaches black at 1.6 × these. */
    private const val RX = 275f
    private const val RY = 175f

    /** The oval vignette reaches black this far from the screen edges (dp). */
    private const val EDGE_DP = 36f

    /**
     * Greyscale light field for the whole screen (sRGB-encoded, dithered), brightest at
     * [cx], [cy] (dp), black well before the screen edges.
     */
    fun field(cx: Float, cy: Float, screen: ScreenSpec, ditherLsb: Float): IntArray {
        val img = LinearImage(MAP_W, MAP_H)
        val sx = screen.widthDp / MAP_W
        val sy = screen.heightDp / MAP_H
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val px = (x + 0.5f) * sx
            val py = (y + 0.5f) * sy
            val u = (px - cx) / RX
            val v = (py - cy) / RY
            val q = u * u + v * v
            val reach = (1f - q / (1.6f * 1.6f)).coerceAtLeast(0f)
            // Round all the way: an oval vignette keeps the screen edges black, too.
            val ex = (px - screen.widthDp / 2f) / (screen.widthDp / 2f - EDGE_DP)
            val ey = (py - screen.heightDp / 2f) / (screen.heightDp / 2f - EDGE_DP)
            val vignette = HaloLight.smoothstep(1f, 0.2f, ex * ex + ey * ey)
            val value = exp(-q * 1.4f) * reach * reach * vignette
            val o = y * MAP_W + x
            img.r[o] = value; img.g[o] = value; img.b[o] = value
        }
        return ImageOps.toArgbDithered(img, seed = 11, ditherLsb = ditherLsb, blackBelow = 0.0005f)
    }

    /** Brightest point of the light (relative luminance): lower in the evening, lowest at night. */
    fun peak(lighting: Lighting): Float {
        val k = ((lighting.brightness - 0.7f) / 0.3f).coerceIn(0f, 1f)
        return 0.006f + 0.008f * k
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
 * says why – never an error code. At night the lamp is turned down; nothing is left out.
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
    /** The lamp's strength from the settings (0 = off). */
    lightFactor: Float = 1f,
    appearance: Appearance = Appearance.DARK,
) {
    val colors = QuietColors.of(lighting, appearance)
    val lines = RestTexts.lines(reason, speaker, entityId)
    // A lamp only lights a dark wall.
    val peak = (RestLight.peak(lighting) * lightFactor).takeIf { it > 0f && appearance == Appearance.DARK }
    val tint = remember(lighting, peak) { peak?.let { HaloLight.lightTint(HaloLight.LAMP, it, lighting) } }
    val screen = ScreenSpec(960, 540, 1f)

    Box(modifier.fillMaxSize()) {
        BaseWall(colors.wall)
        val slot = nowMs / (15 * 60_000L)
        Crossfade(targetState = slot, animationSpec = tween(2_400, easing = Motion.Dimmer), label = "re-hang") { s ->
            val (dx, dy) = RestLight.blockOffset(s * 15 * 60_000L)
            // The lamp hangs just above the clock and moves with it.
            val field = rememberOffMain(tint, s) {
                tint?.let {
                    val px = RestLight.field(480f + dx, 270f + dy - 18f - 35f, screen, SceneBuilder.ditherFor(it))
                    Bitmap.createBitmap(px, RestLight.MAP_W, RestLight.MAP_H, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (field != null && tint != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            field,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            colorFilter = ColorFilter.tint(Color(tint), BlendMode.Modulate),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
                Box(Modifier.fillMaxSize().offset(dx.dp, (dy - 18f).dp), contentAlignment = Alignment.Center) {
                    RestBlock(
                        time = RestTexts.time(nowMs, zone),
                        date = RestTexts.date(nowMs, zone),
                        label = lines.label,
                        sentence = lines.sentence,
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestBlock(time: String, date: String?, label: String?, sentence: String?, colors: QuietColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val clock = titleType(132f, 320, colors.primary, lineHeight = 140f, tracking = -0.01f).merge(TextStyle(textAlign = TextAlign.Center))
        // The figures keep their own widths (in cells this large the "1" would stand apart);
        // a new minute dissolves in, so the re-centred line never jumps.
        Crossfade(targetState = time, Modifier.fillMaxWidth(), animationSpec = tween(700, easing = Motion.Dimmer), label = "minute") { t ->
            BasicText(t, Modifier.fillMaxWidth(), style = clock)
        }
        if (date != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(date, style = textType(22f, 420, colors.secondary).merge(TextStyle(textAlign = TextAlign.Center)))
        }
        if (label != null) {
            Spacer(Modifier.height(28.dp))
            BasicText(label, style = textType(16f, 500, colors.label).merge(TextStyle(textAlign = TextAlign.Center)))
        }
        if (sentence != null) {
            Spacer(Modifier.height(if (label != null) 10.dp else 28.dp))
            BasicText(
                sentence,
                style = textType(19f, 420, colors.secondary, lineHeight = 27f).merge(
                    TextStyle(
                        textAlign = TextAlign.Center,
                        lineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default),
                    ),
                ),
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
}
