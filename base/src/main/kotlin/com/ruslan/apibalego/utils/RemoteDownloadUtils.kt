package com.ruslan.apibalego.utils

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.http.HttpFetcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.deleteIfExists

/**
 * Shared logic for pack downloads
 */
object RemoteDownloadUtils {
    /** Used for the "allow external" option */
    fun isUrlAllowed(url: String, baseUrl: String, allowExternal: Boolean): Boolean {
        if (allowExternal) return true
        return try {
            val target = URI(url)
            val base = URI(baseUrl)
            target.scheme == base.scheme && target.host == base.host && effectivePort(target) == effectivePort(base)
        } catch (e: Exception) {
            false
        }
    }

    private fun effectivePort(uri: URI): Int {
        if (uri.port != -1) return uri.port
        return when (uri.scheme?.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }

    /** Check if install requires update */
    fun installIdentity(version: String, downloadUrl: String): String =
        "${version}_${downloadUrl.hashCode()}"

    fun sanitizeForFileName(s: String): String = s.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

    // pack downloads can be bigger/slower than a manifest fetch, hence the longer timeouts than
    // HttpFetcher's default
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .build()

    fun downloadToFile(target: Path, url: String, apiKey: String) {
        ApibalegoMod.LOGGER.info("Downloading ${target.fileName} from $url...")
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        val headers = if (apiKey.isNotBlank()) mapOf("apiKey" to apiKey) else emptyMap()
        val request: Request = HttpFetcher.makeRequest(url, headers)
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            try {
                val bytesCopied = (response.body ?: throw IOException("Empty response body")).byteStream().use { input ->
                    Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                ApibalegoMod.LOGGER.info("Downloaded ${target.fileName} ($bytesCopied bytes)")
            } finally {
                tmp.deleteIfExists()
            }
        }
    }
}
