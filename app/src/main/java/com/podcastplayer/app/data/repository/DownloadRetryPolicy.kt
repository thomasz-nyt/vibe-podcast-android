package com.podcastplayer.app.data.repository

import java.io.IOException

internal class HttpStatusException(val status: Int, url: String) : IOException("HTTP $status from $url")

internal object DownloadRetryPolicy {
    const val MAX_RETRIES = 2
    fun isTransient(error: Throwable): Boolean = when (error) {
        is HttpStatusException -> error.status == 408 || error.status == 429 || error.status >= 500
        is java.io.FileNotFoundException -> false
        is IOException -> true
        else -> false
    }
    fun shouldRetry(error: Throwable, attempt: Int): Boolean = attempt < MAX_RETRIES && isTransient(error)
}
