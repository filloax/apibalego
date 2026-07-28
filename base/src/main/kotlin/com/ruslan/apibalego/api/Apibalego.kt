package com.ruslan.apibalego.api

import com.google.gson.JsonElement
import com.mojang.serialization.Codec
import com.ruslan.apibalego.http.ApiDetailsParser
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.LiveUpdatesEvent
import com.ruslan.apibalego.socket.LiveUpdatesEventHandler
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.KSerializer
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.function.BiConsumer

/**
 * Server-side registration entry point for mods using Apibalego.
 *
 * For Kotlin mods, use kotlinx serializer methods as they are simpler;
 * for Java, use Codec if you already have it, or pass class otherwise.
 */
object Apibalego {
    //#region api entries

    /**
      Use this in Kotlin mods, pass for deserializer for example
      MyDetailsClass.serializer()
     */
    inline fun <reified T : Any>registerApiHandler(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
    ) {
        ApiEntryRegistry.register(key, detailsDeserializer, handler)
    }

    /**
     * Use Minecraft Codecs to parse `details`.
     */
    @JvmStatic
    fun <T : Any> registerApiHandler(
        key: Identifier,
        detailsCodec: Codec<T>,
        detailsClass: Class<T>,
        handler: ApiEntryHandler<T>,
    ) {
        ApiEntryRegistry.registerWithParser(
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
        handler: ApiEntryHandler<T>,
    ) {
        ApiEntryRegistry.registerWithParser(
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
    fun registerJsonApiHandler(key: Identifier, handler: ApiEntryHandler<JsonElement>) {
        ApiEntryRegistry.registerWithParser(
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
        updateHandler: (MinecraftServer, Collection<ApiEntry<Nothing>>) -> Unit,
        joinHandler: ((ServerPlayer, Collection<ApiEntry<Nothing>>) -> Unit)? = null,
    ) {
        ApiEntryRegistry.registerSimple(key, updateHandler, joinHandler)
    }

    /**
     * Simple events with no details
     */
    @JvmStatic
    @JvmOverloads
    fun registerSimple(
        key: Identifier,
        updateHandler: BiConsumer<MinecraftServer, Collection<ApiEntry<*>>>,
        joinHandler: BiConsumer<ServerPlayer, Collection<ApiEntry<*>>>? = null,
    ) {
        ApiEntryRegistry.registerSimple(
            key,
            { server, entries -> updateHandler.accept(server, entries) },
            joinHandler?.let { { player, entries -> it.accept(player, entries) } },
        )
    }

    //#endregion

    //#region live updates

    /**
      Use this in Kotlin mods, pass for deserializer for example
      MyDetailsClass.serializer()
     */
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadDeserializer: KSerializer<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> = LiveUpdatesEventRegistry.register(eventName, payloadDeserializer, handler)

    /**
     * Use Minecraft Codecs to parse `payload`.
     */
    @JvmStatic
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadCodec: Codec<T>,
        payloadClass: Class<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> = LiveUpdatesEventRegistry.registerWithCodec(
        eventName, payloadCodec, payloadClass.kotlin, handler,
    )

    /**
     * Use gson to parse `payload` as the class.
     */
    @JvmStatic
    fun <T : Any> registerLiveUpdateEvent(
        eventName: String,
        payloadClass: Class<T>,
        handler: LiveUpdatesEventHandler<T>,
    ): LiveUpdatesEvent<T> = LiveUpdatesEventRegistry.registerWithClass(eventName, payloadClass.kotlin, handler)

    /**
     * With this you have to handle parsing `payload` yourself
     */
    @JvmStatic
    fun registerJsonLiveUpdateEvent(
        eventName: String,
        handler: LiveUpdatesEventHandler<JsonElement>,
    ): LiveUpdatesEvent<JsonElement> = LiveUpdatesEventRegistry.registerJson(eventName, handler)

    /**
     * Simple events with no details
     */
    @JvmSynthetic
    fun registerSimpleLiveUpdateEvent(
        eventName: String,
        handler: (MinecraftServer, ResponseSender) -> Unit,
    ): LiveUpdatesEvent<Unit> = LiveUpdatesEventRegistry.register(eventName, handler)

    /**
     * Simple events with no details
     */
    @JvmStatic
    fun registerSimpleLiveUpdateEvent(
        eventName: String,
        handler: BiConsumer<MinecraftServer, ResponseSender>,
    ): LiveUpdatesEvent<Unit> = LiveUpdatesEventRegistry.register(eventName) { server, sender ->
        handler.accept(server, sender)
    }

    //#endregion
}
