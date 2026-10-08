package io.github.hazymorning.klangbild.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders all synthetic covers and some generated Chladni figures onto one sheet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CoverSheetTest {
    @Test
    fun coverSheet() {
        val cell = 400
        val gap = 24
        val items = SyntheticCover.entries.map { SyntheticCovers.render(it) } +
            listOf("Deutschlandfunk Kultur", "SWR2", "Radio Paradise", "FIP", "Lokale Datei").map { name ->
                val px = ChladniArt.render(ChladniArt.specFor(name), 640)
                Bitmap.createBitmap(px, 640, 640, Bitmap.Config.ARGB_8888)
            }
        val cols = 4
        val rows = (items.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(cols * (cell + gap) + gap, rows * (cell + gap) + gap, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF101010.toInt())
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        items.forEachIndexed { i, bmp ->
            val x = gap + (i % cols) * (cell + gap)
            val y = gap + (i / cols) * (cell + gap)
            c.drawBitmap(bmp, null, Rect(x, y, x + cell, y + cell), p)
        }
        val out = File("build/outputs/sheets/covers.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
