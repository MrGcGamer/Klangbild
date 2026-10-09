package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * "Coverfarben": the cover's own colours as the wall behind the picture, as the full-screen
 * views of good music players do it – the artwork blurred far beyond recognition, large and
 * turned, in a few soft layers that mix where they overlap ([FieldMotion]). Toned for a wall:
 * deep in the dark, pale on paper, never so bright that the text loses its contrast; towards
 * the screen's edges it fades to the wall (black stays black along them), and under the text
 * column it is a little darker.
 *
 * Built once per scene. Drawing it is a handful of textured quads in a small layer that is
 * scaled up, plus a fine grain against banding – the same few numbers per frame whether it
 * stands still or moves.
 */
class FieldScene(
    /** The toned, blurred cover as a soft disc (its alpha fades to nothing at the rim); null without the colour background. */
    val texture: Bitmap?,
    /** The colour under the layers: the toned cover's mean. */
    val base: Int,
    /** Over the layers: the wall's colour towards the screen's edges, a shade under the text column. */
    val veil: Bitmap?,
    /** The blurred cover as light, untoned (linear sRGB, [CoverField.LIGHT_SIZE]²): what the light bars take. */
    val light: LinearImage,
    /** The cover's mean in linear light: what the bars show where no layer reaches. */
    val lightMean: FloatArray,
)

object CoverField {
    /** Side of the screen texture (px): the field is a few big soft shapes – this is plenty. */
    const val SIZE = 128

    /** Side of the light bars' texture. */
    const val LIGHT_SIZE = 32

    /** The veil's resolution: it only holds soft ramps. */
    const val VEIL_W = 240
    const val VEIL_H = 135

    /** Where the disc's alpha starts to fall (share of the side from the centre) and where it is gone. */
    const val DISC_INNER = 0.28f
    const val DISC_OUTER = 0.5f

    /** Lightness (Oklab) of the dark field: its darkest and its brightest. */
    const val DARK_LO = 0.10f
    const val DARK_HI = 0.31f

    /** Lightness of the pale field on paper (the text is set against its darkest). */
    const val LIGHT_LO = CoverPalette.FIELD_PALEST_DARK
    const val LIGHT_HI = 0.945f

    /** The text column's shade on the dark field (share of the light taken away). */
    const val TEXT_SHADE = 0.35f

    /** The fade towards the screen's edges (dp), beyond the black band. */
    const val EDGE_FADE_X = 140f
    const val EDGE_FADE_Y = 100f

    /** The artwork's shadow on the field: dropped this far (dp), this soft (σ, dp), at most this deep (share of the light). */
    const val SHADOW_DROP = 12f
    const val SHADOW_SIGMA = 22f
    const val SHADOW_DEPTH = 0.42f

    /**
     * The field for a cover whose [thumb] is [size]² pixels. [strength] is the light level
     * (1 = as designed): it sets how colourful the field is. Without a [layout] only what the
     * light bars need is built. [wall] is the plain wall's colour (what the field fades to at
     * the edges on paper); [shape], the artwork's outline in the single view, casts a soft
     * shadow on the field.
     */
    fun build(
        thumb: IntArray,
        size: Int,
        lighting: Lighting,
        appearance: Appearance,
        strength: Float,
        layout: HaloLayout?,
        wall: Int = 0xFF000000.toInt(),
        shape: PrintShape? = null,
    ): FieldScene {
        val light = blurred(thumb, size, LIGHT_SIZE)
        val lightMean = mean(light)
        if (layout == null) return FieldScene(null, 0, null, light, lightMean)
        val src = blurred(thumb, size, SIZE)
        val pixels = IntArray(SIZE * SIZE)
        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var n = 0f
        val half = SIZE / 2f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            val toned = tone(Oklab.fromLinear(src.r[i], src.g[i], src.b[i]), appearance, strength, lighting)
            val r = hypot(x + 0.5f - half, y + 0.5f - half) / SIZE
            val alpha = 1f - HaloLight.smoothstep(DISC_INNER, DISC_OUTER, r)
            val argb = Oklab.toArgb(toned, (alpha * 255f + 0.5f).toInt())
            pixels[i] = argb
            if (alpha > 0.5f) {
                sumR += Oklab.srgbToLinear(argb shr 16)
                sumG += Oklab.srgbToLinear(argb shr 8)
                sumB += Oklab.srgbToLinear(argb)
                n++
            }
        }
        val base = if (n == 0f) {
            if (appearance == Appearance.DARK) 0xFF000000.toInt() else 0xFFEDE9E2.toInt()
        } else {
            argbOfLinear(sumR / n, sumG / n, sumB / n)
        }
        val texture = Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return FieldScene(texture, base, veil(layout, appearance, wall, shape), light, lightMean)
    }

    /**
     * One colour of the cover, toned for the wall: in the dark between [DARK_LO] and [DARK_HI]
     * with its chroma kept moderate (deep, never neon), on paper a pale wash; then the hour's
     * light, as for everything else.
     */
    fun tone(lab: Lab, appearance: Appearance, strength: Float, lighting: Lighting): Lab {
        val s = strength.coerceIn(0.3f, 1.5f)
        val lch = lab.toLch()
        val toned = if (appearance == Appearance.DARK) {
            val hi = DARK_LO + (DARK_HI - DARK_LO) * min(1f, 0.75f + 0.25f * s)
            val l = DARK_LO + (hi - DARK_LO) * HaloLight.smoothstep(0.06f, 0.9f, lch.l)
            // Darker parts carry less colour: deep, not glowing.
            val c = min(lch.c * 1.15f, 0.095f * s) * (0.55f + 0.45f * (l - DARK_LO) / (DARK_HI - DARK_LO))
            Lch(l, c, lch.h)
        } else {
            val l = LIGHT_LO + (LIGHT_HI - LIGHT_LO) * HaloLight.smoothstep(0.05f, 0.95f, lch.l)
            Lch(l, min(lch.c * 0.55f, 0.045f * s), lch.h)
        }
        return Oklab.gamutMap(lighting.apply(toned.toLab()))
    }

    /**
     * The veil over the field (ARGB, [VEIL_W] × [VEIL_H] for the whole screen): the plain
     * [wall] towards the edges – the outer 36 dp stay as black (or paper) as on the plain
     * wall –, on the dark field a shade under the text column, and the artwork's soft shadow.
     * Where wall and shadow overlap, the veil is the one colour and opacity that gives both:
     * field × (1 − edge) × (1 − shadow) + wall × edge × (1 − shadow).
     */
    private fun veil(layout: HaloLayout, appearance: Appearance, wall: Int, shape: PrintShape?): Bitmap {
        val screen = layout.screen
        val wr = Oklab.srgbToLinear(wall shr 16)
        val wg = Oklab.srgbToLinear(wall shr 8)
        val wb = Oklab.srgbToLinear(wall)
        val dark = appearance == Appearance.DARK
        val px = IntArray(VEIL_W * VEIL_H)
        val sx = screen.widthDp / VEIL_W
        val sy = screen.heightDp / VEIL_H
        val column = layout.columnLeft
        for (y in 0 until VEIL_H) {
            val py = (y + 0.5f) * sy
            val inY = HaloLight.smoothstep(WallGlow.EDGE_BLACK_DP, WallGlow.EDGE_BLACK_DP + EDGE_FADE_Y, min(py, screen.heightDp - py))
            for (x in 0 until VEIL_W) {
                val ppx = (x + 0.5f) * sx
                val inX = HaloLight.smoothstep(WallGlow.EDGE_BLACK_DP, WallGlow.EDGE_BLACK_DP + EDGE_FADE_X, min(ppx, screen.widthDp - ppx))
                val edge = 1f - inX * inY
                val text = if (dark) TEXT_SHADE * HaloLight.smoothstep(column - 150f, column + 20f, ppx) else 0f
                val cast = shape?.let { SHADOW_DEPTH * WallShade.cover(it.signedDistance(ppx, py - SHADOW_DROP), SHADOW_SIGMA) } ?: 0f
                val shadow = 1f - (1f - text) * (1f - cast)
                // field × keep + colour × alpha, with keep = 1 − alpha.
                val keep = (1f - edge) * (1f - shadow)
                val alpha = 1f - keep
                val cover = edge * (1f - shadow)
                fun ch(w: Float) = if (alpha <= 0f) 0 else (Oklab.linearToSrgb(w * cover / alpha) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                px[y * VEIL_W + x] = (a shl 24) or (ch(wr) shl 16) or (ch(wg) shl 8) or ch(wb)
            }
        }
        return Bitmap.createBitmap(px, VEIL_W, VEIL_H, Bitmap.Config.ARGB_8888)
    }

    /** [thumb] ([size]²) resampled to [out]² and blurred far beyond recognition (linear light). */
    private fun blurred(thumb: IntArray, size: Int, out: Int): LinearImage {
        val px = ImageOps.resize(thumb, size, size, out, out)
        val img = LinearImage.fromArgb(px, out, out)
        // σ ≈ a fifth of the side: shapes dissolve into a few large clouds of colour.
        ImageOps.blur(img, radius = max(1, out / 5))
        return img
    }

    private fun mean(img: LinearImage): FloatArray {
        var r = 0f
        var g = 0f
        var b = 0f
        for (i in img.r.indices) {
            r += img.r[i]
            g += img.g[i]
            b += img.b[i]
        }
        val n = img.r.size.toFloat()
        return floatArrayOf(r / n, g / n, b / n)
    }

    private fun argbOfLinear(r: Float, g: Float, b: Float): Int {
        fun ch(v: Float) = (Oklab.linearToSrgb(v) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

/**
 * Where the layers of the cover field stand at a moment – the same numbers for the screen and
 * for the light bars, so both move together. Each layer is the blurred cover as a soft disc,
 * bigger than the screen, turning slowly about its own centre while that centre wanders a
 * little; the layers turn at different speeds and some the other way, so their colours flow
 * into each other without ever repeating visibly (the periods share no common beat).
 *
 * [t] is the field's own time in seconds: it only advances while music plays, scaled by the
 * motion setting, so a pause holds everything still and nothing ever jumps.
 */
object FieldMotion {
    const val LAYERS = 3

    /** Floats per layer in [layers]' output: centre x, centre y (shares of the screen), turn (degrees), diameter (share of the diagonal), opacity. */
    const val STRIDE = 5

    // Per layer: centre, its wander (amplitude, period s), turn (start, degrees per second), diameter (base, breath, period s), opacity.
    private val CX = floatArrayOf(0.36f, 0.66f, 0.30f)
    private val CY = floatArrayOf(0.44f, 0.52f, 0.66f)
    private val WANDER = floatArrayOf(0.05f, 0.07f, 0.06f)
    private val WANDER_X_PERIOD = floatArrayOf(61f, 67f, 71f)
    private val WANDER_Y_PERIOD = floatArrayOf(73f, 59f, 83f)
    private val TURN_START = floatArrayOf(20f, 150f, 275f)
    private val TURN_SPEED = floatArrayOf(360f / 140f, -360f / 105f, 360f / 185f)
    private val DIAMETER = floatArrayOf(1.55f, 1.15f, 0.95f)
    private val BREATH = floatArrayOf(0.06f, 0.08f, 0.07f)
    private val BREATH_PERIOD = floatArrayOf(47f, 53f, 41f)
    private val OPACITY = floatArrayOf(1f, 0.62f, 0.5f)
    private val PHASE = floatArrayOf(0f, 2.1f, 4.2f)

    /** Writes the layers' places at [t] (seconds of the field's time) into [out] ([LAYERS] × [STRIDE] floats). */
    fun layers(t: Double, out: FloatArray) {
        for (i in 0 until LAYERS) {
            val o = i * STRIDE
            out[o] = CX[i] + WANDER[i] * wave(t, WANDER_X_PERIOD[i], PHASE[i])
            out[o + 1] = CY[i] + WANDER[i] * 0.8f * wave(t, WANDER_Y_PERIOD[i], 1.3f * PHASE[i] + 0.7f)
            out[o + 2] = ((TURN_START[i] + TURN_SPEED[i] * t) % 360.0).toFloat()
            out[o + 3] = DIAMETER[i] + BREATH[i] * wave(t, BREATH_PERIOD[i], PHASE[i])
            out[o + 4] = OPACITY[i]
        }
    }

    /** sin(2π t / period + phase), exact however long the field has been running. */
    private fun wave(t: Double, period: Float, phase: Float): Float =
        kotlin.math.sin(TAU_D * ((t / period) % 1.0) + phase).toFloat()

    private const val TAU_D = 6.283185307179586

    /**
     * The colour of the light field at ([x], [y]) (shares of a screen of [aspect]) at the
     * layers' places [layers], in linear sRGB into [out]: the layers over the mean, as on the
     * screen but untoned – light, not wall.
     */
    fun light(field: FieldScene, x: Float, y: Float, aspect: Float, layers: FloatArray, out: FloatArray) {
        val img = field.light
        val n = CoverField.LIGHT_SIZE
        out[0] = field.lightMean[0]
        out[1] = field.lightMean[1]
        out[2] = field.lightMean[2]
        val diag = hypot(aspect, 1f)
        for (i in 0 until LAYERS) {
            val o = i * STRIDE
            val dx = (x - layers[o]) * aspect / diag
            val dy = (y - layers[o + 1]) / diag
            val a = Math.toRadians(layers[o + 2].toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            // Into the disc's own frame (it is drawn turned by the angle).
            val u = 0.5f + (dx * c + dy * s) / layers[o + 3]
            val v = 0.5f + (-dx * s + dy * c) / layers[o + 3]
            val r = hypot(u - 0.5f, v - 0.5f)
            val w = layers[o + 4] * (1f - HaloLight.smoothstep(CoverField.DISC_INNER, CoverField.DISC_OUTER, r))
            if (w <= 0f) continue
            val fx = (u * n - 0.5f).coerceIn(0f, n - 1f)
            val fy = (v * n - 0.5f).coerceIn(0f, n - 1f)
            val x0 = fx.toInt().coerceAtMost(n - 2)
            val y0 = fy.toInt().coerceAtMost(n - 2)
            val tx = fx - x0
            val ty = fy - y0
            fun at(p: FloatArray): Float {
                val i00 = y0 * n + x0
                val top = p[i00] + (p[i00 + 1] - p[i00]) * tx
                val bottom = p[i00 + n] + (p[i00 + n + 1] - p[i00 + n]) * tx
                return top + (bottom - top) * ty
            }
            out[0] += (at(img.r) - out[0]) * w
            out[1] += (at(img.g) - out[1]) * w
            out[2] += (at(img.b) - out[2]) * w
        }
    }
}
