package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.PodcastUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(
    viewModel: com.podcastplayer.app.presentation.viewmodel.PodcastViewModel,
    playerViewModel: com.podcastplayer.app.presentation.viewmodel.PlayerViewModel,
    onPodcastSelected: (Podcast) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlayQueue: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val uiState by viewModel.uiState.collectAsState()
    val savedPodcasts by viewModel.savedPodcasts.collectAsState()
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()

    LaunchedEffect(searchQuery) {
        viewModel.searchPodcasts(searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Podcast Player") },
                actions = {
                    if (!isSearchFocused) {
                        IconButton(onClick = onPlayQueue, enabled = savedPodcasts.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play queue")
                        }
                        TextButton(onClick = onOpenQueue, enabled = savedPodcasts.isNotEmpty()) {
                            Text("Queue")
                        }
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    placeholder = { Text("Search podcasts...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(24.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .pointerInput(isSearchFocused) {
                            if (isSearchFocused) {
                                detectTapGestures(onTap = { focusManager.clearFocus() })
                            }
                        }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isSearchFocused) {
                            item {
                                Text(
                                    text = "Subscriptions",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            if (savedPodcasts.isNotEmpty()) {
                                items(savedPodcasts) { podcast ->
                                    PodcastItem(
                                        podcast = podcast,
                                        onClick = { onPodcastSelected(podcast) },
                                        onSaveToggle = { viewModel.removeSavedPodcast(podcast.id) },
                                        isSaved = true
                                    )
                                }
                            } else {
                                item {
                                    SavedEmptyStateCard(onBrowse = { focusRequester.requestFocus() })
                                }
                            }
                            item { Divider() }
                        }

                        when (val state = uiState) {
                            is PodcastUiState.Initial -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Search for podcasts above")
                                    }
                                }
                            }

                            is PodcastUiState.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is PodcastUiState.Success -> {
                                items(state.podcasts) { podcast ->
                                    val alreadySaved = savedPodcasts.any { it.id == podcast.id }
                                    PodcastItem(
                                        podcast = podcast,
                                        onClick = { onPodcastSelected(podcast) },
                                        onSaveToggle = {
                                            if (alreadySaved) viewModel.removeSavedPodcast(podcast.id) else viewModel.savePodcast(podcast)
                                        },
                                        isSaved = alreadySaved
                                    )
                                }
                            }

                            is PodcastUiState.Error -> {
                                item {
                                    Text(
                                        text = "Error: ${state.message}",
                                        color = MaterialTheme.colorScheme.error
                                    )
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
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onOpenPlayer = onOpenPlayer,
                    onSeek = { positionMs -> playerViewModel.seekTo(positionMs) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SavedEmptyStateCard(
    onBrowse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No saved podcasts yet",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Subscribe on search results to add favorites",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBrowse) {
                Text("Browse podcasts")
            }
        }
    }
}

@Composable
fun PodcastItem(
    podcast: Podcast,
    onClick: () -> Unit,
    onSaveToggle: (() -> Unit)? = null,
    isSaved: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .size(60.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            onSaveToggle?.let {
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = it) {
                    Text(if (isSaved) "Saved" else "Subscribe")
                }
            }
        }
    }
}
