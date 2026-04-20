package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.podcastplayer.app.R
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastQueue
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
    @Suppress("UNUSED_PARAMETER") onOpenDownloads: () -> Unit,
    onPlayQueue: () -> Unit,
    onExportOpml: () -> Unit = {},
    onImportOpml: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
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
    var overflowOpen by remember { mutableStateOf(false) }

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
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                VibeTopBar(
                    title = "Vibe",
                    eyebrow = "Podcasts",
                    actions = {
                        Box {
                            VibeCircleIconButton(
                                icon = Icons.Outlined.MoreHoriz,
                                description = "More options",
                                onClick = { overflowOpen = true },
                            )
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export subscriptions") },
                                    onClick = { overflowOpen = false; onExportOpml() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import subscriptions") },
                                    onClick = { overflowOpen = false; onImportOpml() },
                                )
                            }
                        }
                    },
                )

                SearchField(
                    value = searchQuery,
                    onChange = { searchQuery = it },
                    focusRequester = focusRequester,
                    onFocusChange = { isSearchFocused = it },
                    onClear = {
                        searchQuery = ""
                        focusManager.clearFocus()
                    },
                )

                if (showSaved && queues.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    QueuePlayRow(
                        queues = queues,
                        selectedQueue = selectedQueue,
                        onSelectQueue = { viewModel.selectQueue(it) },
                        onPlayQueue = onPlayQueue,
                        onOpenQueues = onOpenQueue,
                        enabled = queuePodcasts.isNotEmpty(),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showSaved) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VibeSectionEyebrow("Subscriptions")
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = savedPodcasts.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        if (savedPodcasts.isNotEmpty()) {
                            items(savedPodcasts, key = { it.id }) { podcast ->
                                PodcastRow(
                                    podcast = podcast,
                                    onClick = {
                                        focusManager.clearFocus()
                                        onPodcastSelected(podcast)
                                    },
                                    onSaveToggle = { viewModel.removeSavedPodcast(podcast.id) },
                                    onAddToQueue = { queuePickerPodcast = podcast },
                                    isSaved = true,
                                )
                            }
                        } else {
                            item {
                                VibeEmptyState(
                                    icon = Icons.Outlined.BookmarkAdd,
                                    title = "No subscriptions yet",
                                    subtitle = "Search above and tap Subscribe to start your library.",
                                    action = {
                                        VibePrimaryPill(
                                            label = "Browse podcasts",
                                            onClick = { focusRequester.requestFocus() },
                                        )
                                    },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    when (val state = uiState) {
                        is PodcastUiState.Initial -> {
                            if (!showSaved) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Search for podcasts above",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        is PodcastUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }

                        is PodcastUiState.Success -> {
                            if (state.podcasts.isNotEmpty()) {
                                item {
                                    VibeSectionEyebrow(
                                        "Results",
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                                items(state.podcasts, key = { it.id }) { podcast ->
                                    val alreadySaved = savedPodcasts.any { it.id == podcast.id }
                                    PodcastRow(
                                        podcast = podcast,
                                        onClick = {
                                            focusManager.clearFocus()
                                            onPodcastSelected(podcast)
                                        },
                                        onSaveToggle = {
                                            if (alreadySaved) viewModel.removeSavedPodcast(podcast.id)
                                            else viewModel.savePodcast(podcast)
                                        },
                                        onAddToQueue = { queuePickerPodcast = podcast },
                                        isSaved = alreadySaved,
                                    )
                                }
                            }
                        }

                        is PodcastUiState.Error -> {
                            item {
                                VibeSurface {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Error",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.error,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
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
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onOpenPlayer = onOpenPlayer,
                    onSeek = { positionMs -> playerViewModel.seekTo(positionMs) },
                    onDismiss = { playerViewModel.clearPlayer() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
            onDismiss = { queuePickerPodcast = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = {
            Text(
                text = "Search podcasts",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = if (value.isNotBlank()) {
            {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            containerColor = colors.surface,
            cursorColor = colors.primary,
        ),
    )
}

@Composable
private fun QueuePlayRow(
    queues: List<PodcastQueue>,
    selectedQueue: PodcastQueue?,
    onSelectQueue: (String) -> Unit,
    onPlayQueue: () -> Unit,
    onOpenQueues: () -> Unit,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            VibeChip(
                label = selectedQueue?.name ?: "Select queue",
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                queues.forEach { queue ->
                    DropdownMenuItem(
                        text = { Text(queue.name) },
                        onClick = {
                            expanded = false
                            onSelectQueue(queue.id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Manage queues", color = colors.primary) },
                    onClick = {
                        expanded = false
                        onOpenQueues()
                    },
                )
            }
        }
        VibePrimaryPill(
            label = "Play queue",
            onClick = onPlayQueue,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    Icons.Outlined.PlayArrow,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = if (enabled) colors.onPrimary else colors.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun PodcastRow(
    podcast: Podcast,
    onClick: () -> Unit,
    onSaveToggle: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    isSaved: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    VibeSurface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = podcast.title,
                placeholder = painterResource(R.drawable.ic_artwork_placeholder),
                error = painterResource(R.drawable.ic_artwork_placeholder),
                fallback = painterResource(R.drawable.ic_artwork_placeholder),
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (onSaveToggle != null) {
                val icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
                VibeCircleIconButton(
                    icon = icon,
                    description = if (isSaved) "Unsubscribe" else "Subscribe",
                    onClick = onSaveToggle,
                    size = 36.dp,
                    iconSize = 18.dp,
                    tinted = isSaved,
                )
            }
            if (onAddToQueue != null) {
                Spacer(Modifier.width(6.dp))
                VibeCircleIconButton(
                    icon = Icons.Outlined.PlaylistAdd,
                    description = "Add to queue",
                    onClick = onAddToQueue,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun QueuePickerDialog(
    podcast: Podcast,
    queues: List<PodcastQueue>,
    initialSelectedIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember(podcast) { mutableStateOf(initialSelectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add to queue") },
        text = {
            if (queues.isEmpty()) {
                Text("Create a queue first")
            } else {
                Column {
                    queues.forEach { queue ->
                        val checked = queue.id in selectedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - queue.id
                                    else selectedIds + queue.id
                                },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) selectedIds + queue.id
                                    else selectedIds - queue.id
                                },
                            )
                            Text(queue.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIds) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
