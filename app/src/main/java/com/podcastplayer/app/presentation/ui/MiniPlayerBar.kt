package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.ui.theme.JetBrainsMono

/**
 * Vibe mini player — floating pill. Sits above bottom nav with breathing room.
 * Tap opens full player; play button toggles playback; close dismisses.
 */
@Composable
fun MiniPlayerBar(
    episode: Episode,
    artworkUrl: String?,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val duration = playerState.duration.coerceAtLeast(1L)
    val position = playerState.currentPosition.coerceIn(0L, duration)
    val progress = position.toFloat() / duration.toFloat()
    val isPlaying = playerState.state == PlaybackState.PLAYING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenPlayer),
        color = colors.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = episode.imageUrl ?: artworkUrl,
                    contentDescription = episode.title,
                    placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                    error = painterResource(R.drawable.ic_artwork_placeholder),
                    fallback = painterResource(R.drawable.ic_artwork_placeholder),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        color = colors.onSurface,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.1).sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatMiniTime(position),
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMono,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "/ ${formatMiniTime(duration)}",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMono,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss player",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Thin progress line at bottom of pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(colors.primary),
                )
            }
        }
    }
}

private fun formatMiniTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
