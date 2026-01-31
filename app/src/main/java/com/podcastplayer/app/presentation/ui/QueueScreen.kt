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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    podcasts: List<Podcast>,
    currentEpisode: Episode?,
    currentArtworkUrl: String?,
    playerState: PlayerState,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onPlayQueue, enabled = podcasts.isNotEmpty()) {
                        Text("Play Queue")
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
}
