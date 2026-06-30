package com.ruslan.apibalego.test

import com.filloax.fxlib.api.FxLibServices
import com.filloax.fxlib.api.entity.getPersistData
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.handlers.RemoteStructuresHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.ID_API_HANDLER_COMMAND
import com.ruslan.apibalego.http.ID_API_HANDLER_STRUCTURE
import com.ruslan.apibalego.http.ID_API_HANDLER_TOAST
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.ChunkPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.level.gamerules.GameRules
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.optionals.getOrNull

/**
 * Loader-agnostic gametest bodies. Each is a `Consumer<GameTestHelper>`-style function that
 * runs inside a server game environment and calls [GameTestHelper.succeed] on success.
 *
 * The Fabric and NeoForge modules each register these with their loader's gametest system.
 * Tests are ran in an empty structure as they use the API which isn't related to worldgen.
 */
object ApibalegoGameTests {
    fun configLoaded(helper: GameTestHelper) {
        helper.assertTrue(ApiBalegoConfigHandler.config != null, "ApiBalegoConfig was not loaded")
        helper.assertTrue(ApiBalegoConfig.dataSyncUrl.isNotBlank(), "dataSyncUrl default missing")
        helper.succeed()
    }

    /** A registered api-entry handler is invoked when dispatched. */
    fun apiEventDispatchActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "dispatch_active")
        ApiEntryRegistry.registerSimple(key, { _, _ -> counter.incrementAndGet() }, { _, _ -> })
        val type = ApiEntryRegistry.lookup(key)

        ApiEntryRegistry.dispatchUpdate(
            listOf(ApiEntryRaw(type = type, id = "x", active = true)),
            helper.level.server,
        )

        helper.assertTrue(counter.get() == 1, "Handler should run exactly once, ran ${counter.get()}")
        helper.succeed()
    }

    /** Inactive entries filtered by the caller before dispatch are not sent to handlers. */
    fun apiEventDispatchInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "dispatch_inactive")
        ApiEntryRegistry.registerSimple(key, { _, _ -> counter.incrementAndGet() }, { _, _ -> })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = false))
        // Filtering inactive is caller responsibility (mirrors GamemasterApi behavior)
        ApiEntryRegistry.dispatchUpdate(entries.filter { it.active }, helper.level.server)

        helper.assertTrue(counter.get() == 0, "Inactive entry must not be dispatched")
        helper.succeed()
    }

    /** The built-in live-update handlers are registered. */
    fun liveUpdatesBuiltinsRegistered(helper: GameTestHelper) {
        val keys = LiveUpdatesEventRegistry.all().keys
        listOf(LiveUpdatesEventRegistry.RELOAD_EVENT, "toast", "cmd").forEach {
            helper.assertTrue(it in keys, "Built-in live update handler '$it' not registered (have $keys)")
        }
        helper.succeed()
    }

    /** Active join entries are dispatched to join handlers, once per player. */
    fun dispatchJoinActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "join_active")
        ApiEntryRegistry.registerSimple(key, { _, _ -> }, { _, _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = true))
        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchJoin(entries, helper.makeMockServerPlayerInLevelAlt())
        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchJoin(entries, helper.makeMockServerPlayerInLevelAlt())

        helper.assertTrue(counter.get() == 2, "Join handler should run once per player (2), ran ${counter.get()}")
        helper.succeed()
    }

    /** Inactive join entries filtered by the caller before dispatch are not sent to handlers. */
    fun dispatchJoinInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "join_inactive")
        ApiEntryRegistry.registerSimple(key, { _, _ -> }, { _, _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = false))
        @Suppress("DEPRECATION")
        // Filtering inactive is caller responsibility (mirrors GamemasterApi.Callbacks.onPlayerJoin)
        ApiEntryRegistry.dispatchJoin(entries.filter { it.active }, helper.makeMockServerPlayerInLevelAlt())

        helper.assertTrue(counter.get() == 0, "Inactive join entry must not be dispatched")
        helper.succeed()
    }

    // TODO: live update works test

    /** Toast join handler marks the entry id as seen in player persistent data. */
    fun toastJoinMarksAsSeen(helper: GameTestHelper) {
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevelAlt()
        val entryId = "gt-toast-seen-${System.nanoTime()}"
        val toast = ToastHandler.ToastData(title = Component.literal("Test Toast"))
        @Suppress("UNCHECKED_CAST")
        val type: ApiEntryType<ToastHandler.ToastData> = ApiEntryRegistry.lookup(ID_API_HANDLER_TOAST)
            as ApiEntryType<ToastHandler.ToastData>
        val entry = ApiEntry(
            type = type,
            details = toast,
            id = entryId,
            active = true,
        )

        ToastHandler.handleApiJoin(player, listOf(entry))

        val memory = player.getPersistData().getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).get()
        val key = toast.title.string + entryId
        helper.assertTrue(memory.contains(key), "toast entry not marked as seen in player memory")
        helper.succeed()
    }

    /** Command entry dispatch executes the underlying command on the server. */
    fun commandDispatchRunsCommand(helper: GameTestHelper) {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteCommandExecution
        ApiBalegoConfig.remoteCommandExecution = true
        ApiEntryRegistry.dispatchUpdate(
            listOf(ApiEntryRaw(
                type = ApiEntryRegistry.lookup(ID_API_HANDLER_COMMAND),
                details = buildJsonObject { put("command", JsonPrimitive("gamerule keep_inventory true")) },
                id = "gt-cmd-run-${System.nanoTime()}",
                active = true,
            )),
            server,
        )
        // EventUtil.runWhenServerStarted fires on the next tick in gametests
        helper.runAfterDelay(1) {
            try {
                helper.assertTrue(
                    helper.level.gameRules.get(GameRules.KEEP_INVENTORY),
                    "command did not run (keepInventory not set to true)",
                )
                helper.succeed()
            } finally {
                server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule keep_inventory false")
                ApiBalegoConfig.remoteCommandExecution = prevEnabled
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun makeStructureEntry(
        id: String,
        structureId: Identifier,
        pos: BlockPos = BlockPos.ZERO,
        active: Boolean = true,
    ) = ApiEntry(
        type = ApiEntryRegistry.lookup(ID_API_HANDLER_STRUCTURE)
            as ApiEntryType<RemoteStructuresHandler.RemoteStructureSpawnData>,
        details = RemoteStructuresHandler.RemoteStructureSpawnData(structureId, pos),
        id = id,
        active = active,
    )

    /** Inactive structure entries are not added to the spawn map. */
    fun structureHandlerSkipsInactive(helper: GameTestHelper) {
        val bogusId = Identifier.fromNamespaceAndPath("gametest", "nope_inactive")
        val entryId = "gt-struct-inactive-${System.nanoTime()}"
        val spawnKey = "apibalego_structure_$entryId"
        RemoteStructuresHandler.handleApiUpdate(
            helper.level.server,
            listOf(makeStructureEntry(entryId, bogusId, active = false)),
        )
        // Tests run concurrently — check for the specific key, not isEmpty()
        helper.runAfterDelay(1) {
            helper.assertFalse(
                RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(spawnKey),
                "Inactive structure entry must not be added to map",
            )
            helper.succeed()
        }
    }

    /** Active entries referencing a non-existent structure ID are skipped without crashing. */
    fun structureHandlerSkipsUnknownStructure(helper: GameTestHelper) {
        val bogusId = Identifier.fromNamespaceAndPath("gametest", "nonexistent_structure")
        val entryId = "gt-struct-unknown-${System.nanoTime()}"
        val spawnKey = "apibalego_structure_$entryId"
        RemoteStructuresHandler.handleApiUpdate(
            helper.level.server,
            listOf(makeStructureEntry(entryId, bogusId)),
        )
        // Tests run concurrently — check for the specific key, not isEmpty()
        helper.runAfterDelay(1) {
            helper.assertFalse(
                RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(spawnKey),
                "Non-existent structure must be silently skipped",
            )
            helper.succeed()
        }
    }

    /**
     * A valid structure entry populates the handler map, hands off to FxLib's queue, and
     * the structure appears in the vanilla StructureManager (via FxLib's mixin). A subsequent
     * empty re-dispatch clears the map.
     *
     * FxLib has two async hops before placement: [EventUtil.runWhenServerStarted] (1 tick) then
     * [ScheduledServerTask] (1 more tick). Sync checks use [GameTestHelper.runAfterDelay];
     * world-placement polling uses [GameTestHelper.succeedWhen] via [StructureManager.startsForStructure].
     *
     * Clear check lives inside the [succeedWhen] block: the first pass where the world check passes
     * dispatches the clear (async); the re-run one tick later sees the cleared map and succeeds.
     */
    fun structureHandlerRegistersAndClearsStructure(helper: GameTestHelper) {
        val server = helper.level.server
        val validStructId = Identifier.fromNamespaceAndPath("minecraft", "igloo")
        val entryId = "gt-struct-valid-${System.nanoTime()}"
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
            listOf(makeStructureEntry(entryId, validStructId, spawnPos)),
        )

        val spawnKey = "apibalego_structure_$entryId"
        // runWhenServerStarted now does more work (chunk lookup + ScheduledServerTask), so
        // give it 2 ticks instead of 1 to guarantee it completes before checking the maps.
        helper.runAfterDelay(2) {
            helper.assertTrue(
                RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(spawnKey),
                "Valid structure '$validStructId' must be in handler spawn map after dispatch",
            )
            helper.assertTrue(
                FxLibServices.fixedStructureGeneration.registeredStructureSpawns.containsKey(spawnKey),
                "Handler must call fixedStructureGeneration.register() — spawnKey '$spawnKey' not in FxLib queue",
            )

            // Verify placement via vanilla StructureManager routed through FxLib's mixin.
            // startsForStructure(ChunkPos, Predicate) checks at chunk granularity so the igloo
            // doesn't need to overlap spawnPos exactly (igloo doesn't implement FixablePosition).
            // Clear check is also here: first retry after the world check passes dispatches the
            // clear (async); the next retry sees the empty map and all assertions pass.
            helper.succeedWhen {
                val starts = helper.level.structureManager().startsForStructure(spawnChunk) { it == structure }
                helper.assertTrue(
                    structure != null && starts.isNotEmpty(),
                    "Structure '$validStructId' not found via vanilla StructureManager in chunk $spawnChunk",
                )
                RemoteStructuresHandler.handleApiUpdate(server, emptyList())
                // Tests run concurrently — check our specific key was cleared, not isEmpty()
                helper.assertFalse(
                    RemoteStructuresHandler.STRUCTS_TO_SPAWN_BY_ID.containsKey(spawnKey),
                    "Spawn map must not contain entry after re-dispatch with no entries",
                )
            }
        }
    }

    /** Command entry with the same id is not executed more than once. */
    fun commandDispatchIdempotent(helper: GameTestHelper) {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteCommandExecution
        ApiBalegoConfig.remoteCommandExecution = true
        val entryId = "gt-cmd-idem-${System.nanoTime()}"
        // Use send_command_feedback (default true) to avoid clashing with commandDispatchRunsCommand's keep_inventory
        val entry = ApiEntryRaw(
            type = ApiEntryRegistry.lookup(ID_API_HANDLER_COMMAND),
            details = buildJsonObject { put("command", JsonPrimitive("gamerule send_command_feedback false")) },
            id = entryId,
            active = true,
        )

        ApiEntryRegistry.dispatchUpdate(listOf(entry), server)

        helper.runAfterDelay(1) {
            helper.assertFalse(
                helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                "first dispatch should set send_command_feedback false",
            )

            server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule send_command_feedback true")
            helper.assertTrue(
                helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                "manual reset to true failed",
            )

            ApiEntryRegistry.dispatchUpdate(listOf(entry), server)
            // wait another tick for the second dispatch's runWhenServerStarted callback
            helper.runAfterDelay(1) {
                try {
                    helper.assertTrue(
                        helper.level.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK),
                        "second dispatch with same id must be skipped (idempotent)",
                    )
                    helper.succeed()
                } finally {
                    server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule send_command_feedback true")
                    ApiBalegoConfig.remoteCommandExecution = prevEnabled
                }
            }
        }
    }
}
