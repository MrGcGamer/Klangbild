package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
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
    /** The lamp's pool of light, greyscale, 1 px per dp, centred on the print. */
    val glow: Bitmap,
    /** Static wall mask with the print (its shadow, the clearance at the screen edges). */
    val holeMask: Bitmap,
    /** The same without the print – for the moments when the print is taken down. */
    val openMask: Bitmap,
    /** The light's sRGB tint at its brightest, time of day included. */
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

    /** Passe-partout for small prints: a dark mat board… */
    private const val MAT = 0xFF191715.toInt()

    /** …with a light bevel around the window, as cut into real mat board. */
    private const val BEVEL_WHITE = 0.15f

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
        val peak = HaloLight.glowPeak(analysis, printDp, layout.slot)
        val tint = HaloLight.lightTint(color, peak, lighting)
        val night = HaloLight.nightOf(lighting)
        val glowSize = HaloLight.glowSize(shape)
        val glow = HaloLight.glowSprite(shape, night, stableHash(key), ditherFor(tint))
        return ArtworkScene(
            key = key,
            layout = layout,
            cover = cover,
            shape = shape,
            coverGenerated = generated,
            coverNativePx = nativePx,
            glow = Bitmap.createBitmap(glow, glowSize, glowSize, Bitmap.Config.ARGB_8888),
            holeMask = Bitmap.createBitmap(HaloLight.holeMask(shape, layout.screen, night), HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888),
            openMask = openMask(layout.screen),
            lightTint = tint,
            lightColor = color,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            thumb = thumb,
        )
    }

    private val openMasks = HashMap<ScreenSpec, Bitmap>()

    /** The wall mask without a print depends only on the screen: computed once. */
    @Synchronized
    fun openMask(screen: ScreenSpec): Bitmap = openMasks.getOrPut(screen) {
        Bitmap.createBitmap(HaloLight.openMask(screen), HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888)
    }

    /**
     * A small print (shown at its honest size) in a passe-partout that fills the slot, so
     * the grid holds: dark mat board, a light bevel around the window, the print centred.
     */
    fun matted(print: IntArray, printPx: Int, slotPx: Int, density: Float): IntArray {
        val out = IntArray(slotPx * slotPx) { MAT }
        val off = (slotPx - printPx) / 2
        for (y in 0 until printPx) System.arraycopy(print, y * printPx, out, (y + off) * slotPx + off, printPx)
        val bevel = (density / 2f).roundToInt().coerceAtLeast(1)
        fun lift(x: Int, y: Int) {
            if (x !in 0 until slotPx || y !in 0 until slotPx) return
            val p = out[y * slotPx + x]
            fun ch(v: Int) = (v + (255 - v) * BEVEL_WHITE).roundToInt().coerceIn(0, 255)
            out[y * slotPx + x] = (0xFF shl 24) or (ch((p shr 16) and 0xFF) shl 16) or (ch((p shr 8) and 0xFF) shl 8) or ch(p and 0xFF)
        }
        for (b in 1..bevel) {
            val lo = off - b
            val hi = off + printPx - 1 + b
            for (i in lo..hi) {
                lift(i, lo); lift(i, hi); lift(lo, i); lift(hi, i)
            }
        }
        return out
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
