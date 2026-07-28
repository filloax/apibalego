package com.ruslan.apibalego.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

interface IApiEntryRaw {
    val details: JsonElement?
}

/**
 * Separate class to allow for simpler kotlin serialization.
 */
@Serializable
data class ApiEntryRaw(
    @Serializable(with = ApiEntryTypeSerializer::class)
    val type: ApiEntryType<*>,
    override val details: JsonElement? = null,
    val id: String,
    val active: Boolean = true,
) : IApiEntryRaw {
    fun <T: Any> resolve(parsedType: ApiEntryType<T>, parsedDetails: T?): ApiEntry<T> {
        if ((parsedType.detailsParser == null) != (parsedDetails == null)) {
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
