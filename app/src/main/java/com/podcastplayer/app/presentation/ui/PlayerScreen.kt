package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    episode: Episode,
    playerState: PlayerState,
    artworkUrl: String?,
    sleepTimerRemaining: Long?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val description = remember(episode.description) { episode.description.stripHtml() }
    var sleepMinutes by remember { mutableStateOf(15) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = episode.imageUrl ?: artworkUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.size(220.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Slider(
                value = if (playerState.duration > 0) playerState.currentPosition.toFloat() else 0f,
                valueRange = 0f..(if (playerState.duration > 0) playerState.duration.toFloat() else 1f),
                onValueChange = { onSeek(it.toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playerState.currentPosition),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(playerState.duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sleep timer",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (sleepTimerRemaining != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ends in ${formatTime(sleepTimerRemaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onCancelSleepTimer) {
                            Text("Cancel")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { sleepMinutes = (sleepMinutes - 5).coerceAtLeast(5) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease timer")
                            }
                            Text(
                                text = "${sleepMinutes} min",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { sleepMinutes = (sleepMinutes + 5).coerceAtMost(120) }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase timer")
                            }
                        }
                        Button(onClick = { onSetSleepTimer(sleepMinutes * 60 * 1000L) }) {
                            Text("Start")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        val newSpeed = when (playerState.playbackSpeed) {
                            0.5f -> 0.75f
                            0.75f -> 1.0f
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 0.5f
                        }
                        onSpeedChange(newSpeed)
                    }
                ) {
                    Text(
                        text = "Speed ${playerState.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSeek(playerState.currentPosition - 15000) },
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = "Rewind 15s",
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FloatingActionButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(128.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.state == com.podcastplayer.app.domain.model.PlaybackState.PLAYING) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playerState.state == com.podcastplayer.app.domain.model.PlaybackState.PLAYING) {
                            "Pause"
                        } else {
                            "Play"
                        },
                        modifier = Modifier.size(76.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { onSeek(playerState.currentPosition + 30000) },
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = "Forward 30s",
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
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

private fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}
