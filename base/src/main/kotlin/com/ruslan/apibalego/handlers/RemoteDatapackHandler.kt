package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.filloax.fxlib.api.ScheduledServerTask
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.utils.RemoteDownloadUtils
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

/**
 * Downloads and enables/disables datapacks pushed by the gamemaster, using the same
 * folder-scan + reload mechanism vanilla's /reload and /datapack commands use.
 */
object RemoteDatapackHandler : ApiEntryHandler<RemoteDatapackHandler.DatapackDetails> {
    @Serializable
    data class DatapackDetails(
        val downloadUrl: String,
        val version: String = "",
    )

    /**
     * Checks if files are needed to download/remove from existing set,
     * then does download, applies changes, and removes old files
     */
    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<DatapackDetails>>) {
        if (!ApiBalegoConfig.remoteDatapackSync) {
            if (entries.isNotEmpty())
                Apibalego.LOGGER.warn("Received datapack entries but datapack sync disabled, ignoring!")
            return
        }

        EventUtil.runWhenServerStarted(server) { srv ->
            val savedData = ApibalegoPersistentData.get(srv)
            val installed = savedData.installedDatapacks

            val desired = entries.filter { it.active }.associate { it.id to it.details!! }
            val toRemove = installed.keys - desired.keys
            val toDownload = desired.filter { (id, details) -> installed[id] != identity(details) }

            if (toDownload.isEmpty() && toRemove.isEmpty()) return@runWhenServerStarted

            val dir = srv.getWorldPath(LevelResource.DATAPACK_DIR)
            dir.createDirectories()

            // old files to deselect+delete: fully removed ids, and ids being updated to a new identity
            val retiredFileNames = mutableListOf<String>()
            toRemove.forEach { id -> retiredFileNames.add(fileName(id, installed.getValue(id))) }
            toDownload.forEach { (id, _) -> installed[id]?.let { oldIdentity -> retiredFileNames.add(fileName(id, oldIdentity)) } }

            val downloadedIds = mutableSetOf<String>()
            toDownload.forEach { (id, details) ->
                if (!RemoteDownloadUtils.isUrlAllowed(details.downloadUrl, ApiBalegoConfig.dataSyncUrl, ApiBalegoConfig.remoteDatapackAllowExternalUrl)) {
                    Apibalego.LOGGER.error(
                        "Datapack '$id' download URL '${details.downloadUrl}' not allowed " +
                            "(different origin than data sync URL, and external URLs disabled)"
                    )
                    return@forEach
                }
                try {
                    downloadPack(dir, id, identity(details), details.downloadUrl)
                    downloadedIds.add(id)
                } catch (e: Exception) {
                    Apibalego.LOGGER.error("Failed to download datapack '$id' from ${details.downloadUrl}: ${e.message}")
                }
            }

            ScheduledServerTask.schedule(srv, 0) {
                val repo = srv.packRepository
                repo.reload()
                val selected = repo.selectedIds.toMutableSet()

                retiredFileNames.forEach { fileName -> selected.remove("file/$fileName") }

                val availableAfterReload = downloadedIds.filter { id ->
                    val ok = repo.isAvailable(packId(id, identity(desired.getValue(id))))
                    if (!ok) Apibalego.LOGGER.error("Downloaded datapack '$id' not recognized as a pack after reload (bad zip?)")
                    ok
                }
                availableAfterReload.forEach { id -> selected.add(packId(id, identity(desired.getValue(id)))) }

                srv.reloadResources(selected).thenRun {
                    // Only safe to delete now: while a pack is selected, its zip is held open
                    // (locked on Windows), so deleting it before deselect+reload fails.
                    retiredFileNames.forEach { fileName -> deletePackFile(dir, fileName) }
                    toRemove.forEach { id -> installed.remove(id) }
                    availableAfterReload.forEach { id -> installed[id] = identity(desired.getValue(id)) }
                    savedData.setDirty()
                    Apibalego.LOGGER.info("Datapack sync: reloaded resources, selected packs now: $selected")
                }
            }
        }
    }

    private fun identity(details: DatapackDetails) = RemoteDownloadUtils.installIdentity(details.version, details.downloadUrl)
    private fun fileName(id: String, identity: String) =
        "apibalego_dp_${RemoteDownloadUtils.sanitizeForFileName(id)}_${RemoteDownloadUtils.sanitizeForFileName(identity)}.zip"
    private fun packId(id: String, identity: String) = "file/${fileName(id, identity)}"

    private fun deletePackFile(dir: Path, fileName: String) {
        dir.resolve(fileName).deleteIfExists()
    }

    private fun downloadPack(dir: Path, id: String, identity: String, url: String) {
        RemoteDownloadUtils.downloadToFile(dir.resolve(fileName(id, identity)), url, ApiBalegoConfig.dataSyncApiKey)
    }
}
