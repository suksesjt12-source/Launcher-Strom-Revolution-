package com.stormrevolution.launcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormrevolution.launcher.manager.AudioHardwareProbe
import com.stormrevolution.launcher.manager.CpuTranslationRoute
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.DiagnosticReport
import com.stormrevolution.launcher.model.GamePreset
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GameValidationResult
import com.stormrevolution.launcher.model.GamepadState
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.PrefixHealth
import com.stormrevolution.launcher.model.ProcessExecutionState
import com.stormrevolution.launcher.model.RuntimeComponentState
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.model.RuntimeLogEntry
import com.stormrevolution.launcher.model.SystemTelemetry
import com.stormrevolution.launcher.model.VirtualButtonId
import com.stormrevolution.launcher.model.VirtualButtonLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherUiState(
    val isSplashComplete: Boolean = false,
    val gameResult: GameValidationResult = GameValidationResult(),
    val runtimeComponents: List<RuntimeComponentState> = emptyList(),
    val prefixHealth: PrefixHealth = PrefixHealth(),
    val gpuInfo: GpuCapabilityInfo = GpuCapabilityInfo(),
    val audioProbe: AudioHardwareProbe? = null,
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val profile: GameProfile = GameProfile(),
    val controllerConfig: ControllerConfig = ControllerConfig(),
    val gamepadState: GamepadState = GamepadState(),
    val processState: ProcessExecutionState = ProcessExecutionState(),
    val logs: List<RuntimeLogEntry> = emptyList(),
    val diagnosticReport: DiagnosticReport? = null,
    val translationRoute: CpuTranslationRoute? = null,
    val effectiveGraphicsBackend: GraphicsBackend = GraphicsBackend.WINED3D,
    val copyGameToPrivate: Boolean = true,
    val isBusy: Boolean = false,
    val statusBannerMessage: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val container: StormContainer = (application as StormApplication).container

    private val _isSplashComplete = MutableStateFlow(false)
    private val _gpuInfo = MutableStateFlow(GpuCapabilityInfo())
    private val _audioProbe = MutableStateFlow<AudioHardwareProbe?>(null)
    private val _prefixHealth = MutableStateFlow(PrefixHealth())
    private val _telemetry = MutableStateFlow(SystemTelemetry())
    private val _isBusy = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _copyToPrivate = MutableStateFlow(true)
    private val _translationRoute = MutableStateFlow<CpuTranslationRoute?>(null)

    private val _profile = container.settingsManager.gameProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameProfile())

    private val _controllerConfig = container.settingsManager.controllerConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControllerConfig())

    val uiState: StateFlow<LauncherUiState> = combine(
        _isSplashComplete,
        container.gameManager.gameState,
        container.runtimeManager.components,
        _prefixHealth,
        _gpuInfo,
        _audioProbe,
        _telemetry,
        _profile,
        _controllerConfig,
        container.controllerManager.gamepadState,
        container.processManager.executionState,
        container.diagnosticManager.logs,
        container.diagnosticManager.latestReport,
        _translationRoute,
        _copyToPrivate,
        _isBusy,
        _statusBanner
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val game = values[1] as GameValidationResult
        @Suppress("UNCHECKED_CAST")
        val components = values[2] as List<RuntimeComponentState>
        val gpu = values[4] as GpuCapabilityInfo
        val prof = values[7] as GameProfile
        val dxvkReady = components.firstOrNull { it.type == RuntimeComponentType.DXVK }?.installStatus == InstallStatus.VERIFIED
        val effectiveBackend = container.graphicsManager.resolveEffectiveBackend(prof.graphicsBackend, gpu, dxvkReady)

        LauncherUiState(
            isSplashComplete = values[0] as Boolean,
            gameResult = game,
            runtimeComponents = components,
            prefixHealth = values[3] as PrefixHealth,
            gpuInfo = gpu,
            audioProbe = values[5] as AudioHardwareProbe?,
            telemetry = values[6] as SystemTelemetry,
            profile = prof,
            controllerConfig = values[8] as ControllerConfig,
            gamepadState = values[9] as GamepadState,
            processState = values[10] as ProcessExecutionState,
            logs = values[11] as List<RuntimeLogEntry>,
            diagnosticReport = values[12] as DiagnosticReport?,
            translationRoute = values[13] as CpuTranslationRoute?,
            effectiveGraphicsBackend = effectiveBackend,
            copyGameToPrivate = values[14] as Boolean,
            isBusy = values[15] as Boolean,
            statusBannerMessage = values[16] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    init {
        initializeSystem()
        startTelemetryTicker()
    }

    private fun initializeSystem() {
        viewModelScope.launch(Dispatchers.IO) {
            container.diagnosticManager.info(
                LogCategory.RUNTIME,
                "Storm Launcher initializing (JNI Bridge=${NativeBridge.isLoaded})..."
            )
            if (!NativeBridge.isLoaded) {
                container.diagnosticManager.warn(
                    LogCategory.RUNTIME,
                    "JNI native library not packaged in this build (${NativeBridge.loadError}); using JVM POSIX fallback."
                )
            }

            val gpu = container.graphicsManager.probeGpuCapabilities()
            _gpuInfo.value = gpu

            _audioProbe.value = container.audioManager.probeAudioHardware()
            container.runtimeManager.refreshComponentStates(gpu)
            _prefixHealth.value = container.wineManager.inspectPrefixHealth()

            val persistedUri = container.settingsManager.persistedGameUriFlow.first()
            val copyPref = container.settingsManager.copyToPrivateFlow.first()
            _copyToPrivate.value = copyPref

            val game = container.gameManager.revalidateCurrentGame(persistedUri)
            _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(game.peInspection.architecture)
            _telemetry.value = container.performanceMonitor.collectTelemetry()

            delay(950L)
            _isSplashComplete.value = true
        }
    }

    private fun startTelemetryTicker() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2500L)
                _telemetry.value = container.performanceMonitor.collectTelemetry()
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun setCopyGameToPrivate(copy: Boolean) {
        _copyToPrivate.value = copy
        viewModelScope.launch {
            container.settingsManager.saveCopyToPrivate(copy)
        }
    }

    fun onGameDirectorySelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            _statusBanner.value = "Scanning directory & validating NSUNSR.exe..."
            container.settingsManager.saveGameUri(uri.toString())
            val res = container.gameManager.scanSafGameDirectory(uri, _copyToPrivate.value)
            _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(res.peInspection.architecture)
            _statusBanner.value = "Scan complete: ${res.status.label}"
            _isBusy.value = false
        }
    }

    fun revalidateGame() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val uri = container.settingsManager.persistedGameUriFlow.first()
            val res = container.gameManager.revalidateCurrentGame(uri)
            _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(res.peInspection.architecture)
            _statusBanner.value = "Game status: ${res.status.label}"
            _isBusy.value = false
        }
    }

    fun installRuntimeComponent(type: RuntimeComponentType, archiveUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            _statusBanner.value = "Installing ${type.displayName}..."
            val result = container.runtimeManager.installComponentFromUri(type, archiveUri, _gpuInfo.value)
            result.onSuccess { state ->
                val uri = container.settingsManager.persistedGameUriFlow.first()
                val game = container.gameManager.revalidateCurrentGame(uri)
                _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(game.peInspection.architecture)
                _statusBanner.value = "${type.displayName}: ${state.installStatus.label}"
            }.onFailure { err ->
                container.diagnosticManager.error(LogCategory.RUNTIME, "Install ${type.displayName} failed: ${err.message}")
                _statusBanner.value = "Install failed: ${err.message}"
            }
            _isBusy.value = false
        }
    }

    fun verifyAllRuntime() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val gpu = container.graphicsManager.probeGpuCapabilities()
            _gpuInfo.value = gpu
            container.runtimeManager.refreshComponentStates(gpu)
            _prefixHealth.value = container.wineManager.inspectPrefixHealth()
            val uri = container.settingsManager.persistedGameUriFlow.first()
            val game = container.gameManager.revalidateCurrentGame(uri)
            _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(game.peInspection.architecture)
            _statusBanner.value = "Runtime & GPU verification complete."
            _isBusy.value = false
        }
    }

    fun repairRuntimeComponent(type: RuntimeComponentType) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = container.runtimeManager.repairComponent(type, _gpuInfo.value)
            _statusBanner.value = "${type.displayName}: ${state.installStatus.label}"
        }
    }

    fun removeRuntimeComponent(type: RuntimeComponentType) {
        viewModelScope.launch(Dispatchers.IO) {
            container.runtimeManager.removeComponent(type, _gpuInfo.value)
            val uri = container.settingsManager.persistedGameUriFlow.first()
            val game = container.gameManager.revalidateCurrentGame(uri)
            _translationRoute.value = container.runtimeManager.resolveCpuTranslationRoute(game.peInspection.architecture)
            _statusBanner.value = "Removed ${type.displayName}"
        }
    }

    fun createOrRepairPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val cur = uiState.value
            _prefixHealth.value = container.wineManager.createOrRepairPrefix(
                cur.profile, cur.effectiveGraphicsBackend, cur.translationRoute, container.processManager
            )
            _statusBanner.value = "Wine prefix initialized/repaired."
            _isBusy.value = false
        }
    }

    fun resetPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val cur = uiState.value
            _prefixHealth.value = container.wineManager.resetPrefix(
                cur.profile, cur.effectiveGraphicsBackend, cur.translationRoute, container.processManager
            )
            _statusBanner.value = "Wine prefix reset complete."
            _isBusy.value = false
        }
    }

    fun backupPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val res = container.wineManager.backupPrefix()
            _statusBanner.value = res.fold(
                onSuccess = { "Prefix backed up: ${it.name}" },
                onFailure = { "Backup failed: ${it.message}" }
            )
            _isBusy.value = false
        }
    }

    fun restorePrefix(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val res = container.wineManager.restorePrefixFromUri(uri)
            res.onSuccess { health ->
                _prefixHealth.value = health
                _statusBanner.value = "Prefix restored successfully."
            }.onFailure { err ->
                _statusBanner.value = "Restore failed: ${err.message}"
            }
            _isBusy.value = false
        }
    }

    fun updateProfile(transform: (GameProfile) -> GameProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = transform(_profile.value)
            container.settingsManager.saveGameProfile(updated)
            container.graphicsManager.writeDxvkConfiguration(updated, uiState.value.effectiveGraphicsBackend)
            container.audioManager.writeAudioConfiguration(updated)
        }
    }

    fun applyPreset(preset: GamePreset) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = container.settingsManager.applyPreset(_profile.value, preset)
            container.settingsManager.saveGameProfile(updated)
            _statusBanner.value = "Applied ${preset.displayName} preset"
        }
    }

    fun clearShaderCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = container.graphicsManager.clearShaderCache()
            _telemetry.value = container.performanceMonitor.collectTelemetry()
            _statusBanner.value = "Shader cache cleared (${freed / 1024} KB freed)"
        }
    }

    fun updateControllerConfig(transform: (ControllerConfig) -> ControllerConfig) {
        viewModelScope.launch {
            val updated = transform(_controllerConfig.value)
            container.settingsManager.saveControllerConfig(updated)
            container.controllerManager.logControllerCalibration(updated)
        }
    }

    fun updateButtonPosition(id: VirtualButtonId, normX: Float, normY: Float) {
        updateControllerConfig { cfg ->
            val updatedLayouts = cfg.layouts.map {
                if (it.id == id) it.copy(
                    normalizedX = normX.coerceIn(0.05f, 0.95f),
                    normalizedY = normY.coerceIn(0.08f, 0.92f)
                ) else it
            }
            cfg.copy(layouts = updatedLayouts)
        }
    }

    fun resetControllerLayout() {
        updateControllerConfig { cfg ->
            cfg.copy(
                analogNormalizedX = 0.16f,
                analogNormalizedY = 0.76f,
                layouts = VirtualButtonId.entries.map {
                    VirtualButtonLayout(it, it.defaultX, it.defaultY, 1.0f)
                }
            )
        }
        _statusBanner.value = "Controller layout reset to Storm Default"
    }

    fun runDiagnostic(): DiagnosticReport {
        val cur = uiState.value
        return container.diagnosticManager.runDiagnosticAudit(
            gameResult = cur.gameResult,
            runtimeComponents = cur.runtimeComponents,
            prefixHealth = cur.prefixHealth,
            gpuInfo = cur.gpuInfo,
            telemetry = cur.telemetry,
            processState = cur.processState,
            selectedBackend = cur.profile.graphicsBackend
        )
    }

    fun copyLogs() {
        val ok = container.diagnosticManager.copyLogsToClipboard()
        _statusBanner.value = if (ok) "Logs copied to clipboard." else "Clipboard unavailable."
    }

    fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = container.diagnosticManager.exportLogsToFile()
            _statusBanner.value = res.fold(
                onSuccess = { "Exported logs to files/logs/${it.name}" },
                onFailure = { "Export failed: ${it.message}" }
            )
        }
    }

    fun clearLogs() {
        container.diagnosticManager.clearLogs()
        _statusBanner.value = "Logs cleared."
    }

    fun launchGameRuntime(onReadyForViewport: () -> Unit) {
        val cur = uiState.value
        if (cur.gameResult.status != GameStatus.READY_TO_LAUNCH) {
            _statusBanner.value = "Cannot launch: ${cur.gameResult.status.label}"
            runDiagnostic()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val res = container.processManager.launchGame(cur.gameResult, cur.profile, cur.gpuInfo)
            withContext(Dispatchers.Main) {
                res.onSuccess {
                    onReadyForViewport()
                }.onFailure { err ->
                    runDiagnostic()
                    _statusBanner.value = "Launch error: ${err.message}"
                }
            }
        }
    }

    fun stopGameRuntime() {
        viewModelScope.launch(Dispatchers.IO) {
            container.processManager.stopActiveProcess(force = false)
        }
    }
}
