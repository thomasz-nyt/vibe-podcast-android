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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.podcastplayer.app.data.local.UrlDownloadEntity
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.presentation.viewmodel.RestoreDownloadsState
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
    /** On-disk byte count for this entry. 0 if unknown. */
    val sizeBytes: Long = 0L,
) {
    enum class Kind { PODCAST, URL_AUDIO, URL_VIDEO }
}

/** Human-readable byte count: "456 KB", "1.2 GB", etc. Binary (1024-based) units. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0 || value >= 100.0) {
        "%.0f %s".format(value, units[unit])
    } else {
        "%.1f %s".format(value, units[unit])
    }
}

@Composable
fun DownloadsScreen(
    entries: List<DownloadEntryUi>,
    failedUrlDownloads: List<UrlDownloadEntity> = emptyList(),
    totalBytes: Long,
    restoreState: RestoreDownloadsState = RestoreDownloadsState.Idle,
    maintenanceMessage: String? = null,
    onPlay: (DownloadEntryUi) -> Unit,
    onDelete: (DownloadEntryUi) -> Unit,
    onRetryUrlDownload: (String) -> Unit = {},
    onDeleteUrlDownload: (String) -> Unit = {},
    onDeleteAll: () -> Unit,
    onRestoreDownloads: () -> Unit = {},
    onCleanupDuplicates: () -> Unit = {},
    onDismissRestoreResult: () -> Unit = {},
    onDismissMaintenanceMessage: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var maintenanceMenuOpen by remember { mutableStateOf(false) }
    val eyebrow = if (entries.isEmpty()) {
        "Offline"
    } else {
        "Offline · ${entries.size} item${if (entries.size == 1) "" else "s"} · ${formatBytes(totalBytes)}"
    }
    // Offer restore prominently when the library looks freshly wiped (typical
    // post-reinstall state); it stays reachable from the ⋯ menu otherwise.
    val showRestoreCard = restoreState !is RestoreDownloadsState.Idle ||
        (entries.isEmpty() && failedUrlDownloads.isEmpty())

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VibeTopBar(
                title = "Downloads",
                eyebrow = eyebrow,
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
                        Spacer(Modifier.width(8.dp))
                    }
                    Box {
                        VibeCircleIconButton(
                            icon = Icons.Outlined.MoreHoriz,
                            description = "Download maintenance",
                            onClick = { maintenanceMenuOpen = true },
                        )
                        DropdownMenu(
                            expanded = maintenanceMenuOpen,
                            onDismissRequest = { maintenanceMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Restore previous downloads") },
                                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                                onClick = { maintenanceMenuOpen = false; onRestoreDownloads() },
                            )
                            DropdownMenuItem(
                                text = { Text("Clean up duplicate files") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                                onClick = { maintenanceMenuOpen = false; onCleanupDuplicates() },
                            )
                        }
                    }
                },
            )

            if (maintenanceMessage != null) {
                MaintenanceNotice(message = maintenanceMessage, onDismiss = onDismissMaintenanceMessage)
            }

            if (entries.isEmpty() && failedUrlDownloads.isEmpty()) {
                if (showRestoreCard) {
                    RestoreDownloadsCard(
                        state = restoreState,
                        onRestore = onRestoreDownloads,
                        onDismiss = onDismissRestoreResult,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
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
                    if (restoreState !is RestoreDownloadsState.Idle) {
                        item(key = "restore-card") {
                            RestoreDownloadsCard(
                                state = restoreState,
                                onRestore = onRestoreDownloads,
                                onDismiss = onDismissRestoreResult,
                            )
                        }
                    }
                    if (failedUrlDownloads.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VibeSectionEyebrow(text = "Needs attention", modifier = Modifier.weight(1f))
                                Text(
                                    text = "${failedUrlDownloads.size}",
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(failedUrlDownloads, key = { "failed:${it.id}" }) { item ->
                            FailedUrlDownloadRow(
                                item = item,
                                onRetry = { onRetryUrlDownload(item.id) },
                                onDelete = { onDeleteUrlDownload(item.id) },
                            )
                        }
                    }
                    if (entries.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
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
                        }
                    }
                    items(entries, key = { "saved:${it.id}" }) { entry ->
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
private fun FailedUrlDownloadRow(
    item: UrlDownloadEntity,
    onRetry: () -> Unit,
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
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
                KindBadge(
                    if (item.mediaType == "video") DownloadEntryUi.Kind.URL_VIDEO else DownloadEntryUi.Kind.URL_AUDIO
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = UrlSource.fromTag(item.source).displayName.uppercase(),
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.errorMessage ?: item.status.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        VibeCircleIconButton(
            icon = Icons.Outlined.Delete,
            description = "Delete failed download",
            onClick = onDelete,
            size = 36.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(6.dp))
        VibeCircleIconButton(
            icon = Icons.Outlined.Refresh,
            description = "Retry download",
            onClick = onRetry,
            size = 40.dp,
            iconSize = 20.dp,
            tinted = true,
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

/**
 * Offer/status card for relinking media left on the device by a previous
 * install. The [state] machine keeps this one card serving as the button,
 * the progress row, and the result summary.
 */
@Composable
private fun RestoreDownloadsCard(
    state: RestoreDownloadsState,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primaryContainer)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is RestoreDownloadsState.Running) {
                CircularProgressIndicator(
                    color = colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val (title, subtitle) = when (state) {
                    is RestoreDownloadsState.Idle ->
                        "Reinstalled the app?" to
                            "Relink downloads already on this device instead of re-downloading them."
                    is RestoreDownloadsState.Running ->
                        "Restoring downloads…" to
                            "Scanning your media folders and matching episodes."
                    is RestoreDownloadsState.Done ->
                        if (state.restoredEpisodes == 0 && state.importedClips == 0) {
                            "Nothing to restore" to "No previously downloaded files were found."
                        } else {
                            "Restore complete" to buildString {
                                append("Relinked ${state.restoredEpisodes} episode")
                                if (state.restoredEpisodes != 1) append("s")
                                if (state.importedClips > 0) {
                                    append(" · imported ${state.importedClips} clip")
                                    if (state.importedClips != 1) append("s")
                                }
                                append(" without re-downloading.")
                            }
                        }
                    is RestoreDownloadsState.Failed ->
                        "Restore failed" to state.message
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colors.onPrimaryContainer,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is RestoreDownloadsState.Idle -> VibeChip(label = "Restore", onClick = onRestore)
                is RestoreDownloadsState.Running -> Unit
                is RestoreDownloadsState.Done,
                is RestoreDownloadsState.Failed -> VibeChip(label = "Dismiss", onClick = onDismiss)
            }
        }
    }
}

/** One-line dismissible status strip for cleanup results ("Removed 4 files" etc.). */
@Composable
private fun MaintenanceNotice(message: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "DISMISS",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = colors.primary,
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
