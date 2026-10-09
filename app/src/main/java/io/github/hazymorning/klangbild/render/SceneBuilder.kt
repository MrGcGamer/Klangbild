package io.github.hazymorning.klangbild.render

import android.graphics.Bitmap
import io.github.hazymorning.klangbild.art.ChladniArt
import io.github.hazymorning.klangbild.art.stableHash
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverAnalysis
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.ScenePalette
import io.github.hazymorning.klangbild.domain.StageView
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
     * small for the square is matted (a dark passe-partout); a generated plate is round and
     * transparent around the disc.
     */
    val cover: Bitmap,
    /** The print's outline on the wall. */
    val shape: PrintShape,
    val coverGenerated: Boolean,
    /** Size of the source image (shorter side), null for generated art. */
    val coverNativePx: Int?,
    /**
     * The wall around the print, scaled to the whole screen: in the dark the print's light on
     * black ([WallGlow]; in Cover Flow the pool of light above the cards, [FlowLight]), in the
     * light paper with the print's shadow ([WallShade]; in Cover Flow plain paper).
     */
    val wall: Bitmap,
    val palette: ScenePalette,
    val analysis: CoverAnalysis,
    val lighting: Lighting,
    /** 48 × 48 thumbnail of the cover, kept to re-light the scene without the source. */
    val thumb: IntArray,
    /** A small cover in its mat: the light comes from the mat's edge (barely any). */
    val matted: Boolean = false,
    /** How much of the cover's colour the light wall takes (the light level setting). */
    val wallTint: Float = 1f,
    /** The print as a card of the cover flow, with its mirror image; null in the single view. */
    val card: FlowCard? = null,
) {
    val screen: ScreenSpec get() = layout.screen

    val appearance: Appearance get() = palette.appearance

    /** The square the print bitmap occupies (dp). */
    val coverRect: RectDp get() = layout.coverRect(layout.art)

    /** Corner radius the artwork is drawn with (dp); none for a round plate. */
    val corner: Float get() = if (shape.round) 0f else shape.corner
}

object SceneBuilder {
    const val THUMB = 48

    /**
     * Passe-partout for small prints on the dark wall: near-black mat board with a faint grain
     * (a light board would be the largest static bright area on the OLED and glare at night)…
     */
    const val MAT = 0xFF141312.toInt()

    /** …and the white core of the bevel around the window, as cut into real mat board. */
    const val BEVEL = 0xFFC8C1B6.toInt()

    /** On the light wall the board is ivory, a shade lighter than the paper, its bevel white. */
    const val MAT_LIGHT = 0xFFF3EFE8.toInt()
    const val BEVEL_LIGHT = 0xFFFFFDFA.toInt()

    /** Share of the free mat above the print (the rest goes below it). */
    private const val OPTICAL_TOP = 0.455f

    /**
     * @param source decoded cover or null (radio, missing art) → generated Chladni figure.
     */
    fun build(
        key: String,
        source: Bitmap?,
        artSeed: String,
        screen: ScreenSpec,
        lighting: Lighting,
        appearance: Appearance = Appearance.DARK,
        wallTint: Float = 1f,
        view: StageView = StageView.SINGLE,
    ): ArtworkScene {
        val layout = HaloLayout(screen, view)
        val slotPx = (layout.art * screen.density).roundToInt()
        val slotRect = layout.coverRect(layout.art)
        val coverPixels: IntArray
        val coverSize: Int
        val nativePx: Int?
        val thumb: IntArray
        val shape: PrintShape
        var matted = false
        if (source != null) {
            val side = min(source.width, source.height)
            val square = IntArray(side * side)
            source.getPixels(square, 0, side, (source.width - side) / 2, (source.height - side) / 2, side, side)
            nativePx = side
            val coverDp = HaloLayout.coverDp(layout, nativePx)
            val coverPx = (coverDp * screen.density).roundToInt()
            val print = ImageOps.resize(square, side, side, coverPx, coverPx)
            val mat = coverPx < slotPx - 1
            matted = mat
            coverPixels = if (mat) matted(print, coverPx, slotPx, screen.density, appearance) else print
            coverSize = if (mat) slotPx else coverPx
            thumb = ImageOps.thumbnail(square, side, side, THUMB)
            shape = PrintShape(slotRect, corner = layout.radius)
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
        return assemble(key, layout, cover, shape, source == null, nativePx, analysis, lighting, thumb, matted, appearance, wallTint)
    }

    /**
     * Re-light an existing scene (time of day, appearance or wall tint changed) without the
     * source image: only a small print's mat changes with the wall.
     */
    fun relight(scene: ArtworkScene, lighting: Lighting, appearance: Appearance, wallTint: Float): ArtworkScene {
        val sameWall = scene.appearance == appearance
        val cover = if (scene.matted && !sameWall) remat(scene, appearance) else scene.cover
        return assemble(
            scene.key, scene.layout, cover, scene.shape, scene.coverGenerated,
            scene.coverNativePx, scene.analysis, lighting, scene.thumb, scene.matted, appearance, wallTint,
            // The card only changes with the wall (its mirror image, mat and hairline).
            card = scene.card.takeIf { sameWall },
        )
    }

    /** The print of a matted scene in the mat of [appearance]. */
    private fun remat(scene: ArtworkScene, appearance: Appearance): Bitmap {
        val slotPx = scene.cover.width
        val printPx = (HaloLayout.coverDp(scene.layout, scene.coverNativePx) * scene.screen.density).roundToInt()
        val print = IntArray(printPx * printPx)
        scene.cover.getPixels(print, 0, printPx, (slotPx - printPx) / 2, printTop(slotPx, printPx), printPx, printPx)
        val px = matted(print, printPx, slotPx, scene.screen.density, appearance)
        return Bitmap.createBitmap(px, slotPx, slotPx, Bitmap.Config.ARGB_8888)
    }

    private fun printTop(slotPx: Int, printPx: Int) = ((slotPx - printPx) * OPTICAL_TOP).roundToInt()

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
        matted: Boolean,
        appearance: Appearance,
        wallTint: Float,
        card: FlowCard? = null,
    ): ArtworkScene {
        val palette = CoverPalette.derive(analysis, lighting, appearance, wallTint)
        val flow = layout.view == StageView.FLOW
        val wall = when {
            flow && appearance == Appearance.DARK ->
                Bitmap.createBitmap(FlowLight.field(palette.lead, layout, lighting), FlowLight.MAP_W, FlowLight.MAP_H, Bitmap.Config.ARGB_8888)
            flow -> Bitmap.createBitmap(IntArray(16) { palette.wall }, 4, 4, Bitmap.Config.ARGB_8888)
            appearance == Appearance.DARK -> {
                // The wall sees the outside of the picture: for a matted print that is the mat board.
                val edges = if (matted) IntArray(THUMB * THUMB) { MAT } else thumb
                Bitmap.createBitmap(WallGlow.field(edges, THUMB, shape, layout.screen, lighting), WallGlow.MAP_W, WallGlow.MAP_H, Bitmap.Config.ARGB_8888)
            }
            else -> Bitmap.createBitmap(WallShade.field(palette.wall, shape, layout.screen), WallGlow.MAP_W, WallGlow.MAP_H, Bitmap.Config.ARGB_8888)
        }
        val flowCard = if (!flow) null else card ?: FlowCards.build(
            cover = cover,
            round = shape.round,
            cornerPx = layout.radius * layout.screen.density,
            geometry = FlowGeometry(layout),
            density = layout.screen.density,
            appearance = appearance,
            edgeLine = edgeLine(analysis.kind, shape.round, appearance),
        )
        return ArtworkScene(
            key = key,
            layout = layout,
            cover = cover,
            shape = shape,
            coverGenerated = generated,
            coverNativePx = nativePx,
            wall = wall,
            palette = palette,
            analysis = analysis,
            lighting = lighting,
            thumb = thumb,
            matted = matted,
            wallTint = wallTint,
            card = flowCard,
        )
    }

    /**
     * A faint inner hairline (ARGB) where the artwork would dissolve into the wall: white at
     * 7 % inside dark square artwork on black, black at 8 % inside pale artwork on paper.
     */
    fun edgeLine(kind: CoverKind, round: Boolean, appearance: Appearance): Int = when {
        round -> 0
        appearance == Appearance.DARK && kind == CoverKind.DARK -> 0x12FFFFFF
        appearance == Appearance.LIGHT && kind == CoverKind.LIGHT -> 0x14000000
        else -> 0
    }

    /**
     * A small print (shown at its honest size) in a passe-partout that fills the square, so
     * the grid holds: grained mat board, a 1 dp white bevel around the window, the print
     * centred.
     */
    fun matted(print: IntArray, printPx: Int, slotPx: Int, density: Float, appearance: Appearance = Appearance.DARK, seed: Int = 7): IntArray {
        val board = if (appearance == Appearance.DARK) MAT else MAT_LIGHT
        val bevelColor = if (appearance == Appearance.DARK) BEVEL else BEVEL_LIGHT
        var state = seed * 0x9E3779B9.toInt() or 1
        fun grain(): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return (state ushr 8) % 5 - 2
        }
        val out = IntArray(slotPx * slotPx) { shifted(board, grain()) }
        val left = (slotPx - printPx) / 2
        // Optically centred, as framers do: a little more mat below than above.
        val top = printTop(slotPx, printPx)
        for (y in 0 until printPx) System.arraycopy(print, y * printPx, out, (y + top) * slotPx + left, printPx)
        val bevel = density.roundToInt().coerceAtLeast(1)
        for (b in 1..bevel) {
            val x0 = left - b
            val x1 = left + printPx - 1 + b
            val y0 = top - b
            val y1 = top + printPx - 1 + b
            for (x in x0..x1) for (y in intArrayOf(y0, y1)) if (x in 0 until slotPx && y in 0 until slotPx) out[y * slotPx + x] = bevelColor
            for (y in y0..y1) for (x in intArrayOf(x0, x1)) if (x in 0 until slotPx && y in 0 until slotPx) out[y * slotPx + x] = bevelColor
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
     * output level of noise, so even the darkest stretch of a glow never shows bands.
     */
    fun ditherFor(tint: Int): Float {
        val maxC = maxOf((tint shr 16) and 0xFF, (tint shr 8) and 0xFF, tint and 0xFF).coerceAtLeast(16)
        return (1.25f * 255f / maxC).coerceIn(1f, 6f)
    }
}
