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
import androidx.compose.ui.Alignment
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
import com.podcastplayer.app.BuildConfig
import com.podcastplayer.app.data.local.AppSettings
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
import com.podcastplayer.app.presentation.viewmodel.UrlDownloadViewModel
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
    const val Settings = "settings"

    const val EpisodesBase = "episodes"
    const val PodcastIdArg = "podcastId"

    const val EpisodesPattern = "$EpisodesBase/{$PodcastIdArg}"

    fun episodes(podcastId: String): String = "$EpisodesBase/${Uri.encode(podcastId)}"

    // "Add from URL" flow (issue #33). The optional `url` query arg is filled in
    // when arriving via a Share intent or paste shortcut so the screen can
    // auto-populate and start metadata extraction.
    const val AddUrlBase = "add-url"
    const val UrlArg = "url"
    const val AddUrlPattern = "$AddUrlBase?$UrlArg={$UrlArg}"

    fun addUrl(rawUrl: String? = null): String =
        if (rawUrl.isNullOrBlank()) AddUrlBase else "$AddUrlBase?$UrlArg=${Uri.encode(rawUrl)}"
}

@Composable
fun PodcastNavHost(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val db = remember { DatabaseProvider.getDatabase(context) }
    val queueStorage = remember { QueueStorage(context) }
    val appSettings = remember { AppSettings.getInstance(context) }
    val application = context.applicationContext as android.app.Application

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
            PlaybackSessionStorage(context),
            appSettings,
        )
    )
    val urlDownloadViewModel: UrlDownloadViewModel = viewModel(
        factory = UrlDownloadViewModelFactory(application)
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

    // React to a Share intent (issue #33). MainActivity hands us the URL via [sharedUrl];
    // we navigate to the AddFromUrl screen and clear the slot so we don't loop.
    LaunchedEffect(sharedUrl) {
        val url = sharedUrl ?: return@LaunchedEffect
        navController.navigate(Routes.addUrl(url)) {
            launchSingleTop = true
        }
        onSharedUrlConsumed()
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
                    val urlDownloads by urlDownloadViewModel.completedDownloads.collectAsState()
                    val urlInFlight by urlDownloadViewModel.inFlightDownloads.collectAsState()

                    val urlRepository = remember(context) {
                        com.podcastplayer.app.data.repository.UrlDownloadRepository(context)
                    }

                    HomeScreen(
                        subscriptions = savedPodcasts,
                        continueListening = continueListening,
                        urlDownloads = urlDownloads,
                        urlInFlight = urlInFlight,
                        currentEpisode = currentEpisode,
                        currentArtworkUrl = currentArtworkUrl,
                        playerState = playerState,
                        onOpenPodcast = { podcast ->
                            podcastViewModel.selectPodcast(podcast)
                            navController.navigate(Routes.episodes(podcast.id))
                        },
                        onOpenSearch = { navController.navigate(Routes.Search) },
                        onAddFromUrl = { navController.navigate(Routes.addUrl()) },
                        onPlayUrlDownload = { entity ->
                            val episode = urlRepository.toEpisode(entity)
                            if (episode != null) {
                                playerViewModel.playEpisode(episode, entity.thumbnailUrl)
                                navController.navigate(Routes.Player)
                            }
                        },
                        onDeleteUrlDownload = { id -> urlDownloadViewModel.deleteDownload(id) },
                        onCancelUrlDownload = { id -> urlDownloadViewModel.cancelDownload(id) },
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

                composable(
                    route = Routes.AddUrlPattern,
                    arguments = listOf(
                        navArgument(Routes.UrlArg) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val incomingUrl = backStackEntry.arguments?.getString(Routes.UrlArg).orEmpty()
                    val previewState by urlDownloadViewModel.previewState.collectAsState()
                    val selectedMediaType by urlDownloadViewModel.selectedMediaType.collectAsState()

                    AddFromUrlScreen(
                        initialUrl = incomingUrl,
                        previewState = previewState,
                        selectedMediaType = selectedMediaType,
                        onUrlChange = { /* no-op: text field is local */ },
                        onLoadPreview = { urlDownloadViewModel.loadPreview(it) },
                        onSelectMediaType = { urlDownloadViewModel.setMediaType(it) },
                        onConfirm = {
                            urlDownloadViewModel.confirmCurrentDownload()
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        },
                        onBack = {
                            urlDownloadViewModel.resetPreview()
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        },
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
                        onAddFromUrl = { rawUrl ->
                            navController.navigate(Routes.addUrl(rawUrl))
                        },
                        onExportOpml = { exportLauncher.launch("vibe-podcasts.opml") },
                        onImportOpml = {
                            importLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*"))
                        },
                        onOpenSettings = { navController.navigate(Routes.Settings) },
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
                        onToggleAutoDownload = { id, enabled ->
                            podcastViewModel.setQueueAutoDownload(id, enabled)
                        },
                        onBack = {
                            navController.popBackStack(route = Routes.Home, inclusive = false)
                        }
                    )
                }

                composable(Routes.Downloads) {
                    val scope = rememberCoroutineScope()
                    val podcastDownloads by podcastViewModel.downloadedEpisodesUi.collectAsState()
                    val urlDownloads by urlDownloadViewModel.completedDownloads.collectAsState()

                    val urlRepository = remember(context) {
                        com.podcastplayer.app.data.repository.UrlDownloadRepository(context)
                    }

                    val entries = remember(podcastDownloads, urlDownloads) {
                        buildList {
                            podcastDownloads.forEach { item ->
                                add(
                                    DownloadEntryUi(
                                        id = "podcast:${item.episode.id}",
                                        kind = DownloadEntryUi.Kind.PODCAST,
                                        title = item.episode.title,
                                        subtitle = item.podcastTitle,
                                        artworkUrl = item.episode.imageUrl ?: item.podcastArtworkUrl,
                                        episode = item.episode,
                                    ),
                                )
                            }
                            urlDownloads.forEach { entity ->
                                val episode = urlRepository.toEpisode(entity) ?: return@forEach
                                add(
                                    DownloadEntryUi(
                                        id = "url:${entity.id}",
                                        kind = if (entity.mediaType == "video")
                                            DownloadEntryUi.Kind.URL_VIDEO
                                        else DownloadEntryUi.Kind.URL_AUDIO,
                                        title = entity.title,
                                        subtitle = entity.uploader ?: entity.source.uppercase(),
                                        artworkUrl = entity.thumbnailUrl,
                                        episode = episode,
                                    ),
                                )
                            }
                        }
                    }

                    DownloadsScreen(
                        entries = entries,
                        onPlay = { entry ->
                            playerViewModel.playEpisode(entry.episode, entry.artworkUrl)
                            navController.navigate(Routes.Player)
                        },
                        onDelete = { entry ->
                            scope.launch {
                                when (entry.kind) {
                                    DownloadEntryUi.Kind.PODCAST ->
                                        podcastViewModel.deleteDownload(entry.episode.id)
                                    DownloadEntryUi.Kind.URL_AUDIO,
                                    DownloadEntryUi.Kind.URL_VIDEO -> {
                                        // entry.id format: "url:<entity-id>"
                                        urlDownloadViewModel.deleteDownload(entry.id.removePrefix("url:"))
                                    }
                                }
                            }
                        },
                        onDeleteAll = {
                            scope.launch {
                                podcastViewModel.deleteAllDownloads()
                                urlDownloads.forEach { urlDownloadViewModel.deleteDownload(it.id) }
                            }
                        },
                        onBack = { navController.popBackStack(route = Routes.Home, inclusive = false) }
                    )
                }

                composable(Routes.Settings) {
                    val themeMode by appSettings.themeMode.collectAsState()
                    val defaultSpeed by appSettings.defaultPlaybackSpeed.collectAsState()
                    val autoDownloadOnCellular by appSettings.autoDownloadOnCellular.collectAsState()

                    SettingsScreen(
                        themeMode = themeMode,
                        defaultPlaybackSpeed = defaultSpeed,
                        autoDownloadOnCellular = autoDownloadOnCellular,
                        appVersion = BuildConfig.VERSION_NAME,
                        onThemeChange = appSettings::setThemeMode,
                        onPlaybackSpeedChange = appSettings::setDefaultPlaybackSpeed,
                        onAutoDownloadCellularChange = { enabled ->
                            appSettings.setAutoDownloadOnCellular(enabled)
                            // Re-schedule the worker so the new network constraint takes effect.
                            com.podcastplayer.app.service.AutoDownloadWorker.reschedule(
                                context, allowCellular = enabled,
                            )
                        },
                        onBack = { navController.popBackStack() },
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
                    val resumedFromMs by playerViewModel.resumedFromMs.collectAsState()

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
                        Box(modifier = Modifier.fillMaxSize()) {
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
                            ResumeNotice(
                                positionMs = resumedFromMs,
                                onConsume = { playerViewModel.consumeResumeNotice() },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
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
