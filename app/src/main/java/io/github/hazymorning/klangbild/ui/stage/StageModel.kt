package io.github.hazymorning.klangbild.ui.stage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.domain.PlayerStatus
import io.github.hazymorning.klangbild.domain.TimeLine
import io.github.hazymorning.klangbild.domain.TrackInfo
import io.github.hazymorning.klangbild.domain.TrackText
import io.github.hazymorning.klangbild.render.ArtworkScene
import kotlin.math.pow

/**
 * Everything the stage draws for one track. Time-dependent values are providers so that
 * ticking them never recomposes the stage.
 */
@Immutable
class StageModel(
    val scene: ArtworkScene,
    val text: TrackText,
    val status: PlayerStatus,
    val durationMs: Long?,
    /** Smoothed playback position "now" (null without duration information). */
    val positionMs: () -> Long?,
    /** Year of the album, shown after its name. */
    val year: Int? = null,
    /** The progress bar is shown (setting, or a key was just pressed). */
    val progress: Boolean = true,
    /**
     * A line in the progress bar's place: the station of a radio without duration, or a
     * notice ("Verbindung wird wiederhergestellt …"). Wins over the bar.
     */
    val line: String? = null,
    /** "Spiegel im Spiegel · Arvo Pärt": what comes next … */
    val next: String? = null,
    /** … and its title alone, for when both don't fit. */
    val nextTitle: String? = null,
    /** The status line at the top right: where the music plays, the volume, the time. */
    val statusLine: StatusLine? = null,
    /** Night: only artwork, title and artist remain. */
    val night: Boolean = false,
) {
    fun fraction(): Float? {
        val d = durationMs ?: return null
        val p = positionMs() ?: return null
        return if (d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else null
    }
}

/**
 * The quiet line at the top of the column: [clock], the time, then [lead] – where the music
 * plays, a pause or a passing volume ("Überall", "Pausiert", "Lautstärke 35 %").
 */
@Immutable
data class StatusLine(val lead: String?, val clock: String?) {
    val isEmpty get() = lead == null && clock == null
}

/** The stage's words for states and what comes next. */
object StageTexts {
    /** In the progress bar's place while the connection is lost. */
    const val LINK_DOWN = "Verbindung wird wiederhergestellt …"

    /**
     * The status line's lead: a passing volume change wins, then mute and pause (after the
     * place: "Wohnzimmer · Pausiert"), otherwise the place – and the volume, if it is always
     * shown.
     */
    fun lead(
        status: PlayerStatus,
        muted: Boolean,
        volumeFlash: Boolean,
        volume: String?,
        group: String?,
        volumeAlways: Boolean,
    ): String? = when {
        volumeFlash && !muted && volume != null -> "Lautstärke $volume"
        muted -> listOfNotNull(group, "Stumm").joinToString(TimeLine.SEPARATOR)
        status == PlayerStatus.PAUSED -> listOfNotNull(group, "Pausiert").joinToString(TimeLine.SEPARATOR)
        else -> listOfNotNull(group, volume.takeIf { volumeAlways }).joinToString(TimeLine.SEPARATOR).ifEmpty { null }
    }

    /** "Spiegel im Spiegel · Arvo Pärt". */
    fun next(track: TrackInfo?): String? =
        track?.let { TrackText.from(it) }?.let { listOfNotNull(it.title, it.shortArtists).joinToString(TimeLine.SEPARATOR) }

    /** "Spiegel im Spiegel". */
    fun nextTitle(track: TrackInfo?): String? = track?.let { TrackText.from(it).title }

    /** A line in the progress bar's place: the lost connection, or a radio's station. */
    fun line(linkDown: Boolean, track: TrackInfo): String? = when {
        linkDown -> LINK_DOWN
        track.durationMs == null -> track.source
        else -> null
    }
}

fun Int.asColor() = Color(this)

/**
 * Colour matrix for the artwork: the time-of-day lighting (perceptual dimming, only a touch
 * warmer at night – dim light loses colour rather than gaining it) and a gentle highlight
 * compression – even a white cover never glares on black (white becomes a soft paper tone
 * around L* 88 instead of #FFFFFF; a full-screen-bright print would also make the TV's
 * brightness limiter dim everything). On the light wall a white cover stays a little whiter
 * than the paper around it.
 */
fun coverFilter(lighting: Lighting, dim: Float = 1f, appearance: Appearance = Appearance.DARK): ColorFilter {
    val k = lighting.brightness.pow(1.36f) * dim * if (appearance == Appearance.LIGHT) 1.06f else 1f
    val w = lighting.warmth
    val r = k * 0.92f * (1f + 0.0375f * w)
    val g = k * 0.905f * (1f - 0.0066f * w)
    val b = k * 0.876f * (1f - 0.045f * w)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
