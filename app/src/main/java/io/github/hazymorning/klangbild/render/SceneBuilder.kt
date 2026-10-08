package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverPalette
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
    /** Cover at its exact on-screen pixel size (drawn 1:1, no GPU scaling). */
    val cover: Bitmap,
    val coverRect: RectDp,
    val coverGenerated: Boolean,
    /** Size of the source image (shorter side), null for generated art. */
    val coverNativePx: Int?,
    /** The wall with the glow, at reduced resolution; drawn stretched (it is all soft light). */
    val backdrop: Bitmap,
    val palette: ScenePalette,
    val analysis: CoverAnalysis,
    val lighting: Lighting,
    /** Colour of the light (sRGB). */
    val light: Int,
    /** Tint for the additive sun sprite, already scaled to its intended strength. */
    val sunTint: Int,
    /** 48 × 48 thumbnail of the cover, kept to re-light the scene without the source. */
    val thumb: IntArray,
) {
    val screen: ScreenSpec get() = layout.screen
}

object SceneBuilder {
    const val THUMB = 48

    /** Shared greyscale sun sprite. */
    val sunSprite: Bitmap by lazy {
        Bitmap.createBitmap(HaloLight.sunSprite(), HaloLight.SPRITE, HaloLight.SPRITE, Bitmap.Config.ARGB_8888)
    }

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
        val light = HaloLight.lightColor(analysis, palette)
        val peak = HaloLight.glowPeak(analysis)
        val backdropPx = HaloLight.backdrop(palette, light, peak, rect, layout.screen, lighting, stableHash(key))
        val backdrop = Bitmap.createBitmap(backdropPx, HaloLight.BACKDROP_W, HaloLight.BACKDROP_H, Bitmap.Config.ARGB_8888)
        return ArtworkScene(
            key = key,
            layout = layout,
            cover = cover,
            coverRect = rect,
            coverGenerated = generated,
            coverNativePx = nativePx,
            backdrop = backdrop,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            light = light,
            sunTint = sunTint(light, HaloLight.SUN_LIFT, lighting),
            thumb = thumb,
        )
    }

    /**
     * Tint for the additive sun sprite. The sprite is added in sRGB, where small additions
     * on a dark wall look much brighter than their linear value suggests – so the tint is
     * defined directly as the sRGB lift at the sun's centre: [lift] of the light colour.
     */
    private fun sunTint(light: Int, lift: Float, lighting: Lighting): Int {
        val lit = lighting.apply(Oklab.fromArgb(light)).toArgb()
        val r = (lit shr 16) and 0xFF
        val g = (lit shr 8) and 0xFF
        val b = lit and 0xFF
        val maxC = maxOf(r, g, b).coerceAtLeast(1)
        val night = (1f - lighting.brightness).coerceIn(0f, 1f)
        val k = lift * 255f / maxC * (1f - 1.6f * night).coerceAtLeast(0.25f)
        fun ch(v: Int) = (v * k).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Stable per album/station: about half of all albums show the cover on the right. */
    fun mirrorFor(seed: String): Boolean = (stableHash(seed) ushr 7) and 1 == 1
}
