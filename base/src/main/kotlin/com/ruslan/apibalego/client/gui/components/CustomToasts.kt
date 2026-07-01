package com.ruslan.apibalego.client.gui.components

import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

fun ToastManager.addCustomToast(title: Component, message: Component? = null, item: ItemStack? = null) {
    if (item != null && item != ItemStack.EMPTY) {
        addToast(CustomTextItemToast.multiline(minecraft.font, title, item, message))
    } else {
        addToast(CustomTextToast.multiline(minecraft.font, title, message))
    }
}
