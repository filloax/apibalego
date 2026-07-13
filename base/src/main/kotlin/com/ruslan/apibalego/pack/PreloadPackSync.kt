package com.ruslan.apibalego.pack

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.handlers.RemoteDatapackHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.http.HttpFetcher
import com.ruslan.apibalego.http.ID_API_HANDLER_DATAPACK
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.minecraft.server.packs.PackType
import java.net.URLEncoder
import java.nio.file.Path

/**
 * Runs before the very first server-data PackRepository scan so datapacks downloaded here
 * are already known on the first world load and don't need a follow-up reload.
 *
 * Allows some data features that would require a world reload to work if enabled from the start.
 */
object PreloadPackSync {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpFetcher = HttpFetcher("apibalego-preload", ApibalegoMod.LOGGER)

    fun serverDatapackDir(): Path = ApibalegoMod.gameDir.resolve("apibalego/datapacks")

    fun serverRepositorySource() = ApibalegoRepositorySource(serverDatapackDir(), PackType.SERVER_DATA)

    fun preloadServerDatapacks() {
        if (!ApiBalegoConfig.webDataSync || !ApiBalegoConfig.remoteDatapackSync) return

        // still filter client side later as type query param is not necessarily honored
        val url = "${GamemasterApi.syncUrl()}?type=${urlEncode(ID_API_HANDLER_DATAPACK.toString())}"
        val rawEntries = fetchEntries(url, ApiBalegoConfig.dataSyncApiKey)
        if (rawEntries == null) {
            ApibalegoMod.LOGGER.warn("Preload datapack sync: gamemaster unreachable, using whatever's already on disk")
            return
        }

        val type = ApiEntryRegistry.lookup<RemoteDatapackHandler.DatapackDetails>(ID_API_HANDLER_DATAPACK)
        val entries = rawEntries.filter { it.type.key == ID_API_HANDLER_DATAPACK }
            .map { raw -> raw.resolve(type, type.parseDetails(raw)) }
        val desired = entries.filter { it.active }.associate { it.id to it.details!! }

        RemoteDatapackHandler.checkAndDownloadDataPacks(serverDatapackDir(), desired)
    }

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetchEntries(url: String, apiKey: String): List<ApiEntryRaw>? {
        val request = HttpFetcher.makeRequest(url, mapOf("apiKey" to apiKey))
        return try {
            val body = httpFetcher.sendRequestBlocking(request).let { HttpFetcher.getResponseContent(it) }
            json.decodeFromString(ListSerializer(ApiEntryRaw.serializer()), body)
        } catch (e: Exception) {
            ApibalegoMod.LOGGER.error("Preload datapack sync: failed to fetch/parse gamemaster response: ${e.message}")
            null
        }
    }
}
