package com.ruslan.apibalego.test

import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import com.ruslan.apibalego.network.CustomToastPacket
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
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
        listOf(LiveUpdatesEventRegistry.RELOAD_EVENT, "toast").forEach {
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
}
