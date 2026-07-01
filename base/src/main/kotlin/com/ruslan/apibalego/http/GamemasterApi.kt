package com.ruslan.apibalego.http

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Objects

/**
 * The gamemaster API: polls the data-sync endpoint and routes each response entry to the
 * handlers registered for its type ([ApiEntryRegistry]), then dispatches the resulting events
 * ([com.ruslan.apibalego.socket.ApiEventRegistry]). Active events are retained so they can be replayed to joining players.
 */
object GamemasterApi {
    private val activeEntries = mutableListOf<ApiEntryRaw>()
    private var lastHash: Int? = null

    fun init() {
        DataRemoteSync.endpointParams(ApiBalegoConfig.dataSyncEndpoint).headers["apiKey"] = ApiBalegoConfig.dataSyncApiKey

        DataRemoteSync.subscribe(ApiBalegoConfig.dataSyncEndpoint, ListSerializer(ApiEntryRaw.serializer()), ::handleUpdate)
    }

    object Callbacks {
        fun onPlayerJoin(player: ServerPlayer) {
            ApiEntryRegistry.dispatchJoin(activeEntries, player)
        }

        fun onServerStop() {
            reset()
        }
    }

    private fun handleUpdate(entries: List<ApiEntryRaw>, server: MinecraftServer) {
        val hash = Objects.hash(entries)
        if (hash == lastHash) {
            Apibalego.LOGGER.info("Gamemaster data unchanged, not re-dispatching")
            return
        }

        val currentlyActive = entries.filter { it.active }

        activeEntries.clear()
        activeEntries.addAll(currentlyActive)

        ApiEntryRegistry.dispatchUpdate(currentlyActive, server)

        lastHash = hash
    }

    private fun reset() {
        activeEntries.clear()
        lastHash = null
    }
}
