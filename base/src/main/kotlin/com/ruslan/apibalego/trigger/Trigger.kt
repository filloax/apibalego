package com.ruslan.apibalego.trigger

import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.ApiEntryTypeSerializer
import com.ruslan.apibalego.http.ID_API_HANDLER_TRIGGER
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TriggerRaw(
    val events: List<TriggerApiEntry>,
    @Serializable(with = TriggerTypeSerializer::class)
    val triggerType: TriggerType<*>,
    val triggerDetails: JsonElement? = null,
    /** Only trigger for player with this name or UUID */
    val onlyPlayer: String? = null,
    /** Only trigger for players with these names or UUIDs */
    val onlyPlayers: Set<String> = setOf(),
) {
    fun <T: Any> resolve(parsedType: TriggerType<T>): Trigger<T> {
        val parsedDetails = parsedType.parseDetails(this)

        if (parsedType.hasDetails != (parsedDetails != null)) {
            throw IllegalArgumentException("Type mismatch: ${parsedType.key} must ${if (parsedType.hasDetails) "" else "not "}have details, but details are ${parsedDetails != null}")
        }

        if (events.any { it.type.key == ID_API_HANDLER_TRIGGER }) {
            throw IllegalArgumentException("Nested triggers are not supported")
        }

        return Trigger(
            events.mapIndexed { i, ev -> ev.withKey(i) },
            parsedType,
            parsedDetails,
            onlyPlayers + (onlyPlayer?.let { setOf(it) } ?: setOf())
        )
    }
}

class Trigger<T : Any>(
    val events: List<TriggerApiEntry>,
    val triggerType: TriggerType<T>,
    val triggerDetails: T? = null,
    val onlyPlayers: Set<String> = setOf(),
)

/** Subset of ApiEntryRaw fields */
@Serializable
data class TriggerApiEntry(
    @Serializable(with = ApiEntryTypeSerializer::class)
    val type: ApiEntryType<*>,
    val details: JsonElement? = null,
    val key: String? = null,
) {
    val childKey: String get() = key ?: error("Key not set, resolve the trigger first")

    internal fun withKey(index: Int) = if (key != null) this else copy(key = index.toString())
}
