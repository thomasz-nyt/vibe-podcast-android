package com.podcastplayer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VibeDarkColorScheme = darkColorScheme(
    primary = VibeDarkAccent,
    onPrimary = VibeDarkAccentInk,
    primaryContainer = VibeDarkAccentSoft,
    onPrimaryContainer = VibeDarkAccent,

    secondary = VibeDarkFgMuted,
    onSecondary = VibeDarkBg,
    secondaryContainer = VibeDarkSurface2,
    onSecondaryContainer = VibeDarkFg,

    tertiary = VibeDarkSuccess,
    onTertiary = VibeDarkBg,

    background = VibeDarkBg,
    onBackground = VibeDarkFg,
    surface = VibeDarkSurface,
    onSurface = VibeDarkFg,
    surfaceVariant = VibeDarkSurface2,
    onSurfaceVariant = VibeDarkFgMuted,

    outline = VibeDarkBorderStrong,
    outlineVariant = VibeDarkBorder,

    error = VibeDarkDanger,
    onError = VibeDarkFg,
)

private val VibeLightColorScheme = lightColorScheme(
    primary = VibeLightAccent,
    onPrimary = VibeLightAccentInk,
    primaryContainer = VibeLightAccentSoft,
    onPrimaryContainer = VibeLightAccent,

    secondary = VibeLightFgMuted,
    onSecondary = VibeLightBg,
    secondaryContainer = VibeLightSurface2,
    onSecondaryContainer = VibeLightFg,

    tertiary = VibeLightSuccess,
    onTertiary = VibeLightBg,

    background = VibeLightBg,
    onBackground = VibeLightFg,
    surface = VibeLightSurface,
    onSurface = VibeLightFg,
    surfaceVariant = VibeLightSurface2,
    onSurfaceVariant = VibeLightFgMuted,

    outline = VibeLightBorderStrong,
    outlineVariant = VibeLightBorder,

    error = VibeLightDanger,
    onError = VibeLightFg,
)

@Composable
fun PodcastPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VibeDarkColorScheme else VibeLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
