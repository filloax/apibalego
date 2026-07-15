package com.ruslan.apibalego.docs

import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.client.http.ClientApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.socket.LiveUpdatesEventHandler
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ApiDocGeneratorTest {
    @Serializable
    data class Nested(val label: String)

    @Serializable
    data class DocDetails(val name: String, val count: Int, val tags: List<String>, val nested: Nested)

    @Test
    fun `generate writes markdown reflecting registered entry structure`(@TempDir tempDir: Path) {
        val suffix = System.nanoTime()
        val serverKey = Identifier.fromNamespaceAndPath("unittest", "doc_server_$suffix")
        val clientKey = Identifier.fromNamespaceAndPath("unittest", "doc_client_$suffix")
        val eventName = "doc_event_$suffix"

        ApiEntryRegistry.register(
            serverKey,
            DocDetails.serializer(),
            object : ApiEntryHandler<DocDetails> {
                override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<DocDetails>>) {}
            },
        )
        ClientApiEntryRegistry.register(
            clientKey,
            DocDetails.serializer(),
        ) { _: Minecraft, _ -> }
        LiveUpdatesEventRegistry.register(
            eventName,
            DocDetails.serializer(),
        ) { _, _: MinecraftServer, _ -> }

        ApiDocGenerator.generate(tempDir)

        val serverDoc = tempDir.resolve("server-api.md").toFile().readText()
        val clientDoc = tempDir.resolve("client-api.md").toFile().readText()
        val liveSyncDoc = tempDir.resolve("live-sync-events.md").toFile().readText()

        for (doc in listOf(serverDoc to serverKey.toString(), clientDoc to clientKey.toString(), liveSyncDoc to eventName)) {
            val (content, key) = doc
            assertTrue(content.contains("## `$key`"), "missing heading for $key")
            assertTrue(content.contains("`name`: String"), "missing name field for $key")
            assertTrue(content.contains("`count`: Int"), "missing count field for $key")
            assertTrue(content.contains("`tags`: List<String>"), "missing tags field for $key")
            assertTrue(content.contains("`nested`: Nested"), "missing nested field for $key")
            assertTrue(content.contains("`label`: String"), "missing nested's label field for $key")
        }
    }
}
