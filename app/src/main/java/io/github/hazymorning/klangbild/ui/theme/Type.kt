package io.github.hazymorning.klangbild.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hazymorning.klangbild.R
import kotlin.math.roundToInt

/**
 * One typeface for everything: Mona Sans (GitHub's variable grotesk, SIL Open Font License,
 * licence in assets/licenses, shipped unmodified). Hierarchy comes from size, weight and
 * opacity – never from capitals, letter-spacing, italics or a second family.
 *
 * The font has an optical-size axis, used like Apple's SF Text/Display split: small text
 * gets the looser, sturdier text cut, large text the tighter display cut – set to the size
 * in sp, so every size is drawn as the type designer meant it. Scripts the font does not
 * cover (Japanese, Arabic …) fall back to the system's fonts.
 */
object Fonts {
    /** The weights the styles use; a family holds exactly these, so no weight is ever faked. */
    private val WEIGHTS = intArrayOf(300, 320, 400, 420, 440, 460, 480, 500, 540, 600, 700)

    private val families = HashMap<Long, FontFamily>()

    /** Mona Sans for text at [size] sp ([width]: 100 normal, below 100 condensed). */
    fun at(size: Float, width: Float = 100f): FontFamily {
        val opsz = size.roundToInt().coerceIn(8, 100)
        val w = width.roundToInt().coerceIn(75, 125)
        val key = opsz.toLong() shl 32 or w.toLong()
        return synchronized(families) {
            families.getOrPut(key) {
                FontFamily(
                    WEIGHTS.map { weight ->
                        Font(
                            resId = R.font.monasans,
                            weight = FontWeight(weight),
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(weight),
                                FontVariation.width(w.toFloat()),
                                FontVariation.Setting("opsz", opsz.toFloat()),
                            ),
                        )
                    },
                )
            }
        }
    }

    /** Cap height of Mona Sans as a share of the size (for aligning by cap height). */
    const val CAP_HEIGHT = 0.729f
}

/**
 * A text style in Mona Sans at [size] sp: line height [lineHeight] sp (default 1.25 ×),
 * tracking [tracking] em. No tabular figures: Mona's bring a slashed zero and a footed one –
 * a code font's habits; times use [Figures]. Layouts align the result by its baselines and
 * [Fonts.CAP_HEIGHT], never by the boxes.
 */
fun mona(
    size: Float,
    weight: Int,
    color: Color = Color.Unspecified,
    lineHeight: Float = size * 1.25f,
    tracking: Float = 0f,
    width: Float = 100f,
): TextStyle = TextStyle(
    fontFamily = Fonts.at(size, width),
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    color = color,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * Times set like a clock face. Every digit stands in a cell as wide as the widest digit and
 * centred in it (letter-spacing is split evenly on both sides of a glyph), so a ticking time
 * never shifts the line – Mona's own tabular figures would bring a slashed zero. The colon is
 * raised to the middle of the figures; set on the baseline it sags below them.
 */
class Figures internal constructor(private val cell: Float, private val advance: FloatArray, private val tracking: Float) {
    fun of(text: String): AnnotatedString = buildAnnotatedString {
        for (ch in text) {
            when {
                ch in '0'..'9' -> withStyle(SpanStyle(letterSpacing = (tracking + cell - advance[ch - '0']).em)) { append(ch) }
                ch == ':' -> withStyle(SpanStyle(baselineShift = BaselineShift(COLON_RAISE))) { append(ch) }
                else -> append(ch)
            }
        }
    }

    companion object {
        /** How far the colon is raised (em): from the baseline to the figures' middle. */
        const val COLON_RAISE = 0.095f
    }
}

/** [Figures] for [style], measured once. */
@Composable
fun rememberFigures(style: TextStyle): Figures {
    val measurer = rememberTextMeasurer(cacheSize = 12)
    return remember(style) {
        val plain = style.copy(letterSpacing = 0.em)
        val sizePx = measurer.measure("0", plain).let { r -> r.layoutInput.density.run { plain.fontSize.toPx() } }
        val advance = FloatArray(10) { d -> measurer.measure(d.toString(), plain, softWrap = false).getLineRight(0) / sizePx }
        Figures(advance.max(), advance, if (style.letterSpacing.isEm) style.letterSpacing.value else 0f)
    }
}
