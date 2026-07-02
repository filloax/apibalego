package com.ruslan.apibalego.http

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

private val json = Json {
//        ignoreUnknownKeys = true
//        isLenient = true
}

class ApiEntryType<T : Any> internal constructor(
    val key: Identifier,
    // if null, type has no extra info
    val detailsDeserializer: KSerializer<T>?,
    val handler: ApiEntryHandler<T>,
) {
    fun dispatchUpdate(entries: Collection<ApiEntryRaw>, server: MinecraftServer) {
        handler.handleApiUpdate(server, entries.map { it.resolve(this, parseDetails(it)) })
    }

    fun dispatchJoin(entries: Collection<ApiEntryRaw>, player: ServerPlayer) {
        handler.handleApiJoin(player, entries.map { it.resolve(this, parseDetails(it)) })
    }

    private fun parseDetails(raw: ApiEntryRaw) = detailsDeserializer?.let { deserializer ->
        raw.details?.takeIf { it != JsonNull }
            ?.let { d -> json.decodeFromJsonElement(deserializer, d) }
    }

    override fun toString() = key.toString()
}

interface ApiEntryHandler<T : Any> {
    fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<T>>)

    fun handleApiJoin(player: ServerPlayer, entries: Collection<ApiEntry<T>>) {
        // do nothing on join by default
    }
}

object ApiEntryRegistry {
    private val registry = mutableMapOf<Identifier, ApiEntryType<*>>()

    fun <T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
    ) {
        registry[key] = ApiEntryType(key, detailsDeserializer, handler)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (MinecraftServer, Collection<ApiEntry<Nothing>>) -> Unit,
        joinHandler: ((ServerPlayer, Collection<ApiEntry<Nothing>>) -> Unit)? = null,
    ) {
        val handler = object : ApiEntryHandler<Nothing> {
            override fun handleApiUpdate(
                server: MinecraftServer,
                entries: Collection<ApiEntry<Nothing>>
            ) {
                updateHandler(server, entries)
            }

            override fun handleApiJoin(player: ServerPlayer, entries: Collection<ApiEntry<Nothing>>) {
                joinHandler?.invoke(player, entries)
            }
        }
        registry[key] = ApiEntryType(key, null, handler)
    }

    fun lookup(key: Identifier) = registry[key] ?: throw UnknownApiEntryTypeException(key)

    fun dispatchUpdate(all: Collection<ApiEntryRaw>, server: MinecraftServer) {
        val byType = all.groupBy { it.type }
        registry.values.forEach { type ->
            type.dispatchUpdate(byType[type] ?: emptyList(), server)
        }
    }

    fun dispatchJoin(all: Collection<ApiEntryRaw>, player: ServerPlayer) {
        all.groupBy { it.type }.forEach { (type, entries) ->
            type.dispatchJoin(entries, player)
        }
    }
}

class ApiEntryTypeSerializer : KSerializer<ApiEntryType<*>> {
    override val descriptor = PrimitiveSerialDescriptor("ApiEntryType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ApiEntryType<*>) {
        encoder.encodeString(value.key.toString())
    }

    override fun deserialize(decoder: Decoder): ApiEntryType<*> {
        val keyStr = decoder.decodeString()
        val key = Identifier.parse(keyStr)
        return ApiEntryRegistry.lookup(key)
    }
}

class UnknownApiEntryTypeException(key: Identifier) : Exception("Unknown ApiEntryType: $key")