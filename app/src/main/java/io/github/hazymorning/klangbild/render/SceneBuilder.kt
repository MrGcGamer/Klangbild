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
    /** Cover at its exact on-screen pixel size (drawn 1:1, no GPU scaling). */
    val cover: Bitmap,
    val coverRect: RectDp,
    val coverGenerated: Boolean,
    /** Size of the source image (shorter side), null for generated art. */
    val coverNativePx: Int?,
    /** Greyscale light around the cover at reduced resolution; tinted and stretched when drawn. */
    val lightMap: Bitmap,
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
}

object SceneBuilder {
    const val THUMB = 48

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
        val square: IntArray
        val nativePx: Int?
        if (source != null) {
            val side = min(source.width, source.height)
            square = IntArray(side * side)
            source.getPixels(square, 0, side, (source.width - side) / 2, (source.height - side) / 2, side, side)
            nativePx = side
        } else {
            square = IntArray(0)
            nativePx = null
        }
        val coverDp = HaloLayout.coverDp(layout, nativePx)
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
        return assemble(key, layout, cover, layout.coverRect(coverDp), source == null, nativePx, analysis, lighting, thumb)
    }

    /** Re-light an existing scene (time of day changed) without touching the cover. */
    fun relight(scene: ArtworkScene, lighting: Lighting): ArtworkScene = assemble(
        scene.key, scene.layout, scene.cover, scene.coverRect, scene.coverGenerated,
        scene.coverNativePx, scene.analysis, lighting, scene.thumb,
    )

    private fun assemble(
        key: String,
        layout: HaloLayout,
        cover: Bitmap,
        rect: RectDp,
        generated: Boolean,
        nativePx: Int?,
        analysis: CoverAnalysis,
        lighting: Lighting,
        thumb: IntArray,
    ): ArtworkScene {
        val palette = CoverPalette.derive(analysis, lighting)
        val color = HaloLight.lightColor(analysis)
        val peak = HaloLight.glowPeak(analysis, rect.width, layout.slot)
        var tint = 0
        val map = HaloLight.lightMap(
            cover = rect,
            screen = layout.screen,
            openToRight = !layout.mirrored,
            night = HaloLight.nightOf(lighting),
            seed = stableHash(key),
            ditherLsb = { gain ->
                tint = HaloLight.lightTint(color, peak, gain, lighting)
                ditherFor(tint)
            },
        )
        return ArtworkScene(
            key = key,
            layout = layout,
            cover = cover,
            coverRect = rect,
            coverGenerated = generated,
            coverNativePx = nativePx,
            lightMap = Bitmap.createBitmap(map.pixels, HaloLight.MAP_W, HaloLight.MAP_H, Bitmap.Config.ARGB_8888),
            lightTint = tint,
            lightColor = color,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            thumb = thumb,
        )
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
