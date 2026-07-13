package com.ruslan.apibalego.client.handlers

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.client.pack.PreloadPackSyncClient
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.utils.RemoteDownloadUtils
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.streams.asSequence

/**
 * Client-side counterpart to [com.ruslan.apibalego.handlers.RemoteDatapackHandler]: downloads/removes
 * resource packs pushed by the gamemaster into the apibalego resource pack folder
 * (see [PreloadPackSyncClient]), allows resource packs downloaded at game start to avoid requiring
 * a reload.
 *
 * Note that packs from [PreloadPackSyncClient] are "required", meaning they are always on if the file is present.
 */
object ClientResourcePackHandler : ClientApiEntryHandler<ClientResourcePackHandler.PackDetails> {
    @Serializable
    data class PackDetails(
        val downloadUrl: String,
        val version: String = "",
    )

    data class PackCheckResult(val changed: Boolean, val retiredFileNames: List<String>)

    /**
     * Applies only after initial preload (which is before normal api sync)
     */
    override fun handleApiUpdate(client: Minecraft, entries: Collection<ClientApiEntry<PackDetails>>) {
        if (!ApiBalegoConfig.clientResourcePackSync) {
            if (entries.isNotEmpty())
                ApibalegoMod.LOGGER.warn("Received resource pack entries but client resource pack sync disabled, ignoring!")
            return
        }

        val dir = PreloadPackSyncClient.clientResourcePackDir()
        val desired = entries.filter { it.active }.associate { it.id to it.details!! }
        // download on the sync thread, only touch the pack repository on the client thread
        val result = checkAndDownloadResourcePacks(dir, desired)
        if (!result.changed) return

        client.execute {
            result.retiredFileNames.forEach { fileName -> dir.resolve(fileName).deleteIfExists() }

            val repo = client.resourcePackRepository
            repo.reload()
            // saves the current (required-pack-inclusive) selection to options, and reloads resources if it changed
            client.options.updateResourcePacks(repo)
            ApibalegoMod.LOGGER.info("Resource pack sync: reloaded resources after gamemaster update")
        }
    }

    /**
     * Downloads missing packs and reports which files on disk are no longer desired
     */
    fun checkAndDownloadResourcePacks(dir: Path, desired: Map<String, PackDetails>): PackCheckResult {
        dir.createDirectories()

        var changed = false
        val expectedFileNames = mutableSetOf<String>()

        desired.forEach { (id, details) ->
            val name = fileName(id, identity(details))
            expectedFileNames.add(name)
            val target = dir.resolve(name)
            if (!target.exists()) {
                if (!RemoteDownloadUtils.isUrlAllowed(
                        details.downloadUrl, ApiBalegoConfig.clientDataSyncUrl, ApiBalegoConfig.clientResourcePackAllowExternalUrl
                )) {
                    ApibalegoMod.LOGGER.error(
                        "Resource pack '$id' download URL '${details.downloadUrl}' not allowed " +
                            "(different origin than client data sync URL, and external URLs disabled)"
                    )
                    return@forEach
                }
                try {
                    RemoteDownloadUtils.downloadToFile(target, details.downloadUrl, ApiBalegoConfig.clientDataSyncApiKey)
                    changed = true
                } catch (e: Exception) {
                    ApibalegoMod.LOGGER.error("Failed to download resource pack '$id' from ${details.downloadUrl}: ${e.message}")
                }
            }
        }

        val retiredFileNames = Files.list(dir).use { stream ->
            stream.asSequence()
                .map { it.fileName.toString() }
                .filter { it !in expectedFileNames }
                .toList()
        }
        if (retiredFileNames.isNotEmpty()) changed = true

        return PackCheckResult(changed, retiredFileNames)
    }

    private fun identity(details: PackDetails) = RemoteDownloadUtils.installIdentity(details.version, details.downloadUrl)
    private fun fileName(id: String, identity: String) =
        "apibalego_rp_${RemoteDownloadUtils.sanitizeForFileName(id)}_${RemoteDownloadUtils.sanitizeForFileName(identity)}.zip"
}
