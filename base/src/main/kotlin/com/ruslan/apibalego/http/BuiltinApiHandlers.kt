package com.ruslan.apibalego.http

import com.ruslan.apibalego.handlers.RemoteCommandExec
import com.ruslan.apibalego.handlers.ID_API_HANDLER_COMMAND
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.utils.id

val ID_API_HANDLER_TOAST = id("toast")

object BuiltinApiHandlers {
    fun registerAll() {
        ApiEntryRegistry.register(
            ID_API_HANDLER_TOAST,
            ToastHandler.ToastData.serializer(),
            ToastHandler::handleApiUpdate,
            ToastHandler::handleApiJoin,
        )
        ApiEntryRegistry.register(
            ID_API_HANDLER_COMMAND,
            RemoteCommandExec.CommandDetails.serializer(),
            RemoteCommandExec::handleApiUpdate,
            RemoteCommandExec::handleApiJoin,
        )
    }
}