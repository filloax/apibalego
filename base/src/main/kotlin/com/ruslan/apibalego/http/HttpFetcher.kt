package com.ruslan.apibalego.http

import com.ruslan.apibalego.utils.ApibalegoLogger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * shared connection/request setup
 */
class HttpFetcher(
    private val threadNamePrefix: String,
    private val logger: ApibalegoLogger,
) {
    private var client: OkHttpClient? = null

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(5)

        // Shared client for one-off blocking calls (preload), before polling executor is setup
        private val BLOCKING_CLIENT = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .build()

        fun makeRequest(url: String, headers: Map<String, String> = emptyMap()): Request {
            val builder = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept-Charset", "UTF-8")
            headers.forEach { (key, value) -> builder.header(key, value) }
            return builder.build()
        }

        fun getResponseContent(response: Response): String = response.use { it.body?.string() ?: "" }
    }

    fun sendRequest(request: Request): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()
        val c = client
        if (c == null) {
            future.completeExceptionally(IllegalStateException("HTTP client not setup!"))
            return future
        }
        logger.info("$threadNamePrefix: CONNECTING VIA ${request.url}")
        c.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logger.error("$threadNamePrefix: FAILURE WITH ${request.url}", e.message)
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                logger.info("$threadNamePrefix: DISCONNECTED FROM ${request.url}")
                future.complete(response)
            }
        })
        return future
    }

    // use when async executors are not setup yet (preload, mostly)
    fun sendRequestBlocking(request: Request): Response {
        logger.info("$threadNamePrefix: CONNECTING VIA ${request.url} (blocking)")
        return BLOCKING_CLIENT.newCall(request).execute()
    }

    fun start() {
        stop()
        val poolNum = AtomicInteger(1)
        val executor = Executors.newFixedThreadPool(
            1,
        ) { r ->
            // Daemon so a missed stop() (e.g. client process exit) can't hang the JVM
            Thread(null, r, "$threadNamePrefix-${poolNum.getAndIncrement()}-thread").also { it.isDaemon = true }
        }
        client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .dispatcher(okhttp3.Dispatcher(executor))
            .build()
    }

    fun stop() {
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }
}
