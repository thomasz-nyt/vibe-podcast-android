package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastplayer.app.data.local.OpmlExportSummary
import com.podcastplayer.app.data.local.OpmlImportData
import com.podcastplayer.app.data.local.OpmlManager
import com.podcastplayer.app.data.local.PlaybackProgressDao
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastQueue
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: DownloadManager,
    private val savedPodcastsStorage: SavedPodcastsStorage,
    private val queueStorage: QueueStorage,
    private val playbackProgressDao: PlaybackProgressDao,
    private val urlDownloadRepository: UrlDownloadRepository? = null,
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

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val _savedPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val savedPodcasts: StateFlow<List<Podcast>> = _savedPodcasts.asStateFlow()

    private val _selectedQueueId = MutableStateFlow<String?>(null)
    val selectedQueueId: StateFlow<String?> = _selectedQueueId.asStateFlow()

    val queues: StateFlow<List<PodcastQueue>> = queueStorage.queues
        .map { list -> list.map { PodcastQueue(it.id, it.name, it.createdAt, it.autoDownload) } }
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
                podcastTitle = map[episode.podcastId]?.title,
                podcastArtworkUrl = map[episode.podcastId]?.artworkUrl
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All completed URL downloads (`url:<id>` mediaId space). Empty if the repository wasn't injected. */
    private val urlCompletedFlow = urlDownloadRepository?.observeCompleted()
        ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /**
     * In-progress media for the Home "Continue listening" shelf. Joins playback-progress
     * rows against both RSS downloads and URL-downloaded clips, most-recent first.
     * Rows that don't match either source are dropped (we'd have no metadata to render).
     */
    val continueListening: StateFlow<List<ContinueListeningUi>> = combine(
        downloadedEpisodesUi,
        urlCompletedFlow,
        playbackProgressDao.observeInProgress(),
    ) { downloads, urlDownloads, progress ->
        val rssById = downloads.associateBy { it.episode.id }
        val urlById = urlDownloads.associateBy { "url:${it.id}" }
        progress.mapNotNull { entry ->
            val fraction = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0L)
            rssById[entry.episodeId]?.let { ui ->
                return@mapNotNull ContinueListeningUi(
                    episode = ui.episode,
                    podcastTitle = ui.podcastTitle,
                    podcastArtworkUrl = ui.podcastArtworkUrl,
                    progressFraction = fraction,
                    remainingMs = remainingMs,
                )
            }
            val urlEntity = urlById[entry.episodeId] ?: return@mapNotNull null
            val episode = urlDownloadRepository?.toEpisode(urlEntity) ?: return@mapNotNull null
            ContinueListeningUi(
                episode = episode,
                podcastTitle = urlEntity.uploader ?: urlEntity.source.uppercase(),
                podcastArtworkUrl = urlEntity.thumbnailUrl,
                progressFraction = fraction,
                remainingMs = remainingMs,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playbackProgress = MutableStateFlow<Map<String, PlaybackProgressEntity>>(emptyMap())
    val playbackProgress: StateFlow<Map<String, PlaybackProgressEntity>> = _playbackProgress.asStateFlow()

    private val _opmlResult = MutableStateFlow<OpmlResult?>(null)
    val opmlResult: StateFlow<OpmlResult?> = _opmlResult.asStateFlow()

    private val _feedPreviewState = MutableStateFlow<FeedPreviewState>(FeedPreviewState.Idle)
    val feedPreviewState: StateFlow<FeedPreviewState> = _feedPreviewState.asStateFlow()

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

    private fun loadEpisodes(podcast: Podcast, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _episodesUiState.value = EpisodesUiState.Loading
            val feedUrl = podcast.feedUrl ?: run {
                _episodesUiState.value = EpisodesUiState.Error("No feed URL available")
                return@launch
            }
            repository.getEpisodes(feedUrl, podcast.id, forceRefresh).fold(
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
        // Surface the podcast title to DownloadManager so the MediaStore display
        // name reads as "<podcast> - <episode>.mp3" when browsed from VLC / Files.
        val podcastTitle = _savedPodcasts.value.firstOrNull { it.id == episode.podcastId }?.title
            ?: _selectedPodcast.value?.takeIf { it.id == episode.podcastId }?.title
        viewModelScope.launch {
            val result = downloadManager.downloadEpisode(episode, podcastTitle) { progress ->
                _downloadProgress.value = _downloadProgress.value + (episode.id to progress)
            }
            _downloadProgress.value = _downloadProgress.value - episode.id
            if (result.isSuccess) {
                refreshEpisodesWithDownloads()
            } else {
                val reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    ?: "Unknown error"
                _downloadError.value = "Download failed: $reason"
            }
        }
    }

    fun clearDownloadError() {
        _downloadError.value = null
    }

    suspend fun deleteDownload(episodeId: String): Result<Unit> {
        val result = downloadManager.deleteEpisode(episodeId)
        refreshEpisodesWithDownloads()
        return result
    }

    /**
     * Mark an episode as played without actually playing it. Stamps the
     * playback_progress row so it appears in the "completed" filter and
     * doesn't get auto-downloaded again.
     */
    fun markEpisodePlayed(episode: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            val durationMs = episode.duration ?: 0L
            val now = System.currentTimeMillis()
            playbackProgressDao.upsert(
                PlaybackProgressEntity(
                    episodeId = episode.id,
                    podcastId = episode.podcastId,
                    positionMs = durationMs,
                    durationMs = durationMs,
                    completed = true,
                    lastPlayedAtMs = now,
                    updatedAtMs = now,
                )
            )
        }
    }

    /**
     * Reset the "played" state for an episode. Drops the progress row entirely
     * so it surfaces back in feeds and is eligible for auto-download.
     */
    fun markEpisodeUnplayed(episode: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            playbackProgressDao.deleteByEpisodeId(episode.id)
        }
    }

    /** Re-fetch the RSS feed for the currently-selected podcast, bypassing the feed cache. */
    fun refreshSelectedPodcastEpisodes() {
        val podcast = _selectedPodcast.value ?: return
        loadEpisodes(podcast, forceRefresh = true)
    }

    fun savePodcast(podcast: Podcast) {
        viewModelScope.launch { savedPodcastsStorage.save(podcast) }
    }

    fun loadFeedPreview(feedUrl: String) {
        val normalized = feedUrl.trim()
        if (normalized.isBlank()) {
            _feedPreviewState.value = FeedPreviewState.Idle
            return
        }
        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            _feedPreviewState.value = FeedPreviewState.Error("Enter a valid RSS feed URL.")
            return
        }
        viewModelScope.launch {
            _feedPreviewState.value = FeedPreviewState.Loading(normalized)
            repository.getPodcastFromFeed(normalized).fold(
                onSuccess = { podcast ->
                    _feedPreviewState.value = FeedPreviewState.Loaded(podcast)
                },
                onFailure = { error ->
                    _feedPreviewState.value = FeedPreviewState.Error(
                        error.message ?: "Could not read that feed.",
                    )
                },
            )
        }
    }

    fun confirmFeedPreview() {
        val podcast = (_feedPreviewState.value as? FeedPreviewState.Loaded)?.podcast ?: return
        viewModelScope.launch {
            savedPodcastsStorage.save(podcast)
            _feedPreviewState.value = FeedPreviewState.Saved(podcast)
        }
    }

    fun resetFeedPreview() {
        _feedPreviewState.value = FeedPreviewState.Idle
    }

    fun removeSavedPodcast(podcastId: String) {
        viewModelScope.launch { savedPodcastsStorage.remove(podcastId) }
    }

    fun setPodcastAutoDownload(podcastId: String, enabled: Boolean) {
        viewModelScope.launch { savedPodcastsStorage.setAutoDownload(podcastId, enabled) }
    }

    fun setQueueAutoDownload(queueId: String, enabled: Boolean) {
        viewModelScope.launch { queueStorage.setAutoDownload(queueId, enabled) }
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

    suspend fun exportOpml(outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            OpmlManager.writeOpml(_savedPodcasts.value, queueStorage.queues.value, outputStream)
        }.fold(
            onSuccess = { summary ->
                _opmlResult.value = OpmlResult.ExportSuccess(summary.podcastCount, summary.queueCount)
            },
            onFailure = { e -> _opmlResult.value = OpmlResult.Error(e.message ?: "Export failed") }
        )
    }

    suspend fun importOpml(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            OpmlManager.readOpml(inputStream)
        }.fold(
            onSuccess = { data ->
                savedPodcastsStorage.saveAll(data.podcasts)
                queueStorage.mergeImportedQueues(data.queues)
                _opmlResult.value = OpmlResult.ImportSuccess(data.podcasts.size, data.queues.size)
            },
            onFailure = { e -> _opmlResult.value = OpmlResult.Error(e.message ?: "Import failed") }
        )
    }

    fun clearOpmlResult() {
        _opmlResult.value = null
    }

    /**
     * Build the "Play Queue" playlist per docs/specs/004-podcast-queue-play.md section 4-5:
     * for each podcast in queue order, include ALL unplayed (non-completed) episodes,
     * ordered oldest -> newest within that podcast. Queue order across podcasts is preserved
     * in the flattened result. Per-podcast feed fetches run concurrently (independent network
     * calls), bounded by [MAX_CONCURRENT_QUEUE_FEED_FETCHES].
     */
    suspend fun buildUnplayedEpisodesForPodcastQueue(podcasts: List<Podcast>): List<Episode> {
        return buildUnplayedEpisodesForQueue(
            podcasts = podcasts,
            fetchEpisodes = { feedUrl, podcastId -> repository.getEpisodes(feedUrl, podcastId) },
            fetchProgress = { podcastId -> playbackProgressDao.getByPodcastId(podcastId) },
        )
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
    val podcastTitle: String?,
    val podcastArtworkUrl: String?
)

data class ContinueListeningUi(
    val episode: Episode,
    val podcastTitle: String?,
    val podcastArtworkUrl: String?,
    val progressFraction: Float,
    val remainingMs: Long,
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

sealed class OpmlResult {
    data class ExportSuccess(val podcastCount: Int, val queueCount: Int) : OpmlResult()
    data class ImportSuccess(val podcastCount: Int, val queueCount: Int) : OpmlResult()
    data class Error(val message: String) : OpmlResult()
}

sealed class FeedPreviewState {
    data object Idle : FeedPreviewState()
    data class Loading(val url: String) : FeedPreviewState()
    data class Loaded(val podcast: Podcast) : FeedPreviewState()
    data class Saved(val podcast: Podcast) : FeedPreviewState()
    data class Error(val message: String) : FeedPreviewState()
}

/** Bound on simultaneous RSS feed fetches when building a queue playlist. */
private const val MAX_CONCURRENT_QUEUE_FEED_FETCHES = 4

/**
 * Pure, dependency-free implementation of the "Play Queue" playlist builder (see
 * [PodcastViewModel.buildUnplayedEpisodesForPodcastQueue]). Extracted to a top-level
 * function — decoupled from [PodcastRepository]/[PlaybackProgressDao] — so it can be
 * unit tested without the Context-backed storage classes [PodcastViewModel] otherwise
 * requires (there is no Robolectric/Mockito in this project's test setup).
 *
 * Feed fetches for different podcasts are independent network calls, so they run
 * concurrently (bounded by [MAX_CONCURRENT_QUEUE_FEED_FETCHES]) while queue order is
 * preserved in the flattened result: `awaitAll()` on a `List<Deferred<T>>` returns
 * results in the original list order, not completion order.
 */
internal suspend fun buildUnplayedEpisodesForQueue(
    podcasts: List<Podcast>,
    fetchEpisodes: suspend (feedUrl: String, podcastId: String) -> Result<List<Episode>>,
    fetchProgress: suspend (podcastId: String) -> List<PlaybackProgressEntity>,
): List<Episode> = withContext(Dispatchers.IO) {
    val semaphore = Semaphore(MAX_CONCURRENT_QUEUE_FEED_FETCHES)
    coroutineScope {
        podcasts
            .filter { !it.feedUrl.isNullOrBlank() }
            .map { podcast ->
                async {
                    semaphore.withPermit {
                        unplayedEpisodesForPodcast(podcast, fetchEpisodes, fetchProgress)
                    }
                }
            }
            .awaitAll()
            .flatten()
    }
}

/**
 * All non-completed episodes for a single [podcast], oldest -> newest by [Episode.pubDate]
 * (episodes with no pubDate sort last — we don't know how old they are). "Unplayed" means
 * no playback_progress row, or a row with `completed == false`; partially-played episodes
 * are included, per spec section 5.
 */
private suspend fun unplayedEpisodesForPodcast(
    podcast: Podcast,
    fetchEpisodes: suspend (feedUrl: String, podcastId: String) -> Result<List<Episode>>,
    fetchProgress: suspend (podcastId: String) -> List<PlaybackProgressEntity>,
): List<Episode> {
    val feedUrl = podcast.feedUrl ?: return emptyList()
    val episodes = fetchEpisodes(feedUrl, podcast.id).getOrNull().orEmpty()
    if (episodes.isEmpty()) return emptyList()

    val progressByEpisodeId = fetchProgress(podcast.id).associateBy { it.episodeId }

    return episodes
        .filter { episode -> progressByEpisodeId[episode.id]?.completed != true }
        .sortedBy { it.pubDate?.time ?: Long.MAX_VALUE }
        .map { episode ->
            if (episode.imageUrl == null && podcast.artworkUrl != null) {
                episode.copy(imageUrl = podcast.artworkUrl)
            } else {
                episode
            }
        }
}
