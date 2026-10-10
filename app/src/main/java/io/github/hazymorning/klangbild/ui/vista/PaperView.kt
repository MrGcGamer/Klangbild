package io.github.hazymorning.klangbild.ui.vista

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.vista.CloudPlace
import io.github.hazymorning.klangbild.vista.Inks
import io.github.hazymorning.klangbild.vista.Noise
import io.github.hazymorning.klangbild.vista.PaperBaked
import io.github.hazymorning.klangbild.vista.PaperLook
import io.github.hazymorning.klangbild.vista.PaperMotif
import io.github.hazymorning.klangbild.vista.Sheet
import io.github.hazymorning.klangbild.vista.SheetId
import io.github.hazymorning.klangbild.vista.SkyPlaces
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** The baked sheets as images the screen can draw (made off the main thread). */
class PaperImages(val baked: PaperBaked) {
    private fun img(s: Sheet): ImageBitmap = Bitmap.createBitmap(s.pixels, s.width, s.height, Bitmap.Config.ARGB_8888).asImageBitmap()

    val sheets: Map<SheetId, ImageBitmap> = baked.sheets.mapValues { img(it.value) }
    val shadows: Map<SheetId, ImageBitmap> = baked.shadows.mapValues { img(it.value) }
    val trunks = baked.trees.map { img(it.trunk) }
    val crowns = baked.trees.map { t -> t.crown?.let { img(it) } }
    val treeShadows = baked.treeShadows.map { img(it) }
    val accents: Map<SheetId, ImageBitmap> = baked.accents.mapValues { img(it.value) }
    val sway = baked.sway.map { img(it.sheet) }
    val clouds = baked.clouds.map { img(it) }
    val glow = img(baked.glow)
}

/** What moves, at one moment: the wind in the grass and the trees, the light on the water, the stars' flicker. */
class PaperMotion(
    val time: Double,
    val clouds: List<CloudPlace>,
)

/**
 * Draws the paper cut: the sheets back to front, each coloured by its inks; the sun, moon and
 * stars where the sky has them; soft shadows that every sheet throws on the ones behind it,
 * away from the light. Everything that changes per frame is a colour matrix, a position or a
 * small rotation – nothing is drawn anew.
 */
@Composable
fun PaperView(images: PaperImages, look: () -> PaperLook, places: () -> SkyPlaces, motion: () -> PaperMotion, modifier: Modifier = Modifier) {
    val filters = remember(images) { FilterCache() }
    val stars = remember(images) { StarBatches() }
    Canvas(modifier) {
        val l = look()
        val p = places()
        val m = motion()
        val b = images.baked
        val o = PaperMotif.OVERSCAN * density
        translate(-o, -o) {
            drawPaper(images, b, l, p, m, filters, stars, density)
        }
    }
}

/**
 * The stars as a dozen batches of points – by size and by which of four slow flickers they
 * follow – made anew only when they have turned (every few seconds), drawn with a call each.
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
            if (s.y > PaperMotif.HORIZON - 4f) continue
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
        if (cache.size > 64) cache.clear()
        return cache.getOrPut(inks) { ColorFilter.colorMatrix(ColorMatrix(inks.matrix(1f, matrix).copyOf())) }
    }

    fun tint(argb: Int): ColorFilter {
        if (tints.size > 64) tints.clear()
        return tints.getOrPut(argb) { ColorFilter.tint(Color(argb), BlendMode.Modulate) }
    }
}

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

private fun DrawScope.drawPaper(images: PaperImages, b: PaperBaked, l: PaperLook, p: SkyPlaces, m: PaperMotion, f: FilterCache, stars: StarBatches, d: Float) {
    fun dp(v: Float) = b.px(v)
    val shadowFilter = f.tint(l.shadow)
    val sdx = l.shadowDx * d
    val sdy = l.shadowDy * d
    fun shadowOf(id: SheetId) {
        if (l.shadowAlpha <= 0.003f) return
        val s = b.shadows[id] ?: return
        sheet(images.shadows.getValue(id), s, shadowFilter, l.shadowAlpha, sdx, sdy)
    }
    fun land(id: SheetId) = sheet(images.sheets.getValue(id), b.sheets.getValue(id), f.of(l.inks(id)))

    land(SheetId.SKY)

    // Stars: dots of three sizes, in four groups that flicker slowly, each on its own.
    if (l.stars > 0.01f) {
        for (batch in stars.of(p, b, d)) {
            val flicker = 0.78f + 0.22f * Noise.wander(m.time, 2.6 + batch.group * 0.7, 300 + batch.group).toFloat()
            drawPoints(
                batch.points, PointMode.Points, STAR, strokeWidth = 2f * batch.radius, cap = StrokeCap.Round,
                alpha = (l.stars * batch.brightness * flicker).coerceIn(0f, 1f),
            )
        }
    }

    // Glows: along the horizon under the sun, and around the sun itself.
    if (l.horizonGlowAlpha > 0.01f) {
        val w = 980f * d
        val h = 230f * d
        drawImage(
            images.glow, dstOffset = IntOffset((dp(p.sunX) - w / 2).roundToInt(), (dp(PaperMotif.HORIZON) - h / 2).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), colorFilter = f.tint(l.horizonGlow), alpha = l.horizonGlowAlpha, filterQuality = FilterQuality.Low,
        )
    }
    if (l.glowAlpha * l.discAlpha > 0.01f && p.sunY < PaperMotif.HORIZON) {
        val r = 150f * d
        drawImage(
            images.glow, dstOffset = IntOffset((dp(p.sunX) - r).roundToInt(), (dp(p.sunY) - r).roundToInt()),
            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()), colorFilter = f.tint(l.glow), alpha = l.glowAlpha * l.discAlpha, filterQuality = FilterQuality.Low,
        )
    }
    if (l.discAlpha > 0.01f && p.sunY < PaperMotif.HORIZON + 20f) {
        drawCircle(Color(l.sunDisc), radius = SUN_RADIUS * d, center = Offset(dp(p.sunX), dp(p.sunY)), alpha = l.discAlpha)
    }
    if (l.discAlpha > 0.01f && p.moonFraction > 0.01f && p.moonY < PaperMotif.HORIZON + 20f) drawMoon(l, p, d)

    // Clouds: strips of paper drifting; the farther lanes smaller.
    val cloudFilter = f.of(l.cloud)
    for (c in m.clouds) {
        val img = images.clouds[c.design]
        withTransform({
            translate(dp(c.x), dp(c.y))
            scale(c.scale, c.scale, pivot = Offset.Zero)
        }) {
            drawImage(img, colorFilter = cloudFilter, alpha = c.alpha)
        }
    }

    land(SheetId.RANGE_FAR)
    shadowOf(SheetId.RANGE_NEAR)
    land(SheetId.RANGE_NEAR)
    shadowOf(SheetId.SHORE)
    land(SheetId.SHORE)
    land(SheetId.LAKE)
    drawGlints(l, p, m, d, b)
    // The broad-leaved trees among each wood's firs, as strong as their leaves.
    fun accents(id: SheetId) {
        if (b.leafAlpha <= 0.01f) return
        val s = b.accents[id] ?: return
        sheet(images.accents.getValue(id), s, f.of(l.accents.getValue(id)), b.leafAlpha)
    }
    shadowOf(SheetId.WOOD_RIGHT)
    land(SheetId.WOOD_RIGHT)
    accents(SheetId.WOOD_RIGHT)
    shadowOf(SheetId.WOOD_LEFT)
    land(SheetId.WOOD_LEFT)
    accents(SheetId.WOOD_LEFT)
    shadowOf(SheetId.MEADOW)
    land(SheetId.MEADOW)

    // The broad tree: its shadow, the trunk, then the crown, which sways a little about the fork.
    for (i in b.trees.indices) {
        val t = b.trees[i]
        if (l.shadowAlpha > 0.003f) sheet(images.treeShadows[i], b.treeShadows[i], shadowFilter, l.shadowAlpha, sdx, sdy)
        sheet(images.trunks[i], t.trunk, f.of(l.trunk))
        val crown = images.crowns[i] ?: continue
        val angle = (Noise.wander(m.time, 7.0, 40 + i) * 0.45 + Noise.wander(m.time, 2.3, 60 + i) * 0.15).toFloat()
        rotate(angle, pivot = Offset(t.pivotX, t.pivotY)) { sheet(crown, t.crown!!, f.of(l.crown), t.crownAlpha) }
    }

    shadowOf(SheetId.FRONT_LEFT)
    land(SheetId.FRONT_LEFT)
    shadowOf(SheetId.FRONT_RIGHT)
    land(SheetId.FRONT_RIGHT)
    // Grass and reeds on the near shores, swaying in a wind that crosses from one side to the other.
    for (i in b.sway.indices) {
        val t = b.sway[i]
        val gust = Noise.wander(m.time - t.footX / (90.0 * d), 9.0, 7).toFloat()
        val angle = 1.4f * gust + 0.45f * Noise.wander(m.time, 1.9, 80 + i).toFloat()
        rotate(angle, pivot = Offset(t.footX, t.footY)) { sheet(images.sway[i], t.sheet, f.of(l.inks(t.front))) }
    }
}

/**
 * The light on the lake under the sun or moon: a few slim slivers of paper, shorter and
 * thinner towards the far shore, each breathing a little on its own.
 */
private fun DrawScope.drawGlints(l: PaperLook, p: SkyPlaces, m: PaperMotion, d: Float, b: PaperBaked) {
    if (l.glintAlpha <= 0.01f) return
    if (!p.sunUp && !p.moonUp) return
    val x = if (p.sunUp) p.sunX else p.moonX
    val colour = Color(l.glint)
    for (k in 0 until GLINTS) {
        val f = k / (GLINTS - 1f)
        val y = PaperMotif.HORIZON + 4f + 44f * f * f + 5f * f
        val w = (12f + 44f * f) * (0.7f + 0.3f * Noise.wander(m.time, 3.1, 120 + k).toFloat())
        val h = 1.1f + 1.5f * f
        val a = (l.glintAlpha * (0.5f + 0.5f * Noise.wander(m.time, 1.7, 140 + k).toFloat()) * (1f - 0.3f * f)).coerceIn(0f, 1f)
        val cx = x + 5f * Noise.wander(m.time, 4.3, 160 + k).toFloat() + (if (k % 2 == 0) -3f else 3f) * f
        drawOval(colour, Offset(b.px(cx - w / 2), b.px(y)), Size(w * d, h * d), alpha = a)
    }
}

/** The moon: the dark disc a breath lighter than the sky, the lit part with its phase, its bright side towards the sun. */
private fun DrawScope.drawMoon(l: PaperLook, p: SkyPlaces, d: Float) {
    val r = MOON_RADIUS * d
    val cx = (p.moonX + PaperMotif.OVERSCAN) * d
    val cy = (p.moonY + PaperMotif.OVERSCAN) * d
    drawCircle(Color(l.moonDark), radius = r, center = Offset(cx, cy), alpha = l.discAlpha)
    val f = p.moonFraction.coerceIn(0f, 1f)
    // Turned so the lit limb faces the sun (its place on the sky, even below the horizon).
    val angle = Math.toDegrees(atan2((p.sunY - p.moonY).toDouble(), (p.sunX - p.moonX).toDouble())).toFloat()
    val path = Path().apply {
        // The bright limb: a half circle on the right; the terminator: half an ellipse.
        val k = 1f - 2f * f
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
        drawPath(path, Color(l.moonLit), alpha = l.discAlpha)
    }
}

private val STAR = Color(0xFFF3EEDF)
private const val GLINTS = 5

/** The sun's and the moon's size on the sky (dp radius): drawn large, as a print would. */
const val SUN_RADIUS = 15f
const val MOON_RADIUS = 14f
