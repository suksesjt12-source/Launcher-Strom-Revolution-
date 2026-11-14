package com.stormrevolution.launcher.manager

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.stormrevolution.launcher.model.CpuMode
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.PrefixHealth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WineManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val graphicsManager: GraphicsManager,
    private val audioManager: AudioManager,
    private val diagnosticManager: DiagnosticManager
) {

    fun inspectPrefixHealth(): PrefixHealth {
        val prefix = storageManager.prefixDir
        val driveC = File(prefix, "drive_c")
        val sys32 = File(driveC, "windows/system32")
        val sysWow64 = File(driveC, "windows/syswow64")
        val systemReg = File(prefix, "system.reg")
        val userReg = File(prefix, "user.reg")
        val userDefReg = File(prefix, "userdef.reg")

        val hasRegs = systemReg.exists() && userReg.exists() && userDefReg.exists()
        val hasD3d9Override = userReg.exists() && runCatching {
            userReg.readText().contains("\"d3d9\"")
        }.getOrDefault(false)

        val missing = buildList {
            if (!prefix.exists()) add("prefix root directory")
            if (!driveC.exists()) add("drive_c")
            if (!sys32.exists()) add("windows/system32")
            if (!sysWow64.exists()) add("windows/syswow64")
            if (!hasRegs) add("Wine registry hives (system.reg / user.reg / userdef.reg)")
            if (!hasD3d9Override) add("Direct3D 9 DLL override entry")
        }

        return PrefixHealth(
            exists = prefix.exists(),
            prefixPath = prefix.absolutePath,
            hasDriveC = driveC.exists(),
            hasSystem32 = sys32.exists(),
            hasSysWow64 = sysWow64.exists(),
            hasRegistryFiles = hasRegs,
            hasD3d9Override = hasD3d9Override,
            missingComponents = missing
        )
    }

    fun createOrRepairPrefix(
        profile: GameProfile,
        effectiveBackend: GraphicsBackend,
        route: CpuTranslationRoute?,
        processManager: ProcessManager
    ): PrefixHealth {
        storageManager.ensureBaseDirectories()
        val prefix = storageManager.prefixDir
        val driveC = File(prefix, "drive_c")
        val dirs = listOf(
            prefix,
            driveC,
            File(driveC, "windows/system32"),
            File(driveC, "windows/syswow64"),
            File(driveC, "windows/Temp"),
            File(driveC, "Program Files"),
            File(driveC, "Program Files (x86)"),
            File(driveC, "users/storm/Documents"),
            File(driveC, "users/storm/AppData/Local"),
            File(driveC, "users/storm/AppData/Roaming"),
            File(prefix, "dosdevices")
        )
        dirs.forEach { if (!it.exists()) it.mkdirs() }

        // Create dosdevices c: -> ../drive_c and z: -> /
        createRelativeSymlinkIfPossible(File(prefix, "dosdevices/c:"), "../drive_c")
        createRelativeSymlinkIfPossible(File(prefix, "dosdevices/z:"), "/")

        writeRegistryHives(prefix, profile, effectiveBackend)
        diagnosticManager.info(LogCategory.WINE, "Initialized/repaired Wine prefix structure at <app_sandbox>/files/prefix/.")

        // If real Wine + CPU translator binaries exist, invoke wineboot --init
        val wineBin = route?.wineBinary
        val primaryBin = route?.primaryBinary
        if (route != null && route.isExecutableReady && wineBin != null && primaryBin != null) {
            val env = buildRuntimeEnvironment(
                profile = profile,
                effectiveBackend = effectiveBackend,
                gpuInfo = graphicsManager.probeGpuCapabilities(),
                route = route
            )
            diagnosticManager.info(LogCategory.WINE, "Executing wineboot --init via ${primaryBin.name}...")
            val cmd = listOf(primaryBin.absolutePath, wineBin.absolutePath, "wineboot", "--init")
            processManager.runOneShotCommand(cmd, env, prefix)
        } else {
            diagnosticManager.warn(
                LogCategory.WINE,
                "Wine binary not yet installed; created prefix directory structure & registry hives offline."
            )
        }

        return inspectPrefixHealth()
    }

    fun resetPrefix(
        profile: GameProfile,
        effectiveBackend: GraphicsBackend,
        route: CpuTranslationRoute?,
        processManager: ProcessManager
    ): PrefixHealth {
        if (storageManager.prefixDir.exists()) {
            storageManager.prefixDir.deleteRecursively()
            diagnosticManager.warn(LogCategory.WINE, "Reset Wine prefix directory.")
        }
        return createOrRepairPrefix(profile, effectiveBackend, route, processManager)
    }

    fun backupPrefix(): Result<File> {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outZip = File(storageManager.backupsDir, "storm_prefix_$ts.zip")
        return storageManager.zipDirectory(storageManager.prefixDir, outZip).onSuccess {
            diagnosticManager.info(LogCategory.WINE, "Backed up prefix to backups/${it.name}")
        }.onFailure {
            diagnosticManager.error(LogCategory.WINE, "Prefix backup failed: ${it.message}")
        }
    }

    fun restorePrefixFromUri(uri: Uri): Result<PrefixHealth> = runCatching {
        val tempDir = File(context.cacheDir, "prefix_restore_${SystemClock.uptimeMillis()}")
        tempDir.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                storageManager.extractArchive(stream, "prefix_backup.zip", tempDir).getOrThrow()
            } ?: error("Cannot open backup ZIP URI.")
            storageManager.prefixDir.deleteRecursively()
            storageManager.prefixDir.mkdirs()
            tempDir.copyRecursively(storageManager.prefixDir, overwrite = true)
            diagnosticManager.info(LogCategory.WINE, "Restored Wine prefix from backup archive.")
        } finally {
            tempDir.deleteRecursively()
        }
        inspectPrefixHealth()
    }

    fun buildRuntimeEnvironment(
        profile: GameProfile,
        effectiveBackend: GraphicsBackend,
        gpuInfo: GpuCapabilityInfo,
        route: CpuTranslationRoute
    ): Map<String, String> {
        storageManager.ensureBaseDirectories()
        val env = linkedMapOf<String, String>()
        val rootfsDir = File(storageManager.runtimeDir, "rootfs")
        val wineDir = File(storageManager.runtimeDir, "wine")
        val libsDir = File(storageManager.runtimeDir, "libs")

        env["HOME"] = File(storageManager.prefixDir, "drive_c/users/storm").absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["WINEPREFIX"] = storageManager.prefixDir.absolutePath
        env["WINEARCH"] = if (route.usesWow64) "win64" else "win32"
        env["WINEDEBUG"] = "-all,err+seh,err+module,fixme+d3d"
        env["WINEESYNC"] = "1"
        env["WINEFSYNC"] = "0"
        env["WINEDLLOVERRIDES_BASE"] = "winemenubuilder.exe=d"

        // Box64 / Box86 tuning for MediaTek Helio G100 (Cortex-A76 + A55, ARM64)
        val cpuMode: CpuMode = profile.cpuMode
        env["BOX64_NOBANNER"] = "0"
        env["BOX64_DYNAREC"] = "1"
        env["BOX64_DYNAREC_BIGBLOCK"] = cpuMode.dynarecBigBlock.toString()
        env["BOX64_DYNAREC_STRONGMEM"] = cpuMode.strongMem.toString()
        env["BOX64_DYNAREC_FASTNAN"] = "1"
        env["BOX64_DYNAREC_FASTROUND"] = "1"
        env["BOX64_DYNAREC_SAFEFLAGS"] = if (cpuMode == CpuMode.CONSERVATIVE) "2" else "1"
        env["BOX64_LD_LIBRARY_PATH"] = listOf(
            File(wineDir, "lib").absolutePath,
            File(wineDir, "lib64").absolutePath,
            libsDir.absolutePath,
            File(rootfsDir, "usr/lib/x86_64-linux-gnu").absolutePath
        ).joinToString(":")

        if (route.usesBox86) {
            env["BOX86_DYNAREC"] = "1"
            env["BOX86_DYNAREC_BIGBLOCK"] = cpuMode.dynarecBigBlock.toString()
            env["BOX86_DYNAREC_STRONGMEM"] = cpuMode.strongMem.toString()
        }

        env["LD_LIBRARY_PATH"] = listOf(
            libsDir.absolutePath,
            File(rootfsDir, "lib/aarch64-linux-gnu").absolutePath,
            File(rootfsDir, "usr/lib/aarch64-linux-gnu").absolutePath,
            "/system/lib64",
            "/vendor/lib64"
        ).joinToString(":")

        env["PATH"] = listOf(
            File(storageManager.runtimeDir, "box64/bin").absolutePath,
            File(storageManager.runtimeDir, "box86/bin").absolutePath,
            File(wineDir, "bin").absolutePath,
            File(storageManager.runtimeDir, "audio/bin").absolutePath,
            File(rootfsDir, "usr/bin").absolutePath,
            "/system/bin"
        ).joinToString(":")

        // Display & X11 / Virtual screen parameters
        env["DISPLAY"] = ":0"
        env["STORM_RESOLUTION"] = "${profile.resolutionWidth}x${profile.resolutionHeight}"
        env["STORM_XINPUT_SOCKET"] = storageManager.xinputSocketFile.absolutePath

        // Graphics & Audio env vars
        env.putAll(graphicsManager.buildGraphicsEnvVars(profile, effectiveBackend, gpuInfo))
        env.putAll(audioManager.writeAudioConfiguration(profile))

        return env
    }

    private fun writeRegistryHives(
        prefix: File,
        profile: GameProfile,
        effectiveBackend: GraphicsBackend
    ) {
        val d3d9Override = if (effectiveBackend == GraphicsBackend.DXVK) "native,builtin" else "builtin"
        val userReg = File(prefix, "user.reg")
        userReg.writeText(
            """
            WINE REGISTRY Version 2
            ;; All keys relative to \\User\\.Default

            [Software\\Wine\\DllOverrides]
            "d3d9"="$d3d9Override"
            "d3dx9_43"="native,builtin"
            "xinput1_3"="native,builtin"
            "winemenubuilder.exe"=""

            [Software\\Wine\\Direct3D]
            "csmt"=dword:00000001
            "VideoMemorySize"="2048"
            "StrictDrawOrdering"="disabled"

            [Software\\Wine\\Explorer\\Desktops]
            "StormDesktop"="${profile.resolutionWidth}x${profile.resolutionHeight}"
            """.trimIndent() + "\n"
        )

        val systemReg = File(prefix, "system.reg")
        if (!systemReg.exists()) {
            systemReg.writeText(
                """
                WINE REGISTRY Version 2
                ;; All keys relative to \\Machine

                [Software\\Microsoft\\Windows NT\\CurrentVersion]
                "CurrentVersion"="6.1"
                "ProductName"="Microsoft Windows 7"
                """.trimIndent() + "\n"
            )
        }

        val userDefReg = File(prefix, "userdef.reg")
        if (!userDefReg.exists()) {
            userDefReg.writeText("WINE REGISTRY Version 2\n")
        }
    }

    private fun createRelativeSymlinkIfPossible(linkFile: File, target: String) {
        runCatching {
            if (!linkFile.exists()) {
                android.system.Os.symlink(target, linkFile.absolutePath)
            }
        }
    }
}
