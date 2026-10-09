package io.github.hazymorning.klangbild.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sign

/**
 * Where the cards of the cover flow stand: the current cover faces the room in the middle,
 * the ones played before stand turned to the left of it, the ones to come to the right –
 * turned 60° towards the middle, their outer edges nearer, set back in depth, every card in
 * one shared perspective. Each card stands on a glossy floor and is mirrored in it.
 *
 * A card's place is a single number, its position: 0 in the middle, −1 and −2 to the left,
 * 1 and 2 to the right, anything in between while the strip moves. Everything here is plain
 * arithmetic on that number – the UI asks for a card's four corners every frame and draws one
 * textured quad; nothing is laid out, measured or allocated.
 *
 * Measured from the design (at 960 × 540 dp): the middle cover 300 dp, the outer edges of
 * the side cards 222.5 and 255 dp beside its centre, standing at 92 % of its height.
 */
class FlowGeometry(val layout: HaloLayout) {
    /** Side of a card (dp). */
    val side: Float get() = layout.art

    /** Centre of the middle card: the perspective's vanishing point (dp). */
    val cx: Float get() = layout.artRect.centerX
    val cy: Float get() = layout.artRect.centerY

    private val s get() = side / BASE_SIDE

    /** The camera's distance from the screen plane (dp). */
    private val camera get() = CAMERA * s

    /** Centre of the first side card and the step to the next (dp, in the card's own plane). */
    private val firstX get() = FIRST_X * s
    private val stepX get() = STEP_X * s

    /** Depth of the side cards' centres behind the screen plane (dp). */
    private val sideZ get() = SIDE_Z * s

    /** The mirror image starts this far below a card and is this tall (dp). */
    val gap: Float get() = GAP * s
    val reflection: Float get() = REFLECTION * s

    /** Height of a card with its mirror image (dp). */
    val height: Float get() = side + gap + reflection

    /**
     * The four corners (dp, clockwise from the top-left of the card, the bottom ones at the
     * end of its reflection) of the card at [position], written into [out] as x0, y0 … x3, y3.
     */
    fun corners(position: Float, out: FloatArray) {
        val half = side / 2f
        cornersOf(position, -half, -half, half, half + gap + reflection, out)
    }

    /**
     * The corners of the rectangle ([left], [top]) – ([right], [bottom]) in the plane of the card
     * at [position] (dp from the card's centre, y downwards), as for [corners].
     */
    fun cornersOf(position: Float, left: Float, top: Float, right: Float, bottom: Float, out: FloatArray) {
        val t = abs(position)
        val dir = sign(position)
        val near = min(t, 1f)
        // Leaving the middle the card swings aside early and quickly – turning and stepping
        // out together – then glides on to its place. Two cards crossing the middle (one
        // leaving, one arriving) are already clear of each other when they pass, so they never
        // cut through one another and the order in which they are drawn may simply flip.
        val turn = 1f - (1f - near) * (1f - near)
        val angle = SIDE_ANGLE * turn
        val xc = dir * (firstX * turn + stepX * (t - 1f).coerceAtLeast(0f))
        val zc = sideZ * turn
        val c = cos(angle)
        val sn = sin(angle)
        project(left, top, xc, zc, c, sn, dir, out, 0)
        project(right, top, xc, zc, c, sn, dir, out, 2)
        project(right, bottom, xc, zc, c, sn, dir, out, 4)
        project(left, bottom, xc, zc, c, sn, dir, out, 6)
    }

    /** A point (x along the card, y down from its centre) of a card at ([xc], [zc]) turned by acos([c]). */
    private fun project(x: Float, y: Float, xc: Float, zc: Float, c: Float, sn: Float, dir: Float, out: FloatArray, i: Int) {
        val wx = xc + x * c
        // The outer edge comes forward: to the right of the middle that is the card's right edge.
        val wz = zc - dir * x * sn
        val k = camera / (camera + wz)
        out[i] = cx + wx * k
        out[i + 1] = cy + y * k
    }

    /**
     * How brightly a card at [position] is lit: the middle one fully, the side cards a little
     * less with distance – the light falls on the middle.
     */
    fun light(position: Float): Float {
        val t = abs(position)
        return 1f - SIDE_DIM * min(t, 1f) - FAR_DIM * (t - 1f).coerceIn(0f, 1f)
    }

    /** How present a card at [position] is: the third card on each side is already gone. */
    fun presence(position: Float): Float = 1f - HaloLight.smoothstep(MAX_SIDE, EDGE, abs(position))

    companion object {

        /** The design's card size, to which the other measures belong (dp). */
        const val BASE_SIDE = 300f

        /** How far the side cards are turned (radians, 60°): more would shimmer, less would crowd. */
        val SIDE_ANGLE = Math.toRadians(60.0).toFloat()

        const val CAMERA = 1000f
        const val FIRST_X = 166f
        const val STEP_X = 35.3f
        const val SIDE_Z = 213.3f
        const val GAP = 4f
        const val REFLECTION = 90f

        /** Cards shown on each side. */
        const val MAX_SIDE = 2f

        /** Beyond this position a card is out of sight: cards that arrive come in from here. */
        const val EDGE = MAX_SIDE + 0.75f

        /** Darkening of the first side card, and a little more for the next. */
        const val SIDE_DIM = 0.3f
        const val FAR_DIM = 0.12f
    }
}
