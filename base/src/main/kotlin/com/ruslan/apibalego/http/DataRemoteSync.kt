package com.ruslan.apibalego.http

import com.google.gson.GsonBuilder
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.data.ApibalegoPersistentData
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

/**
 * Handles periodic requests to a set of subscribed URLs (if enabled), with generic type parsed
 * from the response as specified by caller. Each subscription is identified by a stable [name]
 * (used for caching/dedup) independent of its [Subscription.url], so the url can be changed
 * live (e.g. via `/gmaster url`) without losing cached state.
 */
object DataRemoteSync {
    var lastSyncSuccessful = false
        private set
    var lastUpdateTime: LocalDateTime? = null
        private set
    var lastSuccessfulUpdateTime: LocalDateTime? = null
        private set

    private var tickUpdateRealTimeDistance: Duration =
        Duration.ofSeconds(((ApiBalegoConfig.dataSyncReloadTime * 60).toLong()))   // how often the mod should query the server
        set(value) {
            if (value > Duration.ofSeconds(10)) {
                throw IllegalArgumentException("Duration too short, must be at least 10s: $value")
            }
            field = value
        }

    private val subscriptions = mutableMapOf<String, Subscription>()
    private val subscriptionParams = mutableMapOf<String, SubscriptionParams>()
    private val gson = GsonBuilder().create()
    private val json = Json { ignoreUnknownKeys = true } // Kotlinx's serialization acts better with kotlin non-nullables etc
    private val httpFetcher = HttpFetcher("apibalego-requests", ApibalegoMod.LOGGER)
    private var didFirstLoad = mutableMapOf<String, Boolean>()
    private val doOnNextServerStart = LinkedBlockingQueue<(MinecraftServer) -> Unit>()
    private val logger = ApibalegoMod.LOGGER

    /**
     * Subscribe to a URL under the given (stable) name. Will send a GET request to that URL, and
     * parse the response as T.
     */
    fun <T: Any>subscribe(name: String, url: String, serializer: DeserializationStrategy<T>, callback: (T, MinecraftServer) -> Unit) {
        subscribeRaw(name, url) { response, server ->
            try {
                callback(json.decodeFromString(serializer, response), server)
            } catch (e: SerializationException) {
                logger.error("[$name] JSON* PARSE FAILURE, IS $response", e)
            } catch (e: Exception) {
                logger.error("[$name] OTHER FAILURE", e)
            }
        }
    }

    /**
     * Subscribe to a URL under the given (stable) name. Will send a GET request to that URL, and
     * call the callback with the raw response.
     */
    fun subscribeRaw(name: String, url: String, callback: (String, MinecraftServer) -> Unit) {
        subscriptions.computeIfAbsent(name) { Subscription(url) }.also { it.url = url }.callbacks.add(callback)
    }

    /**
     * Update the url of an existing subscription (e.g. on config change), keeping its cache/dedup
     * state (keyed by [name]) intact.
     */
    fun setUrl(name: String, url: String) {
        val subscription = subscriptions[name]
        if (subscription == null) {
            logger.warn("Tried to set url for unknown subscription $name")
            return
        }
        subscription.url = url
    }

    fun params(name: String): SubscriptionParams {
        return subscriptionParams.computeIfAbsent(name) { SubscriptionParams() }
    }

    /**
     * Run data sync on all subscriptions.
     * @return A completable future that completes when all subscriptions do, and is true if all had a success
     */
    fun doSync(server: MinecraftServer): CompletableFuture<Boolean> {
        if (!ApiBalegoConfig.webDataSync) {
            return CompletableFuture.completedFuture(false)
        }

        val updateTime = LocalDateTime.now().also { lastUpdateTime = it }
        val future = CompletableFuture<Boolean>()
        val successes = mutableMapOf<String, Boolean>()
        subscriptions.forEach { (name, subscription) ->
            if (subscription.url.isBlank()) {
                logger.warn("[$name] Sync url is empty, won't run")
                successes[name] = false
                return@forEach
            }
            syncSubscription(name, subscription.url, subscription.callbacks, server).thenAccept ta@{ success ->
                successes[name] = success
                if (successes.keys.size >= subscriptions.keys.size) {
                    future.complete(successes.values.all{it})
                }
            }
        }
        return future.thenApply { success ->
            lastSyncSuccessful = success
            if (success) {
                lastSuccessfulUpdateTime = updateTime
            }
            success
        }
    }

    private fun syncSubscription(name: String, url: String, callbacks: List<(String, MinecraftServer) -> Unit>, server: MinecraftServer): CompletableFuture<Boolean> {
        val params = subscriptionParams[name] ?: DEFAULT_PARAMS
        val request = makeRequest(url, params)
        val future = CompletableFuture<Boolean>()
        httpFetcher.sendRequest(request).whenComplete { response, exception ->
            try {
                val result = if (exception != null) {
                    if (name !in didFirstLoad) {
                        logger.info("[$name] Restoring from server memory after connection error as didn't load the first time yet")
                        restoreFromMemory(server, name).thenAccept { savedData ->
                            onRestored(callbacks, server, name, savedData)
                        }
                    }
                    logger.error("[$name] ERROR: ${exception.message}")
                    false

                } else response.use {
                    val status = response.code
                    if (status < 300 && server.isRunning) {
                        didFirstLoad[name] = true
                        val content = response.body?.string() ?: ""
                        saveToMemory(server, name, content)
                        logger.info("[$name] SUCCESS, STATUS: $status")
                        callbacks.forEach { it(content, server) }
                        true
                    } else if (!server.isRunning) {
                        logger.error("Data sync $name: server not running, abort...")
                        false
                    } else {
                        logger.error("[$name] ERROR, STATUS $status\n${response.body?.string() ?: ""}")

                        if (name !in didFirstLoad) {
                            logger.info("[$name] Restoring from server memory after error as didn't load the first time yet")
                            restoreFromMemory(server, name).thenAccept { savedData ->
                                onRestored(callbacks, server, name, savedData)
                            }
                        }
                        false
                    }
                }

                future.complete(result)
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }
        }
        return future
    }

    private fun makeRequest(url: String, params: SubscriptionParams = DEFAULT_PARAMS) =
        HttpFetcher.makeRequest(url, params.headers)

    private fun setupExecutorService() = httpFetcher.start()

    private fun shutdownExecutorService() = httpFetcher.stop()

    private fun saveToMemory(server: MinecraftServer, name: String, response: String) {
        val overworld = getOverworldOrNull(server)
        if (overworld != null) {
            val savedData = ApibalegoPersistentData.get(server)
            savedData.lastEndpointOutputs[name] = response
            savedData.setDirty()
            logger.info("Updated data sync save data")
        } else if (server.isRunning) {
            doOnNextServerStart.offer {
                saveToMemory(server, name, response)
            }
        }
    }

    private fun restoreFromMemory(server: MinecraftServer, name: String, existingFuture: CompletableFuture<String?>? = null): CompletableFuture<String?> {
        val future = existingFuture ?: CompletableFuture<String?>()

        if (!server.isRunning) {
            // abort
            future.completeExceptionally(IllegalStateException("Restore subscription abort: Server not running anymore"))
            return future
        }

        val overworld = getOverworldOrNull(server)
        if (overworld != null) {
            future.complete(ApibalegoPersistentData.get(server).lastEndpointOutputs[name])
        } else {
            doOnNextServerStart.offer {
                restoreFromMemory(server, name, future)
            }
        }

        return future
    }

    private fun onRestored(callbacks: List<(String, MinecraftServer) -> Unit>, server: MinecraftServer, name: String, savedData: String?) {
        if (savedData == null) {
            logger.warn("[$name] No data for sync in server memory!")
            return
        }
        callbacks.forEach { it(savedData, server) }
        didFirstLoad[name] = true
        logger.info("[$name] Restore successful")
    }

    class Subscription(var url: String) {
        val callbacks = mutableListOf<(String, MinecraftServer) -> Unit>()
    }

    data class SubscriptionParams(
        val headers: MutableMap<String, String> = mutableMapOf(),
    )

    private fun getOverworldOrNull(server: MinecraftServer): ServerLevel? {
        return try {
            // Java checks for this to not be null, for some reason?
            server.overworld()
        } catch (e: NullPointerException) {
            null
        }
    }

    val DEFAULT_PARAMS = SubscriptionParams()

    object Callbacks {
        fun handleServerAboutToStartEvent(server: MinecraftServer) {
            setupExecutorService()
        }

        fun handleServerStoppingEvent() {
            shutdownExecutorService()
        }

        fun onServerLevel(server: MinecraftServer, level: ServerLevel) {
            if (ApiBalegoConfig.webDataSync && level.dimension() == Level.OVERWORLD) {
                while (doOnNextServerStart.isNotEmpty()) {
                    doOnNextServerStart.poll()(server)
                }
            }
        }

        fun onServerTick(server: MinecraftServer) {
            if (ApiBalegoConfig.webDataSync) {
                val time = LocalDateTime.now()
                // check real time to make pause not affect it
                if (lastUpdateTime?.let{ Duration.between(it, time) >= tickUpdateRealTimeDistance } == true) {
                    logger.info("Data sync: started periodic sync")
                    doSync(server)
                }
            }
        }
    }
}