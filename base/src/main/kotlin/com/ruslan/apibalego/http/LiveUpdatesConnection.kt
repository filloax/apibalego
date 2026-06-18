package com.ruslan.apibalego.http

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * Connect to an endpoint to be able to remotely trigger updates and run commands past the
 * usual polling interval. Listens for the event types registered in [LiveUpdatesEventRegistry].
 * Class lifetime should be same as the server, just in case.
 */
class LiveUpdatesConnection private constructor(val server: MinecraftServer) : ResponseSender {
    private var running = true
    private var socket: Socket? = null
    private var thread: Thread? = null
    // Socketio is non blocking, since this was initially
    // made for blocking sockets simulate that behavior
    private var asyncLock: Lock = ReentrantLock()

    companion object {
        var retryTimeSeconds = 60
        val charset = Charsets.UTF_8
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private var activeConnection: LiveUpdatesConnection? = null

        fun serverStart(server: MinecraftServer) {
            // todo: fix Neoforge compatibility
            if (Apibalego.isNeoforge) {
                Apibalego.LOGGER.warn("LiveUpdatesConnection is currently not supported on Neoforge, will not start!")
                return
            }
            if (activeConnection == null && ApiBalegoConfig.liveUpdateService) {
                val conn = LiveUpdatesConnection(server)
                conn.start()
                activeConnection = conn
            }
            else if (!ApiBalegoConfig.liveUpdateService)
                Apibalego.LOGGER.info("LiveUpdatesConnection was disabled from mod settings, will not start")
        }

        fun serverStop(server: MinecraftServer) {
            if (ApiBalegoConfig.liveUpdateService) {
                activeConnection?.stop()
                activeConnection = null
            }
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
            val host = ApiBalegoConfig.liveUpdateUrl
            val port = ApiBalegoConfig.liveUpdatePort
            val uri = if (port > 0) URI("$host:$port") else URI(host)
            var success = false

            val connectCondition = asyncLock.newCondition()
            fun fulfillCondition() {
                asyncLock.lock();
                try {
                    connectCondition.signal();
                } finally {
                    asyncLock.unlock();
                }
            }

            try {
                logInfo("Attempting connection to $uri...")

                val options = IO.Options.builder()
                    .setExtraHeaders(mapOf("apiKey" to listOf(ApiBalegoConfig.dataSyncApiKey)))
                    .build()
                val newSocket = IO.socket(uri, options)

                newSocket.on(Socket.EVENT_CONNECT) {
                    logInfo("Connected to $uri")
                    success = true
                    fulfillCondition()
                    newSocket.emit("mod_connect")
                }
                // Set up an error listener
                newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    logError("Connection error: " + args[0])
                    fulfillCondition()
                }

                newSocket.connect()

                asyncLock.lockInterruptibly();
                try {
                    connectCondition.await(retryTimeSeconds.toLong(), TimeUnit.SECONDS);
                } catch (e: InterruptedException) {
                    logError("Interrupted while connecting [A]")
                    interrupted = true
                } finally {
                    asyncLock.unlock();
                }

                if (success) {
                    socket = newSocket
                }
                else {
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
            Apibalego.LOGGER.info("Stopped LiveUpdatesConnection while connecting to remote")
        }
    }

    private fun sendOnSocket(message: String) {
        try {
            logInfo("Sending message on socket: $message")
            socket?.emit("mod_response", message) ?: run {
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
     * RUN ONLY INSIDE SECONDARY THREAD
     *
     * The main loop, listen for triggers from the registered live update handlers.
     */
    private fun listen() {
        try {
            socket?.let { socket ->
                LiveUpdatesEventRegistry.all().forEach { (eventType, handler) ->
                    socket.on(eventType) { args ->
                        val message = args.getOrNull(0)?.toString() ?: ""
                        try {
                            handler.handle(message, server, this)
                        } catch (e: Exception) {
                            logError("Error handling live update '$eventType': ${e.stackTraceToString()}")
                            sendFailure(e.message)
                        }
                    }
                }
            } ?: run {
                logError("Socket is null!")
            }
            while (running && socket?.isActive == true) {
                if (!running) break // in case not running but socket returned

                // Probably not best way? When using normal sockets the main logic was here,
                // but since socketio is async just do this I guess
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
        thread = thread(start=true, name="LiveCheckApibalego") {
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
        // Interrupt thread if sleeping
        running = false
        try {
            thread?.interrupt() ?: Apibalego.LOGGER.error("Thread is null!")
        } catch (e: Exception) {
            Apibalego.LOGGER.error("Error in interrupting the LiveUpdatesConnection thread: ${e.stackTraceToString()}")
        }
    }

    private fun logInfo(message: String) {
        Apibalego.LOGGER.info("LiveUpdatesConnection | $message")
    }

    private fun logError(message: String) {
        Apibalego.LOGGER.error("LiveUpdatesConnection | $message")
    }
}
