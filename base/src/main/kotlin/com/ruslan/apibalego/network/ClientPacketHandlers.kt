package com.ruslan.apibalego.network

import com.filloax.fxlib.api.networking.ToClientContext
import com.ruslan.apibalego.client.gui.components.updateCustomToast

object ClientPacketHandlers {
    fun handleCustomToast(packet: CustomToastPacket, context: ToClientContext) {
        context.client.toastManager.updateCustomToast(packet.title, packet.message, packet.item)
    }
}
