package com.ruslan.apibalego.http.handlers

import com.filloax.fxlib.api.ScheduledServerTask
import com.filloax.fxlib.api.entity.getPersistData
import com.filloax.fxlib.api.json.ItemByNameSerializer
import com.filloax.fxlib.api.json.SimpleComponentSerializer
import com.filloax.fxlib.api.nbt.putIfAbsent
import com.filloax.fxlib.api.networking.sendPacket
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.http.GenericApiEvent
import com.ruslan.apibalego.http.ResponseSender
import com.ruslan.apibalego.network.CustomToastPacket
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrNull

/**
 * Built-in toast handler. Reacts to "toast/..." gamemaster events (polled and on player join)
 * and to "toast" websocket live-update messages.
 */
object ToastHandler {
    const val PREFIX = "toast"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun handle(event: GenericApiEvent, server: MinecraftServer) {
        // Avoid doing too soon on init
        val tickCount = server.tickCount
        val minStartTime = 40
        val delay = (minStartTime - tickCount).coerceAtLeast(0)
        if (delay > 0) {
            ScheduledServerTask.schedule(server, delay) {
                sendAllCustomToastEvent(event, server)
            }
        } else {
            sendAllCustomToastEvent(event, server)
        }
    }

    fun handleJoin(event: GenericApiEvent, player: ServerPlayer) {
        val seqId = event.pos?.let { "${it.x}${it.y}${it.z}" } ?: ""
        val packet = getToastPacket(event, player.level().server)
        checkAndSendCustomToastEvent(player, packet, seqId)
    }

    private fun getToastPacket(event: GenericApiEvent, server: MinecraftServer): CustomToastPacket {
        val titlePart = event.name.replace("$PREFIX/", "").trim()
        val itemToastPattern = Regex("(?<namespace>[^/]+)/(?<path>[^/]+)/(?<title>.+)")
        val match = itemToastPattern.matchEntire(titlePart)
        var item: Item? = null
        val title: String = if (match != null) {
            val namespace = match.groups["namespace"]?.value
            val path = match.groups["path"]?.value
            val title = match.groups["title"]?.value
            if (namespace != null && path != null && title != null) {
                val itemId = Identifier.fromNamespaceAndPath(namespace, path)
                val registryAccess = server.registryAccess()
                item = registryAccess.lookup(Registries.ITEM).getOrNull()?.getValue(itemId)
                if (item == null) {
                    Apibalego.LOGGER.warn("Custom item toast: couldn't find item with id $itemId")
                }
                title
            } else {
                titlePart
            }
        } else {
            titlePart
        }

        val content = event.desc?.let { Component.literal(it) }

        return CustomToastPacket(Component.literal(title), content, item?.defaultInstance ?: ItemStack.EMPTY)
    }

    private fun sendAllCustomToastEvent(event: GenericApiEvent, server: MinecraftServer) {
        val seqId = event.pos?.let { "${it.x}${it.y}${it.z}" } ?: ""
        val packet = getToastPacket(event, server)
        server.playerList.players.forEach { player -> checkAndSendCustomToastEvent(player, packet, seqId) }
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

    // live-update toast

    @Serializable
    private data class ToastData(
        @Serializable(with = SimpleComponentSerializer::class)
        val title: Component,
        @Serializable(with = SimpleComponentSerializer::class)
        val message: Component? = null,
        @Serializable(with = ItemByNameSerializer::class)
        val item: Item? = null,
    )

    fun handleLiveUpdate(message: String, server: MinecraftServer, sender: ResponseSender) {
        Apibalego.LOGGER.info("LiveUpdatesConnection | Received toast message $message")
        val toastData: ToastData = try {
            json.decodeFromString(message)
        } catch (e: Exception) {
            Apibalego.LOGGER.error("LiveUpdatesConnection | Wrong toast format: ${e.message}")
            e.printStackTrace()
            sender.sendFailure(e.message)
            return
        }

        val packet = CustomToastPacket(toastData.title, toastData.message, toastData.item?.defaultInstance ?: ItemStack.EMPTY)
        server.playerList.players.forEach { player ->
            player.sendPacket(packet) { if (it.isSuccess) sender.sendSuccess() else sender.sendFailure() }
        }
    }
}
