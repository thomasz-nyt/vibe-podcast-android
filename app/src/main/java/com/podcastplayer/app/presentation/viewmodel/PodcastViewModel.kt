package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastplayer.app.data.local.PlaybackProgressDao
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: DownloadManager,
    private val savedPodcastsStorage: SavedPodcastsStorage,
    private val playbackProgressDao: PlaybackProgressDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Initial)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    private val _episodesUiState = MutableStateFlow<EpisodesUiState>(EpisodesUiState.Initial)
    val episodesUiState: StateFlow<EpisodesUiState> = _episodesUiState.asStateFlow()

    private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private val _downloadedEpisodes = MutableStateFlow<List<Episode>>(emptyList())
    val downloadedEpisodes: StateFlow<List<Episode>> = _downloadedEpisodes.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _savedPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val savedPodcasts: StateFlow<List<Podcast>> = _savedPodcasts.asStateFlow()

    private val _playbackProgress = MutableStateFlow<Map<String, PlaybackProgressEntity>>(emptyMap())
    val playbackProgress: StateFlow<Map<String, PlaybackProgressEntity>> = _playbackProgress.asStateFlow()

    private var downloadsJob: Job? = null
    private var savedJob: Job? = null
    private var progressJob: Job? = null

    fun searchPodcasts(query: String) {
        if (query.isBlank()) {
            _uiState.value = PodcastUiState.Initial
            return
        }
        viewModelScope.launch {
            _uiState.value = PodcastUiState.Loading
            repository.searchPodcasts(query).fold(
                onSuccess = { podcasts ->
                    _uiState.value = PodcastUiState.Success(podcasts)
                },
                onFailure = { error ->
                    _uiState.value = PodcastUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    init {
        observeSaved()
    }

    fun selectPodcast(podcast: Podcast) {
        _selectedPodcast.value = podcast
        observeDownloads(podcast)
        observePlaybackProgress(podcast)
        loadEpisodes(podcast)
    }

    private fun observeDownloads(podcast: Podcast) {
        downloadsJob?.cancel()
        downloadsJob = viewModelScope.launch {
            downloadManager.getDownloadedEpisodesFlow(podcast.id).collect { episodes ->
                _downloadedEpisodes.value = episodes
                refreshEpisodesWithDownloads()
            }
        }
    }

    private fun observePlaybackProgress(podcast: Podcast) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            playbackProgressDao.observeByPodcastId(podcast.id).collect { list ->
                _playbackProgress.value = list.associateBy { it.episodeId }
            }
        }
    }

    private fun observeSaved() {
        savedJob?.cancel()
        savedJob = viewModelScope.launch {
            savedPodcastsStorage.savedPodcasts.collect { list ->
                _savedPodcasts.value = list
            }
        }
    }

    private fun refreshEpisodesWithDownloads() {
        val currentState = _episodesUiState.value
        if (currentState is EpisodesUiState.Success) {
            val downloadedMap = _downloadedEpisodes.value.associateBy { it.id }
            val updated = currentState.episodes.map { episode ->
                downloadedMap[episode.id]?.let { downloaded ->
                    episode.copy(isDownloaded = true, localPath = downloaded.localPath ?: downloaded.audioUrl)
                } ?: episode
            }
            _episodesUiState.value = EpisodesUiState.Success(updated)
        }
    }

    private fun loadEpisodes(podcast: Podcast) {
        viewModelScope.launch {
            _episodesUiState.value = EpisodesUiState.Loading
            val feedUrl = podcast.feedUrl ?: run {
                _episodesUiState.value = EpisodesUiState.Error("No feed URL available")
                return@launch
            }
            repository.getEpisodes(feedUrl, podcast.id).fold(
                onSuccess = { episodes ->
                    _episodesUiState.value = EpisodesUiState.Success(episodes)
                    refreshEpisodesWithDownloads()
                },
                onFailure = { error ->
                    _episodesUiState.value = EpisodesUiState.Error(error.message ?: "Failed to load episodes")
                }
            )
        }
    }

    fun playEpisode(episode: Episode) {
        _selectedEpisode.value = episode
    }

    fun startDownload(episode: Episode) {
        if (_downloadProgress.value.containsKey(episode.id)) return
        _downloadProgress.value = _downloadProgress.value + (episode.id to 0f)
        viewModelScope.launch {
            val result = downloadManager.downloadEpisode(episode) { progress ->
                _downloadProgress.value = _downloadProgress.value + (episode.id to progress)
            }
            _downloadProgress.value = _downloadProgress.value - episode.id
            if (result.isSuccess) {
                refreshEpisodesWithDownloads()
            }
        }
    }

    suspend fun deleteDownload(episodeId: String): Result<Unit> {
        val result = downloadManager.deleteEpisode(episodeId)
        refreshEpisodesWithDownloads()
        return result
    }

    fun savePodcast(podcast: Podcast) {
        viewModelScope.launch { savedPodcastsStorage.save(podcast) }
    }

    fun removeSavedPodcast(podcastId: String) {
        viewModelScope.launch { savedPodcastsStorage.remove(podcastId) }
    }

    fun moveSavedPodcast(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { savedPodcastsStorage.move(fromIndex, toIndex) }
    }

    override fun onCleared() {
        downloadsJob?.cancel()
        savedJob?.cancel()
        progressJob?.cancel()
        super.onCleared()
    }
}

sealed class PodcastUiState {
    data object Initial : PodcastUiState()
    data object Loading : PodcastUiState()
    data class Success(val podcasts: List<Podcast>) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}

sealed class EpisodesUiState {
    data object Initial : EpisodesUiState()
    data object Loading : EpisodesUiState()
    data class Success(val episodes: List<Episode>) : EpisodesUiState()
    data class Error(val message: String) : EpisodesUiState()
}
