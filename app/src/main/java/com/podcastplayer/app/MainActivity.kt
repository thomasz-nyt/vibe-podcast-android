package com.podcastplayer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        pendingShareUrl = extractSharedUrl(intent)
        setContent {
            PodcastPlayerTheme {
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
}
