package io.github.hazymorning.klangbild.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A button of one's own in the quick settings, set up on the phone: its [name] and the Home
 * Assistant entity it works. What it does follows from the entity's kind ([ActionKind]): a
 * script runs (and learns which speaker the TV follows), a scene is activated, an automation
 * runs its actions, a button is pressed, a switch is turned on or off.
 */
data class QuickAction(val name: String, val entity: String) {
    val kind: ActionKind? get() = ActionKind.of(entity)
}

/** What a button does with its entity, by the entity's domain. */
enum class ActionKind(val domains: List<String>) {
    /** Runs a script, or an automation's actions. */
    RUN(listOf("script", "automation")),

    /** Activates a scene. */
    ACTIVATE(listOf("scene")),

    /** Presses a button. */
    PRESS(listOf("button", "input_button")),

    /** Switches something on or off, and shows which it is. */
    TOGGLE(listOf("switch", "input_boolean", "light")),
    ;

    companion object {
        /** The kind for [entity] (`script.ungroup`), null for an entity no button can work. */
        fun of(entity: String): ActionKind? {
            val domain = entity.substringBefore('.', "")
            return entries.firstOrNull { domain in it.domains }
        }

        /** Every domain a button can work, in the order the setup page lists them. */
        val DOMAINS: List<String> = entries.flatMap { it.domains }
    }
}

/** The buttons as Klangbild keeps them: a small JSON list of names and entities. */
object QuickActions {
    /** At most this many buttons: the quick settings stay a short row. */
    const val MAX = 6

    /** The longest name a button may have (characters). */
    const val MAX_NAME = 32

    /** [actions] as text to store. */
    fun encode(actions: List<QuickAction>): String = buildJsonArray {
        for (a in actions) {
            add(
                buildJsonObject {
                    put("name", a.name)
                    put("entity", a.entity)
                },
            )
        }
    }.toString()

    /** The buttons stored as [text]; whatever cannot be read is left out. */
    fun decode(text: String?): List<QuickAction> {
        if (text.isNullOrBlank()) return emptyList()
        val list = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val entity = (o["entity"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            valid(name, entity)
        }.take(MAX)
    }

    /**
     * A button from what the phone sent: an entity a button can work, and a name (cut to
     * [MAX_NAME] characters; without one, the entity's own words). Null when it is no button.
     */
    fun valid(name: String?, entity: String?): QuickAction? {
        val id = entity?.trim()?.takeIf { LaunchExtras.isEntityId(it) && ActionKind.of(it) != null } ?: return null
        val words = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: nameOf(id)
        return QuickAction(words.take(MAX_NAME).trimEnd(), id)
    }

    /** An entity's own words: `script.ungroup_speakers` is "Ungroup speakers". */
    fun nameOf(entity: String): String =
        entity.substringAfter('.').replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }
}
