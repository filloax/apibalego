package com.ruslan.apibalego

import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRaw
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ClientApiEntryRegistryTest {
    @Serializable
    data class TestDetails(val value: String, val count: Int)

    @Test
    fun `typed entry details are parsed and delivered to update handler`() {
        val key = Identifier.fromNamespaceAndPath("unittest", "client_typed_${System.nanoTime()}")
        val client = mock<Minecraft>()
        var received: TestDetails? = null

        ClientApiEntryRegistry.register(
            key,
            TestDetails.serializer(),
        ) { _, entries ->
            received = entries.firstOrNull()?.details
        }

        val type = ClientApiEntryRegistry.lookup(key)
        val rawDetails = buildJsonObject {
            put("value", JsonPrimitive("hello"))
            put("count", JsonPrimitive(42))
        }

        ClientApiEntryRegistry.dispatchUpdate(
            listOf(ClientApiEntryRaw(type = type, details = rawDetails, id = "test-id", active = true)),
            client,
        )

        assertEquals(TestDetails(value = "hello", count = 42), received)
    }

    @Test
    fun `entries not matching a registered type are not dispatched to unrelated handlers`() {
        val keyA = Identifier.fromNamespaceAndPath("unittest", "client_a_${System.nanoTime()}")
        val keyB = Identifier.fromNamespaceAndPath("unittest", "client_b_${System.nanoTime()}")
        val client = mock<Minecraft>()
        var receivedB: List<ClientApiEntry<Nothing>>? = null

        ClientApiEntryRegistry.registerSimple(keyA) { _, _ -> }
        ClientApiEntryRegistry.registerSimple(keyB) { _, entries -> receivedB = entries.toList() }

        val typeA = ClientApiEntryRegistry.lookup(keyA)

        ClientApiEntryRegistry.dispatchUpdate(
            listOf(ClientApiEntryRaw(type = typeA, id = "only-a", active = true)),
            client,
        )

        assertEquals(emptyList<ClientApiEntry<Nothing>>(), receivedB)
    }
}
