package com.ruslan.apibalego.client.handlers

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.client.data.ApibalegoClientData
import com.ruslan.apibalego.client.gui.components.addCustomToast
import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.socket.ResponseSender
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Client-side toast handler: same data as the server-side [ToastHandler],
 * skips packets and shows directly
 */
object ClientToastHandler : ClientApiEntryHandler<ToastHandler.ToastData> {
    override fun handleApiUpdate(client: Minecraft, entries: Collection<ClientApiEntry<ToastHandler.ToastData>>) {
        var changed = false
        entries.filter { it.active }.forEach { entry ->
            val toast = entry.details!!
            // same key scheme as the server-side per-player toast memory
            val memoryId = toast.title.string + entry.id
            if (ApibalegoClientData.shownToasts(client).add(memoryId)) {
                changed = true
                client.execute {
                    client.gui.toastManager().addCustomToast(toast.title, toast.message, toast.item?.safeDefaultInstance())
                }
            }
        }
        if (changed) ApibalegoClientData.save(client)
    }

    // live

    fun handleLiveUpdate(data: ToastHandler.ToastData, client: Minecraft, sender: ResponseSender) {
        ApibalegoMod.LOGGER.info("ClientLiveUpdatesConnection | Received toast")
        client.execute {
            client.gui.toastManager().addCustomToast(data.title, data.message, data.item?.safeDefaultInstance())
        }
        sender.sendSuccess()
    }

    /**
     * Item registry holders can throw "Components not bound yet" if resolved before the item's
     * data components finish binding (seen at main-menu toasts, before the registry is fully
     * settled), if so fall back to no item
     */
    private fun Item.safeDefaultInstance(): ItemStack? = try {
        defaultInstance
    } catch (e: Exception) {
        ApibalegoMod.LOGGER.error("Failed to build ItemStack for toast item '$this': ${e.message}")
        null
    }
}
