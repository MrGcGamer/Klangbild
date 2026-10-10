package io.github.hazymorning.klangbild.vista

/**
 * Where the camera stands at one moment: how far it has moved in ([zoom], for the nearest
 * layer) and to the side ([panX], [panY], dp, for the nearest layer). Each layer of the
 * diorama takes its share by its depth ([Depth.parallax]): the near banks move most, the sky
 * hardly at all – so the window has depth even when nothing in it moves.
 */
class CameraPose(val zoom: Float, val panX: Float, val panY: Float) {
    fun scale(d: Depth): Float = 1f + (zoom - 1f) * d.parallax
    fun dx(d: Depth): Float = panX * d.parallax
    fun dy(d: Depth): Float = panY * d.parallax
}

/**
 * The camera's slow drift over the diorama: a very slow move in and out and a little to the
 * sides, on waves that never repeat (minutes long, each a sum of incommensurate noises). It
 * never shows an edge: the nearest layer is always a little enlarged, and every layer's paper
 * reaches [PaperMotif.OVERSCAN] beyond the screen.
 */
object VistaCamera {
    /** The nearest layer's enlargement, from … to … */
    const val ZOOM_MIN = 1.025f
    const val ZOOM_MAX = 1.065f

    /** How far the nearest layer moves to the side and up or down (dp). */
    const val PAN_X = 14f
    const val PAN_Y = 4f

    /** The point the camera moves towards (dp): the clearing below the mountains. */
    const val PIVOT_X = 480f
    const val PIVOT_Y = 330f

    /** The pose at rest: the stills and the docs show the whole picture. */
    val REST = CameraPose(ZOOM_MIN, 0f, 0f)

    /** The pose [seconds] into the wall clock's run. */
    fun at(seconds: Double): CameraPose {
        val z = 0.5 + 0.5 * Noise.wander(seconds, ZOOM_PERIOD_S, 7)
        return CameraPose(
            (ZOOM_MIN + (ZOOM_MAX - ZOOM_MIN) * z.coerceIn(0.0, 1.0)).toFloat(),
            (PAN_X * Noise.wander(seconds, PAN_X_PERIOD_S, 11)).toFloat().coerceIn(-PAN_X, PAN_X),
            (PAN_Y * Noise.wander(seconds, PAN_Y_PERIOD_S, 13)).toFloat().coerceIn(-PAN_Y, PAN_Y),
        )
    }

    /** The rough length of one swing (s): a quarter of an hour in and out, ten minutes to the side. */
    private const val ZOOM_PERIOD_S = 900.0
    private const val PAN_X_PERIOD_S = 610.0
    private const val PAN_Y_PERIOD_S = 770.0
}
