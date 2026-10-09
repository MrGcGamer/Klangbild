package io.github.hazymorning.klangbild.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** A colour cluster of the cover in Oklab with its share of the image (0..1). */
data class ColorCluster(val lab: Lab, val weight: Float)

/** Rough character of a cover; drives the edge-case handling of the palette. */
enum class CoverKind { DARK, LIGHT, MONOCHROME, VIVID, BALANCED }

data class CoverAnalysis(
    val clusters: List<ColorCluster>,
    val meanL: Float,
    val meanChroma: Float,
    /** 90th percentile of chroma – "how colourful are the colourful parts". */
    val highChroma: Float,
    val kind: CoverKind,
)

/**
 * The colours of one scene. Everything is derived from the cover but tamed: harmonious,
 * never garish, always readable on the dark wall. Values are ARGB ints so this stays pure
 * Kotlin (the UI wraps them in Compose colours).
 */
data class ScenePalette(
    /** The wall behind everything; very dark, faintly tinted. */
    val base: Int,
    /** Light colours for the halo (from the cover's most significant colours). */
    val glow: List<Int>,
    /** Primary text: near-neutral white with a breath of the cover's hue, never pure white. */
    val ink: Int,
    /** Secondary text (artist, album). */
    val inkMuted: Int,
    /** Tertiary details (album). */
    val inkFaint: Int,
    /** Quiet labels (times, status, station). */
    val inkQuiet: Int,
    /** The quietest voice (what comes next). */
    val inkHush: Int,
    /** One restrained colour accent, e.g. for the progress line. */
    val accent: Int,
    val kind: CoverKind,
    /** Hue (degrees) that tints neutrals; null for neutral covers (uses warm default). */
    val tintHue: Float?,
)

object CoverPalette {
    private const val K = 6
    private const val ITERATIONS = 14
    private const val NEUTRAL_CHROMA = 0.035f

    /** Default hue for neutral covers: a warm, amber "lamp light" hue. */
    const val WARM_HUE = 72f

    /** Hue [a] moved towards [b] by [t] along the shorter arc (degrees). */
    private fun mixHue(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return ((a + d * t) % 360f + 360f) % 360f
    }

    /**
     * Analyses ARGB pixels (typically a 48–64 px thumbnail). Deterministic for equal input.
     */
    fun analyze(pixels: IntArray): CoverAnalysis {
        val labs = ArrayList<Lab>(pixels.size)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            labs += Oklab.fromArgb(p)
        }
        if (labs.isEmpty()) {
            return CoverAnalysis(emptyList(), 0f, 0f, 0f, CoverKind.DARK)
        }
        val meanL = labs.sumOf { it.l.toDouble() }.toFloat() / labs.size
        val chromas = labs.map { it.chroma }.sorted()
        val meanChroma = chromas.average().toFloat()
        val highChroma = chromas[((chromas.size - 1) * 0.9f).toInt()]
        val clusters = kMeans(labs)
        val kind = when {
            meanL < 0.24f -> CoverKind.DARK
            meanL > 0.84f && meanChroma < 0.06f -> CoverKind.LIGHT
            highChroma < NEUTRAL_CHROMA -> CoverKind.MONOCHROME
            highChroma > 0.2f -> CoverKind.VIVID
            else -> CoverKind.BALANCED
        }
        return CoverAnalysis(clusters, meanL, meanChroma, highChroma, kind)
    }

    private fun kMeans(points: List<Lab>): List<ColorCluster> {
        val rnd = Random(0x4B42) // deterministic
        val k = min(K, points.size)
        // k-means++ initialisation
        val centers = ArrayList<Lab>(k)
        centers += points[rnd.nextInt(points.size)]
        val dist = FloatArray(points.size) { Float.MAX_VALUE }
        while (centers.size < k) {
            var sum = 0.0
            val last = centers.last()
            for (i in points.indices) {
                dist[i] = min(dist[i], points[i].distanceSq(last))
                sum += dist[i]
            }
            if (sum <= 0.0) break
            var r = rnd.nextDouble() * sum
            var chosen = points.size - 1
            for (i in points.indices) {
                r -= dist[i]
                if (r <= 0) {
                    chosen = i
                    break
                }
            }
            centers += points[chosen]
        }
        val assign = IntArray(points.size)
        repeat(ITERATIONS) {
            for (i in points.indices) {
                var best = 0
                var bestD = Float.MAX_VALUE
                for (c in centers.indices) {
                    val d = points[i].distanceSq(centers[c])
                    if (d < bestD) {
                        bestD = d
                        best = c
                    }
                }
                assign[i] = best
            }
            for (c in centers.indices) {
                var l = 0.0
                var a = 0.0
                var b = 0.0
                var n = 0
                for (i in points.indices) if (assign[i] == c) {
                    l += points[i].l; a += points[i].a; b += points[i].b; n++
                }
                if (n > 0) centers[c] = Lab((l / n).toFloat(), (a / n).toFloat(), (b / n).toFloat())
            }
        }
        val counts = IntArray(centers.size)
        for (a in assign) counts[a]++
        val raw = centers.indices
            .filter { counts[it] > 0 }
            .map { ColorCluster(centers[it], counts[it].toFloat() / points.size) }
            .sortedByDescending { it.weight }
        // Merge clusters that are perceptually the same colour.
        val merged = ArrayList<ColorCluster>()
        for (c in raw) {
            val twin = merged.indexOfFirst { it.lab.distanceSq(c.lab) < 0.035f * 0.035f }
            if (twin >= 0) {
                val t = merged[twin]
                val w = t.weight + c.weight
                merged[twin] = ColorCluster(t.lab.lerp(c.lab, c.weight / w), w)
            } else {
                merged += c
            }
        }
        return merged.sortedByDescending { it.weight }
    }

    /**
     * Significance of a cluster as a *colour* of the cover: area matters, but a small
     * vivid area (a red dot on cream) characterises a cover more than a large grey one.
     */
    private fun colourScore(c: ColorCluster): Float =
        c.weight.toDouble().pow(0.55).toFloat() * (c.lab.chroma - 0.02f).coerceAtLeast(0f) *
            // very dark or very light clusters carry little colour information
            (1f - (2f * c.lab.l - 1.1f).pow(2)).coerceAtLeast(0.15f)

    fun derive(analysis: CoverAnalysis, lighting: Lighting = Lighting.DAY): ScenePalette {
        val chromatic = analysis.clusters
            .filter { it.lab.chroma >= NEUTRAL_CHROMA && it.weight >= 0.02f }
            .sortedByDescending { colourScore(it) }
        val lead = chromatic.firstOrNull()
        val tintHue = lead?.lab?.hue
        val hue = tintHue ?: WARM_HUE
        // Text is near-neutral: only a breath of the cover's hue so it belongs to the
        // picture – never ivory. A neutral cover gets neutral text.
        val neutralChroma = if (tintHue == null) 0.003f else min(0.009f, lead.lab.chroma * 0.08f + 0.002f)

        // At night the text must not outshine the dimmed cover: all text a little lower
        // still than the light alone would make it – but the title keeps 4.5:1 on black.
        val nightness = ((1f - lighting.brightness) / 0.4f).coerceIn(0f, 1f)
        val t = 1f - 0.10f * nightness
        val base = Lch(0.115f, min(neutralChroma * 1.1f, 0.016f), hue).toLab()
        // In the evening the text takes the warm light's hue rather than the cover's: a rosy tint
        // under amber light reads as pink.
        val evening = ((lighting.warmth - Lighting.DAY.warmth) / (Lighting.EVENING.warmth - Lighting.DAY.warmth)).coerceIn(0f, 1f)
        val inkHue = mixHue(hue, WARM_HUE, 0.85f * evening)
        // In the evening the room's light is warm, and so is the text – a little.
        val inkChroma = neutralChroma + 0.008f * evening
        // Three levels, like white at 100 / 80 / 62 % on black (lightness in Oklab): the title;
        // artist, clock and the progress bar's fill; everything else. Greys closer than that
        // read as a mistake side by side, not as a level.
        val ink = Lch(0.93f * t, inkChroma, inkHue).toLab()
        val inkMuted = Lch(0.80f * t, inkChroma, inkHue).toLab()
        val inkFaint = Lch(0.665f * t, inkChroma, inkHue).toLab()
        val inkQuiet = inkFaint
        val inkHush = inkFaint

        val accent = if (lead == null) {
            Lch(0.82f, 0.055f, WARM_HUE).toLab() // brass
        } else {
            Lch(0.82f, min(lead.lab.chroma * 0.8f, 0.105f).coerceAtLeast(0.05f), lead.lab.hue).toLab()
        }

        val glowSources = if (chromatic.isEmpty()) {
            listOf(Lch(0.62f, 0.03f, WARM_HUE).toLab())
        } else {
            chromatic.take(3).map { c ->
                val l = c.lab.l.coerceIn(0.5f, 0.7f)
                Lch(l, min(c.lab.chroma * 0.9f, 0.13f), c.lab.hue).toLab()
            }
        }

        return ScenePalette(
            base = lighting.apply(base).toArgb(),
            glow = glowSources.map { lighting.apply(it).toArgb() },
            ink = lighting.apply(ink).toArgb(),
            inkMuted = lighting.apply(inkMuted).toArgb(),
            inkFaint = lighting.apply(inkFaint).toArgb(),
            inkQuiet = lighting.apply(inkQuiet).toArgb(),
            inkHush = lighting.apply(inkHush).toArgb(),
            accent = lighting.apply(accent).toArgb(),
            kind = analysis.kind,
            tintHue = tintHue,
        )
    }

    /** Chroma limit for light colours derived from a cover (keeps neon in check). */
    fun tameForLight(lab: Lab, maxChroma: Float = 0.13f, minL: Float = 0f, maxL: Float = 1f): Lab {
        val c = lab.chroma
        val scale = if (c > maxChroma) maxChroma / c else 1f
        val l = lab.l.coerceIn(minL, maxL)
        return Lab(l, lab.a * scale, lab.b * scale)
    }

    internal fun clamp01(x: Float) = max(0f, min(1f, x))
    internal fun soft(x: Float) = sqrt(clamp01(x))
}
