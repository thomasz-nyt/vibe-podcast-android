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
import androidx.compose.material3.Checkbox
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
import com.podcastplayer.app.data.local.DuplicateCleanupPlan
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.presentation.viewmodel.RestoreDownloadsState
import com.podcastplayer.app.presentation.viewmodel.LegacyRestoreMatch
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
    /**
     * Remove every download from the app's list. [deleteFiles] = true also deletes
     * the media files from the phone (including prior-install duplicates); false
     * keeps the files on disk so "Restore previous downloads" can relink them later
     * without any re-downloading.
     */
    onDeleteAll: (deleteFiles: Boolean) -> Unit,
    onRestoreDownloads: () -> Unit = {},
    onReviewLegacyMatches: () -> Unit = {},
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
                        onReviewLegacy = onReviewLegacyMatches,
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
                                onReviewLegacy = onReviewLegacyMatches,
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
        // remember{} inside the conditional resets the checkbox to the safe default
        // (really delete) every time the dialog is opened.
        var deleteFilesToo by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Remove all downloads") },
            text = {
                Column {
                    Text(
                        if (deleteFilesToo) {
                            "Every download is removed from the list and its media file is " +
                                "deleted from this phone, including leftover duplicates."
                        } else {
                            "Downloads are removed from the list, but the media files stay " +
                                "on this phone — use \"Restore previous downloads\" to bring " +
                                "them back later without re-downloading."
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFilesToo = !deleteFilesToo },
                    ) {
                        Checkbox(
                            checked = deleteFilesToo,
                            onCheckedChange = { deleteFilesToo = it },
                        )
                        Text(
                            text = "Also delete media files from phone",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll(deleteFilesToo)
                    showDeleteAllDialog = false
                }) {
                    Text(
                        if (deleteFilesToo) "Remove all" else "Remove, keep files",
                        fontWeight = FontWeight.SemiBold,
                    )
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
    onReviewLegacy: () -> Unit,
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
                        "Reinstalled or cleared your list?" to
                            "Relink downloads already on this device instead of re-downloading them."
                    is RestoreDownloadsState.Running ->
                        "Restoring downloads…" to
                            "Scanning your media folders and matching episodes."
                    is RestoreDownloadsState.ReviewLegacy ->
                        "Legacy matches need review" to
                            "${state.suggestions.size} title-based suggestion" +
                                (if (state.suggestions.size == 1) "" else "s") +
                                " found. Nothing is linked without your confirmation."
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
                is RestoreDownloadsState.ReviewLegacy ->
                    VibeChip(label = "Review", onClick = onReviewLegacy)
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

@Composable
fun LegacyRestoreReviewDialog(
    matches: List<LegacyRestoreMatch>,
    onConfirm: (List<LegacyRestoreMatch>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(matches) { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review legacy matches") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                item {
                    Text(
                        "These files have no stable identity. Select only matches you recognize; " +
                            "confirmed files will be renamed before they are linked.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(matches, key = { it.file.uriString }) { match ->
                    val checked = match.file.uriString in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - match.file.uriString
                                else selected + match.file.uriString
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (checked) selected - match.file.uriString
                                else selected + match.file.uriString
                            },
                        )
                        Column {
                            Text(match.episode.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${match.file.displayName} · ${formatBytes(match.file.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(matches.filter { it.file.uriString in selected }) },
            ) { Text("Confirm selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep unidentified") } },
    )
}

@Composable
fun DuplicateCleanupDialog(
    plan: DuplicateCleanupPlan,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(plan) { mutableStateOf(plan.defaultDeleteUris.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review duplicate files") },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                if (plan.confirmed.isNotEmpty()) {
                    item { Text("Confirmed duplicates", fontWeight = FontWeight.SemiBold) }
                    plan.confirmed.forEach { group ->
                        items(group.items, key = { "confirmed:${it.file.uriString}" }) { item ->
                            DuplicateChoiceRow(item, item.file.uriString in selected) { checked ->
                                selected = if (checked) selected + item.file.uriString
                                else selected - item.file.uriString
                            }
                        }
                    }
                }
                if (plan.ambiguous.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("Ambiguous legacy groups", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Same title, different content. Nothing here is selected automatically.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    plan.ambiguous.forEach { group ->
                        item { Text(group.normalizedTitle, style = MaterialTheme.typography.labelMedium) }
                        items(group.items, key = { "ambiguous:${it.file.uriString}" }) { item ->
                            val isChecked = item.file.uriString in selected
                            val selectionEnabled = group.canToggleDeletion(item.file.uriString, selected)
                            DuplicateChoiceRow(
                                item = item,
                                checked = isChecked,
                                enabled = selectionEnabled,
                            ) { newChecked ->
                                selected = if (newChecked) selected + item.file.uriString
                                else selected - item.file.uriString
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val safeSelection = plan.sanitizeDeleteUris(selected)
            TextButton(
                enabled = safeSelection.isNotEmpty(),
                onClick = { onConfirm(safeSelection) },
            ) { Text("Delete selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DuplicateChoiceRow(
    item: com.podcastplayer.app.data.local.DuplicateReviewItem,
    checked: Boolean,
    enabled: Boolean = item.enabled,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = if (enabled) onCheckedChange else null,
        )
        Column {
            Text(item.file.displayName, style = MaterialTheme.typography.bodySmall)
            val date = java.text.DateFormat.getDateInstance().format(java.util.Date(item.file.dateAddedSec * 1000L))
            val status = when {
                item.file.isProtected -> " · In use"
                !enabled -> " · Kept copy"
                else -> ""
            }
            Text(
                "${formatBytes(item.file.sizeBytes)} · $date$status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
