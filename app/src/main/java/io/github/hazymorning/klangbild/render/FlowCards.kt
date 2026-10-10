package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import io.github.hazymorning.klangbild.color.Appearance
import kotlin.math.roundToInt

/**
 * One card of the cover flow as the GPU draws it: the print with its softly rounded corners
 * and, below it, its mirror image fading into the floor – one bitmap, so a card is a single
 * textured quad. A transparent border of [PAD] pixels lets the texture filter smooth the
 * card's edges however it is turned.
 *
 * Android draws bitmaps without mipmaps, so a card turned far to the side would shimmer while
 * it moves; [half] is the same card at half the resolution, drawn instead once a card is
 * squeezed to less than about two thirds of its width.
 */
class FlowCard(
    val full: Bitmap,
    val half: Bitmap,
    /**
     * A soft shadow around the card on the light wall (alpha only, small – it is all blur):
     * the card's outline grown by [SHADOW_MARGIN] of its side on every side. None on the dark
     * wall, where the cards cast no shadow.
     */
    val shadow: Bitmap?,
    /** The print's mean lightness (Oklab L), so a bright card beside a dark one can step back. */
    val lightness: Float,
) {
    /** Width and height of the card itself in [full] (pixels), without the border. */
    val sidePx: Int get() = full.width - 2 * PAD

    /** Height of card, gap and reflection in [full] (pixels), without the border. */
    val heightPx: Int get() = full.height - 2 * PAD

    companion object {
        const val PAD = 2

        /** How far the shadow bitmap reaches beyond the card, as a share of its side. */
        const val SHADOW_MARGIN = 0.08f
    }
}

object FlowCards {
    /** Opacity of the mirror image where it meets the card – on the dark gloss only: paper is matte. */
    const val REFLECTION = 0.3f

    /**
     * The card for [cover] (the print at its exact size, [cover] square), [round] for a
     * generated plate (already transparent outside its disc), with
     * corners of [cornerPx] and [geometry]'s gap and reflection height; a hairline of
     * [edgeLine] (ARGB, 0 for none) inside the edge where the print would dissolve into the
     * wall; [lightness] is the print's mean lightness. On paper there is no mirror image.
     */
    fun build(
        cover: Bitmap,
        round: Boolean,
        cornerPx: Float,
        geometry: FlowGeometry,
        density: Float,
        appearance: Appearance,
        edgeLine: Int = 0,
        lightness: Float = 0.5f,
    ): FlowCard {
        val side = cover.width
        val gap = (geometry.gap * density).roundToInt()
        val reflection = (geometry.reflection * density).roundToInt()
        val pad = FlowCard.PAD
        val out = Bitmap.createBitmap(side + 2 * pad, side + gap + reflection + 2 * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val print = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val radius = if (round) 0f else cornerPx

        fun card(top: Float, flipped: Boolean) {
            val m = Matrix()
            if (flipped) m.setScale(1f, -1f) // mirrored about the card's bottom edge
            m.postTranslate(pad.toFloat(), if (flipped) top + side else top)
            shader.setLocalMatrix(m)
            val rect = RectF(pad.toFloat(), top, (pad + side).toFloat(), top + side)
            if (round) c.drawRect(rect, print) else c.drawRoundRect(rect, radius, radius, print)
        }
        card(pad.toFloat(), flipped = false)
        if (edgeLine != 0 && !round) {
            val line = (density / 2f).coerceAtLeast(1f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = line
                color = edgeLine
            }
            val r = RectF(pad + line / 2f, pad + line / 2f, pad + side - line / 2f, pad + side - line / 2f)
            c.drawRoundRect(r, radius - line / 2f, radius - line / 2f, p)
        }
        if (appearance == Appearance.DARK) {
            // The mirror image: the card upside down below a hairline gap, fading out on the way down.
            val top = (pad + side + gap).toFloat()
            val layer = c.saveLayer(0f, top, out.width.toFloat(), top + reflection, null)
            card(top, flipped = true)
            val fade = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                this.shader = LinearGradient(
                    0f, top, 0f, top + reflection,
                    intArrayOf(alpha(REFLECTION), alpha(REFLECTION * 0.35f), alpha(REFLECTION * 0.08f), 0),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, top, out.width.toFloat(), top + reflection, fade)
            c.restoreToCount(layer)
        }
        out.prepareToDraw()
        val half = Bitmap.createScaledBitmap(out, out.width / 2, out.height / 2, true)
        half.prepareToDraw()
        val shadow = if (appearance == Appearance.LIGHT) shadow(round, cornerPx / side).also { it.prepareToDraw() } else null
        return FlowCard(out, half, shadow, lightness)
    }

    /** The soft shadow of a card (or a disc, [round]) as alpha: blurred, for any size. */
    private fun shadow(round: Boolean, corner: Float): Bitmap {
        val n = SHADOW_PX
        val card = n / (1f + 2f * FlowCard.SHADOW_MARGIN)
        val inset = (n - card) / 2f
        val mask = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(mask)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
        val r = RectF(inset, inset, inset + card, inset + card)
        if (round) c.drawOval(r, p) else c.drawRoundRect(r, corner * card, corner * card, p)
        // Blur the alpha in linear steps (a few box passes ≈ Gaussian).
        val a = IntArray(n * n)
        mask.getPixels(a, 0, n, 0, 0, n, n)
        val img = LinearImage(n, n)
        for (i in a.indices) img.r[i] = (a[i] ushr 24) / 255f
        ImageOps.blur(img, radius = (n * 0.06f).roundToInt().coerceAtLeast(1))
        for (i in a.indices) a[i] = ((img.r[i] * 255f).roundToInt().coerceIn(0, 255) shl 24)
        mask.setPixels(a, 0, n, 0, 0, n, n)
        return mask
    }

    /** Size of the shadow bitmap (pixels): it is drawn stretched, being nothing but blur. */
    private const val SHADOW_PX = 96

    private fun alpha(a: Float) = (a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24
}
