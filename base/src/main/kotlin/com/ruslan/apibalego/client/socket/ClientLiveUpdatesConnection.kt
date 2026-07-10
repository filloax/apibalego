package com.ruslan.apibalego.client.socket

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.socket.LiveSocket
import com.ruslan.apibalego.socket.ResponseSender
import io.socket.client.IO
import io.socket.client.Socket
import net.minecraft.client.Minecraft
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * Client-side counterpart to [com.ruslan.apibalego.socket.LiveUpdatesConnection]: the client
 * itself connects to the gamemaster over websocket, to allow client-side toasts and reloading
 * client data.
 */
class ClientLiveUpdatesConnection internal constructor(
    private val client: Minecraft? = null,
    private val socketFactory: (URI, IO.Options) -> Socket = Companion::defaultSocket,
) : ResponseSender {
    private var running = true
    private var socket: Socket? = null
    private var thread: Thread? = null
    internal var liveSocket: LiveSocket? = null
    private var asyncLock: Lock = ReentrantLock()

    companion object {
        var retryTimeSeconds = 60

        private var activeConnection: ClientLiveUpdatesConnection? = null

        private fun defaultSocket(uri: URI, options: IO.Options): Socket = IO.socket(uri, options)

        /** No config of its own: piggybacks on [ApiBalegoConfig.liveUpdateService] (feature toggle) and [ApiBalegoConfig.clientDataSync] (client connects on its own). */
        private fun enabled() = ApiBalegoConfig.liveUpdateService && ApiBalegoConfig.clientDataSync

        fun clientStart() {
            if (activeConnection == null && enabled()) {
                val conn = ClientLiveUpdatesConnection(Minecraft.getInstance())
                conn.start()
                activeConnection = conn
            } else if (!enabled()) {
                Apibalego.LOGGER.info("ClientLiveUpdatesConnection was disabled from mod settings, will not start")
            }
        }

        fun clientStop() {
            activeConnection?.stop()
            activeConnection = null
        }
    }

    /**
     * RUN ONLY INSIDE SECONDARY THREAD
     *
     * Attempts connecting every [retryTimeSeconds] seconds to the ip:port specified in config
     */
    private fun connect() {
        var interrupted = false
        while (running && socket == null && !interrupted) {
            // Same endpoint as the server-side connection; the client just listens for different events past "reload".
            val host = ApiBalegoConfig.liveUpdateUrl
            val port = ApiBalegoConfig.liveUpdatePort
            val uri = if (port > 0) URI("$host:$port") else URI(host)
            var success = false

            val connectCondition = asyncLock.newCondition()
            fun fulfillCondition() {
                asyncLock.lock()
                try {
                    connectCondition.signal()
                } finally {
                    asyncLock.unlock()
                }
            }

            try {
                logInfo("Attempting connection to $uri...")

                val options = IO.Options.builder()
                    .setExtraHeaders(mapOf("apiKey" to listOf(ApiBalegoConfig.clientDataSyncApiKey)))
                    .build()
                val newSocket = socketFactory(uri, options)

                newSocket.on(Socket.EVENT_CONNECT) {
                    logInfo("Connected to $uri")
                    success = true
                    fulfillCondition()
                    newSocket.emit("mod_connect")
                }
                newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    logError("Connection error: " + args[0])
                    fulfillCondition()
                }

                newSocket.connect()

                asyncLock.lockInterruptibly()
                try {
                    connectCondition.await(retryTimeSeconds.toLong(), TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    logError("Interrupted while connecting [A]")
                    interrupted = true
                } finally {
                    asyncLock.unlock()
                }

                if (success) {
                    socket = newSocket
                } else {
                    newSocket.off()
                }
            } catch (e: InterruptedException) {
                logError("Interrupted while connecting [B]")
                interrupted = true
            } catch (e: Exception) {
                logError("Failed in connection: " + e.message)
            }

            if (!success) {
                socket = null
                logInfo("Retrying in ${retryTimeSeconds}s...")
                try {
                    Thread.sleep(retryTimeSeconds * 1000L)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (!running) {
            Apibalego.LOGGER.info("Stopped ClientLiveUpdatesConnection while connecting to remote")
        }
    }

    private fun sendOnSocket(message: String) {
        try {
            logInfo("Sending message on socket: $message")
            liveSocket?.send(message) ?: run {
                Apibalego.LOGGER.error("Couldn't send message $message: socket null")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendSuccess(extraItems: Map<String, Any>) {
        var msg = """{"status": "success""""
        extraItems.forEach { key, value ->
            msg += """, "$key": "$value" """
        }
        msg += "}"
        sendOnSocket(msg)
    }

    override fun sendFailure(reason: String?) {
        var msg = """{"status": "failure""""
        reason?.let {
            msg += """, "reason": "$it""""
        }
        msg += "}"
        sendOnSocket(msg)
    }

    /**
     * Wire every [ClientLiveUpdatesEventRegistry] handler onto the socket. Extracted from
     * [listen] so it can be exercised against a mocked socket in tests.
     */
    internal fun bindHandlers(socket: LiveSocket) {
        val client = this.client ?: error("Cannot bind client live update handlers without a client")
        ClientLiveUpdatesEventRegistry.all().forEach { (eventName, event) ->
            socket.on(eventName) { message ->
                try {
                    event.dispatch(message, client, this)
                } catch (e: Exception) {
                    logError("Error handling client live update '$eventName': ${e.stackTraceToString()}")
                    sendFailure(e.message)
                }
            }
        }
    }

    /**
     * RUN ONLY INSIDE SECONDARY THREAD
     *
     * The main loop, listen for triggers from the registered live update handlers.
     */
    private fun listen() {
        try {
            socket?.let { socket ->
                liveSocket = socket.asClientLiveSocket()
                bindHandlers(liveSocket!!)
            } ?: run {
                logError("Socket is null!")
            }
            while (running && socket?.isActive == true) {
                if (!running) break

                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {
            logInfo("Interrupted")
        } catch (e: Exception) {
            logError("Other error in listening: " + e.stackTraceToString())
        }

        try {
            socket?.close()
            socket = null
        } catch (e: Exception) {
            logError("Error when closing connection: " + e.stackTraceToString())
        }
    }

    fun start() {
        thread = thread(start = true, isDaemon = true, name = "ClientLiveCheckApibalego") {
            while (running) {
                connect()
                listen()
            }
            logInfo("Stopped")
        }
    }

    fun stop() {
        logInfo("Stopping thread...")
        socket?.emit("mod_disconnect")
        running = false
        try {
            thread?.interrupt() ?: Apibalego.LOGGER.error("Thread is null!")
        } catch (e: Exception) {
            Apibalego.LOGGER.error("Error in interrupting the ClientLiveUpdatesConnection thread: ${e.stackTraceToString()}")
        }
    }

    private fun logInfo(message: String) {
        Apibalego.LOGGER.info("ClientLiveUpdatesConnection | $message")
    }

    private fun logError(message: String) {
        Apibalego.LOGGER.error("ClientLiveUpdatesConnection | $message")
    }
}

private fun Socket.asClientLiveSocket(): LiveSocket = object : LiveSocket {
    override fun on(event: String, handler: (String) -> Unit) {
        this@asClientLiveSocket.on(event) { args -> handler(args.getOrNull(0)?.toString() ?: "") }
    }

    override fun send(message: String) {
        this@asClientLiveSocket.emit("mod_response", message)
    }
}
