package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.socket.ResponseSender
import com.ruslan.apibalego.utils.id
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

val ID_API_HANDLER_COMMAND = id("command")

object RemoteCommandExec {
    const val PREFIX = "cmd"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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

    fun handleApiUpdate(server: MinecraftServer, entry: ApiEntry<CommandDetails>) {
        if (!ApiBalegoConfig.remoteCommandExecution) {
            Apibalego.LOGGER.warn("Received command entry but remote execution disabled, ignoring! ${entry.id}")
            return
        }

        val details = entry.details ?: run {
            Apibalego.LOGGER.error("Command entry '${entry.id}' is missing command details")
            return
        }
        val cmd = details.command.trim()
        val id = entry.id

        EventUtil.runWhenServerStarted(server, true) { srv ->
            val savedData = ApibalegoPersistentData.get(srv)
            if (savedData.alreadyRanCommands.contains(id)) return@runWhenServerStarted

            savedData.alreadyRanCommands.add(id)
            savedData.setDirty()

            Apibalego.LOGGER.info("Executing remote command $cmd")
            performCommand(cmd, server, details.pos())
            Apibalego.LOGGER.info("Executed remote command $cmd")
        }
    }

    fun handleApiJoin(player: ServerPlayer, entry: ApiEntry<CommandDetails>) {
        // Commands are server-wide operations; no per-player action on join
    }

    fun handleCommandMessage(message: String, server: MinecraftServer, responseSender: ResponseSender) {
        Apibalego.LOGGER.info("LiveUpdatesConnection | Received command message $message")

        if (!ApiBalegoConfig.remoteCommandExecution) {
            Apibalego.LOGGER.warn("Received command message but remote execution disabled, ignoring! $message")
            responseSender.sendFailure("config_disabled")
            return
        }

        val dto: LiveCommandDto = try {
            json.decodeFromString(LiveCommandDto.serializer(), message)
        } catch (e: Exception) {
            Apibalego.LOGGER.error("LiveUpdatesConnection | Wrong command format: ${e.message}")
            e.printStackTrace()
            responseSender.sendFailure(e.message)
            return
        }

        val success = performCommand(dto.command, server)
        Apibalego.LOGGER.info("LiveUpdatesConnection | Executed command, success: $success")
        if (success) responseSender.sendSuccess() else responseSender.sendFailure("command_exception")
    }

    private fun performCommand(command: String, server: MinecraftServer, pos: Vec3? = null): Boolean {
        return try {
            val source = server.createCommandSourceStack().let {
                if (pos != null) it.withPosition(pos) else it
            }
            server.commands.performPrefixedCommand(source, command)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            Apibalego.LOGGER.error("Failed remote command $command: ${e.message}")
            false
        }
    }

    @Serializable
    private data class LiveCommandDto(val command: String)
}
