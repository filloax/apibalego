package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.http.AbstractApiEntryType
import com.ruslan.apibalego.http.AbstractApiEntryTypeSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

private val json = Json {
//        ignoreUnknownKeys = true
//        isLenient = true
}

class ClientApiEntryType<T : Any> internal constructor(
    key: Identifier,
    // if null, type has no extra info
    detailsDeserializer: KSerializer<T>?,
    val handler: ClientApiEntryHandler<T>,
) : AbstractApiEntryType<T>(key, detailsDeserializer) {
    fun dispatchUpdate(entries: List<ClientApiEntryRaw>, client: Minecraft) {
        handler.handleApiUpdate(client, entries.map { it.resolve(this, parseDetails(it)) })
    }
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
    ) {
        registry[key] = ClientApiEntryType(key, detailsDeserializer, handler)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (Minecraft, Collection<ClientApiEntry<Nothing>>) -> Unit,
    ) {
        registry[key] = ClientApiEntryType(key, null, updateHandler)
    }

    fun lookup(key: Identifier) = registry[key] ?: throw UnknownClientApiEntryTypeException(key)

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
    ClientApiEntryRegistry::lookup,
)