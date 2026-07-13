package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.ScheduledServerTask
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.client.pack.PreloadPackSyncClient
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.pack.PreloadPackSync
import com.ruslan.apibalego.utils.RemoteDownloadUtils
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.streams.asSequence

/**
 * Downloads/removes datapacks pushed by the gamemaster into the apibalego
 * datapack folder (see [PreloadPackSync]).
 * <br>
 * Needed as some features require a world reload when added after server start.
 * <br>
 * Note that packs from [PreloadPackSyncClient] are "required", meaning they are always on if the file is present.
 */
object RemoteDatapackHandler : ApiEntryHandler<RemoteDatapackHandler.DatapackDetails> {
    @Serializable
    data class DatapackDetails(
        val downloadUrl: String,
        val version: String = "",
    )

    data class PackCheckResult(val changed: Boolean, val retiredFileNames: List<String>)

    // Applies only after preload, during normal api sync (preload runs earlier)
    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<DatapackDetails>>) {
        if (!ApiBalegoConfig.remoteDatapackSync) {
            if (entries.isNotEmpty())
                Apibalego.LOGGER.warn("Received datapack entries but datapack sync disabled, ignoring!")
            return
        }

        val dir = PreloadPackSync.serverDatapackDir()
        val desired = entries.filter { it.active }.associate { it.id to it.details!! }
        val result = checkAndDownloadDataPacks(dir, desired)
        if (!result.changed) return

        ScheduledServerTask.schedule(server, 0) {
            result.retiredFileNames.forEach { fileName -> dir.resolve(fileName).deleteIfExists() }

            val repo = server.packRepository
            repo.reload()
            server.reloadResources(repo.selectedIds).thenRun {
                Apibalego.LOGGER.info("Datapack sync: reloaded resources after gamemaster update")
            }
        }
    }

    /**
     * Downloads missing packs and reports which files on disk are no longer desired
     */
    fun checkAndDownloadDataPacks(dir: Path, desired: Map<String, DatapackDetails>): PackCheckResult {
        dir.createDirectories()

        var changed = false
        val expectedFileNames = mutableSetOf<String>()

        desired.forEach { (id, details) ->
            val name = fileName(id, identity(details))
            expectedFileNames.add(name)
            val target = dir.resolve(name)
            if (!target.exists()) {
                if (!RemoteDownloadUtils.isUrlAllowed(details.downloadUrl, ApiBalegoConfig.dataSyncUrl, ApiBalegoConfig.remoteDatapackAllowExternalUrl)) {
                    Apibalego.LOGGER.error(
                        "Datapack '$id' download URL '${details.downloadUrl}' not allowed " +
                            "(different origin than data sync URL, and external URLs disabled)"
                    )
                    return@forEach
                }
                try {
                    RemoteDownloadUtils.downloadToFile(target, details.downloadUrl, ApiBalegoConfig.dataSyncApiKey)
                    changed = true
                } catch (e: Exception) {
                    Apibalego.LOGGER.error("Failed to download datapack '$id' from ${details.downloadUrl}: ${e.message}")
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

    private fun identity(details: DatapackDetails) = RemoteDownloadUtils.installIdentity(details.version, details.downloadUrl)
    private fun fileName(id: String, identity: String) =
        "apibalego_dp_${RemoteDownloadUtils.sanitizeForFileName(id)}_${RemoteDownloadUtils.sanitizeForFileName(identity)}.zip"
}
