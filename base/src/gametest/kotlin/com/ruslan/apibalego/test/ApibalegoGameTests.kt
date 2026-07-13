package com.ruslan.apibalego.test

import com.filloax.fxlib.api.FxLibServices
import com.filloax.fxlib.api.entity.getPersistData
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.handlers.RemoteDatapackHandler
import com.ruslan.apibalego.handlers.RemoteStructuresHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.ID_API_HANDLER_COMMAND
import com.ruslan.apibalego.http.ID_API_HANDLER_DATAPACK
import com.ruslan.apibalego.http.ID_API_HANDLER_STRUCTURE
import com.ruslan.apibalego.http.ID_API_HANDLER_TOAST
import com.ruslan.apibalego.pack.PreloadPackSync
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.ChunkPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestSequence
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.gamerules.GameRules
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.optionals.getOrNull

/**
 * Loader-agnostic gametest body. Runs inside a server game environment and calls
 * [GameTestHelper.succeed] on success (via the sequence built by [combinedHandlerLifecycleTest]).
 *
 * The Fabric and NeoForge modules each register this with their loader's gametest system.
 * Runs in an empty structure as it uses the API which isn't related to worldgen.
 *
 * Pure dispatch/registry logic (no real file, network, or world I/O) lives instead as plain
 * JUnit unit tests under base/src/test - see ApibalegoDispatchTest.kt.
 */
object ApibalegoGameTests {
    /** Marks the entry id as seen in the joining player's persistent data. */
    private fun toastJoinStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevelAlt()
        val entryId = "gt-toast-seen-${System.nanoTime()}"
        val toast = ToastHandler.ToastData(title = Component.literal("Test Toast"))
        @Suppress("UNCHECKED_CAST")
        val type: ApiEntryType<ToastHandler.ToastData> = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_TOAST)
            as ApiEntryType<ToastHandler.ToastData>
        val entry = ApiEntry(type = type, details = toast, id = entryId, active = true)

        ToastHandler.handleApiJoin(player, listOf(entry))

        return sequence.thenExecute {
            val memory = player.getPersistData().getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).get()
            val key = toast.title.string + entryId
            helper.assertTrue(memory.contains(key), "toast entry not marked as seen in player memory")
        }
    }

    /**
     * Command entry dispatch executes the underlying command on the server, and re-dispatching
     * the same entry id doesn't run it again.
     */
    private fun commandDispatchStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteCommandExecution
        ApiBalegoConfig.remoteCommandExecution = true
        val commandType = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_COMMAND)

        commandType.dispatchUpdate(
            listOf(ApiEntryRaw(
                type = commandType,
                details = buildJsonObject { put("command", JsonPrimitive("gamerule keep_inventory true")) },
                id = "gt-cmd-run-${System.nanoTime()}",
                active = true,
            )),
            server,
        )

        // Use send_command_feedback (default true) to avoid clashing with the run-command check's keep_inventory
        val idemEntry = ApiEntryRaw(
            type = commandType,
            details = buildJsonObject { put("command", JsonPrimitive("gamerule send_command_feedback false")) },
            id = "gt-cmd-idem-${System.nanoTime()}",
            active = true,
        )

        // EventUtil.runWhenServerStarted fires on the next tick in gametests, hence the 1-tick gaps
        return sequence
            .thenExecuteAfter(1) {
                helper.assertTrue(
                    helper.level.gameRules.get(GameRules.KEEP_INVENTORY),
                    "command did not run (keepInventory not set to true)",
                )
                server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule keep_inventory false")

                commandType.dispatchUpdate(listOf(idemEntry), server)
            }
            .thenExecuteAfter(1) {
                helper.assertFalse(
                    helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                    "first dispatch should set send_command_feedback false",
                )
                server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule send_command_feedback true")
                helper.assertTrue(
                    helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                    "manual reset to true failed",
                )

                commandType.dispatchUpdate(listOf(idemEntry), server)
            }
            .thenExecuteAfter(1) {
                helper.assertTrue(
                    helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                    "second dispatch with same id must be skipped (idempotent)",
                )
                server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule send_command_feedback true")
                ApiBalegoConfig.remoteCommandExecution = prevEnabled
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun makeStructureEntry(
        id: String,
        structureId: Identifier,
        pos: BlockPos = BlockPos.ZERO,
        active: Boolean = true,
    ) = ApiEntry(
        type = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_STRUCTURE)
            as ApiEntryType<RemoteStructuresHandler.RemoteStructureSpawnData>,
        details = RemoteStructuresHandler.RemoteStructureSpawnData(structureId, pos),
        id = id,
        active = active,
    )

    /**
     * Combined lifecycle check for [RemoteStructuresHandler]: an inactive entry is skipped, an entry
     * pointing at a non-existent structure is skipped, then a valid entry populates the handler map,
     * hands off to FxLib's queue, and the structure appears in the vanilla StructureManager (via
     * FxLib's mixin). A subsequent empty re-dispatch clears the map.
     *
     * FxLib has two async hops before placement: [EventUtil.runWhenServerStarted] then a
     * [ScheduledServerTask]. Under gametest server load those hops don't reliably land within a
     * fixed tick count, so every eventual condition below is checked via [GameTestSequence.thenWaitUntil],
     * which retries every tick until it stops throwing, rather than a fixed-delay wait. The two
     * negative/invariant checks (inactive and unknown-structure entries never appearing) don't have
     * this problem since they'd hold on any tick, so a short gap between dispatches is enough there.
     */
    private fun structureHandlerStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        val server = helper.level.server
        val bogusId = Identifier.fromNamespaceAndPath("gametest", "nonexistent_structure")
        val validStructId = Identifier.fromNamespaceAndPath("minecraft", "igloo")
        val inactiveEntryId = "gt-struct-inactive-${System.nanoTime()}"
        val unknownEntryId = "gt-struct-unknown-${System.nanoTime()}"
        val validEntryId = "gt-struct-valid-${System.nanoTime()}"
        val inactiveSpawnKey = "apibalego_structure_$inactiveEntryId"
        val unknownSpawnKey = "apibalego_structure_$unknownEntryId"
        val validSpawnKey = "apibalego_structure_$validEntryId"
        // Spawn at the test's own world position so the placement is tied to this test's region
        // rather than a fixed global coord (BlockPos.ZERO) that persists across runs.
        val spawnPos = helper.absolutePos(BlockPos(2, 0, 2))
        val spawnChunk = ChunkPos(
            SectionPos.blockToSectionCoord(spawnPos.x),
            SectionPos.blockToSectionCoord(spawnPos.z),
        )

        val structure = server.registryAccess()
            .lookup(Registries.STRUCTURE).getOrNull()
            ?.getValue(validStructId)

        RemoteStructuresHandler.handleApiUpdate(
            server,
            listOf(makeStructureEntry(inactiveEntryId, validStructId, active = false)),
        )

        return sequence
            .thenExecuteAfter(2) {
                helper.assertFalse(
                    RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(inactiveSpawnKey),
                    "Inactive structure entry must not be added to map",
                )
                RemoteStructuresHandler.handleApiUpdate(
                    server,
                    listOf(makeStructureEntry(unknownEntryId, bogusId)),
                )
            }
            .thenExecuteAfter(2) {
                helper.assertFalse(
                    RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(unknownSpawnKey),
                    "Non-existent structure must be silently skipped",
                )
                RemoteStructuresHandler.handleApiUpdate(
                    server,
                    listOf(makeStructureEntry(validEntryId, validStructId, spawnPos)),
                )
            }
            .thenWaitUntil {
                helper.assertTrue(
                    RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(validSpawnKey),
                    "Valid structure '$validStructId' must be in handler spawn map after dispatch",
                )
                helper.assertTrue(
                    FxLibServices.fixedStructureGeneration.registeredStructureSpawns.containsKey(validSpawnKey),
                    "Handler must call fixedStructureGeneration.register() — spawnKey '$validSpawnKey' not in FxLib queue",
                )
            }
            // Verify placement via vanilla StructureManager routed through FxLib's mixin.
            // startsForStructure(ChunkPos, Predicate) checks at chunk granularity so the igloo
            // doesn't need to overlap spawnPos exactly (igloo doesn't implement FixablePosition).
            .thenWaitUntil {
                val starts = helper.level.structureManager().startsForStructure(spawnChunk) { it == structure }
                helper.assertTrue(
                    structure != null && starts.isNotEmpty(),
                    "Structure '$validStructId' not found via vanilla StructureManager in chunk $spawnChunk",
                )
            }
            .thenExecute {
                RemoteStructuresHandler.handleApiUpdate(server, emptyList())
            }
            .thenWaitUntil {
                helper.assertFalse(
                    RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(validSpawnKey),
                    "Spawn map must not contain entry after re-dispatch with no entries",
                )
            }
    }

    private fun makeDatapackEntry(id: String, downloadUrl: String, version: String = "1", active: Boolean = true) = ApiEntryRaw(
        type = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_DATAPACK),
        details = buildJsonObject {
            put("downloadUrl", JsonPrimitive(downloadUrl))
            put("version", JsonPrimitive(version))
        },
        id = id,
        active = active,
    )

    /** Minimal but valid datapack zip: just a pack.mcmeta, enough for FolderRepositorySource to recognize it. */
    private fun buildDatapackZipBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("pack.mcmeta"))
            zip.write("""{"pack":{"pack_format":48,"description":"gametest datapack"}}""".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun startZipServer(bytes: ByteArray, requestCount: AtomicInteger? = null): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/pack.zip") { exchange ->
            requestCount?.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private fun datapackIdentity(version: String, downloadUrl: String) = "${version}_${downloadUrl.hashCode()}"

    /**
     * Avoids calling all other types with an empty list (see [ApiEntryRegistry.dispatchUpdate]).
     */
    private fun dispatchDatapackUpdate(entries: List<ApiEntryRaw>, server: MinecraftServer) {
        ApiEntryRegistry.lookup<RemoteDatapackHandler.DatapackDetails>(ID_API_HANDLER_DATAPACK).dispatchUpdate(entries, server)
    }

    /**
     * Datapack sync is a no-op while disabled via config, then end-to-end lifecycle for a single
     * gamemaster-managed datapack once enabled: rejected while external URLs are disabled and the
     * origin differs -> installed and selected once allowed -> re-dispatching the same version
     * doesn't re-download -> version bump while selected downloads to a new file and retires the
     * old one instead of overwriting it (avoiding access denied OS errors) -> changing the download
     * URL alone (same version) is also detected as a change and swapped the same way (regression
     * test for a URL rename being silently ignored) -> deselected and deleted once no longer desired.
     */
    private fun datapackHandlerStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteDatapackSync
        val prevExternal = ApiBalegoConfig.remoteDatapackAllowExternalUrl
        val prevSyncUrl = ApiBalegoConfig.dataSyncUrl
        ApiBalegoConfig.remoteDatapackSync = false
        ApiBalegoConfig.remoteDatapackAllowExternalUrl = false
        // Arbitrary origin guaranteed to differ from the download servers' ports below.
        ApiBalegoConfig.dataSyncUrl = "http://127.0.0.1:1"

        val disabledEntryId = "gt-dp-disabled-${System.nanoTime()}"
        val disabledUrl = "http://127.0.0.1:1/unused.zip"
        val disabledPackFile = PreloadPackSync.serverDatapackDir()
            .resolve("apibalego_dp_${disabledEntryId}_${datapackIdentity("1", disabledUrl)}.zip")
        dispatchDatapackUpdate(listOf(makeDatapackEntry(disabledEntryId, disabledUrl)), server)

        val requestCount = AtomicInteger(0)
        val httpServer = startZipServer(buildDatapackZipBytes(), requestCount)
        val httpServer2 = startZipServer(buildDatapackZipBytes(), requestCount)
        val entryId = "gt-dp-lifecycle-${System.nanoTime()}"
        val url = "http://127.0.0.1:${httpServer.address.port}/pack.zip"
        val url2 = "http://127.0.0.1:${httpServer2.address.port}/pack.zip"
        // filename is identity-derived (version + url hash, see RemoteDatapackHandler) so an
        // in-place update never overwrites the currently-selected (locked-on-Windows) zip.
        // Pack ids are the plain filename (no "file/" prefix) since these come from
        // ApibalegoRepositorySource, not vanilla's plain FolderRepositorySource.
        val fileNameV1 = "apibalego_dp_${entryId}_${datapackIdentity("1", url)}.zip"
        val fileNameV2 = "apibalego_dp_${entryId}_${datapackIdentity("2", url)}.zip"
        val fileNameV2Url2 = "apibalego_dp_${entryId}_${datapackIdentity("2", url2)}.zip"
        val dpDir = PreloadPackSync.serverDatapackDir()
        val packFileV1 = dpDir.resolve(fileNameV1)
        val packFileV2 = dpDir.resolve(fileNameV2)
        val packFileV2Url2 = dpDir.resolve(fileNameV2Url2)

        return sequence
            .thenExecuteAfter(1) {
                helper.assertFalse(Files.exists(disabledPackFile), "Disabled datapack sync must not install anything")
                ApiBalegoConfig.remoteDatapackSync = true
                dispatchDatapackUpdate(listOf(makeDatapackEntry(entryId, url)), server)
            }
            .thenExecute {
                helper.assertFalse(Files.exists(packFileV1), "download must be rejected while external URLs are disabled")
                ApiBalegoConfig.remoteDatapackAllowExternalUrl = true
                dispatchDatapackUpdate(listOf(makeDatapackEntry(entryId, url)), server)
            }
            .thenWaitUntil {
                helper.assertTrue(Files.exists(packFileV1), "download should succeed once external URLs are allowed")
                helper.assertTrue(
                    server.packRepository.selectedIds.contains(fileNameV1),
                    "Pack '$fileNameV1' should be selected after install",
                )
                helper.assertTrue(requestCount.get() == 1, "expected exactly 1 download request so far, got ${requestCount.get()}")
            }
            .thenExecute {
                // same-version re-dispatch must not trigger a second download
                dispatchDatapackUpdate(listOf(makeDatapackEntry(entryId, url)), server)
            }
            .thenWaitUntil {
                helper.assertTrue(
                    requestCount.get() == 1,
                    "same-version re-dispatch must not trigger a second download, got ${requestCount.get()} requests",
                )
            }
            .thenExecute {
                // v1 is still selected here: this must download v2 to a new file rather than
                // overwrite v1's (locked-while-selected) file in place
                dispatchDatapackUpdate(listOf(makeDatapackEntry(entryId, url, version = "2")), server)
            }
            .thenWaitUntil {
                helper.assertTrue(Files.exists(packFileV2), "v2 should be downloaded to its own file")
                helper.assertTrue(
                    server.packRepository.selectedIds.contains(fileNameV2) &&
                        !server.packRepository.selectedIds.contains(fileNameV1),
                    "v2 should replace v1 in the selection",
                )
                helper.assertTrue(requestCount.get() == 2, "version bump should trigger exactly 1 more download, got ${requestCount.get()} total")
                helper.assertFalse(Files.exists(packFileV1), "old v1 file should be deleted after being retired")
            }
            .thenExecute {
                // same version, only the URL changes: must still be detected as a change
                dispatchDatapackUpdate(listOf(makeDatapackEntry(entryId, url2, version = "2")), server)
            }
            .thenWaitUntil {
                helper.assertTrue(Files.exists(packFileV2Url2), "URL-only change should be downloaded to its own file")
                helper.assertTrue(
                    server.packRepository.selectedIds.contains(fileNameV2Url2) &&
                        !server.packRepository.selectedIds.contains(fileNameV2),
                    "new URL's pack should replace the old URL's pack in the selection",
                )
                helper.assertTrue(requestCount.get() == 3, "URL-only change should trigger exactly 1 more download, got ${requestCount.get()} total")
                helper.assertFalse(Files.exists(packFileV2), "old (same-version, old-URL) file should be deleted after being retired")
            }
            .thenExecute {
                RemoteDatapackHandler.handleApiUpdate(server, emptyList())
            }
            .thenWaitUntil {
                helper.assertFalse(server.packRepository.selectedIds.contains(fileNameV2Url2), "pack should be deselected after removal")
                helper.assertFalse(Files.exists(packFileV2Url2), "pack file should be deleted after removal")
            }
            .thenExecute {
                httpServer.stop(0)
                httpServer2.stop(0)
                ApiBalegoConfig.remoteDatapackSync = prevEnabled
                ApiBalegoConfig.remoteDatapackAllowExternalUrl = prevExternal
                ApiBalegoConfig.dataSyncUrl = prevSyncUrl
            }
    }

    /**
     * Combined gametest for everything that needs a real running server: config bootstrap, join
     * dispatch against a real player, and the datapack/structure handlers (real file/network/world
     * I/O). Everything else lives in base/src/test as plain unit tests, which run sequentially and
     * don't need a server at all - see ApibalegoDispatchTest.kt.
     *
     * Kept as one continuous sequence rather than several independent gametests: the shared handler
     * singletons (structure/datapack) treat "not present in this dispatch's entries" as "no longer
     * desired" and retire/delete accordingly, so concurrently-running gametests dispatching to the
     * same handler would clobber each other's state. Running everything in one script means there's
     * only ever one dispatch in flight at a time.
     */
    fun combinedHandlerLifecycleTest(helper: GameTestHelper) {
        helper.assertTrue(ApiBalegoConfigHandler.config != null, "ApiBalegoConfig was not loaded")
        helper.assertTrue(ApiBalegoConfig.dataSyncUrl.isNotBlank(), "dataSyncUrl default missing")

        var sequence = helper.startSequence()
        sequence = toastJoinStep(helper, sequence)
        sequence = commandDispatchStep(helper, sequence)
        sequence = structureHandlerStep(helper, sequence)
        sequence = datapackHandlerStep(helper, sequence)
        sequence.thenSucceed()
    }
}
