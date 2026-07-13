package com.ruslan.apibalego.api.client

import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import kotlinx.serialization.KSerializer
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object ApibalegoClient {
    inline fun <reified T : Any>registerApiHandler(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ClientApiEntryHandler<T>,
    ) {
        ClientApiEntryRegistry.register(key, detailsDeserializer, handler)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (Minecraft, Collection<ClientApiEntry<Nothing>>) -> Unit,
    ) {
        ClientApiEntryRegistry.registerSimple(key, updateHandler)
    }
}