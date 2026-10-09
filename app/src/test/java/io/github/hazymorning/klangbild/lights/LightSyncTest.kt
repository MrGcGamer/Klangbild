package io.github.hazymorning.klangbild.lights

import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.color.CoverPalette
import io.github.hazymorning.klangbild.color.Lighting
import io.github.hazymorning.klangbild.config.BarLevel
import io.github.hazymorning.klangbild.config.LightBars
import io.github.hazymorning.klangbild.data.ha.EntityState
import io.github.hazymorning.klangbild.render.CoverField
import io.github.hazymorning.klangbild.render.FieldScene
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Test

/** The bars behind the TV against a stand-in Home Assistant, in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class LightSyncTest {
    private val left = "light.play_links"
    private val right = "light.play_rechts"
    private val bars = LightBars(left = left, right = right)

    /** A cover red on the left, blue on the right. */
    private fun field(): FieldScene {
        val n = 48
        val thumb = IntArray(n * n) { i -> if (i % n < n / 2) 0xFFC8321E.toInt() else 0xFF1E46B4.toInt() }
        return CoverField.build(thumb, n, CoverPalette.analyze(thumb), Lighting.DAY, Appearance.DARK, 1f, layout = null)
    }

    /** A cover of four colours, one per quarter: the field at the edges keeps changing as it moves. */
    private fun fourColours(): FieldScene {
        val n = 48
        val quarters = intArrayOf(0xFFC8321E.toInt(), 0xFF1E46B4.toInt(), 0xFFE0B020.toInt(), 0xFF2E8B57.toInt())
        val thumb = IntArray(n * n) { i -> quarters[(if (i % n < n / 2) 0 else 1) + (if (i / n < n / 2) 0 else 2)] }
        return CoverField.build(thumb, n, CoverPalette.analyze(thumb), Lighting.DAY, Appearance.DARK, 1f, layout = null)
    }

    private class Call(val at: Long, val service: String, val id: String, val data: JsonObject?) {
        val brightness get() = data?.get("brightness")?.jsonPrimitive?.int
        val transition get() = data?.get("transition")?.jsonPrimitive?.double
    }

    /** Home Assistant as far as the bars go: it applies calls to the states, with the call's context. */
    private inner class FakeHa(val scope: TestScope, initial: Map<String, EntityState>) : LightBridge {
        val states = MutableStateFlow(initial)
        override val lights = states
        val calls = mutableListOf<Call>()
        var followed: Set<String> = emptySet()
        private var n = 0

        override fun follow(ids: Set<String>) {
            followed = ids
        }

        override suspend fun callLight(service: String, entityId: String, data: JsonObject?): String {
            val t = scope.testScheduler.currentTime
            calls += Call(t, service, entityId, data)
            val ctx = "ours-${n++}"
            val old = states.value.getValue(entityId)
            val attrs = buildJsonObject {
                old.attributes.forEach { (k, v) -> put(k, v) }
                data?.get("brightness")?.let { put("brightness", it) }
                data?.get("xy_color")?.let { put("xy_color", it); put("color_mode", "xy") }
                data?.get("color_temp_kelvin")?.let { put("color_temp_kelvin", it); put("color_mode", "color_temp") }
            }
            states.value = states.value + (entityId to old.copy(state = if (service == "turn_off") "off" else "on", attributes = attrs, lastUpdated = t / 1000.0 + 0.001, context = ctx))
            return ctx
        }

        /** Someone else changes a bar. */
        fun byHand(id: String, state: String, attrs: JsonObject = JsonObject(emptyMap())) {
            val t = scope.testScheduler.currentTime
            val old = states.value.getValue(id)
            states.value = states.value + (id to old.copy(state = state, attributes = JsonObject(old.attributes + attrs), lastUpdated = t / 1000.0 + 0.002, context = "someone", contextUser = "u1"))
        }

        fun to(id: String) = calls.filter { it.id == id }
    }

    private fun light(id: String, on: Boolean, brightness: Int = 180, kelvin: Int? = 2700) = EntityState(
        id,
        if (on) "on" else "off",
        buildJsonObject {
            if (on) {
                put("brightness", brightness)
                put("color_mode", "color_temp")
                kelvin?.let { put("color_temp_kelvin", it) }
            }
            putJsonArray("supported_color_modes") { add("color_temp"); add("xy") }
        },
        0.0,
        0.0,
        "before",
    )

    private fun TestScope.sync(): LightSync =
        LightSync(backgroundScope, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))

    private fun target(song: String, playing: Boolean = true, level: BarLevel = BarLevel.NORMAL, motion: Float = 0f, f: FieldScene = field) =
        LightSync.Target(bars, level, f, song, playing, night = 0f, warmth = 0.06f, motion = motion, fieldTime = { 0.0 })

    private val field = field()

    @Test
    fun `takes the bars over in the cover's colours, an off one without a flash`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true), right to light(right, on = false)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(3_000)
        assertThat(ha.followed).containsExactly(left, right)
        // The bar that was on goes straight to its colour …
        val l = ha.to(left).single()
        assertThat(l.service).isEqualTo("turn_on")
        assertThat(l.brightness).isEqualTo(51)
        assertThat(l.data!!["xy_color"]!!.jsonArray).hasSize(2)
        // … the one that was off first comes on at the very least, in its new colour, then rises.
        val (pre, up) = ha.to(right)
        assertThat(pre.brightness).isEqualTo(2)
        assertThat(pre.transition).isEqualTo(0.0)
        assertThat(pre.data!!["xy_color"]).isEqualTo(up.data!!["xy_color"])
        assertThat(up.at - pre.at).isEqualTo(LightSync.PRE_ON_MS)
        // Different colours left and right: the cover is red there and blue here.
        assertThat(l.data!!["xy_color"]).isNotEqualTo(up.data!!["xy_color"])
    }

    @Test
    fun `quick skips cost nothing and the song that stays is shown`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true), right to light(right, on = true)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(5_000)
        val before = ha.calls.size
        val green = IntArray(48 * 48) { 0xFF2E8B57.toInt() }
        val other = CoverField.build(green, 48, CoverPalette.analyze(green), Lighting.DAY, Appearance.DARK, 1f, null)
        for ((i, song) in listOf("b", "c", "d").withIndex()) {
            s.update(target(song, f = if (i == 2) other else field))
            testScheduler.advanceTimeBy(400)
        }
        testScheduler.advanceTimeBy(5_000)
        val after = ha.calls.drop(before)
        // One call per bar, for the last song, in a long fade.
        assertThat(after.map { it.id }).containsExactly(left, right)
        assertThat(after.map { it.transition }.toSet()).containsExactly(LightSync.TRACK_TRANSITION_MS / 1000.0)
    }

    @Test
    fun `a pause dims to half, playing brings it back`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true), right to light(right, on = true)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(5_000)
        s.update(target("a", playing = false))
        testScheduler.advanceTimeBy(1_000)
        assertThat(ha.to(left)).hasSize(1)
        testScheduler.advanceTimeBy(3_000)
        val dim = ha.to(left).last()
        assertThat(dim.brightness).isEqualTo(26)
        s.update(target("a", playing = true))
        testScheduler.advanceTimeBy(3_000)
        assertThat(ha.to(left).last().brightness).isEqualTo(51)
        assertThat(ha.to(left).last().transition).isEqualTo(LightSync.RESUME_TRANSITION_MS / 1000.0)
    }

    @Test
    fun `still colours send nothing, moving ones drift in long fades, never faster than a call a second`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true), right to light(right, on = true)))
        val s = sync()
        s.attach(ha)
        val four = fourColours()
        s.update(target("a", f = four))
        testScheduler.advanceTimeBy(60_000)
        assertThat(ha.to(left)).hasSize(1)

        var t = 0.0
        val moving = LightSync.Target(bars, BarLevel.NORMAL, four, "a", true, 0f, 0.06f, 1f) { t }
        s.update(moving)
        // Five minutes: the colour at a screen edge changes slowly, and only visible steps are sent.
        repeat(600) {
            t += 0.5
            testScheduler.advanceTimeBy(500)
        }
        val drift = ha.to(left).drop(1)
        assertThat(drift.size).isAtMost(31)
        assertThat(drift).isNotEmpty()
        assertThat(drift.map { it.transition }.toSet()).containsExactly(LightSync.DRIFT_TRANSITION_MS / 1000.0)
        assertThat(ha.to(left).zipWithNext { a, b -> b.at - a.at }.minOrNull()!!).isAtLeast(LightSync.MIN_GAP_MS)
    }

    @Test
    fun `a bar someone switches off is theirs and stays as they left it`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true, brightness = 200), right to light(right, on = true, brightness = 90, kelvin = 4000)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(5_000)
        ha.byHand(left, "off")
        testScheduler.advanceTimeBy(2_000)
        s.update(target("b"))
        testScheduler.advanceTimeBy(5_000)
        assertThat(ha.to(left)).hasSize(1)
        assertThat(ha.to(right)).hasSize(2)

        // The music ends: after a while the other bar is given back as it was; the switched-off one is left alone.
        s.update(null)
        testScheduler.advanceTimeBy(LightSync.IDLE_RELEASE_MS + 5_000)
        assertThat(ha.to(left)).hasSize(1)
        val back = ha.to(right).last()
        assertThat(back.brightness).isEqualTo(90)
        assertThat(back.data!!["color_temp_kelvin"]).isEqualTo(JsonPrimitive(4000))
        assertThat(ha.states.value.getValue(left).state).isEqualTo("off")
    }

    @Test
    fun `switched off in the menu the bars are given back at once, one that was off goes dark and then off`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true, brightness = 120), right to light(right, on = false)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(5_000)
        val before = ha.calls.size
        s.update(target("a", level = BarLevel.OFF))
        testScheduler.advanceTimeBy(5_000)
        val back = ha.calls.drop(before)
        assertThat(back.first { it.id == left }.brightness).isEqualTo(120)
        val off = back.filter { it.id == right }
        assertThat(off.map { it.service }).containsExactly("turn_on", "turn_off").inOrder()
        assertThat(off[0].brightness).isEqualTo(2)
        // Off without a fade: a faded off leaves a Hue light at 1 %.
        assertThat(off[1].data).isNull()
        assertThat(ha.states.value.getValue(right).state).isEqualTo("off")
        assertThat(ha.followed).isEmpty()
    }

    @Test
    fun `when the app closes the bars go back through the link they were taken with`() = runTest {
        val ha = FakeHa(this, mapOf(left to light(left, on = true, brightness = 120), right to light(right, on = true, brightness = 60)))
        val s = sync()
        s.attach(ha)
        s.update(target("a"))
        testScheduler.advanceTimeBy(5_000)
        val before = ha.calls.size
        val job = s.release()
        s.attach(null)
        testScheduler.advanceTimeBy(5_000)
        assertThat(job.isCompleted).isTrue()
        assertThat(ha.calls.drop(before).map { it.id to it.brightness }).containsExactly(left to 120, right to 60)
        // Nothing more after that.
        testScheduler.advanceTimeBy(60_000)
        assertThat(ha.calls.size).isEqualTo(before + 2)
    }

    @Test
    fun `colours for the lights stay within what they can show, greys are warm white`() {
        val white = HueColor.whiteXy(2700f)
        assertThat(white.x).isWithin(0.002f).of(0.459f)
        assertThat(white.y).isWithin(0.002f).of(0.411f)
        val grey = HueColor.of(floatArrayOf(0.2f, 0.2f, 0.2f), warmth = 0.06f)
        assertThat(grey.distance(white)).isLessThan(0.002f)
        // Evening: a warmer white.
        assertThat(HueColor.of(floatArrayOf(0.2f, 0.2f, 0.2f), warmth = 0.8f).x).isGreaterThan(white.x)
        // A pure sRGB green lies outside the bars' gamut: pulled onto its edge.
        val green = HueColor.of(floatArrayOf(0f, 1f, 0f), warmth = 0f)
        assertThat(HueColor.clamp(green)).isEqualTo(green)
        val outside = Xy(0.05f, 0.85f)
        assertThat(HueColor.clamp(outside).distance(outside)).isGreaterThan(0.05f)
        // Never below the floor while on.
        assertThat(HueColor.brightness(0f)).isEqualTo(HueColor.FLOOR)
        assertThat(HueColor.brightness(0.2f)).isEqualTo(51)
    }

    private fun JsonArray.floats() = map { it.jsonPrimitive.double }
}
