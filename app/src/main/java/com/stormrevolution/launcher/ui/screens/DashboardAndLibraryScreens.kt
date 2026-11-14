package com.stormrevolution.launcher.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.StormAmber
import com.example.ui.theme.StormCardBorder
import com.example.ui.theme.StormCyan
import com.example.ui.theme.StormError
import com.example.ui.theme.StormObsidian
import com.example.ui.theme.StormSuccess
import com.example.ui.theme.StormSurface
import com.example.ui.theme.StormTextSecondary
import com.example.ui.theme.StormWarning
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.model.GamePreset
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormSectionCard
import com.stormrevolution.launcher.ui.components.TelemetryKeyValueRow

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "splash_pulse").animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF13203A), StormObsidian)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_storm_launcher_fg),
                contentDescription = stringResource(R.string.cd_launcher_emblem),
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .alpha(pulse),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "STORM REVOLUTION",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ARM64 Windows Compatibility Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = StormCyan
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = StormAmber,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Probing Vulkan, Wine WoW64 & Box64...",
                style = MaterialTheme.typography.labelSmall,
                color = StormTextSecondary
            )
        }
    }
}

@Composable
fun DashboardScreen(
    state: LauncherUiState,
    onPlayClick: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenGameFiles: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenGraphics: () -> Unit,
    onOpenSystemInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameReadyToPlay = state.gameResult.status == GameStatus.READY_TO_LAUNCH
    val wineState = state.runtimeComponents.firstOrNull { it.type == RuntimeComponentType.WINE }
    val box64State = state.runtimeComponents.firstOrNull { it.type == RuntimeComponentType.BOX64 }
    val box86State = state.runtimeComponents.firstOrNull { it.type == RuntimeComponentType.BOX86 }
    val wow64State = state.runtimeComponents.firstOrNull { it.type == RuntimeComponentType.WOW64 }

    val runtimeOverallReady = wineState?.installStatus == InstallStatus.VERIFIED &&
        ((box64State?.installStatus == InstallStatus.VERIFIED && wow64State?.installStatus == InstallStatus.VERIFIED) ||
            box86State?.installStatus == InstallStatus.VERIFIED)

    val gameStatusTone = when (state.gameResult.status) {
        GameStatus.READY_TO_LAUNCH, GameStatus.GAME_READY -> StatusTone.OK
        GameStatus.RUNTIME_MISSING, GameStatus.GAME_FOUND -> StatusTone.WARN
        GameStatus.GAME_NOT_FOUND, GameStatus.GAME_VALIDATION_FAILED -> StatusTone.ERROR
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, StormCardBorder),
                colors = CardDefaults.cardColors(containerColor = StormSurface)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_storm_banner),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        StormSurface.copy(alpha = 0.88f),
                                        StormSurface
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(
                                label = state.gameResult.status.label.uppercase(),
                                tone = gameStatusTone
                            )
                            StatusPill(
                                label = "${state.profile.resolutionWidth}x${state.profile.resolutionHeight} • ${state.profile.fpsLimit} FPS",
                                tone = StatusTone.INFO
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "STORM REVOLUTION",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution • PC x86 Runtime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StormTextSecondary
                        )
                    }
                }
            }
        }

        // Primary Action Row: PLAY + Quick Actions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    enabled = gameReadyToPlay && !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("play_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StormAmber,
                        contentColor = StormObsidian,
                        disabledContainerColor = StormSurface,
                        disabledContentColor = StormTextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (gameReadyToPlay) "PLAY STORM REVOLUTION" else "PLAY LOCKED (${state.gameResult.status.label.uppercase()})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionTile(
                        title = "CONFIGURATION",
                        subtitle = state.profile.profileName,
                        icon = Icons.Default.Tune,
                        onClick = onOpenConfig,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_configuration")
                    )
                    DashboardActionTile(
                        title = "RUNTIME MANAGER",
                        subtitle = if (runtimeOverallReady) "Verified" else "Setup Needed",
                        icon = Icons.Default.DeveloperBoard,
                        onClick = onOpenRuntime,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_runtime_manager")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionTile(
                        title = "GAME FILES",
                        subtitle = if (state.gameResult.executableSizeBytes > 0) "NSUNSR.exe Ready" else "Import Folder",
                        icon = Icons.Default.FolderOpen,
                        onClick = onOpenGameFiles,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_game_files")
                    )
                    DashboardActionTile(
                        title = "DEBUG",
                        subtitle = "${state.logs.size} log entries",
                        icon = Icons.Default.BugReport,
                        onClick = onOpenDebug,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_debug")
                    )
                }
            }
        }

        // System & Runtime Readiness Card
        item {
            StormSectionCard(
                title = "Runtime & Compatibility Status",
                icon = Icons.Default.Memory,
                badge = {
                    StatusPill(
                        label = if (runtimeOverallReady) "RUNTIME READY" else "RUNTIME MISSING",
                        tone = if (runtimeOverallReady) StatusTone.OK else StatusTone.WARN
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow(
                    key = "Runtime Status",
                    value = if (runtimeOverallReady) "Wine + Translator Verified" else "Missing Wine / Box64 / WoW64",
                    valueColor = if (runtimeOverallReady) StormSuccess else StormWarning
                )
                TelemetryKeyValueRow(
                    key = "Game Installation",
                    value = state.gameResult.status.label,
                    valueColor = if (gameReadyToPlay) StormSuccess else StormWarning
                )
                TelemetryKeyValueRow(
                    key = "Game Location",
                    value = when {
                        state.gameResult.localExecutablePath != null -> "Private Sandbox (files/game/)"
                        state.gameResult.sourceUriString != null -> "SAF Directory Linked"
                        else -> "Not Selected"
                    },
                    monospace = true
                )
                TelemetryKeyValueRow(
                    key = "Wine Status",
                    value = "${wineState?.installStatus?.label ?: "Unknown"} (${wineState?.version ?: "-"})",
                    valueColor = if (wineState?.installStatus == InstallStatus.VERIFIED) StormSuccess else StormError
                )
                TelemetryKeyValueRow(
                    key = "Box86 / Box64",
                    value = "Box64: ${box64State?.installStatus?.label ?: "-"} • WoW64: ${wow64State?.installStatus?.label ?: "-"} • Box86: ${box86State?.installStatus?.label ?: "-"}",
                    valueColor = if (box64State?.installStatus == InstallStatus.VERIFIED) StormSuccess else StormWarning
                )
                TelemetryKeyValueRow(
                    key = "Graphics Driver",
                    value = "${state.gpuInfo.glRenderer} (${state.effectiveGraphicsBackend.displayName})",
                    valueColor = if (state.gpuInfo.isMaliGpu) StormCyan else MaterialTheme.colorScheme.onSurface
                )
                TelemetryKeyValueRow(
                    key = "Device Memory",
                    value = "${state.telemetry.availableRamMb} MB free / ${state.telemetry.totalRamMb} MB total • Thermal: ${state.telemetry.thermalStatusLabel}",
                    monospace = true
                )
            }
        }

        // Quick Module Shortcuts
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenGraphics,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_quick_graphics")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Graphics", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenControls,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_quick_controls")
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Controls", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenSystemInfo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_quick_sysinfo")
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SysInfo", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DashboardActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(74.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StormSurface),
        border = BorderStroke(1.dp, StormCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = StormCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StormTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun GameLibraryScreen(
    state: LauncherUiState,
    onPickGameFolder: () -> Unit,
    onToggleCopyToPrivate: (Boolean) -> Unit,
    onRescanGame: () -> Unit,
    onApplyPreset: (GamePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val game = state.gameResult
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Game File Manager (SAF)",
                icon = Icons.Default.FolderOpen,
                badge = {
                    StatusPill(
                        label = game.status.label,
                        tone = when (game.status) {
                            GameStatus.READY_TO_LAUNCH, GameStatus.GAME_READY -> StatusTone.OK
                            GameStatus.RUNTIME_MISSING, GameStatus.GAME_FOUND -> StatusTone.WARN
                            else -> StatusTone.ERROR
                        }
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Text(
                    text = "Select your PC installation directory of NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution containing NSUNSR.exe via Android Storage Access Framework. Original files are never modified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StormTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Copy to Private Sandbox",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Recommended for Wine POSIX file access speed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StormTextSecondary
                        )
                    }
                    Switch(
                        checked = state.copyGameToPrivate,
                        onCheckedChange = onToggleCopyToPrivate,
                        modifier = Modifier.testTag("switch_copy_private")
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickGameFolder,
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_pick_game_folder")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select Game Folder")
                    }
                    OutlinedButton(
                        onClick = onRescanGame,
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_rescan_game")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan game files")
                    }
                }
            }
        }

        item {
            StormSectionCard(
                title = "Executable & Integrity Inspection",
                icon = if (game.peInspection.isValidPe) Icons.Default.CheckCircle else Icons.Default.Warning,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow("Target Executable", game.executableName, monospace = true)
                TelemetryKeyValueRow(
                    "PE Architecture",
                    if (game.peInspection.isValidPe) game.peInspection.architecture.label else "Not Verified",
                    valueColor = if (game.peInspection.isValidPe) StormSuccess else StormWarning
                )
                TelemetryKeyValueRow("PE Subsystem", game.peInspection.subsystem)
                TelemetryKeyValueRow(
                    "Executable Size",
                    if (game.executableSizeBytes > 0) "${game.executableSizeBytes / 1024} KB" else "-"
                )
                TelemetryKeyValueRow(
                    "Directory Footprint",
                    "${game.fileCount} files (${game.totalDirectorySizeBytes / (1024 * 1024)} MB)"
                )
                TelemetryKeyValueRow("CPK Archives", "${game.cpkArchiveCount} detected")
                TelemetryKeyValueRow(
                    "DirectX / XInput Hints",
                    "DX9=${game.hasDirectXLibs} • XInput=${game.hasXInputLib}"
                )
                if (game.validationMessages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    game.validationMessages.forEach { msg ->
                        Text(
                            text = "• $msg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StormTextSecondary
                        )
                    }
                }
            }
        }

        item {
            StormSectionCard(
                title = "Quick Performance Presets",
                icon = Icons.Default.Tune,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Text(
                    text = "Active Profile: ${state.profile.profileName} (${state.profile.preset.displayName})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StormCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(GamePreset.LOW, GamePreset.BALANCED, GamePreset.HIGH).forEach { preset ->
                        val active = state.profile.preset == preset
                        OutlinedButton(
                            onClick = { onApplyPreset(preset) },
                            border = BorderStroke(
                                1.dp,
                                if (active) StormAmber else StormCardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preset_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.name,
                                color = if (active) StormAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
