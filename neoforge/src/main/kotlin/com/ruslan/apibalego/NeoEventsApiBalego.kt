package com.ruslan.apibalego

import com.filloax.fxlib.platform.ServerEvent
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.CommandSelection
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS

class NeoEventsApiBalego : ApiBalegoModEvents() {
    override fun onServerStarting(event: ServerEvent) {
        FORGE_BUS.addListener<ServerStartingEvent> { event(it.server) }
    }

    override fun onServerStopping(event: ServerEvent) {
        FORGE_BUS.addListener<ServerStoppingEvent> { event(it.server) }
    }

    override fun onServerLevelLoad(event: (MinecraftServer, ServerLevel) -> Unit) {
        FORGE_BUS.addListener<LevelEvent.Load> { ev ->
            if (!ev.level.isClientSide && ev.level is ServerLevel) {
                val level = ev.level as ServerLevel
                event(level.server, level)
            }
        }
    }

    override fun onStartServerTick(event: ServerEvent) {
        FORGE_BUS.addListener<ServerTickEvent.Pre> { event(it.server) }
    }

    override fun onPlayerServerJoin(event: (ServerPlayer) -> Unit) {
        FORGE_BUS.addListener<PlayerLoggedInEvent> { ev ->
            val player = ev.entity
            if (player is ServerPlayer) {
                event(player)
            }
        }
    }

    override fun onRegisterCommands(event: (CommandDispatcher<CommandSourceStack>, CommandBuildContext, CommandSelection) -> Unit) {
        FORGE_BUS.addListener<RegisterCommandsEvent> { ev ->
            event(ev.dispatcher, ev.buildContext, ev.commandSelection)
        }
    }
}
