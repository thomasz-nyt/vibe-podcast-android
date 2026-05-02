package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.podcastplayer.app.service.PlayerController

/**
 * Compose surface that renders the active Media3 player's video output (issue #33).
 *
 * The screen above already drives playback through [PlayerController]; this
 * composable just attaches a `PlayerView` to the same [MediaController] so the
 * frames render. Custom transport controls live in the parent — we set
 * `useController = false` and only show the surface.
 *
 * Until the `MediaController` is available (the future resolves async), we
 * render a placeholder background so the layout doesn't jump.
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
) {
    val context = LocalContext.current
    val controller = remember { PlayerController.getInstance(context) }
    var player by remember { mutableStateOf<Player?>(null) }

    LaunchedEffect(controller) {
        // Suspends until the MediaController is connected.
        player = controller.awaitController()
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.surfaceVariant),
    ) {
        val current = player
        if (current != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        // Match the rounded corners visually — surface is already clipped.
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    if (view.player !== current) {
                        view.player = current
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

