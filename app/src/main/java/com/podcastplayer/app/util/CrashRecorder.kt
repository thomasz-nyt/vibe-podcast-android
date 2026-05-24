package com.podcastplayer.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches uncaught exceptions and appends a structured record to a rolling
 * file in `filesDir/crashes.log`. Always defers to the previously-installed
 * default handler after writing, so we don't suppress system behavior
 * (like Android's "App keeps stopping" dialog or Play Console crash reporting).
 *
 * Intentionally avoids any third-party crash SDK — keeps APK size flat and
 * doesn't require user consent for analytics. The user-visible value is
 * via a future "Send crash log" action in settings (TODO).
 */
object CrashRecorder {

    private const val LOG_FILE = "crashes.log"
    private const val MAX_BYTES = 256 * 1024 // 256 KB rolling cap

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                appendCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Best-effort — don't shadow the original crash with an IO error.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the on-disk crash log (may not exist if there's nothing to report). */
    fun crashLogFile(context: Context): File = File(context.filesDir, LOG_FILE)

    private fun appendCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashLogFile(context)

        // Roll the file if it's grown past the cap — simplest implementation that
        // keeps the most recent crash visible without unbounded growth.
        if (file.exists() && file.length() > MAX_BYTES) file.delete()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        file.appendText(
            """
            |── $timestamp ──
            |thread: ${thread.name}
            |error: ${throwable.javaClass.simpleName}: ${throwable.message}
            |
            |$stack
            |
            """.trimMargin(),
        )
    }
}
