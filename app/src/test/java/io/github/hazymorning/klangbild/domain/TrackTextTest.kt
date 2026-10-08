package io.github.hazymorning.klangbild.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackTextTest {
    private fun t(title: String, artists: List<String> = listOf("A"), album: String? = null) =
        TrackText.from(TrackInfo(title, artists, album))

    @Test
    fun `splits classical work and movement`() {
        val x = t("Suite bergamasque, L. 75: III. Clair de lune")
        assertThat(x.work).isEqualTo("Suite bergamasque, L. 75")
        assertThat(x.title).isEqualTo("III. Clair de lune")

        val y = t("Symphony No. 9 in D Minor, Op. 125 \"Choral\": IV. Presto - Allegro assai")
        assertThat(y.work).isEqualTo("Symphony No. 9 in D Minor, Op. 125 \"Choral\"")
        assertThat(y.title).isEqualTo("IV. Presto\u00A0– Allegro assai")

        val z = t("Goldberg-Variationen, BWV 988: Aria")
        assertThat(z.work).isEqualTo("Goldberg-Variationen, BWV 988")
        assertThat(z.title).isEqualTo("Aria")
    }

    @Test
    fun `keeps ordinary colons in pop titles`() {
        val x = t("Interlude: Shine")
        assertThat(x.work).isNull()
        assertThat(x.title).isEqualTo("Interlude: Shine")
    }

    @Test
    fun `moves featured artists and version notes out of the title`() {
        val x = t("Get Lucky (feat. Pharrell Williams & Nile Rodgers) - Radio Edit", listOf("Daft Punk"))
        assertThat(x.title).isEqualTo("Get Lucky")
        assertThat(x.version).isEqualTo("Radio Edit")
        assertThat(x.artists).isEqualTo("Daft Punk, Pharrell Williams & Nile Rodgers")

        val y = t("Wish You Were Here - 2011 Remaster")
        assertThat(y.title).isEqualTo("Wish You Were Here")
        assertThat(y.version).isEqualTo("2011 Remaster")

        val z = t("Heroes (Remastered 2017)")
        assertThat(z.title).isEqualTo("Heroes")
        assertThat(z.version).isEqualTo("Remastered 2017")

        // Not a version note: must stay.
        assertThat(t("(I Can't Get No) Satisfaction").title).isEqualTo("(I Can't Get No) Satisfaction")
        assertThat(t("Live").title).isEqualTo("Live")
    }

    @Test
    fun `formats artists and shortens long lists`() {
        assertThat(TrackText.formatArtists(emptyList())).isNull()
        assertThat(TrackText.formatArtists(listOf("Nina Simone"))).isEqualTo("Nina Simone")
        assertThat(TrackText.formatArtists(listOf("A", "B"))).isEqualTo("A & B")
        assertThat(TrackText.formatArtists(listOf("A", "B", "C"))).isEqualTo("A, B & C")
        val orchestra = listOf(
            "Berliner Philharmoniker", "Herbert von Karajan", "Wiener Singverein",
            "Gundula Janowitz", "Hilde Rössel-Majdan", "Waldemar Kmentt", "Walter Berry",
        )
        val s = TrackText.formatArtists(orchestra)!!
        assertThat(s).endsWith(" und weitere")
        assertThat(s.length).isAtMost(TrackText.MAX_ARTISTS_CHARS)
        assertThat(s).startsWith("Berliner Philharmoniker, Herbert von Karajan")
    }

    @Test
    fun `drops an album that only repeats the title`() {
        assertThat(t("Blackstar", album = "Blackstar").album).isNull()
        assertThat(t("Lazarus", album = "Blackstar").album).isEqualTo("Blackstar")
    }
}
