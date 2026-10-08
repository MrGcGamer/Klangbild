package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.render.SceneBuilder
import io.github.hazymorning.klangbild.ui.theme.Fonts
import kotlin.math.roundToInt

/**
 * A complete, static Halo frame for one model. The animated stage composes the same
 * layers with transition and pause parameters.
 */
@Composable
fun HaloStage(model: StageModel, modifier: Modifier = Modifier, wallClockMs: () -> Long = System::currentTimeMillis) {
    Box(modifier.fillMaxSize()) {
        BaseWall(model.scene)
        HaloLightLayer(model.scene, sunAngle = { HaloLight.sunAngle(model.progress(), wallClockMs()) })
        HaloCoverLayer(model.scene)
        HaloTextLayer(model)
    }
}

/** The static wall colour under everything (also fills the edges while the stage drifts). */
@Composable
fun BaseWall(scene: ArtworkScene, modifier: Modifier = Modifier) {
    val color = scene.palette.base.asColor()
    Canvas(modifier.fillMaxSize()) { drawRect(color) }
}

/**
 * The wall glow and the wandering sun. [lightLevel] dims the light (pause, rest),
 * [alpha] fades the whole layer during transitions, [cooling] 0..1 shifts the sun towards
 * a dim amber like a filament that is switched off.
 */
@Composable
fun HaloLightLayer(
    scene: ArtworkScene,
    sunAngle: () -> Float,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    lightLevel: () -> Float = { 1f },
    cooling: () -> Float = { 0f },
) {
    val backdrop = remember(scene) { scene.backdrop.asImageBitmap() }
    val sprite = remember { SceneBuilder.sunSprite.asImageBitmap() }
    val sunColor = scene.sunTint.asColor()
    val amber = Color(0xFF6E3410)
    Canvas(modifier.fillMaxSize()) {
        val a = alpha().coerceIn(0f, 1f)
        if (a <= 0f) return@Canvas
        val level = lightLevel().coerceIn(0f, 1f)
        drawImage(
            backdrop,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = a * level,
            filterQuality = FilterQuality.Low,
        )
        // The sun: a tinted, additive sprite. Its position changes a few dp per minute.
        val rect = scene.coverRect
        val (cx, cy) = HaloLight.sunCenter(rect, sunAngle())
        val d = HaloLight.sunDiameter(rect) * density
        val tint = lerp(sunColor, amber.copy(alpha = 1f).let { scaleColor(it, 0.35f) }, cooling().coerceIn(0f, 1f))
        // The sun lights the wall around the cover, never the area behind it.
        clipRect(
            left = rect.left * density,
            top = rect.top * density,
            right = rect.right * density,
            bottom = rect.bottom * density,
            clipOp = ClipOp.Difference,
        ) {
            drawImage(
                sprite,
                dstOffset = IntOffset((cx * density - d / 2).roundToInt(), (cy * density - d / 2).roundToInt()),
                dstSize = IntSize(d.roundToInt(), d.roundToInt()),
                alpha = a * level,
                colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
                blendMode = BlendMode.Plus,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/**
 * The cover, drawn 1:1 at a whole-pixel position (the bitmap already has its exact display
 * size). A hairline inner edge gives it the presence of a print, also on a dark cover.
 * [dim] (pause) darkens it through the colour matrix rather than transparency, so the
 * light behind never shines through.
 */
@Composable
fun HaloCoverLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    dim: () -> Float = { 1f },
    scale: () -> Float = { 1f },
) {
    val image = remember(scene) { scene.cover.asImageBitmap() }
    val filters = remember(scene) { HashMap<Int, ColorFilter>() }
    val rect = scene.coverRect
    val edge = scene.palette.ink.asColor().copy(alpha = if (scene.analysis.meanL < 0.25f) 0.10f else 0.06f)
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha().coerceIn(0f, 1f)
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    rect.centerX / scene.screen.widthDp,
                    rect.centerY / scene.screen.heightDp,
                )
            },
    ) {
        // Dimming is quantised so an animation reuses a handful of colour filters.
        val step = (dim().coerceIn(0f, 1f) * 50f).roundToInt()
        val filter = filters.getOrPut(step) { coverFilter(scene.lighting, step / 50f) }
        val x = (rect.left * density).roundToInt()
        val y = (rect.top * density).roundToInt()
        drawImage(
            image,
            dstOffset = IntOffset(x, y),
            dstSize = IntSize(image.width, image.height),
            colorFilter = filter,
            filterQuality = FilterQuality.Low,
        )
        drawRect(
            edge,
            topLeft = Offset(x + 0.5f, y + 0.5f),
            size = Size(image.width - 1f, image.height - 1f),
            style = Stroke(width = 1f),
        )
    }
}

private val titleLadder = listOf(
    52.sp to 2, 42.sp to 3, 34.sp to 4, 29.sp to 4, 25.sp to 5,
)

/**
 * The text column on its fixed baselines: clock at the top, title growing upwards from
 * its baseline, the artist line fixed, album below, time line on the cover's bottom edge.
 */
@Composable
fun HaloTextLayer(
    model: StageModel,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    offsetY: () -> Float = { 0f },
) {
    val scene = model.scene
    val p = scene.palette
    val layout = scene.layout
    val t = model.text
    val night = model.night
    val measurer = rememberTextMeasurer(cacheSize = 16)

    val ink = p.ink.asColor()
    val muted = p.inkMuted.asColor()
    val faint = p.inkFaint.asColor()
    val latinTitle = isSpacingFriendly(t.title)

    val titleBase = TextStyle(
        fontFamily = Fonts.Display,
        fontWeight = FontWeight(340),
        color = ink,
        letterSpacing = if (latinTitle) (-0.012).em else 0.em,
        lineBreak = LineBreak.Heading,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    val workStyle = TextStyle(
        fontFamily = Fonts.Text,
        fontStyle = if (isSpacingFriendly(t.work.orEmpty())) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight(380),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        color = muted,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    val clockStyle = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
        letterSpacing = 0.2.em,
        color = faint,
        fontFeatureSettings = "tnum",
    )
    val artistStyle = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = muted,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    val albumStyle = TextStyle(
        fontFamily = Fonts.Text,
        fontStyle = if (isSpacingFriendly(t.album.orEmpty())) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight(380),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        color = faint,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )

    val localDensity = LocalDensity.current
    val colW = (layout.columnWidth * localDensity.density).roundToInt()
    val titleText = remember(t.title) { EmojiText.annotate(t.title) }
    val titleChoice = remember(t.title, t.work, colW, titleBase, localDensity) {
        val workH = t.work?.let {
            measurer.measure(AnnotatedString(it), workStyle, constraints = Constraints(maxWidth = colW), maxLines = 2).size.height
        } ?: 0
        val d = localDensity.density
        val clockBaseline = layout.clockTop * d + with(localDensity) { 12.sp.toPx() } * 0.70f
        val ceiling = clockBaseline + 34 * d + (if (workH > 0) workH + 8 * d else 0f)
        val maxH = (layout.titleBaseline * d - ceiling + 16 * d).roundToInt().coerceAtLeast(1)
        pickTitleSize(titleText, titleBase, measurer, colW, maxH)
    }
    val titleStyle = titleBase.merge(
        TextStyle(fontSize = titleChoice.first, lineHeight = (titleChoice.first.value * 1.04f).sp),
    )

    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha().coerceIn(0f, 1f)
                translationY = offsetY() * density
            },
        content = {
            // 0: clock
            if (model.clock != null && !night) BasicText(model.clock, style = clockStyle) else Box(Modifier)
            // 1: work line
            if (t.work != null) {
                BasicText(t.work, style = workStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 2: title
            BasicText(titleText, style = titleStyle, maxLines = titleChoice.second, overflow = TextOverflow.Ellipsis)
            // 3: artist
            if (t.artists != null) SpacedLabel(t.artists, artistStyle, maxLines = 2) else Box(Modifier)
            // 4: album / version
            val album = listOfNotNull(t.album, t.version).joinToString("  ·  ")
            if (album.isNotEmpty() && !night) {
                BasicText(album, style = albumStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 5: time line
            val line = model.timeLine
            if (line != null && !night) TimeLineText(line, faint) else Box(Modifier)
        },
    ) { measurables, constraints ->
        val c = Constraints(maxWidth = colW)
        val x = (layout.columnLeft * density).roundToInt()
        val clock = measurables[0].measure(c)
        val work = measurables[1].measure(c)
        val artist = measurables[3].measure(c)
        val album = measurables[4].measure(c)
        val timeLine = measurables[5].measure(c)

        val clockTop = (layout.clockTop * density).roundToInt()
        val clockCap = (12.sp.toPx() * 0.70f).roundToInt()
        val clockBaseline = clockTop + clockCap
        val workGap = (8 * density).roundToInt()
        val titleBaseline = (layout.titleBaseline * density).roundToInt()
        val title = measurables[2].measure(c)

        layout(constraints.maxWidth, constraints.maxHeight) {
            fun Placeable.baseline(first: Boolean): Int {
                val b = this[if (first) FirstBaseline else LastBaseline]
                return if (b == androidx.compose.ui.layout.AlignmentLine.Unspecified) height else b
            }
            if (clock.height > 0) clock.place(x, clockBaseline - clock.baseline(true))
            val titleTop = titleBaseline - title.baseline(false)
            title.place(x, titleTop)
            if (work.height > 0) work.place(x, titleTop - workGap - work.height)
            val artistBaseline = (layout.artistBaseline * density).roundToInt()
            if (artist.height > 0) artist.place(x, artistBaseline - artist.baseline(true))
            if (album.height > 0) {
                val artistLast = artistBaseline - artist.baseline(true) + artist.baseline(false)
                val albumBaseline = (if (artist.height > 0) artistLast else artistBaseline) + (layout.albumGap * density).roundToInt()
                album.place(x, albumBaseline - album.baseline(true))
            }
            if (timeLine.height > 0) {
                val b = (layout.timeLineBaseline * density).roundToInt()
                timeLine.place(x, b - timeLine.baseline(false))
            }
        }
    }
}

/**
 * The title at the largest size of the ladder that fits its line limit and the height it
 * is given; balanced line breaks. Ellipsis only as the very last resort.
 */
private fun pickTitleSize(
    text: AnnotatedString,
    base: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxW: Int,
    maxH: Int,
): Pair<TextUnit, Int> {
    for ((size, lines) in titleLadder) {
        val r = measurer.measure(
            text,
            style = base.merge(TextStyle(fontSize = size, lineHeight = (size.value * 1.04f).sp)),
            constraints = Constraints(maxWidth = maxW),
            maxLines = lines + 1,
        )
        if (r.lineCount <= lines && (maxH == Constraints.Infinity || r.size.height <= maxH) && !breaksInsideWord(r)) {
            return size to lines
        }
    }
    return titleLadder.last()
}

/** True if a line ends in the middle of a word (the breaker had no better option). */
private fun breaksInsideWord(r: androidx.compose.ui.text.TextLayoutResult): Boolean {
    val text = r.layoutInput.text.text
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i)
        if (end <= 0 || end >= text.length) continue
        val before = text[end - 1]
        val after = text[end]
        if (before.isLetterOrDigit() && after.isLetterOrDigit()) return true
    }
    return false
}

@Composable
private fun TimeLineText(line: TimeLine.Line, color: Color) {
    val label = line.label
    val friendly = isSpacingFriendly(label)
    val text = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontFamily = Fonts.Sans,
                fontWeight = FontWeight(500),
                fontSize = 12.sp,
                letterSpacing = if (line.figures) 0.12.em else if (friendly) 0.2.em else 0.em,
                fontFeatureSettings = "tnum",
            ),
        ) { append(if (friendly && !line.figures) label.uppercase() else label) }
        line.detail?.let { detail ->
            append("   ")
            withStyle(
                SpanStyle(
                    fontFamily = Fonts.Text,
                    fontStyle = if (isSpacingFriendly(detail)) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 16.sp,
                ),
            ) { append(detail) }
        }
    }
    BasicText(
        text,
        style = TextStyle(color = color, textDirection = TextDirection.Content, textAlign = TextAlign.Left),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

private fun scaleColor(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)
