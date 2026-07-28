package com.ruslan.apibalego.api.client

import com.google.gson.JsonElement
import com.mojang.serialization.Codec
import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesEvent
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesEventHandler
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesEventRegistry
import com.ruslan.apibalego.http.ApiDetailsParser
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.KSerializer
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.util.function.BiConsumer

/**
 * Client-side counterpart of [com.ruslan.apibalego.api.Apibalego].
 * As there, use the KSerializer method variants for kotlin mods,
 * and any of the others for Java
 */
object ApibalegoClient {
    //#region api entries

    /**
      Use this in Kotlin mods, pass for deserializer for example
      MyDetailsClass.serializer()
     */
    inline fun <reified T : Any>registerApiHandler(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ClientApiEntryHandler<T>,
    ) {
        ClientApiEntryRegistry.register(key, detailsDeserializer, handler)
    }

    /**
     * Use Minecraft Codecs to parse `details`.
     */
    @JvmStatic
    fun <T : Any> registerApiHandler(
        key: Identifier,
        detailsCodec: Codec<T>,
        detailsClass: Class<T>,
        handler: ClientApiEntryHandler<T>,
    ) {
        ClientApiEntryRegistry.registerWithParser(
            key,
            ApiDetailsParser.FromCodec(detailsCodec),
            handler,
            detailsClass.kotlin,
        )
    }

    /**
     * Use gson to parse `details` as the class.
     */
    @JvmStatic
    fun <T : Any> registerApiHandler(
        key: Identifier,
        detailsClass: Class<T>,
        handler: ClientApiEntryHandler<T>,
    ) {
        ClientApiEntryRegistry.registerWithParser(
            key,
            ApiDetailsParser.FromClass(detailsClass),
            handler,
            detailsClass.kotlin,
        )
    }

    /**
     * With this you have to handle parsing `details` yourself
     */
    @JvmStatic
    fun registerJsonApiHandler(key: Identifier, handler: ClientApiEntryHandler<JsonElement>) {
        ClientApiEntryRegistry.registerWithParser(
            key,
            ApiDetailsParser.RawJson,
            handler,
            JsonElement::class,
        )
    }

    /**
     * Simple events with no details
     */
    @JvmSynthetic
    fun registerSimple(
        key: Identifier,
        updateHandler: (Minecraft, Collection<ClientApiEntry<Nothing>>) -> Unit,
    ) {
        ClientApiEntryRegistry.registerSimple(key, updateHandler)
    }

    /**
     * Simple events with no details
     */
    @JvmStatic
    fun registerSimple(
        key: Identifier,
        updateHandler: BiConsumer<Minecraft, Collection<ClientApiEntry<*>>>,
    ) {
        ClientApiEntryRegistry.registerSimple(key) { client, entries ->
            updateHandler.accept(client, entries)
        }
    }

    //#endregion

    //#region live updates

    /**
      Use this in Kotlin mods, pass for deserializer for example
      MyPayloadClass.serializer()
     */
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadDeserializer: KSerializer<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> = ClientLiveUpdatesEventRegistry.register(eventName, payloadDeserializer, handler)

    /**
     * Use Minecraft Codecs to parse `payload`.
     */
    @JvmStatic
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadCodec: Codec<T>,
        payloadClass: Class<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> = ClientLiveUpdatesEventRegistry.registerWithCodec(
        eventName, payloadCodec, payloadClass.kotlin, handler,
    )

    /**
     * Use gson to parse `payload` as the class.
     */
    @JvmStatic
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadClass: Class<T>,
        handler: ClientLiveUpdatesEventHandler<T>,
    ): ClientLiveUpdatesEvent<T> = ClientLiveUpdatesEventRegistry.registerWithClass(
        eventName, payloadClass.kotlin, handler,
    )

    /**
     * With this you have to handle parsing `payload` yourself
     */
    @JvmStatic
    fun registerJsonLiveUpdateEvent(
        eventName: String,
        handler: ClientLiveUpdatesEventHandler<JsonElement>,
    ): ClientLiveUpdatesEvent<JsonElement> = ClientLiveUpdatesEventRegistry.registerJson(eventName, handler)

    /**
     * Simple events with no details
     */
    @JvmSynthetic
    fun registerSimpleLiveUpdateEvent(
        eventName: String,
        handler: (Minecraft, ResponseSender) -> Unit,
    ): ClientLiveUpdatesEvent<Unit> = ClientLiveUpdatesEventRegistry.register(eventName, handler)

    /**
     * Simple events with no details
     */
    @JvmStatic
    fun registerSimpleLiveUpdateEvent(
        eventName: String,
        handler: BiConsumer<Minecraft, ResponseSender>,
    ): ClientLiveUpdatesEvent<Unit> = ClientLiveUpdatesEventRegistry.register(eventName) { client, sender ->
        handler.accept(client, sender)
    }

    //#endregion
}
