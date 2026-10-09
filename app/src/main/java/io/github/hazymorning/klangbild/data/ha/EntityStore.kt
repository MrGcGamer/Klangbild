package io.github.hazymorning.klangbild.data.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * One entity as received from Home Assistant. Times are epoch seconds. [context] is the id of
 * what made the last change (a service call's, or the device's own report); [contextParent]
 * and [contextUser] say whether an automation or a person in Home Assistant made it.
 */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject,
    val lastChanged: Double,
    val lastUpdated: Double,
    val context: String? = null,
    val contextParent: String? = null,
    val contextUser: String? = null,
) {
    fun attr(name: String): JsonElement? = attributes[name]?.takeUnless { it is JsonNull }
    fun string(name: String): String? = (attr(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun number(name: String): Double? = (attr(name) as? JsonPrimitive)?.doubleOrNull
}

/**
 * Client-side state for a `subscribe_entities` subscription. HA sends one full snapshot
 * (`a`) and afterwards compressed diffs (`c` with `+`/`-`) and removals (`r`). This is a
 * port of the official frontend's `processEvent` (home-assistant-js-websocket, entities.ts).
 */
class EntityStore {
    private val states = LinkedHashMap<String, EntityState>()

    fun clear() = states.clear()

    operator fun get(entityId: String): EntityState? = states[entityId]

    fun snapshot(): Map<String, EntityState> = LinkedHashMap(states)

    /** Applies one subscription event; returns the ids of entities that changed. */
    fun apply(event: JsonObject): Set<String> {
        val changed = LinkedHashSet<String>()
        (event["a"] as? JsonObject)?.forEach { (id, raw) ->
            val s = raw as? JsonObject ?: return@forEach
            val lc = s.num("lc") ?: 0.0
            val c = context(s["c"])
            states[id] = EntityState(
                entityId = id,
                state = s.str("s") ?: "unknown",
                attributes = s["a"] as? JsonObject ?: JsonObject(emptyMap()),
                lastChanged = lc,
                lastUpdated = s.num("lu") ?: lc,
                context = c?.first,
                contextParent = c?.second,
                contextUser = c?.third,
            )
            changed += id
        }
        (event["r"] as? JsonArray)?.forEach { el ->
            val id = (el as? JsonPrimitive)?.content ?: return@forEach
            if (states.remove(id) != null) changed += id
        }
        (event["c"] as? JsonObject)?.forEach { (id, raw) ->
            val current = states[id] ?: return@forEach // diffs for unknown ids are ignored
            val diff = raw as? JsonObject ?: return@forEach
            val plus = diff["+"] as? JsonObject
            var state = current.state
            var lc = current.lastChanged
            var lu = current.lastUpdated
            var attrs = current.attributes
            var c = Triple(current.context, current.contextParent, current.contextUser)
            if (plus != null) {
                context(plus["c"])?.let { c = it }
                plus.str("s")?.let { state = it }
                val plc = plus.num("lc")
                val plu = plus.num("lu")
                if (plc != null) {
                    lc = plc
                    lu = plc
                } else if (plu != null) {
                    lu = plu
                }
                (plus["a"] as? JsonObject)?.let { add -> attrs = JsonObject(attrs + add) }
            }
            ((diff["-"] as? JsonObject)?.get("a") as? JsonArray)?.let { removed ->
                val keys = removed.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
                if (keys.isNotEmpty()) attrs = JsonObject(attrs.filterKeys { it !in keys })
            }
            states[id] = EntityState(id, state, attrs, lc, lu, c.first, c.second, c.third)
            changed += id
        }
        return changed
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    /** A compressed context: its id alone, or {id, parent_id, user_id}. */
    private fun context(raw: JsonElement?): Triple<String?, String?, String?>? = when (raw) {
        is JsonPrimitive -> raw.takeIf { it.isString }?.let { Triple(it.content, null, null) }
        is JsonObject -> Triple(raw.str("id"), raw.str("parent_id"), raw.str("user_id"))
        else -> null
    }
}
