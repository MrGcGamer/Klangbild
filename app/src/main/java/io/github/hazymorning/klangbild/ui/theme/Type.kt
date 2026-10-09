package io.github.hazymorning.klangbild.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
 * tracking [tracking] em. Figures stay proportional: Mona's tabular figures bring a slashed
 * zero and a footed one – a code font's habits. Layouts align the result by its baselines
 * and [Fonts.CAP_HEIGHT], never by the boxes.
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
