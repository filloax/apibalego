package com.ruslan.apibalego.test.client

import com.ruslan.apibalego.client.data.ApibalegoClientData
import com.ruslan.apibalego.client.gui.components.CustomTextToast
import com.ruslan.apibalego.client.handlers.MainMenuMessageHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRaw
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import com.ruslan.apibalego.client.http.ID_CLIENT_API_HANDLER_MENU_MESSAGE
import com.ruslan.apibalego.client.http.ID_CLIENT_API_HANDLER_RESOURCEPACK
import com.ruslan.apibalego.client.http.ID_CLIENT_API_HANDLER_TOAST
import com.ruslan.apibalego.client.pack.PreloadPackSyncClient
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.gui.components.SplashRenderer
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.network.chat.Component
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Loader-agnostic client gametest bodies, driven through [ClientTestDriver]. Same idea as
 * [com.ruslan.apibalego.test.ApibalegoGameTests] but for the client-side api entries: the Fabric
 * module registers these with fabric-client-gametest-api-v1 (NeoForge has no equivalent yet).
 *
 * Tests dispatch through [ClientApiEntryRegistry.dispatchUpdate] from the test thread, mirroring
 * how ClientDataSync invokes handlers from its sync thread in production. Client gametests run
 * sequentially, but every dispatch reaches all registered types (absent types get an empty list,
 * clearing their state), so each test cleans up its own entries and config toggles.
 */
object ApibalegoClientGameTests {
    private fun menuMessageEntry(id: String, messages: List<String>, active: Boolean = true) = ClientApiEntryRaw(
        type = ClientApiEntryRegistry.lookup(ID_CLIENT_API_HANDLER_MENU_MESSAGE),
        details = buildJsonObject {
            put("messages", buildJsonArray { messages.forEach { add(it) } })
        },
        id = id,
        active = active,
    )

    private fun toastEntry(id: String, title: String) = ClientApiEntryRaw(
        type = ClientApiEntryRegistry.lookup(ID_CLIENT_API_HANDLER_TOAST),
        details = buildJsonObject { put("title", JsonPrimitive(title)) },
        id = id,
        active = true,
    )

    private fun packEntry(id: String, downloadUrl: String, version: String = "1") = ClientApiEntryRaw(
        type = ClientApiEntryRegistry.lookup(ID_CLIENT_API_HANDLER_RESOURCEPACK),
        details = buildJsonObject {
            put("downloadUrl", JsonPrimitive(downloadUrl))
            put("version", JsonPrimitive(version))
        },
        id = id,
        active = true,
    )

    /** SplashRenderer keeps its text private; grab the single Component field (dev-only test code). */
    private fun splashText(renderer: SplashRenderer): String {
        val field = SplashRenderer::class.java.declaredFields.first { it.type == Component::class.java }
        field.isAccessible = true
        return (field.get(renderer) as Component).string
    }

    /**
     * Messages from all active entries merge into one pool, and the SplashManager mixin serves
     * them instead of vanilla splashes; an empty re-dispatch restores vanilla behavior.
     */
    fun menuMessageReplacesSplash(driver: ClientTestDriver) {
        val message = "gt-splash-${System.nanoTime()}"
        val client = driver.onClient { it }
        try {
            // same message via two entries: proves merging without making the assertion random
            ClientApiEntryRegistry.dispatchUpdate(
                listOf(
                    menuMessageEntry("gt-menu-a", listOf(message, message)),
                    menuMessageEntry("gt-menu-b", listOf(message)),
                ),
                client,
            )
            repeat(3) {
                val splash = driver.onClient { it.gui.splashManager().getSplash() }
                check(splash != null && splashText(splash) == message) {
                    "SplashManager should serve the gamemaster message, got '${splash?.let(::splashText)}'"
                }
            }
        } finally {
            ClientApiEntryRegistry.dispatchUpdate(emptyList(), client)
        }
        check(MainMenuMessageHandler.getCustomSplash() == null) {
            "message pool should clear after an empty dispatch"
        }
    }

    /** A toast entry is shown directly on the client, and its id is remembered across dispatches. */
    fun toastShownOnceAndDeduped(driver: ClientTestDriver) {
        val client = driver.onClient { it }
        val entryId = "gt-toast-${System.nanoTime()}"
        val entry = toastEntry(entryId, "gt toast title")

        ClientApiEntryRegistry.dispatchUpdate(listOf(entry), client)
        driver.waitFor("toast to appear") { mc ->
            mc.gui.toastManager().getToast(CustomTextToast::class.java, Toast.NO_TOKEN) != null
        }
        check(ApibalegoClientData.shownToasts(client).any { it.endsWith(entryId) }) {
            "shown toast id should be persisted for dedup"
        }

        driver.onClient { it.gui.toastManager().clear() }
        ClientApiEntryRegistry.dispatchUpdate(listOf(entry), client)
        driver.waitTicks(5)
        driver.onClient { mc ->
            check(mc.gui.toastManager().getToast(CustomTextToast::class.java, Toast.NO_TOKEN) == null) {
                "toast with an already-seen id must not be shown again"
            }
        }
    }

    /** Minimal but valid resource pack zip: just a pack.mcmeta, enough to be recognized as a pack. */
    private fun buildResourcePackZipBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("pack.mcmeta"))
            zip.write("""{"pack":{"pack_format":88,"min_format":88,"max_format":88,"description":"gametest resource pack"}}""".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun startZipServer(bytes: ByteArray, requestCount: AtomicInteger): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/pack.zip") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private fun packIdentity(version: String, downloadUrl: String) = "${version}_${downloadUrl.hashCode()}"

    /**
     * Client counterpart of the server datapack lifecycle test: rejected while external URLs are
     * disabled and the origin differs -> installed, selected and saved to options once allowed ->
     * re-dispatching the same version doesn't re-download -> version bump while selected downloads
     * to a new file and retires the old one instead of overwriting it (test OS access denied errors) 
     * -> changing the download URL alone (same version) is also detected as a change and swapped the
     * same way (regression test for a URL rename being silently ignored) -> deselected on removal,
     * with the file deletion retried on later dispatches (the reload keeps the zip open at first).
     */
    fun resourcePackFullLifecycle(driver: ClientTestDriver) {
        val prevSync = ApiBalegoConfig.clientResourcePackSync
        val prevExternal = ApiBalegoConfig.clientResourcePackAllowExternalUrl
        val prevUrl = ApiBalegoConfig.clientDataSyncUrl
        val requestCount = AtomicInteger(0)
        val httpServer = startZipServer(buildResourcePackZipBytes(), requestCount)
        val httpServer2 = startZipServer(buildResourcePackZipBytes(), requestCount)
        val client = driver.onClient { it }
        try {
            ApiBalegoConfig.clientResourcePackSync = true
            ApiBalegoConfig.clientResourcePackAllowExternalUrl = false
            // Arbitrary origin guaranteed to differ from the download server's port below.
            ApiBalegoConfig.clientDataSyncUrl = "http://127.0.0.1:1"

            val entryId = "gt-rp-lifecycle-${System.nanoTime()}"
            val url = "http://127.0.0.1:${httpServer.address.port}/pack.zip"
            val url2 = "http://127.0.0.1:${httpServer2.address.port}/pack.zip"
            // filename is identity-derived (version + url hash, see ClientResourcePackHandler) so
            // an in-place update never overwrites the currently-selected (locked-on-Windows) zip.
            // Pack ids are the plain filename (no "file/" prefix) since these come from
            // ApibalegoRepositorySource, not vanilla's plain FolderRepositorySource.
            val fileName = "apibalego_rp_${entryId}_${packIdentity("1", url)}.zip"
            val packId = fileName
            val packFile = PreloadPackSyncClient.clientResourcePackDir().resolve(fileName)

            ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url)), client)
            driver.waitTicks(5)
            check(!Files.exists(packFile)) { "download must be rejected while external URLs are disabled" }

            ApiBalegoConfig.clientResourcePackAllowExternalUrl = true
            ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url)), client)
            // generous timeout: selection change triggers a full client resource reload
            driver.waitFor("pack downloaded and selected", timeoutTicks = 1200) { mc ->
                Files.exists(packFile) && mc.resourcePackRepository.selectedIds.contains(packId)
            }
            driver.waitFor("selection saved to options") { mc ->
                mc.options.resourcePacks.contains(packId)
            }
            check(requestCount.get() == 1) { "expected exactly 1 download request so far, got ${requestCount.get()}" }

            ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url)), client)
            driver.waitTicks(5)
            check(requestCount.get() == 1) {
                "same-version re-dispatch must not trigger a second download, got ${requestCount.get()} requests"
            }

            // version bump while v1 is still selected: must download to a new file (v1's zip is
            // locked while selected) and retire the old one, not overwrite it in place
            val fileNameV2 = "apibalego_rp_${entryId}_${packIdentity("2", url)}.zip"
            val packIdV2 = fileNameV2
            val packFileV2 = PreloadPackSyncClient.clientResourcePackDir().resolve(fileNameV2)
            ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url, version = "2")), client)
            driver.waitFor("v2 downloaded and selected in place of v1", timeoutTicks = 1200) { mc ->
                Files.exists(packFileV2) &&
                    mc.resourcePackRepository.selectedIds.contains(packIdV2) &&
                    !mc.resourcePackRepository.selectedIds.contains(packId)
            }
            check(requestCount.get() == 2) { "version bump should trigger exactly 1 more download, got ${requestCount.get()} total" }
            driver.waitFor("old v1 file deleted after being retired") { mc ->
                ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url, version = "2")), mc)
                !Files.exists(packFile)
            }

            // same version, only the URL changes: must still be detected as a change
            val fileNameV2Url2 = "apibalego_rp_${entryId}_${packIdentity("2", url2)}.zip"
            val packIdV2Url2 = fileNameV2Url2
            val packFileV2Url2 = PreloadPackSyncClient.clientResourcePackDir().resolve(fileNameV2Url2)
            ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url2, version = "2")), client)
            driver.waitFor("URL-only change downloaded and selected in place of the old URL", timeoutTicks = 1200) { mc ->
                Files.exists(packFileV2Url2) &&
                    mc.resourcePackRepository.selectedIds.contains(packIdV2Url2) &&
                    !mc.resourcePackRepository.selectedIds.contains(packIdV2)
            }
            check(requestCount.get() == 3) { "URL-only change should trigger exactly 1 more download, got ${requestCount.get()} total" }
            driver.waitFor("old (same-version, old-URL) file deleted after being retired") { mc ->
                ClientApiEntryRegistry.dispatchUpdate(listOf(packEntry(entryId, url2, version = "2")), mc)
                !Files.exists(packFileV2)
            }

            // each empty dispatch recomputes "retired" fresh from disk and retries deletion until
            // the reload frees the zip; once deleted, a follow-up reload actually deselects it
            // (required packs stay active for as long as their file exists, see reloadThenRetire)
            driver.waitFor("pack deselected and file deleted after removal", timeoutTicks = 1200) { mc ->
                ClientApiEntryRegistry.dispatchUpdate(emptyList(), mc)
                !mc.resourcePackRepository.selectedIds.contains(packIdV2Url2) && !Files.exists(packFileV2Url2)
            }
        } finally {
            httpServer.stop(0)
            httpServer2.stop(0)
            ApiBalegoConfig.clientResourcePackSync = prevSync
            ApiBalegoConfig.clientResourcePackAllowExternalUrl = prevExternal
            ApiBalegoConfig.clientDataSyncUrl = prevUrl
        }
    }
}
