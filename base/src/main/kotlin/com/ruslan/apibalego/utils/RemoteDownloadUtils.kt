package com.ruslan.apibalego.utils

import com.ruslan.apibalego.Apibalego
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    fun downloadToFile(target: Path, url: String, apiKey: String) {
        Apibalego.LOGGER.info("Downloading ${target.fileName} from $url...")
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        val conn: URLConnection = URI(url).toURL().openConnection()
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        if (conn is HttpURLConnection) {
            conn.requestMethod = "GET"
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("apiKey", apiKey)
            }
        }
        conn.connect()
        try {
            if (conn is HttpURLConnection && conn.responseCode >= 300) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            val bytesCopied = conn.getInputStream().use { input ->
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Apibalego.LOGGER.info("Downloaded ${target.fileName} ($bytesCopied bytes)")
        } finally {
            if (conn is HttpURLConnection) conn.disconnect()
            tmp.deleteIfExists()
        }
    }
}
