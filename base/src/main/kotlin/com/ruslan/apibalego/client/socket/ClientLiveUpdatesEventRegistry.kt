package com.ruslan.apibalego.client.socket

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.http.ApiDetailsParser
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import kotlin.reflect.KClass

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
    /** Null only for events with no payload; set even when [deserializer] isn't (codec/json events). */
    val payloadType: KClass<T>?,
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
    ): ClientLiveUpdatesEvent<T> = put(
        ClientLiveUpdatesEvent(eventName, deserializer, null, { msg -> json.decodeFromString(deserializer, msg) }, handler)
    )

    /** Register a handler for an event with no meaningful payload (e.g. reload triggers). */
    fun register(
        eventName: String,
        handler: (client: Minecraft, sender: ResponseSender) -> Unit,
    ): ClientLiveUpdatesEvent<Unit> {
        val wrapped = ClientLiveUpdatesEventHandler<Unit> { _, client, sender -> handler(client, sender) }
        return put(ClientLiveUpdatesEvent(eventName, null, null, { _ -> Unit }, wrapped))
    }

    /** [register] with the payload parsed by a Minecraft [Codec] instead of a serializer. */
    fun <T : Any> registerWithCodec(
        eventName: String,
        codec: Codec<T>,
        payloadClass: KClass<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> = put(
        ClientLiveUpdatesEvent(eventName, null, payloadClass, { msg ->
            ApiDetailsParser.decodeWithCodec(codec, JsonParser.parseString(msg))
        }, handler)
    )

    /** [register] with the payload parsed by gson as [payloadClass]. */
    fun <T : Any> registerWithClass(
        eventName: String,
        payloadClass: KClass<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> = put(
        ClientLiveUpdatesEvent(eventName, null, payloadClass, { msg ->
            ApiDetailsParser.decodeWithGson(payloadClass.java, msg)
        }, handler)
    )

    /** [register] with the payload handed over as raw json, parsing is up to the mod. */
    fun registerJson(
        eventName: String,
        handler: ClientLiveUpdatesEventHandler<com.google.gson.JsonElement>,
    ): ClientLiveUpdatesEvent<com.google.gson.JsonElement> = put(
        ClientLiveUpdatesEvent(
            eventName,
            null,
            com.google.gson.JsonElement::class,
            { msg -> JsonParser.parseString(msg) },
            handler,
        )
    )

    private fun <T : Any> put(event: ClientLiveUpdatesEvent<T>): ClientLiveUpdatesEvent<T> {
        if (events.put(event.eventName, event) != null) {
            ApibalegoMod.LOGGER.warn("Overwrote client live update handler for event '${event.eventName}'")
        }
        return event
    }

    fun all(): Map<String, ClientLiveUpdatesEvent<*>> = events
}
