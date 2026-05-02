package com.podcastplayer.app.presentation.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.PlayerViewModel
import com.podcastplayer.app.presentation.viewmodel.PodcastViewModel
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.service.PlaybackSessionStorage
import com.podcastplayer.app.service.PlayerController
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val Search = "search"
    const val Queue = "queue"
    const val Downloads = "downloads"
    const val Player = "player"

    const val EpisodesBase = "episodes"
    const val PodcastIdArg = "podcastId"

    const val EpisodesPattern = "$EpisodesBase/{$PodcastIdArg}"

    fun episodes(podcastId: String): String = "$EpisodesBase/${Uri.encode(podcastId)}"
}

@Composable
fun PodcastNavHost() {
    val context = LocalContext.current
    val db = remember { DatabaseProvider.getDatabase(context) }
    val queueStorage = remember { QueueStorage(context) }

    // Keep ViewModel scoping identical to the previous implementation (created once at the top level).
    val podcastViewModel: PodcastViewModel = viewModel(
        factory = PodcastViewModelFactory(
            PodcastRepository(iTunesApi.create(), RssParser()),
            DownloadManager(context),
            SavedPodcastsStorage(context),
            queueStorage,
            db.playbackProgressDao()
        )
    )
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(
            PlayerController.getInstance(context),
            PlaybackSessionStorage(context)
        )
    )

    val navController = rememberNavController()
    val contentResolver = context.contentResolver
    val opmlScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        uri?.let {
            opmlScope.launch {
                contentResolver.openOutputStream(it)?.use { os ->
                    podcastViewModel.exportOpml(os)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            opmlScope.launch {
                contentResolver.openInputStream(it)?.use { inputStream ->
                    podcastViewModel.importOpml(inputStream)
                }
            }
        }
    }

    // Auto-play Morning queue when app is opened before 8:30 AM
    LaunchedEffect(Unit) {
        val now = java.time.LocalTime.now()
        if (now >= java.time.LocalTime.of(8, 30)) return@LaunchedEffect

        // Wait for session restore in PlayerViewModel.init to complete
        delay(2000)

        // Don't override an existing/restored session
        if (playerViewModel.currentEpisode.value != null) return@LaunchedEffect

        // Find "Morning" queue and resolve its podcasts
        val morningPayload = queueStorage.queues.value
            .firstOrNull { it.name.equals("Morning", ignoreCase = true) }
            ?: return@LaunchedEffect

        val savedMap = podcastViewModel.savedPodcasts.value.associateBy { it.id }
        val podcasts = morningPayload.podcastIds.mapNotNull { savedMap[it] }
        if (podcasts.isEmpty()) return@LaunchedEffect

        // Build unplayed episodes and start playback
        val episodes = podcastViewModel.buildUnplayedEpisodesForPodcastQueue(podcasts)
        if (episodes.isNotEmpty()) {
            playerViewModel.playEpisodesQueue(
                episodes = episodes,
                defaultArtworkUrl = podcasts.firstOrNull()?.artworkUrl
            )
            navController.navigate(Routes.Player)
        }
    }

    // Auto-play Morning queue when app is opened before 8:30 AM
    LaunchedEffect(Unit) {
        val now = java.time.LocalTime.now()
        if (now >= java.time.LocalTime.of(8, 30)) return@LaunchedEffect

        // Wait for session restore in PlayerViewModel.init to complete
        delay(2000)

        // Only skip auto-play if something is actively playing/loading (not paused/restored)
        val playbackState = playerViewModel.playerState.value.state
        if (playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.LOADING) return@LaunchedEffect

        // Find "Morning" queue and resolve its podcasts
        val morningPayload = queueStorage.queues.value
            .firstOrNull { it.name.equals("Morning", ignoreCase = true) }
            ?: return@LaunchedEffect

        val savedMap = podcastViewModel.savedPodcasts.value.associateBy { it.id }
        val podcasts = morningPayload.podcastIds.mapNotNull { savedMap[it] }
        if (podcasts.isEmpty()) return@LaunchedEffect

        // Build unplayed episodes and start playback
        val episodes = podcastViewModel.buildUnplayedEpisodesForPodcastQueue(podcasts)
        if (episodes.isNotEmpty()) {
            playerViewModel.playEpisodesQueue(
                episodes = episodes,
                defaultArtworkUrl = podcasts.firstOrNull()?.artworkUrl
            )
            navController.navigate(Routes.Player)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavRoutes = setOf(Routes.Home, Routes.Search, Routes.Queue, Routes.Downloads)
    val bottomNavTabs = listOf(VibeTab.Home, VibeTab.Search, VibeTab.Queue, VibeTab.Downloads)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val onNavigateTab: (VibeTab) -> Unit = { tab ->
        navController.navigate(tab.id) {
            popUpTo(Routes.Home) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            if (!isLandscape && currentRoute in bottomNavRoutes) {
                VibeBottomNav(
                    active = currentRoute ?: "",
                    onNavigate = onNavigateTab,
                    tabs = bottomNavTabs,
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isLandscape && currentRoute in bottomNavRoutes) {
                VibeNavRail(
                    active = currentRoute ?: "",
                    onNavigate = onNavigateTab,
                    tabs = bottomNavTabs,
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.Home) {
                    val savedPodcasts by podcastViewModel.savedPodcasts.collectAsState()
                    val continueListening by podcastViewModel.continueListening.collectAsState()
                    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
                    val playerState by playerViewModel.playerState.collectAsState()
                    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()

                    HomeScreen(
                        subscriptions = savedPodcasts,
                        continueListening = continueListening,
                        currentEpisode = currentEpisode,
                        currentArtworkUrl = currentArtworkUrl,
                        playerState = playerState,
                        onOpenPodcast = { podcast ->
                            podcastViewModel.selectPodcast(podcast)
                            navController.navigate(Routes.episodes(podcast.id))
                        },
                        onOpenSearch = { navController.navigate(Routes.Search) },
                        onPlayEpisode = { episode, artwork ->
                            playerViewModel.playEpisode(episode, artwork)
                            navController.navigate(Routes.Player)
                        },
                        onPlayPause = { playerViewModel.togglePlayPause() },
                        onOpenPlayer = { navController.navigate(Routes.Player) },
                        onSeek = { playerViewModel.seekTo(it) },
                        onDismissPlayer = { playerViewModel.clearPlayer() },
                    )
                }

                composable(Routes.Search) {
                    val scope = rememberCoroutineScope()
                    val selectedQueuePodcasts by podcastViewModel.selectedQueuePodcasts.collectAsState()

                    PodcastListScreen(
                        viewModel = podcastViewModel,
                        playerViewModel = playerViewModel,
                        onPodcastSelected = { podcast: Podcast ->
                            podcastViewModel.selectPodcast(podcast)
                            navController.navigate(Routes.episodes(podcast.id))
                        },
                        onOpenPlayer = { navController.navigate(Routes.Player) },
                        onOpenQueue = { navController.navigate(Routes.Queue) },
                        onOpenDownloads = { navController.navigate(Routes.Downloads) },
                        onPlayQueue = {
                            val podcasts = selectedQueuePodcasts
                            if (podcasts.isNotEmpty()) {
                                scope.launch {
                                    val episodes = podcastViewModel.buildUnplayedEpisodesForPodcastQueue(podcasts)
                                    if (episodes.isNotEmpty()) {
                                        playerViewModel.playEpisodesQueue(
                                            episodes = episodes,
                                            defaultArtworkUrl = podcasts.firstOrNull()?.artworkUrl
                                        )
                                        navController.navigate(Routes.Player)
                                    }
                                }
                            }
                        },
                        onExportOpml = { exportLauncher.launch("vibe-podcasts.opml") },
                        onImportOpml = {
                            importLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*"))
                        }
                    )
                }

                composable(
                    route = Routes.EpisodesPattern,
                    arguments = listOf(
                        navArgument(Routes.PodcastIdArg) { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val podcastId = backStackEntry.arguments?.getString(Routes.PodcastIdArg)
                    val selectedPodcast by podcastViewModel.selectedPodcast.collectAsState()
                    val savedPodcasts by podcastViewModel.savedPodcasts.collectAsState()

                    val podcastForScreen = when {
                        selectedPodcast?.id == podcastId -> selectedPodcast
                        podcastId != null -> savedPodcasts.firstOrNull { it.id == podcastId }?.also {
                            podcastViewModel.selectPodcast(it)
                        }
                        else -> selectedPodcast
                    }

                    EpisodeListScreen(
                        podcast = podcastForScreen,
                        podcastViewModel = podcastViewModel,
                        playerViewModel = playerViewModel,
                        onBack = {
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        },
                        onPlayEpisode = { navController.navigate(Routes.Player) },
                        onOpenPlayer = { navController.navigate(Routes.Player) },
                        isLandscape = isLandscape,
                    )
                }

                composable(Routes.Queue) {
                    val scope = rememberCoroutineScope()
                    val queues by podcastViewModel.queues.collectAsState()
                    val selectedQueueId by podcastViewModel.selectedQueueId.collectAsState()
                    val queuePodcasts by podcastViewModel.selectedQueuePodcasts.collectAsState()
                    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
                    val playerState by playerViewModel.playerState.collectAsState()
                    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()

                    QueueScreen(
                        queues = queues,
                        selectedQueueId = selectedQueueId,
                        podcasts = queuePodcasts,
                        currentEpisode = currentEpisode,
                        currentArtworkUrl = currentArtworkUrl,
                        playerState = playerState,
                        onSelectQueue = { podcastViewModel.selectQueue(it) },
                        onCreateQueue = { podcastViewModel.createQueue(it) },
                        onRenameQueue = { id, name -> podcastViewModel.renameQueue(id, name) },
                        onDeleteQueue = { podcastViewModel.deleteQueue(it) },
                        onPlayPause = { playerViewModel.togglePlayPause() },
                        onOpenPlayer = { navController.navigate(Routes.Player) },
                        onSeek = { playerViewModel.seekTo(it) },
                        onMove = { from, to ->
                            selectedQueueId?.let { podcastViewModel.movePodcastInQueue(it, from, to) }
                        },
                        onRemove = { podcastId ->
                            selectedQueueId?.let { podcastViewModel.removePodcastFromQueue(it, podcastId) }
                        },
                        onPlayQueue = {
                            if (queuePodcasts.isNotEmpty()) {
                                scope.launch {
                                    val episodes = podcastViewModel.buildUnplayedEpisodesForPodcastQueue(queuePodcasts)
                                    if (episodes.isNotEmpty()) {
                                        playerViewModel.playEpisodesQueue(
                                            episodes = episodes,
                                            defaultArtworkUrl = queuePodcasts.firstOrNull()?.artworkUrl
                                        )
                                        navController.navigate(Routes.Player)
                                    }
                                }
                            }
                        },
                        onDismissPlayer = { playerViewModel.clearPlayer() },
                        onBack = {
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        }
                    )
                }

                composable(Routes.Downloads) {
                    val scope = rememberCoroutineScope()
                    val downloads by podcastViewModel.downloadedEpisodesUi.collectAsState()
                    DownloadsScreen(
                        downloads = downloads,
                        onPlayEpisode = { item ->
                            playerViewModel.playEpisode(item.episode, item.podcastArtworkUrl)
                            navController.navigate(Routes.Player)
                        },
                        onDeleteEpisode = { episodeId ->
                            scope.launch { podcastViewModel.deleteDownload(episodeId) }
                        },
                        onDeleteAll = {
                            scope.launch { podcastViewModel.deleteAllDownloads() }
                        },
                        onBack = { navController.popBackStack(route = Routes.Home, inclusive = false) }
                    )
                }

                composable(Routes.Player) {
                    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
                    val playerState by playerViewModel.playerState.collectAsState()
                    val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsState()
                    val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
                    val hasPrevious by playerViewModel.hasPrevious.collectAsState()
                    val hasNext by playerViewModel.hasNext.collectAsState()
                    val selectedPodcast by podcastViewModel.selectedPodcast.collectAsState()

                    fun goToEpisodesOrSearch() {
                        val podcastId = selectedPodcast?.id
                        if (podcastId == null) {
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        } else {
                            navController.navigate(Routes.episodes(podcastId)) {
                                popUpTo(Routes.Home) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }

                    BackHandler {
                        goToEpisodesOrSearch()
                    }

                    currentEpisode?.let { episode ->
                        PlayerScreen(
                            episode = episode,
                            playerState = playerState,
                            artworkUrl = currentArtworkUrl ?: selectedPodcast?.artworkUrl,
                            sleepTimerRemaining = sleepTimerRemaining,
                            hasPrevious = hasPrevious,
                            hasNext = hasNext,
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onPlayPrevious = { playerViewModel.playPrevious() },
                            onPlayNext = { playerViewModel.playNext() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
                            onSetSleepTimer = { playerViewModel.setSleepTimer(it) },
                            onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
                            onDismiss = { goToEpisodesOrSearch() },
                            isLandscape = isLandscape,
                        )
                    }

                    if (currentEpisode == null) {
                        LaunchedEffect(Unit) {
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        }
                    }
                }
            }
        }
    }
}
