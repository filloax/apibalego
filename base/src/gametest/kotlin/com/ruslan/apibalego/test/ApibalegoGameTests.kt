package com.ruslan.apibalego.test

import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.ApiEventRegistry
import com.ruslan.apibalego.http.GenericApiEvent
import com.ruslan.apibalego.http.LiveUpdatesEventRegistry
import com.ruslan.apibalego.http.RemoteCommandExec
import com.ruslan.apibalego.network.CustomToastPacket
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
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

    /** A registered api-event handler is invoked for an active event with its prefix. */
    fun apiEventDispatchActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val prefix = "gametest_active"
        ApiEventRegistry.registerHandler(prefix) { _, _ -> counter.incrementAndGet() }

        ApiEventRegistry.dispatch(
            listOf(GenericApiEvent(name = "$prefix/x", active = true)),
            helper.level.server,
        )

        check(counter.get() == 1) { "Active event handler should run exactly once, ran ${counter.get()}" }
        helper.succeed()
    }

    /** Inactive events are not dispatched to handlers. */
    fun apiEventDispatchInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val prefix = "gametest_inactive"
        ApiEventRegistry.registerHandler(prefix) { _, _ -> counter.incrementAndGet() }

        ApiEventRegistry.dispatch(
            listOf(GenericApiEvent(name = "$prefix/x", active = false)),
            helper.level.server,
        )

        check(counter.get() == 0) { "Inactive event must not be dispatched" }
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

    /** Active join events are dispatched to registered join handlers, once per player. */
    fun dispatchJoinActive(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val prefix = "gametest_join_active"
        ApiEventRegistry.registerJoinHandler(prefix) { _, _ -> counter.incrementAndGet() }
        val events = listOf(GenericApiEvent(name = "$prefix/x", active = true))
        @Suppress("DEPRECATION")
        ApiEventRegistry.dispatchJoin(events, helper.makeMockServerPlayerInLevel())
        @Suppress("DEPRECATION")
        ApiEventRegistry.dispatchJoin(events, helper.makeMockServerPlayerInLevel())
        check(counter.get() == 2) { "Join handler should run once per player (2), ran ${counter.get()}" }
        helper.succeed()
    }

    /** Inactive join events are not dispatched. */
    fun dispatchJoinInactiveSkipped(helper: GameTestHelper) {
        val counter = AtomicInteger(0)
        val prefix = "gametest_join_inactive"
        ApiEventRegistry.registerJoinHandler(prefix) { _, _ -> counter.incrementAndGet() }
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevel()
        ApiEventRegistry.dispatchJoin(
            listOf(GenericApiEvent(name = "$prefix/x", active = false)),
            player,
        )
        check(counter.get() == 0) { "Inactive join event must not be dispatched" }
        helper.succeed()
    }

    // TODO: live update works test

    /** Toast packet constructs and preserves its fields in the game environment. */
    fun toastPacketBuilds(helper: GameTestHelper) {
        val packet = CustomToastPacket(Component.literal("title"), Component.literal("msg"))
        check(packet.title.string == "title") { "toast title mismatch: ${packet.title.string}" }
        check(packet.message?.string == "msg") { "toast message mismatch" }
        check(packet == CustomToastPacket(Component.literal("title"), Component.literal("msg"))) {
            "toast equality broken"
        }
        helper.succeed()
    }
}
