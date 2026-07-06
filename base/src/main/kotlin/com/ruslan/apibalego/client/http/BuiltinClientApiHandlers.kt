package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.client.handlers.ClientResourcePackHandler
import com.ruslan.apibalego.client.handlers.ClientToastHandler
import com.ruslan.apibalego.client.handlers.MainMenuMessageHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.utils.id

val ID_CLIENT_API_HANDLER_TOAST = id("toast")
val ID_CLIENT_API_HANDLER_RESOURCEPACK = id("resourcepack")
val ID_CLIENT_API_HANDLER_MENU_MESSAGE = id("menu_message")

object BuiltinClientApiHandlers {
    fun registerAll() {
        ClientApiEntryRegistry.register(
            ID_CLIENT_API_HANDLER_TOAST,
            ToastHandler.ToastData.serializer(),
            ClientToastHandler,
        )
        ClientApiEntryRegistry.register(
            ID_CLIENT_API_HANDLER_RESOURCEPACK,
            ClientResourcePackHandler.PackDetails.serializer(),
            ClientResourcePackHandler,
        )
        ClientApiEntryRegistry.register(
            ID_CLIENT_API_HANDLER_MENU_MESSAGE,
            MainMenuMessageHandler.MenuMessages.serializer(),
            MainMenuMessageHandler,
        )
    }
}
