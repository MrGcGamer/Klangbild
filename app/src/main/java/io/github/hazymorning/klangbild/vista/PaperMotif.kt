package io.github.hazymorning.klangbild.vista

/**
 * The one picture, as a paper cut: every contour placed by hand on the 960 × 540 dp screen
 * (and [OVERSCAN] beyond, so the slow drift never shows an edge). The view faces south down a
 * valley to a lake: the sun rises behind the high wooded ridge on the left, crosses the sky
 * and sets on the right, where the land lies lower – over the far shore.
 *
 * From the back: sky; two ranges of mountains; the far shore, its tree line cut fine; the
 * lake; two wooded ridges, a high one from the left and a lower one from the right, their
 * firs close as a fringe; a meadow in the middle with one broad tree; and the near bank, dark,
 * with a tall fir on the right and grasses and reeds – the calm ground the music's label rests on.
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

    /** The broad tree on the meadow: foot x, foot y (on the contour), height, crown radius. */
    val trees = listOf(RoundTree(708f, 420f, 104f, 40f))

    /** The near bank on the left: dark and calm, rising to the corner. */
    val frontLeft = listOf(
        Knot(L, 440f), Knot(60f, 446f), Knot(140f, 458f), Knot(214f, 474f), Knot(280f, 494f), Knot(330f, 514f),
        Knot(372f, 534f), Knot(400f, BOTTOM),
    )

    /** The near bank on the right, under the tall fir. */
    val frontRight = listOf(
        Knot(R, 470f), Knot(930f, 472f), Knot(872f, 480f), Knot(820f, 492f), Knot(776f, 508f), Knot(744f, 528f),
        Knot(728f, BOTTOM),
    )

    /** The tall fir on the right bank: x, height, tiers. */
    val bigFir = Triple(924f, 220f, 9)

    /** Grass along the near banks: clumps at x (dp) with their size; the reeds stand by the water. */
    val grassLeft = listOf(14f to 0.9f, 64f to 0.72f, 120f to 1.0f, 176f to 0.78f, 236f to 0.9f, 300f to 0.7f)
    val grassRight = listOf(784f to 0.8f, 846f to 0.95f, 892f to 0.7f)
    val reeds = listOf(Triple(352f, 524f, 1.0f))

    data class RoundTree(val x: Float, val footY: Float, val height: Float, val crown: Float)
}
