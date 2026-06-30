package com.ruslan.apibalego.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

abstract class ApiEntryBase(
)

/**
 * Separate class to allow for simpler kotlin serialization.
 */
@Serializable
data class ApiEntryRaw(
    @Serializable(with = ApiEntryTypeSerializer::class)
    val type: ApiEntryType<*>,
    val details: JsonElement? = null,
    val id: String,
    val active: Boolean = true,
) {
    fun <T: Any> resolve(parsedType: ApiEntryType<T>, parsedDetails: T?): ApiEntry<T> {
        if ((parsedType.detailsDeserializer == null) != (parsedDetails == null)) {
            throw IllegalArgumentException("Type mismatch: ${parsedType.key} must have details, but details are ${parsedDetails != null}")
        }

        return ApiEntry(
            parsedType,
            parsedDetails,
            id,
            active
        )
    }
}

/**
 * Obtained from ApiEntryRaw, after resolving type
 */
data class ApiEntry<T : Any>(
    val type: ApiEntryType<T>,
    val details: T? = null,
    val id: String,
    val active: Boolean = true,
)
