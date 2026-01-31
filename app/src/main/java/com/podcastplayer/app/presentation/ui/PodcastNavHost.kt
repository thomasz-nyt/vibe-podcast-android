package com.podcastplayer.app.presentation.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.presentation.viewmodel.PlayerViewModel
import com.podcastplayer.app.presentation.viewmodel.PodcastViewModel
import com.podcastplayer.app.service.PlayerController

private object Routes {
    const val Search = "search"
    const val Queue = "queue"
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

    // Keep ViewModel scoping identical to the previous implementation (created once at the top level).
    val podcastViewModel: PodcastViewModel = viewModel(
        factory = PodcastViewModelFactory(
            PodcastRepository(iTunesApi.create(), RssParser()),
            DownloadManager(context),
            SavedPodcastsStorage(context),
            db.playbackProgressDao()
        )
    )
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(
            PlayerController.getInstance(context)
        )
    )

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Search
    ) {
        composable(Routes.Search) {
            PodcastListScreen(
                viewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onPodcastSelected = { podcast: Podcast ->
                    podcastViewModel.selectPodcast(podcast)
                    navController.navigate(Routes.episodes(podcast.id))
                },
                onOpenPlayer = { navController.navigate(Routes.Player) },
                onOpenQueue = { navController.navigate(Routes.Queue) }
            )
        }

        composable(
            route = Routes.EpisodesPattern,
            arguments = listOf(
                navArgument(Routes.PodcastIdArg) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // Ensure the ViewModel is aligned with the route arg (e.g., when navigating here from Queue).
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
                    // Match previous behavior: Episodes back always returns to Search.
                    navController.popBackStack(route = Routes.Search, inclusive = false)
                },
                onPlayEpisode = { navController.navigate(Routes.Player) },
                onOpenPlayer = { navController.navigate(Routes.Player) }
            )
        }

        composable(Routes.Queue) {
            val savedPodcasts by podcastViewModel.savedPodcasts.collectAsState()
            val currentEpisode by playerViewModel.currentEpisode.collectAsState()
            val playerState by playerViewModel.playerState.collectAsState()
            val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()

            QueueScreen(
                podcasts = savedPodcasts,
                currentEpisode = currentEpisode,
                currentArtworkUrl = currentArtworkUrl,
                playerState = playerState,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onOpenPlayer = { navController.navigate(Routes.Player) },
                onSeek = { playerViewModel.seekTo(it) },
                onMove = { from, to -> podcastViewModel.moveSavedPodcast(from, to) },
                onRemove = { podcastId -> podcastViewModel.removeSavedPodcast(podcastId) },
                onPlayQueue = {
                    savedPodcasts.firstOrNull()?.let { first ->
                        podcastViewModel.selectPodcast(first)
                        navController.navigate(Routes.episodes(first.id)) {
                            // Match previous behavior: Queue -> Episodes should not return to Queue on back.
                            popUpTo(Routes.Search) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = {
                    // Match previous behavior: Queue back always returns to Search.
                    navController.popBackStack(route = Routes.Search, inclusive = false)
                }
            )
        }

        composable(Routes.Player) {
            val currentEpisode by playerViewModel.currentEpisode.collectAsState()
            val playerState by playerViewModel.playerState.collectAsState()
            val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsState()
            val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
            val selectedPodcast by podcastViewModel.selectedPodcast.collectAsState()

            fun goToEpisodesOrSearch() {
                val podcastId = selectedPodcast?.id
                if (podcastId == null) {
                    navController.popBackStack(route = Routes.Search, inclusive = false)
                } else {
                    navController.navigate(Routes.episodes(podcastId)) {
                        // Match previous behavior: Player back always returns to Episodes (and then to Search).
                        popUpTo(Routes.Search) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }

            BackHandler {
                goToEpisodesOrSearch()
            }

            PlayerScreen(
                episode = currentEpisode ?: Episode("", "", "", null, null, "", null, null),
                playerState = playerState,
                artworkUrl = currentArtworkUrl ?: selectedPodcast?.artworkUrl,
                sleepTimerRemaining = sleepTimerRemaining,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSeek = { playerViewModel.seekTo(it) },
                onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
                onSetSleepTimer = { playerViewModel.setSleepTimer(it) },
                onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
                onDismiss = { goToEpisodesOrSearch() }
            )
        }
    }
}
