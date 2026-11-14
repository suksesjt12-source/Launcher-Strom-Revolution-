package com.stormrevolution.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily
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
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.InputResponseCurve
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.LogSeverity
import com.stormrevolution.launcher.ui.components.ActionChipRow
import com.stormrevolution.launcher.ui.components.StatusPill
import com.stormrevolution.launcher.ui.components.StatusTone
import com.stormrevolution.launcher.ui.components.StormSectionCard
import com.stormrevolution.launcher.ui.components.TelemetryKeyValueRow

@Composable
fun ControlsSettingsScreen(
    state: LauncherUiState,
    onUpdateController: ((ControllerConfig) -> ControllerConfig) -> Unit,
    onResetLayout: () -> Unit,
    onOpenLayoutEditorPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cfg = state.controllerConfig

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Virtual Gamepad & XInput Bridge",
                icon = Icons.Default.Gamepad,
                badge = {
                    StatusPill(
                        label = cfg.profileName,
                        tone = StatusTone.INFO
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Text("Controller Profile", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = listOf("Storm Default", "Compact", "Pro Claw").map { name ->
                        name to (cfg.profileName == name)
                    },
                    onSelect = { idx ->
                        val chosen = listOf("Storm Default", "Compact", "Pro Claw")[idx]
                        val scale = when (chosen) {
                            "Compact" -> 0.85f
                            "Pro Claw" -> 1.12f
                            else -> 1.0f
                        }
                        onUpdateController { it.copy(profileName = chosen, buttonSizeScale = scale) }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Button Opacity: ${(cfg.buttonOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = cfg.buttonOpacity,
                    onValueChange = { v -> onUpdateController { it.copy(buttonOpacity = v) } },
                    valueRange = 0.25f..1.0f
                )
                Text("Button Size: ${(cfg.buttonSizeScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = cfg.buttonSizeScale,
                    onValueChange = { v -> onUpdateController { it.copy(buttonSizeScale = v) } },
                    valueRange = 0.7f..1.4f
                )
                Text("Analog Sensitivity: ${"%.2f".format(cfg.analogSensitivity)}x", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = cfg.analogSensitivity,
                    onValueChange = { v -> onUpdateController { it.copy(analogSensitivity = v) } },
                    valueRange = 0.6f..1.8f
                )
                Text("Analog Dead Zone: ${(cfg.analogDeadZone * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = cfg.analogDeadZone,
                    onValueChange = { v -> onUpdateController { it.copy(analogDeadZone = v) } },
                    valueRange = 0.02f..0.35f
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Input Response Curve", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                ActionChipRow(
                    items = InputResponseCurve.entries.map { curve ->
                        curve.name to (cfg.responseCurve == curve)
                    },
                    onSelect = { idx ->
                        onUpdateController { it.copy(responseCurve = InputResponseCurve.entries[idx]) }
                    }
                )
            }
        }

        item {
            StormSectionCard(
                title = "Input Routing & Layout Editor",
                icon = Icons.Default.OpenWith,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                ControlSwitchRow(
                    title = "Touchscreen Overlay Enabled",
                    subtitle = "Render interactive Ninja Storm buttons over landscape viewport",
                    checked = cfg.overlayEnabled,
                    onCheckedChange = { v -> onUpdateController { it.copy(overlayEnabled = v) } }
                )
                ControlSwitchRow(
                    title = "XInput Binary IPC Bridge",
                    subtitle = "Dispatch XInput gamepad packets over Unix socket & UDP :28475",
                    checked = cfg.xinputBridgeEnabled,
                    onCheckedChange = { v -> onUpdateController { it.copy(xinputBridgeEnabled = v) } }
                )
                ControlSwitchRow(
                    title = "Keyboard WASD / Action Mapping",
                    subtitle = "Map physical keyboard keys to virtual XInput sticks & face buttons",
                    checked = cfg.keyboardMappingEnabled,
                    onCheckedChange = { v -> onUpdateController { it.copy(keyboardMappingEnabled = v) } }
                )
                ControlSwitchRow(
                    title = "Haptic Tactile Feedback",
                    subtitle = "Light vibration pulse on button press",
                    checked = cfg.hapticFeedback,
                    onCheckedChange = { v -> onUpdateController { it.copy(hapticFeedback = v) } }
                )
                TelemetryKeyValueRow(
                    key = "Bluetooth / USB Gamepads",
                    value = if (state.telemetry.connectedGamepads.isEmpty()) "None detected" else state.telemetry.connectedGamepads.joinToString(", ")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenLayoutEditorPreview,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_open_layout_editor")
                    ) {
                        Icon(Icons.Default.OpenWith, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Layout (Landscape)")
                    }
                    OutlinedButton(
                        onClick = onResetLayout,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_reset_layout")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset controller layout")
                    }
                }
            }
        }
    }
}

@Composable
fun SystemInfoScreen(
    state: LauncherUiState,
    modifier: Modifier = Modifier
) {
    val t = state.telemetry
    val g = state.gpuInfo

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Device & SoC Hardware",
                icon = Icons.Default.Memory,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow("Device Model", t.deviceModel)
                TelemetryKeyValueRow("OS Version", t.androidVersion)
                TelemetryKeyValueRow("Supported ABIs", t.primaryAbi, monospace = true)
                TelemetryKeyValueRow("CPU Hardware", t.cpuHardware)
                TelemetryKeyValueRow("Online Cores / Load", "${t.cpuCores} cores • ${t.cpuLoadPercent}% CPU load", monospace = true)
                TelemetryKeyValueRow("Total / Free RAM", "${t.availableRamMb} MB free / ${t.totalRamMb} MB total", monospace = true)
                TelemetryKeyValueRow("Launcher Heap", "${t.appHeapUsedMb} MB", monospace = true)
                TelemetryKeyValueRow("Internal Storage Free", "${t.internalStorageFreeMb} MB", monospace = true)
                TelemetryKeyValueRow("Thermal Status", t.thermalStatusLabel)
                TelemetryKeyValueRow(
                    key = "JNI Native Bridge",
                    value = if (t.nativeBridgeLoaded) "Loaded (libstorm_runtime_bridge.so)" else "JVM Fallback (${t.nativeBridgeError ?: "no .so"})",
                    valueColor = if (t.nativeBridgeLoaded) StormSuccess else StormWarning
                )
            }
        }

        item {
            StormSectionCard(
                title = "Graphics Stack & Mali GPU Verification",
                icon = Icons.Default.Info,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                TelemetryKeyValueRow("GL Vendor", g.glVendor)
                TelemetryKeyValueRow("GL Renderer", g.glRenderer)
                TelemetryKeyValueRow("GL Version", g.glVersion)
                TelemetryKeyValueRow("Vulkan Support", "${g.vulkanSupported} (${g.vulkanVersion})")
                TelemetryKeyValueRow("Mali Detected", g.isMaliGpu.toString())
                TelemetryKeyValueRow("Adreno / Turnip", if (g.isMaliGpu) "Blocked (Mali-safe)" else g.isAdrenoGpu.toString())
                TelemetryKeyValueRow("Recommended D3D9", g.recommendedBackend.displayName, valueColor = StormCyan)
            }
        }
    }
}

@Composable
fun DebugConsoleScreen(
    state: LauncherUiState,
    onRunDiagnostic: () -> Unit,
    onCopyLogs: () -> Unit,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenLogViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report = state.diagnosticReport

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StormSectionCard(
                title = "Runtime Debug & Diagnostic Console",
                icon = Icons.Default.BugReport,
                badge = {
                    StatusPill(
                        label = if (state.processState.isRunning) "PID ${state.processState.pid ?: "?"}" else "IDLE",
                        tone = if (state.processState.isRunning) StatusTone.OK else StatusTone.INFO
                    )
                },
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRunDiagnostic,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_run_diagnostic")
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Run Diagnostic")
                    }
                    OutlinedButton(
                        onClick = onOpenLogViewer,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_open_log_viewer")
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Full Logs")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyLogs,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_copy_logs")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Logs", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onExportLogs,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_export_logs")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Export", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_clear_logs")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
                    }
                }
            }
        }

        if (report != null) {
            item {
                StormSectionCard(
                    title = "Diagnostic Audit Report (${report.findings.size} finding(s))",
                    icon = Icons.Default.Info,
                    modifier = Modifier.widthIn(max = 840.dp)
                ) {
                    TelemetryKeyValueRow("Generated", report.generatedAt, monospace = true)
                    TelemetryKeyValueRow("Wine", report.wineVersion)
                    TelemetryKeyValueRow("Translation", report.translationLayerVersion)
                    TelemetryKeyValueRow("Graphics", report.graphicsBackend)
                    TelemetryKeyValueRow("Last Error", report.lastErrorMessage, valueColor = StormWarning)
                    Spacer(modifier = Modifier.height(8.dp))
                    report.findings.forEach { finding ->
                        val c = when (finding.severity) {
                            LogSeverity.ERROR -> StormError
                            LogSeverity.WARN -> StormWarning
                            LogSeverity.INFO -> StormCyan
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = StormObsidian),
                            border = BorderStroke(1.dp, c.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = finding.code.title, style = MaterialTheme.typography.titleMedium, color = c)
                                Text(text = finding.summary, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Fix: ${finding.remediation}", style = MaterialTheme.typography.bodyMedium, color = StormTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        item {
            StormSectionCard(
                title = "Live Console Tail",
                icon = Icons.Default.Terminal,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                val tail = state.logs.takeLast(18)
                if (tail.isEmpty()) {
                    Text("No runtime output captured yet.", color = StormTextSecondary)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StormObsidian, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tail.forEach { entry ->
                            val color = when (entry.severity) {
                                LogSeverity.ERROR -> StormError
                                LogSeverity.WARN -> StormWarning
                                LogSeverity.INFO -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = "[${entry.timestamp}] [${entry.category.tag}] ${entry.message}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogViewerScreen(
    state: LauncherUiState,
    onCopyLogs: () -> Unit,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }

    val filtered = remember(state.logs, searchQuery, selectedCategory) {
        state.logs.filter { entry ->
            (selectedCategory == null || entry.category == selectedCategory) &&
                (searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.category.tag.contains(searchQuery, ignoreCase = true))
        }.asReversed()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Filter logs (Wine, Box64, DXVK, PID...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(null, LogCategory.WINE, LogCategory.BOX64, LogCategory.DXVK, LogCategory.PROC, LogCategory.DIAG).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat?.tag ?: "ALL") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCopyLogs, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Copy (${filtered.size})")
                }
                OutlinedButton(onClick = onExportLogs, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Export")
                }
                OutlinedButton(onClick = onClearLogs, modifier = Modifier.height(48.dp)) {
                    Text("Clear")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StormObsidian, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    val color = when (entry.severity) {
                        LogSeverity.ERROR -> StormError
                        LogSeverity.WARN -> StormWarning
                        LogSeverity.INFO -> StormAmber
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StormSurface.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "[${entry.timestamp}] [${entry.severity.name}] [${entry.category.tag}] ${entry.message}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSwitchRow(
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
