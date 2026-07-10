package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.http.AbstractApiEntryType
import com.ruslan.apibalego.http.AbstractApiEntryTypeSerializer
import com.ruslan.apibalego.http.asType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import kotlin.reflect.KClass

private val json = Json {
//        ignoreUnknownKeys = true
//        isLenient = true
}

class ClientApiEntryType<T : Any> internal constructor(
    key: Identifier,
    // if null, type has no extra info
    detailsDeserializer: KSerializer<T>?,
    val handler: ClientApiEntryHandler<T>,
    detailsClass: KClass<T>,
) : AbstractApiEntryType<T>(key, detailsDeserializer, detailsClass) {
    fun dispatchUpdate(entries: List<ClientApiEntryRaw>, client: Minecraft) {
        handler.handleApiUpdate(client, entries.map { it.resolve(this, parseDetails(it)) })
    }
}

inline fun <reified T: Any> ClientApiEntryType<*>.asType(): ClientApiEntryType<T> {
    if (detailsType != T::class)
        throw IllegalArgumentException("Type mismatch: $key is not ${T::class.java.simpleName}")
    @Suppress("UNCHECKED_CAST")
    return this as ClientApiEntryType<T>
}

fun interface ClientApiEntryHandler<T : Any> {
    fun handleApiUpdate(client: Minecraft, entries: Collection<ClientApiEntry<T>>)
}

//#region registry

object ClientApiEntryRegistry {
    private val registry = mutableMapOf<Identifier, ClientApiEntryType<*>>()

    fun <T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ClientApiEntryHandler<T>,
        detailsClass: KClass<T>,
    ) {
        registry[key] = ClientApiEntryType(key, detailsDeserializer, handler, detailsClass)
    }

    inline fun <reified T : Any>register(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ClientApiEntryHandler<T>,
    ) {
        register(key, detailsDeserializer, handler, T::class)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (Minecraft, Collection<ClientApiEntry<Nothing>>) -> Unit,
    ) {
        registry[key] = ClientApiEntryType(key, null, updateHandler, Nothing::class)
    }

    fun lookupRaw(key: Identifier) = registry[key] ?: throw UnknownClientApiEntryTypeException(key)

    inline fun <reified T : Any>lookup(key: Identifier) = lookupRaw(key).asType<T>()

    fun dispatchUpdate(all: Collection<ClientApiEntryRaw>, client: Minecraft) {
        val byType = all.groupBy { it.type }
        registry.values.forEach { type ->
            type.dispatchUpdate(byType[type] ?: emptyList(), client)
        }
    }
}

class UnknownClientApiEntryTypeException(key: Identifier) : Exception("Unknown ClientApiEntryType: $key")

//#endregion

// serializers

class ClientApiEntryTypeSerializer : AbstractApiEntryTypeSerializer<ClientApiEntryType<*>>(
    "ClientApiEntryType",
    ClientApiEntryRegistry::lookupRaw,
)