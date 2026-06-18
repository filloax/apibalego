package com.ruslan.apibalego.network

import com.filloax.fxlib.api.FxLibServices
import com.filloax.fxlib.api.optional
import com.filloax.fxlib.api.networking.playS2C
import com.ruslan.apibalego.utils.resLoc
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrNull

typealias RStreamCodec<T> = StreamCodec<RegistryFriendlyByteBuf, T>

object ApiBalegoPackets {
    object Types {
        val CUSTOM_TOAST = resLoc("custom_toast")
    }

    val CUSTOM_TOAST = CustomToastPacket.ENTRY

    fun registerPacketsS2C() {
        FxLibServices.networking.packetRegistrator.apply {
            playS2C(CUSTOM_TOAST, ClientPacketHandlers::handleCustomToast)
        }
    }

    fun registerPacketsC2S() {
    }
}

open class CustomToastPacket(
    val title: Component,
    val message: Component? = null,
    val item: ItemStack = ItemStack.EMPTY,
) : CustomPacketPayload {
    companion object {
        val CODEC: RStreamCodec<CustomToastPacket> = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, CustomToastPacket::title,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, CustomToastPacket::message.optional(),
            ItemStack.OPTIONAL_STREAM_CODEC, CustomToastPacket::item,
        ) { title, message, item -> CustomToastPacket(title, message.getOrNull(), item) }

        val TYPE = CustomPacketPayload.Type<CustomToastPacket>(ApiBalegoPackets.Types.CUSTOM_TOAST)
        val ENTRY = TypeAndCodec(TYPE, CODEC)
    }

    override fun type() = TYPE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CustomToastPacket

        if (title != other.title) return false
        return message == other.message
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CustomToastPacket(title=$title, message=$message, item=$item)"
    }
}
