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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ShaderBrush
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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import io.github.hazymorning.klangbild.render.HaloLight
import io.github.hazymorning.klangbild.ui.Motion
import io.github.hazymorning.klangbild.ui.theme.Fonts
import kotlin.math.roundToInt

/**
 * A complete, static Halo frame for one model. The animated stage composes the same
 * layers with transition and pause parameters.
 */
@Composable
fun HaloStage(model: StageModel, modifier: Modifier = Modifier, wallClockMs: () -> Long = System::currentTimeMillis) {
    val angle = { HaloLight.sunAngle(model.progress(), wallClockMs()) }
    val plan = rememberTextPlan(model)
    Box(modifier.fillMaxSize()) {
        BaseWall()
        HaloLightLayer(model.scene, sunAngle = angle)
        HaloCoverLayer(model.scene, sunAngle = angle)
        HaloTextLayer(model, plan = plan)
        HaloClockLayer(model, plan = plan)
    }
}

/**
 * The wall under everything: black. Light only exists near the print; far away the OLED
 * pixels are simply off (also fills the edges while the stage drifts).
 */
@Composable
fun BaseWall(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawRect(Color.Black) }
}

/**
 * The light on the wall: the lamp's round pool, placed just inside the print's edge on the
 * sundial's side ([sunAngle]), hidden behind the print and held off the screen edges by the
 * static wall mask, tinted with [tint]; a dark print also gets a faint rim all around.
 * [alpha] fades the layer during transitions; [lightLevel] dims the lamp (pause, link loss)
 * – dim light also loses colour. [holeOpen] 0..1 takes the print's shadow away while the
 * print itself is taken down, so the empty wall shows the whole pool and nothing else.
 */
@Composable
fun HaloLightLayer(
    scene: ArtworkScene,
    sunAngle: () -> Float,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    lightLevel: () -> Float = { 1f },
    tint: () -> Int = { scene.lightTint },
    holeOpen: () -> Float = { 0f },
) {
    val light = remember(scene) { WallLight(scene) }
    Canvas(modifier.fillMaxSize()) {
        val a = alpha().coerceIn(0f, 1f)
        val level = lightLevel().coerceIn(0f, 1f)
        if (a <= 0.002f || level <= 0.002f) return@Canvas
        light.place(HaloLight.lampPose(scene.shape, sunAngle()), density, size)
        val filter = ColorFilter.tint(Color(HaloLight.dimTint(tint(), level)), BlendMode.Modulate)
        val open = holeOpen().coerceIn(0f, 1f)
        if (open > 0.001f) drawRect(light.openWall, alpha = a, colorFilter = filter)
        if (open < 0.999f) {
            drawRect(light.wall, alpha = a * (1f - open), colorFilter = filter)
            // The rim only shows where the pool is dimmer than it (the far sides). Added on
            // top it would brighten the lit side twice over – sums of sRGB values overshoot.
            light.rim?.let { rim ->
                drawRect(
                    rim,
                    alpha = a * (1f - open),
                    colorFilter = ColorFilter.tint(Color(HaloLight.dimTint(scene.rimTint, level)), BlendMode.Modulate),
                    blendMode = BlendMode.Lighten,
                )
            }
        }
    }
}

/** The shaders of one scene's light, made once; per frame only their matrices change. */
private class WallLight(scene: ArtworkScene) {
    private val lamp = clamped(scene.lamp)
    private val hole = clamped(scene.holeMask)
    private val open = clamped(scene.openMask)
    private val rimMap = scene.rim?.let(::clamped)
    private val spriteHalf = scene.lamp.width / 2f
    private val lampMatrix = android.graphics.Matrix()
    private val maskMatrix = android.graphics.Matrix()

    /** Lamp × wall mask with the print. */
    val wall = ShaderBrush(android.graphics.ComposeShader(lamp, hole, android.graphics.BlendMode.MODULATE))

    /** Lamp × wall mask without the print. */
    val openWall = ShaderBrush(android.graphics.ComposeShader(lamp, open, android.graphics.BlendMode.MODULATE))
    val rim = rimMap?.let { ShaderBrush(it) }

    /** Puts the pool where the lamp stands now; the masks just cover the screen. */
    fun place(pose: HaloLight.LampPose, density: Float, size: Size) {
        lampMatrix.setTranslate(-spriteHalf, -spriteHalf)
        lampMatrix.postScale(pose.alongEdge * density / spriteHalf, pose.towardsSun * density / spriteHalf)
        lampMatrix.postRotate(pose.rotationDeg)
        lampMatrix.postTranslate(pose.x * density, pose.y * density)
        lamp.setLocalMatrix(lampMatrix)
        maskMatrix.setScale(size.width / HaloLight.MAP_W, size.height / HaloLight.MAP_H)
        hole.setLocalMatrix(maskMatrix)
        open.setLocalMatrix(maskMatrix)
        rimMap?.setLocalMatrix(maskMatrix)
    }

    private fun clamped(bitmap: android.graphics.Bitmap) =
        android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
}

/**
 * The print, drawn 1:1 at a whole-pixel position (the bitmap already has its exact display
 * size), like a print a few centimetres off the wall: a faint raking light across it from
 * the sundial's side (±4 %) and the faintest catch of light on the lit edge. [dim] (pause)
 * darkens it through the colour matrix rather than transparency, so the light behind
 * never shines through.
 */
@Composable
fun HaloCoverLayer(
    scene: ArtworkScene,
    modifier: Modifier = Modifier,
    sunAngle: () -> Float = { 90f },
    alpha: () -> Float = { 1f },
    dim: () -> Float = { 1f },
    scale: () -> Float = { 1f },
) {
    val coverShader = remember(scene) {
        android.graphics.BitmapShader(scene.cover, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    }
    val filters = remember(scene) { HashMap<Int, ColorFilter>() }
    val rect = scene.coverRect
    val ink = scene.palette.ink.asColor()
    val round = scene.shape.round
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
        // Dimming is quantised so an animation reuses a handful of colour filters. The
        // raking light multiplies by 0.92…1.0, the filter lifts by 1.04: ±4 % around neutral.
        val step = (dim().coerceIn(0f, 1f) * RAKE_LIFT * 50f).roundToInt()
        val filter = filters.getOrPut(step) { coverFilter(scene.lighting, step / 50f) }
        val x = (rect.left * density).roundToInt()
        val y = (rect.top * density).roundToInt()
        val w = scene.cover.width
        val h = scene.cover.height
        coverShader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(x.toFloat(), y.toFloat()) })

        val sun = sunAngle()
        val theta = Math.toRadians(sun.toDouble())
        val ux = kotlin.math.cos(theta).toFloat()
        val uy = -kotlin.math.sin(theta).toFloat()
        val cx = x + w / 2f
        val cy = y + h / 2f
        val half = w / 2f
        val rake = android.graphics.LinearGradient(
            cx + ux * half, cy + uy * half, cx - ux * half, cy - uy * half,
            android.graphics.Color.WHITE, RAKE_FAR, android.graphics.Shader.TileMode.CLAMP,
        )
        drawRect(
            brush = ShaderBrush(android.graphics.ComposeShader(coverShader, rake, android.graphics.BlendMode.MODULATE)),
            topLeft = Offset(x.toFloat(), y.toFloat()),
            size = Size(w.toFloat(), h.toFloat()),
            colorFilter = filter,
        )

        // Only the edges facing the sun catch a breath of light; no line anywhere else.
        if (!round) {
            val line = (density / 2f).coerceAtLeast(1f)
            val sides = listOf(
                0f to Pair(Offset(x + w - line / 2, y.toFloat()), Offset(x + w - line / 2, (y + h).toFloat())),
                90f to Pair(Offset(x.toFloat(), y + line / 2), Offset((x + w).toFloat(), y + line / 2)),
                180f to Pair(Offset(x + line / 2, y.toFloat()), Offset(x + line / 2, (y + h).toFloat())),
                270f to Pair(Offset(x.toFloat(), y + h - line / 2), Offset((x + w).toFloat(), y + h - line / 2)),
            )
            for ((normal, ends) in sides) {
                val c = kotlin.math.cos(Math.toRadians((sun - normal).toDouble())).toFloat()
                val lit = c.coerceAtLeast(0f).let { it * it * it }
                if (lit > 0.02f) drawLine(ink.copy(alpha = 0.10f * lit), ends.first, ends.second, strokeWidth = line)
            }
        }
    }
}

/** Raking light: the far side of the print is multiplied by this grey (sRGB ≈ 0.92). */
private const val RAKE_FAR = 0xFFEBEBEB.toInt()

/** …and the whole print lifted by this, so the lit side ends up a few percent brighter. */
private const val RAKE_LIFT = 1.04f

/** Title sizes from large to small, each with the most lines it may take. */
private val titleLadder = listOf(
    52f to 2, 49f to 2, 46f to 2, 42f to 3, 39f to 3, 36f to 3, 34f to 3, 32f to 3, 30f to 3, 28f to 3, 26f to 4, 24f to 4,
)

/** Distance from the clock's baseline to the top of the text block below it (dp). */
private const val CLOCK_CLEARANCE_DP = 52f

/**
 * The measured decisions for one text column: title size and breaks, the artists fitted
 * onto one line, and whether the clock still has room above the block.
 */
class TextPlan(
    val title: AnnotatedString,
    val titleStyle: TextStyle,
    val titleLines: Int,
    val artists: String?,
    val artistLines: Int,
    val showClock: Boolean,
) {
    /** The artists take at most one line (or there are none). */
    val oneArtistLine: Boolean get() = artists == null || artistLines == 1
}

/** Styles of the text column; colours come from the scene. */
private class ColumnStyles(model: StageModel) {
    private val p = model.scene.palette
    private val t = model.text
    val ink = p.ink.asColor()
    val muted = p.inkMuted.asColor()
    val faint = p.inkFaint.asColor()
    val quiet = p.inkQuiet.asColor()
    val hush = p.inkHush.asColor()
    private val latinTitle = isSpacingFriendly(t.title)
    val cjkTitle = isMostlyCjk(t.title)

    val title = TextStyle(
        fontFamily = Fonts.Display,
        fontWeight = FontWeight(340),
        color = ink,
        letterSpacing = if (latinTitle) (-0.012).em else 0.em,
        lineBreak = LineBreak.Heading,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    val work = TextStyle(
        fontFamily = Fonts.Text,
        fontStyle = if (isSpacingFriendly(t.work.orEmpty())) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight(380),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        color = muted,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    private val latinArtists = isSpacingFriendly(t.artists.orEmpty())
    val artistTracking = 0.16.em

    /** Latin artists are a label in spaced capitals; scripts without case get size instead. */
    val artist = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight(if (latinArtists) 400 else 500),
        fontSize = if (latinArtists) 13.sp else 15.sp,
        lineHeight = if (latinArtists) 19.sp else 21.sp,
        color = muted,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    private val latinAlbum = isSpacingFriendly(t.album.orEmpty())

    /** The album never outweighs the artist: in scripts without italics it gets smaller. */
    val album = TextStyle(
        fontFamily = Fonts.Text,
        fontStyle = if (latinAlbum) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight(380),
        fontSize = if (latinAlbum) 16.sp else 13.sp,
        lineHeight = 21.sp,
        color = faint,
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Left,
    )
    val version = SpanStyle(
        fontFamily = Fonts.Text,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight(380),
        fontSize = 17.sp,
        letterSpacing = 0.em,
        color = muted,
    )
    val clock = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
        letterSpacing = 0.2.em,
        color = quiet,
        fontFeatureSettings = "tnum",
    )

    fun titleAt(size: Float): TextStyle {
        val s = if (cjkTitle) size * 0.86f else size
        return title.merge(TextStyle(fontSize = s.sp, lineHeight = (s * 1.06f).sp))
    }
}

/**
 * The artist line: a label in spaced capitals (Latin scripts), with a trailing "u. a."
 * set in lower-case italics – in capitals it would read like initials.
 */
private fun artistLine(text: String, styles: ColumnStyles): AnnotatedString {
    if (!isSpacingFriendly(text)) return AnnotatedString(text)
    val others = text.endsWith(TrackText.OTHERS)
    val main = if (others) text.dropLast(TrackText.OTHERS.length) else text
    return buildAnnotatedString {
        withStyle(SpanStyle(letterSpacing = styles.artistTracking)) { append(main.uppercase()) }
        if (others) {
            append("\u00A0")
            withStyle(SpanStyle(fontFamily = Fonts.Text, fontStyle = FontStyle.Italic, fontSize = 15.sp, letterSpacing = 0.em)) {
                append(TrackText.OTHERS.trim())
            }
        }
    }
}

/** Measures the column once per model (title size, artist fit, clock room). */
@Composable
fun rememberTextPlan(model: StageModel): TextPlan {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val localDensity = LocalDensity.current
    return remember(model.text, model.scene.layout, model.night, model.clock, model.scene.palette, localDensity) {
        planText(model, ColumnStyles(model), measurer, localDensity)
    }
}

private fun planText(
    model: StageModel,
    styles: ColumnStyles,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
): TextPlan {
    val t = model.text
    val layout = model.scene.layout
    val d = density.density
    val colW = (layout.columnWidth * d).roundToInt()

    val title = buildAnnotatedString {
        append(EmojiText.annotate(t.title, dimTo = styles.ink.copy(alpha = 0.62f)))
        // A version note ("Radio Edit") belongs to the title: small italic after it.
        t.version?.let { v ->
            append(" ")
            withStyle(styles.version) { append(v.replace(' ', ' ')) }
        }
    }

    val workH = t.work?.let {
        measurer.measure(AnnotatedString(it), styles.work, constraints = Constraints(maxWidth = colW), maxLines = 2).size.height
    } ?: 0
    val workBlock = if (workH > 0) workH + 8 * d else 0f
    val clockBaseline = layout.clockTop * d + with(density) { 12.sp.toPx() } * 0.70f
    val withClock = clockBaseline + CLOCK_CLEARANCE_DP * d + workBlock
    // Without the clock the block may rise to the cover's top edge.
    val withoutClock = layout.slotTop * d + workBlock
    fun maxH(ceiling: Float) = (layout.titleBaseline * d - ceiling + 16 * d).roundToInt().coerceAtLeast(1)

    val wantsClock = model.clock != null && !model.night
    val first = pickTitleSize(title, styles, measurer, colW, maxH(if (wantsClock) withClock else withoutClock))
    val showClock = wantsClock && first != null
    val choice = first ?: pickTitleSize(title, styles, measurer, colW, maxH(withoutClock))
        ?: TitleChoice(titleLadder.last().first, titleLadder.last().second, title)

    // Artists on one line: the most complete form that fits.
    val options = t.artistOptions()
    val fitted = options.firstOrNull { option ->
        measurer.measure(artistLine(option, styles), styles.artist, maxLines = 1, softWrap = false).size.width <= colW
    }
    return TextPlan(
        title = choice.text,
        titleStyle = styles.titleAt(choice.size),
        titleLines = choice.lines,
        artists = fitted ?: options.lastOrNull(),
        artistLines = if (fitted != null) 1 else 2,
        showClock = showClock,
    )
}

/**
 * The text column on its fixed baselines: title growing upwards from its baseline (with
 * the work line above it), the artist line fixed, album below, time line on the cover's
 * bottom edge. [dim] darkens the text a little while paused. The clock is a layer of its
 * own ([HaloClockLayer]) so it can stay put while the rest changes.
 */
@Composable
fun HaloTextLayer(
    model: StageModel,
    modifier: Modifier = Modifier,
    plan: TextPlan = rememberTextPlan(model),
    alpha: () -> Float = { 1f },
    dim: () -> Float = { 1f },
    offsetY: () -> Float = { 0f },
    /** Lines that stay as they are during a change: drawn steadily, they don't fade. */
    steady: Set<TextLine> = emptySet(),
    /** Lines not drawn at all (the other layer of a change draws them). */
    hidden: Set<TextLine> = emptySet(),
) {
    val layout = model.scene.layout
    val t = model.text
    val night = model.night
    val styles = remember(model.text, model.scene.palette) { ColumnStyles(model) }
    val localDensity = LocalDensity.current
    val colW = (layout.columnWidth * localDensity.density).roundToInt()

    // Each line fades on its own, so a line that stays the same can simply stay.
    fun line(which: TextLine): Modifier = Modifier.graphicsLayer {
        this.alpha = when (which) {
            in hidden -> 0f
            in steady -> 1f
            else -> alpha().coerceIn(0f, 1f)
        }
        translationY = if (which in steady) 0f else (offsetY() * density).roundToInt().toFloat()
    }
    // At night only an explicit request for the exact time shows a time line.
    val timeLine = model.timeLine?.takeIf { !night || it.figures }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = dim().coerceIn(0f, 1f) },
        content = {
            // 0: work line
            if (t.work != null) {
                BasicText(t.work, line(TextLine.WORK), style = styles.work, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 1: title
            BasicText(plan.title, line(TextLine.TITLE), style = plan.titleStyle, maxLines = plan.titleLines, overflow = TextOverflow.Ellipsis)
            // 2: artist
            if (plan.artists != null) {
                BasicText(
                    artistLine(plan.artists, styles),
                    line(TextLine.ARTIST),
                    style = styles.artist,
                    maxLines = plan.artistLines,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(Modifier)
            }
            // 3: album
            if (t.album != null && !night) {
                BasicText(t.album, line(TextLine.ALBUM), style = styles.album, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier)
            }
            // 4: time line – changes cross-fade instead of jumping
            Crossfade(
                targetState = timeLine,
                modifier = line(TextLine.TIME),
                animationSpec = tween(TIME_LINE_FADE_MS, easing = Motion.Dimmer),
                label = "time-line",
            ) { l ->
                if (l != null) TimeLineText(l, styles.quiet, styles.hush) else Box(Modifier)
            }
        },
    ) { measurables, constraints ->
        val c = Constraints(maxWidth = colW)
        val x = (layout.columnLeft * density).roundToInt()
        val work = measurables[0].measure(c)
        val title = measurables[1].measure(c)
        val artist = measurables[2].measure(c)
        val album = measurables[3].measure(c)
        val timeLine = measurables[4].measure(c)
        val workGap = (8 * density).roundToInt()
        val titleBaseline = (layout.titleBaseline * density).roundToInt()

        layout(constraints.maxWidth, constraints.maxHeight) {
            fun Placeable.baseline(first: Boolean): Int {
                val b = this[if (first) FirstBaseline else LastBaseline]
                return if (b == androidx.compose.ui.layout.AlignmentLine.Unspecified) height else b
            }
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

/** The lines of the text column, for changes that keep some of them. */
enum class TextLine { WORK, TITLE, ARTIST, ALBUM, TIME }

/**
 * Lines that look exactly the same before and after a change (same text in the same place
 * and colour): they stay while the others fade. Each line is compared on its own – the
 * album of the next song stays even when that song has a guest artist.
 */
fun steadyLines(before: StageModel, beforePlan: TextPlan, after: StageModel, afterPlan: TextPlan): Set<TextLine> {
    if (before.scene.layout.columnLeft != after.scene.layout.columnLeft || before.night != after.night) return emptySet()
    val a = before.scene.palette
    val b = after.scene.palette
    val out = HashSet<TextLine>()
    val sameTitleBlock = beforePlan.title.text == afterPlan.title.text &&
        beforePlan.titleStyle.fontSize == afterPlan.titleStyle.fontSize && a.ink == b.ink
    if (sameTitleBlock) out += TextLine.TITLE
    // The work line sits on top of the title block, so it can only stay if the title does.
    if (sameTitleBlock && before.text.work == after.text.work && a.inkMuted == b.inkMuted) out += TextLine.WORK
    val sameArtists = beforePlan.artists == afterPlan.artists && beforePlan.artistLines == afterPlan.artistLines
    if (sameArtists && a.inkMuted == b.inkMuted) out += TextLine.ARTIST
    // The album hangs under the artist line: its place is the same whenever neither side
    // needs a second artist line.
    val albumInPlace = sameArtists || (beforePlan.oneArtistLine && afterPlan.oneArtistLine)
    if (albumInPlace && before.text.album == after.text.album && a.inkFaint == b.inkFaint) out += TextLine.ALBUM
    if (before.timeLine == after.timeLine && a.inkQuiet == b.inkQuiet && a.inkHush == b.inkHush) out += TextLine.TIME
    return out
}

/**
 * The clock at the top of the text column, its cap height on the cover's top edge.
 * [color] overrides the palette's colour (a clock that stays through a change takes on the
 * new print's colour gradually).
 */
@Composable
fun HaloClockLayer(
    model: StageModel,
    modifier: Modifier = Modifier,
    plan: TextPlan = rememberTextPlan(model),
    alpha: () -> Float = { 1f },
    color: ColorProducer? = null,
) {
    val clock = model.clock
    if (clock == null || !plan.showClock || model.night) return
    val layout = model.scene.layout
    val styles = remember(model.text, model.scene.palette) { ColumnStyles(model) }
    Layout(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) },
        content = { BasicText(clock, style = styles.clock, color = color) },
    ) { measurables, constraints ->
        val p = measurables[0].measure(Constraints())
        val baseline = (layout.clockTop * density).roundToInt() + (12.sp.toPx() * 0.70f).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val b = p[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: p.height
            p.place((layout.columnLeft * density).roundToInt(), baseline - b)
        }
    }
}

const val TIME_LINE_FADE_MS = 600

private class TitleChoice(val size: Float, val lines: Int, val text: AnnotatedString)

/**
 * The title at the largest size of the ladder that fits its line limit and the height it
 * is given; balanced line breaks. At each size, a version that may only break after the
 * dashes between a movement's sections is tried first ("Presto – / Allegro assai", never
 * "Allegro / assai"). Returns null if nothing fits.
 */
private fun pickTitleSize(
    text: AnnotatedString,
    styles: ColumnStyles,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxW: Int,
    maxH: Int,
): TitleChoice? {
    val base = glueParentheses(text)
    val glued = glueBetweenDashes(base)
    for ((size, lines) in titleLadder) {
        val style = styles.titleAt(size)
        if (glued != null) {
            val g = measurer.measure(glued, style = style, constraints = Constraints(maxWidth = maxW), maxLines = lines + 1)
            if (g.lineCount <= lines && g.size.height <= maxH && breaksOnlyAfterDashes(g)) return TitleChoice(size, lines, glued)
        }
        val r = measurer.measure(base, style = style, constraints = Constraints(maxWidth = maxW), maxLines = lines + 1)
        if (r.lineCount <= lines && r.size.height <= maxH && !breaksInsideWord(r)) return TitleChoice(size, lines, base)
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
                if (open >= 0 && i - open <= 28) for (j in open until i) if (chars[j] == ' ') chars[j] = '\u00A0'
                open = -1
            }
        }
    }
    return AnnotatedString(String(chars), spanStyles = text.spanStyles, paragraphStyles = text.paragraphStyles)
}

/** The title with every space that is not right after a dash made non-breaking; null without dashes. */
private fun glueBetweenDashes(text: AnnotatedString): AnnotatedString? {
    val s = text.text
    if (!s.contains("\u00A0– ")) return null
    val chars = s.toCharArray()
    for (i in chars.indices) {
        if (chars[i] == ' ' && !(i > 0 && chars[i - 1] == '–')) chars[i] = '\u00A0'
    }
    return AnnotatedString(String(chars), spanStyles = text.spanStyles, paragraphStyles = text.paragraphStyles)
}

/** True if every line but the last ends right after "– ". */
private fun breaksOnlyAfterDashes(r: androidx.compose.ui.text.TextLayoutResult): Boolean {
    val text = r.layoutInput.text.text
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i)
        if (end < 2 || text[end - 1] != ' ' || text[end - 2] != '–') return false
    }
    return true
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

/** How far the separator dot is raised next to Arabic or Hebrew (share of its size). */
private val RTL_DOT_LIFT = BaselineShift(0.2f)

/** True if [text] contains letters of a right-to-left script. */
fun isRightToLeft(text: String): Boolean = text.any {
    val d = Character.getDirectionality(it)
    d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

@Composable
private fun TimeLineText(line: TimeLine.Line, labelColor: Color, detailColor: Color) {
    val label = line.label
    val friendly = isSpacingFriendly(label)
    val text = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontFamily = Fonts.Sans,
                fontWeight = FontWeight(500),
                fontSize = 10.sp,
                letterSpacing = if (line.figures) 0.12.em else if (friendly) 0.22.em else 0.em,
                fontFeatureSettings = "tnum",
                color = labelColor,
            ),
        ) { append(if (friendly && !line.figures) label.uppercase() else label) }
        line.detail?.let { detail ->
            append("\u00A0\u00A0 ")
            // Each part in its own direction (a title in Arabic, an artist in Latin…), and
            // the separator always in the Latin face. Next to Arabic, whose letters sit low
            // on the line, the dot is raised a little – at the Latin height it would read
            // as a full stop.
            val parts = detail.split(TimeLine.SEPARATOR)
            parts.forEachIndexed { i, part ->
                if (i > 0) {
                    val lift = if (isRightToLeft(parts[i - 1]) || isRightToLeft(part)) RTL_DOT_LIFT else BaselineShift.None
                    withStyle(SpanStyle(fontFamily = Fonts.Sans, fontSize = 12.sp, color = detailColor, baselineShift = lift)) {
                        append(TimeLine.SEPARATOR)
                    }
                }
                withStyle(
                    SpanStyle(
                        fontFamily = Fonts.Text,
                        fontStyle = if (isSpacingFriendly(part)) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 15.sp,
                        color = detailColor,
                    ),
                ) { append(if (isSpacingFriendly(part)) part else "\u2068$part\u2069") }
            }
        }
    }
    BasicText(
        text,
        style = TextStyle(color = labelColor, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
