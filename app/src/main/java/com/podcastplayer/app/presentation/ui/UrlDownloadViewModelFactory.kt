package com.podcastplayer.app.presentation.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.podcastplayer.app.presentation.viewmodel.UrlDownloadViewModel

/**
 * Manual factory for [UrlDownloadViewModel] — matches the existing convention in
 * this codebase (no DI framework). Constructed with the [Application] so the VM
 * can pull the repository / service without holding an Activity reference.
 */
class UrlDownloadViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UrlDownloadViewModel::class.java)) {
            return UrlDownloadViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
