package com.ruslan.apibalego

import com.ruslan.apibalego.commands.GamemasterCommand
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.LiveUpdatesConnection
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object ApiBalegoFabric : ModInitializer {
    override fun onInitialize() {
        Apibalego.init()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            DataRemoteSync.Callbacks.handleServerAboutToStartEvent(server)
            DataRemoteSync.doSync(ApiBalegoConfig.dataSyncUrl, server)
            LiveUpdatesConnection.serverStart(server)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            DataRemoteSync.Callbacks.handleServerStoppingEvent()
            LiveUpdatesConnection.serverStop(server)
        }
        ServerLevelEvents.LOAD.register { server, level ->
            DataRemoteSync.Callbacks.onServerLevel(server, level)
        }
        ServerTickEvents.START_SERVER_TICK.register { server ->
            DataRemoteSync.Callbacks.onServerTick(ApiBalegoConfig.dataSyncUrl, server)
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            DataRemoteSync.Callbacks.onPlayerJoin(handler.player)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            GamemasterCommand.register(dispatcher, registryAccess, environment)
        }

        Apibalego.LOGGER.info("Initialized Fabric entry point")
    }
}
