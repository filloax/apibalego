package com.ruslan.apibalego

import com.ruslan.apibalego.commands.GamemasterCommand
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.LiveUpdatesConnection
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS

@Mod(Apibalego.MOD_ID)
object ApiBalegoNeo {
    init {
        Apibalego.isNeoforge = true
        Apibalego.init()

        FORGE_BUS.addListener<ServerStartingEvent> { ev ->
            val server = ev.server
            DataRemoteSync.Callbacks.handleServerAboutToStartEvent(server)
            DataRemoteSync.doSync(ApiBalegoConfig.dataSyncUrl, server)
            LiveUpdatesConnection.serverStart(server)
        }
        FORGE_BUS.addListener<ServerStoppingEvent> { ev ->
            DataRemoteSync.Callbacks.handleServerStoppingEvent()
            LiveUpdatesConnection.serverStop(ev.server)
        }
        FORGE_BUS.addListener<LevelEvent.Load> { ev ->
            if (!ev.level.isClientSide && ev.level is ServerLevel) {
                val level = ev.level as ServerLevel
                DataRemoteSync.Callbacks.onServerLevel(level.server, level)
            }
        }
        FORGE_BUS.addListener<ServerTickEvent.Pre> { ev ->
            DataRemoteSync.Callbacks.onServerTick(ApiBalegoConfig.dataSyncUrl, ev.server)
        }
        FORGE_BUS.addListener<RegisterCommandsEvent> { ev ->
            GamemasterCommand.register(ev.dispatcher, ev.buildContext, ev.commandSelection)
        }

        Apibalego.LOGGER.info("Initialized NeoForge entry point")
    }
}
