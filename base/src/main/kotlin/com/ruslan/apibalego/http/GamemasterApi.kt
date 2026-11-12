package com.ruslan.apibalego.http

import com.filloax.fxlib.api.EventUtil
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.trigger.TriggerHandler
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Objects
import java.util.concurrent.Executors

/**
 * The gamemaster API: polls the data-sync endpoint and routes each response entry to the
 * handlers registered for its type ([ApiEntryRegistry]), then dispatches the resulting events
 * ([com.ruslan.apibalego.socket.ApiEventRegistry]). Active events are retained so they can be replayed to joining players.
 */
object GamemasterApi {
    // Volatile as different threads read/update it
    /** Entries from last sync */
    @Volatile
    var activeEntries: List<ApiEntryRaw> = listOf()
        private set

    private var lastHash: Int? = null
    private val dispatchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "apibalego-dispatch").apply { isDaemon = true }
    }

    fun init() {
        DataRemoteSync.params(ApiBalegoConfig.dataSyncEndpoint).headers["apiKey"] = ApiBalegoConfig.dataSyncApiKey

        DataRemoteSync.subscribe(ApiBalegoConfig.dataSyncEndpoint, syncUrl(), ListSerializer(ApiEntryRaw.serializer()), ::handleUpdate)
    }

    /** The name used to identify the built-in gamemaster subscription (see [DataRemoteSync.setUrl]). */
    fun subscriptionName(): String = ApiBalegoConfig.dataSyncEndpoint

    fun syncUrl(): String = "${ApiBalegoConfig.dataSyncUrl}/${ApiBalegoConfig.dataSyncEndpoint}"

    object Callbacks {
        fun onPlayerJoin(player: ServerPlayer) {
            val server = player.level().server
            val entries = activeEntries
            // forPlayer == null -> normal API entry, not added by triggers or similar features
            ApiEntryRegistry.dispatchJoin(expandTriggers(entries, server).filter { it.forPlayer == null }, player)
            TriggerHandler.refreshForPlayer(player, entries)
        }

        fun onServerStop() {
            reset()
        }
    }

    private fun handleUpdate(entries: List<ApiEntryRaw>, server: MinecraftServer) {
        val hash = Objects.hash(entries)
        if (hash == lastHash) {
            ApibalegoMod.LOGGER.info("Gamemaster data unchanged, not re-dispatching")
            return
        }

        val currentlyActive = entries.filter { it.active }

        activeEntries = currentlyActive

        dispatch(currentlyActive, server)

        lastHash = hash
    }

    /**
     * Reload entries (in case triggers were changed)
     */
    fun redispatch(server: MinecraftServer, types: Collection<ApiEntryType<*>>) {
        dispatch(activeEntries, server, types)
    }

    private fun dispatch(entries: List<ApiEntryRaw>, server: MinecraftServer, onlyTypes: Collection<ApiEntryType<*>>? = null) {
        // expand triggers on server thread to read player state (deaths, advancements, etc)
        EventUtil.runWhenServerStarted(server, true) { srv ->
            val expanded = expandTriggers(entries, srv)
            // dispatch effects on separate threads to avoid blocking server with
            // datapack download
            dispatchExecutor.execute {
                ApiEntryRegistry.dispatchUpdate(expanded, srv, onlyTypes)
            }
        }
    }

    private fun expandTriggers(entries: List<ApiEntryRaw>, server: MinecraftServer) =
        entries.filterNot(TriggerHandler::isTrigger) + TriggerHandler.resolveTriggerEntries(entries, server.playerList.players)

    private fun reset() {
        activeEntries = listOf()
        lastHash = null
    }
}
