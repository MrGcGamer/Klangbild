package io.github.hazymorning.klangbild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.RestReason
import io.github.hazymorning.klangbild.ui.stage.SpacedLabel
import io.github.hazymorning.klangbild.ui.theme.Fonts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quiet colours for screens without a cover: warm, dim, OLED-friendly. */
data class QuietColors(val wall: Color, val primary: Color, val secondary: Color, val label: Color) {
    companion object {
        fun of(lighting: Lighting): QuietColors {
            fun c(l: Float, ch: Float) = Color(lighting.applyArgb(Lch(l, ch, CoverPalette.WARM_HUE).toArgb()))
            return QuietColors(
                wall = c(0.105f, 0.006f),
                primary = c(0.70f, 0.014f),
                secondary = c(0.58f, 0.016f),
                label = c(0.52f, 0.014f),
            )
        }
    }
}

object RestTexts {
    /** Short label in spaced capitals, and an optional sentence below it. */
    fun lines(reason: RestReason, speaker: String?, entityId: String?): Pair<String?, String?> = when (reason) {
        RestReason.IDLE -> speaker to null
        RestReason.PAUSED -> speaker to "Pausiert."
        RestReason.CONNECTING -> "Home Assistant" to "Verbindung wird hergestellt …"
        RestReason.OFFLINE -> "Home Assistant" to "Die Verbindung ist unterbrochen und wird wiederhergestellt …"
        RestReason.UNAVAILABLE -> (speaker ?: "Lautsprecher") to "Gerade nicht erreichbar."
        RestReason.ENTITY_MISSING -> (entityId ?: "Lautsprecher") to
            "Diesen Lautsprecher kennt Home Assistant nicht. Zum Einrichten OK lange drücken."
    }

    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    fun date(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}

/**
 * Nothing is playing: a quiet clock with the date, and one line that says why –
 * never an error code. Night makes it dimmer still.
 */
@Composable
fun RestScreen(
    reason: RestReason,
    speaker: String?,
    entityId: String?,
    lighting: Lighting,
    time: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    val colors = QuietColors.of(lighting)
    val (label, sentence) = RestTexts.lines(reason, speaker, entityId)
    Box(modifier.fillMaxSize().background(colors.wall), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText(
                time,
                style = TextStyle(
                    fontFamily = Fonts.Sans,
                    fontWeight = FontWeight(300),
                    fontSize = 84.sp,
                    letterSpacing = 0.02.em,
                    color = colors.primary,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                date,
                style = TextStyle(
                    fontFamily = Fonts.Text,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                ),
            )
            if (label != null) {
                Spacer(Modifier.height(40.dp))
                SpacedLabel(
                    label,
                    TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 12.sp, color = colors.label),
                    tracking = 0.24.em,
                )
            }
            if (sentence != null) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    sentence,
                    style = TextStyle(
                        fontFamily = Fonts.Text,
                        fontStyle = FontStyle.Italic,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
        }
    }
}
