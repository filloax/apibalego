package com.ruslan.apibalego.client.data

import com.ruslan.apibalego.Apibalego
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import java.io.File

/**
 * Client-side counterpart to [com.ruslan.apibalego.data.ApibalegoPersistentData]: plain json file
 * in the game directory, since client state exists independently of any world.
 *
 * Takes the client as a parameter instead of Minecraft.getInstance() so it stays callable from
 * off-thread contexts that already hold the instance (sync thread, client gametest thread).
 */
object ApibalegoClientData {
    @Serializable
    private data class Data(
        val shownToasts: MutableSet<String> = mutableSetOf(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private var data: Data? = null

    private fun file(client: Minecraft) = File(client.gameDirectory, "apibalego/client_data.json")

    @Synchronized
    private fun get(client: Minecraft): Data = data ?: run {
        val file = file(client)
        val loaded = if (file.exists()) {
            try {
                json.decodeFromString(Data.serializer(), file.readText())
            } catch (e: Exception) {
                Apibalego.LOGGER.error("Failed to read client data file, resetting it", e)
                Data()
            }
        } else Data()
        data = loaded
        loaded
    }

    /** Ids of toasts already shown to this client, to avoid re-showing on every sync/launch. */
    fun shownToasts(client: Minecraft) = get(client).shownToasts

    @Synchronized
    fun save(client: Minecraft) {
        val file = file(client)
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(Data.serializer(), get(client)))
    }
}
