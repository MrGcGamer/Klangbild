package io.github.hazymorning.klangbild.vista

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A point of a drawn contour (dp); a [corner] is cut sharp, every other point is passed smoothly. */
data class Knot(val x: Float, val y: Float, val corner: Boolean = false)

/** An outline as two coordinate arrays (dp), closed from the last point back to the first. */
class Outline(val xs: FloatArray, val ys: FloatArray) {
    val size: Int get() = xs.size

    fun minX() = xs.min()
    fun maxX() = xs.max()
    fun minY() = ys.min()
    fun maxY() = ys.max()

    /** Moved by ([dx], [dy]) and scaled by [s] about the origin. */
    fun transformed(s: Float, dx: Float = 0f, dy: Float = 0f) =
        Outline(FloatArray(size) { xs[it] * s + dx }, FloatArray(size) { ys[it] * s + dy })

    companion object {
        fun of(points: List<Pair<Float, Float>>) = Outline(FloatArray(points.size) { points[it].first }, FloatArray(points.size) { points[it].second })
    }
}

/** An open line through knots, sampled densely: x and y arrays (dp). */
class Polyline(val xs: FloatArray, val ys: FloatArray) {
    val size: Int get() = xs.size

    /** The line's height at [x] (dp) – for lines running left to right or right to left; beyond its ends, the end's height. */
    fun yAt(x: Float): Float {
        val ascending = xs[0] <= xs[size - 1]
        val first = if (ascending) 0 else size - 1
        val last = if (ascending) size - 1 else 0
        if (x <= xs[first]) return ys[first]
        if (x >= xs[last]) return ys[last]
        // Binary search in the line's own direction: lo and hi bracket x.
        var lo = 0
        var hi = size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if ((xs[mid] <= x) == ascending) lo = mid else hi = mid
        }
        val dx = xs[hi] - xs[lo]
        val t = if (dx == 0f) 0f else (x - xs[lo]) / dx
        return ys[lo] + (ys[hi] - ys[lo]) * t.coerceIn(0f, 1f)
    }

    /** Closed down to [bottom]: the shape of a layer of paper under this line. */
    fun closedDown(bottom: Float): Outline {
        val n = size
        val xs2 = FloatArray(n + 2)
        val ys2 = FloatArray(n + 2)
        for (i in 0 until n) {
            xs2[i] = xs[i]; ys2[i] = ys[i]
        }
        xs2[n] = xs[n - 1]; ys2[n] = bottom
        xs2[n + 1] = xs[0]; ys2[n + 1] = bottom
        return Outline(xs2, ys2)
    }
}

/**
 * The shapes the picture is cut from. Everything is drawn from deliberate control points –
 * no randomness, no noise: like a paper cut, every curve is placed by hand.
 */
object Shapes {

    /**
     * A smooth line through [knots] (centripetal Catmull-Rom), cut sharp at corners, sampled
     * about every [step] dp.
     */
    fun line(knots: List<Knot>, step: Float = 1.5f): Polyline {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (i in 0 until knots.size - 1) {
            val p1 = knots[i]
            val p2 = knots[i + 1]
            // At a corner (or an end) the curve leaves straight towards its neighbour.
            val p0 = if (i == 0 || p1.corner) Knot(2 * p1.x - p2.x, 2 * p1.y - p2.y) else knots[i - 1]
            val p3 = if (i + 2 >= knots.size || p2.corner) Knot(2 * p2.x - p1.x, 2 * p2.y - p1.y) else knots[i + 2]
            val len = hypot(p2.x - p1.x, p2.y - p1.y)
            val n = max(2, (len / step).toInt())
            for (k in 0 until n) {
                val (x, y) = catmullRom(p0, p1, p2, p3, k / n.toFloat())
                xs += x
                ys += y
            }
        }
        xs += knots.last().x
        ys += knots.last().y
        return Polyline(xs.toFloatArray(), ys.toFloatArray())
    }

    private fun catmullRom(p0: Knot, p1: Knot, p2: Knot, p3: Knot, t: Float): Pair<Float, Float> {
        fun knot(ti: Float, a: Knot, b: Knot): Float = ti + sqrt(hypot(b.x - a.x, b.y - a.y)).coerceAtLeast(1e-3f)
        val t0 = 0f
        val t1 = knot(t0, p0, p1)
        val t2 = knot(t1, p1, p2)
        val t3 = knot(t2, p2, p3)
        val tt = t1 + (t2 - t1) * t
        fun lerp(a: Float, b: Float, ta: Float, tb: Float) = if (tb - ta == 0f) a else a * (tb - tt) / (tb - ta) + b * (tt - ta) / (tb - ta)
        val a1x = lerp(p0.x, p1.x, t0, t1); val a1y = lerp(p0.y, p1.y, t0, t1)
        val a2x = lerp(p1.x, p2.x, t1, t2); val a2y = lerp(p1.y, p2.y, t1, t2)
        val a3x = lerp(p2.x, p3.x, t2, t3); val a3y = lerp(p2.y, p3.y, t2, t3)
        val b1x = lerp(a1x, a2x, t0, t2); val b1y = lerp(a1y, a2y, t0, t2)
        val b2x = lerp(a2x, a3x, t1, t3); val b2y = lerp(a2y, a3y, t1, t3)
        return lerp(b1x, b2x, t1, t2) to lerp(b1y, b2y, t1, t2)
    }

    /**
     * A closed smooth outline through [knots] (centripetal Catmull-Rom all the way round, no
     * corners), sampled about every [step] dp.
     */
    fun loop(knots: List<Knot>, step: Float = 1f): Outline {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val n = knots.size
        for (i in 0 until n) {
            val p0 = knots[(i - 1 + n) % n]
            val p1 = knots[i]
            val p2 = knots[(i + 1) % n]
            val p3 = knots[(i + 2) % n]
            val len = hypot(p2.x - p1.x, p2.y - p1.y)
            val m = max(2, (len / step).toInt())
            for (k in 0 until m) {
                val (x, y) = catmullRom(p0, p1, p2, p3, k / m.toFloat())
                xs += x
                ys += y
            }
        }
        return Outline(xs.toFloatArray(), ys.toFloatArray())
    }

    /** A circle (dp). */
    fun circle(cx: Float, cy: Float, r: Float, segments: Int = max(24, (r * 1.6f).toInt())): Outline =
        ellipse(cx, cy, r, r, segments)

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, segments: Int = max(24, (max(rx, ry) * 1.6f).toInt())): Outline {
        val xs = FloatArray(segments)
        val ys = FloatArray(segments)
        for (i in 0 until segments) {
            val a = 2 * PI * i / segments
            xs[i] = cx + (rx * cos(a)).toFloat()
            ys[i] = cy + (ry * sin(a)).toFloat()
        }
        return Outline(xs, ys)
    }

    /**
     * A fir as it is cut from paper: a narrow spire of [tiers] stepped tiers, the lowest the
     * widest, on a short trunk. [lean] tips the spire a little to one side (dp at the top).
     */
    fun fir(cx: Float, baseY: Float, height: Float, tiers: Int = 5, width: Float = height * 0.36f, lean: Float = 0f): Outline {
        val trunk = height * 0.07f
        val crownBottom = baseY - trunk
        val crownH = height - trunk
        val right = ArrayList<Pair<Float, Float>>()
        val top = baseY - height
        right += (cx + lean) to top
        for (k in 1..tiers) {
            val f = k / tiers.toFloat()
            // Each tier flares out to its tip, then steps back in to start the next one.
            val y = top + crownH * f
            val half = width / 2f * (0.28f + 0.72f * f)
            val leanHere = lean * (1f - f)
            right += (cx + leanHere + half) to y
            if (k < tiers) right += (cx + leanHere + half * 0.46f) to (y - crownH / tiers * 0.18f)
        }
        right += (cx + trunk * 0.45f) to crownBottom
        right += (cx + trunk * 0.45f) to baseY + 2f
        val left = right.reversed().map { (x, y) -> (2 * cx - x + 2 * leanAt(lean, y, top, crownH)) to y }
        // The mirrored list ends at the tip again: dropped, the outline closes there by itself.
        return Outline.of(right + left.dropLast(1))
    }

    private fun leanAt(lean: Float, y: Float, top: Float, crownH: Float): Float = lean * (1f - ((y - top) / crownH).coerceIn(0f, 1f))

    /**
     * A tapered stroke along the quadratic curve [x0],[y0] → ([cx],[cy]) → [x1],[y1], [w0] wide
     * at its start and [w1] at its end (dp): a grass blade, a twig, a stem.
     */
    fun stroke(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w0: Float, w1: Float, samples: Int = 18): Outline {
        val lx = FloatArray(samples + 1)
        val ly = FloatArray(samples + 1)
        val rx = FloatArray(samples + 1)
        val ry = FloatArray(samples + 1)
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val u = 1 - t
            val x = u * u * x0 + 2 * u * t * cx + t * t * x1
            val y = u * u * y0 + 2 * u * t * cy + t * t * y1
            var tx = 2 * u * (cx - x0) + 2 * t * (x1 - cx)
            var ty = 2 * u * (cy - y0) + 2 * t * (y1 - cy)
            val l = hypot(tx, ty).coerceAtLeast(1e-4f)
            tx /= l; ty /= l
            val w = (w0 + (w1 - w0) * t) / 2f
            lx[i] = x - ty * w; ly[i] = y + tx * w
            rx[i] = x + ty * w; ry[i] = y - tx * w
        }
        val xs = FloatArray(2 * (samples + 1))
        val ys = FloatArray(2 * (samples + 1))
        for (i in 0..samples) {
            xs[i] = lx[i]; ys[i] = ly[i]
            xs[2 * samples + 1 - i] = rx[i]; ys[2 * samples + 1 - i] = ry[i]
        }
        return Outline(xs, ys)
    }

    /**
     * A cloud as a strip of paper: long and low, its top a gentle arch, its bottom nearly
     * straight, both ends rounded. [length] and [thickness] in dp, [peak] where along it the
     * arch is highest (0..1).
     */
    fun sliver(x: Float, y: Float, length: Float, thickness: Float, peak: Float = 0.42f, segments: Int = 96): Outline {
        val pts = ArrayList<Pair<Float, Float>>()
        val r = thickness * 0.5f
        // Top edge from left to right: an arch that tapers into the rounded ends.
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val px = x + length * t
            val rise = if (t < peak) sin((t / peak) * PI / 2).toFloat() else cos(((t - peak) / (1 - peak)) * PI / 2).toFloat()
            val ends = min(1f, min(t, 1 - t) * length / r / 2f)
            val h = thickness * (0.35f + 0.65f * rise) * sqrt(ends.coerceIn(0f, 1f))
            pts += px to (y - h)
        }
        // Bottom edge back from right to left: nearly flat, rounding up into the ends.
        for (i in segments downTo 0) {
            val t = i / segments.toFloat()
            val px = x + length * t
            val ends = min(1f, min(t, 1 - t) * length / r / 2f)
            pts += px to (y + thickness * 0.08f * sqrt(ends.coerceIn(0f, 1f)))
        }
        return Outline.of(pts)
    }

    /** A band with round ends, [length] long and [thickness] high, its left end at [x] and its middle at [y]. */
    fun band(x: Float, y: Float, length: Float, thickness: Float): Outline {
        val r = thickness / 2f
        val pts = ArrayList<Pair<Float, Float>>()
        val n = max(12, (r * 3).toInt())
        for (i in 0..n) {
            val a = PI / 2 + PI * i / n
            pts += (x + r + (r * cos(a)).toFloat()) to (y - (r * sin(a)).toFloat())
        }
        for (i in 0..n) {
            val a = -PI / 2 + PI * i / n
            pts += (x + length - r + (r * cos(a)).toFloat()) to (y - (r * sin(a)).toFloat())
        }
        return Outline.of(pts)
    }

    /**
     * A cloud cut as a band of paper: [length] long, a nearly flat underside at [y], its top
     * rising to about [thickness] in [swells] long, low swells; both ends taper out slowly to a
     * point, like a willow leaf – a band of mist, never a puff. [phase] shifts the swells.
     */
    fun cloudStrip(x: Float, y: Float, length: Float, thickness: Float, swells: Int, phase: Float, segments: Int = 160): Outline {
        val pts = ArrayList<Pair<Float, Float>>()
        fun envelope(t: Float): Float {
            // Long tapers at both ends (the left one a little shorter: the wind comes from there).
            val left = smoothstep(0f, 0.2f, t)
            val right = 1f - smoothstep(0.74f, 1f, t)
            return sqrt(left * right)
        }
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val swell = 0.84f + 0.16f * sin((t * swells + phase * 0.31f) * PI.toFloat()).let { it * it }
            pts += (x + length * t) to (y - thickness * envelope(t) * swell)
        }
        for (i in segments downTo 0) {
            val t = i / segments.toFloat()
            pts += (x + length * t) to (y + thickness * 0.07f * envelope(t))
        }
        return Outline.of(pts)
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
