package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.http.IApiEntryRaw
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * Separate class to allow for simpler kotlin serialization.
 */
@Serializable
data class ClientApiEntryRaw(
    @Serializable(with = ClientApiEntryTypeSerializer::class)
    val type: ClientApiEntryType<*>,
    override val details: JsonElement? = null,
    val id: String,
    val active: Boolean = true,
) : IApiEntryRaw {
    fun <T: Any> resolve(parsedType: ClientApiEntryType<T>, parsedDetails: T?): ClientApiEntry<T> {
        if ((parsedType.detailsParser == null) != (parsedDetails == null)) {
            throw IllegalArgumentException("Type mismatch: ${parsedType.key} must have details, but details are ${parsedDetails != null}")
        }

        return ClientApiEntry(
            parsedType,
            parsedDetails,
            id,
            active
        )
    }
}

/**
 * Obtained from ClientApiEntryRaw, after resolving type
 */
data class ClientApiEntry<T : Any>(
    val type: ClientApiEntryType<T>,
    val details: T? = null,
    val id: String,
    val active: Boolean = true,
)