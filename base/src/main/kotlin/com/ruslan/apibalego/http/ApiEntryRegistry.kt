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
    val updateHandler: (MinecraftServer, ApiEntry<T>) -> Unit,
    val joinHandler: (ServerPlayer, ApiEntry<T>) -> Unit,
) {
    fun dispatchUpdate(raw: ApiEntryRaw, server: MinecraftServer) {
        updateHandler.invoke(server, raw.toRaw(this, parseDetails(raw)))
    }

    fun dispatchJoin(raw: ApiEntryRaw, player: ServerPlayer) {
        joinHandler.invoke(player, raw.toRaw(this, parseDetails(raw)))
    }

    private fun parseDetails(raw: ApiEntryRaw) = detailsDeserializer?.let { deserializer ->
        raw.details?.takeIf { it != JsonNull }
            ?.let { d -> json.decodeFromJsonElement(deserializer, d) }
    }
}

object ApiEntryRegistry {
    private val registry = mutableMapOf<Identifier, ApiEntryType<*>>()

    fun <T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        updateHandler: (MinecraftServer, ApiEntry<T>) -> Unit,
        joinHandler: (ServerPlayer, ApiEntry<T>) -> Unit,
    ) {
        registry[key] = ApiEntryType(key, detailsDeserializer, updateHandler, joinHandler)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (MinecraftServer) -> Unit,
        joinHandler: (ServerPlayer) -> Unit,
    ) {
        registry[key] = ApiEntryType<Nothing>(key, null,
            { server, _ -> updateHandler(server) },
            { player, _ -> joinHandler(player) }
        )
    }

    fun lookup(key: Identifier) = registry[key] ?: throw UnknownApiEntryTypeException(key)

    fun dispatchUpdate(raw: ApiEntryRaw, server: MinecraftServer) {
        raw.type.dispatchUpdate(raw, server)
    }

    fun dispatchAllUpdate(all: Collection<ApiEntryRaw>, server: MinecraftServer) {
        all.forEach { dispatchUpdate(it, server) }
    }

    fun dispatchJoin(raw: ApiEntryRaw, player: ServerPlayer) {
        raw.type.dispatchJoin(raw, player)
    }

    fun dispatchAllJoin(all: Collection<ApiEntryRaw>, player: ServerPlayer) {
        all.forEach { dispatchJoin(it, player) }
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