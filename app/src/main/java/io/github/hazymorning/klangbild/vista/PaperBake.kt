package io.github.hazymorning.klangbild.vista

import kotlin.math.ceil

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
        var standees: List<Standee> = emptyList()
        val frontStandees = ArrayList<Standee>()
        // From the front to the back: every sheet knows what stands in front of it.
        for (id in SheetId.entries.reversed()) {
            when (id) {
                SheetId.CROWNS -> continue
                SheetId.STANDEES -> standees = b.standees()
                SheetId.FRONT_LEFT, SheetId.FRONT_RIGHT -> {
                    frontStandees += b.frontFirs(left = id == SheetId.FRONT_LEFT)
                    b.cutSheet(id)?.let { sheets[id] = b.finish(it) }
                }
                else -> {
                    val build = b.cutSheet(id) ?: continue
                    sheets[id] = b.finish(build)
                }
            }
        }
        val ordered = LinkedHashMap<SheetId, Sheet>()
        for (id in SheetId.entries) sheets[id]?.let { ordered[id] = it }
        val shadows = ordered.filterKeys { it.depth > 0f }.mapValues { (id, s) -> b.shadow(s, shadowBlurDp(id.depth)) }
        val clouds = (0 until CLOUD_DESIGNS).map { b.cloud(it) }
        return PaperBaked(
            cw, ch, density, ordered, shadows, clouds, clouds.map { b.shadow(it, shadowBlurDp(CLOUD_DEPTH)) },
            b.flames(), b.px(b.fg.fireX), b.px(b.fg.fireY), standees, frontStandees, b.px(b.fg.tentX), b.px(b.tentFoot - TENT_HEIGHT * 0.24f),
            b.disc(SUN_RADIUS, 77), b.disc(MOON_RADIUS, 78),
            b.shadow(b.disc(SUN_RADIUS, 79), DISC_SHADOW_DP), b.glow(), shapes, leafAlpha(shapes.leaves), label,
            (System.nanoTime() - started) / 1_000_000,
        )
    }

    /** Cuts the sheet [id] (null where the season leaves it empty, or where its pieces are sprites). */
    private fun Baker.cutSheet(id: SheetId): Build? = when (id) {
        SheetId.SKY -> sky()
        SheetId.RANGE_FAR -> range(id, PaperMotif.farRange, PaperMotif.FAR_FOOT, seed = 2)
        SheetId.SNOW -> snow()
        SheetId.RANGE_NEAR -> range(id, PaperMotif.nearRange, PaperMotif.NEAR_FOOT, seed = 3)
        SheetId.WOOD_FAR -> woodFar()
        SheetId.WOOD_NEAR -> woodNear(leaves = false)
        SheetId.LEAVES -> if (shapes.leaves > 0.05f) woodNear(leaves = true) else null
        SheetId.MEADOW -> meadow()
        SheetId.BLOOMS -> blooms(near = false)
        SheetId.STANDEES, SheetId.CROWNS -> null
        SheetId.TENT -> tent()
        SheetId.PROPS -> props()
        SheetId.FIREWOOD -> firewood()
        SheetId.MEADOW_NEAR -> meadowNear()
        SheetId.BLOOMS_NEAR -> blooms(near = true)
        SheetId.FRONT_LEFT -> front(left = true)
        SheetId.FRONT_RIGHT -> front(left = false)
    }

    /** The leaves' strength for [leaves] (0 bare … 1 full): gone before the last ones fall, back with the first green. */
    fun leafAlpha(leaves: Float): Float = Shapes.smoothstep(0.08f, 0.6f, leaves)

    /** How soft a sheet's shadow is (dp) for its [depth]. */
    fun shadowBlurDp(depth: Float): Float = 1.4f + 2.8f * depth

    /** How many cloud designs there are – long strips, then heaps; the sky draws them in an order that never repeats. */
    const val CLOUD_STRIPS = 5
    const val CLOUD_HEAPS = 3
    const val CLOUD_DESIGNS = CLOUD_STRIPS + CLOUD_HEAPS

    /** The clouds hang this far (dp) in front of the sky. */
    const val CLOUD_DEPTH = 0.8f

    /** The sun's and the moon's discs (dp radius), and their shadow's softness. */
    const val SUN_RADIUS = 15f
    const val MOON_RADIUS = 14f
    private const val DISC_SHADOW_DP = 1.2f

    /**
     * How far down (dp) each sheet is baked: below, nearer sheets hide it everywhere – even when
     * the camera's layers have drifted apart by a few dp.
     */
    internal const val SKY_BOTTOM = 318f
    internal const val RANGE_BOTTOM = 350f
    internal const val WOOD_FAR_BOTTOM = 402f
    internal const val WOOD_NEAR_BOTTOM = 458f
    internal const val MEADOW_BOTTOM = 522f

    /** The tent: half its width and its height (dp). */
    const val TENT_HALF = 30f
    const val TENT_HEIGHT = 41f
}
