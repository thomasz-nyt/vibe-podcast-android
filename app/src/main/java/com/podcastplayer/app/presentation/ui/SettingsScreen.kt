package com.podcastplayer.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.ThemeMode
import com.podcastplayer.app.ui.theme.JetBrainsMono

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    defaultPlaybackSpeed: Float,
    autoDownloadOnCellular: Boolean,
    autoDownloadRetentionLimit: Int,
    appVersion: String,
    onThemeChange: (ThemeMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onAutoDownloadCellularChange: (Boolean) -> Unit,
    onAutoDownloadRetentionChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        VibeTopBar(
            title = "Settings",
            eyebrow = "Preferences",
            onBack = onBack,
        )

        SettingsGroup(label = "Appearance") {
            ThemePicker(current = themeMode, onSelect = onThemeChange)
        }

        SettingsGroup(label = "Playback") {
            SpeedPicker(current = defaultPlaybackSpeed, onSelect = onPlaybackSpeedChange)
        }

        SettingsGroup(label = "Downloads") {
            SwitchRow(
                icon = Icons.Outlined.CloudDownload,
                title = "Auto-download on cellular",
                subtitle = "When off, auto-download only runs on Wi-Fi.",
                checked = autoDownloadOnCellular,
                onCheckedChange = onAutoDownloadCellularChange,
            )
            DividerHairline()
            AutoDownloadRetentionPicker(
                current = autoDownloadRetentionLimit,
                onSelect = onAutoDownloadRetentionChange,
            )
        }

        SettingsGroup(label = "About") {
            InfoRow(
                icon = Icons.Outlined.Info,
                title = "Vibe Podcast",
                subtitle = "Version $appVersion",
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun AutoDownloadRetentionPicker(current: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = "Auto-downloads kept per podcast",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Text(
            text = "Manual and restored downloads are always pinned.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
        )
        AppSettings.AUTO_DOWNLOAD_RETENTION_LIMITS.forEach { limit ->
            val label = if (limit == AppSettings.UNLIMITED_RETENTION) "Unlimited" else limit.toString()
            SelectableRow(
                icon = Icons.Outlined.CloudDownload,
                title = label,
                selected = current == limit,
                onClick = { onSelect(limit) },
            )
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        VibeSectionEyebrow(text = label, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp),
        ) { content() }
    }
}

@Composable
private fun ThemePicker(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeOption(ThemeMode.SYSTEM, "System", Icons.Outlined.Smartphone),
        ThemeOption(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
        ThemeOption(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
    )
    Column {
        options.forEachIndexed { index, opt ->
            SelectableRow(
                icon = opt.icon,
                title = opt.label,
                selected = current == opt.mode,
                onClick = { onSelect(opt.mode) },
            )
            if (index != options.lastIndex) DividerHairline()
        }
    }
}

@Composable
private fun SpeedPicker(current: Float, onSelect: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val speeds = AppSettings.PLAYBACK_SPEEDS
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Default playback speed",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Applied each time you start an episode.",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            speeds.forEach { speed ->
                val active = kotlin.math.abs(current - speed) < 0.01f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) colors.primary else colors.surfaceVariant)
                        .clickable { onSelect(speed) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatSpeedLabel(speed),
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.primary),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.surfaceVariant,
                uncheckedTrackColor = colors.outline,
            ),
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DividerHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        // Drop trailing zero: 1.50 → 1.5
        val trimmed = "%.2f".format(speed).trimEnd('0').trimEnd('.')
        "${trimmed}x"
    }
}

private data class ThemeOption(val mode: ThemeMode, val label: String, val icon: ImageVector)
