package com.podcastplayer.app.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide user preferences. SharedPreferences-backed for simplicity (small,
 * structured, doesn't need streaming). Each property is exposed as a
 * `StateFlow<…>` so Compose can observe changes without an explicit refresh.
 *
 * Designed as a process-wide singleton; instantiating multiple instances
 * with the same context backing them is safe (they read/write the same prefs)
 * but only one will have a "live" flow tracking external writes — keep
 * one instance per app process.
 */
class AppSettings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(loadSpeed())
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _autoDownloadOnCellular = MutableStateFlow(loadAutoDlCellular())
    val autoDownloadOnCellular: StateFlow<Boolean> = _autoDownloadOnCellular.asStateFlow()

    private val _autoDownloadRetentionLimit = MutableStateFlow(loadAutoDownloadRetentionLimit())
    val autoDownloadRetentionLimit: StateFlow<Int> = _autoDownloadRetentionLimit.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        prefs.edit { putFloat(KEY_SPEED, clamped) }
        _defaultPlaybackSpeed.value = clamped
    }

    fun setAutoDownloadOnCellular(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTODL_CELL, enabled) }
        _autoDownloadOnCellular.value = enabled
    }

    fun setAutoDownloadRetentionLimit(limit: Int) {
        val accepted = limit.takeIf { it in AUTO_DOWNLOAD_RETENTION_LIMITS } ?: DEFAULT_RETENTION_LIMIT
        prefs.edit { putInt(KEY_AUTODL_RETENTION, accepted) }
        _autoDownloadRetentionLimit.value = accepted
    }

    private fun loadTheme(): ThemeMode {
        val raw = prefs.getString(KEY_THEME, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun loadSpeed(): Float = prefs.getFloat(KEY_SPEED, 1.0f).coerceIn(MIN_SPEED, MAX_SPEED)

    private fun loadAutoDlCellular(): Boolean = prefs.getBoolean(KEY_AUTODL_CELL, false)

    private fun loadAutoDownloadRetentionLimit(): Int {
        val value = prefs.getInt(KEY_AUTODL_RETENTION, DEFAULT_RETENTION_LIMIT)
        return value.takeIf { it in AUTO_DOWNLOAD_RETENTION_LIMITS } ?: DEFAULT_RETENTION_LIMIT
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        val PLAYBACK_SPEEDS = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val UNLIMITED_RETENTION = -1
        const val DEFAULT_RETENTION_LIMIT = 3
        val AUTO_DOWNLOAD_RETENTION_LIMITS = intArrayOf(1, 3, 5, 10, UNLIMITED_RETENTION)

        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SPEED = "default_playback_speed"
        private const val KEY_AUTODL_CELL = "autodownload_cellular"
        private const val KEY_AUTODL_RETENTION = "autodownload_retention_limit"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Theme override. `SYSTEM` follows the OS dark-mode setting; `LIGHT` and `DARK`
 * lock the app regardless of OS.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
