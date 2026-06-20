package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RssFeed
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.presentation.viewmodel.FeedPreviewState
import com.podcastplayer.app.ui.theme.JetBrainsMono

@Composable
fun AddFeedScreen(
    initialUrl: String,
    state: FeedPreviewState,
    onLoad: (String) -> Unit,
    onSubscribe: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && state is FeedPreviewState.Idle) {
            onLoad(initialUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        VibeTopBar(
            title = "Add show",
            eyebrow = "RSS",
            onBack = onBack,
        )

        VibeSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                VibeSectionEyebrow("Feed URL")
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
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
                            onLoad(url)
                        },
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    decorationBox = { inner ->
                        if (url.isBlank()) {
                            Text(
                                text = "RSS feed URL",
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
                VibePrimaryPill(
                    label = "Look up feed",
                    onClick = {
                        focus.clearFocus()
                        onLoad(url)
                    },
                    enabled = url.isNotBlank(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (state) {
            FeedPreviewState.Idle -> FeedHint()
            is FeedPreviewState.Loading -> FeedLoading()
            is FeedPreviewState.Loaded -> FeedPreview(state = state, onSubscribe = onSubscribe)
            is FeedPreviewState.Saved -> FeedSaved(title = state.podcast.title)
            is FeedPreviewState.Error -> FeedError(message = state.message)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FeedHint() {
    VibeEmptyState(
        icon = Icons.Outlined.RssFeed,
        title = "Paste a feed or video podcast",
        subtitle = "Use an RSS feed to add a podcast that search didn't find.",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun FeedLoading() {
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
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = colors.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Reading feed…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun FeedPreview(state: FeedPreviewState.Loaded, onSubscribe: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val podcast = state.podcast
    val sourceLabel = "RSS FEED"
    VibeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                error = painterResource(R.drawable.ic_artwork_placeholder),
                fallback = painterResource(R.drawable.ic_artwork_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sourceLabel,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (podcast.artist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = podcast.artist,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                VibePrimaryPill(label = "Subscribe", onClick = onSubscribe)
            }
        }
    }
}

@Composable
private fun FeedSaved(title: String) {
    VibeEmptyState(
        icon = Icons.Outlined.RssFeed,
        title = "Subscribed",
        subtitle = "$title is now in your library.",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun FeedError(message: String) {
    VibeEmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = "Could not read feed",
        subtitle = message,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
