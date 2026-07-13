package com.ruslan.apibalego

import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ApiEntryRegistryTest {
    @Serializable
    data class TestDetails(val value: String, val count: Int)

    @Test
    fun `typed entry details are parsed and delivered to update handler`() {
        val key = Identifier.fromNamespaceAndPath("unittest", "typed_${System.nanoTime()}")
        val server = mock<MinecraftServer>()
        var received: TestDetails? = null

        ApiEntryRegistry.register(
            key,
            TestDetails.serializer(),
            object : ApiEntryHandler<TestDetails> {
                override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<TestDetails>>) {
                    received = entries.firstOrNull()?.details
                }
                override fun handleApiJoin(player: ServerPlayer, entries: Collection<ApiEntry<TestDetails>>) {}
            },
        )

        val type = ApiEntryRegistry.lookupRaw(key)
        val rawDetails = buildJsonObject {
            put("value", JsonPrimitive("hello"))
            put("count", JsonPrimitive(42))
        }

        ApiEntryRegistry.dispatchUpdate(
            listOf(ApiEntryRaw(type = type, details = rawDetails, id = "test-id", active = true)),
            server,
        )

        assertEquals(TestDetails(value = "hello", count = 42), received)
    }
}
