package io.github.hazymorning.klangbild.ui

import io.github.hazymorning.klangbild.art.SyntheticCover
import io.github.hazymorning.klangbild.domain.TrackInfo

/**
 * Invented releases for the screenshot tests. They cover the hard cases: classical
 * work titles, many performers, Japanese and Arabic script, emoji, radio without a cover, a
 * missing album, almost black and almost white covers. Covers are painted by
 * [io.github.hazymorning.klangbild.art.SyntheticCovers]; no real artwork lives in the repo.
 */
object Catalogue {
    const val SCHEME = "test://cover/"

    private fun cover(c: SyntheticCover) = SCHEME + c.name

    /** An audiobook with chapters (named and numbered), read at its own pace or faster. */
    val book = TrackInfo(
        title = "Der Process",
        artists = listOf("Franz Kafka"),
        durationMs = 9_000_000L,
        artworkUrl = cover(SyntheticCover.MIST),
        kind = io.github.hazymorning.klangbild.domain.MediaKind.AUDIOBOOK,
        chapters = io.github.hazymorning.klangbild.domain.Chapters.of(
            listOf(
                io.github.hazymorning.klangbild.domain.Chapters.Raw(1, "Verhaftung", 0.0, null),
                io.github.hazymorning.klangbild.domain.Chapters.Raw(2, "Gespräch mit Frau Grubach – dann Fräulein Bürstner", 1_830.0, null),
                io.github.hazymorning.klangbild.domain.Chapters.Raw(3, "003", 3_710.0, null),
                io.github.hazymorning.klangbild.domain.Chapters.Raw(4, "004", 5_400.0, null),
            ),
            9_000_000L,
        ),
    )

    /** A podcast episode without chapters, and the next one. */
    val episode = TrackInfo(
        title = "Folge 112: Wie Wolken entstehen und warum sie nicht herunterfallen",
        artists = listOf("Zeit für Wissen"),
        durationMs = 2_875_000L,
        artworkUrl = cover(SyntheticCover.SALT_WATER),
        kind = io.github.hazymorning.klangbild.domain.MediaKind.PODCAST,
    )
    val nextEpisode = TrackInfo("Folge 113: Der Mond", listOf("Zeit für Wissen"), kind = io.github.hazymorning.klangbild.domain.MediaKind.PODCAST)

    val tracks: List<TrackInfo> = listOf(
        TrackInfo(
            "Neonlicht über der A40", listOf("Lumen Drive"), "Nachtfahrt", 2024, 251_000,
            artworkUrl = cover(SyntheticCover.NIGHT_DRIVE),
        ),
        TrackInfo(
            "Suite bergamasque, L. 75: III. Clair de lune", listOf("Clara Wendt"),
            "Debussy: Préludes & Suite bergamasque", 2019, 302_000,
            artworkUrl = cover(SyntheticCover.MIST),
        ),
        TrackInfo(
            "Kreis, Quadrat, Linie", listOf("Ensemble Dessau"), "Formen im Raum", 1998, 312_000,
            artworkUrl = cover(SyntheticCover.BAUHAUS),
        ),
        TrackInfo(
            "夜に駆ける", listOf("月島ミナ"), "夜光", 2021, 261_000,
            artworkUrl = cover(SyntheticCover.NOCTURNE),
        ),
        TrackInfo(
            "Klaviersonate Nr. 11 A-Dur, KV 331: III. Alla Turca", listOf("Wolfgang Amadeus Mozart"),
            source = "Radio Morgenlicht",
        ),
        TrackInfo(
            "أنت عمري", listOf("فرقة النيل"), "ليالي القاهرة", 2016, 289_000,
            artworkUrl = cover(SyntheticCover.TERRACOTTA),
        ),
        TrackInfo(
            "Salzwasser (feat. Ida Holm) - Radio Edit", listOf("Nordlicht Kollektiv"), "Gezeiten", 2023, 214_000,
            artworkUrl = cover(SyntheticCover.SALT_WATER),
        ),
        TrackInfo(
            "Symphonie Nr. 9 d-Moll, op. 125: IV. Presto – Allegro assai – Allegro assai vivace (alla marcia) – Andante maestoso",
            listOf(
                "Kammerphilharmonie an der Spree", "Johanna Reiter", "Mira Lindqvist",
                "Tomasz Wierzbicki", "Anselm Krüger", "Chor der Stadtkirche",
            ),
            "Beethoven: Die Sinfonien", 2020, 1_451_000,
            artworkUrl = cover(SyntheticCover.CHAMBER_MUSIC),
        ),
        TrackInfo(
            "🌙 Mondlicht-Tape ✨", listOf("lofi stube"), "Nachtschicht", 2022, 167_000,
            artworkUrl = cover(SyntheticCover.PASTEL),
        ),
        TrackInfo(
            "Unter Moos", listOf("Waldhaus"), album = null, year = null, durationMs = 213_000,
            artworkUrl = cover(SyntheticCover.MOSS),
        ),
        TrackInfo(
            "Konfetti im Kopf", listOf("Die Bunten", "Kalle Sommer"), "Sommerfest", 2025, 185_000,
            artworkUrl = cover(SyntheticCover.CONFETTI),
        ),
        TrackInfo(
            "Weißes Rauschen", listOf("Hanna Stein"), "Leerraum", 2017, 400_000,
            artworkUrl = cover(SyntheticCover.WHITE_SPACE),
        ),
    )
}
