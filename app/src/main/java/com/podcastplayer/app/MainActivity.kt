package com.podcastplayer.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.repository.UrlValidator
import com.podcastplayer.app.presentation.ui.PodcastNavHost
import com.podcastplayer.app.ui.theme.PodcastPlayerTheme

class MainActivity : ComponentActivity() {

    /**
     * URL handed in via [Intent.ACTION_SEND] (a "Share to" from YouTube / X / etc.)
     * or [Intent.ACTION_VIEW]. Surfaced into [PodcastNavHost] so it can route to
     * the AddFromUrl screen.
     *
     * Backed by a Compose [androidx.compose.runtime.MutableState] so re-launches
     * (singleTask) propagate to the UI.
     */
    private var pendingShareUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingShareUrl = extractSharedUrl(intent)
        val settings = AppSettings.getInstance(this)
        setContent {
            val themeMode by settings.themeMode.collectAsState()
            PodcastPlayerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PodcastNavHost(
                        sharedUrl = pendingShareUrl,
                        onSharedUrlConsumed = { pendingShareUrl = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a share while the app is open re-enters here.
        val incoming = extractSharedUrl(intent)
        if (incoming != null) pendingShareUrl = incoming
    }

    /**
     * Pull the first URL out of an inbound share intent. Returns null if the intent
     * doesn't carry one (e.g. plain text without an http(s) link).
     */
    private fun extractSharedUrl(intent: Intent?): String? {
        intent ?: return null
        val text: CharSequence? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        return UrlValidator.extractFirstUrl(text)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS,
        )
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }
}
