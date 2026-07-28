package com.ruslan.apibalego.socket

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.http.ApiDetailsParser
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import kotlin.reflect.KClass

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
    val payloadType: KClass<T>?,
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
    ): LiveUpdatesEvent<T> = put(
        LiveUpdatesEvent(eventName, deserializer, null, { msg -> json.decodeFromString(deserializer, msg) }, handler)
    )

    /** Register a handler for an event with no meaningful payload (e.g. reload triggers). */
    fun register(
        eventName: String,
        handler: (server: MinecraftServer, sender: ResponseSender) -> Unit,
    ): LiveUpdatesEvent<Unit> {
        val wrapped = LiveUpdatesEventHandler<Unit> { _, server, sender -> handler(server, sender) }
        return put(LiveUpdatesEvent(eventName, null, null, { _ -> Unit }, wrapped))
    }

    /** [register] with the payload parsed by a Minecraft [Codec] instead of a serializer. */
    fun <T : Any> registerWithCodec(
        eventName: String,
        codec: Codec<T>,
        payloadClass: KClass<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> = put(
        LiveUpdatesEvent(eventName, null, payloadClass, { msg ->
            ApiDetailsParser.decodeWithCodec(codec, JsonParser.parseString(msg))
        }, handler)
    )

    /** [register] with the payload parsed by gson as [payloadClass]. */
    fun <T : Any> registerWithClass(
        eventName: String,
        payloadClass: KClass<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> = put(
        LiveUpdatesEvent(eventName, null, payloadClass, { msg ->
            ApiDetailsParser.decodeWithGson(payloadClass.java, msg)
        }, handler)
    )

    /** [register] with the payload handed over as raw json, parsing is up to the mod. */
    fun registerJson(
        eventName: String,
        handler: LiveUpdatesEventHandler<com.google.gson.JsonElement>,
    ): LiveUpdatesEvent<com.google.gson.JsonElement> = put(
        LiveUpdatesEvent(
            eventName,
            null,
            com.google.gson.JsonElement::class,
            { msg -> JsonParser.parseString(msg) },
            handler,
        )
    )

    private fun <T : Any> put(event: LiveUpdatesEvent<T>): LiveUpdatesEvent<T> {
        if (events.put(event.eventName, event) != null) {
            ApibalegoMod.LOGGER.warn("Overwrote live update handler for event '${event.eventName}'")
        }
        return event
    }

    fun all(): Map<String, LiveUpdatesEvent<*>> = events
}
