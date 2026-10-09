package io.github.hazymorning.klangbild.data.ha

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class EntityStoreTest {
    private fun ev(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `applies add, change, attribute removal and remove`() {
        val store = EntityStore()
        store.apply(ev("""{"a":{"media_player.wz":{"s":"playing","a":{"media_title":"A","media_position":10},"c":"01K","lc":100.5,"lu":101.0}}}"""))
        var st = store["media_player.wz"]!!
        assertThat(st.state).isEqualTo("playing")
        assertThat(st.string("media_title")).isEqualTo("A")
        assertThat(st.lastChanged).isEqualTo(100.5)
        assertThat(st.lastUpdated).isEqualTo(101.0)

        val changed = store.apply(ev("""{"c":{"media_player.wz":{"+":{"s":"paused","lc":200.0,"a":{"media_position":80}},"-":{"a":["media_title"]}}}}"""))
        assertThat(changed).containsExactly("media_player.wz")
        st = store["media_player.wz"]!!
        assertThat(st.state).isEqualTo("paused")
        assertThat(st.number("media_position")).isEqualTo(80.0)
        assertThat(st.string("media_title")).isNull()
        assertThat(st.lastChanged).isEqualTo(200.0)
        assertThat(st.lastUpdated).isEqualTo(200.0)

        store.apply(ev("""{"c":{"media_player.wz":{"+":{"lu":250.0,"a":{"volume_level":0.4}}}}}"""))
        st = store["media_player.wz"]!!
        assertThat(st.state).isEqualTo("paused")
        assertThat(st.lastChanged).isEqualTo(200.0)
        assertThat(st.lastUpdated).isEqualTo(250.0)
        assertThat(st.number("media_position")).isEqualTo(80.0)

        store.apply(ev("""{"r":["media_player.wz"]}"""))
        assertThat(store["media_player.wz"]).isNull()
    }

    @Test
    fun `missing lu means lu equals lc and unknown ids are ignored`() {
        val store = EntityStore()
        store.apply(ev("""{"a":{"sun.sun":{"s":"below_horizon","a":{"elevation":-12.3},"c":"x","lc":5.0}}}"""))
        assertThat(store["sun.sun"]!!.lastUpdated).isEqualTo(5.0)
        val changed = store.apply(ev("""{"c":{"media_player.ghost":{"+":{"s":"playing"}}}}"""))
        assertThat(changed).isEmpty()
        assertThat(store["media_player.ghost"]).isNull()
    }
}
