package com.podcastplayer.app.service

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/** Durable scan outcome; active state comes from WorkManager so process death cannot leave 'checking'. */
class AutoDownloadMonitor(context: Context) {
    private val prefs = context.getSharedPreferences("auto_download_checks", Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(context)
    val lastSuccess: Long get() = prefs.getLong("last_success", 0L)

    fun recordSuccess() {
        prefs.edit().putLong("last_success", System.currentTimeMillis()).remove("error").apply()
    }

    fun recordFailure(message: String) { prefs.edit().putString("error", message).apply() }

    val status = combine(
        callbackFlow {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                trySend(Unit)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Unit)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        },
        workManager.getWorkInfosForUniqueWorkFlow(AutoDownloadWorker.UNIQUE_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(AutoDownloadWorker.IMMEDIATE_WORK_NAME),
    ) { _, periodic, immediate ->
        val error = prefs.getString("error", null)
        val message = when {
            (periodic + immediate).any { it.state == WorkInfo.State.RUNNING } -> "Checking for new episodes"
            error != null -> "Check failed: $error"
            immediate.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
                "Waiting for allowed network or background scheduling"
            lastSuccess > 0 -> "Check completed"
            else -> "Not checked yet"
        }
        AutoDownloadCheckStatus(message, lastSuccess)
    }
}

data class AutoDownloadCheckStatus(val message: String, val lastSuccessMs: Long)
