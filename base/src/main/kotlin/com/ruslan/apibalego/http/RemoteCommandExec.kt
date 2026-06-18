package com.ruslan.apibalego.http

import com.filloax.fxlib.api.EventUtil
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.util.StringUtil
import net.minecraft.world.phys.Vec3

object RemoteCommandExec {
    const val PREFIX = "cmd"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Handler for "cmd/<id>" gamemaster events polled via [DataRemoteSync]. */
    fun handleCommandExec(event: GenericApiEvent, server: MinecraftServer) {
        if (!ApiBalegoConfig.remoteCommandExecution) {
            Apibalego.LOGGER.warn("Received command event but disabled in config, ignoring! $event")
            return
        }

        val id = event.name.replace("$PREFIX/", "")
        val cmd = event.desc?.trim()
        val pos = event.pos

        if (StringUtil.isNullOrEmpty(id)) {
            Apibalego.LOGGER.error("Command exec must have an id after 'cmd/'! $event")
            return
        }
        if (StringUtil.isNullOrEmpty(cmd)) {
            Apibalego.LOGGER.error("Command exec must have a desc with the command! $event")
            return
        }

        EventUtil.runWhenServerStarted(server, true) { srv ->
            val savedData = ApibalegoPersistentData.get(srv)
            if (savedData.alreadyRan.contains(id)) {
                return@runWhenServerStarted
            }

            savedData.alreadyRan.add(id)
            savedData.setDirty()

            Apibalego.LOGGER.info("Executing remote command $cmd")
            performCommand(cmd!!, server, pos?.let{ Vec3(it.x+0.5, it.y+0.5, it.z+0.5) })
            Apibalego.LOGGER.info("Executed remote command $cmd")
        }
    }

    /** Handler for the "cmd" websocket live-update event. Matches [LiveUpdatesEventHandler]. */
    fun handleCommandMessage(message: String, server: MinecraftServer, responseSender: ResponseSender) {
        Apibalego.LOGGER.info("LiveUpdatesConnection | Received command message $message")

        if (!ApiBalegoConfig.remoteCommandExecution) {
            Apibalego.LOGGER.warn("Received command message but disabled in config, ignoring! $message")
            responseSender.sendFailure("config_disabled")
            return
        }

        val remoteCommandDto: RemoteCommandDto = try {
            json.decodeFromString(RemoteCommandDto.serializer(), message)
        } catch (e: Exception) {
            Apibalego.LOGGER.error("LiveUpdatesConnection | Wrong command format: ${e.message}")
            e.printStackTrace()
            responseSender.sendFailure(e.message)
            return
        }

        val success = performCommand(remoteCommandDto.command, server)

        Apibalego.LOGGER.info("LiveUpdatesConnection | Executed command, success: $success")
        if (success) {
            responseSender.sendSuccess()
        } else {
            responseSender.sendFailure("command_exception")
        }
    }

    private fun performCommand(command: String, server: MinecraftServer, pos: Vec3? = null): Boolean {
        return try {
            val commandSourceStack = server.createCommandSourceStack().let {
                if (pos != null)
                    it.withPosition(pos)
                else
                    it
            }
            server.commands.performPrefixedCommand(commandSourceStack, command)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            Apibalego.LOGGER.error("Failed remote command $command: ${e.message}")
            false
        }
    }

    @Serializable
    private data class RemoteCommandDto (
        val command: String,
    )
}
