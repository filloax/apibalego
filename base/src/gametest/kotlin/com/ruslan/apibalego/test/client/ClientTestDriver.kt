package com.ruslan.apibalego.test.client

import net.minecraft.client.Minecraft

/**
 * Minimal loader-agnostic wrapper over a client gametest context (e.g. Fabric's
 * ClientGameTestContext), so client test bodies can live in base like [com.ruslan.apibalego.test.ApibalegoGameTests].
 * Test bodies run on the test thread; use [onClient] for anything touching client state.
 */
interface ClientTestDriver {
    fun waitTicks(ticks: Int)

    /** Retries [predicate] every tick until true, failing with [message] after [timeoutTicks]. */
    fun waitFor(message: String, timeoutTicks: Int = 200, predicate: (Minecraft) -> Boolean)

    fun <T> onClient(block: (Minecraft) -> T): T
}
