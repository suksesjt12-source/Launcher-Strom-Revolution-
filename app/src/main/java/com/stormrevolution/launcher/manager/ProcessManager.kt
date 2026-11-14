package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GameValidationResult
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.ProcessExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProcessManager(
    private val runtimeManager: RuntimeManager,
    private val wineManager: WineManager,
    private val graphicsManager: GraphicsManager,
    private val diagnosticManager: DiagnosticManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _executionState = MutableStateFlow(ProcessExecutionState())
    val executionState: StateFlow<ProcessExecutionState> = _executionState.asStateFlow()

    private var activeNativePid: Int? = null
    private var activeJvmProcess: Process? = null
    private var monitorJob: Job? = null

    fun launchGame(
        gameResult: GameValidationResult,
        profile: GameProfile,
        gpuInfo: GpuCapabilityInfo
    ): Result<ProcessExecutionState> = runCatching {
        val exePath = gameResult.localExecutablePath
            ?: error("NSUNSR.exe must be available in local private storage or mounted path before runtime execution.")
        val exeFile = File(exePath)
        if (!exeFile.exists()) {
            error("Game executable missing at configured path.")
        }

        val dxvkInstalled = runtimeManager.components.value
            .firstOrNull { it.type == com.stormrevolution.launcher.model.RuntimeComponentType.DXVK }
            ?.installStatus == com.stormrevolution.launcher.model.InstallStatus.VERIFIED

        val effectiveBackend = graphicsManager.resolveEffectiveBackend(
            profile.graphicsBackend, gpuInfo, dxvkInstalled
        )
        graphicsManager.writeDxvkConfiguration(profile, effectiveBackend)

        val route = runtimeManager.resolveCpuTranslationRoute(gameResult.peInspection.architecture)
        if (!route.isExecutableReady || route.primaryBinary == null || route.wineBinary == null) {
            val msg = route.blockingReason ?: "Runtime translation path is not ready."
            diagnosticManager.error(LogCategory.PROC, msg)
            error(msg)
        }

        val envMap = wineManager.buildRuntimeEnvironment(profile, effectiveBackend, gpuInfo, route)
        val workingDir = File(gameResult.workingDirectoryPath ?: exeFile.parentFile?.absolutePath ?: "/")
        val cmd = listOf(
            route.primaryBinary.absolutePath,
            route.wineBinary.absolutePath,
            exeFile.absolutePath
        )

        diagnosticManager.info(LogCategory.PROC, "Translation route: ${route.routeDescription}")
        diagnosticManager.info(LogCategory.DXVK, "Graphics backend: ${effectiveBackend.displayName}")
        diagnosticManager.info(LogCategory.PROC, "Launching command: ${cmd.joinToString(" ")}")

        val started = startProcessInternal(
            command = cmd,
            envMap = envMap,
            workingDir = workingDir,
            routeLabel = route.routeDescription,
            graphicsLabel = effectiveBackend.displayName
        )
        started
    }

    fun runOneShotCommand(
        command: List<String>,
        envMap: Map<String, String>,
        workingDir: File
    ): Int {
        return runCatching {
            val pb = ProcessBuilder(command)
            pb.directory(workingDir)
            pb.environment().putAll(envMap)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) diagnosticManager.info(LogCategory.WINE, line)
                }
            }
            val code = proc.waitFor()
            diagnosticManager.info(LogCategory.WINE, "One-shot command exited with code $code")
            code
        }.getOrElse { ex ->
            diagnosticManager.error(LogCategory.WINE, "One-shot command failed: ${ex.message}")
            -1
        }
    }

    fun stopActiveProcess(force: Boolean = false) {
        monitorJob?.cancel()
        activeNativePid?.let { pid ->
            NativeBridge.terminateNativeProcess(pid, force)
            diagnosticManager.warn(LogCategory.PROC, "Sent ${if (force) "SIGKILL" else "SIGTERM"} to native PID $pid")
        }
        activeJvmProcess?.let { proc ->
            if (force) proc.destroyForcibly() else proc.destroy()
            diagnosticManager.warn(LogCategory.PROC, "Terminated managed runtime process.")
        }
        activeNativePid = null
        activeJvmProcess = null
        _executionState.value = _executionState.value.copy(isRunning = false, pid = null)
    }

    private fun startProcessInternal(
        command: List<String>,
        envMap: Map<String, String>,
        workingDir: File,
        routeLabel: String,
        graphicsLabel: String
    ): ProcessExecutionState {
        stopActiveProcess(force = true)

        if (NativeBridge.isLoaded) {
            val args = command.drop(1).toTypedArray()
            val envPairs = envMap.entries.map { "${it.key}=${it.value}" }.toTypedArray()
            val spawned = NativeBridge.spawnNativeProcess(
                executable = command.first(),
                args = args,
                envPairs = envPairs,
                workingDir = workingDir.absolutePath
            )
            if (spawned != null && spawned.size == 3 && spawned[0] > 0) {
                val pid = spawned[0]
                val stdoutFd = spawned[1]
                val stderrFd = spawned[2]
                activeNativePid = pid
                val state = ProcessExecutionState(
                    isRunning = true,
                    pid = pid,
                    commandPreview = command.joinToString(" "),
                    workingDirectory = workingDir.absolutePath,
                    activeTranslationPath = routeLabel,
                    activeGraphicsPath = graphicsLabel,
                    lastExitCode = null,
                    crashReason = null,
                    startedAtMillis = System.currentTimeMillis()
                )
                _executionState.value = state
                monitorNativeProcess(pid, stdoutFd, stderrFd)
                return state
            }
        }

        // Fallback to JVM ProcessBuilder when JNI .so is unavailable
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        pb.environment().putAll(envMap)
        val proc = pb.start()
        activeJvmProcess = proc

        val state = ProcessExecutionState(
            isRunning = true,
            pid = extractPidFallback(proc),
            commandPreview = command.joinToString(" "),
            workingDirectory = workingDir.absolutePath,
            activeTranslationPath = routeLabel,
            activeGraphicsPath = graphicsLabel,
            lastExitCode = null,
            crashReason = null,
            startedAtMillis = System.currentTimeMillis()
        )
        _executionState.value = state
        monitorJvmProcess(proc)
        return state
    }

    private fun monitorNativeProcess(pid: Int, stdoutFd: Int, stderrFd: Int) {
        monitorJob = scope.launch {
            try {
                while (isActive) {
                    val out = NativeBridge.readFdNonBlocking(stdoutFd)
                    if (out.isNotEmpty()) {
                        out.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            diagnosticManager.info(classifyLogCategory(line), line)
                        }
                    }
                    val err = NativeBridge.readFdNonBlocking(stderrFd)
                    if (err.isNotEmpty()) {
                        err.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            diagnosticManager.error(classifyLogCategory(line), line)
                        }
                    }
                    val exitCode = NativeBridge.pollWaitPid(pid)
                    if (exitCode != -999) {
                        val crashMsg = describeExitCode(exitCode)
                        if (exitCode != 0) {
                            diagnosticManager.error(LogCategory.PROC, crashMsg)
                        } else {
                            diagnosticManager.info(LogCategory.PROC, "Game process exited cleanly (code 0).")
                        }
                        _executionState.value = _executionState.value.copy(
                            isRunning = false,
                            pid = null,
                            lastExitCode = exitCode,
                            crashReason = if (exitCode != 0) crashMsg else null
                        )
                        activeNativePid = null
                        break
                    }
                    delay(80L)
                }
            } finally {
                NativeBridge.closeFd(stdoutFd)
                NativeBridge.closeFd(stderrFd)
            }
        }
    }

    private fun monitorJvmProcess(proc: Process) {
        monitorJob = scope.launch {
            launch {
                BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) diagnosticManager.info(classifyLogCategory(line), line)
                    }
                }
            }
            launch {
                BufferedReader(InputStreamReader(proc.errorStream)).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) diagnosticManager.error(classifyLogCategory(line), line)
                    }
                }
            }
            val exitCode = proc.waitFor()
            val crashMsg = describeExitCode(exitCode)
            if (exitCode != 0) diagnosticManager.error(LogCategory.PROC, crashMsg)
            else diagnosticManager.info(LogCategory.PROC, "Process exited cleanly (code 0).")

            _executionState.value = _executionState.value.copy(
                isRunning = false,
                pid = null,
                lastExitCode = exitCode,
                crashReason = if (exitCode != 0) crashMsg else null
            )
            activeJvmProcess = null
        }
    }

    private fun classifyLogCategory(line: String): LogCategory {
        val lower = line.lowercase()
        return when {
            lower.contains("box64") -> LogCategory.BOX64
            lower.contains("box86") -> LogCategory.BOX86
            lower.contains("dxvk") || lower.contains("d3d9") -> LogCategory.DXVK
            lower.contains("pulse") || lower.contains("alsa") -> LogCategory.AUDIO
            else -> LogCategory.WINE
        }
    }

    private fun describeExitCode(code: Int): String = when (code) {
        0 -> "Exited normally (0)"
        126 -> "Permission denied / working directory inaccessible (126)"
        127 -> "Runtime binary not found or missing dynamic linker (127)"
        132 -> "Illegal instruction SIGILL (132) — incompatible CPU DynaRec flags"
        134 -> "Process aborted SIGABRT (134) — Wine/DXVK assertion failure"
        139 -> "Segmentation fault SIGSEGV (139) — memory or 32-bit translation crash"
        else -> "Process terminated with exit code $code"
    }

    private fun extractPidFallback(process: Process): Int? {
        return runCatching {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        }.getOrNull()
    }
}
