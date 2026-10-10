package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.vista.CloudPlace
import io.github.hazymorning.klangbild.vista.Inks
import io.github.hazymorning.klangbild.vista.Noise
import io.github.hazymorning.klangbild.vista.PaperBake
import io.github.hazymorning.klangbild.vista.PaperBaked
import io.github.hazymorning.klangbild.vista.PaperLook
import io.github.hazymorning.klangbild.vista.PaperMotif
import io.github.hazymorning.klangbild.vista.Sheet
import io.github.hazymorning.klangbild.vista.SheetId
import io.github.hazymorning.klangbild.vista.SkyPlaces
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** The baked sheets as images the screen can draw (made off the main thread). */
class PaperImages(val baked: PaperBaked) {
    private fun img(s: Sheet): ImageBitmap = Bitmap.createBitmap(s.pixels, s.width, s.height, Bitmap.Config.ARGB_8888).asImageBitmap()

    val sheets: Map<SheetId, ImageBitmap> = baked.sheets.mapValues { img(it.value) }
    val shadows: Map<SheetId, ImageBitmap> = baked.shadows.mapValues { img(it.value) }
    val clouds = baked.clouds.map { img(it) }
    val cloudShadows = baked.cloudShadows.map { img(it) }
    val flames = baked.flames.map { img(it.sheet) }
    val sun = img(baked.sun)
    val moon = img(baked.moon)
    val discShadow = img(baked.discShadow)
    val glow = img(baked.glow)
}

/** What moves, at one moment: the clouds, the fire, the stars' flicker. */
class PaperMotion(
    val time: Double,
    val clouds: List<CloudPlace>,
)

/**
 * Draws the diorama in four layers. The sky (with the sun and the moon) and the land change
 * only with the light – every ten seconds or so – so each is drawn into a layer of its own and
 * kept between frames; the stars, the clouds and the fire move on every frame, drawn over them.
 * Everything that changes is a colour matrix, a position or a small transform: nothing is cut
 * anew. [look] and [places] should only be read anew when the light has moved on.
 */
@Composable
fun PaperView(images: PaperImages, look: () -> PaperLook, places: () -> SkyPlaces, motion: () -> PaperMotion, modifier: Modifier = Modifier) {
    val filters = remember(images) { FilterCache() }
    val stars = remember(images) { StarBatches() }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            overscanned(images.baked) { drawBack(images, look(), places(), filters) }
        }
        Canvas(Modifier.fillMaxSize()) {
            overscanned(images.baked) { drawMoving(images, look(), places(), motion(), filters, stars) }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            overscanned(images.baked) { drawLand(images, look(), filters) }
        }
        Canvas(Modifier.fillMaxSize()) {
            overscanned(images.baked) { drawFire(images, look(), motion(), filters) }
        }
    }
}

/** The canvas starts [PaperMotif.OVERSCAN] dp left of and above the screen. */
private inline fun DrawScope.overscanned(b: PaperBaked, block: DrawScope.() -> Unit) {
    val o = PaperMotif.OVERSCAN * b.density
    translate(-o, -o) { block() }
}

/**
 * The stars as a dozen batches of points – by size and by which of four slow flickers they
 * follow – made anew only when they have turned (every few seconds), drawn with a call each.
 * Behind the moon's disc there are none.
 */
private class StarBatches {
    class Batch(val points: List<Offset>, val radius: Float, val brightness: Float, val group: Int)

    private var source: SkyPlaces? = null
    private var batches: List<Batch> = emptyList()

    fun of(p: SkyPlaces, b: PaperBaked, d: Float): List<Batch> {
        if (p === source) return batches
        source = p
        val groups = HashMap<Long, MutableList<Offset>>()
        val meta = HashMap<Long, Pair<Float, Float>>()
        for (i in p.stars.indices) {
            val s = p.stars[i]
            if (s.y > PaperMotif.skyline(s.x) - 2f) continue
            if (p.moonUp && hypot(s.x - p.moonX, s.y - p.moonY) < PaperBake.MOON_RADIUS + 3f) continue
            val key = (s.size.toRawBits().toLong() shl 8) or (i % FLICKERS).toLong()
            groups.getOrPut(key) { ArrayList() } += Offset(b.px(s.x), b.px(s.y))
            meta[key] = s.size * d to s.brightness
        }
        batches = groups.map { (key, points) -> Batch(points, meta.getValue(key).first, meta.getValue(key).second, (key and 0xFF).toInt()) }
        return batches
    }
}

private const val FLICKERS = 4

/** Colour filters for inks, made once per new set of colours. */
private class FilterCache {
    private val cache = HashMap<Inks, ColorFilter>()
    private val tints = HashMap<Int, ColorFilter>()
    private val matrix = FloatArray(20)

    fun of(inks: Inks): ColorFilter {
        if (cache.size > 96) cache.clear()
        return cache.getOrPut(inks) { ColorFilter.colorMatrix(ColorMatrix(inks.matrix(1f, matrix).copyOf())) }
    }

    fun tint(argb: Int): ColorFilter {
        if (tints.size > 64) tints.clear()
        return tints.getOrPut(argb) { ColorFilter.tint(Color(argb), BlendMode.Modulate) }
    }
}

/** Draws [img] (the baked [s]) at its place, moved by ([dx], [dy]) pixels. */
private fun DrawScope.sheet(img: ImageBitmap, s: Sheet, filter: ColorFilter?, alpha: Float = 1f, dx: Float = 0f, dy: Float = 0f) {
    if (s.scale == 1 && dx == 0f && dy == 0f) {
        drawImage(img, topLeft = Offset(s.left.toFloat(), s.top.toFloat()), colorFilter = filter, alpha = alpha)
    } else {
        drawImage(
            img,
            dstOffset = IntOffset((s.left + dx).roundToInt(), (s.top + dy).roundToInt()),
            dstSize = IntSize(s.width * s.scale, s.height * s.scale),
            colorFilter = filter,
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

/** The sky's wash, the glows of the horizon and the sun, the sun's paper disc, the moon. */
private fun DrawScope.drawBack(images: PaperImages, l: PaperLook, p: SkyPlaces, f: FilterCache) {
    val b = images.baked
    val d = b.density
    fun dp(v: Float) = b.px(v)
    sheet(images.sheets.getValue(SheetId.SKY), b.sheets.getValue(SheetId.SKY), f.of(l.inks(SheetId.SKY)))

    // Glows: along the horizon under the sun, and around the sun itself.
    if (l.horizonGlowAlpha > 0.01f) {
        val w = 980f * d
        val h = 230f * d
        drawImage(
            images.glow, dstOffset = IntOffset((dp(p.sunX) - w / 2).roundToInt(), (dp(HORIZON_GLOW_Y) - h / 2).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), colorFilter = f.tint(l.horizonGlow), alpha = l.horizonGlowAlpha, filterQuality = FilterQuality.Low,
        )
    }
    if (l.glowAlpha * l.discAlpha > 0.01f && p.sunY < PaperMotif.FAR_FOOT) {
        val r = 150f * d
        drawImage(
            images.glow, dstOffset = IntOffset((dp(p.sunX) - r).roundToInt(), (dp(p.sunY) - r).roundToInt()),
            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()), colorFilter = f.tint(l.glow), alpha = l.glowAlpha * l.discAlpha, filterQuality = FilterQuality.Low,
        )
    }
    if (l.discAlpha > 0.01f && p.sunY < PaperMotif.FAR_FOOT) disc(images, images.sun, b.sun, dp(p.sunX), dp(p.sunY), Inks.flat(l.sunDisc), l.discAlpha, l, f)
    if (l.moonAlpha > 0.01f && p.moonFraction > 0.01f && p.moonY < PaperMotif.FAR_FOOT) drawMoon(images, l, p, f)
}

/** A paper disc glued on the sky, [alpha] strong: its soft shadow, then the disc in [inks]. */
private fun DrawScope.disc(images: PaperImages, img: ImageBitmap, s: Sheet, cx: Float, cy: Float, inks: Inks, alpha: Float, l: PaperLook, f: FilterCache) {
    val d = images.baked.density
    val sh = images.baked.discShadow
    if (l.shadowAlpha > 0.01f) {
        translate(cx, cy) {
            sheet(images.discShadow, sh, f.tint(l.shadow), alpha * l.shadowAlpha * 0.8f, l.shadowSide * 1.6f * d, 1.6f * d)
        }
    }
    translate(cx, cy) { sheet(img, s, f.of(inks), alpha) }
}

/**
 * The moon: at night the whole disc a breath lighter than the sky, the lit part with its phase,
 * its bright side towards the sun; by day only its lit part, faint, as the day moon is.
 */
private fun DrawScope.drawMoon(images: PaperImages, l: PaperLook, p: SkyPlaces, f: FilterCache) {
    val b = images.baked
    val cx = b.px(p.moonX)
    val cy = b.px(p.moonY)
    if (l.moonDarkAlpha > 0.01f) disc(images, images.moon, b.moon, cx, cy, Inks.flat(l.moonDark), l.moonDarkAlpha, l, f)
    val r = PaperBake.MOON_RADIUS * b.density + 1f
    val frac = p.moonFraction.coerceIn(0f, 1f)
    // Turned so the lit limb faces the sun (its place on the sky, even below the horizon).
    val angle = Math.toDegrees(atan2((p.sunY - p.moonY).toDouble(), (p.sunX - p.moonX).toDouble())).toFloat()
    val path = Path().apply {
        // The bright limb: a half circle on the right; the terminator: half an ellipse.
        val k = 1f - 2f * frac
        moveTo(0f, -r)
        arcTo(Rect(-r, -r, r, r), -90f, 180f, false)
        val ex = max(0.0001f, kotlin.math.abs(k) * r)
        arcTo(Rect(-ex, -r, ex, r), 90f, if (k > 0f) -180f else 180f, false)
        close()
    }
    withTransform({
        translate(cx, cy)
        rotate(angle, pivot = Offset.Zero)
    }) {
        clipPath(path) {
            rotate(-angle, pivot = Offset.Zero) { sheet(images.moon, b.moon, f.of(Inks.flat(l.moonLit)), l.moonAlpha) }
        }
    }
}

/** The stars' slow flicker and the drifting tissue clouds with their shadows on the sky. */
private fun DrawScope.drawMoving(images: PaperImages, l: PaperLook, p: SkyPlaces, m: PaperMotion, f: FilterCache, stars: StarBatches) {
    val b = images.baked
    val d = b.density
    if (l.stars > 0.01f) {
        for (batch in stars.of(p, b, d)) {
            val flicker = 0.78f + 0.22f * Noise.wander(m.time, 2.6 + batch.group * 0.7, 300 + batch.group).toFloat()
            drawPoints(
                batch.points, PointMode.Points, STAR, strokeWidth = 2f * batch.radius, cap = StrokeCap.Round,
                alpha = (l.stars * batch.brightness * flicker).coerceIn(0f, 1f),
            )
        }
    }
    val cloudFilter = f.of(l.cloud)
    val shadowFilter = f.tint(l.shadow)
    val depth = PaperBake.CLOUD_DEPTH
    for (c in m.clouds) {
        withTransform({
            translate(b.px(c.x), b.px(c.y))
            scale(c.scale, c.scale, pivot = Offset.Zero)
        }) {
            if (l.shadowAlpha > 0.01f) {
                sheet(
                    images.cloudShadows[c.design], b.cloudShadows[c.design], shadowFilter, c.alpha * l.shadowAlpha * 0.5f,
                    l.shadowSide * (1f + 3.8f * depth) * l.shadowLength * d, (1.4f + 1.6f * depth) * d,
                )
            }
            sheet(images.clouds[c.design], b.clouds[c.design], cloudFilter, c.alpha)
        }
    }
}

/** The land, back to front: each sheet's soft shadow on what lies behind it, then the sheet. */
private fun DrawScope.drawLand(images: PaperImages, l: PaperLook, f: FilterCache) {
    val b = images.baked
    val d = b.density
    val shadowFilter = f.tint(l.shadow)
    for ((id, s) in b.sheets) {
        if (id == SheetId.SKY) continue
        val alpha = if (id == SheetId.LEAVES) b.leafAlpha else 1f
        if (alpha <= 0.01f) continue
        val shadow = b.shadows[id]
        if (shadow != null && l.shadowAlpha > 0.003f) {
            sheet(
                images.shadows.getValue(id), shadow, shadowFilter, l.shadowAlpha * alpha,
                l.shadowSide * (1f + 3.8f * id.depth) * l.shadowLength * d, (1.4f + 1.6f * id.depth) * d,
            )
        }
        sheet(images.sheets.getValue(id), s, f.of(l.inks(id)), alpha)
    }
}

/**
 * The campfire: its tongues of tissue, each stretching and leaning a little on its own, never
 * in a loop; and its warm light on the clearing, breathing with them.
 */
private fun DrawScope.drawFire(images: PaperImages, l: PaperLook, m: PaperMotion, f: FilterCache) {
    val b = images.baked
    val d = b.density
    val t = m.time
    if (l.fire > 0.005f) {
        val reach = l.fireReach * d
        // The glow's Gaussian falls to e^-½ at [reach]; the sprite's own at 0.395 of its radius.
        val w = 2f * reach / 0.395f
        val h = w * 0.68f
        val breath = 0.9f + 0.1f * Noise.wander(t, 0.7, 600).toFloat()
        drawImage(
            images.glow, dstOffset = IntOffset((b.fireX - w / 2).roundToInt(), (b.fireY - 10f * d - h / 2).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), colorFilter = f.tint(l.fireGlow), alpha = (l.fire * breath).coerceIn(0f, 1f),
            blendMode = BlendMode.Plus, filterQuality = FilterQuality.Low,
        )
    }
    val filter = f.of(l.flame)
    for ((i, flame) in b.flames.withIndex()) {
        val k = flame.seed
        val stretch = 1f + 0.07f * Noise.wander(t, 0.9 + 0.2 * k, 500 + k).toFloat() + 0.03f * Noise.wander(t, 0.37, 520 + k).toFloat()
        val lean = 2.5f * Noise.wander(t, 1.3 + 0.3 * k, 540 + k).toFloat()
        withTransform({
            translate(flame.footX, flame.footY)
            rotate(lean, pivot = Offset.Zero)
            scale(1f - 0.3f * (stretch - 1f), stretch, pivot = Offset.Zero)
        }) {
            sheet(images.flames[i], flame.sheet, filter)
        }
    }
}

private val STAR = Color(0xFFF3EEDF)

/** Where the horizon's glow lies (dp): along the far range's foot. */
private const val HORIZON_GLOW_Y = 300f
