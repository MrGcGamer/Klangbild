package io.github.hazymorning.klangbild.data.ma

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Test

/** Music Assistant's queue items (as `player_queues/items` returns them) as tracks around the current one. */
class MaQueueTest {
    private val base = "http://192.168.1.5:8095"

    private val items = Json.parseToJsonElement(
        """
        [
          {"queue_id":"q1","queue_item_id":"a","name":"Lumen Drive - Neonlicht über der A40","duration":251,
           "media_item":{"media_type":"track","name":"Neonlicht über der A40","version":"",
             "artists":[{"name":"Lumen Drive","item_id":"1","provider":"library","media_type":"artist"}],
             "album":{"name":"Nachtfahrt","year":2024,"item_id":"9","provider":"library","media_type":"album"}},
           "image":{"type":"thumb","path":"/music/nachtfahrt/cover.jpg","provider":"filesystem--x","remotely_accessible":false,"proxy_id":"3f2a"},
           "index":0,"available":true},
          {"queue_id":"q1","queue_item_id":"b","name":"Arvo Pärt - Spiegel im Spiegel (Live)","duration":180.5,
           "media_item":{"media_type":"track","name":"Spiegel im Spiegel","version":"Live","artists":[{"name":"Arvo Pärt"}],
             "album":{"name":"Alina","year":1999}},
           "image":{"type":"thumb","path":"https://i.scdn.co/image/ab67616d00001e02abc","provider":"spotify","remotely_accessible":true,"proxy_id":"77aa"}},
          {"queue_id":"q1","queue_item_id":"c","name":"Radio X","duration":null,
           "media_item":{"media_type":"radio","name":"Radio X"},"streamdetails":{"stream_title":"Jemand - Etwas"},"image":null},
          {"queue_id":"q1","queue_item_id":"d","name":"Nur ein Name","duration":0}
        ]
        """,
    ).jsonArray

    @Test
    fun `items become tracks with their version, year and picture`() {
        val w = MaQueue.window(items, offset = 10, currentIndex = 11, baseUrl = base)
        val played = w.played.single()
        assertThat(played.title).isEqualTo("Neonlicht über der A40")
        assertThat(played.artists).containsExactly("Lumen Drive")
        assertThat(played.album).isEqualTo("Nachtfahrt")
        assertThat(played.year).isEqualTo(2024)
        assertThat(played.durationMs).isEqualTo(251_000L)
        // A local picture comes through Music Assistant's proxy – the larger size first.
        assertThat(played.artworkUrl).isEqualTo("$base/imageproxy/3f2a?size=1024")
        assertThat(played.artworkFallbackUrl).isEqualTo("$base/imageproxy/3f2a?size=512")

        val current = w.current!!
        assertThat(current.title).isEqualTo("Spiegel im Spiegel (Live)")
        assertThat(current.durationMs).isEqualTo(180_500L)
        // A public picture is fetched where it is (the largest rendition), the proxy behind it.
        assertThat(current.artworkUrl).isEqualTo("https://i.scdn.co/image/ab67616d0000b273abc")
        assertThat(current.artworkFallbackUrl).isEqualTo("$base/imageproxy/77aa?size=512")

        val (radio, plain) = w.coming
        assertThat(radio.title).isEqualTo("Jemand - Etwas")
        assertThat(radio.source).isEqualTo("Radio X")
        assertThat(radio.artworkUrl).isNull()
        assertThat(plain.title).isEqualTo("Nur ein Name")
        assertThat(plain.durationMs).isNull()
    }

    @Test
    fun `the window reads far enough around the current item, never before the start`() {
        assertThat(MaQueue.range(5)).isEqualTo(0 to 5 + 1 + MaQueue.REACH)
        assertThat(MaQueue.range(100)).isEqualTo(100 - MaQueue.REACH to 2 * MaQueue.REACH + 1)
        // The current item outside what was read: nothing current, nothing to come.
        val w = MaQueue.window(items, offset = 10, currentIndex = 30, baseUrl = base)
        assertThat(w.current).isNull()
        assertThat(w.coming).isEmpty()
        assertThat(w.played).hasSize(4)
    }
}
