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
import com.ruslan.apibalego.trigger.TriggerHandler
import com.ruslan.apibalego.trigger.TriggerRegistry
import com.ruslan.apibalego.trigger.TriggerTypeHandler
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

      @param alwaysShared If false, events of this type can run per-player when inside triggers,
        via [ApiEntryHandler.handleApiUpdatePlayer]
     */
    inline fun <reified T : Any>registerApiHandler(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: ApiEntryHandler<T>,
        alwaysShared: Boolean = true,
    ) {
        ApiEntryRegistry.register(key, detailsDeserializer, handler, alwaysShared)
    }

    /**
     * Use Minecraft Codecs to parse `details`.
     *
     * @param alwaysShared If false, events of this type can run per-player when inside triggers,
     *   via [ApiEntryHandler.handleApiUpdatePlayer]
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Any> registerApiHandler(
        key: Identifier,
        detailsCodec: Codec<T>,
        detailsClass: Class<T>,
        handler: ApiEntryHandler<T>,
        alwaysShared: Boolean = true,
    ) {
        ApiEntryRegistry.registerWithParser(
            key,
            ApiDetailsParser.FromCodec(detailsCodec),
            handler,
            detailsClass.kotlin,
            alwaysShared,
        )
    }

    /**
     * Use gson to parse `details` as the class.
     *
     * @param alwaysShared If false, events of this type can run per-player when inside triggers,
     *   via [ApiEntryHandler.handleApiUpdatePlayer]
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Any> registerApiHandler(
        key: Identifier,
        detailsClass: Class<T>,
        handler: ApiEntryHandler<T>,
        alwaysShared: Boolean = true,
    ) {
        ApiEntryRegistry.registerWithParser(
            key,
            ApiDetailsParser.FromClass(detailsClass),
            handler,
            detailsClass.kotlin,
            alwaysShared,
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

    //#region triggers

    /**
     * Register a trigger type, usable in "trigger" api entries. Call [runTrigger] when
     * the trigger condition might have changed (e.g. from a mod event).
     */
    @JvmStatic
    fun <T : Any> registerTriggerType(
        key: Identifier,
        // if null, type has no extra info
        detailsDeserializer: KSerializer<T>?,
        handler: TriggerTypeHandler<T>,
        detailsClass: Class<T>,
    ) {
        TriggerRegistry.register(key, detailsDeserializer, handler, detailsClass.kotlin)
    }

    /**
     * Check triggers of this type for the player and run their events. Must be called after the
     * state used by the trigger's [TriggerTypeHandler.runKey] is updated.
     */
    @JvmStatic
    fun runTrigger(player: ServerPlayer, key: Identifier) {
        TriggerHandler.runTrigger(player, key)
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
