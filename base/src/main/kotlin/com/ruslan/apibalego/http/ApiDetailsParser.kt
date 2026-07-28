package com.ruslan.apibalego.http

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val apiEntryJson = Json {
//        ignoreUnknownKeys = true
//        isLenient = true
}

private val gson = Gson()

/**
 * Polymorphize (that the right verb?) parser
 * to allow the various overloads in Apibalego etc.
 */
sealed class ApiDetailsParser<T : Any> {
    abstract fun parse(json: JsonElement): T

    class Kotlinx<T : Any>(val serializer: KSerializer<T>) : ApiDetailsParser<T>() {
        override fun parse(json: JsonElement): T = apiEntryJson.decodeFromJsonElement(serializer, json)
    }

    class FromCodec<T : Any>(val codec: Codec<T>) : ApiDetailsParser<T>() {
        override fun parse(json: JsonElement): T = decodeWithCodec(codec, toGson(json))
    }

    /** Uses gson, no codec to write. */
    class FromClass<T : Any>(val payloadClass: Class<T>) : ApiDetailsParser<T>() {
        override fun parse(json: JsonElement): T = decodeWithGson(payloadClass, toGson(json))
    }

    /** Hands over the json as-is, parsing is up to the mod. */
    object RawJson : ApiDetailsParser<com.google.gson.JsonElement>() {
        override fun parse(json: JsonElement): com.google.gson.JsonElement = toGson(json)
    }

    companion object {
        // kotlinx JsonElement.toString() is valid json, and the trees are small enough that
        // reparsing beats writing a DynamicOps over kotlinx elements
        internal fun toGson(json: JsonElement): com.google.gson.JsonElement = JsonParser.parseString(json.toString())

        internal fun <T : Any> decodeWithCodec(codec: Codec<T>, json: com.google.gson.JsonElement): T = codec
            .parse(JsonOps.INSTANCE, json)
            .getOrThrow { msg -> IllegalArgumentException("Cannot parse payload: $msg") }

        internal fun <T : Any> decodeWithGson(payloadClass: Class<T>, json: com.google.gson.JsonElement): T =
            gson.fromJson(json, payloadClass)
                ?: throw IllegalArgumentException("Cannot parse payload as ${payloadClass.simpleName}: $json")

        internal fun <T : Any> decodeWithGson(payloadClass: Class<T>, json: String): T =
            gson.fromJson(json, payloadClass)
                ?: throw IllegalArgumentException("Cannot parse payload as ${payloadClass.simpleName}: $json")
    }
}
