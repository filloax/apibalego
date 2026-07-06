package com.ruslan.apibalego.client.socket

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun interface ClientLiveUpdatesEventHandler<T : Any> {
    fun handleEvent(data: T, client: Minecraft, sender: ResponseSender)
}

/** Client-side counterpart to [com.ruslan.apibalego.socket.LiveUpdatesEvent]. */
class ClientLiveUpdatesEvent<T : Any> internal constructor(
    val eventName: String,
    val deserializer: KSerializer<T>?,
    private val parse: (String) -> T,
    private val handler: ClientLiveUpdatesEventHandler<T>,
) {
    fun dispatch(message: String, client: Minecraft, sender: ResponseSender) {
        handler.handleEvent(parse(message), client, sender)
    }
}

/**
 * Client-side counterpart to [com.ruslan.apibalego.socket.LiveUpdatesEventRegistry]: handlers
 * reacting to real-time websocket events on [ClientLiveUpdatesConnection], keyed by socket.io
 * event type. Separate namespace from the server-side registry, so event names may overlap
 * (e.g. both have a "reload" and a "toast").
 */
object ClientLiveUpdatesEventRegistry {
    private val events = mutableMapOf<String, ClientLiveUpdatesEvent<*>>()

    fun <T : Any> register(
        eventName: String,
        deserializer: KSerializer<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> {
        val event = ClientLiveUpdatesEvent(eventName, deserializer, { msg -> json.decodeFromString(deserializer, msg) }, handler)
        if (events.put(eventName, event) != null) {
            Apibalego.LOGGER.warn("Overwrote client live update handler for event '$eventName'")
        }
        return event
    }

    /** Register a handler for an event with no meaningful payload (e.g. reload triggers). */
    fun register(
        eventName: String,
        handler: (client: Minecraft, sender: ResponseSender) -> Unit,
    ): ClientLiveUpdatesEvent<Unit> {
        val wrapped = ClientLiveUpdatesEventHandler<Unit> { _, client, sender -> handler(client, sender) }
        val event = ClientLiveUpdatesEvent(eventName, null, { _ -> Unit }, wrapped)
        if (events.put(eventName, event) != null) {
            Apibalego.LOGGER.warn("Overwrote client live update handler for event '$eventName'")
        }
        return event
    }

    fun all(): Map<String, ClientLiveUpdatesEvent<*>> = events
}
