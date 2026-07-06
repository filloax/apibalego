package com.ruslan.apibalego.client.handlers

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.client.data.ApibalegoClientData
import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.utils.RemoteDownloadUtils
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

/**
 * Client-side counterpart to [com.ruslan.apibalego.handlers.RemoteDatapackHandler]: downloads and
 * enables/disables resource packs pushed by the gamemaster, saving the selection to options like
 * the vanilla pack screen does.
 */
object ClientResourcePackHandler : ClientApiEntryHandler<ClientResourcePackHandler.PackDetails> {
    @Serializable
    data class PackDetails(
        val downloadUrl: String,
        val version: String = "",
    )

    /**
     * Checks if pack already installed before adding/removing
     * then does download, reloads resources, and removes old files
     */
    override fun handleApiUpdate(client: Minecraft, entries: Collection<ClientApiEntry<PackDetails>>) {
        if (!ApiBalegoConfig.clientResourcePackSync) {
            if (entries.isNotEmpty())
                Apibalego.LOGGER.warn("Received resource pack entries but client resource pack sync disabled, ignoring!")
            return
        }

        val installed = ApibalegoClientData.installedResourcePacks(client)
        val pendingDeletions = ApibalegoClientData.pendingPackFileDeletions(client)

        val desired = entries.filter { it.active }.associate { it.id to it.details!! }
        val toRemove = installed.keys - desired.keys
        val toDownload = desired.filter { (id, details) -> installed[id] != identity(details) }

        if (toDownload.isEmpty() && toRemove.isEmpty() && pendingDeletions.isEmpty()) return

        val dir = client.resourcePackDirectory
        dir.createDirectories()

        // old files to deselect+delete: fully removed ids, and ids being updated to a new identity
        val retiredFileNames = mutableListOf<String>()
        toRemove.forEach { id -> retiredFileNames.add(fileName(id, installed.getValue(id))) }
        toDownload.forEach { (id, _) -> installed[id]?.let { oldIdentity -> retiredFileNames.add(fileName(id, oldIdentity)) } }

        // download on the sync thread, only touch the pack repository on the client thread
        val downloadedIds = mutableSetOf<String>()
        toDownload.forEach { (id, details) ->
            if (!RemoteDownloadUtils.isUrlAllowed(
                    details.downloadUrl, ApiBalegoConfig.clientDataSyncUrl, ApiBalegoConfig.clientResourcePackAllowExternalUrl
            )) {
                Apibalego.LOGGER.error(
                    "Resource pack '$id' download URL '${details.downloadUrl}' not allowed " +
                        "(different origin than client data sync URL, and external URLs disabled)"
                )
                return@forEach
            }
            try {
                RemoteDownloadUtils.downloadToFile(dir.resolve(fileName(id, identity(details))), details.downloadUrl, ApiBalegoConfig.clientDataSyncApiKey)
                downloadedIds.add(id)
            } catch (e: Exception) {
                Apibalego.LOGGER.error("Failed to download resource pack '$id' from ${details.downloadUrl}: ${e.message}")
            }
        }

        client.execute {
            if (downloadedIds.isNotEmpty() || retiredFileNames.isNotEmpty()) {
                val repo = client.resourcePackRepository
                repo.reload()
                val selected = repo.selectedIds.toMutableList()

                retiredFileNames.forEach { fileName -> selected.remove("file/$fileName") }

                val availableAfterReload = downloadedIds.filter { id ->
                    val ok = repo.isAvailable(packId(id, identity(desired.getValue(id))))
                    if (!ok) Apibalego.LOGGER.error("Downloaded resource pack '$id' not recognized as a pack after reload (bad zip?)")
                    ok
                }
                availableAfterReload.forEach { id ->
                    val pid = packId(id, identity(desired.getValue(id)))
                    if (pid !in selected) selected.add(pid)
                }

                repo.setSelected(selected)
                // saves the new selection to options, and reloads resources if it changed
                client.options.updateResourcePacks(repo)

                retiredFileNames.forEach { fileName -> pendingDeletions.add(fileName) }
                toRemove.forEach { id -> installed.remove(id) }
                availableAfterReload.forEach { id -> installed[id] = identity(desired.getValue(id)) }
                Apibalego.LOGGER.info("Resource pack sync: selected packs now: $selected")
            }

            // the resource reload is async and keeps deselected zips open until it finishes
            // (locked on Windows), so deletions are retried on later dispatches/launches
            // instead of being done immediately
            pendingDeletions.removeIf { name ->
                try {
                    dir.resolve(name).deleteIfExists()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            ApibalegoClientData.save(client)
        }
    }

    private fun identity(details: PackDetails) = RemoteDownloadUtils.installIdentity(details.version, details.downloadUrl)
    private fun fileName(id: String, identity: String) =
        "apibalego_rp_${RemoteDownloadUtils.sanitizeForFileName(id)}_${RemoteDownloadUtils.sanitizeForFileName(identity)}.zip"
    private fun packId(id: String, identity: String) = "file/${fileName(id, identity)}"
}
