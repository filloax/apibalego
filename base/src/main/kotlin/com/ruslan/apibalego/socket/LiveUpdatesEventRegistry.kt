package com.ruslan.apibalego.socket

import com.ruslan.apibalego.Apibalego
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun interface LiveUpdatesEventHandler<T : Any> {
    fun handleEvent(data: T, server: MinecraftServer, sender: ResponseSender)
}

/**
 * Registration artifact for a websocket event channel. Owns deserialization of the raw socket
 * message into [T] before dispatching to [handler]. Analogous to [com.ruslan.apibalego.http.ApiEntryType].
 */
class LiveUpdatesEvent<T : Any> internal constructor(
    val eventName: String,
    val deserializer: KSerializer<T>?,
    private val parse: (String) -> T,
    private val handler: LiveUpdatesEventHandler<T>,
) {
    fun dispatch(message: String, server: MinecraftServer, sender: ResponseSender) {
        handler.handleEvent(parse(message), server, sender)
    }
}

/**
 * Registry for handlers reacting to real-time websocket events on the [LiveUpdatesConnection].
 * Keyed by socket.io event type.
 *
 * Apibalego registers built-in "reload", "toast" and "cmd"; consumer mods register their own
 * (e.g. growsseth's "rdialogue").
 */
object LiveUpdatesEventRegistry {
    private val events = mutableMapOf<String, LiveUpdatesEvent<*>>()

    fun <T : Any> register(
        eventName: String,
        deserializer: KSerializer<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> {
        val event = LiveUpdatesEvent(eventName, deserializer, { msg -> json.decodeFromString(deserializer, msg) }, handler)
        if (events.put(eventName, event) != null) {
            Apibalego.LOGGER.warn("Overwrote live update handler for event '$eventName'")
        }
        return event
    }

    /** Register a handler for an event with no meaningful payload (e.g. reload triggers). */
    fun register(
        eventName: String,
        handler: (server: MinecraftServer, sender: ResponseSender) -> Unit,
    ): LiveUpdatesEvent<Unit> {
        val wrapped = LiveUpdatesEventHandler<Unit> { _, server, sender -> handler(server, sender) }
        val event = LiveUpdatesEvent(eventName, null, { _ -> Unit }, wrapped)
        if (events.put(eventName, event) != null) {
            Apibalego.LOGGER.warn("Overwrote live update handler for event '$eventName'")
        }
        return event
    }

    fun all(): Map<String, LiveUpdatesEvent<*>> = events
}
