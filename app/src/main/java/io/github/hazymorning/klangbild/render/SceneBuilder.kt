package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.ScenePalette
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the UI needs to draw one track, precomputed off the main thread. The UI only
 * draws these bitmaps (no per-frame image processing).
 */
class ArtworkScene(
    val key: String,
    val style: StageStyle,
    val screen: ScreenSpec,
    /** Cover at its exact on-screen pixel size (no GPU scaling needed). */
    val cover: Bitmap,
    val coverRect: RectDp,
    val coverGenerated: Boolean,
    /** Size of the source image (shorter side), null for generated art. */
    val coverNativePx: Int?,
    /** Full-screen backdrop at reduced resolution; drawn stretched (it is all soft light). */
    val backdrop: Bitmap,
    val palette: ScenePalette,
    val analysis: CoverAnalysis,
    val lighting: Lighting,
    /** 48 × 48 thumbnail of the cover, kept to rebuild the backdrop when lighting changes. */
    val thumb: IntArray,
)

object SceneBuilder {
    const val THUMB = 48
    const val BACKDROP_W = 960
    const val BACKDROP_H = 540

    /**
     * @param source decoded cover or null (radio, missing art) → generated Chladni figure.
     */
    fun build(
        key: String,
        source: Bitmap?,
        artSeed: String,
        style: StageStyle,
        screen: ScreenSpec,
        lighting: Lighting,
    ): ArtworkScene {
        val generated = source == null
        val square: IntArray
        val nativePx: Int?
        if (source != null) {
            val side = min(source.width, source.height)
            val x0 = (source.width - side) / 2
            val y0 = (source.height - side) / 2
            square = IntArray(side * side)
            source.getPixels(square, 0, side, x0, y0, side, side)
            nativePx = side
        } else {
            square = IntArray(0)
            nativePx = null
        }

        val coverDp = StageLayout.coverDp(style, screen, nativePx, generated)
        val coverPx = (coverDp * screen.density).roundToInt()
        val coverPixels = if (source != null) {
            ImageOps.resize(square, nativePx!!, nativePx, coverPx, coverPx)
        } else {
            ChladniArt.render(ChladniArt.specFor(artSeed), coverPx, seed = stableHash(artSeed))
        }
        val cover = Bitmap.createBitmap(coverPixels, coverPx, coverPx, Bitmap.Config.ARGB_8888)

        val thumb = if (source != null) {
            ImageOps.thumbnail(square, nativePx!!, nativePx, THUMB)
        } else {
            ImageOps.thumbnail(coverPixels, coverPx, coverPx, THUMB)
        }
        val analysis = CoverPalette.analyze(thumb)
        val palette = CoverPalette.derive(analysis, lighting)
        val rect = StageLayout.coverRect(style, screen, coverDp)
        val backdrop = paintBackdrop(style, thumb, palette, rect, screen, lighting, key)
        return ArtworkScene(
            key = key,
            style = style,
            screen = screen,
            cover = cover,
            coverRect = rect,
            coverGenerated = generated,
            coverNativePx = nativePx,
            backdrop = backdrop,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            thumb = thumb,
        )
    }

    /** Re-light an existing scene (time of day changed) without touching the cover. */
    fun relight(scene: ArtworkScene, lighting: Lighting): ArtworkScene {
        val palette = CoverPalette.derive(scene.analysis, lighting)
        val backdrop = paintBackdrop(scene.style, scene.thumb, palette, scene.coverRect, scene.screen, lighting, scene.key)
        return ArtworkScene(
            scene.key, scene.style, scene.screen, scene.cover, scene.coverRect, scene.coverGenerated,
            scene.coverNativePx, backdrop, palette, scene.analysis, lighting, scene.thumb,
        )
    }

    fun frameRect(style: StageStyle, cover: RectDp): RectDp = when (style) {
        StageStyle.GALERIE -> {
            val mat = cover.width * GALLERY_MAT
            val border = cover.width * GALLERY_FRAME
            val grow = mat + border
            RectDp(cover.left - grow, cover.top - grow, cover.width + 2 * grow, cover.height + 2 * grow)
        }
        else -> cover
    }

    const val GALLERY_MAT = 0.2f
    const val GALLERY_FRAME = 0.035f

    private fun paintBackdrop(
        style: StageStyle,
        thumb: IntArray,
        palette: ScenePalette,
        rect: RectDp,
        screen: ScreenSpec,
        lighting: Lighting,
        key: String,
    ): Bitmap {
        val frame = frameRect(style, rect)
        val frac = FracRect(
            frame.left / screen.widthDp,
            frame.top / screen.heightDp,
            frame.right / screen.widthDp,
            frame.bottom / screen.heightDp,
        )
        val seed = stableHash(key)
        val px = when (style) {
            StageStyle.HALO -> Backdrops.halo(thumb, THUMB, palette, frac, lighting, BACKDROP_W, BACKDROP_H, seed)
            StageStyle.GALERIE -> Backdrops.gallery(palette, frac, lighting, BACKDROP_W, BACKDROP_H, seed)
            StageStyle.FEUILLETON -> Backdrops.editorial(palette, lighting, BACKDROP_W, BACKDROP_H, seed)
        }
        return Bitmap.createBitmap(px, BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
    }
}
