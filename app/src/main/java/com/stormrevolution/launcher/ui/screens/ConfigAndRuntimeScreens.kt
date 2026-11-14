package com.stormrevolution.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StormAmber
import com.example.ui.theme.StormCardBorder
import com.example.ui.theme.StormCyan
import com.example.ui.theme.StormError
import com.example.ui.theme.StormSuccess
import com.example.ui.theme.StormSurface
import com.example.ui.theme.StormTextSecondary
import com.example.ui.theme.StormWarning
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.model.AspectRatioMode
import com.stormrevolution.launcher.model.AudioBackend
import com.stormrevolution.launcher.model.CpuMode
import com.stormrevolution.launcher.model.GamePreset
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.RuntimeComponentState
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.model.TextureFilteringMode
import com.stormrevolution.launcher.ui.components.ActionChipRow
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormSectionCard
import com.stormrevolution.launcher.ui.components.TelemetryKeyValueRow
import kotlin.math.roundToInt

@Composable
fun GameConfigurationScreen(
    state: LauncherUiState,
    onApplyPreset: (GamePreset) -> Unit,
    onUpdateProfile: ((GameProfile) -> GameProfile) -> Unit,
    onCreateOrRepairPrefix: () -> Unit,
    onResetPrefix: () -> Unit,
    onBackupPrefix: () -> Unit,
    onRestorePrefix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    val resolutions = listOf(
        854 to 480,
        960 to 540,
        1024 to 576,
        1280 to 720
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Game Profile: ${profile.profileName}",
                icon = Icons.Default.Tune,
                badge = {
                    StatusPill(label = profile.preset.name, tone = StatusTone.INFO)
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Text("Preset Mode", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = listOf(
                        "LOW" to (profile.preset == GamePreset.LOW),
                        "BALANCED" to (profile.preset == GamePreset.BALANCED),
                        "HIGH" to (profile.preset == GamePreset.HIGH)
                    ),
                    onSelect = { idx ->
                        onApplyPreset(listOf(GamePreset.LOW, GamePreset.BALANCED, GamePreset.HIGH)[idx])
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Display Resolution (16:9)", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = resolutions.map { (w, h) ->
                        "${w}x${h}" to (profile.resolutionWidth == w && profile.resolutionHeight == h)
                    },
                    onSelect = { idx ->
                        val (w, h) = resolutions[idx]
                        onUpdateProfile {
                            it.copy(
                                preset = GamePreset.CUSTOM,
                                resolutionWidth = w,
                                resolutionHeight = h
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Aspect Ratio & Scaling", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = AspectRatioMode.entries.map { mode ->
                        mode.name.replace("_", " ") to (profile.aspectRatioMode == mode)
                    },
                    onSelect = { idx ->
                        val mode = AspectRatioMode.entries[idx]
                        onUpdateProfile { it.copy(aspectRatioMode = mode, maintainAspectRatio = mode != AspectRatioMode.FIT_SCREEN) }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("CPU Translation Mode (Box64 DynaRec)", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = CpuMode.entries.map { mode ->
                        mode.name to (profile.cpuMode == mode)
                    },
                    onSelect = { idx ->
                        val mode = CpuMode.entries[idx]
                        onUpdateProfile { it.copy(cpuMode = mode) }
                    }
                )
            }
        }

        item {
            StormSectionCard(
                title = "Audio Engine (PulseAudio / ALSA)",
                icon = Icons.Default.GraphicEq,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                state.audioProbe?.let { probe ->
                    TelemetryKeyValueRow(
                        key = "Hardware Rate",
                        value = "${probe.sampleRateHz} Hz • ${probe.framesPerBuffer} frames (${probe.estimatedLatencyMs}ms base)",
                        monospace = true
                    )
                    TelemetryKeyValueRow(
                        key = "Output Device",
                        value = probe.outputDeviceNames.firstOrNull() ?: "Speaker"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ActionChipRow(
                    items = AudioBackend.entries.map { b ->
                        b.name.replace("_", " ") to (profile.audioBackend == b)
                    },
                    onSelect = { idx ->
                        onUpdateProfile { it.copy(audioBackend = AudioBackend.entries[idx]) }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Anti-Underrun Buffer: ${profile.audioBufferMs} ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = profile.audioBufferMs.toFloat(),
                    onValueChange = { valMs ->
                        onUpdateProfile { it.copy(audioBufferMs = valMs.roundToInt()) }
                    },
                    valueRange = 48f..160f,
                    steps = 6
                )
                Text(
                    text = "Volume: ${profile.audioVolumePercent}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = profile.audioVolumePercent.toFloat(),
                    onValueChange = { vol ->
                        onUpdateProfile { it.copy(audioVolumePercent = vol.roundToInt()) }
                    },
                    valueRange = 0f..100f
                )
            }
        }

        item {
            StormSectionCard(
                title = "Wine Prefix Manager",
                icon = Icons.Default.Layers,
                badge = {
                    StatusPill(
                        label = if (state.prefixHealth.exists && state.prefixHealth.hasRegistryFiles) "HEALTHY" else "NEEDS INIT",
                        tone = if (state.prefixHealth.exists && state.prefixHealth.hasRegistryFiles) StatusTone.OK else StatusTone.WARN
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow("Prefix Path", "<app_sandbox>/files/prefix/", monospace = true)
                TelemetryKeyValueRow("drive_c / system32", "drive_c=${state.prefixHealth.hasDriveC} • sys32=${state.prefixHealth.hasSystem32} • syswow64=${state.prefixHealth.hasSysWow64}")
                TelemetryKeyValueRow("Registry & DX9", "hives=${state.prefixHealth.hasRegistryFiles} • d3d9=${state.prefixHealth.hasD3d9Override}")

                if (state.prefixHealth.missingComponents.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Missing: ${state.prefixHealth.missingComponents.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StormWarning
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateOrRepairPrefix,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_create_repair_prefix")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Create / Repair")
                    }
                    OutlinedButton(
                        onClick = onResetPrefix,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_reset_prefix")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackupPrefix,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_backup_prefix")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Backup ZIP")
                    }
                    OutlinedButton(
                        onClick = onRestorePrefix,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_restore_prefix")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Restore ZIP")
                    }
                }
            }
        }
    }
}

@Composable
fun RuntimeManagerScreen(
    state: LauncherUiState,
    onInstallComponent: (RuntimeComponentType) -> Unit,
    onRepairComponent: (RuntimeComponentType) -> Unit,
    onRemoveComponent: (RuntimeComponentType) -> Unit,
    onVerifyAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Windows ARM64 Translation Stack",
                icon = Icons.Default.DeveloperBoard,
                badge = {
                    OutlinedButton(
                        onClick = onVerifyAll,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_verify_all_runtime")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Verify All")
                    }
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Text(
                    text = "Active CPU Route: ${state.translationRoute?.routeDescription ?: "Waiting for verification"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.translationRoute?.isExecutableReady == true) StormSuccess else StormWarning
                )
                state.translationRoute?.blockingReason?.let { reason ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                }
            }
        }

        items(state.runtimeComponents, key = { it.type.name }) { comp ->
            RuntimeComponentCard(
                component = comp,
                onInstall = { onInstallComponent(comp.type) },
                onRepair = { onRepairComponent(comp.type) },
                onVerify = onVerifyAll,
                onRemove = { onRemoveComponent(comp.type) },
                modifier = Modifier.widthIn(max = 840.dp)
            )
        }
    }
}

@Composable
private fun RuntimeComponentCard(
    component: RuntimeComponentState,
    onInstall: () -> Unit,
    onRepair: () -> Unit,
    onVerify: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = when (component.installStatus) {
        InstallStatus.VERIFIED, InstallStatus.SYSTEM_AVAILABLE -> StatusTone.OK
        InstallStatus.MISSING_DEPENDENCY, InstallStatus.NOT_INSTALLED -> StatusTone.WARN
        InstallStatus.CORRUPTED, InstallStatus.SYSTEM_UNAVAILABLE -> StatusTone.ERROR
    }
    val isSystemOnly = component.type == RuntimeComponentType.VULKAN_BACKEND ||
        component.type == RuntimeComponentType.GRAPHICS_DRIVER

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StormSurface),
        border = BorderStroke(1.dp, StormCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = component.type.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "License: ${component.type.licenseName} • ${component.architecture}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StormCyan
                    )
                }
                StatusPill(label = component.installStatus.label, tone = tone)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TelemetryKeyValueRow("Version", component.version, monospace = true)
            TelemetryKeyValueRow("Compatibility", component.compatibilityStatus)
            TelemetryKeyValueRow("Dependencies", component.dependencyStatus)
            TelemetryKeyValueRow("Details", component.details, monospace = true)

            if (!isSystemOnly) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("install_${component.type.folderName}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (component.installStatus == InstallStatus.VERIFIED) "Update" else "Install", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onRepair,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Repair ${component.type.displayName}")
                    }
                    OutlinedButton(
                        onClick = onVerify,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = "Verify ${component.type.displayName}")
                    }
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${component.type.displayName}", tint = StormError)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphicsSettingsScreen(
    state: LauncherUiState,
    onUpdateProfile: ((GameProfile) -> GameProfile) -> Unit,
    onClearShaderCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    val gpu = state.gpuInfo

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "GPU & Mali Compatibility Probe",
                icon = Icons.Default.Layers,
                badge = {
                    StatusPill(
                        label = if (gpu.vulkanSupported) "VULKAN OK" else "GLES ONLY",
                        tone = if (gpu.vulkanSupported) StatusTone.OK else StatusTone.WARN
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow("GPU Vendor", gpu.glVendor)
                TelemetryKeyValueRow("GPU Renderer", gpu.glRenderer)
                TelemetryKeyValueRow("OpenGL ES", gpu.glVersion)
                TelemetryKeyValueRow("Vulkan API", gpu.vulkanVersion, monospace = true)
                TelemetryKeyValueRow("Resolved D3D9 Backend", state.effectiveGraphicsBackend.displayName, valueColor = StormCyan)
                gpu.turnipBlockedReason?.let { guard ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = guard, style = MaterialTheme.typography.bodyMedium, color = StormAmber)
                }
            }
        }

        item {
            StormSectionCard(
                title = "DirectX 9 Translation Backend",
                icon = Icons.Default.Tune,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                ActionChipRow(
                    items = GraphicsBackend.entries.map { b ->
                        b.name to (profile.graphicsBackend == b)
                    },
                    onSelect = { idx ->
                        onUpdateProfile { it.copy(graphicsBackend = GraphicsBackend.entries[idx]) }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Texture Filtering", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = TextureFilteringMode.entries.map { tf ->
                        "${tf.anisotropyLevel}x" to (profile.textureFiltering == tf)
                    },
                    onSelect = { idx ->
                        onUpdateProfile { it.copy(textureFiltering = TextureFilteringMode.entries[idx]) }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Frame Rate Limiter: ${profile.fpsLimit} FPS", style = MaterialTheme.typography.bodyMedium)
                ActionChipRow(
                    items = listOf(30, 45, 60).map { fps -> "$fps FPS" to (profile.fpsLimit == fps) },
                    onSelect = { idx ->
                        val fps = listOf(30, 45, 60)[idx]
                        onUpdateProfile { it.copy(fpsLimit = fps) }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ToggleRow(
                    title = "VSync",
                    subtitle = "Synchronize presentation interval (disable for lower latency at 30 FPS)",
                    checked = profile.vsyncEnabled,
                    onCheckedChange = { checked -> onUpdateProfile { it.copy(vsyncEnabled = checked) } }
                )
                ToggleRow(
                    title = "Shader State Cache",
                    subtitle = "Cache compiled Vulkan/GLES shaders (${"%.2f".format(state.telemetry.shaderCacheSizeMb)} MB)",
                    checked = profile.shaderCacheEnabled,
                    onCheckedChange = { checked -> onUpdateProfile { it.copy(shaderCacheEnabled = checked) } }
                )
                ToggleRow(
                    title = "Graphics Pipeline Library Cache",
                    subtitle = "Reduce shader compilation stutter on Mali Vulkan",
                    checked = profile.pipelineCacheEnabled,
                    onCheckedChange = { checked -> onUpdateProfile { it.copy(pipelineCacheEnabled = checked) } }
                )
                ToggleRow(
                    title = "In-Game FPS & Frame Time HUD",
                    subtitle = "Display live FPS, frame time, and thermal status during play",
                    checked = profile.showFpsOverlay,
                    onCheckedChange = { checked -> onUpdateProfile { it.copy(showFpsOverlay = checked) } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onClearShaderCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_clear_shader_cache")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Shader Cache")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
