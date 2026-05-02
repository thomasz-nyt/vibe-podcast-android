package com.podcastplayer.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Responsible for:
 * - Initializing the bundled yt-dlp runtime (youtubedl-android) and ffmpeg on first launch.
 *   Both calls are idempotent — they no-op when already initialized.
 * - Optionally updating the yt-dlp Python script in the background so extraction keeps working
 *   even when YouTube/X change their internal APIs (yt-dlp updates frequently).
 *
 * The init runs on a background dispatcher; consumers (e.g. [com.podcastplayer.app.data.repository.UrlDownloadRepository])
 * await [youtubeDlReady] before invoking yt-dlp.
 */
class PodcastApplication : Application() {

    /** Best-effort background scope for one-shot init / update tasks. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        appScope.launch {
            initYoutubeDl()
            // Fire-and-forget update; safe to skip if it fails.
            tryUpdateYoutubeDl()
        }
    }

    private fun initYoutubeDl() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            youtubeDlReady = true
            Log.i(TAG, "YoutubeDL + FFmpeg initialized")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error initializing YoutubeDL", e)
        }
    }

    private fun tryUpdateYoutubeDl() {
        if (!youtubeDlReady) return
        try {
            // Updates the bundled yt-dlp Python script in-place from upstream.
            // Throws on network failure — which is fine; we just keep using the bundled version.
            YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
            Log.i(TAG, "yt-dlp updated")
        } catch (e: Throwable) {
            Log.w(TAG, "yt-dlp update skipped: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PodcastApplication"

        /**
         * True once [YoutubeDL.init] has succeeded.
         *
         * Repository code should bail out gracefully (with a user-visible error) if this is still
         * false when a download is requested — typically only happens on first launch with no
         * network connection, since init unpacks the bundled Python runtime locally.
         */
        @Volatile
        var youtubeDlReady: Boolean = false
            private set

        @Volatile
        private var instance: PodcastApplication? = null

        fun get(): PodcastApplication =
            requireNotNull(instance) { "PodcastApplication not initialized" }
    }
}
