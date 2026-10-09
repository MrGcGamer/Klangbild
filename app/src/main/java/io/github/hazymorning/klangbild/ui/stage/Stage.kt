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
import androidx.compose.ui.unit.em
import io.github.hazymorning.klangbild.color.CoverKind
import io.github.hazymorning.klangbild.color.ScenePalette
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.ui.Motion
import io.github.hazymorning.klangbild.ui.theme.Fonts
import io.github.hazymorning.klangbild.ui.theme.mona
import io.github.hazymorning.klangbild.ui.theme.rememberFigures
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
 * (pause, the "Wandlicht" setting: 0 = off, 1 = as designed, 1.45 = strong); [alpha] fades
 * the layer during a change.
 */
@Composable
fun GlowLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    level: () -> Float = { 1f },
) {
    val image = remember(scene) { scene.glow.asImageBitmap() }
    val filters = remember { HashMap<Int, ColorFilter?>() }
    Canvas(modifier.fillMaxSize()) {
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
 * through transparency; [scale] lets it settle during a change. A
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
 * The stage's typography: one family, sentence case; hierarchy from size, weight and three
 * levels of brightness. The title is large and light, the artist smaller but a little heavier
 * – the two balance each other; everything else is quiet. [width] narrows the whole block,
 * never a single line, when a very long title needs it.
 */
class StageStyles(palette: ScenePalette, val width: Float = 100f) {
    val ink = palette.ink.asColor()
    val muted = palette.inkMuted.asColor()
    val quiet = palette.inkQuiet.asColor()

    /** The title at one step of [titleLadder]; Japanese, Chinese and Korean a little smaller (their characters stand taller than capitals). */
    fun title(step: TitleStep, cjk: Boolean = false): TextStyle {
        val size = if (cjk) step.size * CJK_SCALE else step.size
        return mona(
            size = size,
            weight = step.weight,
            color = ink,
            lineHeight = size * step.leading + if (cjk) 6f else 0f,
            tracking = if (!cjk && size >= 40f) -0.005f else 0f,
            width = width,
        ).merge(TextStyle(lineBreak = LineBreak.Heading, textDirection = TextDirection.Content, textAlign = TextAlign.Left))
    }

    /** The version under the title ("Radio Edit"): a quiet subtitle at 60 % of the title's size. */
    fun version(titleSize: Float): TextStyle = mona((titleSize * 0.6f).coerceIn(17f, 26f), 440, quiet, width = width).text()

    val work = mona(17f, 480, quiet, lineHeight = 23f, width = width).text()
    val artist = mona(21f, 520, muted, lineHeight = 27f, width = width).text()
    val artistSmall = mona(19f, 520, muted, lineHeight = 25f, width = width).text()
    val album = mona(17f, 420, quiet, lineHeight = 23f, tracking = 0.005f, width = width).text()

    /** Times, status, station: small and calm, always at normal width. */
    val label = mona(15f, 500, quiet, lineHeight = 20f, tracking = 0.01f)
    val next = mona(15f, 440, quiet, lineHeight = 20f).merge(TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left))

    /** "Als Nächstes" differs from what follows by weight alone. */
    val nextLead = SpanStyle(fontWeight = FontWeight(560))

    private fun TextStyle.text() = merge(TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left))

    companion object {
        const val CJK_SCALE = 0.88f

        /** The whole block's width when a title only fits narrower. */
        const val NARROW = 87.5f
    }
}

/** One size of the title: [size] sp, at most [lines] lines; [narrow] sets the whole block narrower. */
class TitleStep(val size: Float, val lines: Int, val weight: Int, val leading: Float, val narrow: Boolean = false)

/**
 * Title sizes from large to small: two lines at display size, then three lines a little
 * smaller and (slightly) heavier so they stay as strong. Only when three lines at the smallest
 * size are not enough does the whole block become narrower.
 */
val titleLadder = listOf(
    TitleStep(44f, 2, 440, 1.06f),
    TitleStep(40f, 2, 440, 1.06f),
    TitleStep(34f, 3, 450, 1.1f),
    TitleStep(30f, 3, 450, 1.1f),
    TitleStep(26f, 3, 460, 1.12f),
    TitleStep(26f, 3, 460, 1.12f, narrow = true),
    TitleStep(24f, 4, 460, 1.12f, narrow = true),
)

/** Vertical rhythm of the text block: space from a line's baseline to the next one's cap height (dp). */
private object Gaps {
    const val WORK = 14f
    const val VERSION = 14f
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

/** The measured decisions for the text block: title step and breaks, the artists fitted onto one line … */
class TextPlan(
    val title: AnnotatedString,
    val titleStyle: TextStyle,
    val titleLines: Int,
    /** The styles of the block's lines (narrower when the title needs it). */
    val styles: StageStyles,
    /** "Radio Edit" under the title. */
    val version: String?,
    val versionStyle: TextStyle,
    val artists: String?,
    val artistStyle: TextStyle,
    /** "Album · 2024", or only the album when both don't fit. */
    val album: String?,
    /** What comes next – title and artist, or only the title when both don't fit. */
    val next: String?,
)

/** Measures the block once per model. */
@Composable
fun rememberTextPlan(model: StageModel): TextPlan {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val localDensity = LocalDensity.current
    return remember(model.text, model.year, model.next, model.scene.layout, model.night, model.scene.palette, model.progress, model.line, model.durationMs == null, localDensity) {
        planText(model, measurer, localDensity)
    }
}

private fun planText(
    model: StageModel,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
): TextPlan {
    val t = model.text
    val layout = model.scene.layout
    val d = density.density
    val colW = (layout.columnWidth * d).roundToInt()
    val normal = StageStyles(model.scene.palette)
    val narrow = StageStyles(model.scene.palette, StageStyles.NARROW)
    val cjk = isMostlyCjk(t.title)
    val version = t.version?.takeUnless { model.night }

    val title = EmojiText.annotate(t.title, dimTo = normal.ink.copy(alpha = 0.62f))

    // Everything but the title, from the title's last baseline down (or the work line above
    // it), to know how much height the title may take.
    fun cap(style: TextStyle) = style.fontSize.value * Fonts.CAP_HEIGHT * d
    fun linesHeight(text: String, style: TextStyle, lines: Int): Float {
        val r = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = colW), maxLines = lines)
        return r.getLineBaseline(r.lineCount - 1) - r.getLineBaseline(0)
    }
    fun budget(step: TitleStep): Float {
        var rest = 0f
        if (t.work != null) rest += cap(normal.work) + linesHeight(t.work, normal.work, 2) + Gaps.WORK * d
        if (version != null) rest += Gaps.VERSION * d + cap(normal.version(step.size))
        rest += Gaps.ARTIST * d + cap(normal.artist)
        if (!model.night && (t.album != null || model.year != null)) rest += Gaps.ALBUM * d + cap(normal.album)
        if (!model.night && (model.line != null || (model.progress && model.durationMs != null))) {
            rest += Gaps.BOTTOM * d + (4 + 10) * d + cap(normal.label)
        }
        return layout.art * BLOCK_SHARE * d - rest
    }

    val choice = pickTitleSize(title, { step -> (if (step.narrow) narrow else normal).title(step, cjk) }, measurer, colW, ::budget, d)
        ?: TitleChoice(titleLadder.last(), title)
    val block = if (choice.step.narrow) narrow else normal

    // Artists on one line: the most complete form that fits – at the artist's size, then a
    // step smaller, before anything is cut.
    val options = t.artistOptions()
    fun fits(option: String, style: TextStyle) =
        measurer.measure(AnnotatedString(option), style, maxLines = 1, softWrap = false).size.width <= colW
    var artistStyle = block.artist
    var artists: String? = null
    for (style in listOf(block.artist, block.artistSmall)) {
        artists = options.firstOrNull { fits(it, style) }
        if (artists != null) {
            artistStyle = style
            break
        }
    }
    if (artists == null && options.isNotEmpty()) {
        artists = options.last()
        artistStyle = block.artistSmall
    }
    // The album with its year; the year goes first when both don't fit.
    val album = if (model.night) null else {
        val full = listOfNotNull(t.album, model.year?.toString()).joinToString(TimeLine.SEPARATOR).ifEmpty { null }
        if (full != null && t.album != null && model.year != null && !fits(full, block.album)) t.album else full
    }
    // What comes next: title and artist, or the title alone rather than a cut-off artist.
    val next = if (model.night) null else model.next?.let { full ->
        if (model.nextTitle == null || measurer.measure(nextText(full, normal), normal.next, maxLines = 1, softWrap = false).size.width <= colW) {
            full
        } else {
            model.nextTitle
        }
    }
    return TextPlan(
        title = choice.text,
        titleStyle = block.title(choice.step, cjk),
        titleLines = choice.step.lines,
        styles = block,
        version = version,
        versionStyle = block.version(choice.step.size),
        artists = artists,
        artistStyle = artistStyle,
        album = album,
        next = next,
    )
}

/** The lines of the text block, for transitions that treat them one by one. */
enum class TextLine { WORK, TITLE, VERSION, ARTIST, ALBUM, BOTTOM, NEXT }

/**
 * Large type has more room before its first stem than small type; left as it is, a title
 * looks indented against the lines below it. Pulled left by the difference (dp).
 */
private fun opticalIndent(size: Float): Float = -(0.065f * (size - 15f)).coerceAtLeast(0f)

/**
 * The text block beside the artwork, centred on it from the first line's cap height to the
 * last baseline: (work), title, (version), artist, album · year, then the progress bar (or the
 * station, or a notice). What comes next sits apart on the artwork's bottom edge. [alpha] and
 * [rise] animate a change line by line; [dim] darkens the text while paused.
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
    val block = plan.styles
    val localDensity = LocalDensity.current
    val colW = (layout.columnWidth * localDensity.density).roundToInt()
    val playing = model.status == PlayerStatus.PLAYING || model.status == PlayerStatus.BUFFERING

    fun line(which: TextLine): Modifier = Modifier.graphicsLayer {
        this.alpha = alpha(which).coerceIn(0f, 1f)
        translationY = (rise(which) * density).roundToInt().toFloat()
    }
    val showBar = !night && model.line == null && model.progress && model.durationMs != null
    val bottomLine = if (night) null else model.line

    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = dim().coerceIn(0f, 1f) },
        content = {
            // 0: work line
            if (t.work != null) BasicText(t.work, line(TextLine.WORK), style = block.work, maxLines = 2, overflow = TextOverflow.Ellipsis) else Box(Modifier)
            // 1: title
            BasicText(plan.title, line(TextLine.TITLE), style = plan.titleStyle, maxLines = plan.titleLines, overflow = TextOverflow.Ellipsis)
            // 2: version
            if (plan.version != null) BasicText(plan.version, line(TextLine.VERSION), style = plan.versionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) else Box(Modifier)
            // 3: artist
            if (plan.artists != null) {
                BasicText(plan.artists, line(TextLine.ARTIST), style = plan.artistStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 4: album · year
            if (plan.album != null) BasicText(plan.album, line(TextLine.ALBUM), style = block.album, maxLines = 1, overflow = TextOverflow.Ellipsis) else Box(Modifier)
            // 5: progress bar, station or notice – changes cross-fade
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
                        colors = ProgressColors(track = styles.ink.copy(alpha = 0.16f), fill = styles.muted, label = styles.quiet),
                        labelStyle = styles.label,
                    )
                    else -> BasicText(content, style = styles.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // 6: what comes next, on the artwork's bottom edge
            if (plan.next != null) NextLine(plan.next, styles, line(TextLine.NEXT)) else Box(Modifier)
        },
    ) { measurables, constraints ->
        val c = Constraints(maxWidth = colW)
        val cBottom = Constraints(minWidth = colW, maxWidth = colW)
        val work = measurables[0].measure(c)
        val title = measurables[1].measure(c)
        val version = measurables[2].measure(c)
        val artist = measurables[3].measure(c)
        val album = measurables[4].measure(c)
        val bottom = measurables[5].measure(cBottom)
        val nextLine = measurables[6].measure(c)
        fun px(dp: Float) = (dp * density).roundToInt()
        fun capOf(style: TextStyle) = (style.fontSize.toPx() * Fonts.CAP_HEIGHT).roundToInt()

        // Each part: where its cap height is (from its top), where its last baseline is, and
        // its optical indent.
        class Part(val p: Placeable, val capTop: Int, val baseline: Int, val gap: Int, val dx: Int = 0)
        val parts = ArrayList<Part>()
        fun text(p: Placeable, style: TextStyle, gap: Float) {
            if (p.height == 0) return
            val first = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
            val last = p[LastBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
            parts += Part(p, first - capOf(style), last, if (parts.isEmpty()) 0 else px(gap), px(opticalIndent(style.fontSize.value)))
        }
        text(work, block.work, 0f)
        text(title, plan.titleStyle, Gaps.WORK)
        text(version, plan.versionStyle, Gaps.VERSION)
        text(artist, plan.artistStyle, Gaps.ARTIST)
        text(album, block.album, Gaps.ALBUM)
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
            parts.forEachIndexed { i, part -> part.p.place(x + part.dx, top + capY[i] - part.capTop) }
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
    BasicText(nextText(text, styles), modifier, style = styles.next, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "Als Nächstes" in a heavier weight, 12 dp, then what comes next (isolated if right-to-left). */
private fun nextText(text: String, styles: StageStyles): AnnotatedString = buildAnnotatedString {
    withStyle(styles.nextLead) { append("Als Nächstes") }
    // A no-break space widened to about 12 dp.
    withStyle(SpanStyle(letterSpacing = 0.55.em)) { append(" ") }
    append(if (isRightToLeft(text)) "⁧$text⁩" else text)
}

/**
 * The status line at the top of the column: the time, then where the music plays and what
 * it is doing ("Wohnzimmer · Pausiert", or a passing volume) – cap height on the artwork's top
 * edge, on the column's left edge like everything else. The place cross-fades when it changes;
 * the clock just ticks, its figures in fixed cells.
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
    val figures = rememberFigures(styles.label)
    val clockText = status.clock
    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) },
        content = {
            if (clockText != null) BasicText(figures.of(clockText), style = styles.label.copy(color = styles.muted), maxLines = 1) else Box(Modifier)
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
 * given ([budget], from its first cap height to its last baseline); balanced line breaks. At
 * each step, a version that may only break after the dashes between a movement's sections is
 * tried first ("Presto – / Allegro assai", never "Allegro / assai"); if only the free version
 * fits, one step smaller is still worth it when the dash version fits there. Returns null if
 * nothing fits.
 */
private fun pickTitleSize(
    text: AnnotatedString,
    styleOf: (TitleStep) -> TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxW: Int,
    budget: (TitleStep) -> Float,
    density: Float,
): TitleChoice? {
    val base = glueShortWords(glueParentheses(text))
    val glued = glueBetweenDashes(base)
    fun height(r: TextLayoutResult): Float =
        r.layoutInput.style.fontSize.value * Fonts.CAP_HEIGHT * density + (r.getLineBaseline(r.lineCount - 1) - r.getLineBaseline(0))
    fun gluedFits(step: TitleStep): Boolean {
        if (glued == null) return false
        val g = measurer.measure(glued, style = styleOf(step), constraints = Constraints(maxWidth = maxW), maxLines = step.lines + 1)
        return g.lineCount <= step.lines && height(g) <= budget(step) && breaksOnlyAfterDashes(g)
    }
    for ((i, step) in titleLadder.withIndex()) {
        if (gluedFits(step)) return TitleChoice(step, glued!!)
        val r = measurer.measure(base, style = styleOf(step), constraints = Constraints(maxWidth = maxW), maxLines = step.lines + 1)
        if (r.lineCount <= step.lines && height(r) <= budget(step) && !breaksInsideWord(r)) {
            val smaller = titleLadder.getOrNull(i + 1)
            if (smaller != null && gluedFits(smaller)) return TitleChoice(smaller, glued!!)
            return TitleChoice(step, base)
        }
    }
    return null
}

/**
 * A short word (one to three letters: "im", "de", "der", "You") stays with the word after it,
 * so no line of a title ends on it: "Spiegel / im Spiegel", never "Spiegel im / Spiegel".
 */
private fun glueShortWords(text: AnnotatedString): AnnotatedString {
    val s = text.text
    val chars = s.toCharArray()
    var start = 0
    var changed = false
    for (i in chars.indices) {
        if (chars[i] != ' ') continue
        val word = s.substring(start, i)
        if (word.length in 1..3 && word.all { it.isLetter() } && i + 1 < chars.size && chars[i + 1].isLetterOrDigit()) {
            chars[i] = '\u00A0'
            changed = true
        }
        start = i + 1
    }
    return if (!changed) text else AnnotatedString(String(chars), spanStyles = text.spanStyles, paragraphStyles = text.paragraphStyles)
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
