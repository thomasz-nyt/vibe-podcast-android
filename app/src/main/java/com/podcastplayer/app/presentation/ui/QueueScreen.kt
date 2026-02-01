package com.podcastplayer.app.presentation.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.domain.model.Podcast
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queues: List<com.podcastplayer.app.domain.model.PodcastQueue>,
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
    onBack: () -> Unit
) {
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> onMove(from.index, to.index) }
    )

    val selectedQueue = queues.firstOrNull { it.id == selectedQueueId }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queues") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nameInput = ""
                        showCreateDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add queue")
                    }
                    IconButton(
                        onClick = {
                            nameInput = selectedQueue?.name.orEmpty()
                            showRenameDialog = true
                        },
                        enabled = selectedQueue != null
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename queue")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = selectedQueue != null
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete queue")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                QueueSelectorRow(
                    queues = queues,
                    selectedQueue = selectedQueue,
                    onSelectQueue = onSelectQueue,
                    onPlayQueue = onPlayQueue,
                    enabled = podcasts.isNotEmpty()
                )

                if (podcasts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No podcasts in queue")
                        Text(
                            text = "Add from Saved to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = reorderState.listState,
                        modifier = Modifier
                            .weight(1f)
                            .reorderable(reorderState),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 140.dp
                        )
                    ) {
                        items(podcasts, key = { it.id }) { podcast ->
                            ReorderableItem(reorderState, key = podcast.id) { isDragging ->
                                val elevation = animateDpAsState(
                                    targetValue = if (isDragging) 6.dp else 0.dp,
                                    label = "elevation"
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .detectReorderAfterLongPress(reorderState),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = elevation.value
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = podcast.artworkUrl,
                                            contentDescription = podcast.title,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(podcast.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                podcast.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onRemove(podcast.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                                        }
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Reorder",
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
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
            onDismiss = { showCreateDialog = false }
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
            onDismiss = { showRenameDialog = false }
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
            }
        )
    }
}

@Composable
private fun QueueSelectorRow(
    queues: List<com.podcastplayer.app.domain.model.PodcastQueue>,
    selectedQueue: com.podcastplayer.app.domain.model.PodcastQueue?,
    onSelectQueue: (String) -> Unit,
    onPlayQueue: () -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box {
            TextButton(onClick = { expanded = true }, enabled = queues.isNotEmpty()) {
                Text(selectedQueue?.name ?: "Select queue")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                queues.forEach { queue ->
                    DropdownMenuItem(
                        text = { Text(queue.name) },
                        onClick = {
                            expanded = false
                            onSelectQueue(queue.id)
                        }
                    )
                }
            }
        }
        Button(onClick = onPlayQueue, enabled = enabled) {
            Text("Play")
        }
    }
}

@Composable
private fun QueueNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { value -> name = value },
                placeholder = { Text("Queue name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
