package com.ruslan.apibalego.api

import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRegistry
import kotlinx.serialization.KSerializer
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object Apibalego {
    inline fun <reified T : Any>registerApiHandler(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
    ) {
        ApiEntryRegistry.register(key, detailsDeserializer, handler)
    }

    fun registerSimple(
        key: Identifier,
        updateHandler: (MinecraftServer, Collection<ApiEntry<Nothing>>) -> Unit,
        joinHandler: ((ServerPlayer, Collection<ApiEntry<Nothing>>) -> Unit)? = null,
    ) {
        ApiEntryRegistry.registerSimple(key, updateHandler, joinHandler)
    }
}