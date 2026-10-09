package io.github.hazymorning.klangbild.render

import io.github.hazymorning.klangbild.color.Lab
import io.github.hazymorning.klangbild.color.Lch
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.color.Oklab
import kotlin.math.exp
import kotlin.math.min

/**
 * The wall behind the cover flow in the dark: a broad, dim pool of light above the cards in
 * the colour of the current cover, as if a lamp over the shelf caught its colour – brightest
 * above the middle card, gone before the text column (which stands on black) and the screen's
 * edges. The floor below the cards stays black, so their mirror images stand out.
 *
 * One dithered bitmap per scene at half the dp resolution (the light is soft), drawn as one
 * rectangle like [WallGlow].
 */
object FlowLight {
    const val MAP_W = 480
    const val MAP_H = 270

    /** Brightest point (relative luminance, ≈ L* 9 by day). */
    const val PEAK = 0.011f

    /** Spread of the pool (dp) and how far its centre sits above the middle card's top edge. */
    const val SIGMA_X = 220f
    const val SIGMA_Y = 150f
    const val ABOVE = 30f

    fun field(lead: Lab?, layout: HaloLayout, lighting: Lighting): IntArray {
        val screen = layout.screen
        val color = if (lead == null) HaloLight.LAMP else Lch(0.62f, min(lead.chroma * 0.8f, 0.12f), lead.hue)
        val tint = HaloLight.lightTint(color, PEAK, lighting)
        val tr = Oklab.srgbToLinear(tint shr 16)
        val tg = Oklab.srgbToLinear(tint shr 8)
        val tb = Oklab.srgbToLinear(tint)
        val img = LinearImage(MAP_W, MAP_H)
        val sx = screen.widthDp / MAP_W
        val sy = screen.heightDp / MAP_H
        val cx = layout.artRect.centerX
        val cy = layout.artTop - ABOVE
        val floor = layout.artTop + layout.art
        val column = layout.columnLeft
        for (y in 0 until MAP_H) {
            val py = (y + 0.5f) * sy
            // Above the floor line only: the floor is dark glass.
            val wall = HaloLight.smoothstep(floor + 6f, floor - 30f, py)
            for (x in 0 until MAP_W) {
                val px = (x + 0.5f) * sx
                val u = (px - cx) / SIGMA_X
                val v = (py - cy) / SIGMA_Y
                val fade = HaloLight.smoothstep(WallGlow.EDGE_BLACK_DP, WallGlow.EDGE_BLACK_DP + 60f, min(px, screen.widthDp - px)) *
                    HaloLight.smoothstep(WallGlow.EDGE_BLACK_DP, WallGlow.EDGE_BLACK_DP + 40f, min(py, screen.heightDp - py))
                // The text column stands on black.
                val beside = HaloLight.smoothstep(column - 10f, column - 160f, px)
                val value = exp(-0.5f * (u * u + v * v)) * fade * wall * beside
                val o = y * MAP_W + x
                img.r[o] = tr * value
                img.g[o] = tg * value
                img.b[o] = tb * value
            }
        }
        return ImageOps.toArgbDithered(img, seed = 19, ditherLsb = 1.1f, blackBelow = 0.0004f)
    }
}
