package com.ruslan.apibalego.network

import com.filloax.fxlib.api.networking.ToClientContext
import com.ruslan.apibalego.client.gui.components.addCustomToast

object ClientPacketHandlers {
    fun handleCustomToast(packet: CustomToastPacket, context: ToClientContext) {
        context.client.gui.toastManager().addCustomToast(packet.title, packet.message, packet.item)
    }
}
