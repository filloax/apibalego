package com.ruslan.apibalego

import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.BuiltinLiveUpdateEvents
import com.ruslan.apibalego.socket.LIVE_EVENT_CMD
import com.ruslan.apibalego.socket.LIVE_EVENT_RELOAD
import com.ruslan.apibalego.socket.LIVE_EVENT_TOAST
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

// JUnit for things that do not require gametests
// which are wonky when too many things done at once
class ApibalegoDispatchTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `a registered api-entry handler is invoked when dispatched`() {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("unittest", "dispatch_active_${System.nanoTime()}")
        ApiEntryRegistry.registerSimple(key, { _, _ -> counter.incrementAndGet() }, { _, _ -> })
        val type = ApiEntryRegistry.lookupRaw(key)
        val server = mock<MinecraftServer>()

        type.dispatchUpdate(listOf(ApiEntryRaw(type = type, id = "x", active = true)), server)

        assertEquals(1, counter.get(), "Handler should run exactly once, ran ${counter.get()}")
    }

    /**
     * dispatchUpdate must still invoke a registered type's handler with an empty list when that
     * type has no entries in the given batch, so stateful handlers (datapack, structure) see
     * "removed" rather than simply not being called at all.
     */
    @Test
    fun `dispatchUpdate hits absent types with an empty list`() {
        val calledWithSize = AtomicInteger(-1)
        val presentKey = Identifier.fromNamespaceAndPath("unittest", "dispatch_full_present_${System.nanoTime()}")
        val absentKey = Identifier.fromNamespaceAndPath("unittest", "dispatch_full_absent_${System.nanoTime()}")
        ApiEntryRegistry.registerSimple(presentKey, { _, _ -> })
        ApiEntryRegistry.registerSimple(absentKey, { _, entries -> calledWithSize.set(entries.size) })
        val presentType = ApiEntryRegistry.lookupRaw(presentKey)
        val server = mock<MinecraftServer>()

        ApiEntryRegistry.dispatchUpdate(
            listOf(ApiEntryRaw(type = presentType, id = "x", active = true)),
            server,
        )

        assertEquals(
            0, calledWithSize.get(),
            "Handler for a type absent from the full-update entries should still run, with an empty list (got ${calledWithSize.get()})",
        )
    }

    @Test
    fun `the built-in live-update handlers are registered`() {
        BuiltinLiveUpdateEvents.registerAll()
        val keys = LiveUpdatesEventRegistry.all().keys
        listOf(LIVE_EVENT_RELOAD, LIVE_EVENT_TOAST, LIVE_EVENT_CMD).forEach {
            assertTrue(it in keys, "Built-in live update handler '$it' not registered (have $keys)")
        }
    }

    @Test
    fun `active join entries are dispatched to join handlers, once per player`() {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("unittest", "join_active_${System.nanoTime()}")
        ApiEntryRegistry.registerSimple(key, { _, _ -> }, { _, _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookupRaw(key)
        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = true))

        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchJoin(entries, mock<ServerPlayer>())
        @Suppress("DEPRECATION")
        ApiEntryRegistry.dispatchJoin(entries, mock<ServerPlayer>())

        assertEquals(2, counter.get(), "Join handler should run once per player (2), ran ${counter.get()}")
    }

    @Test
    fun `inactive join entries filtered by the caller before dispatch are not sent to handlers`() {
        val counter = AtomicInteger(0)
        val key = Identifier.fromNamespaceAndPath("unittest", "join_inactive_${System.nanoTime()}")
        ApiEntryRegistry.registerSimple(key, { _, _ -> }, { _, _ -> counter.incrementAndGet() })
        val type = ApiEntryRegistry.lookupRaw(key)
        val entries = listOf(ApiEntryRaw(type = type, id = "x", active = false))

        @Suppress("DEPRECATION")
        // Filtering inactive is caller responsibility (mirrors GamemasterApi.Callbacks.onPlayerJoin)
        ApiEntryRegistry.dispatchJoin(entries.filter { it.active }, mock<ServerPlayer>())

        assertEquals(0, counter.get(), "Inactive join entry must not be dispatched")
    }
}
