package com.podcastplayer.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.podcastplayer.app.data.local.UrlDownloadEntity
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import com.podcastplayer.app.data.repository.UrlDownloadStatus
import com.podcastplayer.app.data.repository.UrlMetadata
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.data.repository.UrlValidator
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.service.UrlDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel powering the "Add from URL" feature (issue #33).
 *
 * Responsibilities:
 * - Validate / classify URLs as the user types or shares
 * - Resolve metadata (title, thumbnail, duration) before download
 * - Enqueue the chosen format and trigger the download service
 * - Expose the home-screen-visible list of completed URL downloads
 *
 * Constructed with [Application] so we can spin up the repository and call
 * [UrlDownloadService.startPump] without needing a separate Context dependency.
 */
class UrlDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UrlDownloadRepository(application)

    /** Pending/queued metadata-preview state for the AddFromUrl screen. */
    private val _previewState = MutableStateFlow<UrlPreviewState>(UrlPreviewState.Idle)
    val previewState: StateFlow<UrlPreviewState> = _previewState.asStateFlow()

    /** Currently chosen format on the AddFromUrl screen. */
    private val _selectedMediaType = MutableStateFlow(MediaType.AUDIO)
    val selectedMediaType: StateFlow<MediaType> = _selectedMediaType.asStateFlow()

    /** All completed URL downloads, newest first — fed to the home screen. */
    val completedDownloads: StateFlow<List<UrlDownloadEntity>> =
        repository.observeCompleted().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /** Items currently downloading — shown as a banner / progress card. */
    val inFlightDownloads: StateFlow<List<UrlDownloadEntity>> =
        repository.observeInFlight().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /**
     * Begin metadata extraction for [rawUrl]. Called when the user opens the
     * AddFromUrl screen with a URL (paste, share, or dedicated input).
     */
    fun loadPreview(rawUrl: String) {
        if (rawUrl.isBlank()) {
            _previewState.value = UrlPreviewState.Idle
            return
        }
        val source = UrlSource.classify(rawUrl)
        if (source == UrlSource.OTHER && !rawUrl.startsWith("http", ignoreCase = true)) {
            _previewState.value = UrlPreviewState.Error("That doesn't look like a URL we can download.")
            return
        }
        _previewState.value = UrlPreviewState.Loading(rawUrl, source)
        viewModelScope.launch {
            val metadata = repository.fetchMetadata(rawUrl)
            _previewState.value = if (metadata != null) {
                UrlPreviewState.Loaded(rawUrl, source, metadata)
            } else {
                UrlPreviewState.Error(
                    "Couldn't read that URL. It may be private, age-gated, or temporarily unavailable.",
                )
            }
        }
    }

    fun setMediaType(mediaType: MediaType) {
        _selectedMediaType.value = mediaType
    }

    /**
     * Confirm the download. Inserts a row, then starts the service so it picks
     * the row up. Returns the stable download id (so the caller can observe it).
     */
    fun confirmDownload(rawUrl: String, mediaType: MediaType, prefetched: UrlMetadata?) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            repository.enqueue(rawUrl, mediaType, prefetched)
            UrlDownloadService.startPump(app)
        }
    }

    /** Convenience overload that pulls the URL/metadata from the current preview state. */
    fun confirmCurrentDownload() {
        val state = _previewState.value as? UrlPreviewState.Loaded ?: return
        confirmDownload(state.rawUrl, _selectedMediaType.value, state.metadata)
        _previewState.value = UrlPreviewState.Idle
        _selectedMediaType.value = MediaType.AUDIO
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun cancelDownload(id: String) {
        UrlDownloadService.cancel(getApplication(), id)
    }

    fun resetPreview() {
        _previewState.value = UrlPreviewState.Idle
        _selectedMediaType.value = MediaType.AUDIO
    }

    companion object {
        /** True when [text] looks like a YouTube/X URL we can ingest. */
        fun isSupportedUrl(text: CharSequence?): Boolean = UrlValidator.isSupportedUrl(text)
    }
}

/**
 * UI state for the metadata-preview phase of "Add from URL".
 */
sealed interface UrlPreviewState {
    data object Idle : UrlPreviewState

    data class Loading(val rawUrl: String, val source: UrlSource) : UrlPreviewState

    data class Loaded(
        val rawUrl: String,
        val source: UrlSource,
        val metadata: UrlMetadata,
    ) : UrlPreviewState

    data class Error(val message: String) : UrlPreviewState
}
