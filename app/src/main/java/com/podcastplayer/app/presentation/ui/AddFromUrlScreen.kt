package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.presentation.viewmodel.UrlPreviewState
import com.podcastplayer.app.ui.theme.JetBrainsMono

/**
 * The "Add from URL" screen — the single converged entry point for paste,
 * dedicated input, and Share intent flows.
 *
 * Layout:
 * - Top bar with Back button
 * - URL field (or read-only display when entered via Share/Paste)
 * - Metadata preview (thumbnail, title, uploader, duration) once loaded
 * - Format selector: Audio (MP3) / Video (MP4)
 * - Big primary CTA "Save offline"
 *
 * The screen is purely presentational; the [com.podcastplayer.app.presentation.viewmodel.UrlDownloadViewModel]
 * owns all state.
 */
@Composable
fun AddFromUrlScreen(
    initialUrl: String,
    previewState: UrlPreviewState,
    selectedMediaType: MediaType,
    onUrlChange: (String) -> Unit,
    onLoadPreview: (String) -> Unit,
    onSelectMediaType: (MediaType) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var url by remember { mutableStateOf(initialUrl) }
    val scroll = rememberScrollState()

    // Auto-load metadata for an initial URL handed in via share / paste.
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && previewState is UrlPreviewState.Idle) {
            onLoadPreview(initialUrl)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            VibeTopBar(
                title = "Add from URL",
                eyebrow = "OFFLINE · YOUTUBE / X",
                onBack = onBack,
            )

            Spacer(Modifier.height(4.dp))

            // URL input row — paste-friendly, single line, monospace.
            VibeSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    VibeSectionEyebrow(text = "URL")
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            onUrlChange(it)
                        },
                        textStyle = TextStyle(
                            color = colors.onSurface,
                            fontSize = 13.sp,
                            fontFamily = JetBrainsMono,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        singleLine = true,
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                focus.clearFocus()
                                onLoadPreview(url)
                            },
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                        ),
                        decorationBox = { inner ->
                            if (url.isBlank()) {
                                Text(
                                    text = "https://youtu.be/… or https://x.com/…",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontFamily = JetBrainsMono,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VibePrimaryPill(
                            label = "Look up",
                            onClick = {
                                focus.clearFocus()
                                onLoadPreview(url)
                            },
                            enabled = url.isNotBlank(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Metadata preview area.
            when (previewState) {
                is UrlPreviewState.Idle -> {
                    PreviewHint(
                        title = "Paste or share a YouTube / X link",
                        subtitle = "We'll fetch the title and thumbnail before saving.",
                    )
                }

                is UrlPreviewState.Loading -> {
                    PreviewLoading(source = previewState.source)
                }

                is UrlPreviewState.Loaded -> {
                    PreviewLoaded(
                        title = previewState.metadata.title,
                        uploader = previewState.metadata.uploader,
                        thumbnailUrl = previewState.metadata.thumbnailUrl,
                        durationMs = previewState.metadata.durationMs,
                        source = previewState.source,
                    )

                    Spacer(Modifier.height(18.dp))
                    SectionTitle("Format")
                    Spacer(Modifier.height(8.dp))
                    FormatPicker(
                        selected = selectedMediaType,
                        onSelect = onSelectMediaType,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VibePrimaryPill(
                            label = if (selectedMediaType == MediaType.AUDIO)
                                "Save audio offline"
                            else
                                "Save video offline",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.onPrimary,
                                )
                            },
                            onClick = onConfirm,
                        )
                    }
                }

                is UrlPreviewState.Error -> {
                    PreviewError(message = previewState.message)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PreviewHint(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewLoading(source: UrlSource) {
    val colors = MaterialTheme.colorScheme
    VibeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName.uppercase(),
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.6.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Reading metadata…",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PreviewLoaded(
    title: String,
    uploader: String?,
    thumbnailUrl: String?,
    durationMs: Long?,
    source: UrlSource,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        VibeSurface {
            Column(modifier = Modifier.padding(12.dp)) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant),
                    placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                    error = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                    fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_artwork_placeholder),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceChip(source = source)
                    Spacer(Modifier.size(8.dp))
                    if (durationMs != null) {
                        DurationChip(durationMs = durationMs)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!uploader.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = uploader,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewError(message: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LinkOff,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Couldn't load that URL",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun FormatPicker(
    selected: MediaType,
    onSelect: (MediaType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FormatTile(
            label = "Audio",
            sub = "MP3 · smaller",
            icon = Icons.Outlined.Audiotrack,
            selected = selected == MediaType.AUDIO,
            onClick = { onSelect(MediaType.AUDIO) },
            modifier = Modifier.weight(1f),
        )
        FormatTile(
            label = "Video",
            sub = "MP4 · keeps visuals",
            icon = Icons.Outlined.Movie,
            selected = selected == MediaType.VIDEO,
            onClick = { onSelect(MediaType.VIDEO) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormatTile(
    label: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (selected) colors.primaryContainer else colors.surface
    val accent = if (selected) colors.primary else colors.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) colors.primary else colors.outline,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Text(
            text = sub,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceChip(source: UrlSource) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = source.displayName.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            letterSpacing = 1.4.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DurationChip(durationMs: Long) {
    val colors = MaterialTheme.colorScheme
    val totalSec = durationMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            letterSpacing = 1.0.sp,
            color = colors.onSurfaceVariant,
        )
    }
}
