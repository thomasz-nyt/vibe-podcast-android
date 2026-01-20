package com.podcastplayer.app.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.repository.PodcastRepository

class PodcastViewModelFactory(
    private val repository: PodcastRepository,
    private val downloadManager: com.podcastplayer.app.data.repository.DownloadManager,
    private val savedPodcastsStorage: SavedPodcastsStorage
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.podcastplayer.app.presentation.viewmodel.PodcastViewModel::class.java)) {
            return com.podcastplayer.app.presentation.viewmodel.PodcastViewModel(repository, downloadManager, savedPodcastsStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
