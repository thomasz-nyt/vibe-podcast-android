package com.podcastplayer.app.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared HTTP GET-with-redirects helper.
 *
 * `HttpURLConnection`'s built-in redirect handling does not cross http<->https,
 * which real-world RSS feeds and media CDNs both do. This manually follows
 * redirects (bounded, with a visited-chain trail for error messages) and always
 * sets connect/read timeouts so callers never block indefinitely on a stalled
 * socket. Used by both [DownloadManager] (media downloads) and [PodcastRepository]
 * (RSS feed fetches).
 */
internal object HttpConnections {
    private const val MAX_REDIRECTS = 20
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    fun openWithRedirects(
        initialUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpURLConnection {
        var url = URL(initialUrl)
        var redirects = 0
        val visited = mutableListOf(url.toString())
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("HTTP $code without Location header from $url")
                }
                if (++redirects > MAX_REDIRECTS) {
                    throw IOException(
                        "Exceeded $MAX_REDIRECTS redirects. Chain: ${visited.joinToString(" -> ")}"
                    )
                }
                url = try {
                    URL(url, location)
                } catch (e: Exception) {
                    throw IOException("Invalid redirect target '$location' from $url", e)
                }
                visited += url.toString()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code from $url")
            }
            return conn
        }
    }
}
