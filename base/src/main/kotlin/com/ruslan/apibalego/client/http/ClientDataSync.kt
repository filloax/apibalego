package com.ruslan.apibalego.client.http

import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.http.HttpFetcher
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import java.io.File
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Client-side counterpart to [com.ruslan.apibalego.http.DataRemoteSync]: lets the client fetch
 * data from the gamemaster server on its own, so it can be done in main menu, download
 * resource packs, etc.
 */
object ClientDataSync {
    private val logger = Apibalego.LOGGER
    private val httpFetcher = HttpFetcher("apibalego-client-requests", logger)
    private val json = Json { ignoreUnknownKeys = true }
    private val subscriptions = mutableMapOf<String, Subscription>()
    private val subscriptionHeaders = mutableMapOf<String, MutableMap<String, String>>()
    private var scheduler: ScheduledExecutorService? = null

    fun <T: Any> subscribe(name: String, url: String, serializer: DeserializationStrategy<T>, callback: (T) -> Unit) {
        subscribeRaw(name, url) { response ->
            try {
                callback(json.decodeFromString(serializer, response))
            } catch (e: SerializationException) {
                logger.error("[client:$name] JSON PARSE FAILURE, IS $response", e)
            } catch (e: Exception) {
                logger.error("[client:$name] OTHER FAILURE", e)
            }
        }
    }

    fun subscribeRaw(name: String, url: String, callback: (String) -> Unit) {
        subscriptions.computeIfAbsent(name) { Subscription(url) }.also { it.url = url }.callbacks.add(callback)
    }

    /** Update the url of an existing subscription, keeping its cache intact. */
    fun setUrl(name: String, url: String) {
        val subscription = subscriptions[name]
        if (subscription == null) {
            logger.warn("Tried to set url for unknown subscription $name")
            return
        }
        subscription.url = url
    }

    fun headers(name: String): MutableMap<String, String> =
        subscriptionHeaders.computeIfAbsent(name) { mutableMapOf() }

    fun start(syncInterval: Duration = Duration.ofMinutes(ApiBalegoConfig.clientDataSyncReloadTime.toLong())) {
        if (!ApiBalegoConfig.clientDataSync) return

        httpFetcher.start()
        loadFromCache()
        sync()
        val intervalSeconds = syncInterval.seconds.coerceAtLeast(10)
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(null, r, "apibalego-client-sync").also { it.isDaemon = true }
        }
        scheduler?.scheduleWithFixedDelay({ sync() }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduler?.shutdown()
        scheduler = null
        httpFetcher.stop()
    }

    /** Force an immediate poll of every subscription, bypassing the schedule (e.g. a live-update reload push). */
    fun sync() {
        subscriptions.forEach { (name, subscription) ->
            if (subscription.url.isBlank()) return@forEach
            val request = HttpFetcher.makeRequest(subscription.url, subscriptionHeaders[name] ?: emptyMap())
            httpFetcher.sendRequest(request).whenComplete { response, exception ->
                try {
                    if (exception != null) {
                        logger.error("[client:$name] ERROR: ${exception.message}")
                        return@whenComplete
                    }
                    response.use {
                        val status = response.code
                        if (status < 300) {
                            val content = response.body?.string() ?: ""
                            saveToCache(name, content)
                            logger.info("[client:$name] SUCCESS, STATUS: $status")
                            subscription.callbacks.forEach { it(content) }
                        } else {
                            logger.error("[client:$name] ERROR, STATUS $status")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("[client:$name] OTHER FAILURE", e)
                }
            }
        }
    }

    private fun cacheDir(): File =
        File(Minecraft.getInstance().gameDirectory, "apibalego/cache").also { it.mkdirs() }

    private fun cacheFile(name: String): File =
        File(cacheDir(), name.replace("/", "_") + ".json")

    private fun saveToCache(name: String, content: String) {
        cacheFile(name).writeText(content)
    }

    private fun loadFromCache() {
        subscriptions.forEach { (name, subscription) ->
            val file = cacheFile(name)
            if (file.exists()) {
                logger.info("[client:$name] Restoring from local cache")
                val content = file.readText()
                subscription.callbacks.forEach { it(content) }
            }
        }
    }

    private class Subscription(var url: String) {
        val callbacks = mutableListOf<(String) -> Unit>()
    }
}
