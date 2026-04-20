package com.podcastplayer.app.presentation.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastQueue
import com.podcastplayer.app.ui.theme.JetBrainsMono
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    queues: List<PodcastQueue>,
    selectedQueueId: String?,
    podcasts: List<Podcast>,
    currentEpisode: Episode?,
    currentArtworkUrl: String?,
    playerState: PlayerState,
    onSelectQueue: (String) -> Unit,
    onCreateQueue: (String) -> Unit,
    onRenameQueue: (String, String) -> Unit,
    onDeleteQueue: (String) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSeek: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    onPlayQueue: () -> Unit,
    onDismissPlayer: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from, to -> onMove(from.index, to.index) },
    )

    val selectedQueue = queues.firstOrNull { it.id == selectedQueueId }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VibeTopBar(
                title = selectedQueue?.name ?: "Queues",
                eyebrow = "Queue",
                actions = {
                    VibeCircleIconButton(
                        icon = Icons.Default.Add,
                        description = "Add queue",
                        onClick = {
                            nameInput = ""
                            showCreateDialog = true
                        },
                    )
                    if (selectedQueue != null) {
                        Spacer(Modifier.width(8.dp))
                        VibeCircleIconButton(
                            icon = Icons.Outlined.Edit,
                            description = "Rename queue",
                            onClick = {
                                nameInput = selectedQueue.name
                                showRenameDialog = true
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        VibeCircleIconButton(
                            icon = Icons.Outlined.Delete,
                            description = "Delete queue",
                            onClick = { showDeleteDialog = true },
                        )
                    }
                },
            )

            QueueSelectorRow(
                queues = queues,
                selectedQueue = selectedQueue,
                onSelectQueue = onSelectQueue,
                onPlayQueue = onPlayQueue,
                enabled = podcasts.isNotEmpty(),
            )

            if (podcasts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VibeEmptyState(
                        icon = Icons.Outlined.QueueMusic,
                        title = "No podcasts in queue",
                        subtitle = "Add from Saved to get started",
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VibeSectionEyebrow(text = "Podcasts", modifier = Modifier.weight(1f))
                    Text(
                        text = "${podcasts.size}",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .reorderable(reorderState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 140.dp,
                    ),
                ) {
                    items(podcasts, key = { it.id }) { podcast ->
                        ReorderableItem(reorderState, key = podcast.id) { isDragging ->
                            QueueRow(
                                podcast = podcast,
                                isDragging = isDragging,
                                onRemove = { onRemove(podcast.id) },
                                reorderModifier = Modifier.detectReorderAfterLongPress(reorderState),
                            )
                        }
                    }
                }
            }
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

    if (showCreateDialog) {
        QueueNameDialog(
            title = "New queue",
            initialName = nameInput,
            onConfirm = {
                onCreateQueue(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (showRenameDialog && selectedQueue != null) {
        QueueNameDialog(
            title = "Rename queue",
            initialName = nameInput.ifBlank { selectedQueue.name },
            onConfirm = {
                onRenameQueue(selectedQueue.id, it)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog && selectedQueue != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete queue") },
            text = { Text("Delete \"${selectedQueue.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteQueue(selectedQueue.id)
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun QueueRow(
    podcast: Podcast,
    isDragging: Boolean,
    onRemove: () -> Unit,
    reorderModifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isDragging) colors.primary else colors.outline
    val elevation = animateDpAsState(
        targetValue = if (isDragging) 1.dp else 0.dp,
        label = "drag-border",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                width = 1.dp + elevation.value,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            VibeCircleIconButton(
                icon = Icons.Outlined.Delete,
                description = "Remove",
                onClick = onRemove,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = reorderModifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueSelectorRow(
    queues: List<PodcastQueue>,
    selectedQueue: PodcastQueue?,
    onSelectQueue: (String) -> Unit,
    onPlayQueue: () -> Unit,
    enabled: Boolean,
) {
    if (queues.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        VibeSectionEyebrow(text = "Select queue")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(queues, key = { it.id }) { queue ->
                    VibeChip(
                        label = queue.name,
                        onClick = { onSelectQueue(queue.id) },
                        active = queue.id == selectedQueue?.id,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            VibePrimaryPill(
                label = "Play",
                onClick = onPlayQueue,
                enabled = enabled,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { value -> name = value },
                placeholder = { Text("Queue name") },
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

