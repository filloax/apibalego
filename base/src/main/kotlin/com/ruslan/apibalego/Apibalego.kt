package com.ruslan.apibalego

import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.handlers.RemoteCommandExec
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.BuiltinApiHandlers
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.network.ApiBalegoPackets
import com.ruslan.apibalego.socket.LiveUpdatesEventRegistry
import com.ruslan.apibalego.utils.ApibalegoLogger
import net.minecraft.server.level.ServerPlayer
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

        /**
         * Initialize the mod.
         * Calling init() more than once (e.g. several consumer mods) is harmless.
         */
        fun init() {
            if (initialized) return
            initialized = true

            LOGGER.info("Initializing")

            BuiltinApiHandlers.registerAll()

            // Built-in live update handlers
            LiveUpdatesEventRegistry.register(LiveUpdatesEventRegistry.RELOAD_EVENT) { _, server, sender ->
                LOGGER.info("LiveUpdates reload requested, running data sync...")
                DataRemoteSync.doSync(ApiBalegoConfig.dataSyncUrl, server).thenAccept { success ->
                    if (success) sender.sendSuccess() else sender.sendFailure()
                }
            }
            LiveUpdatesEventRegistry.register(ToastHandler.PREFIX, ToastHandler::handleLiveUpdate)
            LiveUpdatesEventRegistry.register(RemoteCommandExec.PREFIX, RemoteCommandExec::handleCommandMessage)

            GamemasterApi.init()

            ApiBalegoPackets.registerPacketsS2C()
            ApiBalegoPackets.registerPacketsC2S()

            ApiBalegoConfigHandler.initConfig()

            ApiBalegoModEvents.get().initCallbacks()

            LOGGER.info("Initialized!")
        }
    }
}
