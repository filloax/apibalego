package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.filloax.fxlib.api.entity.getPersistData
import com.filloax.fxlib.api.nbt.putIfAbsent
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.socket.ResponseSender
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object RemoteCommandExecHandler : ApiEntryHandler<RemoteCommandExecHandler.CommandDetails> {
    @Serializable
    data class CommandDetails(
        val command: String,
        val x: Int? = null,
        val y: Int? = null,
        val z: Int? = null,
    ) {
        fun pos(): Vec3? = if (x != null && y != null && z != null)
            Vec3(x + 0.5, y + 0.5, z + 0.5) else null
    }

    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<CommandDetails>>) {
        if (!checkEnabled(entries)) return

        EventUtil.runWhenServerStarted(server, true) { srv ->
            entries.forEach fe@{ entry ->
                val details = entry.details ?: run {
                    ApibalegoMod.LOGGER.error("Command entry '${entry.id}' is missing command details")
                    return@fe
                }
                val cmd = details.command.trim()
                val id = entry.id

                val savedData = ApibalegoPersistentData.get(srv)
                if (savedData.alreadyRanCommands.contains(id)) return@fe

                savedData.alreadyRanCommands.add(id)
                savedData.setDirty()

                ApibalegoMod.LOGGER.info("Executing remote command $cmd")
                performCommand(cmd, server, details.pos())
                ApibalegoMod.LOGGER.info("Executed remote command $cmd")
            }
        }
    }

    override fun handleApiUpdatePlayer(player: ServerPlayer, entries: Collection<ApiEntry<CommandDetails>>) {
        if (!checkEnabled(entries)) return

        // Run command as player, at player pos, but with server perms
        // track in player data

        val data = player.getPersistData()
        data.putIfAbsent(ApiBalegoConstants.REMOTE_COMMAND_MEMORY, CompoundTag())
        val memory = data.getCompound(ApiBalegoConstants.REMOTE_COMMAND_MEMORY).get()

        entries.forEach fe@{ entry ->
            val details = entry.details ?: run {
                ApibalegoMod.LOGGER.error("Command entry '${entry.id}' is missing command details")
                return@fe
            }
            val cmd = details.command.trim()
            if (memory.contains(entry.id)) return@fe
            memory.put(entry.id, IntTag.valueOf(1))

            ApibalegoMod.LOGGER.info("Executing remote command $cmd for player ${player.name}")
            performCommand(cmd, player.level().server, details.pos(), player)
            ApibalegoMod.LOGGER.info("Executed remote command $cmd for player ${player.name}")
        }
    }

    fun handleCommandMessage(dto: LiveCommandDto, server: MinecraftServer, responseSender: ResponseSender) {
        ApibalegoMod.LOGGER.info("LiveUpdatesConnection | Received command message ${dto.command}")

        if (!ApiBalegoConfig.remoteCommandExecution) {
            ApibalegoMod.LOGGER.warn("Received command message but remote execution disabled, ignoring! ${dto.command}")
            responseSender.sendFailure("config_disabled")
            return
        }

        val success = performCommand(dto.command, server)
        ApibalegoMod.LOGGER.info("LiveUpdatesConnection | Executed command, success: $success")
        if (success) responseSender.sendSuccess() else responseSender.sendFailure("command_exception")
    }

    private fun checkEnabled(entries: Collection<ApiEntry<CommandDetails>>): Boolean {
        if (!ApiBalegoConfig.remoteCommandExecution) {
            if (entries.isNotEmpty())
                ApibalegoMod.LOGGER.warn("Received command entries but remote execution disabled, ignoring!")
            return false
        }
        return true
    }

    private fun performCommand(command: String, server: MinecraftServer, pos: Vec3? = null, player: ServerPlayer? = null): Boolean {
        return try {
            // use server stack for perms, but set entity for pos, @p, etc
            val source = server.createCommandSourceStack().let {
                    if (player != null) it.withEntity(player)
                        .withLevel(player.level())
                        .withPosition(player.position())
                        .withRotation(player.rotationVector)
                    else it
                }.let {
                    if (pos != null) it.withPosition(pos) else it
                }
                .withSuppressedOutput()
            server.commands.performPrefixedCommand(source, command)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            ApibalegoMod.LOGGER.error("Failed remote command $command: ${e.message}")
            false
        }
    }

    @Serializable
    data class LiveCommandDto(val command: String)
}
