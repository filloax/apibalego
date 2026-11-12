package com.ruslan.apibalego.http

import com.ruslan.apibalego.handlers.RemoteCommandExecHandler
import com.ruslan.apibalego.handlers.RemoteDatapackHandler
import com.ruslan.apibalego.handlers.RemoteStructuresHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.trigger.TriggerHandler
import com.ruslan.apibalego.trigger.TriggerRaw
import com.ruslan.apibalego.utils.id
import net.minecraft.server.MinecraftServer

val ID_API_HANDLER_TOAST = id("toast")
val ID_API_HANDLER_COMMAND = id("command")
val ID_API_HANDLER_STRUCTURE = id("structure")
val ID_API_HANDLER_DATAPACK = id("datapack")
val ID_API_HANDLER_TRIGGER = id("trigger")

object BuiltinApiHandlers {
    fun registerAll() {
        ApiEntryRegistry.register(
            ID_API_HANDLER_TOAST,
            ToastHandler.ToastData.serializer(),
            ToastHandler,
            false,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_COMMAND,
            RemoteCommandExecHandler.CommandDetails.serializer(),
            RemoteCommandExecHandler,
            false,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_STRUCTURE,
            RemoteStructuresHandler.RemoteStructureSpawnData.serializer(),
            RemoteStructuresHandler,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_DATAPACK,
            RemoteDatapackHandler.DatapackDetails.serializer(),
            RemoteDatapackHandler,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_TRIGGER,
            TriggerRaw.serializer(),
            object : ApiEntryHandler<TriggerRaw> {
                override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<TriggerRaw>>) { }
            },
        )
    }
}