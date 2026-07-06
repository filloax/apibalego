package com.ruslan.apibalego.socket

import com.ruslan.apibalego.handlers.RemoteCommandExecHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.DataRemoteSync
import com.ruslan.apibalego.Apibalego

const val LIVE_EVENT_RELOAD = "reload"
const val LIVE_EVENT_TOAST = "toast"
const val LIVE_EVENT_CMD = "cmd"

object BuiltinLiveUpdateEvents {
    fun registerAll() {
        LiveUpdatesEventRegistry.register(LIVE_EVENT_RELOAD) { server, sender ->
            Apibalego.LOGGER.info("LiveUpdates reload requested, running data sync...")
            DataRemoteSync.doSync(server).thenAccept { success ->
                if (success) sender.sendSuccess() else sender.sendFailure()
            }
        }
        LiveUpdatesEventRegistry.register(LIVE_EVENT_TOAST, ToastHandler.ToastData.serializer(), ToastHandler::handleLiveUpdate)
        LiveUpdatesEventRegistry.register(LIVE_EVENT_CMD, RemoteCommandExecHandler.LiveCommandDto.serializer(), RemoteCommandExecHandler::handleCommandMessage)
    }
}
