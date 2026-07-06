package com.ruslan.apibalego.http

import com.ruslan.apibalego.utils.ApibalegoLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * connection/request setup, shared between the server-side ([DataRemoteSync])
 * and client-side sync classes
 */
class HttpFetcher(
    private val threadNamePrefix: String,
    private val logger: ApibalegoLogger,
) {
    private var executorService: ExecutorService? = null

    fun makeConnection(url: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept-Charset", "UTF-8")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        headers.forEach { conn.setRequestProperty(it.key, it.value) }

        return conn
    }

    fun sendRequest(conn: HttpURLConnection): CompletableFuture<HttpURLConnection> {
        val completableFuture = CompletableFuture<HttpURLConnection>()
        executorService?.submit {
            logger.info("$threadNamePrefix: CONNECTING VIA ${conn.url}")
            try {
                conn.connect()
                completableFuture.complete(conn)
                conn.disconnect()
                logger.info("$threadNamePrefix: DISCONNECTED FROM ${conn.url}")
            } catch (e: Exception) {
                logger.error("$threadNamePrefix: FAILURE WITH ${conn.url}", e.message)
                completableFuture.completeExceptionally(e)
            }
        } ?: run {
            completableFuture.completeExceptionally(IllegalStateException("Executor service not setup!"))
        }
        return completableFuture
    }

    fun getResponseContent(conn: HttpURLConnection): String {
        val respReader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
        var inputLine: String?
        val contentBuffer = StringBuffer()
        while (respReader.readLine().also { inputLine = it } != null) {
            contentBuffer.append(inputLine)
        }
        respReader.close()
        return contentBuffer.toString()
    }

    fun start() {
        stop()
        val poolNum = AtomicInteger(1)
        executorService = Executors.newFixedThreadPool(
            1,
            object : ThreadFactory {
                private val threadNum = AtomicInteger(1)
                private val namePrefix = "$threadNamePrefix-" + poolNum.getAndIncrement() + "-thread"
                override fun newThread(r: Runnable): Thread {
                    // Daemon so a missed stop() (e.g. client process exit) can't hang the JVM
                    return Thread(null, r, namePrefix + threadNum.getAndIncrement()).also { it.isDaemon = true }
                }
            }
        )
    }

    fun stop() {
        executorService?.shutdown()
        executorService = null
    }
}
