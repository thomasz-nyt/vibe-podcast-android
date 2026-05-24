package com.podcastplayer.app.util

import android.util.Log

/**
 * Thin logging facade so we don't sprinkle `android.util.Log` everywhere.
 *
 * Debug logs are gated by [Log.isLoggable] — clear `logcat` of `Vibe:D` lines
 * in production by default, but flip on locally with:
 *     adb shell setprop log.tag.Vibe DEBUG
 *
 * Warnings + errors always log so user-reported issues are diagnosable from
 * `adb logcat` output.
 */
object Logger {

    private const val DEFAULT_TAG = "Vibe"

    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (Log.isLoggable(tag, Log.DEBUG)) Log.d(tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
