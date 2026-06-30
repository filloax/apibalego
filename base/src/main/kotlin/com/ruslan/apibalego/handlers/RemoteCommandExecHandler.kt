package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.socket.ResponseSender
import com.ruslan.apibalego.utils.id
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3

object RemoteCommandExecHandler : ApiEntryHandler<RemoteCommandExecHandler.CommandDetails> {
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

    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<CommandDetails>>) {
        if (!ApiBalegoConfig.remoteCommandExecution) {
            if (entries.isNotEmpty())
                Apibalego.LOGGER.warn("Received command entries but remote execution disabled, ignoring!")
            return
        }

        EventUtil.runWhenServerStarted(server, true) { srv ->
            entries.forEach fe@{ entry ->
                val details = entry.details ?: run {
                    Apibalego.LOGGER.error("Command entry '${entry.id}' is missing command details")
                    return@fe
                }
                val cmd = details.command.trim()
                val id = entry.id

                val savedData = ApibalegoPersistentData.get(srv)
                if (savedData.alreadyRanCommands.contains(id)) return@runWhenServerStarted

                savedData.alreadyRanCommands.add(id)
                savedData.setDirty()

                Apibalego.LOGGER.info("Executing remote command $cmd")
                performCommand(cmd, server, details.pos())
                Apibalego.LOGGER.info("Executed remote command $cmd")
            }
        }
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
