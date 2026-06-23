package com.ruslan.apibalego.http

import com.ruslan.apibalego.Apibalego
import net.minecraft.server.MinecraftServer

fun interface LiveUpdatesEventHandler {
    fun handle(message: String, server: MinecraftServer, sender: ResponseSender)
}

/**
 * Registry for handlers reacting to real-time websocket events on the [LiveUpdatesConnection].
 * Keyed by socket.io event type.
 *
 * Apibalego registers built-in "reload", "toast" and "cmd"; consumer mods register their own
 * (e.g. growsseth's "rdialogue").
 */
object LiveUpdatesEventRegistry {
    const val RELOAD_EVENT = "reload"

    private val handlers = mutableMapOf<String, LiveUpdatesEventHandler>()

    fun register(eventType: String, handler: LiveUpdatesEventHandler) {
        if (handlers.put(eventType, handler) != null) {
            Apibalego.LOGGER.warn("Overwrote live update handler for event '$eventType'")
        }
    }

    fun all(): Map<String, LiveUpdatesEventHandler> = handlers
}
