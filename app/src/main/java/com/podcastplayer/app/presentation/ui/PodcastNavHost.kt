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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.podcastplayer.app.data.local.CanonicalMediaReference
import com.podcastplayer.app.data.local.MediaPayloadAvailability
import com.podcastplayer.app.data.local.MediaStoreScanner
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.ManualDownloadRepository
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
import androidx.compose.runtime.produceState
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

    const val AddFeedBase = "add-feed"
    const val FeedUrlArg = "feedUrl"
    const val AddFeedPattern = "$AddFeedBase?$FeedUrlArg={$FeedUrlArg}"

    fun addFeed(rawUrl: String? = null): String =
        if (rawUrl.isNullOrBlank()) AddFeedBase else "$AddFeedBase?$FeedUrlArg=${Uri.encode(rawUrl)}"
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
    val urlDownloadRepositoryShared = remember {
        com.podcastplayer.app.data.repository.UrlDownloadRepository(context)
    }
    val mediaScanner = remember { com.podcastplayer.app.data.local.MediaStoreScanner(context) }
    val podcastViewModel: PodcastViewModel = viewModel(
        factory = PodcastViewModelFactory(
            PodcastRepository(iTunesApi.create(), RssParser()),
            DownloadManager(context),
            ManualDownloadRepository(context),
            SavedPodcastsStorage(context),
            queueStorage,
            db.playbackProgressDao(),
            urlDownloadRepositoryShared,
            mediaScanner,
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

    // Auto-play Morning queue when app is opened before 8:30 AM (issue #4).
    LaunchedEffect(Unit) {
        val now = java.time.LocalTime.now()
        if (now >= java.time.LocalTime.of(8, 30)) return@LaunchedEffect

        // Wait for session restore in PlayerViewModel.init to complete.
        delay(2000)

        // Only skip auto-play if something is actively playing/loading (not paused/restored).
        val playbackState = playerViewModel.playerState.value.state
        if (playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.LOADING) return@LaunchedEffect

        // Find "Morning" queue and resolve its podcasts.
        val morningPayload = queueStorage.queues.value
            .firstOrNull { it.name.equals("Morning", ignoreCase = true) }
            ?: return@LaunchedEffect

        val savedMap = podcastViewModel.savedPodcasts.value.associateBy { it.id }
        val podcasts = morningPayload.podcastIds.mapNotNull { savedMap[it] }
        if (podcasts.isEmpty()) return@LaunchedEffect

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
        val route = if (com.podcastplayer.app.data.repository.UrlSource.classify(url) ==
            com.podcastplayer.app.data.repository.UrlSource.OTHER
        ) {
            Routes.addFeed(url)
        } else {
            Routes.addUrl(url)
        }
        navController.navigate(route) {
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
                    val urlNeedsAttention by urlDownloadViewModel.needsAttentionDownloads.collectAsState()

                    val urlRepository = remember(context) {
                        com.podcastplayer.app.data.repository.UrlDownloadRepository(context)
                    }

                    HomeScreen(
                        subscriptions = savedPodcasts,
                        continueListening = continueListening,
                        urlDownloads = urlDownloads,
                        urlInFlight = urlInFlight,
                        urlNeedsAttention = urlNeedsAttention,
                        currentEpisode = currentEpisode,
                        currentArtworkUrl = currentArtworkUrl,
                        playerState = playerState,
                        onOpenPodcast = { podcast ->
                            podcastViewModel.selectPodcast(podcast)
                            navController.navigate(Routes.episodes(podcast.id))
                        },
                        onOpenSearch = { navController.navigate(Routes.Search) },
                        onAddFeed = { navController.navigate(Routes.addFeed()) },
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
                        onRetryUrlDownload = { id -> urlDownloadViewModel.retryDownload(id) },
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

                composable(
                    route = Routes.AddFeedPattern,
                    arguments = listOf(
                        navArgument(Routes.FeedUrlArg) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val incomingUrl = backStackEntry.arguments?.getString(Routes.FeedUrlArg).orEmpty()
                    val feedPreviewState by podcastViewModel.feedPreviewState.collectAsState()

                    AddFeedScreen(
                        initialUrl = incomingUrl,
                        state = feedPreviewState,
                        onLoad = { podcastViewModel.loadFeedPreview(it) },
                        onSubscribe = { podcastViewModel.confirmFeedPreview() },
                        onBack = {
                            podcastViewModel.resetFeedPreview()
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
                        onAddFeed = { rawUrl ->
                            navController.navigate(Routes.addFeed(rawUrl))
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
                    val urlDownloads by urlDownloadViewModel.resolvedCompletedDownloads.collectAsState()
                    val failedUrlDownloads by urlDownloadViewModel.needsAttentionDownloads.collectAsState()
                    val restoreState by podcastViewModel.restoreState.collectAsState()
                    LaunchedEffect(Unit) {
                        podcastViewModel.refreshDownloadAvailability()
                        urlDownloadViewModel.refreshDownloadAvailability()
                    }
                    var maintenanceMessage by remember { mutableStateOf<String?>(null) }
                    var duplicatePlan by remember {
                        mutableStateOf<com.podcastplayer.app.data.local.DuplicateCleanupPlan?>(null)
                    }
                    var showLegacyReview by remember { mutableStateOf(false) }
                    var pendingDeleteContinuation by remember {
                        mutableStateOf<((Boolean) -> Unit)?>(null)
                    }
                    var pendingWriteContinuation by remember {
                        mutableStateOf<((Boolean) -> Unit)?>(null)
                    }

                    // Receives the outcome of the system batch-delete consent dialog
                    // (used by single delete, remove-all, and duplicate cleanup).
                    val deleteLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        val continuation = pendingDeleteContinuation
                        pendingDeleteContinuation = null
                        continuation?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
                    }
                    val writeLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        val continuation = pendingWriteContinuation
                        pendingWriteContinuation = null
                        continuation?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
                    }

                    fun reportActualDeletion(targetUris: List<String>) {
                        scope.launch {
                            when (val scan = mediaScanner.scanAll()) {
                                is MediaStoreScanner.ScanResult.Success -> {
                                    val remaining = scan.items.mapTo(hashSetOf()) { it.canonicalKey }
                                    val actual = targetUris.count {
                                        CanonicalMediaReference.keyOf(it) !in remaining
                                    }
                                    maintenanceMessage = "Removed $actual file" +
                                        if (actual == 1) "." else "s."
                                }
                                is MediaStoreScanner.ScanResult.PermissionRequired ->
                                    maintenanceMessage = "Media access is required to verify deletion."
                                is MediaStoreScanner.ScanResult.Failed ->
                                    maintenanceMessage = "Could not verify deletion: ${scan.message}"
                            }
                        }
                    }

                    fun requestConsentDelete(uris: List<String>) {
                        val targets = uris.distinct()
                        if (targets.isEmpty()) return

                        fun deleteSequential(remaining: List<String>) {
                            val uri = remaining.firstOrNull() ?: run {
                                reportActualDeletion(targets)
                                return
                            }
                            when (val result = mediaScanner.delete(uri)) {
                                MediaStoreScanner.MutationResult.Success -> deleteSequential(remaining.drop(1))
                                MediaStoreScanner.MutationResult.Failed -> deleteSequential(remaining.drop(1))
                                is MediaStoreScanner.MutationResult.NeedsConsent -> {
                                    pendingDeleteContinuation = { granted ->
                                        if (granted) deleteSequential(remaining)
                                        else {
                                            maintenanceMessage = "Canceled — remaining files were kept."
                                            reportActualDeletion(targets)
                                        }
                                    }
                                    deleteLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(
                                            result.pendingIntent.intentSender
                                        ).build()
                                    )
                                }
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                            deleteSequential(targets)
                            return
                        }

                        val needsConsent = targets.filter { uri ->
                            mediaScanner.delete(uri) !is MediaStoreScanner.MutationResult.Success
                        }
                        if (needsConsent.isEmpty()) {
                            reportActualDeletion(targets)
                            return
                        }
                        val request = mediaScanner.createDeleteRequest(needsConsent.map(Uri::parse))
                        if (request == null) {
                            maintenanceMessage = "Some files could not be deleted."
                            reportActualDeletion(targets)
                            return
                        }
                        pendingDeleteContinuation = { granted ->
                            if (!granted) maintenanceMessage = "Canceled — remaining files were kept."
                            reportActualDeletion(targets)
                        }
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                        )
                    }

                    fun startCleanup() {
                        scope.launch {
                            runCatching { podcastViewModel.planDuplicateCleanup() }
                                .onSuccess { plan ->
                                    if (plan.confirmed.isEmpty() && plan.ambiguous.isEmpty()) {
                                        maintenanceMessage = "No duplicate files found."
                                    } else {
                                        duplicatePlan = plan
                                    }
                                }
                                .onFailure { error ->
                                    maintenanceMessage = error.message ?: "Could not inspect duplicate files."
                                }
                        }
                    }

                    fun applyLegacyBatch(
                        matches: List<com.podcastplayer.app.presentation.viewmodel.LegacyRestoreMatch>,
                    ) {
                        scope.launch {
                            var confirmed = 0
                            matches.forEach { match ->
                                if (podcastViewModel.applyLegacyRestoreMatch(match) ==
                                    MediaStoreScanner.MutationResult.Success
                                ) confirmed++
                            }
                            podcastViewModel.finishLegacyRestoreReview(confirmed)
                            showLegacyReview = false
                            maintenanceMessage = "Linked $confirmed confirmed legacy file" +
                                if (confirmed == 1) "." else "s."
                        }
                    }

                    fun applyLegacySequential(
                        matches: List<com.podcastplayer.app.presentation.viewmodel.LegacyRestoreMatch>,
                        confirmedSoFar: Int = 0,
                    ) {
                        val match = matches.firstOrNull() ?: run {
                            podcastViewModel.finishLegacyRestoreReview(confirmedSoFar)
                            showLegacyReview = false
                            maintenanceMessage = "Linked $confirmedSoFar confirmed legacy file" +
                                if (confirmedSoFar == 1) "." else "s."
                            return
                        }
                        scope.launch {
                            when (val result = podcastViewModel.applyLegacyRestoreMatch(match)) {
                                MediaStoreScanner.MutationResult.Success ->
                                    applyLegacySequential(matches.drop(1), confirmedSoFar + 1)
                                MediaStoreScanner.MutationResult.Failed ->
                                    applyLegacySequential(matches.drop(1), confirmedSoFar)
                                is MediaStoreScanner.MutationResult.NeedsConsent -> {
                                    pendingWriteContinuation = { granted ->
                                        if (granted) applyLegacySequential(matches, confirmedSoFar)
                                        else applyLegacySequential(matches.drop(1), confirmedSoFar)
                                    }
                                    writeLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(
                                            result.pendingIntent.intentSender
                                        ).build()
                                    )
                                }
                            }
                        }
                    }

                    fun startLegacyRestore(
                        matches: List<com.podcastplayer.app.presentation.viewmodel.LegacyRestoreMatch>,
                    ) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val request = mediaScanner.createWriteRequest(matches.map { Uri.parse(it.file.uriString) })
                            if (request == null) {
                                applyLegacyBatch(matches)
                            } else {
                                pendingWriteContinuation = { granted ->
                                    if (granted) applyLegacyBatch(matches)
                                    else maintenanceMessage = "Canceled — files stayed unidentified."
                                }
                                writeLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                                )
                            }
                        } else {
                            applyLegacySequential(matches)
                        }
                    }

                    // Media read permission widens restore/remove-all/cleanup to files a
                    // previous install owned. Actions that also work on this install's own
                    // files set [pendingMediaActionProceedsOnDeny] so declining the prompt
                    // degrades gracefully instead of blocking them.
                    var pendingMediaAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var pendingMediaActionProceedsOnDeny by remember { mutableStateOf(false) }
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        val action = pendingMediaAction
                        val proceedAnyway = pendingMediaActionProceedsOnDeny
                        pendingMediaAction = null
                        pendingMediaActionProceedsOnDeny = false
                        if (grants.values.any { it } || proceedAnyway) {
                            action?.invoke()
                        } else {
                            maintenanceMessage =
                                "Media access is needed to find files from a previous install."
                        }
                    }

                    // Offer the media-access prompt but run [action] even when access is
                    // declined: restore and remove-all both work on this install's OWN
                    // files without any permission — access only widens them to files a
                    // previous install left behind (prior-install episodes / duplicates).
                    fun runWithOptionalMediaAccess(action: () -> Unit) {
                        if (podcastViewModel.hasMediaReadPermission()) {
                            action()
                        } else {
                            pendingMediaAction = action
                            pendingMediaActionProceedsOnDeny = true
                            permissionLauncher.launch(podcastViewModel.mediaReadPermissions())
                        }
                    }

                    fun removeAllDownloads() {
                        runWithOptionalMediaAccess {
                            scope.launch {
                                val consent = podcastViewModel.deleteAllDownloads()
                                if (consent.isEmpty()) {
                                    maintenanceMessage = "All downloads removed."
                                } else {
                                    requestConsentDelete(consent)
                                }
                            }
                        }
                    }

                    // Strict variant: the action is pointless without access (duplicate
                    // cleanup must SEE non-owned files to delete them), so deny = stop.
                    fun withMediaPermission(action: () -> Unit) {
                        if (podcastViewModel.hasMediaReadPermission()) {
                            action()
                        } else {
                            pendingMediaAction = action
                            permissionLauncher.launch(podcastViewModel.mediaReadPermissions())
                        }
                    }
                    // Raw entities give us fileSize, which the UI flows above don't carry.
                    val podcastEntities by produceState<List<com.podcastplayer.app.data.local.DownloadedEpisodeEntity>>(
                        initialValue = emptyList(),
                    ) {
                        com.podcastplayer.app.data.repository.DownloadManager(context)
                            .getAllDownloadedEntitiesFlow()
                            .collect { value = it }
                    }

                    val urlRepository = remember(context) {
                        com.podcastplayer.app.data.repository.UrlDownloadRepository(context)
                    }

                    val entries = remember(podcastDownloads, urlDownloads, podcastEntities) {
                        val sizeById = podcastEntities.associate { it.id to it.fileSize }
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
                                        availability = item.availability,
                                        canRepair = item.episode.audioUrl.isNotBlank(),
                                        sizeBytes = if (item.availability is MediaPayloadAvailability.Available) {
                                            sizeById[item.episode.id] ?: 0L
                                        } else {
                                            0L
                                        },
                                    ),
                                )
                            }
                            urlDownloads.forEach { resolved ->
                                val entity = resolved.entity
                                add(
                                    DownloadEntryUi(
                                        id = "url:${entity.id}",
                                        kind = if (entity.mediaType == "video")
                                            DownloadEntryUi.Kind.URL_VIDEO
                                        else DownloadEntryUi.Kind.URL_AUDIO,
                                        title = entity.title,
                                        subtitle = entity.uploader ?: entity.source.uppercase(),
                                        artworkUrl = entity.thumbnailUrl,
                                        episode = urlRepository.toEpisodeMetadata(resolved),
                                        availability = resolved.availability,
                                        canRepair = entity.sourceUrl.isNotBlank(),
                                        sizeBytes = if (resolved.availability is MediaPayloadAvailability.Available) {
                                            entity.fileSize ?: 0L
                                        } else {
                                            0L
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    val totalBytes = remember(entries) { entries.sumOf { it.sizeBytes } }

                    DownloadsScreen(
                        entries = entries,
                        failedUrlDownloads = failedUrlDownloads,
                        totalBytes = totalBytes,
                        onPlay = { entry ->
                            if (entry.availability is MediaPayloadAvailability.Available) {
                                playerViewModel.playEpisode(entry.episode, entry.artworkUrl)
                                navController.navigate(Routes.Player)
                            }
                        },
                        onDelete = { entry ->
                            scope.launch {
                                // Row + owned file are removed immediately; a file owned by a
                                // previous install comes back as a consent URI → system dialog.
                                val consent = when (entry.kind) {
                                    DownloadEntryUi.Kind.PODCAST ->
                                        podcastViewModel.deleteDownload(entry.episode.id)
                                    DownloadEntryUi.Kind.URL_AUDIO,
                                    DownloadEntryUi.Kind.URL_VIDEO ->
                                        // entry.id format: "url:<entity-id>"
                                        podcastViewModel.deleteUrlDownload(entry.id.removePrefix("url:"))
                                }
                                requestConsentDelete(consent)
                            }
                        },
                        onRepair = { entry ->
                            when (entry.kind) {
                                DownloadEntryUi.Kind.PODCAST -> podcastViewModel.startDownload(entry.episode)
                                DownloadEntryUi.Kind.URL_AUDIO,
                                DownloadEntryUi.Kind.URL_VIDEO ->
                                    urlDownloadViewModel.repairMissingDownload(entry.id.removePrefix("url:"))
                            }
                        },
                        onGrantMediaAccess = {
                            withMediaPermission {
                                podcastViewModel.refreshDownloadAvailability()
                                urlDownloadViewModel.refreshDownloadAvailability()
                            }
                        },
                        onRetryUrlDownload = { id -> urlDownloadViewModel.retryDownload(id) },
                        onDeleteUrlDownload = { id -> urlDownloadViewModel.deleteDownload(id) },
                        onDeleteAll = { deleteFiles ->
                            if (deleteFiles) {
                                removeAllDownloads()
                            } else {
                                // Keep-files variant: clear the list only; the media stays on
                                // the phone for Restore / instant re-download reuse.
                                scope.launch {
                                    podcastViewModel.removeAllDownloadsKeepingFiles()
                                    maintenanceMessage = "List cleared — media files kept on this " +
                                        "phone. Use \"Restore previous downloads\" to bring them back."
                                }
                            }
                        },
                        restoreState = restoreState,
                        maintenanceMessage = maintenanceMessage,
                        onRestoreDownloads = {
                            // Optional access: files kept via "Remove all → keep files" are
                            // owned by this install and restore without any permission.
                            runWithOptionalMediaAccess { podcastViewModel.restorePreviousDownloads() }
                        },
                        onReviewLegacyMatches = { showLegacyReview = true },
                        onCleanupDuplicates = { withMediaPermission { startCleanup() } },
                        onDismissRestoreResult = { podcastViewModel.dismissRestoreResult() },
                        onDismissMaintenanceMessage = { maintenanceMessage = null },
                        onBack = { navController.popBackStack(route = Routes.Home, inclusive = false) }
                    )

                    val legacyState = restoreState as? com.podcastplayer.app.presentation.viewmodel.RestoreDownloadsState.ReviewLegacy
                    if (showLegacyReview && legacyState != null) {
                        LegacyRestoreReviewDialog(
                            matches = legacyState.suggestions,
                            onConfirm = ::startLegacyRestore,
                            onDismiss = {
                                showLegacyReview = false
                                podcastViewModel.finishLegacyRestoreReview(0)
                            },
                        )
                    }

                    duplicatePlan?.let { plan ->
                        DuplicateCleanupDialog(
                            plan = plan,
                            onConfirm = { uris ->
                                duplicatePlan = null
                                requestConsentDelete(uris)
                            },
                            onDismiss = { duplicatePlan = null },
                        )
                    }
                }

                composable(Routes.Settings) {
                    val scope = rememberCoroutineScope()
                    val themeMode by appSettings.themeMode.collectAsState()
                    val defaultSpeed by appSettings.defaultPlaybackSpeed.collectAsState()
                    val autoDownloadOnCellular by appSettings.autoDownloadOnCellular.collectAsState()
                    val retentionLimit by appSettings.autoDownloadRetentionLimit.collectAsState()
                    var pendingRetentionLimit by remember { mutableStateOf<Int?>(null) }
                    var pendingRetentionTrimCount by remember { mutableStateOf(0) }

                    SettingsScreen(
                        themeMode = themeMode,
                        defaultPlaybackSpeed = defaultSpeed,
                        autoDownloadOnCellular = autoDownloadOnCellular,
                        autoDownloadRetentionLimit = retentionLimit,
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
                        onAutoDownloadRetentionChange = { requested ->
                            val lowering = retentionLimit == AppSettings.UNLIMITED_RETENTION ||
                                (requested != AppSettings.UNLIMITED_RETENTION && requested < retentionLimit)
                            if (!lowering) {
                                appSettings.setAutoDownloadRetentionLimit(requested)
                            } else {
                                scope.launch {
                                    pendingRetentionTrimCount =
                                        com.podcastplayer.app.service.AutoDownloadRetentionManager(context)
                                            .previewTrimCount(requested)
                                    pendingRetentionLimit = requested
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )

                    val proposedLimit = pendingRetentionLimit
                    if (proposedLimit != null) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { pendingRetentionLimit = null },
                            title = { androidx.compose.material3.Text("Lower auto-download limit?") },
                            text = {
                                androidx.compose.material3.Text(
                                    "$pendingRetentionTrimCount older automatic file" +
                                        (if (pendingRetentionTrimCount == 1) " is" else "s are") +
                                        " eligible for removal. Manual and restored downloads stay pinned."
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        appSettings.setAutoDownloadRetentionLimit(proposedLimit)
                                        pendingRetentionLimit = null
                                        scope.launch {
                                            com.podcastplayer.app.service.AutoDownloadRetentionManager(context)
                                                .trimAll(proposedLimit)
                                        }
                                    },
                                ) { androidx.compose.material3.Text("Apply and trim") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { pendingRetentionLimit = null },
                                ) { androidx.compose.material3.Text("Cancel") }
                            },
                        )
                    }
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
