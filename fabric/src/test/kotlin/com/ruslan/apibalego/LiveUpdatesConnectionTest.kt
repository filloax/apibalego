package com.ruslan.apibalego

import com.ruslan.apibalego.http.LiveSocket
import com.ruslan.apibalego.http.LiveUpdatesConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [LiveUpdatesConnection]'s response path against a fake [LiveSocket] — socket.io mocked
 * with a plain in-memory double, no real network and no Minecraft server needed.
 */
class LiveUpdatesConnectionTest {
    /** In-memory stand-in for the socket.io socket. */
    private class FakeLiveSocket : LiveSocket {
        val sent = mutableListOf<String>()
        val listeners = mutableMapOf<String, (String) -> Unit>()
        override fun on(event: String, handler: (String) -> Unit) { listeners[event] = handler }
        override fun send(message: String) { sent.add(message) }
    }

    @Test
    fun `sendSuccess emits a success status with extra fields over the socket`() {
        val conn = LiveUpdatesConnection()
        val socket = FakeLiveSocket()
        conn.liveSocket = socket

        conn.sendSuccess(mapOf("amount" to 3))

        assertEquals(1, socket.sent.size)
        val msg = socket.sent.single()
        assertTrue(msg.contains(""""status": "success""""), msg)
        assertTrue(msg.contains(""""amount": "3"""), msg)
    }

    @Test
    fun `sendFailure emits a failure status with the reason over the socket`() {
        val conn = LiveUpdatesConnection()
        val socket = FakeLiveSocket()
        conn.liveSocket = socket

        conn.sendFailure("boom")

        val msg = socket.sent.single()
        assertTrue(msg.contains(""""status": "failure""""), msg)
        assertTrue(msg.contains(""""reason": "boom""""), msg)
    }
}
