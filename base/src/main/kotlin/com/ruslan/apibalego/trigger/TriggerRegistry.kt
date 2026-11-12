package com.ruslan.apibalego.trigger

import com.ruslan.apibalego.http.ApiDetailsParser
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import kotlin.reflect.KClass

class TriggerType<T : Any> (
    val key: Identifier,
    private val detailsParser: ApiDetailsParser<T>?,
    val handler: TriggerTypeHandler<T>,
    val detailsType: KClass<T>,
) {
    override fun toString() = key.toString()

    fun parseDetails(raw: TriggerRaw) = detailsParser?.let { parser ->
        raw.triggerDetails?.takeIf { it != JsonNull }?.let(parser::parse)
    }

    val hasDetails = detailsParser != null
}

inline fun <reified T: Any> TriggerType<*>.asType(): TriggerType<T> {
    if (detailsType != T::class)
        throw IllegalArgumentException("Type mismatch: $key is not ${T::class.java.simpleName}")
    @Suppress("UNCHECKED_CAST")
    return this as TriggerType<T>
}

interface TriggerTypeHandler<T : Any> {
    /**
     * Identifies the current trigger run.
     * <br>
     * For example, for player respawn, should stay the same as long as player is alive,
     * and change on respawn.
     *
     * If null: not triggered yet
     */
    fun runKey(player: ServerPlayer, details: T?): String?

    fun isTriggered(player: ServerPlayer, details: T?): Boolean = runKey(player, details) != null
}

//#region registry

object TriggerRegistry {
    private val registry = mutableMapOf<Identifier, TriggerType<*>>()

    fun <T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: TriggerTypeHandler<T>,
        detailsClass: KClass<T>,
    ) {
        registry[key] = TriggerType(key, detailsDeserializer?.let { ApiDetailsParser.Kotlinx(it) }, handler, detailsClass)
    }

    inline fun <reified T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: TriggerTypeHandler<T>,
    ) {
        register(key, detailsDeserializer, handler, T::class)
    }

    fun lookupRaw(key: Identifier) = registry[key] ?: throw UnknownTriggerTypeException(key)

    inline fun <reified T : Any>lookup(key: Identifier) = lookupRaw(key).asType<T>()

    fun all(): Map<Identifier, TriggerType<*>> = registry
}

class UnknownTriggerTypeException(key: Identifier) : Exception("Unknown TriggerType: $key")

//#endregion


// Serializer

class TriggerTypeSerializer : KSerializer<TriggerType<*>> {
    override val descriptor = PrimitiveSerialDescriptor("TriggerType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TriggerType<*>) {
        encoder.encodeString(value.key.toString())
    }

    override fun deserialize(decoder: Decoder): TriggerType<*> {
        val keyStr = decoder.decodeString()
        val key = Identifier.parse(keyStr)
        return TriggerRegistry.lookupRaw(key)
    }
}