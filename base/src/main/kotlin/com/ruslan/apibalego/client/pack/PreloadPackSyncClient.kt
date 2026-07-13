package com.ruslan.apibalego.client.pack

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.client.handlers.ClientResourcePackHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRaw
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import com.ruslan.apibalego.client.http.ClientGamemasterApi
import com.ruslan.apibalego.client.http.ID_CLIENT_API_HANDLER_RESOURCEPACK
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.http.HttpFetcher
import com.ruslan.apibalego.pack.ApibalegoRepositorySource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.minecraft.server.packs.PackType
import java.net.URLEncoder
import java.nio.file.Path

/**
 * Preloads resource packs to avoid reloading resources when they are enabled from the
 * start in the API
 */
object PreloadPackSyncClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpFetcher = HttpFetcher("apibalego-preload-client", ApibalegoMod.LOGGER)

    fun clientResourcePackDir(): Path = ApibalegoMod.gameDir.resolve("apibalego/resourcepacks")

    fun clientRepositorySource() = ApibalegoRepositorySource(clientResourcePackDir(), PackType.CLIENT_RESOURCES)

    fun preloadClientResourcePacks() {
        if (!ApiBalegoConfig.clientDataSync || !ApiBalegoConfig.clientResourcePackSync) return

        // type= is a hint for backends that support filtering server-side to shrink the response;
        // we still filter client-side below regardless, since it's not guaranteed to be honored.
        val url = "${ClientGamemasterApi.syncUrl()}?type=${urlEncode(ID_CLIENT_API_HANDLER_RESOURCEPACK.toString())}"
        val rawEntries = fetchEntries(url, ApiBalegoConfig.clientDataSyncApiKey)
        if (rawEntries == null) {
            ApibalegoMod.LOGGER.warn("Preload resource pack sync: gamemaster unreachable, using whatever's already on disk")
            return
        }

        val type = ClientApiEntryRegistry.lookup<ClientResourcePackHandler.PackDetails>(ID_CLIENT_API_HANDLER_RESOURCEPACK)
        val entries = rawEntries.filter { it.type.key == ID_CLIENT_API_HANDLER_RESOURCEPACK }
            .map { raw -> raw.resolve(type, type.parseDetails(raw)) }
        val desired = entries.filter { it.active }.associate { it.id to it.details!! }

        ClientResourcePackHandler.checkAndDownloadResourcePacks(clientResourcePackDir(), desired)
    }

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetchEntries(url: String, apiKey: String): List<ClientApiEntryRaw>? {
        val request = HttpFetcher.makeRequest(url, mapOf("apiKey" to apiKey))
        return try {
            val body = httpFetcher.sendRequestBlocking(request).let { HttpFetcher.getResponseContent(it) }
            json.decodeFromString(ListSerializer(ClientApiEntryRaw.serializer()), body)
        } catch (e: Exception) {
            ApibalegoMod.LOGGER.error("Preload resource pack sync: failed to fetch/parse gamemaster response: ${e.message}")
            null
        }
    }
}
