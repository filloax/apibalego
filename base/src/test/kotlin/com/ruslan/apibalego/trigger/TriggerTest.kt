package com.ruslan.apibalego.trigger

import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.ID_API_HANDLER_TRIGGER
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class TriggerTest {
    companion object {
        /** Run keys returned by the test trigger type, by player uuid; missing = not triggered */
        private val runKeys = mutableMapOf<UUID, String>()

        private val noopHandler = object : ApiEntryHandler<Nothing> {
            override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<Nothing>>) {}
        }

        private lateinit var triggerType: TriggerType<*>
        private lateinit var detailsTriggerType: TriggerType<String>
        private lateinit var sharedType: ApiEntryType<*>
        private lateinit var playerType: ApiEntryType<*>
        private lateinit var triggerApiType: ApiEntryType<TriggerRaw>

        @JvmStatic
        @BeforeAll
        fun setup() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            // BuiltinApiHandlers.registerAll needs FxLib's platform services, unavailable here
            ApiEntryRegistry.register(ID_API_HANDLER_TRIGGER, TriggerRaw.serializer(), object : ApiEntryHandler<TriggerRaw> {
                override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<TriggerRaw>>) {}
            })

            val triggerKey = Identifier.fromNamespaceAndPath("unittest", "trigger_${System.nanoTime()}")
            TriggerRegistry.register(triggerKey, null, object : TriggerTypeHandler<Nothing> {
                override fun runKey(player: ServerPlayer, details: Nothing?) = runKeys[player.uuid]
            }, Nothing::class)
            triggerType = TriggerRegistry.lookupRaw(triggerKey)

            val detailsTriggerKey = Identifier.fromNamespaceAndPath("unittest", "trigger_details_${System.nanoTime()}")
            TriggerRegistry.register(detailsTriggerKey, String.serializer(), object : TriggerTypeHandler<String> {
                override fun runKey(player: ServerPlayer, details: String?) = runKeys[player.uuid]
            })
            detailsTriggerType = TriggerRegistry.lookup(detailsTriggerKey)

            val sharedKey = Identifier.fromNamespaceAndPath("unittest", "trigger_shared_${System.nanoTime()}")
            val playerKey = Identifier.fromNamespaceAndPath("unittest", "trigger_player_${System.nanoTime()}")
            ApiEntryRegistry.register(sharedKey, null, noopHandler, Nothing::class, alwaysShared = true)
            ApiEntryRegistry.register(playerKey, null, noopHandler, Nothing::class, alwaysShared = false)
            sharedType = ApiEntryRegistry.lookupRaw(sharedKey)
            playerType = ApiEntryRegistry.lookupRaw(playerKey)
            triggerApiType = ApiEntryRegistry.lookup(ID_API_HANDLER_TRIGGER)
        }

        private fun player(name: String, runKey: String?): ServerPlayer {
            val uuid = UUID.randomUUID()
            if (runKey != null) runKeys[uuid] = runKey
            return mock<ServerPlayer> {
                on { getUUID() } doReturn uuid
                on { scoreboardName } doReturn name
            }
        }

        private fun triggerEntry(
            id: String,
            events: List<TriggerApiEntry> = listOf(TriggerApiEntry(sharedType), TriggerApiEntry(playerType)),
            onlyPlayers: Set<String> = setOf(),
        ) = ApiEntry(
            type = triggerApiType,
            details = TriggerRaw(events, triggerType, onlyPlayers = onlyPlayers),
            id = id,
        )
    }

    @Test
    fun `resolved events get their index as key unless set`() {
        val trigger = TriggerRaw(
            listOf(TriggerApiEntry(sharedType), TriggerApiEntry(playerType, key = "custom")),
            triggerType,
        ).resolve(triggerType)

        assertEquals(listOf("0", "custom"), trigger.events.map { it.childKey })
    }

    @Test
    fun `onlyPlayer is merged into onlyPlayers`() {
        val trigger = TriggerRaw(listOf(), triggerType, onlyPlayer = "a", onlyPlayers = setOf("b")).resolve(triggerType)

        assertEquals(setOf("a", "b"), trigger.onlyPlayers)
    }

    @Test
    fun `nested triggers are rejected`() {
        val raw = TriggerRaw(listOf(TriggerApiEntry(triggerApiType)), triggerType)

        assertThrows(IllegalArgumentException::class.java) { raw.resolve(triggerType) }
    }

    @Test
    fun `missing details are rejected for trigger types with details`() {
        val raw = TriggerRaw(listOf(), detailsTriggerType)

        assertThrows(IllegalArgumentException::class.java) { raw.resolve(detailsTriggerType) }
    }

    @Test
    fun `trigger details are parsed`() {
        val trigger = TriggerRaw(listOf(), detailsTriggerType, triggerDetails = JsonPrimitive("x")).resolve(detailsTriggerType)

        assertEquals("x", trigger.triggerDetails)
    }

    @Test
    fun `trigger entries parse from json`() {
        val json = """
            {"type": "$ID_API_HANDLER_TRIGGER", "id": "t", "details": {
                "triggerType": "${triggerType.key}",
                "events": [{"type": "${playerType.key}", "key": "k"}]
            }}
        """.trimIndent()
        val raw = Json.decodeFromString(ApiEntryRaw.serializer(), json)
        val player = player("p", "1")

        val entries = TriggerHandler.resolveTriggerEntries(listOf(raw), listOf(player))

        assertEquals(listOf("trigger/t/${player.uuid}/1/k"), entries.map { it.id })
    }

    @Test
    fun `untriggered players produce no entries`() {
        val entries = TriggerHandler.resolveTriggerEntries(listOf(triggerEntry("t")), listOf(player("p", null)))

        assertTrue(entries.isEmpty(), "Expected no entries, got $entries")
    }

    @Test
    fun `per-player events have player and run key in id, shared events are deduplicated`() {
        val p1 = player("p1", "1")
        val p2 = player("p2", "5")

        val entries = TriggerHandler.resolveTriggerEntries(listOf(triggerEntry("t")), listOf(p1, p2))

        val shared = entries.filter { it.type == sharedType }
        assertEquals(listOf("trigger/t/0"), shared.map { it.id })
        assertNull(shared.single().forPlayer, "Shared events must not be tied to a player")

        val perPlayer = entries.filter { it.type == playerType }
        assertEquals(
            setOf("trigger/t/${p1.uuid}/1/1" to p1.uuid, "trigger/t/${p2.uuid}/5/1" to p2.uuid),
            perPlayer.map { it.id to it.forPlayer }.toSet(),
        )
    }

    @Test
    fun `run key change changes per-player ids only`() {
        val player = player("p", "1")
        val before = TriggerHandler.resolveTriggerEntries(listOf(triggerEntry("t")), listOf(player))
        runKeys[player.uuid] = "2"
        val after = TriggerHandler.resolveTriggerEntries(listOf(triggerEntry("t")), listOf(player))

        assertEquals(
            before.filter { it.type == sharedType }.map { it.id },
            after.filter { it.type == sharedType }.map { it.id },
        )
        assertEquals(listOf("trigger/t/${player.uuid}/2/1"), after.filter { it.type == playerType }.map { it.id })
    }

    @Test
    fun `onlyPlayers matches by name or uuid`() {
        val byName = player("allowed", "1")
        val byUuid = player("other", "1")
        val excluded = player("excluded", "1")
        val entry = triggerEntry("t", listOf(TriggerApiEntry(playerType)), setOf("allowed", byUuid.uuid.toString()))

        val entries = TriggerHandler.resolveTriggerEntries(listOf(entry), listOf(byName, byUuid, excluded))

        assertEquals(setOf(byName.uuid, byUuid.uuid), entries.map { it.forPlayer }.toSet())
    }

    @Test
    fun `invalid triggers are skipped without affecting others`() {
        val broken = ApiEntry(
            type = triggerApiType,
            details = TriggerRaw(listOf(TriggerApiEntry(playerType)), detailsTriggerType),
            id = "broken",
        )

        val entries = TriggerHandler.resolveTriggerEntries(listOf(broken, triggerEntry("ok")), listOf(player("p", "1")))

        assertTrue(entries.isNotEmpty() && entries.all { it.id.startsWith("trigger/ok/") }, "Got $entries")
    }

    @Test
    fun `non-trigger entries are ignored`() {
        val raw = ApiEntryRaw(type = playerType, id = "x", details = buildJsonObject {})

        assertTrue(TriggerHandler.resolveTriggerEntries(listOf(raw), listOf(player("p", "1"))).isEmpty())
    }
}
