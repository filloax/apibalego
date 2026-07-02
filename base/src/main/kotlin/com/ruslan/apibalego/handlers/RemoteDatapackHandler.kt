package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.filloax.fxlib.api.ScheduledServerTask
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
     * Diffs the desired set against previously-installed state, since inactive/removed entries
     * never reach here (GamemasterApi filters to active=true before dispatch), so "not in this
     * dispatch" is the only signal available for "should be removed".
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
            val toDownload = desired.filter { (id, details) -> installed[id] != details.version }

            if (toDownload.isEmpty() && toRemove.isEmpty()) return@runWhenServerStarted

            val dir = srv.getWorldPath(LevelResource.DATAPACK_DIR)
            dir.createDirectories()

            val downloadedIds = mutableSetOf<String>()
            toDownload.forEach { (id, details) ->
                if (!isUrlAllowed(details.downloadUrl)) {
                    Apibalego.LOGGER.error(
                        "Datapack '$id' download URL '${details.downloadUrl}' not allowed " +
                            "(different origin than data sync URL, and external URLs disabled)"
                    )
                    return@forEach
                }
                try {
                    downloadPack(dir, id, details.downloadUrl)
                    downloadedIds.add(id)
                } catch (e: Exception) {
                    Apibalego.LOGGER.error("Failed to download datapack '$id' from ${details.downloadUrl}: ${e.message}")
                }
            }

            ScheduledServerTask.schedule(srv, 0) {
                val repo = srv.packRepository
                repo.reload()
                val selected = repo.selectedIds.toMutableSet()

                toRemove.forEach { id -> selected.remove(packId(id)) }

                val availableAfterReload = downloadedIds.filter { id ->
                    val ok = repo.isAvailable(packId(id))
                    if (!ok) Apibalego.LOGGER.error("Downloaded datapack '$id' not recognized as a pack after reload (bad zip?)")
                    ok
                }
                availableAfterReload.forEach { id -> selected.add(packId(id)) }

                srv.reloadResources(selected).thenRun {
                    // Only safe to delete now: while a pack is selected, its zip is held open
                    // (locked on Windows), so deleting it before deselect+reload fails.
                    toRemove.forEach { id ->
                        deletePackFile(dir, id)
                        installed.remove(id)
                    }
                    availableAfterReload.forEach { id -> installed[id] = desired.getValue(id).version }
                    savedData.setDirty()
                    Apibalego.LOGGER.info("Datapack sync: reloaded resources, selected packs now: $selected")
                }
            }
        }
    }

    private fun isUrlAllowed(url: String): Boolean {
        if (ApiBalegoConfig.remoteDatapackAllowExternalUrl) return true
        return try {
            val target = URI(url)
            val base = URI(ApiBalegoConfig.dataSyncUrl)
            target.scheme == base.scheme && target.host == base.host && effectivePort(target) == effectivePort(base)
        } catch (e: Exception) {
            false
        }
    }

    private fun effectivePort(uri: URI): Int {
        if (uri.port != -1) return uri.port
        return when (uri.scheme?.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }

    private fun fileName(id: String) = "apibalego_dp_${id.replace(Regex("[^a-zA-Z0-9_.-]"), "_")}.zip"
    private fun packId(id: String) = "file/${fileName(id)}"

    private fun deletePackFile(dir: Path, id: String) {
        dir.resolve(fileName(id)).deleteIfExists()
    }

    private fun downloadPack(dir: Path, id: String, url: String) {
        val target = dir.resolve(fileName(id))
        val tmp = dir.resolve("${fileName(id)}.tmp")
        val conn: URLConnection = URI(url).toURL().openConnection()
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        if (conn is HttpURLConnection) {
            conn.requestMethod = "GET"
            if (ApiBalegoConfig.dataSyncApiKey.isNotBlank()) {
                conn.setRequestProperty("apiKey", ApiBalegoConfig.dataSyncApiKey)
            }
        }
        conn.connect()
        try {
            if (conn is HttpURLConnection && conn.responseCode >= 300) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            conn.getInputStream().use { input ->
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (conn is HttpURLConnection) conn.disconnect()
            tmp.deleteIfExists()
        }
    }
}
