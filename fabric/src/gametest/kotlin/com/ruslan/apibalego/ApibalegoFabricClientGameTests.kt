package com.ruslan.apibalego

import com.ruslan.apibalego.test.client.ApibalegoClientGameTests
import com.ruslan.apibalego.test.client.ClientTestDriver
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft

/**
 * Fabric client gametest entry points. Registered via the `fabric-client-gametest` entrypoints in
 * fabric.mod.json; each launches inside a real client. Bodies live in base
 * ([ApibalegoClientGameTests]), same split as the server-side gametests.
 *
 * Run with: `./gradlew :fabric:runClientGameTest` (see build.gradle.kts).
 */
class FabricClientTestDriver(private val context: ClientGameTestContext) : ClientTestDriver {
    override fun waitTicks(ticks: Int) = context.waitTicks(ticks)

    override fun waitFor(message: String, timeoutTicks: Int, predicate: (Minecraft) -> Boolean) {
        try {
            context.waitFor({ client -> predicate(client) }, timeoutTicks)
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $message", e)
        }
    }

    override fun <T> onClient(block: (Minecraft) -> T): T =
        context.computeOnClient<T, Throwable> { client -> block(client) }
}

class MenuMessageClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) =
        ApibalegoClientGameTests.menuMessageReplacesSplash(FabricClientTestDriver(context))
}

class ToastClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) =
        ApibalegoClientGameTests.toastShownOnceAndDeduped(FabricClientTestDriver(context))
}

class ResourcePackClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) =
        ApibalegoClientGameTests.resourcePackFullLifecycle(FabricClientTestDriver(context))
}
