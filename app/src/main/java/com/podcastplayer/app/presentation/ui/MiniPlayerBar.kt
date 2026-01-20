package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.domain.model.PlaybackState

@Composable
fun MiniPlayerBar(
    episode: Episode,
    artworkUrl: String?,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var sliderValue by remember(playerState.currentPosition, playerState.duration) {
        mutableStateOf(playerState.currentPosition.coerceAtLeast(0L).toFloat())
    }
    val duration = playerState.duration.coerceAtLeast(0L)
    val description = remember(episode.description) { episode.description.stripHtml() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = artworkUrl ?: episode.localPath ?: episode.audioUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.state == PlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playerState.state == PlaybackState.PLAYING) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                onValueChangeFinished = { onSeek(sliderValue.toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(sliderValue.toLong()),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall
                )
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
