package com.podcastplayer.app.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun PodcastNavHost() {
    val context = LocalContext.current
    val podcastViewModel: PodcastViewModel = viewModel(
        factory = PodcastViewModelFactory(
            PodcastRepository(iTunesApi.create(), RssParser()),
            DownloadManager(context),
            SavedPodcastsStorage(context)
        )
    )
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(
            PlayerController.getInstance(context)
        )
    )

    var currentScreen by remember { mutableStateOf("search") }
    var selectedPodcast by remember { mutableStateOf<Podcast?>(null) }

    BackHandler(enabled = currentScreen != "search") {
        when (currentScreen) {
            "player" -> currentScreen = "episodes"
            "episodes" -> {
                currentScreen = "search"
                selectedPodcast = null
            }
        }
    }

    when (currentScreen) {
        "search" -> {
            PodcastListScreen(
                viewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onPodcastSelected = { podcast ->
                    selectedPodcast = podcast
                    podcastViewModel.selectPodcast(podcast)
                    currentScreen = "episodes"
                },
                onOpenPlayer = { currentScreen = "player" }
            )
        }
        "episodes" -> {
            EpisodeListScreen(
                podcast = selectedPodcast,
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onBack = {
                    currentScreen = "search"
                    selectedPodcast = null
                },
                onPlayEpisode = {
                    currentScreen = "player"
                },
                onOpenPlayer = { currentScreen = "player" }
            )
        }
        "player" -> {
            val currentEpisode by playerViewModel.currentEpisode.collectAsState()
            val playerState by playerViewModel.playerState.collectAsState()
            val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsState()
            val currentArtworkUrl by playerViewModel.currentArtworkUrl.collectAsState()
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
                onDismiss = { currentScreen = "episodes" }
            )
        }
    }
}
