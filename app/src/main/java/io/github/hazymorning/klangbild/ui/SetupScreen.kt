package io.github.hazymorning.klangbild.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.HaloLayout
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.ui.stage.ArtworkLayer
import io.github.hazymorning.klangbild.ui.stage.BaseWall
import io.github.hazymorning.klangbild.ui.stage.WallLayer
import io.github.hazymorning.klangbild.ui.stage.asColor
import io.github.hazymorning.klangbild.ui.theme.Fonts
import io.github.hazymorning.klangbild.ui.theme.textType
import io.github.hazymorning.klangbild.ui.theme.titleType
import kotlin.math.max

object SetupArt {
    /**
     * The QR code as a printed card at the exact on-screen pixel size of the cover slot:
     * ivory paper (a whiter one on the light wall), dark modules on whole pixels, generous
     * quiet zone. Becomes the "cover" of the setup scene, so it hangs on the wall in the same
     * light as an album.
     */
    fun card(qr: Array<BooleanArray>?, sizePx: Int, appearance: Appearance = Appearance.DARK): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (appearance == Appearance.DARK) 0xFFEAE2D4.toInt() else 0xFFF7F4EE.toInt())
        if (qr != null) {
            val n = qr.size
            val module = max(1, (sizePx * 0.80f / n).toInt())
            val offset = (sizePx - module * n) / 2
            val p = Paint().apply { color = 0xFF1C1814.toInt(); isAntiAlias = false }
            for (y in 0 until n) for (x in 0 until n) {
                if (qr[y][x]) {
                    val l = offset + x * module
                    val t = offset + y * module
                    c.drawRect(l.toFloat(), t.toFloat(), (l + module).toFloat(), (t + module).toFloat(), p)
                }
            }
        }
        return bmp
    }

    fun scene(info: SetupInfo?, screen: ScreenSpec, lighting: Lighting, appearance: Appearance = Appearance.DARK): ArtworkScene {
        // The code hangs where the artwork would; setup asks for action, so it takes only a
        // little of the evening's dimming.
        val layout = HaloLayout(screen)
        val px = (layout.art * screen.density).toInt()
        val card = card(info?.qr, px, appearance)
        return SceneBuilder.build("setup|${info?.url}|${info?.code}", card, "setup", screen, Lighting.DAY.lerp(lighting, 0.35f), appearance)
    }

    /** "http://192.168.1.23:8765" → "192.168.1.23:8765" (what people actually type). */
    fun shortUrl(url: String): String = url.removePrefix("http://").removePrefix("https://").trimEnd('/')
}

/**
 * Setup: the QR code hangs on the wall where the artwork would, in the same light; beside
 * it two sentences say what to do. The six-digit code is set large for typing the address by
 * hand.
 */
@Composable
fun SetupScreen(
    reason: SetupReason,
    info: SetupInfo?,
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
) {
    val p = scene.palette
    val layout = scene.layout
    val balanced = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default)
    val label = textType(15f, 500, p.inkQuiet.asColor(), tracking = 0.01f)
    val body = textType(19f, 420, p.inkMuted.asColor(), lineHeight = 27f).merge(TextStyle(lineBreak = balanced))
    Box(modifier.fillMaxSize()) {
        BaseWall(p.wall.asColor())
        WallLayer(scene)
        ArtworkLayer(scene)

        val lead = when (reason) {
            SetupReason.FIRST_RUN -> "Einrichten mit dem Handy"
            SetupReason.AUTH_FAILED -> "Home\u00A0Assistant hat das\u00A0Zugangstoken abgelehnt."
            SetupReason.REQUESTED -> "Verbindung ändern"
        }
        // A short word that opens a sentence never ends a line ("Auf der Seite" stays
        // together), and articles and adjectives stay with their noun.
        val text = when (reason) {
            SetupReason.AUTH_FAILED ->
                "Erstelle in Home\u00A0Assistant ein\u00A0neues\u00A0langlebiges\u00A0Zugangstoken, scanne den\u00A0Code und füge es auf der Seite ein."
            else ->
                "Scanne den\u00A0Code mit\u00A0der\u00A0Kamera. Auf\u00A0der\u00A0Seite verbindest du Home\u00A0Assistant und wählst den\u00A0Lautsprecher."
        }
        val hint = if (reason == SetupReason.REQUESTED) "Zurück schließt" else null
        // The block hangs from the card's top edge: the name's cap height on it.
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Column(Modifier.width(layout.columnWidth.dp)) {
                    BasicText("Klangbild", style = titleType(44f, 420, p.ink.asColor(), tracking = -0.005f))
                    Spacer(Modifier.height(4.dp))
                    BasicText(lead, style = textType(21f, 520, p.inkMuted.asColor(), lineHeight = 27f).merge(TextStyle(lineBreak = balanced)))
                    Spacer(Modifier.height(20.dp))
                    BasicText(text, style = body)
                    Spacer(Modifier.height(30.dp))
                    BasicText("Code", style = label)
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        info?.code?.chunked(3)?.joinToString(" ") ?: "—",
                        style = titleType(44f, 400, p.ink.asColor(), tracking = 0.01f),
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        info?.url?.let { url ->
                            buildAnnotatedString {
                                append("oder im Browser: ")
                                // A little air between the figures, so the dots stay visible from the sofa.
                                withStyle(SpanStyle(letterSpacing = 0.03.em)) { append(SetupArt.shortUrl(url)) }
                            }
                        } ?: AnnotatedString("Keine Netzwerkverbindung"),
                        style = body.copy(color = p.inkQuiet.asColor()),
                    )
                }
            },
        ) { measurables, constraints ->
            val block = measurables.first().measure(Constraints())
            val first = block[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: 0
            val baseline = (layout.artTop * density).roundToInt() + (44f * fontScale * density * Fonts.CAP_HEIGHT).roundToInt()
            layout(constraints.maxWidth, constraints.maxHeight) {
                block.place((layout.columnLeft * density).roundToInt(), baseline - first)
            }
        }
        if (hint != null) {
            BasicText(
                hint,
                style = label,
                modifier = Modifier.offset(layout.columnLeft.dp, (layout.artTop + layout.art - 18f).dp),
            )
        }
    }
}
