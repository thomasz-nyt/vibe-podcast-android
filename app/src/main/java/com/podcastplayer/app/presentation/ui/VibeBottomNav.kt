package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vibe bottom nav — 4 tabs with pill-tinted active state.
 *
 * Integration: wrap [PodcastNavHost]'s NavHost in a Scaffold with bottomBar,
 * or embed directly in each tab-level screen. Pass the current route id as [active].
 */
enum class VibeTab(val id: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Search("search", "Search", Icons.Outlined.Search),
    Queue("queue", "Queue", Icons.Outlined.PlaylistPlay),
    Downloads("downloads", "Downloads", Icons.Outlined.Download),
}

@Composable
fun VibeBottomNav(
    active: String,
    onNavigate: (VibeTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<VibeTab> = VibeTab.entries,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .border(
                width = 1.dp, color = colors.outlineVariant,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isActive = active == tab.id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isActive) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}
