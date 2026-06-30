package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.ScheduledServerTask
import com.filloax.fxlib.api.entity.getPersistData
import com.filloax.fxlib.api.json.ItemByNameSerializer
import com.filloax.fxlib.api.json.SimpleComponentSerializer
import com.filloax.fxlib.api.nbt.putIfAbsent
import com.filloax.fxlib.api.networking.sendPacket
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.socket.ResponseSender
import com.ruslan.apibalego.network.CustomToastPacket
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.network.PacketSendListener
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack


/**
 * Built-in toast handler. Reacts to "toast/..." gamemaster events (polled and on player join)
 * and to "toast" websocket live-update messages.
 */
object ToastHandler : ApiEntryHandler<ToastHandler.ToastData> {
    @Serializable
    data class ToastData(
        @Serializable(with = SimpleComponentSerializer::class)
        val title: Component,
        @Serializable(with = SimpleComponentSerializer::class)
        val message: Component? = null,
        @Serializable(with = ItemByNameSerializer::class)
        val item: Item? = null,
    ) {
        fun toPacket() = CustomToastPacket(
            title,
            message,
            item?.defaultInstance ?: ItemStack.EMPTY
        )
    }

    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<ToastData>>) {
        // Avoid doing too soon on init
        val tickCount = server.tickCount
        val minStartTime = 40
        entries.forEach { entry ->
            val delay = (minStartTime - tickCount).coerceAtLeast(0)
            if (delay > 0) {
                ScheduledServerTask.schedule(server, delay) {
                    sendAllCustomToastEvent(entry, server)
                }
            } else {
                sendAllCustomToastEvent(entry, server)
            }
        }
    }

    override fun handleApiJoin(player: ServerPlayer, entries: Collection<ApiEntry<ToastData>>) {
        entries.forEach { entry ->
            checkAndSendCustomToastEvent(player, entry.details!!.toPacket(), entry.id)
        }
    }

    private fun sendAllCustomToastEvent(entry: ApiEntry<ToastData>, server: MinecraftServer) {
        val toast = entry.details!!
        val packet = toast.toPacket()
        server.playerList.players.forEach { player -> checkAndSendCustomToastEvent(player, packet, entry.id) }
    }

    private fun checkAndSendCustomToastEvent(player: ServerPlayer, packet: CustomToastPacket, seqId: String) {
        val data = player.getPersistData()
        data.putIfAbsent(ApiBalegoConstants.CUSTOM_TOAST_MEMORY, CompoundTag())
        val memory = data.getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).get()
        val id = packet.title.string + seqId

        if (!memory.contains(id)) {
            player.sendPacket(packet)
            memory.put(id, IntTag.valueOf(1))
        }
    }

    // live

    fun handleLiveUpdate(data: ToastData, server: MinecraftServer, sender: ResponseSender) {
        Apibalego.LOGGER.info("LiveUpdatesConnection | Received toast")
        val packet = data.toPacket()
        server.playerList.players.forEach { player ->
            player.sendPacket(packet) { future ->
                if (future.isSuccess) sender.sendSuccess() else sender.sendFailure()
            }
        }
    }
}
