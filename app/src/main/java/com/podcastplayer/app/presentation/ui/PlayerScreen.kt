package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.ui.theme.JetBrainsMono

@Composable
fun PlayerScreen(
    episode: Episode,
    playerState: PlayerState,
    artworkUrl: String?,
    sleepTimerRemaining: Long?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
    isLandscape: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val description = remember(episode.description) { episode.description.stripHtml() }
    val isPlaying = playerState.state == PlaybackState.PLAYING
    val duration = playerState.duration.coerceAtLeast(1L)
    val position = playerState.currentPosition.coerceIn(0L, duration)

    var sleepSheetOpen by remember { mutableStateOf(false) }

    if (isLandscape) {
        PlayerLandscape(
            episode = episode,
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            playbackSpeed = playerState.playbackSpeed,
            sleepTimerRemaining = sleepTimerRemaining,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onPlayPause = onPlayPause,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
            onSeek = onSeek,
            onSpeedChange = onSpeedChange,
            onSetSleepTimer = onSetSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onDismiss = onDismiss,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar — minimal, just a chevron-down dismiss
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = colors.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "NOW PLAYING",
                fontSize = 10.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp))
        }

        Spacer(Modifier.height(28.dp))

        // Hero artwork with subtle accent border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.outline, RoundedCornerShape(20.dp)),
            ) {
                if (episode.mediaType == MediaType.VIDEO) {
                    // URL-downloaded videos render the actual frames here while the
                    // existing transport bar below still drives playback (issue #33).
                    VideoSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp)
                } else {
                    AsyncImage(
                        model = episode.imageUrl ?: artworkUrl,
                        contentDescription = episode.title,
                        placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                        error = painterResource(R.drawable.ic_artwork_placeholder),
                        fallback = painterResource(R.drawable.ic_artwork_placeholder),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Title + description
        Text(
            text = episode.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Scrubber
        Slider(
            value = position.toFloat(),
            valueRange = 0f..duration.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlayerTime(position),
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.primary,
            )
            Text(
                text = "-${formatPlayerTime(duration - position)}",
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Speed + Sleep pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            VibePill(
                icon = { Icon(Icons.Rounded.Speed, null, Modifier.size(14.dp), tint = colors.onSurface) },
                label = "${formatSpeed(playerState.playbackSpeed)}x",
                onClick = { onSpeedChange(cycleSpeed(playerState.playbackSpeed)) },
            )
            Spacer(Modifier.width(10.dp))
            val sleepLabel = sleepTimerRemaining
                ?.let { "${(it / 60000L).coerceAtLeast(1)}m" }
                ?: "Sleep"
            VibePill(
                icon = {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        Modifier.size(14.dp),
                        tint = if (sleepTimerRemaining != null) colors.primary else colors.onSurface,
                    )
                },
                label = sleepLabel,
                active = sleepTimerRemaining != null,
                onClick = {
                    if (sleepTimerRemaining != null) onCancelSleepTimer() else sleepSheetOpen = true
                },
            )
        }

        Spacer(Modifier.weight(1f))

        // Transport row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = Icons.Rounded.SkipPrevious,
                description = "Previous episode",
                enabled = hasPrevious,
                size = 44.dp,
                iconSize = 26.dp,
                onClick = onPlayPrevious,
            )
            TransportButton(
                icon = Icons.Rounded.FastRewind,
                description = "Rewind 15s",
                size = 52.dp,
                iconSize = 28.dp,
                onClick = { onSeek((position - 15_000L).coerceAtLeast(0L)) },
            )
            // Primary play/pause — circular accent
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
            TransportButton(
                icon = Icons.Rounded.FastForward,
                description = "Forward 30s",
                size = 52.dp,
                iconSize = 28.dp,
                onClick = { onSeek((position + 30_000L).coerceAtMost(duration)) },
            )
            TransportButton(
                icon = Icons.Rounded.SkipNext,
                description = "Next episode",
                enabled = hasNext,
                size = 44.dp,
                iconSize = 26.dp,
                onClick = onPlayNext,
            )
        }
    }

    if (sleepSheetOpen) {
        SleepTimerPicker(
            onDismiss = { sleepSheetOpen = false },
            onPick = { minutes ->
                sleepSheetOpen = false
                onSetSleepTimer(minutes * 60_000L)
            },
        )
    }
}

@Composable
private fun VibePill(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (active) colors.primaryContainer else colors.surfaceVariant
    val fg = if (active) colors.primary else colors.onSurface
    Row(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, colors.outline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SleepTimerPicker(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(5L, 10L, 15L, 30L, 45L, 60L)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.take(3).forEach { m ->
                    SleepOption(minutes = m, onClick = { onPick(m) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.drop(3).forEach { m ->
                    SleepOption(minutes = m, onClick = { onPick(m) })
                }
            }
        }
    }
}

@Composable
private fun SleepOption(minutes: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${minutes}m",
            fontSize = 13.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
        )
    }
}

// ─── Landscape player layout ──────────────────────────────────────────────────
// Left 300dp: close button + 240dp artwork.
// Right: title block, scrubber, transport row, speed/sleep chips.
@Composable
private fun PlayerLandscape(
    episode: Episode,
    artworkUrl: String?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playbackSpeed: Float,
    sleepTimerRemaining: Long?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val progress = position.toFloat() / duration.toFloat()
    var sleepSheetOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = 24.dp, top = 32.dp, end = 110.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // LEFT — close button + artwork
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, colors.outline, RoundedCornerShape(18.dp)),
            ) {
                if (episode.mediaType == MediaType.VIDEO) {
                    VideoSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 18.dp)
                } else {
                    AsyncImage(
                        model = episode.imageUrl ?: artworkUrl,
                        contentDescription = episode.title,
                        placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                        error = painterResource(R.drawable.ic_artwork_placeholder),
                        fallback = painterResource(R.drawable.ic_artwork_placeholder),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // RIGHT — metadata + scrubber + transport + chips
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            // Title block
            Text(
                text = episode.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                letterSpacing = (-0.4).sp,
                lineHeight = 26.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.podcastId,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(22.dp))

            // Scrubber
            Slider(
                value = position.toFloat(),
                valueRange = 0f..duration.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlayerTime(position),
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    color = colors.primary,
                )
                Text(
                    text = "-${formatPlayerTime(duration - position)}",
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Transport + chips in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                TransportButton(
                    icon = Icons.Rounded.SkipPrevious,
                    description = "Previous episode",
                    enabled = hasPrevious,
                    size = 38.dp,
                    iconSize = 20.dp,
                    onClick = onPlayPrevious,
                )
                Spacer(Modifier.width(2.dp))
                TransportButton(
                    icon = Icons.Rounded.FastRewind,
                    description = "Rewind 15s",
                    size = 44.dp,
                    iconSize = 22.dp,
                    onClick = { onSeek((position - 15_000L).coerceAtLeast(0L)) },
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                TransportButton(
                    icon = Icons.Rounded.FastForward,
                    description = "Forward 30s",
                    size = 44.dp,
                    iconSize = 22.dp,
                    onClick = { onSeek((position + 30_000L).coerceAtMost(duration)) },
                )
                Spacer(Modifier.width(2.dp))
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    description = "Next episode",
                    enabled = hasNext,
                    size = 38.dp,
                    iconSize = 20.dp,
                    onClick = onPlayNext,
                )

                Spacer(Modifier.weight(1f))

                // Speed chip
                VibePill(
                    icon = { Icon(Icons.Rounded.Speed, null, Modifier.size(13.dp), tint = colors.onSurface) },
                    label = "${formatSpeed(playbackSpeed)}×",
                    onClick = { onSpeedChange(cycleSpeed(playbackSpeed)) },
                )
                Spacer(Modifier.width(8.dp))
                // Sleep chip
                val sleepLabel = sleepTimerRemaining?.let { "${(it / 60000L).coerceAtLeast(1)}m" } ?: "Sleep"
                VibePill(
                    icon = {
                        Icon(
                            Icons.Outlined.Timer,
                            null,
                            Modifier.size(13.dp),
                            tint = if (sleepTimerRemaining != null) colors.primary else colors.onSurface,
                        )
                    },
                    label = sleepLabel,
                    active = sleepTimerRemaining != null,
                    onClick = {
                        if (sleepTimerRemaining != null) onCancelSleepTimer()
                        else sleepSheetOpen = true
                    },
                )
            }
        }
    }

    if (sleepSheetOpen) {
        SleepTimerPicker(
            onDismiss = { sleepSheetOpen = false },
            onPick = { minutes ->
                sleepSheetOpen = false
                onSetSleepTimer(minutes * 60_000L)
            },
        )
    }
}

private fun cycleSpeed(current: Float): Float = when (current) {
    0.75f -> 1.0f
    1.0f -> 1.25f
    1.25f -> 1.5f
    1.5f -> 1.75f
    1.75f -> 2.0f
    2.0f -> 0.75f
    else -> 1.0f
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "%.1f".format(speed) else "%.2f".format(speed).trimEnd('0')

private fun formatPlayerTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}
