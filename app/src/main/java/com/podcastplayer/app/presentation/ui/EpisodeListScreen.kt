package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.EpisodesUiState
import com.podcastplayer.app.presentation.viewmodel.PlayerViewModel
import com.podcastplayer.app.presentation.viewmodel.PodcastViewModel
import com.podcastplayer.app.ui.theme.JetBrainsMono
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun EpisodeListScreen(
    podcast: Podcast?,
    podcastViewModel: PodcastViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onPlayEpisode: () -> Unit = {},
    onOpenPlayer: () -> Unit,
    isLandscape: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val episodesState by podcastViewModel.episodesUiState.collectAsState()
    val downloadedEpisodes by podcastViewModel.downloadedEpisodes.collectAsState()
    val downloadProgress by podcastViewModel.downloadProgress.collectAsState()
    val playbackProgressMap by podcastViewModel.playbackProgress.collectAsState()
    val downloadedIds = remember(downloadedEpisodes) { downloadedEpisodes.map { it.id }.toSet() }
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val scope = rememberCoroutineScope()

    if (isLandscape) {
        EpisodeListLandscape(
            podcast = podcast,
            episodesState = episodesState as? EpisodesUiState.Success,
            downloadedIds = downloadedIds,
            downloadProgress = downloadProgress,
            playbackProgressMap = playbackProgressMap,
            onBack = onBack,
            onPlayEpisode = { episode ->
                playerViewModel.playEpisode(episode, podcast?.artworkUrl)
                onPlayEpisode()
            },
            onDownload = { podcastViewModel.startDownload(it) },
            onDelete = { id -> scope.launch { podcastViewModel.deleteDownload(id) } },
            podcastViewModel = podcastViewModel,
        )
        return
    }

    Scaffold(containerColor = colors.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                VibeTopBar(
                    title = podcast?.title ?: "Episodes",
                    eyebrow = podcast?.artist,
                    onBack = onBack,
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (val state = episodesState) {
                        is EpisodesUiState.Initial,
                        is EpisodesUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = colors.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        is EpisodesUiState.Success -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 4.dp,
                                    end = 16.dp,
                                    bottom = 140.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        VibeSectionEyebrow("Episodes")
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = state.episodes.size.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                                items(state.episodes, key = { it.id }) { episode ->
                                    val isDownloaded = downloadedIds.contains(episode.id) || episode.isDownloaded
                                    val progress = downloadProgress[episode.id]
                                    val playback = playbackProgressMap[episode.id]
                                    val isCompleted = playback?.completed == true
                                    val playbackProgress = playback?.let {
                                        val duration = it.durationMs
                                        if (duration <= 0L) null
                                        else (it.positionMs.toFloat() / duration.toFloat())
                                    }

                                    EpisodeRow(
                                        episode = episode,
                                        artworkUrl = podcast?.artworkUrl,
                                        isDownloaded = isDownloaded,
                                        downloadProgress = progress,
                                        isCompleted = isCompleted,
                                        playbackProgress = playbackProgress,
                                        onClick = {
                                            playerViewModel.playEpisode(episode, podcast?.artworkUrl)
                                            onPlayEpisode()
                                        },
                                        onDownload = { podcastViewModel.startDownload(episode) },
                                        onDelete = {
                                            scope.launch { podcastViewModel.deleteDownload(episode.id) }
                                        },
                                    )
                                }
                            }
                        }

                        is EpisodesUiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                VibeSurface {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Could not load episodes",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.onSurface,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            currentEpisode?.let {
                MiniPlayerBar(
                    episode = it,
                    artworkUrl = currentArtworkUrl ?: podcast?.artworkUrl,
                    playerState = playerState,
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onOpenPlayer = onOpenPlayer,
                    onSeek = { ms -> playerViewModel.seekTo(ms) },
                    onDismiss = { playerViewModel.clearPlayer() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ─── Landscape two-column layout ──────────────────────────────
// Left: sticky 280dp meta column. Right: scrollable episode list.
@Composable
private fun EpisodeListLandscape(
    podcast: Podcast?,
    episodesState: EpisodesUiState.Success?,
    downloadedIds: Set<String>,
    downloadProgress: Map<String, Float>,
    playbackProgressMap: Map<String, com.podcastplayer.app.data.local.PlaybackProgressEntity>,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onDelete: (String) -> Unit,
    podcastViewModel: PodcastViewModel,
) {
    val colors = MaterialTheme.colorScheme
    var subscribed by remember { mutableStateOf(podcastViewModel.savedPodcasts.value.any { it.id == podcast?.id }) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // LEFT — sticky meta column
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .border(width = 1.dp, color = colors.outlineVariant, shape = RoundedCornerShape(0.dp))
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Podcast artwork
            AsyncImage(
                model = podcast?.artworkUrl,
                contentDescription = podcast?.title,
                placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                error = painterResource(R.drawable.ic_artwork_placeholder),
                fallback = painterResource(R.drawable.ic_artwork_placeholder),
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                podcast?.artist?.let { artist ->
                    Text(
                        text = artist.uppercase(),
                        fontFamily = JetBrainsMono,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.6.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = podcast?.title ?: "",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 21.sp,
                )
            }

            // Subscribe toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = if (subscribed) colors.outline else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .background(if (subscribed) androidx.compose.ui.graphics.Color.Transparent else colors.primary)
                        .clickable {
                            if (subscribed) podcastViewModel.removeSavedPodcast(podcast?.id ?: "")
                            else podcast?.let { podcastViewModel.savePodcast(it) }
                            subscribed = !subscribed
                        }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (subscribed) "Subscribed" else "Subscribe",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (subscribed) colors.onSurface else colors.onPrimary,
                    )
                }
            }
        }

        // RIGHT — scrollable episode list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 110.dp, top = 32.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Episodes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            letterSpacing = (-0.2).sp,
                        )
                        episodesState?.let { state ->
                            Text(
                                text = state.episodes.size.toString().padStart(2, '0'),
                                fontFamily = JetBrainsMono,
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            episodesState?.let { state ->
                items(state.episodes, key = { it.id }) { episode ->
                    val isDownloaded = downloadedIds.contains(episode.id) || episode.isDownloaded
                    val progress = downloadProgress[episode.id]
                    val playback = playbackProgressMap[episode.id]
                    val isCompleted = playback?.completed == true
                    val playbackProgress = playback?.let { pb ->
                        val dur = pb.durationMs
                        if (dur <= 0L) null else (pb.positionMs.toFloat() / dur.toFloat())
                    }

                    EpisodeLandscapeRow(
                        episode = episode,
                        artworkUrl = podcast?.artworkUrl,
                        isDownloaded = isDownloaded,
                        downloadProgress = progress,
                        isCompleted = isCompleted,
                        playbackProgress = playbackProgress,
                        onClick = { onPlayEpisode(episode) },
                        onDownload = { onDownload(episode) },
                        onDelete = { onDelete(episode.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeLandscapeRow(
    episode: Episode,
    artworkUrl: String?,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isCompleted: Boolean,
    playbackProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.dp, color = colors.outlineVariant, shape = RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Small play button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, colors.outline, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = colors.onSurface,
                    modifier = Modifier.size(13.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = buildMetadataLabel(episode),
                        fontFamily = JetBrainsMono,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.8.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (isDownloaded) {
                        Text(
                            text = "· SAVED",
                            fontFamily = JetBrainsMono,
                            fontSize = 9.5.sp,
                            color = colors.primary,
                        )
                    }
                    if (isCompleted) {
                        Text(
                            text = "· PLAYED",
                            fontFamily = JetBrainsMono,
                            fontSize = 9.5.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = episode.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted) colors.onSurfaceVariant else colors.onSurface,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.description?.let { desc ->
                    val clean = remember(desc) { desc.stripHtml() }
                    if (clean.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = clean,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (playbackProgress != null && playbackProgress > 0.01f && !isCompleted) {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = playbackProgress.coerceIn(0f, 1f),
                            color = colors.primary,
                            trackColor = colors.outlineVariant,
                            modifier = Modifier
                                .width(180.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                    }
                }
            }

            // Download icon button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (isDownloaded) onDelete() else onDownload() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                    contentDescription = if (isDownloaded) "Remove download" else "Download",
                    tint = if (isDownloaded) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (downloadProgress != null && !isDownloaded) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = downloadProgress.coerceIn(0f, 1f),
                color = colors.primary,
                trackColor = colors.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.outlineVariant),
    )
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    artworkUrl: String?,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isCompleted: Boolean,
    playbackProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val description = remember(episode.description) { episode.description.stripHtml() }
    var expanded by remember { mutableStateOf(false) }

    VibeSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box {
                    AsyncImage(
                        model = episode.imageUrl ?: artworkUrl,
                        contentDescription = episode.title,
                        placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                        error = painterResource(R.drawable.ic_artwork_placeholder),
                        fallback = painterResource(R.drawable.ic_artwork_placeholder),
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildMetadataLabel(episode),
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = colors.onSurfaceVariant,
                        )
                        if (isCompleted) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Played",
                                tint = colors.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "Played",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.primary)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (!isCompleted && playbackProgress != null && playbackProgress > 0.01f) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = playbackProgress.coerceIn(0f, 1f),
                    color = colors.primary,
                    trackColor = colors.outlineVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }

            if (description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            if (downloadProgress != null && !isDownloaded) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = downloadProgress.coerceIn(0f, 1f),
                    color = colors.primary,
                    trackColor = colors.outlineVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isDownloaded -> {
                        VibeChip(
                            label = "Downloaded",
                            onClick = onDelete,
                            active = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DownloadDone,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.primary,
                                )
                            },
                        )
                    }
                    downloadProgress != null -> {
                        VibeChip(
                            label = "Downloading…",
                            onClick = {},
                            enabled = false,
                        )
                    }
                    else -> {
                        VibeChip(
                            label = "Download",
                            onClick = onDownload,
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Download,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.onSurface,
                                )
                            },
                        )
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
    episode.duration?.let { parts.add(formatDuration(it)) }
    return parts.joinToString(" • ")
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
