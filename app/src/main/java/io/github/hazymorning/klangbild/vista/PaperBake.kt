package io.github.hazymorning.klangbild.vista

import io.github.hazymorning.klangbild.render.ImageOps
import io.github.hazymorning.klangbild.render.LinearImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The sheets of the paper cut, back to front. */
enum class SheetId { SKY, RANGE_FAR, RANGE_NEAR, SHORE, LAKE, WOOD_LEFT, WOOD_RIGHT, MEADOW, FRONT_LEFT, FRONT_RIGHT }

/**
 * A baked piece of the picture in its own box of the canvas (pixels, the canvas starting
 * [PaperMotif.OVERSCAN] dp left of and above the screen): ink weights and coverage (see [Inks]).
 * For the land sheets red is the foot (the lower part, a shade lighter, like light lying
 * between the sheets), green the lit paper edge along the top, blue the snow. A sheet baked
 * smaller (soft shadows) is drawn [scale] times its size.
 */
class Sheet(val left: Int, val top: Int, val width: Int, val height: Int, val pixels: IntArray, val scale: Int = 1)

/** Snow, leaves and blossom as the season stands – what changes the shapes, not only the colours. */
data class SeasonShapes(
    /** Snow on the ground, 0 (none) to 1 (deep winter). */
    val snow: Float = 0f,
    /** How far snow reaches down the mountains, 0 (only the highest summits) to 1 (all of them). */
    val peaks: Float = 0f,
    /** Leaves on the tree, 0 (bare) to 1 (full crown). */
    val leaves: Float = 1f,
    /** Blossom on the tree, 0 to 1. */
    val blossom: Float = 0f,
)

/**
 * The broad tree: trunk and branches as one sprite, the crown as another (it sways). While the
 * leaves come and go the crown is drawn [crownAlpha] strong – thin paper, the branches showing through.
 */
class TreeSprites(val trunk: Sheet, val crown: Sheet?, val pivotX: Float, val pivotY: Float, val crownAlpha: Float = 1f)

/** Something that sways about its foot (canvas pixels): a clump of grass, the reeds. */
class SwaySprite(val sheet: Sheet, val footX: Float, val footY: Float, val front: SheetId)

/** Everything the picture is drawn from, for one screen size and one state of the season. */
class PaperBaked(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val density: Float,
    val sheets: Map<SheetId, Sheet>,
    /** Soft shadows of the sheets that cast one (half resolution, coverage only). */
    val shadows: Map<SheetId, Sheet>,
    val trees: List<TreeSprites>,
    val treeShadows: List<Sheet>,
    /**
     * The broad-leaved trees among the firs of each wood (drawn over it): red and green the
     * weights of their second and third colour, blue the shade; drawn [PaperBaked.leafAlpha] strong.
     */
    val accents: Map<SheetId, Sheet>,
    val sway: List<SwaySprite>,
    val clouds: List<Sheet>,
    /** A soft round light (white, coverage only) for glows; drawn scaled. */
    val glow: Sheet,
    val shapes: SeasonShapes,
    /** How strongly leaves show on the broad-leaved trees: they thin out in autumn and come back in spring. */
    val leafAlpha: Float,
    /** The side the near bank is cut wide for, under the music's label. */
    val label: LabelSide,
    val bakeMs: Long,
) {
    /** Canvas pixels of a screen position in dp. */
    fun px(dp: Float): Float = (dp + PaperMotif.OVERSCAN) * density
}

/**
 * Bakes the paper cut for a screen: every sheet rasterised once with exact anti-aliasing,
 * its inks as weights. Colours are not baked – the light paints them every frame.
 */
object PaperBake {
    fun bake(widthPx: Int, heightPx: Int, density: Float, shapes: SeasonShapes = SeasonShapes(), label: LabelSide = LabelSide.LEFT): PaperBaked {
        val started = System.nanoTime()
        val m = PaperMotif
        val fg = m.foreground(label)
        val cw = ceil((m.WIDTH + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(widthPx)
        val ch = ceil((m.HEIGHT + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(heightPx)
        val b = Baker(cw, ch, density)
        // In deep winter the snow lies on whole slopes, not only along their tops.
        val deep = Shapes.smoothstep(0.55f, 0.9f, shapes.snow)
        val sheets = LinkedHashMap<SheetId, Sheet>()
        sheets[SheetId.SKY] = b.sky()
        sheets[SheetId.RANGE_FAR] = b.ridge(
            m.rangeFar, foot = 12f to 70f, snow = { x, depth -> b.peakSnow(m.rangeFar, x, depth, shapes, 228f, 300f, 0, teeth = 10f) }, seed = 2,
            bottom = RANGE_FAR_BOTTOM,
        )
        sheets[SheetId.RANGE_NEAR] = b.ridge(
            m.rangeNear, foot = 8f to 44f, snow = { x, depth -> b.peakSnow(m.rangeNear, x, depth, shapes, 274f, 352f, 5, teeth = 0f) }, seed = 3,
            bottom = RANGE_NEAR_BOTTOM,
        )
        sheets[SheetId.SHORE] = b.ridge(
            m.shore, foot = 3f to 14f, snow = { _, depth -> b.groundSnow(depth, shapes, 2f + 40f * deep) }, seed = 4,
            extras = treeLine(m.shore, b), extraSnow = shapes.snow, extraInk = 1f, bottom = PaperMotif.HORIZON + 6f,
        )
        sheets[SheetId.LAKE] = b.lake()
        val rowLeft = firRow(m.woodLeft, -14f, 470f, tall = 46f, short = 9f, spacing = 9.5f, b, seed = 1)
        val rowRight = firRow(m.woodRight, 664f, 978f, tall = 12f, short = 30f, spacing = 6.2f, b, seed = 2)
        sheets[SheetId.WOOD_LEFT] = b.ridge(
            m.woodLeft, foot = 20f to 110f, snow = { _, depth -> b.groundSnow(depth, shapes, 3.5f + 400f * deep) }, seed = 5,
            extras = rowLeft.firs, extraSnow = shapes.snow,
            outline = b.withShore(m.woodLeft, m.woodLeftShore, left = true), extraInk = 1f, footEnd = 190f, bottom = HIDDEN_BELOW,
        )
        sheets[SheetId.WOOD_RIGHT] = b.ridge(
            m.woodRight, foot = 20f to 100f, snow = { _, depth -> b.groundSnow(depth, shapes, 3.5f + 400f * deep) }, seed = 6,
            extras = rowRight.firs, extraSnow = shapes.snow,
            outline = b.withShore(m.woodRight, m.woodRightShore, left = false), extraInk = 1f, footEnd = 170f, bottom = HIDDEN_BELOW,
        )
        val accents = mapOf(SheetId.WOOD_LEFT to b.accents(rowLeft.broad), SheetId.WOOD_RIGHT to b.accents(rowRight.broad))
        sheets[SheetId.MEADOW] = b.ridge(m.meadow, foot = 30f to 120f, snow = { _, depth -> b.groundSnow(depth, shapes, 5f + 400f * deep) }, seed = 7)
        sheets[SheetId.FRONT_LEFT] = b.ridge(
            fg.frontLeft, foot = 1000f to 1001f, snow = { _, depth -> b.groundSnow(depth, shapes, 4f) }, seed = 8,
            outline = b.closedToCorner(fg.frontLeft, left = true), extras = grassFringe(fg.frontLeft, -20f, fg.frontLeft.last().x - 12f, b, 3),
        )
        val (firX, firH, firTiers) = m.bigFir
        sheets[SheetId.FRONT_RIGHT] = b.ridge(
            fg.frontRight, foot = 1000f to 1001f, snow = { _, depth -> b.groundSnow(depth, shapes, 4f) }, seed = 9,
            outline = b.closedToCorner(fg.frontRight, left = false),
            extras = grassFringe(fg.frontRight, fg.frontRight.last().x + 12f, 980f, b, 5) +
                Shapes.fir(firX, b.ridgeY(fg.frontRight, firX) + 6f, firH, firTiers, width = firH * 0.34f),
            extraSnow = shapes.snow,
        )
        val shadows = listOf(SheetId.RANGE_NEAR, SheetId.SHORE, SheetId.WOOD_LEFT, SheetId.WOOD_RIGHT, SheetId.MEADOW, SheetId.FRONT_LEFT, SheetId.FRONT_RIGHT)
            .associateWith { b.shadow(sheets.getValue(it)) }
        val trees = listOf(b.tree(fg.tree, b.ridgeY(m.meadow, fg.tree.x) + 2f, shapes))
        val treeShadows = trees.map { t -> b.shadow(t.crown ?: t.trunk) }
        val sway = ArrayList<SwaySprite>()
        fg.grassLeft.forEachIndexed { i, (x, size) -> sway += b.clump(fg.frontLeft, x, size, i, SheetId.FRONT_LEFT) }
        fg.grassRight.forEachIndexed { i, (x, size) -> sway += b.clump(fg.frontRight, x, size, i + 10, SheetId.FRONT_RIGHT) }
        fg.reeds.forEachIndexed { i, (x, y, size) -> sway += b.reeds(x, y, size, i, if (x < 480f) SheetId.FRONT_LEFT else SheetId.FRONT_RIGHT) }
        val clouds = (0 until CLOUD_DESIGNS).map { b.cloud(it) }
        return PaperBaked(
            cw, ch, density, sheets, shadows, trees, treeShadows, accents, sway, clouds, b.glow(), shapes, leafAlpha(shapes.leaves), label,
            (System.nanoTime() - started) / 1_000_000,
        )
    }

    /** The far shore's tree line: tiny firs standing close along its whole length, cut fine. */
    private fun treeLine(knots: List<Knot>, b: Baker): List<Outline> {
        val out = ArrayList<Outline>()
        var x = -PaperMotif.OVERSCAN - 2f
        var k = 0
        while (x < PaperMotif.WIDTH + PaperMotif.OVERSCAN + 2f) {
            // Heights rise and fall along the shore like a real tree line, by a fixed rhythm.
            val h = 5.2f + 1.8f * sin(k * 1.7f) + 1.3f * sin(k * 0.41f + 1.3f)
            out += Shapes.fir(x, b.ridgeY(knots, x) + 1f, h, tiers = 2, width = h * 0.55f)
            x += 2.4f + 0.8f * (1 + sin(k * 2.3f))
            k++
        }
        return out
    }

    /** A broad-leaved tree among the firs: foot x and y (dp), height, and which of the three autumn colours it takes (0..2). */
    private class Broad(val x: Float, val baseY: Float, val height: Float, val tone: Int)

    private class Row(val firs: List<Outline>, val broad: List<Broad>)

    /**
     * Firs standing close along a ridge from [from] to [to] (dp): [tall] at the start, [short]
     * at the end (smaller towards the water), in a rhythm of heights and gaps that is fixed –
     * placed like a cut, not scattered. Every few trees a broad-leaved one stands among them
     * (its leaves turn in autumn), in a rhythm of its own.
     */
    private fun firRow(knots: List<Knot>, from: Float, to: Float, tall: Float, short: Float, spacing: Float, b: Baker, seed: Int): Row {
        val firs = ArrayList<Outline>()
        val broad = ArrayList<Broad>()
        var x = from
        var k = seed * 5
        while (x < to) {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            val size = tall + (short - tall) * t
            val h = size * (0.74f + 0.2f * sin(k * 1.9f) + 0.12f * sin(k * 0.63f + seed))
            val baseY = b.ridgeY(knots, x) + h * 0.1f + 1.5f
            if (BROAD_EVERY[k % BROAD_EVERY.size]) {
                broad += Broad(x, baseY, h * 0.86f, (k / 2 + seed) % 3)
            } else {
                val tiers = when {
                    h > 34f -> 6
                    h > 22f -> 5
                    h > 12f -> 4
                    else -> 3
                }
                firs += Shapes.fir(x, baseY, h, tiers)
            }
            x += spacing * (size / tall.coerceAtLeast(short)).coerceIn(0.45f, 1f) * (0.8f + 0.3f * (0.5f + 0.5f * sin(k * 2.7f)))
            k++
        }
        return Row(firs, broad)
    }

    /** Which places in a row of firs a broad-leaved tree takes: a rhythm, not a scatter. */
    private val BROAD_EVERY = booleanArrayOf(false, false, true, false, false, false, true, false, true, false, false, false, false, true)

    /** The leaves' strength for [leaves] (0 bare … 1 full): gone before the last ones fall, back with the first green. */
    fun leafAlpha(leaves: Float): Float = Shapes.smoothstep(0.08f, 0.6f, leaves)

    /**
     * A fringe of grass along a near bank's top between [from] and [to] (dp): close blades in
     * tufts – taller ones gathered, short ones between – each leaning a little its own way.
     */
    private fun grassFringe(knots: List<Knot>, from: Float, to: Float, b: Baker, seed: Int): List<Outline> {
        val out = ArrayList<Outline>()
        var x = from
        var k = seed * 7
        while (x < to) {
            val base = b.ridgeY(knots, x) + 2f
            // Tufts every 30–50 dp: a slow wave of height, sharpened, plus a quick flutter.
            val tuft = (0.5f + 0.5f * sin(x * 0.16f + seed + 0.6f * sin(x * 0.043f))).let { it * it * it }
            val h = 4f + 10f * tuft + 2.5f * (0.5f + 0.5f * sin(k * 1.91f + seed))
            val lean = 3f * sin(k * 0.83f + seed * 0.7f) + 1.5f * tuft
            out += Shapes.stroke(x, base + 2f, x + lean * 0.3f, base - h * 0.55f, x + lean, base - h, 1.4f, 0.2f, samples = 10)
            x += 1.5f + 1.1f * (0.5f + 0.5f * sin(k * 2.71f))
            k++
        }
        return out
    }

    /** The sky is baked down to here (dp): lower, the far range hides it everywhere. */
    private const val SKY_BOTTOM = 292f

    /** Below this (dp) the meadow hides every sheet behind it, from edge to edge. */
    private const val HIDDEN_BELOW = 524f

    /** The lake can only be seen between these (dp): the wooded shores close it in. */
    private const val LAKE_LEFT = 286f
    private const val LAKE_RIGHT = 936f

    /** The ranges are baked down to here (dp): the nearer range and the far shore hide what is lower. */
    private const val RANGE_FAR_BOTTOM = 322f
    private const val RANGE_NEAR_BOTTOM = 352f

    /** How many cloud designs there are; the sky draws them in an order that never repeats. */
    const val CLOUD_DESIGNS = 6

    /** How soft a sheet's shadow is (dp). */
    const val SHADOW_BLUR_DP = 2.2f

    /** Width of the lit paper edge along a sheet's top (dp). */
    private const val RIM_DP = 1.0f

    private val ZERO = Triple(0f, 0f, 0f)

    /**
     * The broad tree's crown: an egg (u across −1…1, v down 0…1), a little fuller low on the
     * left – drawn, not computed.
     */
    private val EGG = listOf(
        0f to 0f, 0.4f to 0.035f, 0.72f to 0.15f, 0.92f to 0.33f, 1f to 0.53f, 0.96f to 0.71f, 0.8f to 0.86f, 0.52f to 0.96f,
        0.16f to 1f, -0.2f to 0.99f, -0.54f to 0.94f, -0.8f to 0.82f, -0.96f to 0.64f, -0.99f to 0.44f, -0.88f to 0.25f,
        -0.64f to 0.1f, -0.33f to 0.02f,
    )

    /** The tree's limbs in the crown's coordinates: end (u, v), bend (u, v) – all well inside the egg. */
    private val TREE_LIMBS = listOf(
        floatArrayOf(-0.56f, 0.34f, -0.28f, 0.62f), floatArrayOf(-0.2f, 0.2f, -0.1f, 0.48f), floatArrayOf(0.16f, 0.18f, 0.06f, 0.46f),
        floatArrayOf(0.54f, 0.32f, 0.26f, 0.6f), floatArrayOf(-0.74f, 0.6f, -0.42f, 0.72f), floatArrayOf(0.72f, 0.56f, 0.4f, 0.7f),
    )

    /**
     * The snow line's teeth, as a paper cutter would snip them: points along the range (dp)
     * where a tongue of snow runs down (positive) or a rib of rock reaches up (negative),
     * straight cuts between – in a rhythm of uneven steps, placed, not scattered. Each range
     * reads it from its own offset.
     */
    private val TEETH: Pair<FloatArray, FloatArray> = run {
        val steps = floatArrayOf(15f, 10f, 21f, 12f, 17f, 9f, 23f, 13f, 11f, 19f, 14f, 8f)
        val down = floatArrayOf(1f, 0.5f, 0.85f, 0.3f, 0.95f, 0.6f, 0.7f, 0.4f, 0.9f)
        val up = floatArrayOf(-0.35f, -0.1f, -0.5f, -0.2f, -0.3f)
        val xs = ArrayList<Float>()
        val vs = ArrayList<Float>()
        var x = -60f
        var i = 0
        while (x < 1100f) {
            xs += x
            vs += if (i % 2 == 0) down[(i / 2) % down.size] else up[(i / 2) % up.size]
            x += steps[i % steps.size]
            i++
        }
        xs.toFloatArray() to vs.toFloatArray()
    }

    /** The teeth at [x] (dp), −0.5 … 1, for a range reading them from [offset]. */
    private fun teeth(x: Float, offset: Int): Float {
        val (xs, vs) = TEETH
        val shifted = x + offset * 37f
        var i = 0
        while (i < xs.size - 2 && xs[i + 1] < shifted) i++
        val t = ((shifted - xs[i]) / (xs[i + 1] - xs[i])).coerceIn(0f, 1f)
        return vs[i] + (vs[i + 1] - vs[i]) * t
    }

    private class Baker(val cw: Int, val ch: Int, val d: Float) {
        private val o = PaperMotif.OVERSCAN
        private val lines = HashMap<List<Knot>, Polyline>()

        fun px(dp: Float) = (dp + o) * d

        fun line(knots: List<Knot>) = lines.getOrPut(knots) { Shapes.line(knots) }

        fun ridgeY(knots: List<Knot>, x: Float): Float = line(knots).yAt(x)

        /** A contour followed by its shore down to the bottom, closed through the canvas corner on its side. */
        fun withShore(top: List<Knot>, shore: List<Knot>, left: Boolean): Outline {
            val all = Shapes.line(top + shore.drop(1))
            val cornerX = if (left) -o - 4f else PaperMotif.WIDTH + o + 4f
            return Outline(all.xs + floatArrayOf(cornerX), all.ys + floatArrayOf(PaperMotif.BOTTOM))
        }

        /** A contour that runs from a side down to the bottom, closed through that side's lower corner. */
        fun closedToCorner(top: List<Knot>, left: Boolean): Outline {
            val all = line(top)
            val cornerX = if (left) -o - 4f else PaperMotif.WIDTH + o + 4f
            return Outline(all.xs + floatArrayOf(cornerX), all.ys + floatArrayOf(PaperMotif.BOTTOM))
        }

        /** The sky: four gradient stops from the top of the canvas to the horizon, as weights (stop 0 is the base). */
        fun sky(): Sheet {
            // Below the far range's lowest point the sky is hidden everywhere.
            val h = ceil(px(SKY_BOTTOM)).toInt()
            val r = InkRaster(cw, h, seed = 1)
            val horizon = px(PaperMotif.HORIZON)
            for (y in 0 until h) {
                val w = SkyStops.weights((y + 0.5f) / horizon)
                for (x in 0 until cw) r.over(x, y, w[0], w[1], w[2], 1f, dither = true)
            }
            return Sheet(0, 0, cw, h, r.pixels)
        }

        /**
         * The lake: the sky's light mirrored – bright by the far shore (base), deeper towards the
         * viewer (red), its own colour near the bottom (green).
         */
        fun lake(): Sheet {
            // Only between the wooded shores, and only down to where the meadow hides it.
            val left = max(0, floor(px(LAKE_LEFT)).toInt())
            val right = min(cw, ceil(px(LAKE_RIGHT)).toInt())
            val top = floor(px(PaperMotif.HORIZON - 1f)).toInt()
            val h = min(ch, ceil(px(HIDDEN_BELOW)).toInt()) - top
            val w = right - left
            val r = InkRaster(w, h, seed = 7)
            val shore = px(PaperMotif.HORIZON)
            for (y in 0 until h) {
                val py = top + y + 0.5f
                val depth = (py - shore) / d
                val cov = (py - shore + 0.5f).coerceIn(0f, 1f)
                val deeper = Shapes.smoothstep(0f, 70f, depth)
                val own = Shapes.smoothstep(60f, 200f, depth)
                for (x in 0 until w) r.over(x, y, deeper * (1f - own), own, 0f, cov, dither = true)
            }
            return Sheet(left, top, w, h, r.pixels)
        }

        /**
         * A sheet under a contour: everything below [knots] (closed down to [bottom], or
         * [outline]), plus [extras] cut from the same paper on top (firs, grass), baked in a box
         * just big enough for it. The foot fades in between [foot] dp under the contour; [snow]
         * says for a column and a depth whether snow lies there.
         */
        fun ridge(
            knots: List<Knot>,
            foot: Pair<Float, Float>,
            snow: (xDp: Float, depthDp: Float) -> Float,
            seed: Int,
            extras: List<Outline> = emptyList(),
            extraSnow: Float = 0f,
            outline: Outline? = null,
            /** The ink the extras take: 0 the sheet's own, 1 its second (the woods' evergreen firs). */
            extraInk: Float = 0f,
            /** Where the foot fades out again (dp under the contour): a band of light between the sheets. */
            footEnd: Float = Float.MAX_VALUE,
            /** How far down the sheet is baked (dp): below, nearer sheets hide it everywhere. */
            bottom: Float = PaperMotif.BOTTOM,
        ): Sheet {
            val line = line(knots)
            val poly = clipBelow(outline ?: line.closedDown(bottom), bottom)
            var minX = poly.minX()
            var maxX = poly.maxX()
            var minY = poly.minY()
            for (e in extras) {
                minX = min(minX, e.minX())
                maxX = max(maxX, e.maxX())
                minY = min(minY, e.minY())
            }
            val left = max(0, floor(px(minX)).toInt() - 2)
            val right = min(cw, ceil(px(maxX)).toInt() + 2)
            val top = max(0, floor(px(minY)).toInt() - 2)
            val bot = min(ch, ceil(px(poly.maxY())).toInt() + 1)
            val w = right - left
            val h = bot - top
            val r = InkRaster(w, h, seed)
            val cov = Coverage(w, h)
            cov.polygon(FloatArray(poly.size) { px(poly.xs[it]) - left }, FloatArray(poly.size) { px(poly.ys[it]) - top })
            val c = cov.resolve()
            val ridge = FloatArray(w) { x -> line.yAt((left + x + 0.5f) / d - o) }
            for (y in 0 until h) {
                val yDp = (top + y + 0.5f) / d - o
                for (x in 0 until w) {
                    val k = c[y * w + x]
                    if (k <= 0f) continue
                    val depth = yDp - ridge[x]
                    val f = Shapes.smoothstep(foot.first, foot.second, depth) * (1f - Shapes.smoothstep(footEnd - 60f, footEnd, depth))
                    val edge = 1f - Shapes.smoothstep(RIM_DP - 0.5f / d, RIM_DP + 0.5f / d, depth)
                    val s = snow((left + x + 0.5f) / d - o, depth)
                    r.over(x, y, f * (1f - s), edge * (1f - s), s, k, dither = true)
                }
            }
            // Snow lies along the upper edges of a fir's tiers – only where a fir is tall enough to
            // show it; on the small ones it would be specks.
            for (e in extras) {
                val band = if (extraSnow > 0f && e.maxY() - e.minY() > 24f) 0.8f + 1.4f * extraSnow else 0f
                cut(r, left, top, e, extraInk, band)
            }
            return Sheet(left, top, w, h, r.pixels)
        }

        /** [poly] with everything below [bottom] (dp) cut away – its points pulled up to it. */
        private fun clipBelow(poly: Outline, bottom: Float): Outline =
            if (poly.maxY() <= bottom) poly else Outline(poly.xs, FloatArray(poly.size) { min(poly.ys[it], bottom) })

        /**
         * Lays outline [e] (dp) onto raster [r] (which starts at canvas column [left], row [top]) in the ink
         * [ink] (green's weight), with snow on its upward-facing edges [snowBand] dp deep.
         */
        private fun cut(r: InkRaster, left: Int, top: Int, e: Outline, ink: Float, snowBand: Float) {
            val ex0 = max(left, floor(px(e.minX())).toInt() - 1)
            val ex1 = min(left + r.width, ceil(px(e.maxX())).toInt() + 1)
            val ey0 = max(top, floor(px(e.minY())).toInt() - 1)
            val ey1 = min(top + r.height, ceil(px(e.maxY())).toInt() + 1)
            if (ex1 <= ex0 || ey1 <= ey0) return
            val ew = ex1 - ex0
            val eh = ey1 - ey0
            val ec = Coverage(ew, eh)
            ec.polygon(FloatArray(e.size) { px(e.xs[it]) - ex0 }, FloatArray(e.size) { px(e.ys[it]) - ey0 })
            val v = ec.resolve()
            val reach = snowBand * d
            val step = reach.toInt()
            val frac = reach - step
            for (y in 0 until eh) for (x in 0 until ew) {
                val k = v[y * ew + x]
                if (k <= 0f) continue
                // Snow where the paper ends within the band above: air over it.
                val snow = if (reach > 0f) {
                    fun at(yy: Int) = if (yy < 0) 0f else v[yy * ew + x].coerceIn(0f, 1f)
                    val above = at(y - step) * (1f - frac) + at(y - step - 1) * frac
                    (1f - above).coerceIn(0f, 1f)
                } else {
                    0f
                }
                r.over(ex0 + x - left, ey0 + y - top, 0f, ink * (1f - snow), snow, k)
            }
        }

        /**
         * Snow on a range: down from its summits to a line that falls with the season – from
         * [summer] (dp, only the highest summits) to [winter] (all of it) – its lower edge cut
         * in uneven teeth: tongues of snow down the gullies, ribs of rock between ([TEETH]).
         */
        fun peakSnow(knots: List<Knot>, x: Float, depth: Float, shapes: SeasonShapes, summer: Float, winter: Float, offset: Int, teeth: Float): Float {
            val ridgeY = ridgeY(knots, x)
            // The lower range takes its snow later in autumn and loses it earlier in spring.
            val season = if (teeth > 0f) shapes.peaks else shapes.peaks * shapes.peaks
            val line = summer + (winter - summer) * season
            val reach = (line - ridgeY).coerceAtLeast(0f)
            if (reach <= 0f) return 0f
            // The teeth grow with the cap: a thin cap gets small ones, a deep one up to [teeth] dp;
            // without teeth the edge only swells gently, as snow lies on rounded hills.
            val cap = if (teeth > 0f) {
                reach + (reach * 0.5f).coerceAtMost(teeth) * teeth(x, offset)
            } else {
                reach + (reach * 0.4f).coerceAtMost(4f) * (0.6f * sin(x * 0.031f + offset) + 0.4f * sin(x * 0.083f + 2f * offset))
            }
            return 1f - Shapes.smoothstep(cap - 0.6f / d, cap + 0.6f / d, depth)
        }

        /** A band of snow along a sheet's top edge. */
        fun groundSnow(depth: Float, shapes: SeasonShapes, deepest: Float): Float {
            if (shapes.snow <= 0f) return 0f
            val cap = deepest * shapes.snow
            return 1f - Shapes.smoothstep(cap - 0.5f / d, cap + 0.5f / d, depth)
        }

        /** A soft shadow of a sheet: its coverage, at half resolution, blurred. */
        fun shadow(s: Sheet): Sheet {
            val f = 2
            val pad = (SHADOW_BLUR_DP * d * 2.5f / f).roundToInt() + 2
            val w = s.width / f + 2 * pad
            val h = s.height / f + 2 * pad
            val img = LinearImage(w, h)
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val v = (s.pixels[y * s.width + x] ushr 24) / 255f
                if (v > 0f) img.r[(y / f + pad) * w + x / f + pad] += v / (f * f)
            }
            ImageOps.blur(img, (SHADOW_BLUR_DP * d / f).roundToInt().coerceAtLeast(1))
            val px = IntArray(w * h) { i -> ((img.r[i] * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF }
            return Sheet(s.left - pad * f, s.top - pad * f, w, h, px, scale = f)
        }

        /**
         * The broad tree: a slim trunk forking into limbs and twigs (bare in winter, they carry
         * its shape), and one crown cut as an egg, its lower part a second, darker paper. While
         * the leaves come and go the crown is thin paper ([TreeSprites.crownAlpha]): the
         * branches show through it.
         */
        fun tree(t: PaperMotif.BroadTree, footY: Float, shapes: SeasonShapes): TreeSprites {
            val w = t.width
            val crownH = t.height * 0.74f
            val top = footY - t.height
            val cx = t.x
            fun x(u: Float) = cx + u * w / 2f
            fun y(v: Float) = top + v * crownH
            val forkY = y(0.8f)
            val parts = ArrayList<Outline>()
            parts += Shapes.stroke(cx - 1f, footY + 3f, cx - 1.8f, (footY + forkY) / 2f, cx, forkY, w * 0.1f, w * 0.068f)
            // Limbs from the fork: end (u, v) and the bend they pass (u, v).
            for ((i, lb) in TREE_LIMBS.withIndex()) {
                val w0 = if (i < 4) w * 0.052f else w * 0.04f
                parts += Shapes.stroke(cx, forkY + 2f, x(lb[2]), y(lb[3]), x(lb[0]), y(lb[1]), w0, w * 0.014f)
            }
            // Twigs off the limbs' ends, inside the crown's outline – shown while the crown is thin.
            if (shapes.leaves < 0.75f) for ((i, lb) in TREE_LIMBS.withIndex()) {
                val side = if (lb[0] < 0f) -1f else 1f
                val ex = x(lb[0])
                val ey = y(lb[1])
                parts += Shapes.stroke(x(lb[0] * 0.82f), y(lb[1] + (0.8f - lb[1]) * 0.18f), ex + side * w * 0.04f, ey - crownH * 0.04f, ex + side * w * (0.07f + 0.01f * i), ey - crownH * 0.09f, w * 0.018f, w * 0.008f)
                parts += Shapes.stroke(ex, ey, ex - side * w * 0.015f, ey - crownH * 0.05f, ex - side * w * 0.025f, ey - crownH * 0.1f, w * 0.015f, w * 0.007f)
            }
            val trunk = sprite(parts) { _, _ -> ZERO }
            val alpha = leafAlpha(shapes.leaves)
            val crown = if (alpha > 0.01f) {
                val ccy = y(0.5f)
                fun egg(du: Float, dv: Float, scale: Float) = Shapes.loop(EGG.map { (u, v) -> Knot(cx + ((u + du) * w / 2f) * scale, ccy + (v - 0.5f + dv) * crownH * scale) }, step = 0.8f)
                // The lit paper: the same egg, a little higher – what is left of the crown below it is in shade.
                crownSprite(egg(0f, 0f, 1f), egg(-0.04f, -0.16f, 0.97f), top, crownH, shapes.blossom)
            } else {
                null
            }
            return TreeSprites(trunk, crown, px(cx), px(forkY), alpha)
        }

        /** The crown: [outline]; red the shade wherever [lit] does not reach, blue the blossom, densest towards the top. */
        private fun crownSprite(outline: Outline, lit: Outline, top: Float, crownH: Float, blossom: Float): Sheet {
            val x0 = floor(px(outline.minX())).toInt() - 2
            val y0 = floor(px(outline.minY())).toInt() - 2
            val x1 = ceil(px(outline.maxX())).toInt() + 2
            val y1 = ceil(px(outline.maxY())).toInt() + 2
            val w = x1 - x0
            val h = y1 - y0
            fun coverage(o: Outline): FloatArray {
                val c = Coverage(w, h)
                c.polygon(FloatArray(o.size) { px(o.xs[it]) - x0 }, FloatArray(o.size) { px(o.ys[it]) - y0 })
                return c.resolve()
            }
            val shape = coverage(outline)
            val light = coverage(lit)
            val r = InkRaster(w, h, seed = x0 * 31 + y0)
            for (yy in 0 until h) for (xx in 0 until w) {
                val i = yy * w + xx
                val k = shape[i]
                if (k <= 0f) continue
                val v = (((y0 + yy + 0.5f) / d - o) - top) / crownH
                val bloom = if (blossom > 0f) blossom * (0.6f + 0.4f * (1f - Shapes.smoothstep(0.1f, 0.9f, v))) else 0f
                r.over(xx, yy, 1f - light[i].coerceIn(0f, 1f), 0f, bloom, k)
            }
            return Sheet(x0, y0, w, h, r.pixels)
        }

        /**
         * The broad-leaved trees among a row of firs, as one sprite: a slim trunk and a round
         * crown each; red and green weigh in their second and third colour by [Broad.tone],
         * blue the crown's shaded underside.
         */
        fun accents(trees: List<Broad>): Sheet {
            if (trees.isEmpty()) return Sheet(0, 0, 1, 1, IntArray(1))
            // Each tree: its trunk (in the shade's colour) and its crown.
            val parts = ArrayList<Pair<Outline, Broad?>>()
            for (t in trees) {
                val r = t.height * 0.3f
                val cy = t.baseY - t.height + r * 1.05f
                parts += Shapes.stroke(t.x, t.baseY + 1f, t.x, (t.baseY + cy) / 2f, t.x, cy, (t.height * 0.07f).coerceAtLeast(0.9f), 0.6f, samples = 6) to null
                parts += Shapes.ellipse(t.x, cy, r * 0.92f, r * 1.06f) to t
            }
            val outlines = parts.map { it.first }
            val x0 = floor(px(outlines.minOf { it.minX() })).toInt() - 2
            val y0 = floor(px(outlines.minOf { it.minY() })).toInt() - 2
            val x1 = ceil(px(outlines.maxOf { it.maxX() })).toInt() + 2
            val y1 = ceil(px(outlines.maxOf { it.maxY() })).toInt() + 2
            val w = x1 - x0
            val h = y1 - y0
            val r = InkRaster(w, h, seed = 91)
            for ((outline, t) in parts) {
                val bx0 = max(0, floor(px(outline.minX())).toInt() - 1 - x0)
                val by0 = max(0, floor(px(outline.minY())).toInt() - 1 - y0)
                val bx1 = min(w, ceil(px(outline.maxX())).toInt() + 1 - x0)
                val by1 = min(h, ceil(px(outline.maxY())).toInt() + 1 - y0)
                val bw = bx1 - bx0
                val bh = by1 - by0
                if (bw <= 0 || bh <= 0) continue
                val c = Coverage(bw, bh)
                c.polygon(FloatArray(outline.size) { px(outline.xs[it]) - x0 - bx0 }, FloatArray(outline.size) { px(outline.ys[it]) - y0 - by0 })
                val v = c.resolve()
                if (t == null) {
                    for (yy in 0 until bh) for (xx in 0 until bw) r.over(bx0 + xx, by0 + yy, 0f, 0f, 1f, v[yy * bw + xx])
                    continue
                }
                val red = if (t.tone == 1) 1f else 0f
                val green = if (t.tone == 2) 1f else 0f
                val crownR = t.height * 0.3f
                val cy = t.baseY - t.height + crownR * 1.05f
                for (yy in 0 until bh) for (xx in 0 until bw) {
                    val k = v[yy * bw + xx]
                    if (k <= 0f) continue
                    val yDp = (y0 + by0 + yy + 0.5f) / d - o
                    val shade = Shapes.smoothstep(cy + crownR * 0.1f, cy + crownR * 0.75f, yDp) * 0.6f
                    r.over(bx0 + xx, by0 + yy, red * (1f - shade), green * (1f - shade), shade, k)
                }
            }
            return Sheet(x0, y0, w, h, r.pixels)
        }

        /** A clump of grass on a near shore's edge: long blades fanning out from one foot. */
        fun clump(knots: List<Knot>, x: Float, size: Float, i: Int, front: SheetId): SwaySprite {
            val foot = ridgeY(knots, x) + 3f
            val h = 30f * size
            val parts = ArrayList<Outline>()
            val blades = 9
            for (k in 0 until blades) {
                val f = (k - (blades - 1) / 2f) / ((blades - 1) / 2f)
                val len = h * (0.5f + 0.5f * (1f - f * f) * (0.8f + 0.2f * sin(k * 2.3f + i)))
                val tipX = x + f * h * 0.42f + h * 0.06f * sin(i * 1.7f)
                val tipY = foot - len * (1f - 0.18f * f * f)
                parts += Shapes.stroke(x + f * 1.6f, foot + 2f, x + f * h * 0.08f, foot - len * 0.62f, tipX, tipY, 1.7f, 0.2f, samples = 12)
            }
            return SwaySprite(sprite(parts) { _, _ -> ZERO }, px(x), px(foot), front)
        }

        /** Reeds by the water: tall stems with their brown heads and a few long leaves. */
        fun reeds(x: Float, foot: Float, size: Float, i: Int, front: SheetId): SwaySprite {
            val parts = ArrayList<Outline>()
            val stems = listOf(-6f to 1.0f, 2f to 1.18f, 9f to 0.86f, -13f to 0.72f)
            for ((k, s) in stems.withIndex()) {
                val (dx, hk) = s
                val h = 64f * size * hk
                val tipX = x + dx + (k - 1.5f) * 2.5f + i
                parts += Shapes.stroke(x + dx * 0.4f, foot + 2f, x + dx * 0.7f, foot - h * 0.5f, tipX, foot - h, 1.3f, 0.9f, samples = 14)
                // The head: a slim upright oval just under the stem's tip.
                parts += Shapes.ellipse(tipX - 0.15f, foot - h + 9f * size, 2.4f * size, 7.5f * size)
            }
            // Long leaves curving out to the sides.
            parts += Shapes.stroke(x, foot + 2f, x - 10f, foot - 30f * size, x - 24f * size, foot - 44f * size, 2.6f, 0.2f)
            parts += Shapes.stroke(x + 2f, foot + 2f, x + 14f, foot - 26f * size, x + 26f * size, foot - 36f * size, 2.4f, 0.2f)
            return SwaySprite(sprite(parts) { _, _ -> ZERO }, px(x), px(foot), front)
        }

        /**
         * A cloud cut from paper: a long, low band, its ends tapering out, and a second,
         * shorter band laid over it a little higher – calm as mist, never a puff.
         */
        fun cloud(i: Int): Sheet {
            val designs = listOf(
                floatArrayOf(320f, 15f, 2f, 0.0f), floatArrayOf(220f, 12f, 1f, 0.3f), floatArrayOf(400f, 17f, 3f, 0.6f),
                floatArrayOf(170f, 10f, 1f, 0.9f), floatArrayOf(280f, 14f, 2f, 1.2f), floatArrayOf(140f, 9f, 1f, 1.5f),
            )
            val (len, th, swells, phase) = designs[i]
            val parts = listOf(
                Shapes.cloudStrip(0f, 0f, len, th, swells.toInt(), phase),
                Shapes.cloudStrip(len * (0.18f + 0.06f * (i % 3)), -th * 0.62f, len * 0.52f, th * 0.78f, 1, phase + 0.4f),
            )
            return sprite(parts) { _, y -> Triple(Shapes.smoothstep(-th * 0.25f, th * 0.1f, y), 0f, 0f) }
        }

        /** A soft round light, 1 in the middle to 0 at the rim (drawn scaled). */
        fun glow(): Sheet {
            val side = 256
            val px = IntArray(side * side)
            var s = 77
            for (y in 0 until side) for (x in 0 until side) {
                val u = (x + 0.5f) / side * 2 - 1
                val v = (y + 0.5f) / side * 2 - 1
                val r2 = u * u + v * v
                val a = exp(-r2 * 3.2f) * (1f - Shapes.smoothstep(0.7f, 1f, sqrt(r2)))
                s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
                val dither = ((s ushr 8) % 1000) / 1000f - 0.5f
                px[y * side + x] = ((a * 255f + dither).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
            }
            return Sheet(0, 0, side, side, px)
        }

        /** Rasterises [parts] (dp) into a sprite just big enough for them; [weights] gives each pixel's inks. */
        fun sprite(parts: List<Outline>, weights: (xDp: Float, yDp: Float) -> Triple<Float, Float, Float>): Sheet {
            val x0 = floor(px(parts.minOf { it.minX() })).toInt() - 2
            val y0 = floor(px(parts.minOf { it.minY() })).toInt() - 2
            val x1 = ceil(px(parts.maxOf { it.maxX() })).toInt() + 2
            val y1 = ceil(px(parts.maxOf { it.maxY() })).toInt() + 2
            val w = x1 - x0
            val h = y1 - y0
            val r = InkRaster(w, h, seed = x0 * 31 + y0)
            for (p in parts) {
                val c = Coverage(w, h)
                c.polygon(FloatArray(p.size) { px(p.xs[it]) - x0 }, FloatArray(p.size) { px(p.ys[it]) - y0 })
                val v = c.resolve()
                for (y in 0 until h) for (x in 0 until w) {
                    val k = v[y * w + x]
                    if (k <= 0f) continue
                    val (a, b, c3) = weights((x0 + x + 0.5f) / d - o, (y0 + y + 0.5f) / d - o)
                    r.over(x, y, a, b, c3, k)
                }
            }
            return Sheet(x0, y0, w, h, r.pixels)
        }
    }
}

/** Where the sky's four colours sit between the top of the canvas (0) and the horizon (1). */
object SkyStops {
    val positions = floatArrayOf(0f, 0.42f, 0.78f, 1f)

    /** The weights of stops 1, 2 and 3 at [v] (stop 0 takes the rest). */
    fun weights(v: Float): FloatArray {
        val w = FloatArray(3)
        val p = positions
        val t = v.coerceIn(0f, 1f)
        when {
            t <= p[1] -> w[0] = t / p[1]
            t <= p[2] -> {
                val k = (t - p[1]) / (p[2] - p[1])
                w[0] = 1 - k; w[1] = k
            }
            else -> {
                val k = (t - p[2]) / (p[3] - p[2])
                w[1] = 1 - k; w[2] = k
            }
        }
        return w
    }
}
