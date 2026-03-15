package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
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
import com.podcastplayer.app.presentation.viewmodel.OpmlResult
import com.podcastplayer.app.presentation.viewmodel.PodcastUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(
    viewModel: com.podcastplayer.app.presentation.viewmodel.PodcastViewModel,
    playerViewModel: com.podcastplayer.app.presentation.viewmodel.PlayerViewModel,
    onPodcastSelected: (Podcast) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDownloads: () -> Unit,
    onPlayQueue: () -> Unit,
    onExportOpml: () -> Unit = {},
    onImportOpml: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val showSaved = !isSearchFocused || searchQuery.isBlank()

    val uiState by viewModel.uiState.collectAsState()
    val savedPodcasts by viewModel.savedPodcasts.collectAsState()
    val queues by viewModel.queues.collectAsState()
    val selectedQueueId by viewModel.selectedQueueId.collectAsState()
    val selectedQueue = remember(queues, selectedQueueId) {
        queues.firstOrNull { it.id == selectedQueueId }
    }
    val queuePodcasts by viewModel.selectedQueuePodcasts.collectAsState()
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val scope = rememberCoroutineScope()
    var queuePickerPodcast by remember { mutableStateOf<Podcast?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val opmlResult by viewModel.opmlResult.collectAsState()

    LaunchedEffect(opmlResult) {
        val result = opmlResult ?: return@LaunchedEffect
        val message = when (result) {
            is OpmlResult.ExportSuccess -> "Exported ${result.count} subscriptions"
            is OpmlResult.ImportSuccess -> "Imported ${result.count} subscriptions"
            is OpmlResult.Error -> "Error: ${result.message}"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.clearOpmlResult()
    }

    LaunchedEffect(searchQuery) {
        viewModel.searchPodcasts(searchQuery)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Podcast Player") },
                actions = {
                    if (!isSearchFocused) {
                        IconButton(onClick = onOpenDownloads) {
                            Icon(Icons.Default.Download, contentDescription = "Downloads")
                        }
                        TextButton(onClick = onOpenQueue, enabled = queues.isNotEmpty()) {
                            Text("Queues")
                        }
                        Box {
                            var showOverflow by remember { mutableStateOf(false) }
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export subscriptions") },
                                    onClick = { showOverflow = false; onExportOpml() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import subscriptions") },
                                    onClick = { showOverflow = false; onImportOpml() }
                                )
                            }
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
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
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )

                if (showSaved) {
                    QueuePlayRow(
                        queues = queues,
                        selectedQueue = selectedQueue,
                        onSelectQueue = { viewModel.selectQueue(it) },
                        onPlayQueue = onPlayQueue,
                        enabled = queuePodcasts.isNotEmpty()
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
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
                        if (showSaved) {
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
                                        onClick = {
                                            focusManager.clearFocus()
                                            onPodcastSelected(podcast)
                                        },
                                        onSaveToggle = { viewModel.removeSavedPodcast(podcast.id) },
                                        onAddToQueue = { queuePickerPodcast = podcast },
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
                                        onClick = {
                                            focusManager.clearFocus()
                                            onPodcastSelected(podcast)
                                        },
                                        onSaveToggle = {
                                            if (alreadySaved) viewModel.removeSavedPodcast(podcast.id) else viewModel.savePodcast(podcast)
                                        },
                                        onAddToQueue = { queuePickerPodcast = podcast },
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

    queuePickerPodcast?.let { podcast ->
        QueuePickerDialog(
            podcast = podcast,
            queues = queues,
            initialSelectedIds = viewModel.getQueueIdsForPodcast(podcast.id),
            onConfirm = { selected ->
                scope.launch { viewModel.setPodcastQueues(podcast, selected) }
                queuePickerPodcast = null
            },
            onDismiss = { queuePickerPodcast = null }
        )
    }
}

@Composable
private fun QueuePlayRow(
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
private fun QueuePickerDialog(
    podcast: Podcast,
    queues: List<com.podcastplayer.app.domain.model.PodcastQueue>,
    initialSelectedIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(podcast) { mutableStateOf(initialSelectedIds.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to queue") },
        text = {
            if (queues.isEmpty()) {
                Text("Create a queue first")
            } else {
                Column {
                    queues.forEach { queue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(queue.id),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedIds.add(queue.id)
                                    } else {
                                        selectedIds.remove(queue.id)
                                    }
                                }
                            )
                            Text(queue.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIds) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastItem(
    podcast: Podcast,
    onClick: () -> Unit,
    onSaveToggle: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    isSaved: Boolean = false,
) {
    Card(
        onClick = onClick,
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
            onAddToQueue?.let {
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = it) {
                    Text("Queue")
                }
            }
        }
    }
}
