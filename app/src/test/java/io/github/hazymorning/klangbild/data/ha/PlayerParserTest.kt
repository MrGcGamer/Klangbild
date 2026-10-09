package io.github.hazymorning.klangbild.data.ha

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.domain.PlayerStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class PlayerParserTest {
    private val base = "http://192.168.1.5:8123"
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun entity(state: String, attrs: String) = EntityState("media_player.wz", state, obj(attrs), 0.0, 0.0)

    @Test
    fun `maps a music assistant player`() {
        val p = PlayerParser.player(
            entity(
                "playing",
                """{"media_title":"Bohemian Rhapsody (Remastered 2011)","media_artist":"Queen / David Bowie",
                   "media_album_name":"A Night at the Opera","media_duration":355,"media_position":73,
                   "media_position_updated_at":"2026-10-08T18:21:07.512345+00:00",
                   "entity_picture":"http://192.168.1.6:8095/imageproxy/ab?size=512&fmt=jpg",
                   "entity_picture_local":"/api/media_player_proxy/media_player.wz?token=t&cache=c",
                   "app_id":"music_assistant","mass_player_type":"player","active_queue":"q1","friendly_name":"Wohnzimmer"}""",
            ),
            base,
        )
        assertThat(p.status).isEqualTo(PlayerStatus.PLAYING)
        assertThat(p.isMusicAssistant).isTrue()
        assertThat(p.activeQueue).isEqualTo("q1")
        val t = p.track!!
        assertThat(t.title).isEqualTo("Bohemian Rhapsody (Remastered 2011)")
        assertThat(t.artists).containsExactly("Queen", "David Bowie").inOrder()
        assertThat(t.durationMs).isEqualTo(355_000)
        assertThat(t.artworkUrl).isEqualTo("http://192.168.1.6:8095/imageproxy/ab?size=1024&fmt=jpg")
        assertThat(t.artworkFallbackUrl).isEqualTo("http://192.168.1.5:8123/api/media_player_proxy/media_player.wz?token=t&cache=c")
        assertThat(p.positionMs).isEqualTo(73_000)
        assertThat(p.positionAtEpochMs).isEqualTo(1791483667512L)
    }

    @Test
    fun `group members without this speaker, volume and mute`() {
        val leader = PlayerParser.player(
            entity(
                "playing",
                """{"media_title":"X","group_members":["media_player.wz","media_player.kueche","media_player.bad","media_player.kueche"],
                   "volume_level":0.35,"is_volume_muted":false}""",
            ),
            base,
        )
        assertThat(leader.groupMembers).containsExactly("media_player.kueche", "media_player.bad").inOrder()
        assertThat(leader.volume).isWithin(1e-6f).of(0.35f)
        assertThat(leader.muted).isFalse()

        // A member lists the leader first – it is one of the others, too.
        val member = PlayerParser.player(
            entity("playing", """{"media_title":"X","group_members":["media_player.kueche","media_player.wz"],"is_volume_muted":true}"""),
            base,
        )
        assertThat(member.groupMembers).containsExactly("media_player.kueche")
        assertThat(member.volume).isNull()
        assertThat(member.muted).isTrue()

        val alone = PlayerParser.player(entity("playing", """{"media_title":"X","group_members":["media_player.wz"]}"""), base)
        assertThat(alone.groupMembers).isEmpty()
        val noAttribute = PlayerParser.player(entity("playing", """{"media_title":"X","group_members":null}"""), base)
        assertThat(noAttribute.groupMembers).isEmpty()
    }

    @Test
    fun `state strings and missing data`() {
        assertThat(PlayerParser.status("buffering")).isEqualTo(PlayerStatus.BUFFERING)
        assertThat(PlayerParser.status("standby")).isEqualTo(PlayerStatus.IDLE)
        assertThat(PlayerParser.status("unavailable")).isEqualTo(PlayerStatus.UNAVAILABLE)
        assertThat(PlayerParser.status("weird")).isEqualTo(PlayerStatus.UNKNOWN)
        val off = PlayerParser.player(entity("off", """{"friendly_name":"WZ"}"""), base)
        assertThat(off.track).isNull()
        val idle = PlayerParser.player(entity("idle", """{"media_title":""}"""), base)
        assertThat(idle.track).isNull()
        assertThat(PlayerParser.parseInstant("2026-10-08T18:21:07+00:00")).isEqualTo(1791483667000L)
        assertThat(PlayerParser.parseInstant("nonsense")).isNull()
    }

    @Test
    fun `parses get_queue response with next item and radio`() {
        val response = obj(
            """{"media_player.wz":{"queue_id":"q1","current_index":5,"elapsed_time":73,
              "current_item":{"queue_item_id":"9c","name":"Queen - Bohemian Rhapsody","duration":355,
                "media_item":{"media_type":"track","name":"Bohemian Rhapsody","version":"Remastered 2011",
                  "image":"https://i.scdn.co/image/ab67616d00001e02cafe",
                  "artists":[{"name":"Queen"}],"album":{"name":"A Night at the Opera","image":null}}},
              "next_item":{"name":"x","duration":200,"media_item":{"media_type":"radio","name":"SWR2","image":"/api/x.png","artists":[]},
                "stream_title":"Mozart – Klaviersonate Nr. 11"}}}""",
        )
        val q = PlayerParser.queue(response, "media_player.wz", base)!!
        assertThat(q.current!!.title).isEqualTo("Bohemian Rhapsody (Remastered 2011)")
        assertThat(q.current!!.artists).containsExactly("Queen")
        assertThat(q.current!!.artworkUrl).isEqualTo("https://i.scdn.co/image/ab67616d0000b273cafe")
        assertThat(q.next!!.title).isEqualTo("Mozart – Klaviersonate Nr. 11")
        assertThat(q.next!!.source).isEqualTo("SWR2")
        assertThat(q.next!!.artworkUrl).isEqualTo("http://192.168.1.5:8123/api/x.png")
    }
}
