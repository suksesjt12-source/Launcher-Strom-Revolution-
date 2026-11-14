package com.stormrevolution.launcher.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.stormrevolution.launcher.model.DiagnosticErrorCode
import com.stormrevolution.launcher.model.DiagnosticFinding
import com.stormrevolution.launcher.model.DiagnosticReport
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GameValidationResult
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.LogSeverity
import com.stormrevolution.launcher.model.PrefixHealth
import com.stormrevolution.launcher.model.ProcessExecutionState
import com.stormrevolution.launcher.model.RuntimeComponentState
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.model.RuntimeLogEntry
import com.stormrevolution.launcher.model.SystemTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class DiagnosticManager(
    private val context: Context,
    private val storageManager: StorageManager
) {
    private val nextId = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileTimeFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    private val _logs = MutableStateFlow<List<RuntimeLogEntry>>(emptyList())
    val logs: StateFlow<List<RuntimeLogEntry>> = _logs.asStateFlow()

    private val _latestReport = MutableStateFlow<DiagnosticReport?>(null)
    val latestReport: StateFlow<DiagnosticReport?> = _latestReport.asStateFlow()

    fun log(category: LogCategory, severity: LogSeverity, rawMessage: String) {
        val sanitized = sanitizeSensitiveInfo(rawMessage)
        val entry = RuntimeLogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            category = category,
            severity = severity,
            message = sanitized
        )
        _logs.update { current ->
            (current + entry).takeLast(1500)
        }
    }

    fun info(category: LogCategory, message: String) = log(category, LogSeverity.INFO, message)
    fun warn(category: LogCategory, message: String) = log(category, LogSeverity.WARN, message)
    fun error(category: LogCategory, message: String) = log(category, LogSeverity.ERROR, message)

    fun clearLogs() {
        _logs.value = emptyList()
        info(LogCategory.DIAG, "Diagnostic log buffer cleared by user.")
    }

    fun formatLogsAsText(): String {
        return _logs.value.joinToString("\n") { entry ->
            "[${entry.timestamp}] [${entry.severity.name}] [${entry.category.tag}] ${entry.message}"
        }
    }

    fun copyLogsToClipboard(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val text = formatLogsAsText().ifBlank { "No logs recorded yet." }
        clipboard.setPrimaryClip(ClipData.newPlainText("Storm Launcher Logs", text))
        info(LogCategory.DIAG, "Copied ${_logs.value.size} log lines to clipboard.")
        return true
    }

    fun exportLogsToFile(): Result<File> = runCatching {
        storageManager.ensureBaseDirectories()
        val outFile = File(storageManager.logsDir, "storm_diag_${fileTimeFormat.format(Date())}.log")
        val reportText = buildString {
            _latestReport.value?.let { rep ->
                appendLine("=== STORM LAUNCHER DIAGNOSTIC REPORT ===")
                appendLine("Generated: ${rep.generatedAt}")
                appendLine("Android: ${rep.androidVersion} | Device: ${rep.deviceModel}")
                appendLine("CPU ABI: ${rep.cpuArchitecture} | GPU: ${rep.gpuName}")
                appendLine("Wine: ${rep.wineVersion} | Translation: ${rep.translationLayerVersion}")
                appendLine("Graphics: ${rep.graphicsBackend}")
                appendLine("Last Error: ${rep.lastErrorMessage}")
                appendLine("--- Findings (${rep.findings.size}) ---")
                rep.findings.forEach { f ->
                    appendLine("[${f.severity}] ${f.code.title}: ${f.summary} -> ${f.remediation}")
                }
                appendLine()
            }
            appendLine("=== RUNTIME LOGS ===")
            appendLine(formatLogsAsText())
        }
        outFile.writeText(reportText)
        info(LogCategory.DIAG, "Exported diagnostic report to logs/${outFile.name}")
        outFile
    }

    fun runDiagnosticAudit(
        gameResult: GameValidationResult,
        runtimeComponents: List<RuntimeComponentState>,
        prefixHealth: PrefixHealth,
        gpuInfo: GpuCapabilityInfo,
        telemetry: SystemTelemetry,
        processState: ProcessExecutionState,
        selectedBackend: GraphicsBackend
    ): DiagnosticReport {
        val findings = mutableListOf<DiagnosticFinding>()
        val recentLogMessages = _logs.value.takeLast(60).map {
            "[${it.timestamp}] [${it.category.tag}] ${it.message}"
        }

        // 1. Game directory / executable checks
        when (gameResult.status) {
            GameStatus.GAME_NOT_FOUND -> findings += DiagnosticFinding(
                DiagnosticErrorCode.MISSING_EXECUTABLE,
                LogSeverity.WARN,
                "NSUNSR.exe has not been imported or located yet.",
                "Open Game Files and select your PC installation folder via Android Storage Access Framework."
            )
            GameStatus.GAME_VALIDATION_FAILED -> findings += DiagnosticFinding(
                DiagnosticErrorCode.INVALID_GAME_DIRECTORY,
                LogSeverity.ERROR,
                gameResult.validationMessages.firstOrNull() ?: "Game directory failed structural or PE validation.",
                "Verify NSUNSR.exe is a valid Windows PE32 executable and data_win32/*.cpk archives are intact."
            )
            else -> Unit
        }

        // 2. Wine & CPU translation checks
        val wineState = runtimeComponents.firstOrNull { it.type == RuntimeComponentType.WINE }
        val box64State = runtimeComponents.firstOrNull { it.type == RuntimeComponentType.BOX64 }
        val box86State = runtimeComponents.firstOrNull { it.type == RuntimeComponentType.BOX86 }
        val wow64State = runtimeComponents.firstOrNull { it.type == RuntimeComponentType.WOW64 }

        if (wineState?.installStatus != InstallStatus.VERIFIED) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.WINE_INIT_FAILED,
                LogSeverity.ERROR,
                "Wine binary is missing or failed ELF verification (${wineState?.installStatus?.label}).",
                "Import an ARM64 Wine WoW64 runtime package (.zip / .tar.gz / .wcp) in Runtime Manager."
            )
        }

        if (box64State?.installStatus != InstallStatus.VERIFIED) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.BOX64_INIT_FAILED,
                LogSeverity.ERROR,
                "Box64 ARM64 translator binary is not installed or failed verification.",
                "Install Box64 ARM64 in Runtime Manager to execute Wine WoW64 and x86_64 components."
            )
        }

        if (gameResult.peInspection.isValidPe && gameResult.peInspection.architecture.is32BitX86) {
            val has32BitSupport = (wow64State?.installStatus == InstallStatus.VERIFIED) ||
                (box86State?.installStatus == InstallStatus.VERIFIED)
            if (!has32BitSupport) {
                findings += DiagnosticFinding(
                    DiagnosticErrorCode.BOX86_INIT_FAILED,
                    LogSeverity.ERROR,
                    "NSUNSR.exe is 32-bit x86 (PE32), but neither Wine WoW64 nor Box86 is verified.",
                    "Install Wine WoW64 (recommended for Android 14 ARM64) or Box86 before launching."
                )
            }
        }

        // 3. Prefix & DLL / DirectX checks
        if (!prefixHealth.exists || !prefixHealth.hasSystem32 || !prefixHealth.hasRegistryFiles) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.WINE_INIT_FAILED,
                LogSeverity.WARN,
                "Wine prefix at private files/prefix/ is uninitialized or missing registry hives.",
                "Run Create / Repair Prefix in Configuration."
            )
        }
        if (prefixHealth.exists && !prefixHealth.hasD3d9Override) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.MISSING_DIRECTX,
                LogSeverity.WARN,
                "DirectX 9 (d3d9 / d3dx9_43) DLL override configuration is incomplete in prefix.",
                "Use Repair Prefix to regenerate Wine Direct3D9 overrides."
            )
        }
        if (gameResult.localExecutablePath != null && !gameResult.hasDirectXLibs && !prefixHealth.hasD3d9Override) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.MISSING_DLL,
                LogSeverity.WARN,
                "Neither local d3dx9_*.dll nor prefix DX9 overrides were detected.",
                "Ensure DirectX 9 redistributable DLLs or DXVK/WineD3D DLLs are deployed to system32/syswow64."
            )
        }

        // 4. Vulkan / Graphics Renderer checks
        if (!gpuInfo.vulkanSupported) {
            val sev = if (selectedBackend == GraphicsBackend.DXVK) LogSeverity.ERROR else LogSeverity.WARN
            findings += DiagnosticFinding(
                DiagnosticErrorCode.VULKAN_UNAVAILABLE,
                sev,
                "Vulkan hardware ICD unavailable or incompatible on ${gpuInfo.glRenderer}.",
                "Use WineD3D (OpenGL ES) or Automatic Detection instead of forcing DXVK."
            )
        }
        if (gpuInfo.turnipBlockedReason != null) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.GRAPHICS_RENDERER_FAILED,
                LogSeverity.INFO,
                gpuInfo.turnipBlockedReason,
                "Keep Mali Vulkan / WineD3D selected; Turnip is restricted to Adreno GPUs."
            )
        }

        // 5. Storage & Memory checks
        if (telemetry.internalStorageFreeMb in 1..511) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.INSUFFICIENT_STORAGE,
                LogSeverity.ERROR,
                "Low internal storage (${telemetry.internalStorageFreeMb} MB free).",
                "Free at least 1 GB for Wine prefix, shader cache, and runtime extraction."
            )
        }
        if (telemetry.availableRamMb in 1..1023) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.INSUFFICIENT_MEMORY,
                LogSeverity.WARN,
                "Available system RAM is low (${telemetry.availableRamMb} MB free).",
                "Close background apps on your 8 GB device before launching NSUNSR.exe."
            )
        }

        // 6. Log-driven crash & permission detection
        val logTextLower = recentLogMessages.joinToString("\n").lowercase()
        if (logTextLower.contains("permission denied") || logTextLower.contains("eacces")) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.PERMISSION_DENIED,
                LogSeverity.ERROR,
                "POSIX EACCES / Permission denied observed during runtime execution.",
                "Run Verify/Repair in Runtime Manager to restore 0755 executable permissions inside app private storage."
            )
        }
        if (processState.lastExitCode != null && processState.lastExitCode != 0) {
            findings += DiagnosticFinding(
                DiagnosticErrorCode.GAME_PROCESS_CRASH,
                LogSeverity.ERROR,
                processState.crashReason ?: "Process exited with non-zero code ${processState.lastExitCode}.",
                "Review [WINE] and [DXVK] entries in Log Viewer; switch backend to WineD3D if Vulkan crashed."
            )
        }

        val lastError = _logs.value.lastOrNull { it.severity == LogSeverity.ERROR }?.message
            ?: findings.firstOrNull { it.severity == LogSeverity.ERROR }?.summary
            ?: "No critical errors recorded."

        val report = DiagnosticReport(
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            cpuArchitecture = Build.SUPPORTED_ABIS.joinToString(", "),
            gpuName = "${gpuInfo.glRenderer} (${gpuInfo.glVendor})",
            runtimeVersion = runtimeComponents.firstOrNull { it.type == RuntimeComponentType.ROOTFS }?.version ?: "Not Installed",
            wineVersion = wineState?.version ?: "Not Installed",
            translationLayerVersion = "${box64State?.version ?: "No Box64"} / ${wow64State?.version ?: box86State?.version ?: "No 32-bit layer"}",
            graphicsBackend = "${selectedBackend.displayName} (Recommended: ${gpuInfo.recommendedBackend.displayName})",
            gameExecutableDisplay = gameResult.localExecutablePath?.let { sanitizeSensitiveInfo(it) }
                ?: if (gameResult.sourceUriString != null) "SAF Tree (Selected)" else "Not selected",
            lastErrorMessage = lastError,
            findings = findings,
            recentLogs = recentLogMessages
        )
        _latestReport.value = report
        info(LogCategory.DIAG, "Diagnostic audit completed: ${findings.size} finding(s) logged.")
        return report
    }

    /**
     * Strips user-specific paths or email-like strings before storing or exporting logs.
     */
    fun sanitizeSensitiveInfo(input: String): String {
        return input
            .replace(Regex("/data/user/\\d+/[^/\\s]+"), "<app_sandbox>")
            .replace(Regex("/data/data/[^/\\s]+"), "<app_sandbox>")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<redacted_email>")
    }
}
