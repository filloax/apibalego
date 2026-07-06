package com.ruslan.apibalego

import com.filloax.fxlib.api.platform.ServiceUtil
import com.filloax.fxlib.platform.ServerEvent
import com.mojang.brigadier.CommandDispatcher
import com.ruslan.apibalego.commands.GamemasterCommand
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.socket.LiveUpdatesConnection
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.CommandSelection
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

abstract class ApiBalegoModEvents {
    companion object {
        fun get(): ApiBalegoModEvents = ServiceUtil.findService(ApiBalegoModEvents::class.java)
    }

    fun initCallbacks() {
        onServerStarting { server ->
            DataRemoteSync.Callbacks.handleServerAboutToStartEvent(server)
            DataRemoteSync.doSync(server)
            LiveUpdatesConnection.serverStart(server)
        }
        onServerStopping { server ->
            DataRemoteSync.Callbacks.handleServerStoppingEvent()
            LiveUpdatesConnection.serverStop(server)
            GamemasterApi.Callbacks.onServerStop()
        }
        onServerLevelLoad { server, level ->
            DataRemoteSync.Callbacks.onServerLevel(server, level)
        }
        onStartServerTick { server ->
            DataRemoteSync.Callbacks.onServerTick(server)
        }
        onPlayerServerJoin { player ->
            GamemasterApi.Callbacks.onPlayerJoin(player)
        }
        onRegisterCommands { dispatcher, ctx, selection ->
            GamemasterCommand.register(dispatcher, ctx, selection)
        }
    }

    abstract fun onServerStarting(event: ServerEvent)
    abstract fun onServerStopping(event: ServerEvent)
    abstract fun onServerLevelLoad(event: (MinecraftServer, ServerLevel) -> Unit)
    abstract fun onStartServerTick(event: ServerEvent)
    abstract fun onPlayerServerJoin(event: (player: ServerPlayer) -> Unit)
    abstract fun onRegisterCommands(event: (dispatcher: CommandDispatcher<CommandSourceStack>, ctx: CommandBuildContext, selection: CommandSelection) -> Unit)
}
