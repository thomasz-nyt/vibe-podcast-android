package com.podcastplayer.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.podcastplayer.app.data.remote.upgradeITunesArtwork
import com.podcastplayer.app.domain.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SavedPodcastsStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()

    private val _savedPodcasts = MutableStateFlow(load())
    val savedPodcasts: StateFlow<List<Podcast>> = _savedPodcasts.asStateFlow()

    suspend fun save(podcast: Podcast) {
        mutex.withLock {
            val updated = (_savedPodcasts.value + podcast).distinctBy { it.id }
            persist(updated)
        }
    }

    suspend fun remove(podcastId: String) {
        mutex.withLock {
            val updated = _savedPodcasts.value.filterNot { it.id == podcastId }
            persist(updated)
        }
    }

    suspend fun saveAll(podcasts: List<Podcast>) {
        mutex.withLock {
            val updated = (_savedPodcasts.value + podcasts).distinctBy { it.id }
            persist(updated)
        }
    }

    suspend fun move(fromIndex: Int, toIndex: Int) {
        mutex.withLock {
            val list = _savedPodcasts.value.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) return@withLock
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            persist(list)
        }
    }

    private fun persist(list: List<Podcast>) {
        val migrated = list.map { it.copy(artworkUrl = upgradeITunesArtwork(it.artworkUrl)) }
        prefs.edit().putString(KEY_PODCASTS, gson.toJson(migrated)).apply()
        _savedPodcasts.value = migrated
    }

    private fun load(): List<Podcast> {
        val json = prefs.getString(KEY_PODCASTS, null) ?: return emptyList()
        val parsed = try {
            val type = object : TypeToken<List<Podcast>>() {}.type
            gson.fromJson<List<Podcast>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val migrated = parsed.map { it.copy(artworkUrl = upgradeITunesArtwork(it.artworkUrl)) }
        if (migrated != parsed) {
            prefs.edit().putString(KEY_PODCASTS, gson.toJson(migrated)).apply()
        }
        return migrated
    }

    companion object {
        private const val PREFS_NAME = "saved_podcasts"
        private const val KEY_PODCASTS = "podcasts"
    }
}
