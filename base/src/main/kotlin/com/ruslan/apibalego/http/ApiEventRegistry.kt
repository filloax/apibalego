package com.ruslan.apibalego.http

import com.ruslan.apibalego.Apibalego
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

fun interface ApiEventHandler {
    fun handle(event: GenericApiEvent, server: MinecraftServer)
}

fun interface ApiEventJoinHandler {
    fun handle(event: GenericApiEvent, player: ServerPlayer)
}

/**
 * Registry for handlers reacting to gamemaster events polled via [DataRemoteSync].
 * Handlers are keyed by event name prefix (the part before the first '/').
 *
 * Apibalego registers built-in "toast" and "cmd" handlers; consumer mods register their own.
 */
object ApiEventRegistry {
    private val handlers = mutableMapOf<String, ApiEventHandler>()
    private val joinHandlers = mutableMapOf<String, ApiEventJoinHandler>()

    fun registerHandler(prefix: String, handler: ApiEventHandler) {
        if (handlers.put(prefix, handler) != null) {
            Apibalego.LOGGER.warn("Overwrote api event handler for prefix '$prefix'")
        }
    }

    fun registerJoinHandler(prefix: String, handler: ApiEventJoinHandler) {
        if (joinHandlers.put(prefix, handler) != null) {
            Apibalego.LOGGER.warn("Overwrote api event join handler for prefix '$prefix'")
        }
    }

    fun dispatch(events: List<GenericApiEvent>, server: MinecraftServer) {
        events.forEach { event ->
            if (event.active) {
                val prefix = event.name.split("/")[0]
                handlers[prefix]?.handle(event, server)
            }
        }
    }

    fun dispatchJoin(events: List<GenericApiEvent>, player: ServerPlayer) {
        events.forEach { event ->
            if (event.active) {
                val prefix = event.name.split("/")[0]
                joinHandlers[prefix]?.handle(event, player)
            }
        }
    }
}
