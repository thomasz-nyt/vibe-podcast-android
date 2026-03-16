package com.podcastplayer.app.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.podcastplayer.app.presentation.viewmodel.PlayerViewModel
import com.podcastplayer.app.service.PlaybackSessionStorage
import com.podcastplayer.app.service.PlayerController

class PlayerViewModelFactory(
    private val playerController: PlayerController,
    private val playbackSessionStorage: PlaybackSessionStorage
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            return PlayerViewModel(playerController, playbackSessionStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
