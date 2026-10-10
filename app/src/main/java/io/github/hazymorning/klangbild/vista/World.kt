package io.github.hazymorning.klangbild.vista

import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where on Earth the window looks out (degrees, north and east positive). */
data class Place(val latitude: Double, val longitude: Double)

/** The sky over a place at one moment: sun and moon (degrees, azimuth from north through east) and the stars' turn. */
data class Heavens(
    val sunElevation: Float,
    val sunAzimuth: Float,
    val moonElevation: Float,
    val moonAzimuth: Float,
    val moonFraction: Float,
    /** Local sidereal time (degrees): how far the stars have turned. */
    val siderealDeg: Double,
    val latitude: Double,
)

/**
 * The real world behind the picture, kept to what it shows in every moment: the sun's and the
 * moon's places and the moon's phase (computed with commons-suncalc, Apache 2.0, about a
 * minute exact – far more than a picture needs), the stars' turn, and the season from the
 * calendar. Everything is a smooth function of time: nothing in the picture ever jumps.
 */
object World {

    fun heavens(epochMs: Long, place: Place): Heavens {
        val instant = Instant.ofEpochMilli(epochMs)
        val sun = SunPosition.compute().on(instant).at(place.latitude, place.longitude).execute()
        val moon = MoonPosition.compute().on(instant).at(place.latitude, place.longitude).execute()
        val lit = MoonIllumination.compute().on(instant).at(place.latitude, place.longitude).execute()
        return Heavens(
            sunElevation = sun.altitude.toFloat(),
            sunAzimuth = sun.azimuth.toFloat(),
            moonElevation = moon.altitude.toFloat(),
            moonAzimuth = moon.azimuth.toFloat(),
            moonFraction = lit.fraction.toFloat(),
            siderealDeg = siderealDegrees(epochMs, place.longitude),
            latitude = place.latitude,
        )
    }

    /**
     * What the sky does at [epochMs] over [place] – everything the picture's light depends on –
     * and where the heavens stand; [cover] is the clouds as a mood, [dayOfYear] a day the preview
     * has chosen for the season instead of the calendar's.
     */
    fun sky(epochMs: Long, place: Place, zone: ZoneId, cover: Float, dayOfYear: Int? = null): Pair<SkyState, Heavens> {
        val h = heavens(epochMs, place)
        val (season, shapes) = if (dayOfYear != null) seasonOnDay(dayOfYear.toFloat()) else season(epochMs, zone, place.latitude)
        return SkyState(h.sunElevation, h.sunAzimuth, h.moonElevation, h.moonAzimuth, h.moonFraction, cover = cover, season = season, blossom = shapes.blossom) to h
    }

    /** Local mean sidereal time in degrees (the standard formula, good to a fraction of a degree). */
    fun siderealDegrees(epochMs: Long, longitude: Double): Double {
        val jd = epochMs / 86_400_000.0 + 2440587.5
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
        return ((gmst + longitude) % 360.0 + 360.0) % 360.0
    }

    /**
     * Without Home Assistant's location: a place guessed from the time zone – the zone's big
     * city where it is known, else a latitude of 50° and the longitude its offset implies.
     * Good enough for the sun's colours; the stars and the moon only shift a little.
     */
    fun guessPlace(zone: ZoneId): Place {
        KNOWN[zone.id]?.let { return it }
        val offsetHours = zone.rules.getStandardOffset(Instant.now()).totalSeconds / 3600.0
        return Place(50.0, offsetHours * 15.0)
    }

    private val KNOWN = mapOf(
        "Europe/Berlin" to Place(52.52, 13.40), "Europe/Vienna" to Place(48.21, 16.37), "Europe/Zurich" to Place(47.37, 8.54),
        "Europe/Amsterdam" to Place(52.37, 4.90), "Europe/Brussels" to Place(50.85, 4.35), "Europe/Luxembourg" to Place(49.61, 6.13),
        "Europe/Paris" to Place(48.86, 2.35), "Europe/London" to Place(51.51, -0.13), "Europe/Copenhagen" to Place(55.68, 12.57),
        "Europe/Stockholm" to Place(59.33, 18.07), "Europe/Oslo" to Place(59.91, 10.75), "Europe/Prague" to Place(50.08, 14.44),
        "Europe/Warsaw" to Place(52.23, 21.01), "Europe/Rome" to Place(41.90, 12.50), "Europe/Madrid" to Place(40.42, -3.70),
        "Europe/Busingen" to Place(47.70, 8.69), "Europe/Vaduz" to Place(47.14, 9.52),
    )

    /**
     * The season at [epochMs] for a place at [latitude] (the south has its seasons half a year
     * apart): how much spring, summer, autumn and winter colour the land, and what has changed
     * shape – snow, leaves, blossom. Every value passes into the next over weeks.
     */
    fun season(epochMs: Long, zone: ZoneId, latitude: Double): Pair<SeasonMix, SeasonShapes> {
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        var day = date.dayOfYear - 1 + (date.hour * 60 + date.minute) / 1440.0
        if (latitude < 0) day = (day + 182.6) % 365.25
        return seasonOnDay(day.toFloat())
    }

    /** The season on day [d] of a northern year (0 = 1 January). */
    fun seasonOnDay(d: Float): Pair<SeasonMix, SeasonShapes> {
        // Each season's weight: a plateau with long ramps at both ends (day numbers, not dates).
        val spring = window(d, 68f, 100f, 130f, 161f)
        val summer = window(d, 130f, 161f, 236f, 272f)
        val autumn = window(d, 252f, 278f, 314f, 340f)
        val winter = max(window(d, 323f, 349f, 400f, 420f), window(d + 365f, 323f, 349f, 416f, 449f))
        val sum = (spring + summer + autumn + winter).coerceAtLeast(1e-4f)
        val mix = SeasonMix(spring / sum, summer / sum, autumn / sum, winter / sum)
        val shapes = SeasonShapes(
            snow = max(window(d, 334f, 353f, 410f, 434f), window(d + 365f, 334f, 353f, 410f, 434f)),
            peaks = 1f - window(d, 92f, 170f, 258f, 324f),
            leaves = window(d, 98f, 130f, 288f, 320f),
            blossom = window(d, 94f, 108f, 114f, 130f),
        )
        return mix to shapes
    }

    /** 0 before [a], rising to 1 at [b], 1 until [c], falling to 0 at [e] – smoothly. */
    private fun window(x: Float, a: Float, b: Float, c: Float, e: Float): Float =
        Shapes.smoothstep(a, b, x) * (1f - Shapes.smoothstep(c, e, x))

    private fun max(a: Float, b: Float) = if (a > b) a else b
}

/**
 * Home Assistant's weather, read only as the light's mood: how cloudy the sky is. Rain, snow,
 * fog or storms never fall in the picture – they only make it quieter.
 */
object WeatherMood {
    /** Cover 0 (clear) … 1 (overcast) for a weather entity's [state] and its `cloud_coverage` (%), or null when unknown. */
    fun cover(state: String?, cloudCoverage: Double?): Float? {
        val byState = when (state) {
            "sunny", "clear-night" -> 0.05f
            "windy" -> 0.2f
            "partlycloudy" -> 0.45f
            "windy-variant" -> 0.5f
            "exceptional" -> 0.6f
            "cloudy" -> 0.85f
            "fog" -> 0.9f
            "rainy", "pouring", "snowy", "snowy-rainy", "hail", "lightning", "lightning-rainy" -> 1f
            else -> null
        }
        val byCoverage = cloudCoverage?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
        return when {
            byState != null && byCoverage != null -> (byState + byCoverage) / 2f
            else -> byState ?: byCoverage
        }
    }

    /** Without a weather entity: a fair sky with a few clouds. */
    const val FAIR = 0.25f
}
