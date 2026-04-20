package com.podcastplayer.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.podcastplayer.app.domain.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QueuePodcastsStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()

    private val _queuePodcasts = MutableStateFlow(load())
    val queuePodcasts: StateFlow<List<Podcast>> = _queuePodcasts.asStateFlow()

    suspend fun add(podcast: Podcast) {
        mutex.withLock {
            val updated = (_queuePodcasts.value + podcast).distinctBy { it.id }
            persist(updated)
        }
    }

    suspend fun remove(podcastId: String) {
        mutex.withLock {
            val updated = _queuePodcasts.value.filterNot { it.id == podcastId }
            persist(updated)
        }
    }

    suspend fun move(fromIndex: Int, toIndex: Int) {
        mutex.withLock {
            val current = _queuePodcasts.value.toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            persist(current)
        }
    }

    private fun persist(list: List<Podcast>) {
        prefs.edit().putString(KEY_QUEUE, gson.toJson(list)).apply()
        _queuePodcasts.value = list
    }

    private fun load(): List<Podcast> {
        val json = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Podcast>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "queue_podcasts"
        private const val KEY_QUEUE = "queue"
    }
}
