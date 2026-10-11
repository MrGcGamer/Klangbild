package io.github.hazymorning.klangbild.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.R
import kotlin.math.roundToInt

/**
 * The two typefaces (both SIL Open Font License, shipped unmodified, licenses in
 * assets/licenses):
 *
 * - [TITLE]: Jost (Owen Earl), a geometric sans in the line of Paul Renner's Futura, for the
 *   titles, the large clock and the headings. Its round, even forms and small x-height keep
 *   large sizes calm.
 * - [TEXT]: DM Sans (Colophon Foundry), a geometric grotesque with optical sizes and a larger
 *   x-height, for everything read at 15 to 21 sp from the sofa: artists, albums, times, status,
 *   menus. Its optical-size axis is set to the size in sp, so small lines get the looser,
 *   sturdier cut.
 *
 * Both have a cap height of 0.70 em, so lines aligned by cap height or baseline agree across
 * the two. Hierarchy comes from size, weight and three ink levels, never from capitals,
 * letter-spacing, italics or a third family. Scripts the faces do not cover (Japanese,
 * Arabic and others) fall back to the system's fonts.
 */
enum class Face(
    internal val font: Int,
    /** The weights the styles use; a family holds exactly these, so no weight is ever faked. */
    internal val weights: IntArray,
    /** The optical-size axis (sp), or null for a face without one. */
    internal val opsz: ClosedFloatingPointRange<Float>?,
    /** How far a colon is raised to sit in the middle of the figures (em). */
    internal val colonRaise: Float,
) {
    TITLE(R.font.jost, intArrayOf(320, 400, 420, 430, 440), null, 0.123f),
    TEXT(R.font.dmsans, intArrayOf(400, 420, 440, 460, 480, 500, 520, 560, 600), 9f..40f, 0.10f),
}

object Fonts {
    /** Cap height of both faces as a share of the size (for aligning by cap height). */
    const val CAP_HEIGHT = 0.70f

    /** How far DM Sans's descenders (p, q, y) reach below the baseline, as a share of the size. */
    const val DESCENT = 0.22f

    private val families = HashMap<Long, FontFamily>()

    /** [face] for text at [size] sp. */
    fun family(face: Face, size: Float): FontFamily {
        val opsz = face.opsz?.let { size.coerceIn(it.start, it.endInclusive).roundToInt() } ?: 0
        val key = face.ordinal.toLong() shl 32 or opsz.toLong()
        return synchronized(families) {
            families.getOrPut(key) {
                FontFamily(
                    face.weights.map { weight ->
                        val settings = if (face.opsz != null) {
                            FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz.toFloat()))
                        } else {
                            FontVariation.Settings(FontVariation.weight(weight))
                        }
                        Font(resId = face.font, weight = FontWeight(weight), variationSettings = settings)
                    },
                )
            }
        }
    }
}

/**
 * A text style in [face] at [size] sp: line height [lineHeight] sp, tracking [tracking] em.
 * Layouts align the result by its baselines and [Fonts.CAP_HEIGHT], never by the boxes.
 */
fun type(
    face: Face,
    size: Float,
    weight: Int,
    color: Color = Color.Unspecified,
    lineHeight: Float = size * 1.25f,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = Fonts.family(face, size),
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    color = color,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** A style in the title face (Jost). */
fun titleType(size: Float, weight: Int, color: Color = Color.Unspecified, lineHeight: Float = size * 1.2f, tracking: Float = 0f) =
    type(Face.TITLE, size, weight, color, lineHeight, tracking)

/** A style in the text face (DM Sans). */
fun textType(size: Float, weight: Int, color: Color = Color.Unspecified, lineHeight: Float = size * 1.3f, tracking: Float = 0f) =
    type(Face.TEXT, size, weight, color, lineHeight, tracking)

/**
 * Times set like a clock face. Every digit stands in a cell as wide as the widest digit and
 * centered in it (letter-spacing is split evenly on both sides of a glyph), so a ticking time
 * never shifts the line. The colon is raised to the middle of the figures; set on the
 * baseline, it sags below them.
 */
class Figures internal constructor(
    private val cell: Float,
    private val advance: FloatArray,
    private val tracking: Float,
    private val colonRaise: Float,
) {
    fun of(text: String): AnnotatedString = buildAnnotatedString {
        for (ch in text) {
            when {
                ch in '0'..'9' -> withStyle(SpanStyle(letterSpacing = (tracking + cell - advance[ch - '0']).em)) { append(ch) }
                ch == ':' -> withStyle(SpanStyle(baselineShift = BaselineShift(colonRaise))) { append(ch) }
                else -> append(ch)
            }
        }
    }
}

/** [Figures] for [style] in [face], measured once. */
@Composable
fun rememberFigures(style: TextStyle, face: Face): Figures {
    val measurer = rememberTextMeasurer(cacheSize = 12)
    return remember(style, face) {
        val plain = style.copy(letterSpacing = 0.em)
        val sizePx = measurer.measure("0", plain).let { r -> r.layoutInput.density.run { plain.fontSize.toPx() } }
        val advance = FloatArray(10) { d -> measurer.measure(d.toString(), plain, softWrap = false).getLineRight(0) / sizePx }
        Figures(advance.max(), advance, if (style.letterSpacing.isEm) style.letterSpacing.value else 0f, face.colonRaise)
    }
}

/**
 * How far [text] in [style] has to move right so that what is seen (its ink, not its
 * advance box) stands centered where the box would be (px). A large "10:12" leans left by its
 * first figure's side bearing; a clock in the middle of the screen must not.
 */
@Composable
fun rememberInkShift(text: String, style: TextStyle): Float {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(text, style, resolver, density) {
        val typeface = resolver.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? android.graphics.Typeface
            ?: return@remember 0f
        val paint = android.graphics.Paint().apply {
            this.typeface = typeface
            textSize = with(density) { style.fontSize.toPx() }
            if (style.letterSpacing.isEm) letterSpacing = style.letterSpacing.value
        }
        // The outlines' exact bounds: whole pixels would leave the clock up to a pixel off.
        val outline = android.graphics.Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, outline)
        val ink = android.graphics.RectF()
        outline.computeBounds(ink, true)
        paint.measureText(text) / 2f - (ink.left + ink.right) / 2f
    }
}
