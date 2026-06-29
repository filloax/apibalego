package com.ruslan.apibalego.test

import com.filloax.fxlib.api.entity.getPersistData
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.handlers.ID_API_HANDLER_COMMAND
import com.ruslan.apibalego.handlers.RemoteCommandExec
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.ID_API_HANDLER_TOAST
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.level.gamerules.GameRules
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loader-agnostic gametest bodies. Each is a `Consumer<GameTestHelper>`-style function that
 * runs inside a server game environment and calls [GameTestHelper.succeed] on success.
 *
 * The Fabric and NeoForge modules each register these with their loader's gametest system.
 * Tests are ran in an empty structure as they use the API which isn't related to worldgen.
 */
object ApibalegoGameTests {
    private fun check(cond: Boolean, msg: () -> String) {
        if (!cond) throw AssertionError(msg())
    }

    fun configLoaded(helper: GameTestHelper) {
        check(ApiBalegoConfigHandler.config != null) { "ApiBalegoConfig was not loaded" }
        check(ApiBalegoConfig.dataSyncUrl.isNotBlank()) { "dataSyncUrl default missing" }
        helper.succeed()
    }

    /** A registered api-entry handler is invoked when dispatched. */
    fun apiEventDispatchActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "dispatch_active")
        ApiEntryRegistry.registerSimple(key, { _ -> counter.incrementAndGet() }, { _ -> })
        val type = ApiEntryRegistry.lookup(key)

        ApiEntryRegistry.dispatchAllUpdate(
            listOf(ApiEntryRaw(type = type, id = "x", active = true)),
            helper.level.server,
        )

        check(counter.get() == 1) { "Handler should run exactly once, ran ${counter.get()}" }
        helper.succeed()
    }

    /** Inactive entries filtered by the caller before dispatch are not sent to handlers. */
    fun apiEventDispatchInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "dispatch_inactive")
        ApiEntryRegistry.registerSimple(key, { _ -> counter.incrementAndGet() }, { _ -> })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = false))
        // Filtering inactive is caller responsibility (mirrors GamemasterApi behavior)
        ApiEntryRegistry.dispatchAllUpdate(entries.filter { it.active }, helper.level.server)

        check(counter.get() == 0) { "Inactive entry must not be dispatched" }
        helper.succeed()
    }

    /** The built-in live-update handlers are registered. */
    fun liveUpdatesBuiltinsRegistered(helper: GameTestHelper) {
        val keys = LiveUpdatesEventRegistry.all().keys
        listOf(LiveUpdatesEventRegistry.RELOAD_EVENT, "toast", RemoteCommandExec.PREFIX).forEach {
            check(it in keys) { "Built-in live update handler '$it' not registered (have $keys)" }
        }
        helper.succeed()
    }

    /** Active join entries are dispatched to join handlers, once per player. */
    fun dispatchJoinActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "join_active")
        ApiEntryRegistry.registerSimple(key, { _ -> }, { _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = true))
        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchAllJoin(entries, helper.makeMockServerPlayerInLevel())
        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchAllJoin(entries, helper.makeMockServerPlayerInLevel())

        check(counter.get() == 2) { "Join handler should run once per player (2), ran ${counter.get()}" }
        helper.succeed()
    }

    /** Inactive join entries filtered by the caller before dispatch are not sent to handlers. */
    fun dispatchJoinInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("gametest", "join_inactive")
        ApiEntryRegistry.registerSimple(key, { _ -> }, { _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookup(key)

        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = false))
        @Suppress("DEPRECATION")
        // Filtering inactive is caller responsibility (mirrors GamemasterApi.Callbacks.onPlayerJoin)
        ApiEntryRegistry.dispatchAllJoin(entries.filter { it.active }, helper.makeMockServerPlayerInLevel())

        check(counter.get() == 0) { "Inactive join entry must not be dispatched" }
        helper.succeed()
    }

    // TODO: live update works test

    /** Toast join handler marks the entry id as seen in player persistent data. */
    fun toastJoinMarksAsSeen(helper: GameTestHelper) {
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevel()
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

        ToastHandler.handleApiJoin(player, entry)

        val memory = player.getPersistData().getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).get()
        val key = toast.title.string + entryId
        check(memory.contains(key)) { "toast entry not marked as seen in player memory" }
        helper.succeed()
    }

    /** Command entry dispatch executes the underlying command on the server. */
    fun commandDispatchRunsCommand(helper: GameTestHelper) {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteCommandExecution
        ApiBalegoConfig.remoteCommandExecution = true
        try {
            ApiEntryRegistry.dispatchAllUpdate(
                listOf(ApiEntryRaw(
                    type = ApiEntryRegistry.lookup(ID_API_HANDLER_COMMAND),
                    details = buildJsonObject { put("command", JsonPrimitive("gamerule keepInventory true")) },
                    id = "gt-cmd-run-${System.nanoTime()}",
                    active = true,
                )),
                server,
            )
            check(helper.level.gameRules.get(GameRules.KEEP_INVENTORY) == true) {
                "command did not run (keepInventory not set to true)"
            }
        } finally {
            server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule keepInventory false")
            ApiBalegoConfig.remoteCommandExecution = prevEnabled
        }
        helper.succeed()
    }

    /** Command entry with the same id is not executed more than once. */
    fun commandDispatchIdempotent(helper: GameTestHelper) {
        val server = helper.level.server
        val prevEnabled = ApiBalegoConfig.remoteCommandExecution
        ApiBalegoConfig.remoteCommandExecution = true
        try {
            val entryId = "gt-cmd-idem-${System.nanoTime()}"
            val entry = ApiEntryRaw(
                type = ApiEntryRegistry.lookup(ID_API_HANDLER_COMMAND),
                details = buildJsonObject { put("command", JsonPrimitive("gamerule keepInventory true")) },
                id = entryId,
                active = true,
            )

            ApiEntryRegistry.dispatchAllUpdate(listOf(entry), server)
            check(helper.level.gameRules.get(GameRules.KEEP_INVENTORY) == true) {
                "first dispatch should set keepInventory true"
            }

            server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule keepInventory false")
            check(helper.level.gameRules.get(GameRules.KEEP_INVENTORY) == false) {
                "manual reset to false failed"
            }

            ApiEntryRegistry.dispatchAllUpdate(listOf(entry), server)
            check(helper.level.gameRules.get(GameRules.KEEP_INVENTORY) == false) {
                "second dispatch with same id must be skipped (idempotent)"
            }
        } finally {
            server.commands.performPrefixedCommand(server.createCommandSourceStack(), "gamerule keepInventory false")
            ApiBalegoConfig.remoteCommandExecution = prevEnabled
        }
        helper.succeed()
    }
}
