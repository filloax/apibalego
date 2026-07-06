package com.ruslan.apibalego

import com.ruslan.apibalego.client.http.BuiltinClientApiHandlers
import com.ruslan.apibalego.client.http.ClientDataSync
import com.ruslan.apibalego.client.http.ClientGamemasterApi
import com.ruslan.apibalego.client.socket.BuiltinClientLiveUpdateEvents
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesConnection
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.BuiltinApiHandlers
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.network.ApiBalegoPackets
import com.ruslan.apibalego.socket.BuiltinLiveUpdateEvents
import com.ruslan.apibalego.utils.ApibalegoLogger
import org.apache.logging.log4j.LogManager

/**
 * Provides the generic HTTP polling ([DataRemoteSync]), websocket live updates
 * ([com.ruslan.apibalego.socket.LiveUpdatesConnection]), config and commands, plus
 * registries that let consumer mods plug in their own event handlers.
 */
abstract class Apibalego {
    companion object {
        const val MOD_ID = "apibalego"
        const val MOD_NAME = "Apibalego"

        @JvmField
        val LOGGER = ApibalegoLogger(LogManager.getLogger(MOD_NAME))

        var isNeoforge = false      // set to true in ApiBalegoNeo for custom logic

        private var initialized = false
        private var clientInitialized = false

        /**
         * Initialize the mod.
         * Calling init() more than once (e.g. several consumer mods) is harmless.
         */
        fun init() {
            if (initialized) return
            initialized = true

            LOGGER.info("Initializing")

            ApiBalegoConfigHandler.initConfig()

            BuiltinApiHandlers.registerAll()
            BuiltinLiveUpdateEvents.registerAll()

            GamemasterApi.init()

            ApiBalegoPackets.registerPacketsS2C()
            ApiBalegoPackets.registerPacketsC2S()

            ApiBalegoModEvents.get().initCallbacks()

            LOGGER.info("Initialized!")
        }

        /**
         * Initialize the client-only parts of the mod. Call from each loader's client entrypoint,
         * after [init]. Calling more than once is harmless.
         */
        fun initClient() {
            if (clientInitialized) return
            clientInitialized = true

            BuiltinClientApiHandlers.registerAll()
            BuiltinClientLiveUpdateEvents.registerAll()
            ClientGamemasterApi.init()
            ClientDataSync.start()
            ClientLiveUpdatesConnection.clientStart()

            LOGGER.info("Initialized client!")
        }
    }
}
