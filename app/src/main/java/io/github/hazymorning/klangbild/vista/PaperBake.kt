package io.github.hazymorning.klangbild.vista

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sheets of the diorama, back to front. Each stands at its own distance in front of what
 * lies behind it ([depth], dp – how far and how softly its shadow falls; 0: none), is a little
 * out of focus the farther it is from the clearing ([blur], dp), and catches the light on its cut
 * top edge ([rim]); [occludes]: it darkens what stands close behind it.
 */
enum class SheetId(val kind: PaperKind, val depth: Float, val blur: Float, val rim: Float, val occludes: Boolean = true) {
    SKY(PaperKind.WASH, 0f, 0f, 0f),
    RANGE_FAR(PaperKind.CARD, 0f, 1.0f, 0.1f),
    // The caps lie flat on their mountains: their shade is baked into the range (see occludes), no shadow spills on the sky.
    SNOW(PaperKind.WHITE, 0f, 1.0f, 0f),
    RANGE_NEAR(PaperKind.CARD, 1f, 0.7f, 0.1f),
    WOOD_FAR(PaperKind.CARD, 1f, 0.5f, 0.1f),
    WOOD_NEAR(PaperKind.CARD, 1.2f, 0.25f, 0.1f),
    LEAVES(PaperKind.CARD, 0f, 0.25f, 0f, occludes = false),
    MEADOW(PaperKind.CARD, 1.6f, 0f, 0.1f),
    STANDEES(PaperKind.CARD, 1.2f, 0f, 0.08f),
    FIREWOOD(PaperKind.CARD, 0.3f, 0f, 0.12f),
    MEADOW_NEAR(PaperKind.CARD, 0.8f, 0f, 0.1f),
    FRONT_LEFT(PaperKind.CARD, 2.6f, 0.6f, 0.1f),
    FRONT_RIGHT(PaperKind.CARD, 2.6f, 0.6f, 0.1f),
}

/**
 * A baked piece of the picture in its own box (pixels): for the sheets of the canvas (which
 * starts [PaperMotif.OVERSCAN] dp left of and above the screen), for sprites relative to their
 * anchor. Its pixels hold ink weights, the paper's light and coverage (see [Inks]); a sheet
 * baked smaller (soft shadows) is drawn [scale] times its size.
 */
class Sheet(val left: Int, val top: Int, val width: Int, val height: Int, val pixels: IntArray, val scale: Int = 1)

/** Snow, leaves and blossom as the season stands – what changes the shapes, not only the colours. */
data class SeasonShapes(
    /** Snow on the ground, 0 (none) to 1 (deep winter). */
    val snow: Float = 0f,
    /** How far snow reaches down the mountains, 0 (only the highest summit) to 1 (all of them). */
    val peaks: Float = 0f,
    /** Leaves on the broad-leaved trees, 0 (bare) to 1 (full crowns). */
    val leaves: Float = 1f,
    /** Blossom, 0 to 1 – it only colours the crowns ([SkyState.blossom]); the bake ignores it. */
    val blossom: Float = 0f,
)

/** One tongue of the campfire's flames: crumpled tissue, flickering about its foot (canvas pixels). */
class Flame(val sheet: Sheet, val footX: Float, val footY: Float, val seed: Int)

/** Everything the picture is drawn from, for one screen size, one state of the season and one label side. */
class PaperBaked(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val density: Float,
    val sheets: Map<SheetId, Sheet>,
    /** Soft shadows of the sheets that stand apart from the one behind (half resolution, coverage only). */
    val shadows: Map<SheetId, Sheet>,
    /** The tissue clouds (relative to their left end and baseline) and their shadows. */
    val clouds: List<Sheet>,
    val cloudShadows: List<Sheet>,
    /** The fire's flames, tallest first, and the fire's foot (canvas pixels). */
    val flames: List<Flame>,
    val fireX: Float,
    val fireY: Float,
    /** Paper discs for the sun and the moon (relative to their middle), and their shadow. */
    val sun: Sheet,
    val moon: Sheet,
    val discShadow: Sheet,
    /** A soft round light (white, coverage only) for glows; drawn scaled. */
    val glow: Sheet,
    val shapes: SeasonShapes,
    /** How strongly the broad-leaved crowns show: they thin out in autumn and come back in spring. */
    val leafAlpha: Float,
    /** The side the near bank is cut wide for, under the music's label. */
    val label: LabelSide,
    val bakeMs: Long,
) {
    /** Canvas pixels of a screen position in dp. */
    fun px(dp: Float): Float = (dp + PaperMotif.OVERSCAN) * density
}

/**
 * Bakes the diorama for a screen: every piece of paper cut once – with exact anti-aliasing and
 * the slight unevenness of a hand with scissors – laid on its sheet, given its paper's grain,
 * darkened where nearer sheets stand close in front of it, softened where it lies far from the
 * clearing. Colours are not baked – the light paints them every frame.
 */
object PaperBake {
    fun bake(widthPx: Int, heightPx: Int, density: Float, shapes: SeasonShapes = SeasonShapes(), label: LabelSide = LabelSide.LEFT): PaperBaked {
        val started = System.nanoTime()
        val m = PaperMotif
        val cw = ceil((m.WIDTH + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(widthPx)
        val ch = ceil((m.HEIGHT + 2 * m.OVERSCAN) * density).toInt().coerceAtLeast(heightPx)
        val b = Baker(cw, ch, density, shapes, m.foreground(label))
        val sheets = LinkedHashMap<SheetId, Sheet>()
        // From the front to the back: every sheet knows what stands in front of it.
        for (id in SheetId.entries.reversed()) {
            val build = b.build(id) ?: continue
            sheets[id] = b.finish(build)
        }
        val ordered = LinkedHashMap<SheetId, Sheet>()
        for (id in SheetId.entries) sheets[id]?.let { ordered[id] = it }
        val shadows = ordered.filterKeys { it.depth > 0f }.mapValues { (id, s) -> b.shadow(s, shadowBlurDp(id.depth)) }
        val clouds = (0 until CLOUD_DESIGNS).map { b.cloud(it) }
        return PaperBaked(
            cw, ch, density, ordered, shadows, clouds, clouds.map { b.shadow(it, shadowBlurDp(CLOUD_DEPTH)) },
            b.flames(), b.px(b.fg.fireX), b.px(b.fg.fireY), b.disc(SUN_RADIUS, 77), b.disc(MOON_RADIUS, 78),
            b.shadow(b.disc(SUN_RADIUS, 79), DISC_SHADOW_DP), b.glow(), shapes, leafAlpha(shapes.leaves), label,
            (System.nanoTime() - started) / 1_000_000,
        )
    }

    /** The leaves' strength for [leaves] (0 bare … 1 full): gone before the last ones fall, back with the first green. */
    fun leafAlpha(leaves: Float): Float = Shapes.smoothstep(0.08f, 0.6f, leaves)

    /** How soft a sheet's shadow is (dp) for its [depth]. */
    fun shadowBlurDp(depth: Float): Float = 1.4f + 2.8f * depth

    /** How many cloud designs there are; the sky draws them in an order that never repeats. */
    const val CLOUD_DESIGNS = 5

    /** The clouds hang this far (dp) in front of the sky. */
    const val CLOUD_DEPTH = 0.8f

    /** The sun's and the moon's discs (dp radius), and their shadow's softness. */
    const val SUN_RADIUS = 15f
    const val MOON_RADIUS = 14f
    private const val DISC_SHADOW_DP = 1.2f

    /** The paper's light is stored in the raster up to this factor while it is cut. */
    private const val TONE_MAX = 1.25f

    /** How far down (dp) each sheet is baked: below, nearer sheets hide it everywhere. */
    private const val SKY_BOTTOM = 312f
    private const val RANGE_BOTTOM = 344f
    private const val WOOD_FAR_BOTTOM = 402f
    private const val WOOD_NEAR_BOTTOM = 458f
    private const val MEADOW_BOTTOM = 500f

    /** A weight (or the paper's light) at a point of the screen (dp). */
    private fun interface W {
        fun at(x: Float, y: Float): Float
    }

    private fun c(v: Float) = W { _, _ -> v }
    private val ZERO = c(0f)

    /** A sheet while it is being cut: weights and light in a raster over its box (canvas pixels). */
    private class Build(val id: SheetId, val left: Int, val top: Int, val width: Int, val height: Int) {
        val raster = InkRaster(width, height, seed = id.ordinal + 1)
    }

    /** A coverage mask in a box of canvas pixels. */
    private class Mask(val left: Int, val top: Int, val width: Int, val height: Int, val v: FloatArray)

    private class Baker(val cw: Int, val ch: Int, val d: Float, val shapes: SeasonShapes, val fg: PaperMotif.Foreground) {
        private val o = PaperMotif.OVERSCAN
        private val grain = PaperGrain(d)
        /** What stands in front of the sheets baked so far (coverage on a grid of [AO_CELL] dp). */
        private val gw = ceil(cw / (AO_CELL * d)).toInt() + 1
        private val gh = ceil(ch / (AO_CELL * d)).toInt() + 1
        private val front = FloatArray(gw * gh)
        private val meadowLine = Shapes.line(PaperMotif.meadow)

        fun px(dp: Float) = (dp + o) * d
        fun dpX(px: Int) = (px + 0.5f) / d - o
        fun dpY(px: Int) = (px + 0.5f) / d - o

        // --- cutting ------------------------------------------------------------------------

        /** The union coverage of [outlines] (dp), in their box padded by [pad] pixels, clipped to the canvas. */
        fun mask(outlines: List<Outline>, pad: Int = 2): Mask? {
            var x0 = Float.MAX_VALUE
            var y0 = Float.MAX_VALUE
            var x1 = -Float.MAX_VALUE
            var y1 = -Float.MAX_VALUE
            for (e in outlines) {
                x0 = min(x0, e.minX()); y0 = min(y0, e.minY()); x1 = max(x1, e.maxX()); y1 = max(y1, e.maxY())
            }
            val left = max(0, floor(px(x0)).toInt() - pad)
            val top = max(0, floor(px(y0)).toInt() - pad)
            val right = min(cw, ceil(px(x1)).toInt() + pad)
            val bottom = min(ch, ceil(px(y1)).toInt() + pad)
            if (right <= left || bottom <= top) return null
            val w = right - left
            val h = bottom - top
            val out = FloatArray(w * h)
            for (e in outlines) {
                val c = Coverage(w, h)
                c.polygon(FloatArray(e.size) { px(e.xs[it]) - left }, FloatArray(e.size) { px(e.ys[it]) - top })
                val v = c.resolve()
                for (i in v.indices) if (v[i] > out[i]) out[i] = v[i]
            }
            return Mask(left, top, w, h, out)
        }

        /**
         * Lays a piece of paper ([m]) on [b]: its weights and its light take over where it covers,
         * and it darkens what lies under its edge by [aoCast] (spread over [aoSigma] dp) – a
         * piece glued over another.
         */
        fun lay(b: Build, piece: Mask?, w1: W, w2: W, tone: W = c(1f), aoCast: Float = 0f, aoSigma: Float = 3f) {
            piece ?: return
            val r = b.raster
            val m = if (aoCast > 0f) expand(piece, ceil(3f * aoSigma * d).toInt() + 2) else piece
            if (aoCast > 0f) {
                val blurred = m.v.copyOf()
                blur(blurred, m.width, m.height, max(1, (aoSigma * d - 0.5f).roundToInt()))
                for (y in 0 until m.height) {
                    val ry = m.top + y - b.top
                    if (ry < 0 || ry >= b.height) continue
                    for (x in 0 until m.width) {
                        val rx = m.left + x - b.left
                        if (rx < 0 || rx >= b.width) continue
                        val i = y * m.width + x
                        val shade = aoCast * blurred[i] * (1f - m.v[i])
                        if (shade <= 0.002f) continue
                        val p = r.pixels[ry * b.width + rx]
                        if (p ushr 24 == 0) continue
                        val t = ((p and 0xFF) * (1f - shade)).roundToInt()
                        r.pixels[ry * b.width + rx] = (p and 0xFFFFFF00.toInt()) or t
                    }
                }
            }
            for (y in 0 until m.height) {
                val ry = m.top + y - b.top
                if (ry < 0 || ry >= b.height) continue
                val yDp = dpY(m.top + y)
                for (x in 0 until m.width) {
                    val k = m.v[y * m.width + x]
                    if (k <= 0.002f) continue
                    val rx = m.left + x - b.left
                    if (rx < 0 || rx >= b.width) continue
                    val xDp = dpX(m.left + x)
                    r.over(rx, ry, w1.at(xDp, yDp), w2.at(xDp, yDp), (tone.at(xDp, yDp) / TONE_MAX).coerceIn(0f, 1f), k)
                }
            }
        }

        /** A box over the canvas from [x0] to [x1] and [y0] to [y1] (dp). */
        fun build(id: SheetId, x0: Float, y0: Float, x1: Float, y1: Float): Build {
            val left = max(0, floor(px(x0)).toInt() - 2)
            val top = max(0, floor(px(y0)).toInt() - 2)
            val right = min(cw, ceil(px(x1)).toInt() + 2)
            val bottom = min(ch, ceil(px(y1)).toInt() + 2)
            return Build(id, left, top, max(1, right - left), max(1, bottom - top))
        }

        fun build(id: SheetId): Build? = when (id) {
            SheetId.SKY -> sky()
            SheetId.RANGE_FAR -> range(id, PaperMotif.farRange, PaperMotif.FAR_FOOT, seed = 2)
            SheetId.SNOW -> snow()
            SheetId.RANGE_NEAR -> range(id, PaperMotif.nearRange, PaperMotif.NEAR_FOOT, seed = 3)
            SheetId.WOOD_FAR -> woodFar()
            SheetId.WOOD_NEAR -> woodNear(leaves = false)
            SheetId.LEAVES -> if (shapes.leaves > 0.05f) woodNear(leaves = true) else null
            SheetId.MEADOW -> meadow()
            SheetId.STANDEES -> standees()
            SheetId.FIREWOOD -> firewood()
            SheetId.MEADOW_NEAR -> meadowNear()
            SheetId.FRONT_LEFT -> front(left = true)
            SheetId.FRONT_RIGHT -> front(left = false)
        }

        // --- the sheets ---------------------------------------------------------------------

        /** The sky: a wash from the zenith's colour (base) through the middle one to the horizon's. */
        fun sky(): Build {
            val b = build(SheetId.SKY, -o, -o, PaperMotif.WIDTH + o, SKY_BOTTOM)
            for (y in 0 until b.height) {
                val t = (dpY(b.top + y) / SKY_SPAN).coerceIn(0f, 1f)
                val horizon = Shapes.smoothstep(0.38f, 1f, t)
                val middle = Shapes.smoothstep(0f, 0.58f, t) * (1f - horizon)
                for (x in 0 until b.width) b.raster.over(x, y, middle, horizon, 1f / TONE_MAX, 1f, dither = true)
            }
            return b
        }

        /** A range of folded mountains, back to front, each darkening the one behind under its edge. */
        fun range(id: SheetId, mountains: List<Mountain>, foot: Float, seed: Int): Build {
            val rnd = Rnd(seed)
            val b = build(id, -o, mountains.minOf { it.summitY } - 4f, PaperMotif.WIDTH + o, RANGE_BOTTOM)
            for ((i, mt) in mountains.withIndex()) {
                val outline = cut(mt.knots, seed * 31 + i, amp = 0.6f)
                lay(b, mask(listOf(outline)), faceRight(mt, foot), faceFront(mt, foot), c(1f + 0.03f * rnd.normal()), aoCast = 0.2f, aoSigma = 5f)
            }
            // Winter: torn caps of snow on the near range too (its third ink is the snow's).
            if (id == SheetId.RANGE_NEAR) {
                val reach = 34f * Shapes.smoothstep(0.6f, 1f, shapes.peaks) + 10f * shapes.snow
                if (reach > 2f) for ((i, mt) in mountains.withIndex()) {
                    val outline = cut(mt.knots, seed * 31 + i, amp = 0.6f)
                    val cap = capOutline(outline, mt, mt.summitY + reach, foot, seed * 57 + i, tongues = 4f)
                    val face = faceRight(mt, foot)
                    lay(b, intersect(mask(listOf(outline)), mask(listOf(cap))), W { x, y -> 0.25f * face.at(x, y) }, c(0.75f))
                }
            }
            return b
        }

        /** The far range's caps: torn white paper glued on, down to the season's snow line, tongues down the creases. */
        fun snow(): Build? {
            val line = 188f + 212f * shapes.peaks.pow(1.5f)
            val mountains = PaperMotif.farRange
            if (mountains.none { line - it.summitY > 3f }) return null
            val b = build(SheetId.SNOW, -o, mountains.minOf { it.summitY } - 4f, PaperMotif.WIDTH + o, min(RANGE_BOTTOM, line + 24f))
            for ((i, mt) in mountains.withIndex()) {
                if (line - mt.summitY < 3f) continue
                val outline = cut(mt.knots, 2 * 31 + i, amp = 0.6f)
                val cap = capOutline(outline, mt, line, PaperMotif.FAR_FOOT, 400 + i, tongues = 7f)
                // Cut a hair wider than its mountain, as a glued-on piece would be: the card's edge never shows under it.
                val wide = mask(listOf(outline), pad = (CAP_OVERHANG * 3f * d).toInt() + 4)?.let { margin(it, CAP_OVERHANG) }
                lay(b, intersect(wide, mask(listOf(cap))), faceRight(mt, PaperMotif.FAR_FOOT), faceFront(mt, PaperMotif.FAR_FOOT))
            }
            return b
        }

        /** The far wood: a gentle line of small firs, one silhouette, standing close. */
        fun woodFar(): Build {
            val rnd = Rnd(41)
            val ground = cut(PaperMotif.woodFar, 41, amp = 0.5f, closedTo = WOOD_FAR_BOTTOM)
            val line = Shapes.line(PaperMotif.woodFar)
            val b = build(SheetId.WOOD_FAR, -o, 294f, PaperMotif.WIDTH + o, WOOD_FAR_BOTTOM)
            lay(b, mask(listOf(ground)), ZERO, groundSnow(0.92f))
            val trees = ArrayList<Triple<Float, Float, Float>>()
            var x = -16f
            while (x < PaperMotif.WIDTH + 20f) {
                val s = sin(x * 0.013f)
                val h = (14f + 6f * s * s) * (0.66f + 0.5f * rnd.next())
                trees += Triple(line.yAt(x) + h * 0.08f + 2f * rnd.next() + 1f, x, h)
                x += 4.2f * (0.5f + 0.8f * rnd.next())
            }
            for ((foot, tx, h) in trees.sortedBy { it.first }) {
                val f = fir(tx, foot, h, rnd, width = 0.48f, jag = 0.7f)
                lay(b, mask(f.tiers + f.trunk), ZERO, ZERO, c(1f + 0.03f * rnd.normal()))
                capFir(b, f)
            }
            return b
        }

        /**
         * The near wood, dipping into the valley: firs close as one silhouette, taller towards
         * the sides, and here and there a broad-leaved tree – its trunk on the wood, its crown
         * on a sheet of its own ([leaves]), whose inks the season paints.
         */
        fun woodNear(leaves: Boolean): Build? {
            val rnd = Rnd(43)
            val line = Shapes.line(PaperMotif.woodNear)
            class Tree(val foot: Float, val x: Float, val h: Float, val broad: Boolean, val tone: Int)
            val trees = ArrayList<Tree>()
            var x = -20f
            while (x < PaperMotif.WIDTH + 30f) {
                val side = abs(x - 520f) / 520f
                val h = (22f + 30f * side.pow(1.2f)) * (0.66f + 0.5f * rnd.next())
                val broad = rnd.next() < BROAD_SHARE
                trees += Tree(line.yAt(x) + h * 0.08f + 2f * rnd.next() + 1f, x, h, broad, rnd.int(3))
                x += (7f + 6f * side) * (0.5f + 0.8f * rnd.next())
            }
            // Cut every tree (the same cuts for both sheets), then lay what belongs on this one.
            class Cut(val tree: Tree, val tone: Float, val fir: FirCut?, val round: RoundTree?)
            val cuts = trees.sortedBy { it.foot }.map { t ->
                val tone = 1f + 0.05f * rnd.normal()
                if (t.broad) Cut(t, tone, null, roundTree(t.x, t.foot, t.h * 0.85f, rnd)) else Cut(t, tone, fir(t.x, t.foot, t.h, rnd, width = 0.48f, jag = 0.7f), null)
            }
            if (leaves) {
                val crowns = cuts.mapNotNull { it.round }
                if (crowns.isEmpty()) return null
                val all = crowns.flatMap { it.discs }
                val b = build(SheetId.LEAVES, all.minOf { it.minX() } - 3f, all.minOf { it.minY() } - 3f, all.maxOf { it.maxX() } + 3f, all.maxOf { it.maxY() } + 3f)
                for (k in cuts) {
                    val tree = k.round ?: continue
                    // Three colours by the tree's own tone; the crown's lower part in shade.
                    lay(b, mask(tree.discs), c(if (k.tree.tone == 1) 1f else 0f), c(if (k.tree.tone == 2) 1f else 0f), crownShade(tree, k.tone))
                }
                return b
            }
            val ground = cut(PaperMotif.woodNear, 43, amp = 0.6f, closedTo = WOOD_NEAR_BOTTOM)
            val b = build(SheetId.WOOD_NEAR, -o, 268f, PaperMotif.WIDTH + o, WOOD_NEAR_BOTTOM)
            lay(b, mask(listOf(ground)), ZERO, groundSnow(0.92f))
            for (k in cuts) {
                val f = k.fir
                if (f != null) {
                    lay(b, mask(f.tiers + f.trunk), ZERO, ZERO, c(k.tone))
                    capFir(b, f)
                } else if (shapes.leaves >= 0.25f) {
                    // Without its leaves the broad tree is gone from the wood: a bare trunk would stand like a post.
                    lay(b, mask(listOf(k.round!!.trunk)), ZERO, ZERO, c(0.8f * k.tone))
                }
            }
            return b
        }

        /** The clearing's meadow; in winter white patches spread over it until it is all snow. */
        fun meadow(): Build {
            val ground = cut(PaperMotif.meadow, 51, amp = 0.9f, closedTo = MEADOW_BOTTOM)
            val b = build(SheetId.MEADOW, -o, 412f, PaperMotif.WIDTH + o, MEADOW_BOTTOM)
            lay(b, mask(listOf(ground)), ZERO, groundSnow(0.95f))
            return b
        }

        /** Firs standing on the meadow as cut-outs, a white margin round each, folded down the middle. */
        fun standees(): Build? {
            val rnd = Rnd(61)
            val firs = fg.standees.map { (x, h) -> fir(x, meadowLine.yAt(x) + 6f, h, rnd, width = 0.46f, snow = snowOnTrees()) }
            if (firs.isEmpty()) return null
            val all = firs.flatMap { it.tiers + it.trunk }
            val b = build(SheetId.STANDEES, all.minOf { it.minX() } - 4f, all.minOf { it.minY() } - 4f, all.maxOf { it.maxX() } + 4f, all.maxOf { it.maxY() } + 4f)
            for (f in firs) standee(b, f, margin = 1.3f, aoSigma = 2f)
            return b
        }

        /** The campfire's wood: a ring of stones, two logs crossed over the embers, their cut ends showing. */
        fun firewood(): Build {
            val rnd = Rnd(71)
            val fx = fg.fireX
            val fy = fg.fireY
            val b = build(SheetId.FIREWOOD, fx - 36f, fy - 16f, fx + 36f, fy + 18f)
            class Stone(val x: Float, val y: Float, val r: Float, val outline: Outline)
            val stones = (0 until 12).map { k ->
                val a = PI.toFloat() * k / 11f
                val sx = fx - 27f * cos(a)
                val sy = fy + 4f + 7f * sin(a)
                val r = 3.9f + 1.4f * rnd.next()
                val pts = (0 until 30).map { i ->
                    val t = 2 * PI.toFloat() * i / 30f
                    (sx + r * 1.3f * cos(t) * (1f + 0.1f * sin(3 * t + k))) to (sy + r * 0.85f * sin(t))
                }
                Stone(sx, sy, r, wobbled(Outline.of(pts), 700 + k, 0.25f, closed = true))
            }.sortedBy { it.y }
            fun stone(s: Stone) = lay(
                b, mask(listOf(s.outline)), c(1f), ZERO,
                W { x, y -> 1f + 0.16f * (1f - Shapes.smoothstep(s.y - s.r * 0.7f, s.y + s.r * 0.3f, y)) - 0.1f * Shapes.smoothstep(s.x - s.r, s.x + s.r * 1.2f, x) },
                aoCast = 0.3f, aoSigma = 1.2f,
            )
            stones.take(6).forEach { stone(it) }
            val logs = listOf(
                Outline.of(listOf(fx - 24f to fy + 6f, fx + 2f to fy - 11f, fx + 6.5f to fy - 7.5f, fx - 19.5f to fy + 10f)),
                Outline.of(listOf(fx + 24f to fy + 6f, fx - 2f to fy - 11f, fx - 6.5f to fy - 7.5f, fx + 19.5f to fy + 10f)),
            )
            for (log in logs) lay(b, mask(listOf(log)), ZERO, ZERO, aoCast = 0.3f, aoSigma = 1f)
            for (side in listOf(-1f, 1f)) lay(b, mask(listOf(Shapes.ellipse(fx + side * 22f, fy + 8.2f, 3f, 3.3f))), ZERO, c(1f))
            stones.drop(6).forEach { stone(it) }
            return b
        }

        /** The nearer strip of meadow, its edge a fringe of short grass. */
        fun meadowNear(): Build {
            val rnd = Rnd(81)
            val ground = cut(PaperMotif.meadowNear, 81, amp = 1f, closedTo = PaperMotif.BOTTOM)
            val line = Shapes.line(PaperMotif.meadowNear)
            val b = build(SheetId.MEADOW_NEAR, -o, 464f, PaperMotif.WIDTH + o, PaperMotif.BOTTOM)
            lay(b, mask(listOf(ground)), ZERO, groundSnow(0.95f))
            lay(b, mask(grass(line, -20f, PaperMotif.WIDTH + 30f, rnd, 2f, 6f)), ZERO, c(0.6f * shapes.snow), c(1.05f))
            return b
        }

        /**
         * A near bank with its fringe of grass and its tall firs. Snow lies only along its top:
         * the bank under the label stays dark in every season.
         */
        fun front(left: Boolean): Build? {
            val rnd = Rnd(if (left) 91 else 97)
            val knots = if (left) fg.frontLeft else fg.frontRight
            val line = Shapes.line(knots)
            val bank = cut(knots, if (left) 91 else 97, amp = 0.9f).let { e ->
                val cornerX = if (left) -o - 4f else PaperMotif.WIDTH + o + 4f
                Outline(e.xs + floatArrayOf(cornerX), e.ys + floatArrayOf(PaperMotif.BOTTOM))
            }
            val firs = fg.frontFirs.filter { (it.x < PaperMotif.WIDTH / 2f) == left }.map { fir(it.x, it.footY, it.height, rnd, width = 0.42f, snow = snowOnTrees()) }
            val parts = listOf(bank) + firs.flatMap { it.tiers + it.trunk }
            val b = build(if (left) SheetId.FRONT_LEFT else SheetId.FRONT_RIGHT, parts.minOf { it.minX() } - 4f, parts.minOf { it.minY() } - 4f, parts.maxOf { it.maxX() } + 4f, PaperMotif.BOTTOM)
            val band = if (shapes.snow > 0.05f) 3f + 7f * shapes.snow else 0f
            lay(b, mask(listOf(bank)), c(0.5f), W { x, y -> if (band <= 0f) 0f else 0.9f * (1f - Shapes.smoothstep(band - 0.6f, band + 0.6f, y - line.yAt(x))) })
            val from = if (left) -20f else knots.last().x - 6f
            val to = if (left) knots.last().x + 6f else PaperMotif.WIDTH + 30f
            lay(b, mask(grass(line, from, to, rnd, 3f, 10f)), c(0.55f), c(0.5f * shapes.snow), c(1.06f))
            for (f in firs) standee(b, f, margin = 2f, aoSigma = 3f)
            return b
        }

        // --- pieces -------------------------------------------------------------------------

        /** A fir as a stand-up cut-out: the white margin, the trunk, the tiers folded down the middle, snow on them. */
        fun standee(b: Build, f: FirCut, margin: Float, aoSigma: Float) {
            val pad = ((margin * 3f + aoSigma * 3f) * d).toInt() + 4
            val all = mask(f.tiers + f.trunk, pad) ?: return
            lay(b, margin(all, margin), ZERO, c(1f), aoCast = 0.22f, aoSigma = aoSigma)
            lay(b, mask(listOf(f.trunk), pad), c(0.35f), ZERO, c(0.55f))
            lay(b, mask(f.tiers, pad), foldOf(f), ZERO)
            capFir(b, f)
        }

        /** Snow on a fir's tiers, where the season has it. */
        fun capFir(b: Build, f: FirCut) {
            if (f.caps.isEmpty()) return
            lay(b, intersect(mask(f.tiers), mask(f.caps)), ZERO, c(0.95f))
        }

        /** The fir's fold: its right half takes the first ink, sharp but anti-aliased along the crease. */
        fun foldOf(f: FirCut) = W { x, y -> ((x - (f.axisX + f.lean * (y - f.top))) * d + 0.5f).coerceIn(0f, 1f) }

        /** The right face of a mountain (right of its last crease). */
        fun faceRight(mt: Mountain, foot: Float) = W { x, y ->
            val t = ((y - mt.summitY) / (foot - mt.summitY)).coerceIn(0f, 1f)
            val crease = mt.summitX + (mt.creases.last() - mt.summitX) * t
            ((x - crease) * d + 0.5f).coerceIn(0f, 1f)
        }

        /** The face between two creases, turned towards the viewer (none with one crease). */
        fun faceFront(mt: Mountain, foot: Float): W {
            if (mt.creases.size < 2) return ZERO
            return W { x, y ->
                val t = ((y - mt.summitY) / (foot - mt.summitY)).coerceIn(0f, 1f)
                val a = mt.summitX + (mt.creases[0] - mt.summitX) * t
                val e = mt.summitX + (mt.creases[1] - mt.summitX) * t
                (((x - a) * d + 0.5f).coerceIn(0f, 1f) - ((x - e) * d + 0.5f).coerceIn(0f, 1f)).coerceAtLeast(0f)
            }
        }

        /** The crown's light: its lower part in shade. */
        fun crownShade(t: RoundTree, tone: Float) = W { _, y -> tone * (1f - 0.18f * Shapes.smoothstep(t.cy - t.r * 0.2f, t.cy + t.r * 0.8f, y)) }

        /** Snow on the ground: patches that spread as the season deepens, cut sharp like pieces of paper; [full] at deep snow. */
        fun groundSnow(full: Float): W {
            val s = shapes.snow
            if (s <= 0.02f) return ZERO
            val threshold = 1f - 2f * s
            return W { x, y ->
                val n = (0.65 * Noise.at(x / 34.0 + 11.3, y / 17.0 - 4.1) + 0.35 * Noise.at(x / 11.0 - 7.7, y / 6.0 + 2.9)).toFloat()
                full * ((n - threshold) * 30f * d + 0.5f).coerceIn(0f, 1f)
            }
        }

        /** How much snow the firs carry. */
        fun snowOnTrees(): Float = Shapes.smoothstep(0.25f, 0.8f, shapes.snow)

        /** A white margin round [m]: its coverage spread by [marginDp] and cut again with a soft edge. */
        fun margin(m: Mask, marginDp: Float): Mask {
            val v = m.v.copyOf()
            blur(v, m.width, m.height, max(1, (marginDp * d * 0.55f).roundToInt()))
            for (i in v.indices) v[i] = Shapes.smoothstep(0.04f, 0.22f, v[i])
            return Mask(m.left, m.top, m.width, m.height, v)
        }

        /** [m] in a box grown by [pad] pixels on every side (within the canvas). */
        fun expand(m: Mask, pad: Int): Mask {
            val left = max(0, m.left - pad)
            val top = max(0, m.top - pad)
            val right = min(cw, m.left + m.width + pad)
            val bottom = min(ch, m.top + m.height + pad)
            val w = right - left
            val h = bottom - top
            val v = FloatArray(w * h)
            for (y in 0 until m.height) System.arraycopy(m.v, y * m.width, v, (m.top + y - top) * w + (m.left - left), m.width)
            return Mask(left, top, w, h, v)
        }

        /** Where both masks cover. */
        fun intersect(a: Mask?, b: Mask?): Mask? {
            a ?: return null
            b ?: return null
            val out = FloatArray(a.v.size)
            for (y in 0 until a.height) for (x in 0 until a.width) {
                val bx = a.left + x - b.left
                val by = a.top + y - b.top
                if (bx < 0 || by < 0 || bx >= b.width || by >= b.height) continue
                out[y * a.width + x] = a.v[y * a.width + x] * b.v[by * b.width + bx]
            }
            return Mask(a.left, a.top, a.width, a.height, out)
        }

        /**
         * The lower edge of a snow cap: torn along [line] (dp), with a tongue down each crease,
         * closed over the mountain's top.
         */
        fun capOutline(outline: Outline, mt: Mountain, line: Float, foot: Float, seed: Int, tongues: Float): Outline {
            val x0 = outline.minX() - 2f
            val x1 = outline.maxX() + 2f
            val n = ((x1 - x0) / 0.6f).toInt() + 1
            val xs = FloatArray(n + 2)
            val ys = FloatArray(n + 2)
            val tear = noise1(n, seed, floatArrayOf(5f / 0.6f, 14f / 0.6f, 40f / 0.6f), floatArrayOf(0.45f, 0.8f, 1f))
            for (i in 0 until n) {
                val x = x0 + i * 0.6f
                var tongue = 0f
                for (cb in mt.creases) {
                    val cx = mt.summitX + (cb - mt.summitX) * ((line - mt.summitY) / (foot - mt.summitY)).coerceIn(0f, 1f)
                    // A wide, shallow V where the fold holds the snow – never a drip.
                    val u = (x - cx) / 11f
                    tongue += tongues * exp(-u * u)
                }
                xs[i] = x
                ys[i] = line + tongue + 3f * tear[i]
            }
            xs[n] = x1; ys[n] = -o - 20f
            xs[n + 1] = x0; ys[n + 1] = -o - 20f
            return Outline(xs, ys)
        }

        /** A contour cut by hand: the drawn line, a little uneven; closed down to [closedTo] (dp), or as drawn. */
        fun cut(knots: List<Knot>, seed: Int, amp: Float, closedTo: Float? = null): Outline {
            val line = Shapes.line(knots, step = 1f)
            val wobbly = wobbled(Outline(line.xs, line.ys), seed, amp, closed = false)
            if (closedTo == null) return wobbly
            val n = wobbly.size
            return Outline(wobbly.xs + floatArrayOf(wobbly.xs[n - 1], wobbly.xs[0]), wobbly.ys + floatArrayOf(closedTo, closedTo))
        }

        /** Grass blades along [line] from [from] to [to] (dp): narrow, leaning, uneven in height. */
        fun grass(line: Polyline, from: Float, to: Float, rnd: Rnd, low: Float, high: Float): List<Outline> {
            val out = ArrayList<Outline>()
            var x = from
            while (x < to) {
                val foot = line.yAt(x) + 2.5f
                val h = low + (high - low) * rnd.next().pow(1.8f)
                val lean = rnd.normal() * 2.2f
                val w = 1.1f + 0.8f * rnd.next()
                out += Outline.of(listOf(x - w to foot + 2f, x + lean * 0.5f to foot - h * 0.6f, x + lean to foot - h, x + lean * 0.4f + 0.3f to foot - h * 0.55f, x + w to foot + 2f))
                x += 1.2f + 2.2f * rnd.next()
            }
            return out
        }

        // --- finishing ----------------------------------------------------------------------

        /**
         * A cut sheet made ready to draw: its paper's grain, the shade of what stands close in
         * front of it, the light on its cut top edge – all in the paper's light – and its focus.
         * Then what it covers joins what stands in front of the sheets behind it.
         */
        fun finish(b: Build): Sheet {
            val id = b.id
            val r = b.raster
            val g = grain.field(id.ordinal * 101 + 7, id.kind, b.left, b.top, b.width, b.height)
            val ao = aoFor(id)
            val rimPx = max(1, d.roundToInt())
            val out = r.pixels
            val alpha = IntArray(out.size) { out[it] ushr 24 }
            for (y in 0 until b.height) {
                for (x in 0 until b.width) {
                    val i = y * b.width + x
                    val a = alpha[i]
                    if (a == 0) continue
                    val p = out[i]
                    val tone = (p and 0xFF) / 255f * TONE_MAX
                    val above = if (y >= rimPx) alpha[i - rimPx * b.width] else 0
                    val rim = 1f + id.rim * ((a - above) / 255f).coerceIn(0f, 1f)
                    val cx = b.left + x
                    val cy = b.top + y
                    val t = (tone * g.at(cx, cy) * ao.at(cx, cy) * rim / Inks.T_SCALE).coerceIn(0f, 1f)
                    val w1 = (p shr 16 and 0xFF) / 255f
                    val w2 = (p shr 8 and 0xFF) / 255f
                    out[i] = (a shl 24) or ((w1 * t * 255f).roundToInt() shl 16) or ((w2 * t * 255f).roundToInt() shl 8) or (t * 255f).roundToInt()
                }
            }
            if (id.blur * d >= 0.75f) focus(out, b.width, b.height, id.blur * d)
            if (id.occludes) addFront(out, b)
            return Sheet(b.left, b.top, b.width, b.height, out)
        }

        /** Softens a sheet (premultiplied, so its edge blurs into nothing, not into black). */
        private fun focus(p: IntArray, w: Int, h: Int, sigmaPx: Float) {
            val a = FloatArray(p.size)
            val rr = FloatArray(p.size)
            val gg = FloatArray(p.size)
            val bb = FloatArray(p.size)
            for (i in p.indices) {
                val v = p[i]
                val k = (v ushr 24) / 255f
                a[i] = k
                rr[i] = (v shr 16 and 0xFF) * k
                gg[i] = (v shr 8 and 0xFF) * k
                bb[i] = (v and 0xFF) * k
            }
            val radius = max(1, (sigmaPx - 0.5f).roundToInt())
            for (plane in arrayOf(a, rr, gg, bb)) blur(plane, w, h, radius)
            for (i in p.indices) {
                val k = a[i]
                if (k < 1f / 512f) {
                    p[i] = 0
                    continue
                }
                fun ch(v: Float) = (v / k).roundToInt().coerceIn(0, 255)
                p[i] = ((k * 255f).roundToInt().coerceIn(0, 255) shl 24) or (ch(rr[i]) shl 16) or (ch(gg[i]) shl 8) or ch(bb[i])
            }
        }

        /** The shade of what stands in front of [id]: close by and over a hand's width; the sky only faintly. */
        private fun aoFor(id: SheetId): AoField {
            if (front.all { it == 0f }) return AoField(null, null, 0f, 0f)
            return if (id == SheetId.SKY) {
                AoField(blurred(front, 9), null, 0.08f, 0f)
            } else {
                AoField(blurred(front, 1), blurred(front, 5), 0.18f, 0.2f)
            }
        }

        private fun blurred(src: FloatArray, r: Int): FloatArray = src.copyOf().also { blur(it, gw, gh, r) }

        private inner class AoField(val near: FloatArray?, val wide: FloatArray?, val kNear: Float, val kWide: Float) {
            fun at(x: Int, y: Int): Float {
                if (near == null) return 1f
                val gx = x / (AO_CELL * d) - 0.5f
                val gy = y / (AO_CELL * d) - 0.5f
                var v = 1f - kNear * sample(near, gx, gy)
                if (wide != null) v *= 1f - kWide * sample(wide, gx, gy)
                return v
            }

            private fun sample(g: FloatArray, gx: Float, gy: Float): Float {
                val i = gx.toInt().coerceIn(0, gw - 2)
                val j = gy.toInt().coerceIn(0, gh - 2)
                val fx = (gx - i).coerceIn(0f, 1f)
                val fy = (gy - j).coerceIn(0f, 1f)
                val a = g[j * gw + i]
                val b = g[j * gw + i + 1]
                val c = g[(j + 1) * gw + i]
                val e = g[(j + 1) * gw + i + 1]
                return a + (b - a) * fx + (c - a + (e - c - b + a) * fx) * fy
            }
        }

        /** Adds what [p] covers to the grid of what stands in front. */
        private fun addFront(p: IntArray, b: Build) {
            val cell = AO_CELL * d
            val sum = FloatArray(gw * gh)
            val count = FloatArray(gw * gh)
            for (y in 0 until b.height) {
                val gy = ((b.top + y) / cell).toInt().coerceIn(0, gh - 1)
                for (x in 0 until b.width) {
                    val gx = ((b.left + x) / cell).toInt().coerceIn(0, gw - 1)
                    sum[gy * gw + gx] += (p[y * b.width + x] ushr 24) / 255f
                    count[gy * gw + gx] += 1f
                }
            }
            val area = cell * cell
            for (i in front.indices) if (count[i] > 0f) front[i] = max(front[i], (sum[i] / area).coerceAtMost(1f))
        }

        /** A soft shadow of a sheet: its coverage at half resolution, blurred by [blurDp]. */
        fun shadow(s: Sheet, blurDp: Float): Sheet {
            val f = 2
            val radius = max(1, (blurDp * d / f - 0.5f).roundToInt())
            val pad = radius * 3 + 2
            val w = s.width / f + 2 * pad
            val h = s.height / f + 2 * pad
            val v = FloatArray(w * h)
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val a = (s.pixels[y * s.width + x] ushr 24) / 255f
                if (a > 0f) v[(y / f + pad) * w + x / f + pad] += a / (f * f)
            }
            blur(v, w, h, radius)
            val px = IntArray(w * h) { i -> ((v[i] * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF }
            return Sheet(s.left - pad * f, s.top - pad * f, w, h, px, scale = f)
        }

        // --- sprites ------------------------------------------------------------------------

        /** A sprite of [m] relative to an anchor at canvas pixel ([ax], [ay]), finished like a sheet. */
        private fun sprite(m: Mask, ax: Float, ay: Float, kind: PaperKind, seed: Int, blurDp: Float, weights: (xDp: Float, yDp: Float, i: Int) -> FloatArray): Sheet {
            val g = grain.field(seed, kind, m.left, m.top, m.width, m.height)
            val p = IntArray(m.v.size)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                val i = y * m.width + x
                val k = m.v[i]
                if (k <= 0.002f) continue
                val w = weights(dpX(m.left + x), dpY(m.top + y), i)
                val t = (w[2] * g.at(m.left + x, m.top + y) / Inks.T_SCALE).coerceIn(0f, 1f)
                val a = (k * w[3]).coerceIn(0f, 1f)
                p[i] = ((a * 255f).roundToInt() shl 24) or ((w[0] * t * 255f).roundToInt() shl 16) or ((w[1] * t * 255f).roundToInt() shl 8) or (t * 255f).roundToInt()
            }
            if (blurDp * d >= 0.75f) focus(p, m.width, m.height, blurDp * d)
            return Sheet(m.left - ax.roundToInt(), m.top - ay.roundToInt(), m.width, m.height, p)
        }

        /**
         * A cloud of torn tissue: one or two long strips laid over each other, their edges
         * torn, their lower part a little in shade. Relative to its left end and baseline.
         */
        fun cloud(i: Int): Sheet {
            val pieces = CLOUDS[i]
            val outlines = pieces.mapIndexed { k, p -> tissue(p[0], p[1], p[2], p[3], 900 + i * 7 + k) }
            // Laid out around a point well inside the canvas, then made relative to it.
            val ox = 200f
            val oy = 200f
            val shifted = outlines.map { it.transformed(1f, ox, oy) }
            val m = mask(shifted, pad = (2f * d).toInt() + 3)!!
            val baseline = pieces[0][1]
            val th = pieces[0][3]
            return sprite(m, px(ox), px(oy), PaperKind.WHITE, 950 + i, blurDp = 0.6f) { _, y, _ ->
                val local = y - oy - baseline
                floatArrayOf(0.7f * Shapes.smoothstep(-th * 0.2f, th * 0.4f, local), 0f, 1f, CLOUD_ALPHA)
            }
        }

        /** A strip of tissue torn from a sheet: [length] long, [thickness] high, its left end at [x], its baseline at [y]. */
        private fun tissue(x: Float, y: Float, length: Float, thickness: Float, seed: Int): Outline {
            val n = (length * 2f).toInt()
            val swell = noise1(n, seed, floatArrayOf(30f * 2f, 80f * 2f), floatArrayOf(0.6f, 1f))
            val rag = noise1(n, seed + 1, floatArrayOf(3f * 2f, 9f * 2f), floatArrayOf(0.5f, 1f))
            val top = ArrayList<Pair<Float, Float>>()
            val bottom = ArrayList<Pair<Float, Float>>()
            for (i in 0 until n) {
                val u = i / (n - 1f) * 2f - 1f
                val env = sqrt((1f - u * u).coerceAtLeast(0f)).pow(0.8f)
                val px = x + (u + 1f) / 2f * length
                top += px to y - thickness * env * (0.8f + 0.25f * swell[i])
                bottom += px to y + thickness * 0.35f * env + 1.2f * rag[i]
            }
            return wobbled(Outline.of(top + bottom.reversed()), seed + 2, 0.9f, closed = true, scales = floatArrayOf(2.5f, 6f, 15f), amps = floatArrayOf(0.6f, 0.8f, 0.5f))
        }

        /**
         * The campfire's flames: tongues of crumpled tissue, yellow at the heart, orange, red at
         * the tips, a little translucent at their torn edges – each flickering on its own.
         */
        fun flames(): List<Flame> {
            val fx = fg.fireX
            val fy = fg.fireY
            val base = fy - 2f
            return TONGUES.sortedByDescending { it[1] }.mapIndexed { k, (ox0, hh0, hw0, lean) ->
                val ox = ox0 * FLAME_SCALE
                val hh = hh0 * FLAME_SCALE
                val hw = hw0 * FLAME_SCALE
                val n = 70
                val right = ArrayList<Pair<Float, Float>>()
                val left = ArrayList<Pair<Float, Float>>()
                // Torn edges, as ragged as the tongue is wide: the tip itself stays a clean point.
                val tornRight = noise1(n + 1, 300 + k, floatArrayOf(3f, 7f, 17f), floatArrayOf(0.6f, 0.8f, 0.5f))
                val tornLeft = noise1(n + 1, 310 + k, floatArrayOf(3f, 7f, 17f), floatArrayOf(0.6f, 0.8f, 0.5f))
                for (i in 0..n) {
                    val v = i / n.toFloat()
                    val wv = hw * sin(PI.toFloat() * min(1f, v * 1.15f + 0.05f)).coerceAtLeast(0f).pow(0.85f) * (1f - v).pow(0.35f)
                    val xc = fx + ox + lean * hh * v * v + 1.5f * sin(v * 5f + ox)
                    val rag = 0.6f * min(1f, wv / 2.5f)
                    right += (xc + wv + rag * tornRight[i]) to (base - v * hh)
                    left += (xc - wv - rag * tornLeft[i]) to (base - v * hh)
                }
                val outline = Outline.of(right + left.reversed())
                val m = mask(listOf(outline), pad = (2f * d).toInt() + 3)!!
                val soft = m.v.copyOf().also { blur(it, m.width, m.height, max(1, (0.9f * d - 0.5f).roundToInt())) }
                val footX = px(fx + ox)
                val footY = px(base)
                val sheet = sprite(m, footX, footY, PaperKind.WHITE, 330 + k, blurDp = 0f) { x, y, i ->
                    val u = ((base - y) / hh).coerceIn(0f, 1f)
                    val w1: Float
                    val w2: Float
                    if (u < 0.45f) {
                        w1 = u / 0.45f; w2 = 0f
                    } else {
                        val f = (u - 0.45f) / 0.55f
                        w1 = 1f - f; w2 = f
                    }
                    val crumple = 0.86f + 0.22f * (1f - abs(Noise.at(x / 3.2 + k * 13.1, y / 3.2 - k * 7.7).toFloat()))
                    // Tissue: a little translucent, more so at the torn edge.
                    val edge = soft[i]
                    floatArrayOf(w1, w2, crumple * (1f + 0.3f * (1f - u)), 0.82f + 0.16f * Shapes.smoothstep(0.5f, 0.95f, edge))
                }
                Flame(sheet, footX, footY, k)
            }
        }

        /** A disc of white paper, [radius] dp, cut a little unevenly; relative to its middle. */
        fun disc(radius: Float, seed: Int): Sheet {
            val ox = 100f
            val oy = 100f
            val circle = wobbled(Shapes.circle(ox, oy, radius, segments = 96), seed, 0.25f, closed = true)
            val m = mask(listOf(circle), pad = 3)!!
            return sprite(m, px(ox), px(oy), PaperKind.WHITE, seed, blurDp = 0f) { _, _, _ -> floatArrayOf(0f, 0f, 1f, 1f) }
        }

        /** A soft round light, 1 in the middle to 0 at the rim (drawn scaled). */
        fun glow(): Sheet {
            val side = 256
            val px = IntArray(side * side)
            val rnd = Rnd(77)
            for (y in 0 until side) for (x in 0 until side) {
                val u = (x + 0.5f) / side * 2 - 1
                val v = (y + 0.5f) / side * 2 - 1
                val r2 = u * u + v * v
                val a = exp(-r2 * 3.2f) * (1f - Shapes.smoothstep(0.7f, 1f, sqrt(r2)))
                px[y * side + x] = ((a * 255f + rnd.next() - 0.5f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
            }
            return Sheet(0, 0, side, side, px)
        }

        // --- trees --------------------------------------------------------------------------

        /** A broad-leaved tree: a trunk and a crown of four overlapping paper discs. */
        fun roundTree(cx: Float, footY: Float, h: Float, rnd: Rnd): RoundTree {
            val r = h * 0.3f
            val cy = footY - h + r * 1.1f
            val discs = (0 until 4).map { k ->
                val a = -PI.toFloat() / 2 + (k - 1.5f) * 0.9f
                val dx = (if (k == 0) 0f else cos(a) * r * 0.45f) + rnd.normal() * r * 0.05f
                val dy = (if (k == 0) 0f else sin(a) * r * 0.3f + r * 0.25f) + rnd.normal() * r * 0.05f
                val rr = r * (0.75f + 0.2f * rnd.next())
                wobbled(Shapes.ellipse(cx + dx, cy + dy, rr, rr * 0.92f, segments = 40), (cx * 13 + k).toInt(), 0.5f, closed = true)
            }
            val tw = max(0.8f, h * 0.05f)
            val trunk = Outline.of(listOf(cx - tw to cy, cx + tw to cy, cx + tw * 1.2f to footY + 2f, cx - tw * 1.2f to footY + 2f))
            return RoundTree(discs, trunk, cx, cy, r)
        }

        /**
         * A fir cut by hand: stacked tiers with drooping sides and bough tips along their lower
         * edge, the halves not quite equal, a slight lean; snow along the tiers' upper edges.
         */
        fun fir(cx: Float, footY: Float, h: Float, rnd: Rnd, width: Float = 0.46f, jag: Float = 1f, droop: Float = 1.5f, snow: Float = 0f): FirCut {
            var tiers = when {
                h < 14f -> 3
                h < 26f -> 4
                h < 50f -> 5
                h < 110f -> 6
                else -> 7
            }
            if (rnd.next() < 0.3f) tiers++
            val trunkH = h * 0.07f
            val top = footY - h
            val crownH = h - trunkH
            val lean = rnd.normal() * 0.035f
            val asym = 1f + 0.12f * rnd.normal()
            val outlines = ArrayList<Outline>()
            val caps = ArrayList<Outline>()
            for (i in 0 until tiers) {
                val t0 = (i / tiers.toFloat()).pow(1.1f) * 0.9f
                val t1 = min(1f, (i + 1.35f) / tiers)
                val y0 = top + crownH * t0
                val y1 = top + crownH * t1
                val hw = width * h * 0.5f * (0.2f + 0.8f * t1) * (1f + 0.08f * rnd.normal())
                val ax = cx + lean * (y0 - top)
                val hl = hw * asym
                val hr = hw / asym
                fun side(sign: Float, half: Float, t: Float) = (ax + sign * half * t) to (y0 + (y1 - y0) * t.pow(droop) + 0.3f * rnd.normal() * h / 80f)
                val right = (0 until 6).map { side(1f, hr, 0.12f + 0.88f * it / 5f) }
                val left = (0 until 6).map { side(-1f, hl, 1f - 0.88f * it / 5f) }
                val apex = (ax + 0.3f * rnd.normal()) to (y0 - if (i == 0) 1f else 0f)
                val pts = ArrayList<Pair<Float, Float>>()
                pts += apex
                pts += right
                val n = 2 + ((hl + hr) / 14f).toInt()
                for (k in n downTo 0) {
                    val x = ax - hl + (hl + hr) * k / n
                    if (k in 1 until n) {
                        val yb = y1 - (y1 - y0) * 0.18f * jag * (0.5f + 0.9f * rnd.next())
                        pts += (x + (hl + hr) / n * (0.35f + 0.3f * rnd.next())) to yb
                    }
                    pts += x to (y1 + if (k in 1 until n) 0.6f * rnd.normal() * h / 60f else 0f)
                }
                pts += left
                outlines += Outline.of(pts)
                if (snow > 0f) {
                    val depth = (y1 - y0) * (0.22f + 0.25f * snow)
                    caps += Outline.of(listOf(apex) + right + right.reversed().map { (x, y) -> x to y + depth * (0.7f + 0.3f * sin(x * 0.9f)) })
                    val down = left.reversed()
                    caps += Outline.of(listOf(apex) + down + down.reversed().map { (x, y) -> x to y + depth * (0.7f + 0.3f * sin(x * 0.7f)) })
                }
            }
            val tw = max(0.7f, h * 0.042f)
            val trunk = Outline.of(listOf(cx - tw to footY - trunkH - 3f, cx + tw to footY - trunkH - 3f, cx + tw * 1.2f to footY + 2f, cx - tw * 1.2f to footY + 2f))
            return FirCut(outlines, caps, trunk, cx, top, lean)
        }

        // --- helpers ------------------------------------------------------------------------

        /**
         * [e] cut by an uneven hand: every point pushed along the line's normal by a smooth
         * wander of its length along the line ([scales] dp), at most [amp] dp.
         */
        fun wobbled(e: Outline, seed: Int, amp: Float, closed: Boolean, scales: FloatArray = floatArrayOf(7f, 19f, 53f), amps: FloatArray = floatArrayOf(0.35f, 0.6f, 1f)): Outline {
            val n = e.size
            if (n < 3 || amp <= 0f) return e
            val s = FloatArray(n)
            for (i in 1 until n) s[i] = s[i - 1] + kotlin.math.hypot(e.xs[i] - e.xs[i - 1], e.ys[i] - e.ys[i - 1])
            val off = FloatArray(n)
            var peak = 1e-6f
            for (i in 0 until n) {
                var v = 0f
                for (k in scales.indices) v += amps[k] * Noise.at((s[i] / scales[k] + seed * 7.31f + k * 31.7f).toDouble()).toFloat()
                off[i] = v
                peak = max(peak, abs(v))
            }
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            for (i in 0 until n) {
                val a = if (i > 0) i - 1 else if (closed) n - 1 else 0
                val b = if (i < n - 1) i + 1 else if (closed) 0 else n - 1
                val tx = e.xs[b] - e.xs[a]
                val ty = e.ys[b] - e.ys[a]
                val l = kotlin.math.hypot(tx, ty).coerceAtLeast(1e-6f)
                val k = off[i] / peak * amp
                xs[i] = e.xs[i] - ty / l * k
                ys[i] = e.ys[i] + tx / l * k
            }
            return Outline(xs, ys)
        }

        /** Smooth 1-D noise over [n] samples, features [scales] samples long. */
        fun noise1(n: Int, seed: Int, scales: FloatArray, amps: FloatArray): FloatArray {
            val out = FloatArray(n)
            var peak = 1e-6f
            for (i in 0 until n) {
                var v = 0f
                for (k in scales.indices) v += amps[k] * Noise.at((i / scales[k] + seed * 3.17f + k * 19.3f).toDouble()).toFloat()
                out[i] = v
                peak = max(peak, abs(v))
            }
            for (i in 0 until n) out[i] /= peak
            return out
        }
    }

    /** A fir as cut: its tiers, the snow on them, its trunk, and its fold (the axis from its top, leaning). */
    private class FirCut(val tiers: List<Outline>, val caps: List<Outline>, val trunk: Outline, val axisX: Float, val top: Float, val lean: Float)

    /** A broad-leaved tree: crown discs, trunk, and the crown's middle and radius (dp). */
    private class RoundTree(val discs: List<Outline>, val trunk: Outline, val cx: Float, val cy: Float, val r: Float)

    /** How far (dp) a snow cap reaches beyond its mountain's edge. */
    private const val CAP_OVERHANG = 1.4f

    /** The grid (dp) on which what stands in front is noted, for the shade it casts. */
    private const val AO_CELL = 2f

    /** The sky's wash reaches the horizon's colour this far down (dp). */
    private const val SKY_SPAN = 345f

    /** How many of the near wood's trees are broad-leaved. */
    private const val BROAD_SHARE = 0.1f

    /** The tissue clouds: their strips (left end, baseline, length, thickness – dp). */
    private val CLOUDS = listOf(
        listOf(floatArrayOf(0f, 0f, 230f, 15f), floatArrayOf(50f, -12f, 120f, 11f)),
        listOf(floatArrayOf(0f, 0f, 180f, 12f)),
        listOf(floatArrayOf(0f, 0f, 300f, 17f), floatArrayOf(110f, -11f, 140f, 12f)),
        listOf(floatArrayOf(0f, 0f, 140f, 10f)),
        listOf(floatArrayOf(0f, 0f, 240f, 14f), floatArrayOf(60f, -9f, 100f, 9f)),
    )

    /** Tissue lets a little of the sky through. */
    private const val CLOUD_ALPHA = 0.93f

    /** The flames' tongues: offset from the fire's middle, height, half width, lean (dp, before [FLAME_SCALE]). */
    private val TONGUES = listOf(
        floatArrayOf(-9f, 26f, 8.5f, -0.25f), floatArrayOf(8.5f, 30f, 8f, 0.22f), floatArrayOf(0f, 42f, 10.5f, 0.05f),
        floatArrayOf(-3f, 22f, 6.5f, -0.1f), floatArrayOf(4f, 18f, 6f, 0.15f),
    )
    private const val FLAME_SCALE = 1.25f

    /** A box blur (three passes) over a plane of [w] × [h], edges clamped. */
    private fun blur(p: FloatArray, w: Int, h: Int, r: Int) {
        if (r <= 0 || w <= 0 || h <= 0) return
        val tmp = FloatArray(max(w, h))
        val norm = 1f / (2 * r + 1)
        repeat(3) {
            for (y in 0 until h) {
                val row = y * w
                var acc = 0f
                for (k in -r..r) acc += p[row + k.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[x] = acc * norm
                    acc += p[row + (x + r + 1).coerceAtMost(w - 1)] - p[row + (x - r).coerceAtLeast(0)]
                }
                System.arraycopy(tmp, 0, p, row, w)
            }
            for (x in 0 until w) {
                var acc = 0f
                for (k in -r..r) acc += p[k.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    tmp[y] = acc * norm
                    acc += p[(y + r + 1).coerceAtMost(h - 1) * w + x] - p[(y - r).coerceAtLeast(0) * w + x]
                }
                for (y in 0 until h) p[y * w + x] = tmp[y]
            }
        }
    }
}
