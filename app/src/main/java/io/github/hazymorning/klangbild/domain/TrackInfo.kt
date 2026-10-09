package io.github.hazymorning.klangbild.domain

/**
 * What is playing, normalised from Home Assistant / Music Assistant.
 * Only plain data – presentation decisions live in [TrackText].
 */
data class TrackInfo(
    val title: String,
    /** Individual artist names when known (MA queue), otherwise one combined string. */
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    /** Best artwork URL, absolute. */
    val artworkUrl: String? = null,
    /** Alternative artwork URL to try if [artworkUrl] fails (e.g. the HA proxy). */
    val artworkFallbackUrl: String? = null,
    /** Radio station or other source label, shown when it adds information. */
    val source: String? = null,
    /** Stable identity used for transitions and caching. */
    val key: String = listOf(title, artists.joinToString(), album.orEmpty()).joinToString("|"),
) {
    /** Seed for generated artwork: stable per album/station rather than per song. */
    val artSeed: String get() = source ?: album ?: artists.firstOrNull() ?: title
}
