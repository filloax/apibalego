package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.client.Minecraft
import java.util.Objects

/**
 * Client-side counterpart to [com.ruslan.apibalego.http.GamemasterApi]: polls the gamemaster
 * directly from the client (works with no world/server loaded, like from main menu)
 */
object ClientGamemasterApi {
    private var lastHash: Int? = null

    fun init() {
        ClientDataSync.headers(subscriptionName())["apiKey"] = ApiBalegoConfig.clientDataSyncApiKey

        ClientDataSync.subscribe(subscriptionName(), syncUrl(), ListSerializer(ClientApiEntryRaw.serializer()), ::handleUpdate)
    }

    /** The name used to identify this subscription (see [ClientDataSync.setUrl]). */
    fun subscriptionName(): String = ApiBalegoConfig.clientDataSyncEndpoint

    fun syncUrl(): String = "${ApiBalegoConfig.clientDataSyncUrl}/${ApiBalegoConfig.clientDataSyncEndpoint}"

    private fun handleUpdate(entries: List<ClientApiEntryRaw>) {
        val hash = Objects.hash(entries)
        if (hash == lastHash) {
            Apibalego.LOGGER.info("Client gamemaster data unchanged, not re-dispatching")
            return
        }

        val currentlyActive = entries.filter { it.active }
        ClientApiEntryRegistry.dispatchUpdate(currentlyActive, Minecraft.getInstance())

        lastHash = hash
    }
}
