package com.podcastplayer.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class QueueStorage(context: Context) {

    data class QueuePayload(
        val id: String,
        val name: String,
        val createdAt: Long,
        val podcastIds: List<String>
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()

    private val _queues = MutableStateFlow(emptyList<QueuePayload>())
    val queues: StateFlow<List<QueuePayload>> = _queues.asStateFlow()

    init {
        val loaded = load()
        if (loaded.isEmpty()) {
            val defaultQueue = QueuePayload(
                id = UUID.randomUUID().toString(),
                name = "Morning",
                createdAt = System.currentTimeMillis(),
                podcastIds = emptyList()
            )
            persist(listOf(defaultQueue))
        } else {
            _queues.value = loaded
        }
    }

    suspend fun createQueue(name: String): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val updated = _queues.value + QueuePayload(
            id = id,
            name = name.trim().ifBlank { "Queue" },
            createdAt = System.currentTimeMillis(),
            podcastIds = emptyList()
        )
        persist(updated)
        id
    }

    suspend fun renameQueue(queueId: String, name: String) = mutex.withLock {
        val updated = _queues.value.map { queue ->
            if (queue.id == queueId) queue.copy(name = name.trim().ifBlank { queue.name }) else queue
        }
        persist(updated)
    }

    suspend fun deleteQueue(queueId: String) = mutex.withLock {
        val updated = _queues.value.filterNot { it.id == queueId }
        persist(updated)
    }

    suspend fun addPodcast(queueId: String, podcastId: String) = mutex.withLock {
        val updated = _queues.value.map { queue ->
            if (queue.id == queueId && !queue.podcastIds.contains(podcastId)) {
                queue.copy(podcastIds = queue.podcastIds + podcastId)
            } else {
                queue
            }
        }
        persist(updated)
    }

    suspend fun removePodcast(queueId: String, podcastId: String) = mutex.withLock {
        val updated = _queues.value.map { queue ->
            if (queue.id == queueId) {
                queue.copy(podcastIds = queue.podcastIds.filterNot { it == podcastId })
            } else {
                queue
            }
        }
        persist(updated)
    }

    suspend fun movePodcast(queueId: String, fromIndex: Int, toIndex: Int) = mutex.withLock {
        val updated = _queues.value.map { queue ->
            if (queue.id == queueId) {
                val list = queue.podcastIds.toMutableList()
                if (fromIndex in list.indices && toIndex in list.indices) {
                    val item = list.removeAt(fromIndex)
                    list.add(toIndex, item)
                    queue.copy(podcastIds = list)
                } else {
                    queue
                }
            } else {
                queue
            }
        }
        persist(updated)
    }

    suspend fun setPodcastQueues(podcastId: String, queueIds: Set<String>) = mutex.withLock {
        val updated = _queues.value.map { queue ->
            val contains = queue.podcastIds.contains(podcastId)
            val shouldContain = queueIds.contains(queue.id)
            if (contains && !shouldContain) {
                queue.copy(podcastIds = queue.podcastIds.filterNot { it == podcastId })
            } else if (!contains && shouldContain) {
                queue.copy(podcastIds = queue.podcastIds + podcastId)
            } else {
                queue
            }
        }
        persist(updated)
    }

    fun getQueuesForPodcast(podcastId: String): Set<String> {
        return _queues.value.filter { it.podcastIds.contains(podcastId) }.map { it.id }.toSet()
    }

    private fun persist(list: List<QueuePayload>) {
        prefs.edit().putString(KEY_QUEUES, gson.toJson(list)).apply()
        _queues.value = list
    }

    private fun load(): List<QueuePayload> {
        val json = prefs.getString(KEY_QUEUES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<QueuePayload>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "podcast_queues"
        private const val KEY_QUEUES = "queues"
    }
}
