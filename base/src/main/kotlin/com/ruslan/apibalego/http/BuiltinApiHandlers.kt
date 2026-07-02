package com.ruslan.apibalego.http

import com.ruslan.apibalego.handlers.RemoteCommandExecHandler
import com.ruslan.apibalego.handlers.RemoteDatapackHandler
import com.ruslan.apibalego.handlers.RemoteStructuresHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.utils.id

val ID_API_HANDLER_TOAST = id("toast")
val ID_API_HANDLER_COMMAND = id("command")
val ID_API_HANDLER_STRUCTURE = id("structure")
val ID_API_HANDLER_DATAPACK = id("datapack")

object BuiltinApiHandlers {
    fun registerAll() {
        ApiEntryRegistry.register(
            ID_API_HANDLER_TOAST,
            ToastHandler.ToastData.serializer(),
            ToastHandler,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_COMMAND,
            RemoteCommandExecHandler.CommandDetails.serializer(),
            RemoteCommandExecHandler,
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
    }
}