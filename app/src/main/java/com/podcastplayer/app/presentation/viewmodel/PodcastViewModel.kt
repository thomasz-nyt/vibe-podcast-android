package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastplayer.app.data.local.PlaybackProgressDao
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: DownloadManager,
    private val savedPodcastsStorage: SavedPodcastsStorage,
    private val queueStorage: QueueStorage,
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

    private val _selectedQueueId = MutableStateFlow<String?>(null)
    val selectedQueueId: StateFlow<String?> = _selectedQueueId.asStateFlow()

    val queues: StateFlow<List<PodcastQueue>> = queueStorage.queues
        .map { list -> list.map { PodcastQueue(it.id, it.name, it.createdAt) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedQueuePodcasts: StateFlow<List<Podcast>> = combine(
        queueStorage.queues,
        _selectedQueueId,
        savedPodcasts
    ) { queueList, selectedId, saved ->
        val queue = queueList.firstOrNull { it.id == selectedId }
        val savedMap = saved.associateBy { it.id }
        queue?.podcastIds?.mapNotNull { savedMap[it] } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloadedEpisodesAll: StateFlow<List<Episode>> = downloadManager
        .getAllDownloadedEpisodesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedEpisodesUi: StateFlow<List<DownloadedEpisodeUi>> = combine(
        downloadedEpisodesAll,
        savedPodcasts
    ) { episodes, podcasts ->
        val map = podcasts.associateBy { it.id }
        episodes.map { episode ->
            DownloadedEpisodeUi(
                episode = episode,
                podcastTitle = map[episode.podcastId]?.title
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        observeQueues()
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

    private fun observeQueues() {
        viewModelScope.launch {
            queueStorage.queues.collect { list ->
                val selected = _selectedQueueId.value
                if (list.isNotEmpty() && (selected == null || list.none { it.id == selected })) {
                    _selectedQueueId.value = list.first().id
                }
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

    fun selectQueue(queueId: String) {
        _selectedQueueId.value = queueId
    }

    fun createQueue(name: String) {
        viewModelScope.launch {
            val newId = queueStorage.createQueue(name)
            _selectedQueueId.value = newId
        }
    }

    fun renameQueue(queueId: String, name: String) {
        viewModelScope.launch { queueStorage.renameQueue(queueId, name) }
    }

    fun deleteQueue(queueId: String) {
        viewModelScope.launch { queueStorage.deleteQueue(queueId) }
    }

    fun addPodcastToQueue(queueId: String, podcast: Podcast) {
        viewModelScope.launch {
            savedPodcastsStorage.save(podcast)
            queueStorage.addPodcast(queueId, podcast.id)
        }
    }

    fun removePodcastFromQueue(queueId: String, podcastId: String) {
        viewModelScope.launch { queueStorage.removePodcast(queueId, podcastId) }
    }

    fun movePodcastInQueue(queueId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { queueStorage.movePodcast(queueId, fromIndex, toIndex) }
    }

    fun setPodcastQueues(podcast: Podcast, queueIds: Set<String>) {
        viewModelScope.launch {
            savedPodcastsStorage.save(podcast)
            queueStorage.setPodcastQueues(podcast.id, queueIds)
        }
    }

    fun getQueueIdsForPodcast(podcastId: String): Set<String> {
        return queueStorage.getQueuesForPodcast(podcastId)
    }

    suspend fun deleteAllDownloads(): Result<Unit> {
        return downloadManager.deleteAllDownloads()
    }

    /**
     * Build a flattened list of unplayed episodes for the given podcasts, in the same podcast order.
     * Within each podcast, episodes are ordered oldest -> newest (when pubDate is available).
     */
    suspend fun buildUnplayedEpisodesForPodcastQueue(podcasts: List<Podcast>): List<Episode> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<Episode>()

            for (podcast in podcasts) {
                val feedUrl = podcast.feedUrl ?: continue

                val episodes = repository.getEpisodes(feedUrl, podcast.id).getOrNull().orEmpty()
                if (episodes.isEmpty()) continue

                val progressByEpisodeId = playbackProgressDao.getByPodcastId(podcast.id)
                    .associateBy { it.episodeId }

                val unplayed = episodes
                    .filter { ep -> progressByEpisodeId[ep.id]?.completed != true }
                    .sortedWith(compareBy<Episode> { it.pubDate == null }.thenBy { it.pubDate })

                result.addAll(unplayed)
            }

            result
        }
    }

    override fun onCleared() {
        downloadsJob?.cancel()
        savedJob?.cancel()
        progressJob?.cancel()
        super.onCleared()
    }
}

data class DownloadedEpisodeUi(
    val episode: Episode,
    val podcastTitle: String?
)

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
