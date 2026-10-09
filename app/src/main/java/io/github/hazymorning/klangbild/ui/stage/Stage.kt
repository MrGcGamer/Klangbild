package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.ScenePalette
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.ui.Motion
import io.github.hazymorning.klangbild.ui.theme.Fonts
import io.github.hazymorning.klangbild.ui.theme.mona
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A complete, static stage for one model. The animated stage ([StageTheatre]) composes the
 * same layers with transition and pause parameters.
 */
@Composable
fun Stage(model: StageModel, modifier: Modifier = Modifier) {
    val plan = rememberTextPlan(model)
    Box(modifier.fillMaxSize()) {
        BaseWall()
        GlowLayer(model.scene)
        ArtworkLayer(model.scene)
        TextLayer(model, plan = plan)
        StatusLayer(model)
    }
}

/** The wall under everything: black (on an OLED the pixels are simply off). */
@Composable
fun BaseWall(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawRect(Color.Black) }
}

/**
 * The artwork's light on the wall ([io.github.hazymorning.klangbild.render.WallGlow]): one
 * static bitmap, scaled softly to the screen. [level] is its brightness in linear light
 * (pause, the "Licht" setting: 0 = off, 1 = as designed, 1.45 = strong); [alpha] fades the
 * layer during a change; [scale] follows the artwork when it recedes on pause.
 */
@Composable
fun GlowLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    level: () -> Float = { 1f },
    scale: () -> Float = { 1f },
) {
    val image = remember(scene) { scene.glow.asImageBitmap() }
    val filters = remember { HashMap<Int, ColorFilter?>() }
    val rect = scene.coverRect
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(rect.centerX / scene.screen.widthDp, rect.centerY / scene.screen.heightDp)
            },
    ) {
        val a = alpha().coerceIn(0f, 1f)
        val l = level().coerceIn(0f, 2f)
        if (a <= 0.002f || l <= 0.002f) return@Canvas
        // The bitmap is sRGB: a factor k in linear light is k^(1/2.2) on its values.
        val step = (l.pow(1f / 2.2f) * 40f).roundToInt()
        val filter = filters.getOrPut(step) { if (step == 40) null else scaleFilter(step / 40f) }
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = a,
            colorFilter = filter,
            filterQuality = FilterQuality.Low,
        )
    }
}

private fun scaleFilter(k: Float) = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
)

/**
 * The artwork, drawn 1:1 at a whole-pixel position (the bitmap already has its exact display
 * size) with softly rounded corners. [dim] (pause) darkens it through the colour matrix, not
 * through transparency; [scale] lets it settle during a change and recede on pause. A
 * hairline of light inside a dark artwork's edge keeps it from dissolving into the black wall.
 */
@Composable
fun ArtworkLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    dim: () -> Float = { 1f },
    scale: () -> Float = { 1f },
) {
    val shader = remember(scene) {
        android.graphics.BitmapShader(scene.cover, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    }
    val filters = remember(scene) { HashMap<Int, ColorFilter>() }
    val rect = scene.coverRect
    val edge = remember(scene) { edgeLine(scene) }
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha().coerceIn(0f, 1f)
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(rect.centerX / scene.screen.widthDp, rect.centerY / scene.screen.heightDp)
            },
    ) {
        val step = (dim().coerceIn(0f, 1f) * 50f).roundToInt()
        val filter = filters.getOrPut(step) { coverFilter(scene.lighting, step / 50f) }
        val x = (rect.left * density).roundToInt().toFloat()
        val y = (rect.top * density).roundToInt().toFloat()
        val w = scene.cover.width.toFloat()
        val h = scene.cover.height.toFloat()
        val corner = CornerRadius(scene.corner * density)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(x, y) })
        drawRoundRect(ShaderBrush(shader), Offset(x, y), Size(w, h), corner, colorFilter = filter)
        if (edge != null) {
            val line = (density / 2f).coerceAtLeast(1f)
            drawRoundRect(
                edge,
                Offset(x + line / 2f, y + line / 2f),
                Size(w - line, h - line),
                CornerRadius((scene.corner * density - line / 2f).coerceAtLeast(0f)),
                style = Stroke(width = line),
            )
        }
    }
}

/** A faint inner hairline for dark square artwork (white at 7 %); none for light artwork or a plate. */
private fun edgeLine(scene: ArtworkScene): Color? =
    if (!scene.shape.round && scene.palette.kind == CoverKind.DARK) Color.White.copy(alpha = 0.07f) else null

/**
 * The stage's typography: one family, sentence case; hierarchy from size, weight and
 * opacity. The title is large and light, the artist smaller but a little heavier – the two
 * balance each other; everything else is quiet.
 */
class StageStyles(palette: ScenePalette) {
    val ink = palette.ink.asColor()
    val muted = palette.inkMuted.asColor()
    val faint = palette.inkFaint.asColor()
    val quiet = palette.inkQuiet.asColor()
    val hush = palette.inkHush.asColor()

    /** The title at one step of [titleLadder]. */
    fun title(step: TitleStep): TextStyle = mona(
        size = step.size,
        weight = step.weight,
        color = ink,
        lineHeight = step.size * step.leading,
        tracking = if (step.size >= 40f) -0.005f else 0f,
        width = step.width,
    ).merge(TextStyle(lineBreak = LineBreak.Heading, textDirection = TextDirection.Content, textAlign = TextAlign.Left))

    /** A version note after the title ("Radio Edit"): lighter and quieter. */
    val version = SpanStyle(fontWeight = FontWeight(400), color = faint)

    val work = mona(17f, 480, faint, lineHeight = 23f).text()
    val artist = mona(21f, 520, muted, lineHeight = 27f).text()
    val artistSmall = mona(19f, 520, muted, lineHeight = 25f).text()
    val artistNarrow = mona(19f, 520, muted, lineHeight = 25f, width = 88f).text()
    val album = mona(17f, 420, faint, lineHeight = 23f, tracking = 0.005f).text()

    /** Times, status, station: small and calm. */
    val label = mona(15f, 500, quiet, lineHeight = 20f, tracking = 0.01f)
    val next = mona(15f, 420, faint, lineHeight = 20f).merge(TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left))
    val nextLead = SpanStyle(fontWeight = FontWeight(520), color = quiet)

    private fun TextStyle.text() = merge(TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left))
}

/** One size of the title: [size] sp, at most [lines] lines, [width] of the font's width axis. */
class TitleStep(val size: Float, val lines: Int, val weight: Int, val leading: Float, val width: Float = 100f)

/**
 * Title sizes from large to small: two lines at display size, then three lines a little
 * smaller and (slightly) heavier so they stay as strong; very long titles narrow the
 * letters before they shrink further.
 */
val titleLadder = listOf(
    TitleStep(44f, 2, 440, 1.06f),
    TitleStep(40f, 2, 440, 1.06f),
    TitleStep(36f, 3, 450, 1.1f),
    TitleStep(32f, 3, 450, 1.1f),
    TitleStep(29f, 3, 460, 1.1f),
    TitleStep(26f, 3, 460, 1.12f),
    TitleStep(26f, 3, 460, 1.12f, width = 90f),
    TitleStep(26f, 3, 460, 1.12f, width = 82f),
    TitleStep(24f, 4, 460, 1.12f, width = 82f),
)

/** Vertical rhythm of the text block: space from a line's baseline to the next one's cap height (dp). */
private object Gaps {
    const val WORK = 14f
    const val ARTIST = 16f
    const val ALBUM = 10f
    const val BOTTOM = 26f
}

/**
 * The text block may take at most this share of the artwork's height (from the first cap
 * height to the last baseline): the artwork is the heaviest element and stays clearly the
 * tallest.
 */
const val BLOCK_SHARE = 0.62f

/** The measured decisions for the text block: title step and breaks, the artists fitted onto one line. */
class TextPlan(
    val title: AnnotatedString,
    val titleStyle: TextStyle,
    val titleLines: Int,
    val artists: String?,
    val artistStyle: TextStyle,
    /** "Album · 2024", or only the album when both don't fit. */
    val album: String?,
)

/** Measures the block once per model. */
@Composable
fun rememberTextPlan(model: StageModel): TextPlan {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val localDensity = LocalDensity.current
    return remember(model.text, model.year, model.scene.layout, model.night, model.scene.palette, model.progress, model.line, model.durationMs == null, localDensity) {
        planText(model, StageStyles(model.scene.palette), measurer, localDensity)
    }
}

private fun planText(
    model: StageModel,
    styles: StageStyles,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
): TextPlan {
    val t = model.text
    val layout = model.scene.layout
    val d = density.density
    val colW = (layout.columnWidth * d).roundToInt()

    val title = buildAnnotatedString {
        append(EmojiText.annotate(t.title, dimTo = styles.ink.copy(alpha = 0.62f)))
        // A version note ("Radio Edit") belongs to the title: lighter, after it – kept in
        // one piece, and when it wraps it starts its line flush (an ordinary space before it).
        t.version?.let { v ->
            append(" ")
            withStyle(styles.version) { append(v.replace(' ', '\u00A0')) }
        }
    }

    // Everything but the title, from the title's last baseline down (or the work line above
    // it), to know how much height the title may take.
    fun cap(style: TextStyle) = style.fontSize.value * Fonts.CAP_HEIGHT * d
    fun linesHeight(text: String, style: TextStyle, lines: Int): Float {
        val r = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = colW), maxLines = lines)
        return r.getLineBaseline(r.lineCount - 1) - r.getLineBaseline(0)
    }
    var rest = 0f
    if (t.work != null) rest += cap(styles.work) + linesHeight(t.work, styles.work, 2) + Gaps.WORK * d
    rest += Gaps.ARTIST * d + cap(styles.artist)
    if (!model.night && (t.album != null || model.year != null)) rest += Gaps.ALBUM * d + cap(styles.album)
    if (!model.night && (model.line != null || (model.progress && model.durationMs != null))) {
        rest += Gaps.BOTTOM * d + (4 + 10) * d + cap(styles.label)
    }
    val budget = layout.art * BLOCK_SHARE * d - rest

    val choice = pickTitleSize(title, styles, measurer, colW, budget, d)
        ?: TitleChoice(titleLadder.last(), title)

    // Artists on one line: the most complete form that fits – at the artist's size, then a
    // step smaller, then a little narrower, before anything is cut.
    val options = t.artistOptions()
    fun fits(option: String, style: TextStyle) =
        measurer.measure(AnnotatedString(option), style, maxLines = 1, softWrap = false).size.width <= colW
    var artistStyle = styles.artist
    var artists: String? = null
    for (style in listOf(styles.artist, styles.artistSmall, styles.artistNarrow)) {
        artists = options.firstOrNull { fits(it, style) }
        if (artists != null) {
            artistStyle = style
            break
        }
    }
    if (artists == null && options.isNotEmpty()) {
        artists = options.last()
        artistStyle = styles.artistNarrow
    }
    // The album with its year; the year goes first when both don't fit.
    val album = if (model.night) null else {
        val full = listOfNotNull(t.album, model.year?.toString()).joinToString(TimeLine.SEPARATOR).ifEmpty { null }
        if (full != null && t.album != null && model.year != null && !fits(full, styles.album)) t.album else full
    }
    return TextPlan(
        title = choice.text,
        titleStyle = styles.title(choice.step),
        titleLines = choice.step.lines,
        artists = artists,
        artistStyle = artistStyle,
        album = album,
    )
}

/** The lines of the text block, for transitions that treat them one by one. */
enum class TextLine { WORK, TITLE, ARTIST, ALBUM, BOTTOM, NEXT }

/**
 * The text block beside the artwork, centred on it from the first line's cap height to the
 * last baseline: (work), title, artist, album · year, then the progress bar (or the station,
 * or a notice). What comes next sits apart on the artwork's bottom edge. [alpha] and [rise]
 * animate a change line by line; [dim] darkens the text while paused.
 */
@Composable
fun TextLayer(
    model: StageModel,
    modifier: Modifier = Modifier,
    plan: TextPlan = rememberTextPlan(model),
    alpha: (TextLine) -> Float = { 1f },
    rise: (TextLine) -> Float = { 0f },
    dim: () -> Float = { 1f },
) {
    val layout = model.scene.layout
    val t = model.text
    val night = model.night
    val styles = remember(model.scene.palette) { StageStyles(model.scene.palette) }
    val localDensity = LocalDensity.current
    val colW = (layout.columnWidth * localDensity.density).roundToInt()
    val playing = model.status == PlayerStatus.PLAYING || model.status == PlayerStatus.BUFFERING

    fun line(which: TextLine): Modifier = Modifier.graphicsLayer {
        this.alpha = alpha(which).coerceIn(0f, 1f)
        translationY = (rise(which) * density).roundToInt().toFloat()
    }
    val albumText = plan.album
    val showBar = !night && model.line == null && model.progress && model.durationMs != null
    val bottomLine = if (night) null else model.line
    val next = if (night) null else model.next

    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = dim().coerceIn(0f, 1f) },
        content = {
            // 0: work line
            if (t.work != null) BasicText(t.work, line(TextLine.WORK), style = styles.work, maxLines = 2, overflow = TextOverflow.Ellipsis) else Box(Modifier)
            // 1: title
            BasicText(plan.title, line(TextLine.TITLE), style = plan.titleStyle, maxLines = plan.titleLines, overflow = TextOverflow.Ellipsis)
            // 2: artist
            if (plan.artists != null) {
                BasicText(plan.artists, line(TextLine.ARTIST), style = plan.artistStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 3: album · year
            if (albumText != null) BasicText(albumText, line(TextLine.ALBUM), style = styles.album, maxLines = 1, overflow = TextOverflow.Ellipsis) else Box(Modifier)
            // 4: progress bar, station or notice – changes cross-fade
            Crossfade(
                targetState = when {
                    bottomLine != null -> bottomLine
                    showBar -> BAR
                    else -> null
                },
                modifier = line(TextLine.BOTTOM),
                animationSpec = tween(Motion.LINE_FADE_MS, easing = Motion.Dimmer),
                label = "bottom-line",
            ) { content ->
                when (content) {
                    null -> Box(Modifier)
                    BAR -> ProgressBar(
                        durationMs = model.durationMs ?: 1L,
                        positionMs = model.positionMs,
                        playing = playing,
                        colors = ProgressColors(track = styles.ink.copy(alpha = 0.2f), fill = styles.ink.copy(alpha = 0.9f), label = styles.quiet),
                        labelStyle = styles.label,
                    )
                    else -> BasicText(content, style = styles.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // 5: what comes next, on the artwork's bottom edge
            if (next != null) NextLine(next, styles, line(TextLine.NEXT)) else Box(Modifier)
        },
    ) { measurables, constraints ->
        val c = Constraints(maxWidth = colW)
        val cBottom = Constraints(minWidth = colW, maxWidth = colW)
        val work = measurables[0].measure(c)
        val title = measurables[1].measure(c)
        val artist = measurables[2].measure(c)
        val album = measurables[3].measure(c)
        val bottom = measurables[4].measure(cBottom)
        val nextLine = measurables[5].measure(c)
        fun px(dp: Float) = (dp * density).roundToInt()
        fun capOf(style: TextStyle) = (style.fontSize.toPx() * Fonts.CAP_HEIGHT).roundToInt()

        // Each part: where its cap height is (from its top) and where its last baseline is.
        class Part(val p: Placeable, val capTop: Int, val baseline: Int, val gap: Int)
        val parts = ArrayList<Part>()
        fun text(p: Placeable, style: TextStyle, gap: Float) {
            if (p.height == 0) return
            val first = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
            val last = p[LastBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
            parts += Part(p, first - capOf(style), last, if (parts.isEmpty()) 0 else px(gap))
        }
        text(work, styles.work, 0f)
        text(title, plan.titleStyle, Gaps.WORK)
        text(artist, plan.artistStyle, Gaps.ARTIST)
        text(album, styles.album, Gaps.ALBUM)
        if (bottom.height > 0) {
            // The bar block measures from the bar's top to the labels' baseline; a line of
            // text in its place is aligned by its own cap height.
            val isText = bottomLine != null
            val first = if (isText) bottom[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: bottom.height else 0
            parts += Part(
                bottom,
                capTop = if (isText) first - capOf(styles.label) else 0,
                baseline = if (isText) first else bottom.height,
                gap = if (parts.isEmpty()) 0 else px(Gaps.BOTTOM),
            )
        }
        // Positions of each cap height, relative to the first one.
        var y = 0
        val capY = IntArray(parts.size)
        parts.forEachIndexed { i, part ->
            if (i > 0) {
                val prev = parts[i - 1]
                y = capY[i - 1] - prev.capTop + prev.baseline + part.gap
            }
            capY[i] = y
        }
        val blockH = parts.lastOrNull()?.let { capY.last() - it.capTop + it.baseline } ?: 0
        val x = (layout.columnLeft * density).roundToInt()
        val top = (layout.textCenter * density).roundToInt() - blockH / 2
        val footer = (layout.footerBaseline * density).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            parts.forEachIndexed { i, part -> part.p.place(x, top + capY[i] - part.capTop) }
            if (nextLine.height > 0) {
                val b = nextLine[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: nextLine.height
                nextLine.place(x, footer - b)
            }
        }
    }
}

private const val BAR = "\u0000bar"

@Composable
private fun NextLine(text: String, styles: StageStyles, modifier: Modifier) {
    val annotated = buildAnnotatedString {
        withStyle(styles.nextLead) { append("Als Nächstes") }
        append("  ")
        append(if (isRightToLeft(text)) "⁧$text⁩" else text)
    }
    BasicText(annotated, modifier, style = styles.next, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The status line at the top of the column: the time, then where the music plays (or
 * "Pausiert", or a passing volume) – cap height on the artwork's top edge, on the column's
 * left edge like everything else. The place cross-fades when it changes; the clock just
 * ticks.
 */
@Composable
fun StatusLayer(
    model: StageModel,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
) {
    val status = model.statusLine
    if (status == null || status.isEmpty || model.night) return
    val layout = model.scene.layout
    val styles = remember(model.scene.palette) { StageStyles(model.scene.palette) }
    val clockText = status.clock
    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) },
        content = {
            if (clockText != null) BasicText(clockText, style = styles.label.copy(color = styles.muted), maxLines = 1) else Box(Modifier)
            Crossfade(
                targetState = status.lead,
                animationSpec = tween(Motion.LINE_FADE_MS, easing = Motion.Dimmer),
                label = "status-lead",
            ) { lead ->
                if (lead != null) {
                    BasicText(
                        if (clockText != null) TimeLine.SEPARATOR.trimStart() + lead else lead,
                        style = styles.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Box(Modifier)
                }
            }
        },
    ) { measurables, constraints ->
        val clock = measurables[0].measure(Constraints())
        val space = if (clock.width > 0) (styles.label.fontSize.toPx() * 0.26f).roundToInt() else 0
        val lead = measurables[1].measure(Constraints(maxWidth = ((layout.columnWidth * density).roundToInt() - clock.width - space).coerceAtLeast(0)))
        val left = (layout.columnLeft * density).roundToInt()
        fun Placeable.first(): Int = this[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: height
        // Cap height on the artwork's top edge.
        val baseline = (layout.statusTop * density).roundToInt() + (styles.label.fontSize.toPx() * Fonts.CAP_HEIGHT).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (clock.height > 0) clock.place(left, baseline - clock.first())
            if (lead.height > 0) lead.place(left + clock.width + space, baseline - lead.first())
        }
    }
}

private class TitleChoice(val step: TitleStep, val text: AnnotatedString)

/**
 * The title at the largest step of the ladder that fits its line limit and the height it is
 * given (from its first cap height to its last baseline); balanced line breaks. At each
 * step, a version that may only break after the dashes between a movement's sections is
 * tried first ("Presto – / Allegro assai", never "Allegro / assai"); if only the free version
 * fits, one step smaller is still worth it when the dash version fits there. Returns null if
 * nothing fits.
 */
private fun pickTitleSize(
    text: AnnotatedString,
    styles: StageStyles,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxW: Int,
    maxH: Float,
    density: Float,
): TitleChoice? {
    val base = glueParentheses(text)
    val glued = glueBetweenDashes(base)
    fun height(r: TextLayoutResult, step: TitleStep): Float =
        step.size * Fonts.CAP_HEIGHT * density + (r.getLineBaseline(r.lineCount - 1) - r.getLineBaseline(0))
    fun gluedFits(step: TitleStep): Boolean {
        if (glued == null) return false
        val g = measurer.measure(glued, style = styles.title(step), constraints = Constraints(maxWidth = maxW), maxLines = step.lines + 1)
        return g.lineCount <= step.lines && height(g, step) <= maxH && breaksOnlyAfterDashes(g)
    }
    for ((i, step) in titleLadder.withIndex()) {
        if (gluedFits(step)) return TitleChoice(step, glued!!)
        val r = measurer.measure(base, style = styles.title(step), constraints = Constraints(maxWidth = maxW), maxLines = step.lines + 1)
        if (r.lineCount <= step.lines && height(r, step) <= maxH && !breaksInsideWord(r)) {
            val smaller = titleLadder.getOrNull(i + 1)
            if (smaller != null && gluedFits(smaller)) return TitleChoice(smaller, glued!!)
            return TitleChoice(step, base)
        }
    }
    return null
}

/** Short parentheses never break inside: "(alla marcia)" stays one piece. */
private fun glueParentheses(text: AnnotatedString): AnnotatedString {
    val s = text.text
    if (!s.contains('(')) return text
    val chars = s.toCharArray()
    var open = -1
    for (i in chars.indices) {
        when (chars[i]) {
            '(' -> open = i
            ')' -> {
                if (open >= 0 && i - open <= 28) for (j in open until i) if (chars[j] == ' ') chars[j] = ' '
                open = -1
            }
        }
    }
    return AnnotatedString(String(chars), spanStyles = text.spanStyles, paragraphStyles = text.paragraphStyles)
}

/** The title with every space that is not right after a dash made non-breaking; null without dashes. */
private fun glueBetweenDashes(text: AnnotatedString): AnnotatedString? {
    val s = text.text
    if (!s.contains(" – ")) return null
    val chars = s.toCharArray()
    for (i in chars.indices) {
        if (chars[i] == ' ' && !(i > 0 && chars[i - 1] == '–')) chars[i] = ' '
    }
    return AnnotatedString(String(chars), spanStyles = text.spanStyles, paragraphStyles = text.paragraphStyles)
}

/** True if every line but the last ends right after "– ". */
private fun breaksOnlyAfterDashes(r: TextLayoutResult): Boolean {
    val text = r.layoutInput.text.text
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i)
        if (end < 2 || text[end - 1] != ' ' || text[end - 2] != '–') return false
    }
    return true
}

/** True if a line ends in the middle of a word (the breaker had no better option). */
private fun breaksInsideWord(r: TextLayoutResult): Boolean {
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

/** True if most letters of [text] are Chinese, Japanese or Korean (no capitals, full-width). */
fun isMostlyCjk(text: String): Boolean {
    var cjk = 0
    var letters = 0
    for (ch in text) {
        if (!ch.isLetter()) continue
        letters++
        val c = ch.code
        if (c in 0x3040..0x30FF || c in 0x3400..0x4DBF || c in 0x4E00..0x9FFF || c in 0xAC00..0xD7AF || c in 0xF900..0xFAFF) cjk++
    }
    return letters > 0 && cjk * 2 >= letters
}

/** True if [text] contains letters of a right-to-left script. */
fun isRightToLeft(text: String): Boolean = text.any {
    val d = Character.getDirectionality(it)
    d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}
