package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.EpisodesUiState
import com.podcastplayer.app.presentation.viewmodel.PlayerViewModel
import com.podcastplayer.app.presentation.viewmodel.PodcastViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    podcast: Podcast?,
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onPlayEpisode: () -> Unit = {},
    onOpenPlayer: () -> Unit
) {
    val episodesState by podcastViewModel.episodesUiState.collectAsState()
    val downloadedEpisodes by podcastViewModel.downloadedEpisodes.collectAsState()
    val downloadedIds = remember(downloadedEpisodes) { downloadedEpisodes.map { it.id }.toSet() }
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(podcast?.title ?: "Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f, fill = true)) {
                when (val state = episodesState) {
                    is EpisodesUiState.Initial -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("Loading episodes...")
                        }
                    }
                    is EpisodesUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is EpisodesUiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.episodes) { episode ->
                                val isDownloaded = downloadedIds.contains(episode.id) || episode.isDownloaded
                                EpisodeItem(
                                    episode = episode,
                                    artworkUrl = podcast?.artworkUrl,
                                    isDownloaded = isDownloaded,
                                    onClick = {
                                        playerViewModel.playEpisode(episode, podcast?.artworkUrl)
                                        onPlayEpisode()
                                    },
                                    onDownload = {
                                        scope.launch { podcastViewModel.downloadEpisode(episode) }
                                    },
                                    onDelete = {
                                        scope.launch { podcastViewModel.deleteDownload(episode.id) }
                                    }
                                )
                            }
                        }
                    }
                    is EpisodesUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("Error: ${state.message}")
                        }
                    }
                }
            }

            currentEpisode?.let {
                MiniPlayerBar(
                    episode = it,
                    artworkUrl = podcast?.artworkUrl,
                    playerState = playerState,
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onOpenPlayer = onOpenPlayer,
                    onSeek = { playerViewModel.seekTo(it) }
                )
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: Episode,
    artworkUrl: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val description = remember(episode.description) { episode.description.stripHtml() }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = episode.imageUrl ?: artworkUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildMetadataLabel(episode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "More")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloaded) {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                    Button(onClick = onDownload) {
                        Text("Download")
                    }
                }
            }
        }
    }
}

private fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun buildMetadataLabel(episode: Episode): String {
    val parts = mutableListOf<String>()
    episode.pubDate?.let {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        parts.add(formatter.format(it))
    }
    episode.duration?.let {
        parts.add(formatDuration(it))
    }
    return parts.joinToString(" • ")
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
