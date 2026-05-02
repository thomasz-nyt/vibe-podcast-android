package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.data.local.UrlDownloadEntity
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.ContinueListeningUi
import com.podcastplayer.app.ui.theme.JetBrainsMono
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    subscriptions: List<Podcast>,
    continueListening: List<ContinueListeningUi>,
    urlDownloads: List<UrlDownloadEntity>,
    urlInFlight: List<UrlDownloadEntity>,
    currentEpisode: Episode?,
    currentArtworkUrl: String?,
    playerState: PlayerState,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenSearch: () -> Unit,
    onAddFromUrl: () -> Unit,
    onPlayUrlDownload: (UrlDownloadEntity) -> Unit,
    onDeleteUrlDownload: (String) -> Unit,
    onCancelUrlDownload: (String) -> Unit,
    onPlayEpisode: (Episode, String?) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismissPlayer: () -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            HomeHeader(now = now)

            // Quick-action row: "Add from URL" entry point.
            QuickActionRow(onAddFromUrl = onAddFromUrl)

            // Currently downloading items, if any.
            if (urlInFlight.isNotEmpty()) {
                SectionHeader(label = "Saving from URL")
                InFlightColumn(
                    items = urlInFlight,
                    onCancel = onCancelUrlDownload,
                )
                Spacer(Modifier.height(4.dp))
            }

            if (continueListening.isNotEmpty()) {
                SectionHeader(label = "Continue listening")
                ContinueListeningRow(
                    items = continueListening,
                    onPlay = { item ->
                        onPlayEpisode(item.episode, item.podcastArtworkUrl)
                    },
                )
                Spacer(Modifier.height(4.dp))
            }

            if (urlDownloads.isNotEmpty()) {
                SectionHeader(
                    label = "Saved from URL",
                    count = urlDownloads.size,
                )
                UrlDownloadsRow(
                    items = urlDownloads,
                    onPlay = onPlayUrlDownload,
                    onDelete = onDeleteUrlDownload,
                )
                Spacer(Modifier.height(4.dp))
            }

            if (subscriptions.isEmpty() && urlDownloads.isEmpty() && urlInFlight.isEmpty()) {
                VibeEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No subscriptions yet",
                    subtitle = "Search for podcasts, or paste a YouTube / X link to save offline.",
                    action = {
                        VibePrimaryPill(label = "Browse podcasts", onClick = onOpenSearch)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else if (subscriptions.isNotEmpty()) {
                SectionHeader(
                    label = "Your subscriptions",
                    count = subscriptions.size,
                )
                SubscriptionsGrid(
                    podcasts = subscriptions,
                    onOpenPodcast = onOpenPodcast,
                )
            }

            Spacer(Modifier.height(160.dp))
        }

        currentEpisode?.let { episode ->
            MiniPlayerBar(
                episode = episode,
                artworkUrl = currentArtworkUrl,
                playerState = playerState,
                onPlayPause = onPlayPause,
                onOpenPlayer = onOpenPlayer,
                onSeek = onSeek,
                onDismiss = onDismissPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HomeHeader(now: LocalDateTime) {
    val colors = MaterialTheme.colorScheme
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = statusPad + 14.dp, bottom = 6.dp),
    ) {
        Text(
            text = formatDateEyebrow(now),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.8.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = greeting(now.hour),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
    }
}

/** Quick-actions strip directly under the greeting. Currently just "Add from URL" (issue #33). */
@Composable
private fun QuickActionRow(onAddFromUrl: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VibeChip(
            label = "Add from URL",
            onClick = onAddFromUrl,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            },
        )
    }
}

@Composable
private fun SectionHeader(label: String, count: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString().padStart(2, '0'),
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContinueListeningRow(
    items: List<ContinueListeningUi>,
    onPlay: (ContinueListeningUi) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            ContinueListeningCard(item = item, onClick = { onPlay(item) })
        }
    }
}

@Composable
private fun ContinueListeningCard(item: ContinueListeningUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .width(248.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.episode.imageUrl ?: item.podcastArtworkUrl,
            contentDescription = item.episode.title,
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(8.dp)),
            placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            error = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            item.podcastTitle?.let {
                Text(
                    text = it.uppercase(),
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.episode.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = item.progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.primary,
                trackColor = colors.outline,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatRemaining(item.remainingMs)} left",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontally-scrolling row of completed URL downloads. Each card shows
 * thumbnail + source badge + title + format chip and plays on tap.
 */
@Composable
private fun UrlDownloadsRow(
    items: List<UrlDownloadEntity>,
    onPlay: (UrlDownloadEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            UrlDownloadCard(
                item = item,
                onPlay = { onPlay(item) },
                onDelete = { onDelete(item.id) },
            )
        }
    }
}

@Composable
private fun UrlDownloadCard(
    item: UrlDownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val mediaType = MediaType.fromTag(item.mediaType)
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .padding(10.dp),
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant),
                placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            )
            // Play overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Format badge (top-right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (mediaType == MediaType.VIDEO) Icons.Outlined.Movie else Icons.Outlined.Audiotrack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (mediaType == MediaType.VIDEO) "VIDEO" else "AUDIO",
                    fontFamily = JetBrainsMono,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.0.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = UrlSource.fromTag(item.source).displayName.uppercase(),
                fontFamily = JetBrainsMono,
                fontSize = 9.5.sp,
                letterSpacing = 1.4.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onDelete),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
        if (!item.uploader.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.uploader,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Currently-downloading items shown in a column with progress + cancel. */
@Composable
private fun InFlightColumn(
    items: List<UrlDownloadEntity>,
    onCancel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            InFlightCard(item = item, onCancel = { onCancel(item.id) })
        }
    }
}

@Composable
private fun InFlightCard(item: UrlDownloadEntity, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant),
                placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${UrlSource.fromTag(item.source).displayName.uppercase()} · ${item.mediaType.uppercase()}",
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Cancel",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onCancel),
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = (item.progressPercent / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = colors.primary,
            trackColor = colors.outline,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = statusLabel(item),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscriptionsGrid(
    podcasts: List<Podcast>,
    onOpenPodcast: (Podcast) -> Unit,
) {
    val rows = podcasts.chunked(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { podcast ->
                    SubscriptionTile(
                        podcast = podcast,
                        onClick = { onOpenPodcast(podcast) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad last row so tiles keep width
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SubscriptionTile(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = podcast.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceVariant),
            placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            error = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
            fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = podcast.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = podcast.artist.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.8.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── helpers ────────────────────────────────────────────────────────

private fun greeting(hour: Int): String = when {
    hour < 12 -> "Good morning."
    hour < 18 -> "Good afternoon."
    else -> "Good evening."
}

private fun formatDateEyebrow(now: LocalDateTime): String {
    val dow = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
    val mon = now.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
    val day = now.dayOfMonth.toString().padStart(2, '0')
    val hh = now.hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    return "$dow · $mon $day · $hh:$mm"
}

private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun statusLabel(item: UrlDownloadEntity): String {
    val status = com.podcastplayer.app.data.repository.UrlDownloadStatus.entries
        .firstOrNull { it.name == item.status }
    return when (status) {
        com.podcastplayer.app.data.repository.UrlDownloadStatus.QUEUED -> "QUEUED"
        com.podcastplayer.app.data.repository.UrlDownloadStatus.EXTRACTING_METADATA -> "READING METADATA"
        com.podcastplayer.app.data.repository.UrlDownloadStatus.DOWNLOADING ->
            "DOWNLOADING · ${item.progressPercent.toInt()}%"
        else -> item.status.uppercase()
    }
}
