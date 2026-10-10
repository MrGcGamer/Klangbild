package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.vista.CameraPose
import io.github.hazymorning.klangbild.vista.CloudPlace
import io.github.hazymorning.klangbild.vista.Depth
import io.github.hazymorning.klangbild.vista.Inks
import io.github.hazymorning.klangbild.vista.MistField
import io.github.hazymorning.klangbild.vista.Noise
import io.github.hazymorning.klangbild.vista.PaperBake
import io.github.hazymorning.klangbild.vista.PaperBaked
import io.github.hazymorning.klangbild.vista.PaperLook
import io.github.hazymorning.klangbild.vista.PaperMotif
import io.github.hazymorning.klangbild.vista.Sheet
import io.github.hazymorning.klangbild.vista.SheetId
import io.github.hazymorning.klangbild.vista.SkyPlaces
import io.github.hazymorning.klangbild.vista.VistaCamera
import io.github.hazymorning.klangbild.vista.Weather
import io.github.hazymorning.klangbild.vista.Weatherfall
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** The baked sheets as images the screen can draw (made off the main thread); the baked pixels are let go once copied. */
class PaperImages(val baked: PaperBaked) {
    private fun img(s: Sheet): ImageBitmap = Bitmap.createBitmap(s.pixels, s.width, s.height, Bitmap.Config.ARGB_8888).asImageBitmap().also { s.release() }

    val sheets: Map<SheetId, ImageBitmap> = baked.sheets.mapValues { img(it.value) }
    val shadows: Map<SheetId, ImageBitmap> = baked.shadows.mapValues { img(it.value) }
    val standees: List<List<ImageBitmap>> = baked.standees.map { st -> st.parts.map { img(it.second) } }
    val standeeShadows: List<List<ImageBitmap>> = baked.standees.map { st -> st.shadows.map { img(it) } }
    val clouds = baked.clouds.map { img(it) }
    val cloudShadows = baked.cloudShadows.map { img(it) }
    val flames = baked.flames.map { img(it.sheet) }
    val sun = img(baked.sun)
    val moon = img(baked.moon)
    val discShadow = img(baked.discShadow)
    val glow = img(baked.glow)
}

/**
 * What moves, at one moment: the clouds, the fire, the stars' flicker, the cut-outs in the wind,
 * the camera, what falls – [time] the wall clock's seconds, [cloudTime] the clouds' own clock
 * (faster in the wind), [drift] how far the wind has blown so far (its integral over time).
 */
class PaperMotion(
    val time: Double,
    val clouds: List<CloudPlace>,
    val camera: CameraPose = VistaCamera.REST,
    val weather: Weather = Weather.FAIR,
    val cloudTime: Double = time,
    val drift: Double = time * Weather.BREEZE,
)

/**
 * Draws the diorama in layers, each moved by the camera as far as its depth asks
 * ([VistaCamera]): the sky (with the sun and the moon), the far mountains, the clearing and the
 * near banks change only with the light – every ten seconds or so – so each is drawn into a
 * layer of its own and kept between frames, the camera only moving the finished layer; the
 * stars and the clouds, the cut-outs swaying in the breeze, the fire and its light are drawn
 * every frame. Everything that changes is a colour matrix, a position or a small transform:
 * nothing is cut anew. [look] and [places] should only be read anew when the light has moved on.
 */
@Composable
fun PaperView(images: PaperImages, look: () -> PaperLook, places: () -> SkyPlaces, motion: () -> PaperMotion, modifier: Modifier = Modifier) {
    val filters = remember(images) { FilterCache() }
    val stars = remember(images) { StarBatches() }
    val fall = remember(images) { FallBatches() }
    val b = images.baked
    val all = 0 to b.canvasHeight
    val sky = remember(images) { 0 to b.sheets.getValue(SheetId.SKY).let { it.top + it.height } }
    val far = remember(images) { b.band(Depth.FAR) }
    val mid = remember(images) { b.band(Depth.MID) }
    val front = remember(images) { b.band(Depth.FRONT) }
    Box(modifier) {
        Canvas(Modifier.canvas(b, sky).camera(Depth.SKY, motion, b, sky, cached = true)) { rows(sky) { drawBack(images, look(), places(), filters) } }
        Canvas(Modifier.canvas(b, all).camera(Depth.SKY, motion, b, all, cached = false)) { drawMoving(images, look(), places(), motion(), filters, stars) }
        Canvas(Modifier.canvas(b, far).camera(Depth.FAR, motion, b, far, cached = true)) { rows(far) { drawLand(images, look(), filters, Depth.FAR) } }
        Canvas(Modifier.canvas(b, all).camera(Depth.FAR, motion, b, all, cached = false)) { drawWeather(images, look(), motion(), filters, fall, Depth.FAR) }
        Canvas(Modifier.canvas(b, mid).camera(Depth.MID, motion, b, mid, cached = true)) { rows(mid) { drawLand(images, look(), filters, Depth.MID) } }
        Canvas(Modifier.canvas(b, all).camera(Depth.MID, motion, b, all, cached = false)) { drawStandeesAndFlames(images, look(), motion(), filters) }
        Canvas(Modifier.canvas(b, all).camera(Depth.MID, motion, b, all, cached = false)) { drawWeather(images, look(), motion(), filters, fall, Depth.MID) }
        Canvas(Modifier.canvas(b, front).camera(Depth.FRONT, motion, b, front, cached = true)) { rows(front) { drawLand(images, look(), filters, Depth.FRONT) } }
        Canvas(Modifier.canvas(b, all).camera(Depth.MID, motion, b, all, cached = false)) { drawLights(images, look(), motion(), filters) }
        Canvas(Modifier.canvas(b, all).camera(Depth.FRONT, motion, b, all, cached = false)) { drawWeather(images, look(), motion(), filters, fall, Depth.FRONT) }
    }
}

/**
 * A layer's canvas: the baked canvas's rows [rows] (pixels), placed where they stand – the
 * canvas reaches [PaperMotif.OVERSCAN] beyond the screen on every side, so neither the camera
 * nor the screen's drift ever shows an edge, and a layer kept as an image is only as tall as
 * the paper it holds.
 */
private fun Modifier.canvas(b: PaperBaked, rows: Pair<Int, Int>): Modifier = layout { measurable, constraints ->
    val o = (PaperMotif.OVERSCAN * b.density).roundToInt()
    val p = measurable.measure(Constraints.fixed(b.canvasWidth, rows.second - rows.first))
    layout(constraints.maxWidth, constraints.maxHeight) { p.place(-o, rows.first - o) }
}

/** Draws in the baked canvas's coordinates on a canvas that starts at row [rows]. */
private inline fun DrawScope.rows(rows: Pair<Int, Int>, block: DrawScope.() -> Unit) {
    translate(0f, -rows.first.toFloat()) { block() }
}

/**
 * Moves a layer with the camera, as far as its [depth] asks – read every frame, but only the
 * layer's transform changes: a [cached] layer keeps what it drew (until the light moves on).
 * The layer holds the baked canvas's [rows]; it turns about the camera's point on the screen.
 */
private fun Modifier.camera(depth: Depth, motion: () -> PaperMotion, b: PaperBaked, rows: Pair<Int, Int>, cached: Boolean): Modifier = graphicsLayer {
    val pose = motion().camera
    val k = pose.scale(depth)
    scaleX = k
    scaleY = k
    translationX = pose.dx(depth) * density
    translationY = pose.dy(depth) * density
    transformOrigin = TransformOrigin(b.px(VistaCamera.PIVOT_X) / b.canvasWidth, (b.px(VistaCamera.PIVOT_Y) - rows.first) / (rows.second - rows.first))
    if (cached) compositingStrategy = CompositingStrategy.Offscreen
}

/**
 * Moves what lies on the near bank (the music's label) with it – a screen-sized box, the same
 * point of the screen at the middle of the camera's move.
 */
fun Modifier.onNearBank(motion: () -> PaperMotion): Modifier = graphicsLayer {
    val pose = motion().camera
    val k = pose.scale(Depth.FRONT)
    scaleX = k
    scaleY = k
    translationX = pose.dx(Depth.FRONT) * density
    translationY = pose.dy(Depth.FRONT) * density
    transformOrigin = TransformOrigin(VistaCamera.PIVOT_X / PaperMotif.WIDTH, VistaCamera.PIVOT_Y / PaperMotif.HEIGHT)
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
    val deckFilter = f.of(l.deck)
    val shadowFilter = f.tint(l.shadow)
    val depth = PaperBake.CLOUD_DEPTH
    for (c in m.clouds) {
        // The ceiling's heaps hang upside down: their humps towards the ground, their shadow still below them.
        val flip = if (c.deck) -1f else 1f
        withTransform({
            translate(b.px(c.x), b.px(c.y))
            scale(c.scale, c.scale * flip, pivot = Offset.Zero)
        }) {
            if (l.shadowAlpha > 0.01f) {
                sheet(
                    images.cloudShadows[c.design], b.cloudShadows[c.design], shadowFilter, c.alpha * l.shadowAlpha * 0.5f,
                    l.shadowSide * (1f + 3.8f * depth) * l.shadowLength * d, flip * (1.4f + 1.6f * depth) * d,
                )
            }
            sheet(images.clouds[c.design], b.clouds[c.design], if (c.deck) deckFilter else cloudFilter, c.alpha)
        }
    }
}

/** One layer of the land, back to front: each sheet's soft shadow on what lies behind it, then the sheet. */
private fun DrawScope.drawLand(images: PaperImages, l: PaperLook, f: FilterCache, depth: Depth) {
    val b = images.baked
    val d = b.density
    val shadowFilter = f.tint(l.shadow)
    for ((id, s) in b.sheets) {
        if (id == SheetId.SKY || id.layer != depth) continue
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
 * The cut-outs on the meadow, each swaying a little about its foot in a breeze of its own –
 * a few tenths of a degree, never in a loop – with its shadow; then the campfire's tongues of
 * tissue, each stretching and leaning on its own.
 */
private fun DrawScope.drawStandeesAndFlames(images: PaperImages, l: PaperLook, m: PaperMotion, f: FilterCache) {
    val b = images.baked
    val d = b.density
    val t = m.time
    val shadowFilter = f.tint(l.shadow)
    val depth = SheetId.STANDEES.depth
    for ((i, st) in b.standees.withIndex()) {
        val sway = SWAY_DEG * Noise.wander(t, 5.5 + 0.8 * st.seed, 700 + st.seed).toFloat()
        withTransform({
            translate(st.footX, st.footY)
            rotate(sway, pivot = Offset.Zero)
        }) {
            if (l.shadowAlpha > 0.003f) {
                for ((k, sh) in st.shadows.withIndex()) {
                    val alpha = if (st.parts[k].first == SheetId.CROWNS) b.leafAlpha else 1f
                    sheet(images.standeeShadows[i][k], sh, shadowFilter, l.shadowAlpha * alpha, l.shadowSide * (1f + 3.8f * depth) * l.shadowLength * d, (1.4f + 1.6f * depth) * d)
                }
            }
            for ((k, part) in st.parts.withIndex()) {
                val (id, sheet) = part
                val alpha = if (id == SheetId.CROWNS) b.leafAlpha else 1f
                if (alpha > 0.01f) sheet(images.standees[i][k], sheet, f.of(l.inks(id)), alpha)
            }
        }
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

/**
 * The light of the camp, added over everything near it: the fire's on the clearing, breathing
 * with its flames, and at night the lantern's in the tent's door.
 */
private fun DrawScope.drawLights(images: PaperImages, l: PaperLook, m: PaperMotion, f: FilterCache) {
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
    if (l.lantern > 0.01f) {
        val w = LANTERN_DP * d
        val h = w * 0.85f
        drawImage(
            images.glow, dstOffset = IntOffset((b.doorX - w / 2).roundToInt(), (b.doorY - h / 2).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), colorFilter = f.tint(LANTERN), alpha = (0.85f * l.lantern).coerceIn(0f, 1f),
            blendMode = BlendMode.Plus, filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * The weather at one depth, moving with its layer: the valley's mist lying there (tissue bands,
 * drawn wide and faint), then the snowflakes and the rain's threads falling in front of it.
 */
private fun DrawScope.drawWeather(images: PaperImages, l: PaperLook, m: PaperMotion, f: FilterCache, fall: FallBatches, depth: Depth) {
    val b = images.baked
    val w = m.weather
    if (l.mistAlpha > 0.01f && depth != Depth.FRONT) {
        val filter = f.of(Inks.flat(l.mist))
        for (c in MistField.places(m.cloudTime, l.mistAlpha, depth, fall.mist)) {
            withTransform({
                translate(b.px(c.x), b.px(c.y))
                scale(c.scale * c.stretch, c.scale, pivot = Offset.Zero)
            }) {
                sheet(images.clouds[c.design], b.clouds[c.design], filter, c.alpha)
            }
        }
    }
    if (w.snow > 0.003f) {
        for (band in Weatherfall.SNOW) if (band.depth == depth) {
            val n = Weatherfall.place(band, m.time, m.drift, w.wind, w.snow, snow = true, fall.items)
            drawIntoCanvas { fall.flakes(it.nativeCanvas, n, b, l.snowInk, l.shadow, l.shadowAlpha) }
        }
    }
    if (w.rain > 0.003f) {
        for (band in Weatherfall.RAIN) if (band.depth == depth) {
            val n = Weatherfall.place(band, m.time, m.drift, w.wind, w.rain, snow = false, fall.items)
            drawIntoCanvas { fall.threads(it.nativeCanvas, n, b, l.rainInk, band) }
        }
    }
}

/**
 * Draws what falls in a few calls: the flakes as round points and the drops as lines, sorted
 * into a handful of sizes and strengths – each batch one call. Buffers are kept between frames.
 */
private class FallBatches {
    val items = FloatArray(Weatherfall.STRIDE * 200)
    val mist = ArrayList<CloudPlace>()
    private val sizes = 3
    private val strengths = 3
    private val buffers = Array(sizes * strengths) { FloatArray(400 * 2) }
    private val lines = Array(strengths) { FloatArray(200 * 4) }
    private val counts = IntArray(sizes * strengths)
    private val lineCounts = IntArray(strengths)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    /** The flakes: discs of white paper, each with a breath of shadow below it (seen against a pale sky), by size and strength. */
    fun flakes(canvas: android.graphics.Canvas, n: Int, b: PaperBaked, ink: Int, shadow: Int, shadowAlpha: Float) {
        if (n == 0) return
        counts.fill(0)
        var lo = Float.MAX_VALUE
        var hi = 0f
        for (i in 0 until n) {
            val s = items[i * Weatherfall.STRIDE + 2]
            lo = minOf(lo, s)
            hi = maxOf(hi, s)
        }
        for (i in 0 until n) {
            val o = i * Weatherfall.STRIDE
            val size = if (hi > lo) (((items[o + 2] - lo) / (hi - lo)) * sizes).toInt().coerceIn(0, sizes - 1) else 0
            val strength = (items[o + 3] * strengths).toInt().coerceIn(0, strengths - 1)
            val k = size * strengths + strength
            val buf = buffers[k]
            val c = counts[k]
            if (c * 2 + 1 >= buf.size) continue
            buf[c * 2] = b.px(items[o])
            buf[c * 2 + 1] = b.px(items[o + 1])
            counts[k] = c + 1
        }
        paint.style = android.graphics.Paint.Style.STROKE
        val drop = FLAKE_SHADOW_DP * b.density
        for (k in buffers.indices) {
            if (counts[k] == 0) continue
            val size = k / strengths
            val strength = k % strengths
            val r = lo + (hi - lo) * (size + 0.5f) / sizes
            val a = (strength + 1f) / strengths
            paint.strokeWidth = 2f * r * b.density
            paint.color = shadow
            paint.alpha = (255 * a * (0.18f + 0.4f * shadowAlpha)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(drop * 0.4f, drop)
            canvas.drawPoints(buffers[k], 0, counts[k] * 2, paint)
            canvas.restore()
            paint.color = ink
            paint.alpha = (255 * a).toInt().coerceIn(0, 255)
            canvas.drawPoints(buffers[k], 0, counts[k] * 2, paint)
        }
    }

    /** The rain: fine threads, slanting with the wind, by strength. */
    fun threads(canvas: android.graphics.Canvas, n: Int, b: PaperBaked, ink: Int, band: Weatherfall.Band) {
        if (n == 0) return
        val lc = lineCounts
        lc.fill(0)
        for (i in 0 until n) {
            val o = i * Weatherfall.STRIDE
            val strength = (items[o + 3] * strengths).toInt().coerceIn(0, strengths - 1)
            val buf = lines[strength]
            val c = lc[strength]
            if (c * 4 + 3 >= buf.size) continue
            val len = items[o + 2]
            val slant = items[o + 4]
            // The head where it is, the tail behind it along the way it came.
            buf[c * 4] = b.px(items[o] - slant * len)
            buf[c * 4 + 1] = b.px(items[o + 1] - len)
            buf[c * 4 + 2] = b.px(items[o])
            buf[c * 4 + 3] = b.px(items[o + 1])
            lc[strength] = c + 1
        }
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = (0.7f + 0.03f * band.sizeMax) * b.density
        for (k in 0 until strengths) {
            if (lc[k] == 0) continue
            paint.color = ink
            paint.alpha = (255 * RAIN_ALPHA * (k + 1f) / strengths).toInt().coerceIn(0, 255)
            canvas.drawLines(lines[k], 0, lc[k] * 4, paint)
        }
    }
}

/** How strong the rain's threads are at most: a veil, not a curtain. */
private const val RAIN_ALPHA = 0.42f

/** How far below a flake its shadow falls (dp). */
private const val FLAKE_SHADOW_DP = 0.9f

/** How far the cut-outs lean in the breeze, at most (degrees). */
private const val SWAY_DEG = 0.8f

/** The lantern's light in the tent's door: its colour and how wide it falls (dp). */
private const val LANTERN_DP = 40f
private const val LANTERN = 0xFFFFB45A.toInt()

private val STAR = Color(0xFFF3EEDF)

/** Where the horizon's glow lies (dp): along the far range's foot. */
private const val HORIZON_GLOW_Y = 300f
