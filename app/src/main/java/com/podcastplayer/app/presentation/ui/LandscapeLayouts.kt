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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.ui.theme.JetBrainsMono

// ─── Nav Rail ──────────────────────────────────────────────────
// Replaces the bottom nav in landscape orientation.
// 88dp wide, vertical, with brand mark + 4 tab items.
@Composable
fun VibeNavRail(
    active: String,
    onNavigate: (VibeTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<VibeTab> = VibeTab.entries,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(colors.background)
            .border(
                width = 1.dp,
                color = colors.outlineVariant,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // Brand mark — "V" in accent
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
            )
        }

        Spacer(Modifier.height(14.dp))

        tabs.forEach { tab ->
            val isActive = active == tab.id
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onNavigate(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isActive) colors.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = tab.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) colors.onSurface else colors.onSurfaceVariant,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

// ─── Landscape Mini Player ─────────────────────────────────────
// Wider pill displayed at the bottom of the content area (not the screen).
// Includes Back-15 and Fwd-30 buttons since there's room.
@Composable
fun MiniPlayerBarLandscape(
    episode: Episode,
    artworkUrl: String?,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val duration = playerState.duration.coerceAtLeast(1L)
    val position = playerState.currentPosition.coerceIn(0L, duration)
    val progress = position.toFloat() / duration.toFloat()
    val isPlaying = playerState.state == PlaybackState.PLAYING

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 36.dp, bottom = 12.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant)
            .clickable(onClick = onOpenPlayer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = episode.imageUrl ?: artworkUrl,
                contentDescription = episode.title,
                placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                error = painterResource(R.drawable.ic_artwork_placeholder),
                fallback = painterResource(R.drawable.ic_artwork_placeholder),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatLandscapeTime(position),
                        color = colors.primary,
                        fontSize = 10.5.sp,
                        fontFamily = JetBrainsMono,
                    )
                    Text(
                        text = " / ${formatLandscapeTime(duration)}",
                        color = colors.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        fontFamily = JetBrainsMono,
                    )
                }
            }

            LandscapeIconBtn(
                icon = Icons.Rounded.FastRewind,
                description = "Rewind 15s",
                size = 36.dp,
                onClick = { onSeek((position - 15_000L).coerceAtLeast(0L)) },
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            LandscapeIconBtn(
                icon = Icons.Rounded.FastForward,
                description = "Forward 30s",
                size = 36.dp,
                onClick = { onSeek((position + 30_000L).coerceAtMost(duration)) },
            )
        }

        // Thin accent progress line at bottom
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

// ─── Landscape Browse Scaffold ─────────────────────────────────
// Row shell for Home/Search/Queue/Downloads in landscape:
// NavRail on the left, content + mini player on the right.
@Composable
fun LandscapeBrowseScaffold(
    currentRoute: String,
    onNavigate: (VibeTab) -> Unit,
    episode: Episode?,
    artworkUrl: String?,
    playerState: PlayerState?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
        VibeNavRail(
            active = currentRoute,
            onNavigate = onNavigate,
        )

        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (episode != null) 80.dp else 0.dp),
            ) {
                content()
            }

            if (episode != null && playerState != null) {
                MiniPlayerBarLandscape(
                    episode = episode,
                    artworkUrl = artworkUrl,
                    playerState = playerState,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onOpenPlayer = onOpenPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ─── Internal helpers ──────────────────────────────────────────

@Composable
private fun LandscapeIconBtn(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatLandscapeTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
