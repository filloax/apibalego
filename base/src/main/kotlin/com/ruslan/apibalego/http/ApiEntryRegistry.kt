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
import kotlin.reflect.KClass

private val json = Json {
//        ignoreUnknownKeys = true
//        isLenient = true
}

abstract class AbstractApiEntryType<T : Any> (
    val key: Identifier,
    val detailsDeserializer: KSerializer<T>?,
    val detailsType: KClass<T>,
) {
    // Some features like preload for packs cannot go through usual dispatch,
    // allow em to resolve anyways
    fun parseDetails(raw: IApiEntryRaw) = detailsDeserializer?.let { deserializer ->
        raw.details?.takeIf { it != JsonNull }
            ?.let { d -> json.decodeFromJsonElement(deserializer, d) }
    }

    override fun toString() = key.toString()
}

class ApiEntryType<T : Any> internal constructor(
    key: Identifier,
    // if null, type has no extra info
    detailsDeserializer: KSerializer<T>?,
    val handler: ApiEntryHandler<T>,
    detailsType: KClass<T>,
) : AbstractApiEntryType<T>(key, detailsDeserializer, detailsType) {
    fun dispatchUpdate(entries: Collection<ApiEntryRaw>, server: MinecraftServer) {
        handler.handleApiUpdate(server, entries.map { it.resolve(this, parseDetails(it)) })
    }

    fun dispatchJoin(entries: Collection<ApiEntryRaw>, player: ServerPlayer) {
        handler.handleApiJoin(player, entries.map { it.resolve(this, parseDetails(it)) })
    }
}

inline fun <reified T: Any> ApiEntryType<*>.asType(): ApiEntryType<T> {
    if (detailsType != T::class)
        throw IllegalArgumentException("Type mismatch: $key is not ${T::class.java.simpleName}")
    @Suppress("UNCHECKED_CAST")
    return this as ApiEntryType<T>
}

interface ApiEntryHandler<T : Any> {
    fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<T>>)

    fun handleApiJoin(player: ServerPlayer, entries: Collection<ApiEntry<T>>) {
        // do nothing on join by default
    }
}

//#region registry

object ApiEntryRegistry {
    private val registry = mutableMapOf<Identifier, ApiEntryType<*>>()

    fun <T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
        detailsClass: KClass<T>,
    ) {
        registry[key] = ApiEntryType(key, detailsDeserializer, handler, detailsClass)
    }

    inline fun <reified T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
    ) {
        register(key, detailsDeserializer, handler, T::class)
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
        registry[key] = ApiEntryType(key, null, handler, Nothing::class)
    }

    fun lookupRaw(key: Identifier) = registry[key] ?: throw UnknownApiEntryTypeException(key)

    inline fun <reified T : Any>lookup(key: Identifier) = lookupRaw(key).asType<T>()

    fun all(): Map<Identifier, ApiEntryType<*>> = registry

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

class UnknownApiEntryTypeException(key: Identifier) : Exception("Unknown ApiEntryType: $key")

//#endregion

//#region Serializers

abstract class AbstractApiEntryTypeSerializer<T : AbstractApiEntryType<*>>(
    serialName: String,
    val lookup: (Identifier) -> T,
) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.key.toString())
    }

    override fun deserialize(decoder: Decoder): T {
        val keyStr = decoder.decodeString()
        val key = Identifier.parse(keyStr)
        return lookup(key)
    }
}

class ApiEntryTypeSerializer : AbstractApiEntryTypeSerializer<ApiEntryType<*>>(
    "ApiEntryType",
    ApiEntryRegistry::lookupRaw,
)

//#endregion