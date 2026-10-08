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
        // Text first (left), then the code on the wall (right).
        val layout = HaloLayout(screen, mirrored = true)
        val px = (layout.slot * screen.density).toInt()
        val card = card(info?.qr, px)
        return SceneBuilder.build("setup|${info?.url}|${info?.code}", card, "setup", screen, lighting, mirrored = true)
    }
}

/**
 * Setup: the QR code hangs on the wall like a cover; the column explains in two sentences
 * what to do. The six-digit code is printed large for typing the address by hand.
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
    Box(modifier.fillMaxSize()) {
        BaseWall(scene)
        HaloLightLayer(scene, sunAngle = { HaloLight.sunAngle(0.5f, 0L) })
        HaloCoverLayer(scene)

        val lead = when (reason) {
            SetupReason.FIRST_RUN -> "Einrichten mit dem Handy"
            SetupReason.AUTH_FAILED -> "Home Assistant hat den Zugangsschlüssel abgelehnt."
            SetupReason.REQUESTED -> "Einstellungen"
        }
        val body = when (reason) {
            SetupReason.AUTH_FAILED ->
                "Scanne den Code und füge einen neuen langlebigen Zugangsschlüssel ein."
            else ->
                "Scanne den Code mit der Kamera. Auf der Seite verbindest du Home Assistant und wählst den Lautsprecher."
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
                    color = p.inkMuted.asColor(),
                ),
            )
            Spacer(Modifier.height(18.dp))
            BasicText(
                body,
                style = TextStyle(fontFamily = Fonts.Text, fontSize = 16.sp, lineHeight = 23.sp, color = p.inkFaint.asColor()),
            )
            Spacer(Modifier.height(30.dp))
            SpacedLabel(
                "Code",
                TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 12.sp, color = p.inkFaint.asColor()),
                tracking = 0.22.em,
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                info?.code?.chunked(3)?.joinToString(" ") ?: "—",
                style = TextStyle(
                    fontFamily = Fonts.Sans,
                    fontWeight = FontWeight(300),
                    fontSize = 34.sp,
                    letterSpacing = 0.12.em,
                    color = p.ink.asColor(),
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                info?.url?.let { "oder im Browser: $it" } ?: "Keine Netzwerkverbindung",
                style = TextStyle(fontFamily = Fonts.Sans, fontSize = 13.sp, letterSpacing = 0.04.em, color = p.inkFaint.asColor()),
            )
        }
        if (hint != null) {
            SpacedLabel(
                hint,
                TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight(500), fontSize = 12.sp, color = p.inkFaint.asColor()),
                tracking = 0.2.em,
                modifier = Modifier.offset(layout.columnLeft.dp, (layout.timeLineBaseline - 12f).dp),
            )
        }
    }
}
