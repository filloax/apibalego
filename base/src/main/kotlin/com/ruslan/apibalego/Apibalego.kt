package com.ruslan.apibalego

import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.ApiEventRegistry
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.LiveUpdatesEventRegistry
import com.ruslan.apibalego.http.RemoteCommandExec
import com.ruslan.apibalego.http.handlers.ToastHandler
import com.ruslan.apibalego.network.ApiBalegoPackets
import com.ruslan.apibalego.utils.ApibalegoLogger
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager

/**
 * Provides the generic HTTP polling ([DataRemoteSync]), websocket live updates
 * ([com.ruslan.apibalego.http.LiveUpdatesConnection]), config and commands, plus
 * registries that let consumer mods plug in their own event handlers.
 */
abstract class Apibalego {
    companion object {
        const val MOD_ID = "apibalego"
        const val MOD_NAME = "Apibalego"

        @JvmField
        val LOGGER = ApibalegoLogger(LogManager.getLogger(MOD_NAME))

        var isNeoforge = false      // set to true in ApiBalegoNeo for custom logic

        /** Consumer mods add join-dispatch behavior here (called on every player join). */
        val onPlayerJoinHooks = mutableListOf<(ServerPlayer) -> Unit>()

        private var initialized = false

        /**
         * Initialize the mod.
         * Calling init() more than once (e.g. several consumer mods) is harmless.
         */
        fun init() {
            if (initialized) return
            initialized = true

            LOGGER.info("Initializing")

            // Built-in api event handlers (consumer mods add their own via ApiEventRegistry)
            ApiEventRegistry.registerHandler(ToastHandler.PREFIX, ToastHandler::handle)
            ApiEventRegistry.registerHandler(RemoteCommandExec.PREFIX, RemoteCommandExec::handleCommandExec)
            ApiEventRegistry.registerJoinHandler(ToastHandler.PREFIX, ToastHandler::handleJoin)

            // Built-in live update handlers
            LiveUpdatesEventRegistry.register(LiveUpdatesEventRegistry.RELOAD_EVENT) { _, server, sender ->
                LOGGER.info("LiveUpdates reload requested, running data sync...")
                DataRemoteSync.doSync(com.ruslan.apibalego.config.ApiBalegoConfig.dataSyncUrl, server).thenAccept { success ->
                    if (success) sender.sendSuccess() else sender.sendFailure()
                }
            }
            LiveUpdatesEventRegistry.register(ToastHandler.PREFIX, ToastHandler::handleLiveUpdate)
            LiveUpdatesEventRegistry.register(RemoteCommandExec.PREFIX, RemoteCommandExec::handleCommandMessage)

            ApiBalegoPackets.registerPacketsS2C()
            ApiBalegoPackets.registerPacketsC2S()

            ApiBalegoConfigHandler.initConfig()

            LOGGER.info("Initialized!")
        }
    }
}
