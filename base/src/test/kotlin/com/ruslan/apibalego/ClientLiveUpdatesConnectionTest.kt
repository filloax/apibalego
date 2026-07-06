package com.ruslan.apibalego

import com.ruslan.apibalego.client.socket.ClientLiveUpdatesConnection
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesEventRegistry
import com.ruslan.apibalego.socket.LiveSocket
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Serializable
private data class ClientTestMsg(val v: String)

/**
 * Drives [ClientLiveUpdatesConnection] against a fake/mocked [LiveSocket] — socket.io mocked
 * at the boundary, no real network and no Minecraft client needed. Mirrors
 * [LiveUpdatesConnectionTest] on the server side.
 */
class ClientLiveUpdatesConnectionTest {
    private class FakeLiveSocket : LiveSocket {
        val sent = mutableListOf<String>()
        val listeners = mutableMapOf<String, (String) -> Unit>()
        override fun on(event: String, handler: (String) -> Unit) { listeners[event] = handler }
        override fun send(message: String) { sent.add(message) }
    }

    @Test
    fun `sendSuccess emits a success status with extra fields over the socket`() {
        val conn = ClientLiveUpdatesConnection()
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
        val conn = ClientLiveUpdatesConnection()
        val socket = FakeLiveSocket()
        conn.liveSocket = socket

        conn.sendFailure("boom")

        val msg = socket.sent.single()
        assertTrue(msg.contains(""""status": "failure""""), msg)
        assertTrue(msg.contains(""""reason": "boom""""), msg)
    }

    @Test
    fun `bindHandlers dispatches incoming event to registered handler`() {
        val client = mock<Minecraft>()
        val conn = ClientLiveUpdatesConnection(client)
        val socket = FakeLiveSocket()
        val eventName = "test-dispatch-${System.nanoTime()}"
        val received = mutableListOf<String>()

        ClientLiveUpdatesEventRegistry.register(eventName, ClientTestMsg.serializer()) { data, _, _ -> received.add(data.v) }
        conn.bindHandlers(socket)
        socket.listeners[eventName]?.invoke("""{"v":"payload"}""")

        assertEquals(listOf("payload"), received)
    }

    @Test
    fun `bindHandlers catches handler exception and sends failure`() {
        val client = mock<Minecraft>()
        val conn = ClientLiveUpdatesConnection(client)
        val socket = FakeLiveSocket()
        conn.liveSocket = socket
        val eventName = "test-throw-${System.nanoTime()}"

        ClientLiveUpdatesEventRegistry.register(eventName, ClientTestMsg.serializer()) { _, _, _ -> throw RuntimeException("handler-error") }
        conn.bindHandlers(socket)
        socket.listeners[eventName]?.invoke("""{"v":"trigger"}""")

        val msg = socket.sent.single()
        assertTrue(msg.contains(""""status": "failure""""), msg)
        assertTrue(msg.contains("handler-error"), msg)
    }

    @Test
    fun `bindHandlers registers events on Mockito-mocked LiveSocket and dispatches`() {
        val client = mock<Minecraft>()
        val conn = ClientLiveUpdatesConnection(client)
        val socket = mock<LiveSocket>()
        val eventName = "test-mockito-${System.nanoTime()}"
        val received = mutableListOf<String>()

        ClientLiveUpdatesEventRegistry.register(eventName, ClientTestMsg.serializer()) { data, _, _ -> received.add(data.v) }
        conn.bindHandlers(socket)

        val handlerCaptor = argumentCaptor<(String) -> Unit>()
        verify(socket, atLeastOnce()).on(eq(eventName), handlerCaptor.capture())
        handlerCaptor.lastValue.invoke("""{"v":"mockito-payload"}""")

        assertEquals(listOf("mockito-payload"), received)
    }

    // integration tests

    private fun stubSocketConnect(
        mockSocket: Socket,
        capturedListeners: MutableMap<String, Emitter.Listener>,
    ) {
        whenever(mockSocket.on(any<String>(), any())).thenAnswer { inv ->
            val event = inv.getArgument<String>(0)
            val listener = inv.getArgument<Emitter.Listener>(1)
            capturedListeners[event] = listener
            if (event == Socket.EVENT_CONNECT) Thread { listener.call() }.start()
            mockSocket
        }
        whenever(mockSocket.isActive).thenReturn(true)
    }

    @Test
    fun `start wires full event pipeline and dispatches through real connect and listen`() {
        val prevRetry = ClientLiveUpdatesConnection.retryTimeSeconds
        ClientLiveUpdatesConnection.retryTimeSeconds = 1
        try {
            val client = mock<Minecraft>()
            val mockSocket = mock<Socket>()
            val capturedListeners = mutableMapOf<String, Emitter.Listener>()
            val eventName = "test-integration-${System.nanoTime()}"
            val handlerBound = CountDownLatch(1)

            stubSocketConnect(mockSocket, capturedListeners)
            whenever(mockSocket.on(eq(eventName), any())).thenAnswer { inv ->
                capturedListeners[eventName] = inv.getArgument(1)
                handlerBound.countDown()
                mockSocket
            }

            val received = mutableListOf<String>()
            ClientLiveUpdatesEventRegistry.register(eventName, ClientTestMsg.serializer()) { data, _, sender ->
                received.add(data.v)
                sender.sendSuccess()
            }

            val conn = ClientLiveUpdatesConnection(client) { _, _ -> mockSocket }
            conn.start()
            try {
                assertTrue(handlerBound.await(3, TimeUnit.SECONDS), "handler not bound within 3 s")
                capturedListeners[eventName]?.call("""{"v":"integration-payload"}""")
                assertEquals(listOf("integration-payload"), received)
            } finally {
                conn.stop()
            }
        } finally {
            ClientLiveUpdatesConnection.retryTimeSeconds = prevRetry
        }
    }

    @Test
    fun `start handler exception sends failure without crashing connection`() {
        val prevRetry = ClientLiveUpdatesConnection.retryTimeSeconds
        ClientLiveUpdatesConnection.retryTimeSeconds = 1
        try {
            val client = mock<Minecraft>()
            val mockSocket = mock<Socket>()
            val capturedListeners = mutableMapOf<String, Emitter.Listener>()
            val eventName = "test-integration-err-${System.nanoTime()}"
            val handlerBound = CountDownLatch(1)

            stubSocketConnect(mockSocket, capturedListeners)
            whenever(mockSocket.on(eq(eventName), any())).thenAnswer { inv ->
                capturedListeners[eventName] = inv.getArgument(1)
                handlerBound.countDown()
                mockSocket
            }

            ClientLiveUpdatesEventRegistry.register(eventName, ClientTestMsg.serializer()) { _, _, _ -> throw RuntimeException("integration-error") }

            val conn = ClientLiveUpdatesConnection(client) { _, _ -> mockSocket }
            conn.start()
            try {
                assertTrue(handlerBound.await(3, TimeUnit.SECONDS), "handler not bound within 3 s")
                capturedListeners[eventName]?.call("""{"v":"trigger"}""")
                verify(mockSocket, atLeastOnce()).emit(eq("mod_response"), any<String>())
            } finally {
                conn.stop()
            }
        } finally {
            ClientLiveUpdatesConnection.retryTimeSeconds = prevRetry
        }
    }
}
