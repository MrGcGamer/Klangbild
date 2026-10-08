package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import io.github.hazymorning.klangbild.color.ScenePalette
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Cover position as fractions of the screen (0..1), independent of resolution. */
data class FracRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val w get() = right - left
    val h get() = bottom - top
}

/**
 * Backdrops are computed once per track (and when the time-of-day lighting changes) on a
 * background thread, at reduced resolution, then drawn stretched to the screen: one opaque
 * textured quad per frame instead of real-time blurs at 4K.
 */
object Backdrops {
    /** Low-resolution grid for the light simulation. */
    private const val LW = 240
    private const val LH = 135

    /**
     * HALO: the cover is backlit; its colours spill onto a dark wall like light from a lamp
     * hidden behind a picture. Light is blurred in linear space at two scales (a near
     * glow and a wide spill), which approximates the soft inverse-square falloff of real light.
     */
    fun halo(
        thumb: IntArray,
        thumbSize: Int,
        palette: ScenePalette,
        cover: FracRect,
        lighting: Lighting,
        outW: Int,
        outH: Int,
        seed: Int,
    ): IntArray {
        val light = LinearImage(LW, LH)
        val x0 = (cover.left * LW).toInt()
        val x1 = (cover.right * LW).toInt()
        val y0 = (cover.top * LH).toInt()
        val y1 = (cover.bottom * LH).toInt()
        // tamed light colours of the cover, sampled into the cover rectangle
        val tamed = FloatArray(thumbSize * thumbSize * 3)
        for (i in 0 until thumbSize * thumbSize) {
            val lab = Oklab.fromArgb(thumb[i])
            val c = lab.chroma
            val l = 0.42f + 0.40f * lab.l
            val lit = if (c < 0.03f) {
                // neutral areas glow like warm lamp light rather than grey
                Lch(l, 0.032f, CoverPalette.WARM_HUE).toLab()
            } else {
                CoverPalette.tameForLight(Lab(l, lab.a, lab.b), maxChroma = 0.15f)
            }
            val rgb = Oklab.toLinear(Oklab.gamutMap(lit))
            tamed[i * 3] = rgb[0]
            tamed[i * 3 + 1] = rgb[1]
            tamed[i * 3 + 2] = rgb[2]
        }
        for (y in y0 until y1) for (x in x0 until x1) {
            val tx = ((x - x0 + 0.5f) / (x1 - x0) * thumbSize).toInt().coerceIn(0, thumbSize - 1)
            val ty = ((y - y0 + 0.5f) / (y1 - y0) * thumbSize).toInt().coerceIn(0, thumbSize - 1)
            val t = (ty * thumbSize + tx) * 3
            val o = y * LW + x
            light.r[o] = tamed[t]
            light.g[o] = tamed[t + 1]
            light.b[o] = tamed[t + 2]
        }
        val near = copy(light)
        ImageOps.blur(near, radius = max(2, (LW * 0.022f).toInt()))
        ImageOps.blur(light, radius = max(4, (LW * 0.075f).toInt()))

        val base = Oklab.toLinear(Oklab.fromArgb(palette.base))
        val out = LinearImage(LW, LH)
        val coverDiag = sqrt(cover.w * cover.w + cover.h * cover.h)
        for (y in 0 until LH) for (x in 0 until LW) {
            val o = y * LW + x
            val u = (x + 0.5f) / LW
            val v = (y + 0.5f) / LH
            // vignette towards the screen edges keeps the corners truly dark (OLED)
            val dx = (u - cover.cx) / (coverDiag * 1.6f)
            val dy = (v - cover.cy) / (coverDiag * 1.25f)
            val reach = exp(-(dx * dx + dy * dy) * 1.2f)
            val gainNear = 0.42f
            val gainWide = 0.62f
            out.r[o] = base[0] + (near.r[o] * gainNear + light.r[o] * gainWide) * reach
            out.g[o] = base[1] + (near.g[o] * gainNear + light.g[o] * gainWide) * reach
            out.b[o] = base[2] + (near.b[o] * gainNear + light.b[o] * gainWide) * reach
        }
        applyLighting(out, lighting)
        return ImageOps.toArgbDithered(upsample(out, outW, outH), seed)
    }

    /**
     * GALERIE: a museum wall at night. A warm spotlight from above pools on the wall,
     * the frame throws a soft shadow. The wall takes a muted tint from the cover.
     */
    fun gallery(
        palette: ScenePalette,
        frame: FracRect,
        lighting: Lighting,
        outW: Int,
        outH: Int,
        seed: Int,
    ): IntArray {
        val hue = palette.tintHue ?: CoverPalette.WARM_HUE
        val wall = Oklab.toLinear(Lch(0.205f, 0.018f, hue).toLab())
        val spot = Oklab.toLinear(Lch(0.30f, 0.03f, CoverPalette.WARM_HUE).toLab())
        val out = LinearImage(LW, LH)
        val aspect = outW.toFloat() / outH
        for (y in 0 until LH) for (x in 0 until LW) {
            val o = y * LW + x
            val u = (x + 0.5f) / LW
            val v = (y + 0.5f) / LH
            // spotlight cone: an ellipse centred above the frame, falling off softly
            val sx = (u - frame.cx) * aspect / (frame.w * aspect * 1.1f)
            val sy = (v - (frame.top + frame.h * 0.25f)) / (frame.h * 0.95f)
            val spotlight = exp(-(sx * sx + sy * sy) * 1.6f)
            // frame shadow: soft, offset downwards
            val shx = (u - frame.cx) / (frame.w * 0.56f)
            val shy = (v - frame.cy - frame.h * 0.035f) / (frame.h * 0.56f)
            val shadow = 1f - 0.55f * exp(-(pow8(shx) + pow8(shy)) * 0.9f)
            // general room falloff
            val room = 0.72f + 0.28f * exp(-((u - 0.45f) * (u - 0.45f) * 2.2f + (v - 0.35f) * (v - 0.35f) * 2.6f))
            val k = spotlight
            out.r[o] = (wall[0] * room + spot[0] * k) * shadow
            out.g[o] = (wall[1] * room + spot[1] * k) * shadow
            out.b[o] = (wall[2] * room + spot[2] * k) * shadow
        }
        applyLighting(out, lighting)
        return ImageOps.toArgbDithered(upsample(out, outW, outH), seed)
    }

    /**
     * FEUILLETON: a deep, inked page in the cover's leading colour with a gentle tonal
     * gradient – the colour of a well-printed magazine spread at night.
     */
    fun editorial(
        palette: ScenePalette,
        lighting: Lighting,
        outW: Int,
        outH: Int,
        seed: Int,
    ): IntArray {
        val hue = palette.tintHue ?: CoverPalette.WARM_HUE
        val chroma = if (palette.tintHue == null) 0.012f else 0.045f
        val a = Oklab.toLinear(Lch(0.235f, chroma, hue).toLab())
        val b = Oklab.toLinear(Lch(0.15f, chroma * 0.8f, hue + 8f).toLab())
        val out = LinearImage(LW, LH)
        for (y in 0 until LH) for (x in 0 until LW) {
            val o = y * LW + x
            val u = (x + 0.5f) / LW
            val v = (y + 0.5f) / LH
            val t = (0.55f * u + 0.45f * v).coerceIn(0f, 1f)
            out.r[o] = a[0] + (b[0] - a[0]) * t
            out.g[o] = a[1] + (b[1] - a[1]) * t
            out.b[o] = a[2] + (b[2] - a[2]) * t
        }
        applyLighting(out, lighting)
        return ImageOps.toArgbDithered(upsample(out, outW, outH), seed)
    }

    private fun pow8(x: Float): Float {
        val x2 = x * x
        val x4 = x2 * x2
        return x4 * x4
    }

    private fun copy(src: LinearImage): LinearImage = LinearImage(src.width, src.height).also {
        src.r.copyInto(it.r); src.g.copyInto(it.g); src.b.copyInto(it.b)
    }

    private fun applyLighting(img: LinearImage, lighting: Lighting) {
        if (lighting.brightness == 1f && lighting.warmth == 0f) return
        for (i in img.r.indices) {
            val lab = lighting.apply(Oklab.fromLinear(img.r[i], img.g[i], img.b[i]))
            val rgb = Oklab.toLinear(lab)
            img.r[i] = max(0f, rgb[0]); img.g[i] = max(0f, rgb[1]); img.b[i] = max(0f, rgb[2])
        }
    }

    /** Bilinear upsampling in linear light. */
    private fun upsample(src: LinearImage, w: Int, h: Int): LinearImage {
        val out = LinearImage(w, h)
        val sx = src.width.toFloat() / w
        val sy = src.height.toFloat() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, src.height - 1f)
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, src.height - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, src.width - 1f)
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, src.width - 1)
                val tx = fx - x0
                val i00 = y0 * src.width + x0
                val i01 = y0 * src.width + x1
                val i10 = y1 * src.width + x0
                val i11 = y1 * src.width + x1
                val o = y * w + x
                out.r[o] = bilerp(src.r, i00, i01, i10, i11, tx, ty)
                out.g[o] = bilerp(src.g, i00, i01, i10, i11, tx, ty)
                out.b[o] = bilerp(src.b, i00, i01, i10, i11, tx, ty)
            }
        }
        return out
    }

    private fun bilerp(p: FloatArray, i00: Int, i01: Int, i10: Int, i11: Int, tx: Float, ty: Float): Float {
        val top = p[i00] + (p[i01] - p[i00]) * tx
        val bottom = p[i10] + (p[i11] - p[i10]) * tx
        return top + (bottom - top) * ty
    }
}
