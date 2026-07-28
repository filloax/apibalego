package com.ruslan.apibalego

import com.ruslan.apibalego.api.Apibalego
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ApibalegoEntryPointTest {
    @Serializable
    data class TestDetails(val value: String)

    @Test
    fun `kotlin serializer overload keeps working`() {
        val key = Identifier.fromNamespaceAndPath("unittest", "kt_serializer_${System.nanoTime()}")
        var received: TestDetails? = null

        Apibalego.registerApiHandler(key, TestDetails.serializer(), object : ApiEntryHandler<TestDetails> {
            override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<TestDetails>>) {
                received = entries.firstOrNull()?.details
            }
        })

        val type = ApiEntryRegistry.lookupRaw(key)
        type.dispatchUpdate(
            listOf(ApiEntryRaw(type, buildJsonObject { put("value", JsonPrimitive("hi")) }, "id", true)),
            mock<MinecraftServer>(),
        )

        assertEquals(TestDetails("hi"), received)
    }

    @Test
    fun `kotlin serializer overload of live update events keeps working`() {
        val eventName = "kt_live_${System.nanoTime()}"
        var received: TestDetails? = null

        val event = Apibalego.registerLiveUpdateEvent(eventName, TestDetails.serializer()) { data, _, _ ->
            received = data
        }

        event.dispatch("""{"value": "live"}""", mock<MinecraftServer>(), mock<ResponseSender>())

        assertEquals(TestDetails("live"), received)
    }

    @Test
    fun `kotlin lambda overload of simple live update events keeps working`() {
        val eventName = "kt_live_simple_${System.nanoTime()}"
        var called = false

        val event = Apibalego.registerSimpleLiveUpdateEvent(eventName) { _, _ -> called = true }

        event.dispatch("", mock<MinecraftServer>(), mock<ResponseSender>())

        assertEquals(true, called)
    }

    @Test
    fun `kotlin lambda overload of registerSimple keeps working`() {
        val key = Identifier.fromNamespaceAndPath("unittest", "kt_simple_${System.nanoTime()}")
        var receivedId: String? = null

        Apibalego.registerSimple(key, { _, entries -> receivedId = entries.firstOrNull()?.id })

        val type = ApiEntryRegistry.lookupRaw(key)
        type.dispatchUpdate(listOf(ApiEntryRaw(type, null, "simple-id", true)), mock<MinecraftServer>())

        assertEquals("simple-id", receivedId)
    }
}
