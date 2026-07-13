package com.ruslan.apibalego.client.socket

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.client.handlers.ClientToastHandler
import com.ruslan.apibalego.client.http.ClientDataSync
import com.ruslan.apibalego.handlers.ToastHandler

// Note that this triggers resource reload
const val CLIENT_LIVE_EVENT_RELOAD = "client_reload"
const val CLIENT_LIVE_EVENT_TOAST = "client_toast"

object BuiltinClientLiveUpdateEvents {
    fun registerAll() {
        ClientLiveUpdatesEventRegistry.register(CLIENT_LIVE_EVENT_RELOAD) { _, sender ->
            ApibalegoMod.LOGGER.info("Client LiveUpdates reload requested, running client data sync...")
            ClientDataSync.sync()
            sender.sendSuccess()
        }
        ClientLiveUpdatesEventRegistry.register(CLIENT_LIVE_EVENT_TOAST, ToastHandler.ToastData.serializer(), ClientToastHandler::handleLiveUpdate)
    }
}
