package com.endermythex.enderrmodded.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client HTTP minimal (sans dépendance) avec cookies, redirections et téléchargement avec progression.
 */
object Http {
    const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0"

    init {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    class HttpException(val code: Int, url: String, val retryAfterSeconds: Long? = null) : IOException("HTTP $code : $url")

    fun open(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = BROWSER_UA,
    ): HttpURLConnection {
        var attempt = 0
        while (true) {
            try {
                return openOnce(url, headers, userAgent)
            } catch (e: HttpException) {
                // Trop de requêtes / service indisponible : on patiente puis on réessaie.
                if ((e.code != 429 && e.code != 503) || attempt >= 3) throw e
                Thread.sleep((e.retryAfterSeconds ?: (2L shl attempt)).coerceAtMost(30) * 1000)
                attempt++
            }
        }
    }

    private fun openOnce(url: String, headers: Map<String, String>, userAgent: String): HttpURLConnection {
        var current = url
        repeat(10) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw HttpException(code, current)
                connection.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                connection.disconnect()
                throw HttpException(code, current, retryAfter)
            }
            return connection
        }
        throw IOException("Trop de redirections : $url")
    }

    /** Contenu de la page et URL finale (après redirections). */
    fun getPage(url: String, headers: Map<String, String> = emptyMap(), userAgent: String = BROWSER_UA): Pair<String, String> =
        open(url, headers, userAgent).let { c -> c.url.toString() to c.inputStream.use { it.readBytes().decodeToString() } }

    fun getString(url: String, headers: Map<String, String> = emptyMap(), userAgent: String = BROWSER_UA): String =
        open(url, headers, userAgent).let { c -> c.inputStream.use { it.readBytes().decodeToString() } }

    fun download(
        url: String,
        destination: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val connection = open(url, headers)
        val total = connection.contentLengthLong
        val tmp = File(destination.parentFile, destination.name + ".part")
        destination.parentFile?.mkdirs()
        connection.inputStream.use { input -> tmp.outputStream().use { copy(input, it, total, onProgress) } }
        if (destination.exists()) destination.delete()
        if (!tmp.renameTo(destination)) throw IOException("Impossible d'écrire $destination")
    }

    private fun copy(input: InputStream, output: java.io.OutputStream, total: Long, onProgress: (Long, Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var downloaded = 0L
        var lastReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (downloaded - lastReport > 256 * 1024) {
                lastReport = downloaded
                onProgress(downloaded, total)
            }
        }
        onProgress(downloaded, total)
    }
}
