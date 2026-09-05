package com.podcastplayer.app.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.podcastplayer.app.data.local.MediaStoreScanner
import com.podcastplayer.app.data.local.PlaybackProgressDao
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.ManualDownloadRepository
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.data.repository.UrlDownloadRepository

class PodcastViewModelFactory(
    private val repository: PodcastRepository,
    private val downloadManager: DownloadManager,
    private val manualDownloadRepository: ManualDownloadRepository,
    private val savedPodcastsStorage: SavedPodcastsStorage,
    private val queueStorage: QueueStorage,
    private val playbackProgressDao: PlaybackProgressDao,
    private val urlDownloadRepository: UrlDownloadRepository? = null,
    private val mediaScanner: MediaStoreScanner? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(com.podcastplayer.app.presentation.viewmodel.PodcastViewModel::class.java)) {
            return com.podcastplayer.app.presentation.viewmodel.PodcastViewModel(
                repository,
                downloadManager,
                manualDownloadRepository,
                savedPodcastsStorage,
                queueStorage,
                playbackProgressDao,
                urlDownloadRepository,
                mediaScanner,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
