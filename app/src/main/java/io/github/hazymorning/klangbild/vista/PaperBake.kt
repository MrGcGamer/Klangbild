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

/** The broad tree: trunk and branches as one sprite, the crown as another (it sways). */
class TreeSprites(val trunk: Sheet, val crown: Sheet?, val pivotX: Float, val pivotY: Float)

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
    val sway: List<SwaySprite>,
    val clouds: List<Sheet>,
    /** A soft round light (white, coverage only) for glows; drawn scaled. */
    val glow: Sheet,
    val shapes: SeasonShapes,
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
    fun bake(widthPx: Int, heightPx: Int, density: Float, shapes: SeasonShapes = SeasonShapes()): PaperBaked {
        val started = System.nanoTime()
        val m = PaperMotif
        val cw = ceil((m.WIDTH + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(widthPx)
        val ch = ceil((m.HEIGHT + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(heightPx)
        val b = Baker(cw, ch, density)
        val sheets = LinkedHashMap<SheetId, Sheet>()
        sheets[SheetId.SKY] = b.sky()
        sheets[SheetId.RANGE_FAR] = b.ridge(m.rangeFar, foot = 12f to 70f, snow = { x, depth -> b.peakSnow(m.rangeFar, x, depth, shapes, 228f, 292f) }, seed = 2)
        sheets[SheetId.RANGE_NEAR] = b.ridge(m.rangeNear, foot = 8f to 44f, snow = { x, depth -> b.peakSnow(m.rangeNear, x, depth, shapes, 274f, 308f) }, seed = 3)
        sheets[SheetId.SHORE] = b.ridge(
            m.shore, foot = 3f to 14f, snow = { _, depth -> b.groundSnow(depth, shapes, 2f + 40f * Shapes.smoothstep(0.55f, 0.9f, shapes.snow)) }, seed = 4,
            extras = treeLine(m.shore, b), extraSnow = shapes.snow, extraInk = 1f,
        )
        sheets[SheetId.LAKE] = b.lake()
        sheets[SheetId.WOOD_LEFT] = b.ridge(
            m.woodLeft, foot = 20f to 110f, snow = { _, depth -> b.groundSnow(depth, shapes, 3.5f) }, seed = 5,
            extras = firRow(m.woodLeft, -14f, 470f, tall = 46f, short = 9f, spacing = 9.5f, b, seed = 1), extraSnow = shapes.snow,
            outline = b.withShore(m.woodLeft, m.woodLeftShore, left = true), extraInk = 1f, footEnd = 190f,
        )
        sheets[SheetId.WOOD_RIGHT] = b.ridge(
            m.woodRight, foot = 20f to 100f, snow = { _, depth -> b.groundSnow(depth, shapes, 3.5f) }, seed = 6,
            extras = firRow(m.woodRight, 664f, 978f, tall = 10f, short = 30f, spacing = 10f, b, seed = 2), extraSnow = shapes.snow,
            outline = b.withShore(m.woodRight, m.woodRightShore, left = false), extraInk = 1f, footEnd = 170f,
        )
        // In deep winter the meadow lies under snow, all of it.
        sheets[SheetId.MEADOW] = b.ridge(m.meadow, foot = 30f to 120f, snow = { _, depth -> b.groundSnow(depth, shapes, 5f + 400f * Shapes.smoothstep(0.55f, 0.9f, shapes.snow)) }, seed = 7)
        sheets[SheetId.FRONT_LEFT] = b.ridge(
            m.frontLeft, foot = 1000f to 1001f, snow = { _, depth -> b.groundSnow(depth, shapes, 4f) }, seed = 8,
            outline = b.closedToCorner(m.frontLeft, left = true), extras = grassFringe(m.frontLeft, -20f, 380f, b, 3),
        )
        val (firX, firH, firTiers) = m.bigFir
        sheets[SheetId.FRONT_RIGHT] = b.ridge(
            m.frontRight, foot = 1000f to 1001f, snow = { _, depth -> b.groundSnow(depth, shapes, 4f) }, seed = 9,
            outline = b.closedToCorner(m.frontRight, left = false),
            extras = grassFringe(m.frontRight, 730f, 980f, b, 5) + Shapes.fir(firX, b.ridgeY(m.frontRight, firX) + 6f, firH, firTiers, width = firH * 0.34f),
            extraSnow = shapes.snow,
        )
        val shadows = listOf(SheetId.RANGE_NEAR, SheetId.SHORE, SheetId.WOOD_LEFT, SheetId.WOOD_RIGHT, SheetId.MEADOW, SheetId.FRONT_LEFT, SheetId.FRONT_RIGHT)
            .associateWith { b.shadow(sheets.getValue(it)) }
        val trees = m.trees.map { b.tree(it, shapes) }
        val treeShadows = trees.map { t -> b.shadow(t.crown ?: t.trunk) }
        val sway = ArrayList<SwaySprite>()
        m.grassLeft.forEachIndexed { i, (x, size) -> sway += b.clump(m.frontLeft, x, size, i, SheetId.FRONT_LEFT) }
        m.grassRight.forEachIndexed { i, (x, size) -> sway += b.clump(m.frontRight, x, size, i + 10, SheetId.FRONT_RIGHT) }
        m.reeds.forEachIndexed { i, (x, y, size) -> sway += b.reeds(x, y, size, i, if (x < 480f) SheetId.FRONT_LEFT else SheetId.FRONT_RIGHT) }
        val clouds = (0 until CLOUD_DESIGNS).map { b.cloud(it) }
        return PaperBaked(cw, ch, density, sheets, shadows, trees, treeShadows, sway, clouds, b.glow(), shapes, (System.nanoTime() - started) / 1_000_000)
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

    /**
     * Firs standing close along a ridge from [from] to [to] (dp): [tall] at the start, [short]
     * at the end (smaller towards the water), in a rhythm of heights and gaps that is fixed –
     * placed like a cut, not scattered.
     */
    private fun firRow(knots: List<Knot>, from: Float, to: Float, tall: Float, short: Float, spacing: Float, b: Baker, seed: Int): List<Outline> {
        val out = ArrayList<Outline>()
        var x = from
        var k = seed * 5
        while (x < to) {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            val size = tall + (short - tall) * t
            val h = size * (0.78f + 0.16f * sin(k * 1.9f) + 0.1f * sin(k * 0.63f + seed))
            val tiers = when {
                h > 34f -> 6
                h > 22f -> 5
                h > 12f -> 4
                else -> 3
            }
            out += Shapes.fir(x, b.ridgeY(knots, x) + h * 0.06f + 1.5f, h, tiers)
            x += spacing * (size / tall.coerceAtLeast(short)).coerceIn(0.45f, 1f) * (0.8f + 0.3f * (0.5f + 0.5f * sin(k * 2.7f)))
            k++
        }
        return out
    }

    /** A fringe of grass blades along a near shore's top between [from] and [to] (dp). */
    private fun grassFringe(knots: List<Knot>, from: Float, to: Float, b: Baker, seed: Int): List<Outline> {
        val out = ArrayList<Outline>()
        var x = from
        var k = seed * 7
        while (x < to) {
            val base = b.ridgeY(knots, x) + 2f
            // Tall and short blades in a slow rhythm, each leaning a little its own way.
            val h = 7f + 5f * (0.5f + 0.5f * sin(k * 0.37f)) + 3f * (0.5f + 0.5f * sin(k * 1.91f + seed))
            val lean = 3.5f * sin(k * 0.83f + seed * 0.7f)
            out += Shapes.stroke(x, base + 2f, x + lean * 0.3f, base - h * 0.55f, x + lean, base - h, 1.5f, 0.2f, samples = 10)
            x += 2.1f + 1.3f * (0.5f + 0.5f * sin(k * 2.71f))
            k++
        }
        return out
    }

    /** How many cloud designs there are; the sky draws them in an order that never repeats. */
    const val CLOUD_DESIGNS = 6

    /** How soft a sheet's shadow is (dp). */
    const val SHADOW_BLUR_DP = 2.2f

    /** Width of the lit paper edge along a sheet's top (dp). */
    private const val RIM_DP = 1.0f

    private val ZERO = Triple(0f, 0f, 0f)

    /** Where the snow runs down in couloirs (dp): beside the summits of both ranges. */
    private val GULLIES = floatArrayOf(130f, 250f, 290f, 350f, 384f, 462f, 600f, 780f)

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
            val h = ceil(px(PaperMotif.HORIZON + 8f)).toInt()
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
            val top = floor(px(PaperMotif.HORIZON - 1f)).toInt()
            val h = ch - top
            val r = InkRaster(cw, h, seed = 7)
            val shore = px(PaperMotif.HORIZON)
            for (y in 0 until h) {
                val py = top + y + 0.5f
                val depth = (py - shore) / d
                val cov = (py - shore + 0.5f).coerceIn(0f, 1f)
                val deeper = Shapes.smoothstep(0f, 70f, depth)
                val own = Shapes.smoothstep(60f, 200f, depth)
                for (x in 0 until cw) r.over(x, y, deeper * (1f - own), own, 0f, cov, dither = true)
            }
            return Sheet(0, top, cw, h, r.pixels)
        }

        /**
         * A sheet under a contour: everything below [knots] (closed down, or [outline]), plus
         * [extras] cut from the same paper on top (firs, grass). The foot fades in between
         * [foot] dp under the contour; [snow] says for a column and a depth whether snow lies there.
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
        ): Sheet {
            val line = line(knots)
            var minY = line.ys.min()
            for (e in extras) minY = min(minY, e.minY())
            val top = max(0, floor(px(minY) - 2).toInt())
            val poly = outline ?: line.closedDown(PaperMotif.BOTTOM)
            val bot = min(ch, ceil(px(poly.maxY())).toInt())
            val h = bot - top
            val r = InkRaster(cw, h, seed)
            val cov = Coverage(cw, h)
            cov.polygon(FloatArray(poly.size) { px(poly.xs[it]) }, FloatArray(poly.size) { px(poly.ys[it]) - top })
            val c = cov.resolve()
            val ridge = FloatArray(cw) { x -> line.yAt((x + 0.5f) / d - o) }
            for (y in 0 until h) {
                val yDp = (top + y + 0.5f) / d - o
                for (x in 0 until cw) {
                    val k = c[y * cw + x]
                    if (k <= 0f) continue
                    val depth = yDp - ridge[x]
                    val f = Shapes.smoothstep(foot.first, foot.second, depth) * (1f - Shapes.smoothstep(footEnd - 60f, footEnd, depth))
                    val edge = 1f - Shapes.smoothstep(RIM_DP - 0.5f / d, RIM_DP + 0.5f / d, depth)
                    val s = snow((x + 0.5f) / d - o, depth)
                    r.over(x, y, f, edge, s, k, dither = true)
                }
            }
            for (e in extras) cut(r, top, h, e) { xDp, yDp -> Triple(0f, extraInk, if (extraSnow > 0f) outlineSnow(e, xDp, yDp, extraSnow) else 0f) }
            return Sheet(0, top, cw, h, r.pixels)
        }

        /** Lays outline [e] (dp) onto raster [r] (which starts at canvas row [top]) with [weights]. */
        private fun cut(r: InkRaster, top: Int, h: Int, e: Outline, weights: (Float, Float) -> Triple<Float, Float, Float>) {
            val ex0 = max(0, floor(px(e.minX())).toInt() - 1)
            val ex1 = min(cw, ceil(px(e.maxX())).toInt() + 1)
            val ey0 = max(top, floor(px(e.minY())).toInt() - 1)
            val ey1 = min(top + h, ceil(px(e.maxY())).toInt() + 1)
            if (ex1 <= ex0 || ey1 <= ey0) return
            val ew = ex1 - ex0
            val eh = ey1 - ey0
            val ec = Coverage(ew, eh)
            ec.polygon(FloatArray(e.size) { px(e.xs[it]) - ex0 }, FloatArray(e.size) { px(e.ys[it]) - ey0 })
            val v = ec.resolve()
            for (y in 0 until eh) for (x in 0 until ew) {
                val k = v[y * ew + x]
                if (k <= 0f) continue
                val (a, b, c) = weights((ex0 + x + 0.5f) / d - o, (ey0 + y + 0.5f) / d - o)
                r.over(ex0 + x, ey0 + y - top, a, b, c, k)
            }
        }

        /** Snow on a cut-out: wherever its outline lies just above, as on the tiers of a fir. */
        private fun outlineSnow(e: Outline, x: Float, y: Float, amount: Float): Float {
            var best = Float.MAX_VALUE
            for (i in 0 until e.size) {
                val dy = y - e.ys[i]
                if (dy < 0f || dy > 3.5f) continue
                if (abs(e.xs[i] - x) < 1.1f) best = min(best, dy)
            }
            val depth = 0.9f + 1.5f * amount
            return if (best < depth) 1f - Shapes.smoothstep(depth - 0.35f, depth + 0.35f, best) else 0f
        }

        /**
         * Snow on a range: down from its summits to a line that falls with the season – from
         * [summer] (dp, only the highest summits) to [winter] (all of it) – its lower edge
         * dipping into the gullies, as snow lies in a mountain's folds.
         */
        fun peakSnow(knots: List<Knot>, x: Float, depth: Float, shapes: SeasonShapes, summer: Float, winter: Float): Float {
            val ridgeY = ridgeY(knots, x)
            val line = summer + (winter - summer) * shapes.peaks
            val reach = (line - ridgeY).coerceAtLeast(0f)
            // The cap's lower edge: a slow swell along the range, and below every summit a few
            // couloirs where the snow runs further down – placed, not scattered.
            var gullies = 0f
            for (g in GULLIES) {
                // A couloir narrows to a point as it runs down: a V, not a drop.
                val u = abs(x - g) / 4.5f
                if (u < 1f) gullies = max(gullies, (1f - u) * (1f - u))
            }
            val cap = reach * (0.92f + 0.08f * sin(x * 0.045f)) + reach.coerceAtMost(18f) * 0.4f * gullies
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
         * The broad tree: a trunk that forks into limbs and twigs (bare in winter, they carry its
         * shape), and a crown of overlapping rounds, broader than tall, that thins from the
         * outside in as the leaves fall.
         */
        fun tree(t: PaperMotif.RoundTree, shapes: SeasonShapes): TreeSprites {
            val r = t.crown
            val cx = t.x
            val cy = t.footY - t.height + r * 1.05f
            val fork = t.footY - t.height * 0.42f
            val parts = ArrayList<Outline>()
            parts += Shapes.stroke(cx - 1.5f, t.footY + 3f, cx - 2.5f, (t.footY + fork) / 2f, cx, fork, r * 0.2f, r * 0.13f)
            val limbs = listOf(
                floatArrayOf(-0.72f, -0.42f, -0.30f, 0.02f), floatArrayOf(0.04f, -0.98f, -0.05f, -0.45f), floatArrayOf(0.74f, -0.36f, 0.32f, 0.06f),
                floatArrayOf(-0.42f, -0.82f, -0.12f, -0.30f), floatArrayOf(0.44f, -0.80f, 0.16f, -0.32f),
            )
            // Under a full crown the limbs end inside it; bare, they reach out to the crown's edge.
            val reach = 1f - 0.35f * Shapes.smoothstep(0.4f, 0.9f, shapes.leaves)
            for ((i, lb) in limbs.withIndex()) {
                val w0 = if (i < 3) r * 0.1f else r * 0.07f
                val ex = cx + lb[0] * r * reach
                val ey = fork + (cy + lb[1] * r - fork) * reach
                parts += Shapes.stroke(cx, fork + 2f, cx + lb[2] * r * reach, fork + (cy + lb[3] * r - fork) * reach, ex, ey, w0, r * 0.025f)
            }
            // Twigs off every limb's last third – only while the crown is thin enough to show them.
            if (shapes.leaves < 0.6f) for ((i, lb) in limbs.withIndex()) {
                val ex = cx + lb[0] * r
                val ey = cy + lb[1] * r
                val side = if (lb[0] < 0) -1f else 1f
                parts += Shapes.stroke(ex - lb[0] * r * 0.3f, ey - lb[1] * r * 0.25f, ex + side * r * 0.08f, ey - r * 0.12f, ex + side * r * (0.18f + 0.04f * i), ey - r * 0.22f, r * 0.03f, r * 0.012f)
            }
            val trunk = sprite(parts) { _, _ -> ZERO }
            val k = shapes.leaves
            val crown = if (k > 0.02f) {
                // One drawn crown: a broad dome, its outline gently uneven; it grows from the
                // middle in spring and shrinks back as the leaves fall.
                val size = 0.5f + 0.5f * Shapes.smoothstep(0f, 0.85f, k)
                val ccx = cx
                val ccy = cy + r * 0.12f
                val dome = listOf(
                    -1.0f to 0.30f, -0.97f to 0.0f, -0.86f to -0.32f, -0.62f to -0.6f, -0.3f to -0.8f, 0.06f to -0.88f,
                    0.42f to -0.78f, 0.72f to -0.55f, 0.93f to -0.22f, 1.0f to 0.1f, 0.9f to 0.36f, 0.62f to 0.48f,
                    0.28f to 0.42f, -0.06f to 0.5f, -0.42f to 0.44f, -0.74f to 0.47f,
                )
                val knots = dome.map { (u, v) -> Knot(ccx + u * r * 1.12f * size, ccy + v * r * size) }
                val ring = Shapes.line(knots + knots.first() + knots[1], step = 1f)
                sprite(listOf(Outline(ring.xs, ring.ys))) { x, y ->
                    // The underside a shade darker (red); blossom as dots (blue).
                    val shade = Shapes.smoothstep(-0.05f, 0.5f, (y - ccy) / (r * size))
                    val dot = if (shapes.blossom > 0f) blossomDot(x - cx, y - cy, r, shapes.blossom) else 0f
                    Triple(shade, 0f, dot)
                }
            } else {
                null
            }
            return TreeSprites(trunk, crown, px(cx), px(fork))
        }

        /** Blossom on the crown: small round dots on a fixed, irregular grid (no randomness). */
        private fun blossomDot(dx: Float, dy: Float, r: Float, amount: Float): Float {
            val cell = r * 0.22f
            val gx = ((dx + 4 * r) / cell).toInt()
            val gy = ((dy + 4 * r) / cell).toInt()
            val ox = ((gx * 7 + gy * 3) % 5) / 5f * 0.6f + 0.2f
            val oy = ((gx * 2 + gy * 9) % 5) / 5f * 0.6f + 0.2f
            val cx = (gx + ox) * cell - 4 * r
            val cy = (gy + oy) * cell - 4 * r
            val dist = kotlin.math.hypot(dx - cx, dy - cy)
            val size = cell * 0.22f * sqrt(amount)
            return 1f - Shapes.smoothstep(size - 0.5f / d, size + 0.5f / d, dist)
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
         * A cloud cut from paper: a long, low strip with a flat underside and a few wide, low
         * swells along its top – calm as a band of mist, never a puff. A second, shorter strip
         * lies over the first, a little higher.
         */
        fun cloud(i: Int): Sheet {
            val designs = listOf(
                floatArrayOf(300f, 16f, 3f, 0.0f), floatArrayOf(210f, 13f, 2f, 0.3f), floatArrayOf(380f, 18f, 4f, 0.6f),
                floatArrayOf(160f, 11f, 2f, 0.9f), floatArrayOf(260f, 15f, 3f, 1.2f), floatArrayOf(130f, 10f, 1f, 1.5f),
            )
            val (len, th, humps, phase) = designs[i]
            val parts = listOf(
                Shapes.cloudStrip(0f, 0f, len, th, humps.toInt(), phase),
                Shapes.cloudStrip(len * 0.22f, -th * 0.55f, len * 0.5f, th * 0.8f, max(1, humps.toInt() - 1), phase + 0.4f),
            )
            return sprite(parts) { _, y -> Triple(Shapes.smoothstep(-th * 0.2f, th * 0.15f, y), 0f, 0f) }
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
