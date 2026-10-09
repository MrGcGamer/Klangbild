package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import io.github.hazymorning.klangbild.color.ScenePalette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the UI needs to draw one track, precomputed off the main thread. The UI only
 * draws these bitmaps – no image processing per frame.
 */
class ArtworkScene(
    val key: String,
    val layout: HaloLayout,
    /**
     * The print at its exact on-screen pixel size (drawn 1:1, no GPU scaling). A cover too
     * small for the slot is matted (a dark passe-partout); a generated plate is round and
     * transparent around the disc.
     */
    val cover: Bitmap,
    /** The print's outline on the wall. */
    val shape: PrintShape,
    val coverGenerated: Boolean,
    /** Size of the source image (shorter side), null for generated art. */
    val coverNativePx: Int?,
    /** The lamp's pool of light: a greyscale disc, placed and stretched when drawn. */
    val lamp: Bitmap,
    /** Static wall mask with the print (dark behind it, clear of the screen edges). */
    val holeMask: Bitmap,
    /** The same without the print – for the moments when the print is taken down. */
    val openMask: Bitmap,
    /** The faint rim of light around a dark print (null when the print stands out anyway). */
    val rim: Bitmap?,
    /** Tint of the rim. */
    val rimTint: Int,
    /** The lamp's sRGB tint at its brightest (the lamp itself), time of day included. */
    val lightTint: Int,
    /** Daylight colour of the light, kept to re-light the scene. */
    val lightColor: Lch,
    val palette: ScenePalette,
    val analysis: CoverAnalysis,
    val lighting: Lighting,
    /** 48 × 48 thumbnail of the cover, kept to re-light the scene without the source. */
    val thumb: IntArray,
) {
    val screen: ScreenSpec get() = layout.screen

    /** The square the print bitmap occupies (dp). */
    val coverRect: RectDp get() = layout.coverRect(layout.slot)
}

object SceneBuilder {
    const val THUMB = 48

    /**
     * Passe-partout for small prints: charcoal mat board with a faint grain (a light board
     * would be the largest static bright area on the OLED and glare at night)…
     */
    const val MAT = 0xFF221F1C.toInt()

    /** …and the white core of the bevel around the window, as cut into real mat board. */
    const val BEVEL = 0xFFC8C1B6.toInt()

    /**
     * @param source decoded cover or null (radio, missing art) → generated Chladni figure.
     * @param layoutSeed decides the side of the cover (album or station), against burn-in.
     */
    fun build(
        key: String,
        source: Bitmap?,
        artSeed: String,
        screen: ScreenSpec,
        lighting: Lighting,
        layoutSeed: String? = null,
        mirrored: Boolean? = null,
    ): ArtworkScene {
        val layout = HaloLayout(screen, mirrored = mirrored ?: layoutSeed?.let { mirrorFor(it) } ?: false)
        val slotPx = (layout.slot * screen.density).roundToInt()
        val slotRect = layout.coverRect(layout.slot)
        val coverPixels: IntArray
        val coverSize: Int
        val nativePx: Int?
        val thumb: IntArray
        val shape: PrintShape
        if (source != null) {
            val side = min(source.width, source.height)
            val square = IntArray(side * side)
            source.getPixels(square, 0, side, (source.width - side) / 2, (source.height - side) / 2, side, side)
            nativePx = side
            val coverDp = HaloLayout.coverDp(layout, nativePx)
            val coverPx = (coverDp * screen.density).roundToInt()
            val print = ImageOps.resize(square, side, side, coverPx, coverPx)
            val mat = coverPx < slotPx - 1
            coverPixels = if (mat) matted(print, coverPx, slotPx, screen.density) else print
            coverSize = if (mat) slotPx else coverPx
            thumb = ImageOps.thumbnail(square, side, side, THUMB)
            shape = PrintShape(slotRect)
        } else {
            nativePx = null
            coverPixels = ChladniArt.render(ChladniArt.specFor(artSeed), slotPx, seed = stableHash(artSeed))
            coverSize = slotPx
            thumb = ImageOps.thumbnail(coverPixels, slotPx, slotPx, THUMB)
            val r = ChladniArt.PLATE * slotRect.width / 2f
            shape = PrintShape(RectDp(slotRect.centerX - r, slotRect.centerY - r, 2 * r, 2 * r), round = true)
        }
        val cover = Bitmap.createBitmap(coverPixels, coverSize, coverSize, Bitmap.Config.ARGB_8888)
        val analysis = CoverPalette.analyze(thumb)
        return assemble(key, layout, cover, shape, source == null, nativePx, analysis, lighting, thumb)
    }

    /** Re-light an existing scene (time of day changed) without touching the cover. */
    fun relight(scene: ArtworkScene, lighting: Lighting): ArtworkScene = assemble(
        scene.key, scene.layout, scene.cover, scene.shape, scene.coverGenerated,
        scene.coverNativePx, scene.analysis, lighting, scene.thumb,
    )

    private fun assemble(
        key: String,
        layout: HaloLayout,
        cover: Bitmap,
        shape: PrintShape,
        generated: Boolean,
        nativePx: Int?,
        analysis: CoverAnalysis,
        lighting: Lighting,
        thumb: IntArray,
    ): ArtworkScene {
        val palette = CoverPalette.derive(analysis, lighting)
        val color = HaloLight.lightColor(analysis)
        val printDp = nativePx?.let { HaloLayout.coverDp(layout, it) } ?: layout.slot
        // glowPeak is the brightness where the light leaves the print; the lamp itself is brighter.
        val peak = HaloLight.lampPeak(HaloLight.glowPeak(analysis, printDp, layout.slot))
        val tint = HaloLight.lightTint(color, peak, lighting)
        val rimLuminance = HaloLight.rimLuminance(edgeLuminance(thumb))
        // The rim is the same lamp's light, a little less coloured – not a second light.
        val rimColor = Lch(color.l, color.c / 2f, color.h)
        val rimTint = if (rimLuminance > 0f) HaloLight.lightTint(rimColor, rimLuminance, lighting) else 0
        val s = HaloLight.LAMP_SPRITE
        return ArtworkScene(
            key = key,
            layout = layout,
            cover = cover,
            shape = shape,
            coverGenerated = generated,
            coverNativePx = nativePx,
            lamp = Bitmap.createBitmap(HaloLight.lampSprite(ditherFor(tint), seed = stableHash(key)), s, s, Bitmap.Config.ARGB_8888),
            holeMask = Bitmap.createBitmap(HaloLight.holeMask(shape, layout.screen), HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888),
            openMask = openMask(layout.screen),
            rim = if (rimLuminance > 0f) {
                Bitmap.createBitmap(HaloLight.rimMap(shape, layout.screen), HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888)
            } else {
                null
            },
            rimTint = rimTint,
            lightTint = tint,
            lightColor = color,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            thumb = thumb,
        )
    }

    /** Mean relative luminance of the outer ring of a 48 × 48 thumbnail (the print's edge). */
    fun edgeLuminance(thumb: IntArray, size: Int = THUMB, ring: Int = 3): Float {
        var sum = 0.0
        var n = 0
        for (y in 0 until size) for (x in 0 until size) {
            if (x >= ring && x < size - ring && y >= ring && y < size - ring) continue
            sum += Oklab.relativeLuminance(thumb[y * size + x])
            n++
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    private val openMasks = HashMap<ScreenSpec, Bitmap>()

    /** The wall mask without a print depends only on the screen: computed once. */
    @Synchronized
    fun openMask(screen: ScreenSpec): Bitmap = openMasks.getOrPut(screen) {
        Bitmap.createBitmap(HaloLight.openMask(screen), HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888)
    }

    /**
     * A small print (shown at its honest size) in a passe-partout that fills the slot, so
     * the grid holds: grained mat board, a 1 dp white bevel around the window, the print
     * centred.
     */
    fun matted(print: IntArray, printPx: Int, slotPx: Int, density: Float, seed: Int = 7): IntArray {
        var state = seed * 0x9E3779B9.toInt() or 1
        fun grain(): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return (state ushr 8) % 5 - 2
        }
        val out = IntArray(slotPx * slotPx) { shifted(MAT, grain()) }
        val off = (slotPx - printPx) / 2
        for (y in 0 until printPx) System.arraycopy(print, y * printPx, out, (y + off) * slotPx + off, printPx)
        val bevel = density.roundToInt().coerceAtLeast(1)
        for (b in 1..bevel) {
            val lo = off - b
            val hi = off + printPx - 1 + b
            for (i in lo..hi) {
                for ((x, y) in arrayOf(i to lo, i to hi, lo to i, hi to i)) {
                    if (x in 0 until slotPx && y in 0 until slotPx) out[y * slotPx + x] = BEVEL
                }
            }
        }
        return out
    }

    /** [argb] with [d] added to each channel. */
    private fun shifted(argb: Int, d: Int): Int {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) + d).coerceIn(0, 255) shl shift
        return (argb and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
    }

    /**
     * Dither amplitude (grey levels) for a map that is drawn multiplied by [tint]: about one
     * output level of noise, so even the darkest stretch of the glow never shows bands.
     */
    fun ditherFor(tint: Int): Float {
        val maxC = maxOf((tint shr 16) and 0xFF, (tint shr 8) and 0xFF, tint and 0xFF).coerceAtLeast(16)
        return (1.25f * 255f / maxC).coerceIn(1f, 6f)
    }

    /** Stable per album/station: about half of all albums show the cover on the right. */
    fun mirrorFor(seed: String): Boolean = (stableHash(seed) ushr 7) and 1 == 1
}
