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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.SetupReason
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.HaloLayout
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.render.ScreenSpec
import io.github.hazymorning.klangbild.setup.SetupInfo
import io.github.hazymorning.klangbild.ui.stage.BaseWall
import io.github.hazymorning.klangbild.ui.stage.HaloCoverLayer
import io.github.hazymorning.klangbild.ui.stage.HaloLightLayer
import io.github.hazymorning.klangbild.ui.stage.SpacedLabel
import io.github.hazymorning.klangbild.ui.stage.asColor
import io.github.hazymorning.klangbild.ui.theme.Fonts
import kotlin.math.max

object SetupArt {
    /**
     * The QR code as a printed card at the exact on-screen pixel size of the cover slot:
     * ivory paper, dark modules on whole pixels, generous quiet zone. Becomes the "cover" of
     * the setup scene, so it hangs on the wall in the same light as an album.
     */
    fun card(qr: Array<BooleanArray>?, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFEAE2D4.toInt())
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

    fun scene(info: SetupInfo?, screen: ScreenSpec, lighting: Lighting): ArtworkScene {
        // Text first (left), then the code on the wall (right). Setup asks for action, so
        // it takes only a little of the evening's dimming.
        val layout = HaloLayout(screen, mirrored = true)
        val px = (layout.slot * screen.density).toInt()
        val card = card(info?.qr, px)
        return SceneBuilder.build(
            "setup|${info?.url}|${info?.code}", card, "setup", screen, Lighting.DAY.lerp(lighting, 0.35f), mirrored = true,
        )
    }

    /** "http://192.168.1.23:8765" → "192.168.1.23:8765" (what people actually type). */
    fun shortUrl(url: String): String = url.removePrefix("http://").removePrefix("https://").trimEnd('/')
}

/**
 * Setup: the QR code hangs on the wall like a cover; the column explains in two sentences
 * what to do. The six-digit code is printed large for typing the address by hand. Few
 * voices: the name, one italic sentence, one plain paragraph, labels, the code.
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
    val label = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 10.sp, color = p.inkFaint.asColor())
    val body = TextStyle(fontFamily = Fonts.Text, fontSize = 16.sp, lineHeight = 23.sp, color = p.inkMuted.asColor(), lineBreak = balanced)
    Box(modifier.fillMaxSize()) {
        BaseWall()
        HaloLightLayer(scene, sunAngle = { HaloLight.sunAngle(0.5f, 0L) })
        HaloCoverLayer(scene, sunAngle = { HaloLight.sunAngle(0.5f, 0L) })

        val lead = when (reason) {
            SetupReason.FIRST_RUN -> "Einrichten mit dem Handy"
            SetupReason.AUTH_FAILED -> "Home\u00A0Assistant hat das Zugangstoken abgelehnt."
            SetupReason.REQUESTED -> "Einstellungen"
        }
        val text = when (reason) {
            SetupReason.AUTH_FAILED ->
                "Erstelle in Home\u00A0Assistant ein neues langlebiges Zugangstoken, scanne den Code und füge es auf der Seite ein."
            // A short word that opens a sentence never ends a line: "Auf der Seite" stays together.
            else ->
                "Scanne den Code mit der Kamera. Auf\u00A0der\u00A0Seite verbindest du Home\u00A0Assistant und wählst den Lautsprecher."
        }
        val hint = when (reason) {
            SetupReason.FIRST_RUN -> "OK drücken: Demo ansehen"
            SetupReason.REQUESTED -> "Zurück: schließen"
            SetupReason.AUTH_FAILED -> null
        }
        Column(
            Modifier
                .offset(layout.columnLeft.dp, layout.slotTop.dp)
                .width(layout.columnWidth.dp),
        ) {
            BasicText(
                "Klangbild",
                style = TextStyle(fontFamily = Fonts.Display, fontWeight = FontWeight(340), fontSize = 52.sp, color = p.ink.asColor()),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                lead,
                style = TextStyle(
                    fontFamily = Fonts.Text,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    color = p.ink.asColor(),
                    lineBreak = balanced,
                ),
            )
            Spacer(Modifier.height(16.dp))
            BasicText(text, style = body)
            Spacer(Modifier.height(30.dp))
            SpacedLabel("Code", label, tracking = 0.22.em)
            Spacer(Modifier.height(6.dp))
            BasicText(
                info?.code?.chunked(3)?.joinToString("\u2009") ?: "—",
                style = TextStyle(
                    fontFamily = Fonts.Sans,
                    fontWeight = FontWeight(300),
                    fontSize = 34.sp,
                    letterSpacing = 0.08.em,
                    color = p.ink.asColor(),
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                info?.url?.let { "oder im Browser: ${SetupArt.shortUrl(it)}" } ?: "Keine Netzwerkverbindung",
                style = body,
            )
        }
        if (hint != null) {
            SpacedLabel(
                hint,
                label,
                tracking = 0.22.em,
                modifier = Modifier.offset(layout.columnLeft.dp, (layout.timeLineBaseline - 10f).dp),
            )
        }
    }
}
