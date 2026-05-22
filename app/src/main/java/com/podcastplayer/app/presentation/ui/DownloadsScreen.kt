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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.ui.theme.JetBrainsMono

/**
 * Shape for a single row in the Downloads tab. Sources include RSS podcast
 * downloads ([Kind.PODCAST]) and "Add from URL" downloads ([Kind.URL_AUDIO] /
 * [Kind.URL_VIDEO]). The NavHost is responsible for building this list by
 * combining the two underlying ViewModels — keeping the screen oblivious to
 * the persistence split between Room tables.
 */
data class DownloadEntryUi(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    /** Playable Episode — used by the play handler regardless of source. */
    val episode: Episode,
) {
    enum class Kind { PODCAST, URL_AUDIO, URL_VIDEO }
}

@Composable
fun DownloadsScreen(
    entries: List<DownloadEntryUi>,
    onPlay: (DownloadEntryUi) -> Unit,
    onDelete: (DownloadEntryUi) -> Unit,
    onDeleteAll: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VibeTopBar(
                title = "Downloads",
                eyebrow = "Offline",
                actions = {
                    if (entries.isNotEmpty()) {
                        VibeChip(
                            label = "Remove all",
                            onClick = { showDeleteAllDialog = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                },
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VibeEmptyState(
                        icon = Icons.Outlined.CloudDownload,
                        title = "No downloads",
                        subtitle = "Save podcast episodes or paste YouTube / X links to listen offline.",
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VibeSectionEyebrow(text = "Saved", modifier = Modifier.weight(1f))
                    Text(
                        text = "${entries.size}",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        DownloadRow(
                            entry = entry,
                            onPlay = { onPlay(entry) },
                            onDelete = { onDelete(entry) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Remove all downloads") },
            text = { Text("This will delete every downloaded file from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    showDeleteAllDialog = false
                }) {
                    Text("Remove all", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntryUi,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline, shape)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp)),
            placeholder = painterResource(R.drawable.ic_artwork_placeholder),
            error = painterResource(R.drawable.ic_artwork_placeholder),
            fallback = painterResource(R.drawable.ic_artwork_placeholder),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(entry.kind)
                if (!entry.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = entry.subtitle.uppercase(),
                        fontFamily = JetBrainsMono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        VibeCircleIconButton(
            icon = Icons.Outlined.Delete,
            description = "Delete download",
            onClick = onDelete,
            size = 36.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(6.dp))
        VibeCircleIconButton(
            icon = Icons.Default.PlayArrow,
            description = "Play",
            onClick = onPlay,
            size = 40.dp,
            iconSize = 22.dp,
            tinted = true,
        )
    }
}

@Composable
private fun KindBadge(kind: DownloadEntryUi.Kind) {
    val colors = MaterialTheme.colorScheme
    val (label, icon) = when (kind) {
        DownloadEntryUi.Kind.PODCAST -> "PODCAST" to Icons.Outlined.Podcasts
        DownloadEntryUi.Kind.URL_AUDIO -> "AUDIO" to Icons.Outlined.Audiotrack
        DownloadEntryUi.Kind.URL_VIDEO -> "VIDEO" to Icons.Outlined.Movie
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = colors.onSurfaceVariant,
        )
    }
}
