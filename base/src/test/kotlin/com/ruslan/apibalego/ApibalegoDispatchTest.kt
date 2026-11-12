package com.ruslan.apibalego

import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
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
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID
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

    private class RecordingHandler : ApiEntryHandler<Nothing> {
        val updates = mutableListOf<List<String>>()
        val playerUpdates = mutableListOf<Pair<ServerPlayer, List<String>>>()

        override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<Nothing>>) {
            updates.add(entries.map { it.id })
        }

        override fun handleApiUpdatePlayer(player: ServerPlayer, entries: Collection<ApiEntry<Nothing>>) {
            playerUpdates.add(player to entries.map { it.id })
        }
    }

    private fun registerRecording(name: String, alwaysShared: Boolean = true): Pair<RecordingHandler, com.ruslan.apibalego.http.ApiEntryType<*>> {
        val key = Identifier.fromNamespaceAndPath("unittest", "${name}_${System.nanoTime()}")
        val handler = RecordingHandler()
        ApiEntryRegistry.register(key, null, handler, Nothing::class, alwaysShared)
        return handler to ApiEntryRegistry.lookupRaw(key)
    }

    /** Server mock that runs [MinecraftServer.execute] tasks immediately and knows the given players */
    private fun serverWithPlayers(vararg players: ServerPlayer): MinecraftServer {
        val playerList = mock<PlayerList> {
            players.forEach { p -> on { getPlayer(p.uuid) } doReturn p }
        }
        return mock<MinecraftServer> {
            on { execute(any()) } doAnswer { (it.arguments[0] as Runnable).run() }
            on { this.playerList } doReturn playerList
        }
    }

    private fun mockPlayer(): ServerPlayer {
        val uuid = UUID.randomUUID()
        return mock<ServerPlayer> { on { getUUID() } doReturn uuid }
    }

    @Test
    fun `dispatchUpdate with onlyTypes only calls those types`() {
        val (included, includedType) = registerRecording("only_included")
        val (excluded, excludedType) = registerRecording("only_excluded")

        ApiEntryRegistry.dispatchUpdate(
            listOf(ApiEntryRaw(type = includedType, id = "a"), ApiEntryRaw(type = excludedType, id = "b")),
            mock<MinecraftServer>(),
            listOf(includedType),
        )

        assertEquals(listOf(listOf("a")), included.updates)
        assertTrue(excluded.updates.isEmpty(), "Type not in onlyTypes must not be called, got ${excluded.updates}")
    }

    @Test
    fun `player entries are sent to their player only, not to the global update`() {
        val (handler, type) = registerRecording("player_entries", alwaysShared = false)
        val p1 = mockPlayer()
        val p2 = mockPlayer()
        val offline = UUID.randomUUID()

        ApiEntryRegistry.dispatchUpdate(
            listOf(
                ApiEntryRaw(type = type, id = "global"),
                ApiEntryRaw(type = type, id = "p1-a", forPlayer = p1.uuid),
                ApiEntryRaw(type = type, id = "p1-b", forPlayer = p1.uuid),
                ApiEntryRaw(type = type, id = "p2", forPlayer = p2.uuid),
                ApiEntryRaw(type = type, id = "offline", forPlayer = offline),
            ),
            serverWithPlayers(p1, p2),
            listOf(type),
        )

        assertEquals(listOf(listOf("global")), handler.updates)
        assertEquals(
            setOf(p1 to listOf("p1-a", "p1-b"), p2 to listOf("p2")),
            handler.playerUpdates.toSet(),
        )
    }

    @Test
    fun `default player update falls back to the global update`() {
        val received = mutableListOf<String>()
        val handler = object : ApiEntryHandler<Nothing> {
            override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<Nothing>>) {
                received.addAll(entries.map { it.id })
            }
        }
        val key = Identifier.fromNamespaceAndPath("unittest", "player_fallback_${System.nanoTime()}")
        ApiEntryRegistry.register(key, null, handler, Nothing::class, alwaysShared = false)
        val type = ApiEntryRegistry.lookupRaw(key)
        val server = mock<MinecraftServer>()
        val level = mock<ServerLevel> { on { this.server } doReturn server }
        val player = mock<ServerPlayer> { on { level() } doReturn level }

        type.dispatchUpdatePlayer(listOf(ApiEntryRaw(type = type, id = "x")), player)

        assertEquals(listOf("x"), received)
    }

    @Test
    fun `forPlayer is not serialized`() {
        val (_, type) = registerRecording("transient_player")
        val json = Json.encodeToString(ApiEntryRaw.serializer(), ApiEntryRaw(type = type, id = "x", forPlayer = UUID.randomUUID()))

        assertFalse("forPlayer" in json, "forPlayer must be transient, got $json")
    }
}
