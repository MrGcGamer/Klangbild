package io.github.hazymorning.klangbild.vista

/** Where the music's label lies in the picture: the near bank on that side is cut wide and calm for it. */
enum class LabelSide { LEFT, RIGHT, NONE }

/**
 * The one picture, as a paper cut: every contour placed by hand on the 960 × 540 dp screen
 * (and [OVERSCAN] beyond, so the slow drift never shows an edge). The view faces south down a
 * valley to a lake: the sun rises behind the high wooded ridge on the left, crosses the sky
 * and sets on the right, where the land lies lower – over the far shore.
 *
 * From the back: sky; two ranges of mountains; the far shore, its tree line cut fine; the
 * lake; two wooded ridges, a high one from the left and a lower one from the right, their
 * firs close as a fringe; a meadow in the middle with one broad tree; and the near banks, dark,
 * with a tall fir on the right and grasses and reeds. The bank under the music's label is cut
 * wide and nearly level ([Foreground]), so the label lies on calm dark paper in every light.
 */
object PaperMotif {
    const val WIDTH = 960f
    const val HEIGHT = 540f
    const val OVERSCAN = 18f

    /** Left and right end of every contour: beyond the overscan on both sides. */
    private const val L = -OVERSCAN - 2f
    private const val R = WIDTH + OVERSCAN + 2f

    /** Below the canvas: where outlines close. */
    const val BOTTOM = HEIGHT + OVERSCAN + 4f

    /** The lake's far shore: where sky, mountains and water meet (dp from the top). */
    const val HORIZON = 352f

    /** The farther range: four summits, the highest left of the middle. */
    val rangeFar = listOf(
        Knot(L, 286f), Knot(40f, 278f), Knot(96f, 262f), Knot(140f, 246f, corner = true), Knot(176f, 256f),
        Knot(214f, 250f), Knot(258f, 232f, corner = true), Knot(296f, 244f), Knot(330f, 224f), Knot(364f, 196f, corner = true),
        Knot(398f, 216f), Knot(430f, 226f), Knot(470f, 212f, corner = true), Knot(512f, 232f), Knot(560f, 246f),
        Knot(610f, 240f, corner = true), Knot(656f, 254f), Knot(720f, 262f), Knot(790f, 256f, corner = true),
        Knot(840f, 266f), Knot(900f, 274f), Knot(R, 280f),
    )

    /** The nearer range: lower and rounder, in another rhythm. */
    val rangeNear = listOf(
        Knot(L, 302f), Knot(60f, 294f), Knot(130f, 286f), Knot(190f, 294f), Knot(250f, 284f), Knot(310f, 272f, corner = true),
        Knot(356f, 284f), Knot(410f, 294f), Knot(470f, 288f), Knot(530f, 298f), Knot(600f, 292f), Knot(670f, 302f),
        Knot(740f, 298f), Knot(810f, 306f), Knot(880f, 302f), Knot(R, 308f),
    )

    /** The far shore's rolling hills, lowest where the sun goes down; a tree line along all of it. */
    val shore = listOf(
        Knot(L, 334f), Knot(60f, 326f), Knot(140f, 322f), Knot(226f, 330f), Knot(310f, 334f), Knot(392f, 328f),
        Knot(470f, 334f), Knot(552f, 339f), Knot(630f, 334f), Knot(710f, 340f), Knot(792f, 343f), Knot(870f, 339f),
        Knot(R, 343f),
    )

    /** The high wooded ridge from the left, down to the water … */
    val woodLeft = listOf(
        Knot(L, 246f), Knot(50f, 250f), Knot(118f, 262f), Knot(188f, 280f), Knot(256f, 302f), Knot(318f, 322f),
        Knot(376f, 338f), Knot(428f, 348f), Knot(478f, 354f, corner = true),
    )

    /** … where it reaches the water and runs along it towards the viewer. */
    val woodLeftShore = listOf(
        Knot(478f, 354f, corner = true), Knot(456f, 362f), Knot(420f, 374f), Knot(380f, 392f), Knot(344f, 420f),
        Knot(318f, 460f), Knot(300f, BOTTOM),
    )

    /** The lower wooded ridge from the right, down to the water … */
    val woodRight = listOf(
        Knot(R, 300f), Knot(924f, 304f), Knot(866f, 314f), Knot(808f, 326f), Knot(752f, 340f), Knot(700f, 350f),
        Knot(652f, 356f, corner = true),
    )

    /** … and its shore. */
    val woodRightShore = listOf(
        Knot(652f, 356f, corner = true), Knot(676f, 366f), Knot(712f, 380f), Knot(760f, 400f), Knot(820f, 430f),
        Knot(880f, 470f), Knot(920f, BOTTOM),
    )

    /** The meadow in the middle, in front of the lake, rising gently to the right. */
    val meadow = listOf(
        Knot(L, 520f), Knot(260f, 498f), Knot(380f, 470f), Knot(470f, 448f), Knot(560f, 432f), Knot(650f, 422f),
        Knot(740f, 418f), Knot(830f, 420f), Knot(R, 428f),
    )

    /**
     * The near banks, the broad tree, the grasses and the reeds for a label on [side]: the bank
     * under it reaches far in and stays nearly level, the other one falls away low.
     */
    fun foreground(side: LabelSide): Foreground = when (side) {
        LabelSide.LEFT -> Foreground(
            frontLeft = frontLeftWide, frontRight = frontRightLow,
            tree = BroadTree(708f, 112f, 66f),
            grassLeft = listOf(18f to 0.84f, 150f to 0.7f, 286f to 0.9f, 404f to 0.66f),
            grassRight = listOf(784f to 0.8f, 856f to 0.95f),
            reeds = listOf(Triple(452f, 470f, 0.9f)),
        )
        LabelSide.RIGHT -> Foreground(
            frontLeft = frontLeftLow, frontRight = frontRightWide,
            tree = BroadTree(480f, 104f, 62f),
            grassLeft = listOf(14f to 0.9f, 120f to 1.0f, 236f to 0.9f),
            grassRight = listOf(540f to 0.7f, 680f to 0.86f, 820f to 0.72f),
            reeds = listOf(Triple(352f, 524f, 1.0f)),
        )
        LabelSide.NONE -> Foreground(
            frontLeft = frontLeftLow, frontRight = frontRightLow,
            tree = BroadTree(708f, 112f, 66f),
            grassLeft = listOf(14f to 0.9f, 64f to 0.72f, 120f to 1.0f, 176f to 0.78f, 236f to 0.9f, 300f to 0.7f),
            grassRight = listOf(784f to 0.8f, 856f to 0.95f),
            reeds = listOf(Triple(352f, 524f, 1.0f)),
        )
    }

    /** The near bank on the left, low: rising to the corner. */
    private val frontLeftLow = listOf(
        Knot(L, 440f), Knot(60f, 446f), Knot(140f, 458f), Knot(214f, 474f), Knot(280f, 494f), Knot(330f, 514f),
        Knot(372f, 534f), Knot(400f, BOTTOM),
    )

    /** The near bank on the left, wide: a long, low swell, nearly level under the label, falling to the water late. */
    private val frontLeftWide = listOf(
        Knot(L, 428f), Knot(70f, 425f), Knot(170f, 425f), Knot(270f, 428f), Knot(350f, 432f), Knot(400f, 439f),
        Knot(436f, 452f), Knot(460f, 476f), Knot(474f, 506f), Knot(480f, BOTTOM),
    )

    /** The near bank on the right, low, under the tall fir. */
    private val frontRightLow = listOf(
        Knot(R, 470f), Knot(930f, 472f), Knot(872f, 480f), Knot(820f, 492f), Knot(776f, 508f), Knot(744f, 528f),
        Knot(728f, BOTTOM),
    )

    /** The near bank on the right, wide: level under the label, the tall fir at its end. */
    private val frontRightWide = listOf(
        Knot(R, 430f), Knot(890f, 427f), Knot(800f, 428f), Knot(700f, 430f), Knot(620f, 433f), Knot(566f, 438f),
        Knot(530f, 450f), Knot(506f, 472f), Knot(492f, 502f), Knot(486f, BOTTOM),
    )

    /** The tall fir on the right bank: x, height, tiers. */
    val bigFir = Triple(936f, 224f, 9)

    /**
     * Where the label lies (dp): its left edge on either side, its bottom, and the widest it
     * may be – the wide bank's top keeps clear of it by at least [LABEL_CLEARANCE].
     */
    const val LABEL_LEFT_X = 64f
    const val LABEL_RIGHT_X = 560f
    const val LABEL_BOTTOM = 508f
    const val LABEL_HEIGHT = 56f
    const val LABEL_MAX_WIDTH = 336f
    const val LABEL_CLEARANCE = 12f

    /** The near banks and what stands on them. */
    class Foreground(
        val frontLeft: List<Knot>,
        val frontRight: List<Knot>,
        val tree: BroadTree,
        /** Grass clumps along the banks' tops: x (dp) and size. */
        val grassLeft: List<Pair<Float, Float>>,
        val grassRight: List<Pair<Float, Float>>,
        /** Reeds by the water: x, foot y (dp) and size. */
        val reeds: List<Triple<Float, Float, Float>>,
    )

    /** The broad tree on the meadow: foot x, its height and its crown's width (dp); it stands on the meadow's contour. */
    data class BroadTree(val x: Float, val height: Float, val width: Float)
}
