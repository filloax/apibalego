package com.ruslan.apibalego

import com.filloax.fxlib.platform.ServerEvent
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.CommandSelection
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

class FabricEventsApiBalego : ApiBalegoModEvents() {
    override fun onServerStarting(event: ServerEvent) = ServerLifecycleEvents.SERVER_STARTING.register(event)

    override fun onServerStopping(event: ServerEvent) = ServerLifecycleEvents.SERVER_STOPPING.register(event)

    override fun onServerLevelLoad(event: (MinecraftServer, ServerLevel) -> Unit) = ServerLevelEvents.LOAD.register(event)

    override fun onStartServerTick(event: ServerEvent) = ServerTickEvents.START_SERVER_TICK.register(event)

    override fun onPlayerServerJoin(event: (ServerPlayer) -> Unit) = ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        event(handler.player)
    }

    override fun onRegisterCommands(event: (CommandDispatcher<CommandSourceStack>, CommandBuildContext, CommandSelection) -> Unit) = CommandRegistrationCallback.EVENT.register { dispatcher, ctx, selection ->
        event(dispatcher, ctx, selection)
    }
}
