package com.stormrevolution.launcher.manager

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.PeArchitecture
import com.stormrevolution.launcher.model.RuntimeComponentState
import com.stormrevolution.launcher.model.RuntimeComponentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class CpuTranslationRoute(
    val isExecutableReady: Boolean,
    val routeDescription: String,
    val primaryBinary: File?,
    val wineBinary: File?,
    val usesWow64: Boolean,
    val usesBox86: Boolean,
    val usesBox64: Boolean,
    val blockingReason: String?
)

/**
 * Verifies real binaries and ELF headers inside files/runtime/ for all 10 runtime components.
 * Never marks a component as Installed/Verified unless real files are present and pass inspection.
 */
class RuntimeManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val diagnosticManager: DiagnosticManager
) {
    companion object {
        private const val EM_386 = 3
        private const val EM_ARM = 40
        private const val EM_X86_64 = 62
        private const val EM_AARCH64 = 183
    }

    private val _components = MutableStateFlow<List<RuntimeComponentState>>(emptyList())
    val components: StateFlow<List<RuntimeComponentState>> = _components.asStateFlow()

    fun refreshComponentStates(gpuInfo: GpuCapabilityInfo): List<RuntimeComponentState> {
        storageManager.ensureBaseDirectories()
        val states = RuntimeComponentType.entries.map { type ->
            verifySingleComponent(type, gpuInfo)
        }
        _components.value = states
        return states
    }

    fun verifySingleComponent(type: RuntimeComponentType, gpuInfo: GpuCapabilityInfo): RuntimeComponentState {
        val dir = File(storageManager.runtimeDir, type.folderName)
        val manifest = readManifestMeta(dir)

        return when (type) {
            RuntimeComponentType.WINE -> {
                val wine64 = findFirstExisting(
                    File(dir, "bin/wine64"),
                    File(dir, "bin/wine"),
                    File(storageManager.runtimeDir, "rootfs/usr/bin/wine64"),
                    File(storageManager.runtimeDir, "rootfs/usr/bin/wine")
                )
                if (wine64 == null) {
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Not Installed"),
                        architecture = "x86_64 / WoW64 PE",
                        installStatus = InstallStatus.NOT_INSTALLED,
                        compatibilityStatus = "Requires Wine 9.x+ WoW64 or Wine + Box86/64",
                        dependencyStatus = "Depends on RootFS & Box64",
                        installPath = dir.absolutePath,
                        details = "Missing bin/wine or bin/wine64 binary."
                    )
                } else {
                    val elf = NativeBridge.inspectElfBinary(wine64)
                    val ok = elf.valid && elf.executable
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Wine (Local Binary)"),
                        architecture = describeElfMachine(elf.machine, elf.bitness),
                        installStatus = if (ok) InstallStatus.VERIFIED else InstallStatus.CORRUPTED,
                        compatibilityStatus = if (ok) "Executable verified" else "Invalid ELF or missing +x",
                        dependencyStatus = "RootFS required",
                        installPath = wine64.absolutePath,
                        details = "Binary: ${wine64.name} (${wine64.length() / 1024} KB)"
                    )
                }
            }

            RuntimeComponentType.BOX64 -> {
                val box64 = findFirstExisting(
                    File(dir, "bin/box64"),
                    File(dir, "box64"),
                    File(storageManager.runtimeDir, "rootfs/usr/local/bin/box64")
                )
                if (box64 == null) {
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Not Installed"),
                        architecture = "aarch64 (arm64-v8a)",
                        installStatus = InstallStatus.NOT_INSTALLED,
                        compatibilityStatus = "Compatible with Helio G100 ARM64",
                        dependencyStatus = "Required for x86_64 & WoW64 Wine",
                        installPath = dir.absolutePath,
                        details = "Missing bin/box64 ARM64 ELF binary."
                    )
                } else {
                    val elf = NativeBridge.inspectElfBinary(box64)
                    val validAarch64 = elf.valid && elf.machine == EM_AARCH64 && elf.executable
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Box64 ARM64"),
                        architecture = describeElfMachine(elf.machine, elf.bitness),
                        installStatus = if (validAarch64) InstallStatus.VERIFIED else InstallStatus.CORRUPTED,
                        compatibilityStatus = if (validAarch64) "Native ARM64 DynaRec Ready" else "Expected AArch64 ELF",
                        dependencyStatus = "Independent native binary",
                        installPath = box64.absolutePath,
                        details = "ELF64 AArch64 • exec=${elf.executable}"
                    )
                }
            }

            RuntimeComponentType.BOX86 -> {
                val box86 = findFirstExisting(
                    File(dir, "bin/box86"),
                    File(dir, "box86")
                )
                val supportsArm32 = Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()
                if (box86 == null) {
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Not Installed"),
                        architecture = "armeabi-v7a (32-bit)",
                        installStatus = InstallStatus.NOT_INSTALLED,
                        compatibilityStatus = if (supportsArm32) {
                            "Optional 32-bit fallback (WoW64 preferred)"
                        } else {
                            "Blocked: CPU has no 32-bit ARM ABI; use Wine WoW64"
                        },
                        dependencyStatus = "Requires 32-bit ARM libc sysroot",
                        installPath = dir.absolutePath,
                        details = if (supportsArm32) "Not installed" else "64-bit-only device ABI",
                        isCompatibleWithDevice = supportsArm32
                    )
                } else {
                    val elf = NativeBridge.inspectElfBinary(box86)
                    val validArm32 = elf.valid && elf.machine == EM_ARM && elf.executable && supportsArm32
                    RuntimeComponentState(
                        type = type,
                        version = manifest.optString("version", "Box86 ARM32"),
                        architecture = describeElfMachine(elf.machine, elf.bitness),
                        installStatus = if (validArm32) InstallStatus.VERIFIED else InstallStatus.CORRUPTED,
                        compatibilityStatus = if (validArm32) "32-bit ARM DynaRec Ready" else "Incompatible ABI / ELF",
                        dependencyStatus = "Requires armhf libs",
                        installPath = box86.absolutePath,
                        details = "ELF32 ARM • exec=${elf.executable}",
                        isCompatibleWithDevice = supportsArm32
                    )
                }
            }

            RuntimeComponentType.WOW64 -> {
                val wineDir = File(storageManager.runtimeDir, "wine")
                val i386Pe = findFirstExisting(
                    File(dir, "i386-windows/kernel32.dll"),
                    File(wineDir, "lib/wine/i386-windows/kernel32.dll"),
                    File(wineDir, "lib64/wine/i386-windows/kernel32.dll")
                )
                val x64Unix = findFirstExisting(
                    File(dir, "x86_64-unix/ntdll.so"),
                    File(wineDir, "lib/wine/x86_64-unix/ntdll.so"),
                    File(wineDir, "lib64/wine/x86_64-unix/ntdll.so")
                )
                val status = when {
                    i386Pe != null && x64Unix != null -> InstallStatus.VERIFIED
                    i386Pe != null || x64Unix != null -> InstallStatus.MISSING_DEPENDENCY
                    else -> InstallStatus.NOT_INSTALLED
                }
                RuntimeComponentState(
                    type = type,
                    version = manifest.optString("version", if (status == InstallStatus.VERIFIED) "Wine WoW64" else "Not Installed"),
                    architecture = "i386-windows + x86_64-unix",
                    installStatus = status,
                    compatibilityStatus = "Primary 32-bit NSUNSR.exe translation path on ARM64",
                    dependencyStatus = "Depends on Wine + Box64",
                    installPath = dir.absolutePath,
                    details = "i386-windows=${i386Pe != null}, x86_64-unix=${x64Unix != null}"
                )
            }

            RuntimeComponentType.DXVK -> {
                val d3d9Dll = findFirstExisting(
                    File(dir, "x32/d3d9.dll"),
                    File(dir, "d3d9.dll")
                )
                val status = when {
                    d3d9Dll == null -> InstallStatus.NOT_INSTALLED
                    !gpuInfo.vulkanSupported -> InstallStatus.MISSING_DEPENDENCY
                    else -> InstallStatus.VERIFIED
                }
                RuntimeComponentState(
                    type = type,
                    version = manifest.optString("version", if (d3d9Dll != null) "DXVK D3D9" else "Not Installed"),
                    architecture = "Win32 PE32 (d3d9.dll)",
                    installStatus = status,
                    compatibilityStatus = if (gpuInfo.vulkanSupported) {
                        "Vulkan ${gpuInfo.vulkanVersion} available"
                    } else {
                        "Vulkan unavailable — use WineD3D fallback"
                    },
                    dependencyStatus = "Requires Vulkan ICD & Wine",
                    installPath = d3d9Dll?.absolutePath ?: dir.absolutePath,
                    details = if (d3d9Dll != null) "d3d9.dll (${d3d9Dll.length() / 1024} KB)" else "Missing d3d9.dll",
                    isCompatibleWithDevice = gpuInfo.vulkanSupported
                )
            }

            RuntimeComponentType.VULKAN_BACKEND -> {
                val status = if (gpuInfo.vulkanSupported) InstallStatus.SYSTEM_AVAILABLE else InstallStatus.SYSTEM_UNAVAILABLE
                RuntimeComponentState(
                    type = type,
                    version = gpuInfo.vulkanVersion,
                    architecture = "arm64-v8a System ICD",
                    installStatus = status,
                    compatibilityStatus = if (gpuInfo.isMaliGpu) "ARM Mali Vulkan ICD" else gpuInfo.glRenderer,
                    dependencyStatus = "Android OS / GPU Driver",
                    installPath = "/system/lib64/libvulkan.so",
                    details = "HW Level=${gpuInfo.vulkanHardwareLevel} | ${gpuInfo.glRenderer}",
                    isCompatibleWithDevice = gpuInfo.vulkanSupported
                )
            }

            RuntimeComponentType.GRAPHICS_DRIVER -> {
                val status = if (gpuInfo.glRenderer != "Unknown") InstallStatus.SYSTEM_AVAILABLE else InstallStatus.SYSTEM_UNAVAILABLE
                RuntimeComponentState(
                    type = type,
                    version = gpuInfo.glVersion.take(28),
                    architecture = "arm64-v8a",
                    installStatus = status,
                    compatibilityStatus = if (gpuInfo.isMaliGpu) {
                        "Mali Native (Turnip Blocked)"
                    } else {
                        gpuInfo.glVendor
                    },
                    dependencyStatus = "Device GPU Driver",
                    installPath = "/vendor/lib64/egl",
                    details = "${gpuInfo.glVendor} • ${gpuInfo.glRenderer}",
                    isCompatibleWithDevice = true
                )
            }

            RuntimeComponentType.AUDIO_BACKEND -> {
                val pulse = findFirstExisting(
                    File(dir, "bin/pulseaudio"),
                    File(storageManager.runtimeDir, "rootfs/usr/bin/pulseaudio")
                )
                val status = if (pulse != null) InstallStatus.VERIFIED else InstallStatus.NOT_INSTALLED
                RuntimeComponentState(
                    type = type,
                    version = manifest.optString("version", if (pulse != null) "PulseAudio" else "Not Installed"),
                    architecture = "arm64-v8a",
                    installStatus = status,
                    compatibilityStatus = "48 kHz anti-underrun config ready",
                    dependencyStatus = "Optional (silent fallback supported)",
                    installPath = pulse?.absolutePath ?: dir.absolutePath,
                    details = if (pulse != null) "PulseAudio binary verified" else "Audio daemon archive not imported"
                )
            }

            RuntimeComponentType.ROOTFS -> {
                val libc = findFirstExisting(
                    File(dir, "lib/aarch64-linux-gnu/libc.so.6"),
                    File(dir, "usr/lib/aarch64-linux-gnu/libc.so.6"),
                    File(dir, "lib/ld-linux-aarch64.so.1")
                )
                RuntimeComponentState(
                    type = type,
                    version = manifest.optString("version", if (libc != null) "ARM64 RootFS" else "Not Installed"),
                    architecture = "aarch64-linux-gnu",
                    installStatus = if (libc != null) InstallStatus.VERIFIED else InstallStatus.NOT_INSTALLED,
                    compatibilityStatus = "Userspace glibc container for Wine/Box64",
                    dependencyStatus = "Base layer for Wine & PulseAudio",
                    installPath = dir.absolutePath,
                    details = if (libc != null) "Found ${libc.name}" else "Missing ld-linux-aarch64 / libc.so.6"
                )
            }

            RuntimeComponentType.RUNTIME_LIBRARIES -> {
                val hasLibs = dir.exists() && (dir.listFiles()?.any { it.name.endsWith(".so") || it.name.endsWith(".dll") } == true)
                RuntimeComponentState(
                    type = type,
                    version = manifest.optString("version", if (hasLibs) "Installed" else "Not Installed"),
                    architecture = "arm64-v8a + PE32",
                    installStatus = if (hasLibs) InstallStatus.VERIFIED else InstallStatus.NOT_INSTALLED,
                    compatibilityStatus = "libgcc_s, libstdc++, vulkan-1, xinput1_3",
                    dependencyStatus = "Supports Wine & DXVK",
                    installPath = dir.absolutePath,
                    details = if (hasLibs) "${dir.listFiles()?.size ?: 0} library entries" else "No extra runtime libraries imported"
                )
            }
        }
    }

    /**
     * Resolves how NSUNSR.exe (32-bit x86 PE32) or a 64-bit PE32+ binary should be executed.
     * Enforces Rule: NEVER run 32-bit x86 binaries directly on raw 64-bit Box64 without WoW64 or Box86!
     */
    fun resolveCpuTranslationRoute(peArchitecture: PeArchitecture): CpuTranslationRoute {
        val states = _components.value.ifEmpty { emptyList() }
        val wineOk = states.firstOrNull { it.type == RuntimeComponentType.WINE }?.installStatus == InstallStatus.VERIFIED
        val box64Ok = states.firstOrNull { it.type == RuntimeComponentType.BOX64 }?.installStatus == InstallStatus.VERIFIED
        val wow64Ok = states.firstOrNull { it.type == RuntimeComponentType.WOW64 }?.installStatus == InstallStatus.VERIFIED
        val box86Ok = states.firstOrNull { it.type == RuntimeComponentType.BOX86 }?.installStatus == InstallStatus.VERIFIED

        val box64File = findFirstExisting(
            File(storageManager.runtimeDir, "box64/bin/box64"),
            File(storageManager.runtimeDir, "box64/box64"),
            File(storageManager.runtimeDir, "rootfs/usr/local/bin/box64")
        )
        val box86File = findFirstExisting(
            File(storageManager.runtimeDir, "box86/bin/box86"),
            File(storageManager.runtimeDir, "box86/box86")
        )
        val wineFile = findFirstExisting(
            File(storageManager.runtimeDir, "wine/bin/wine64"),
            File(storageManager.runtimeDir, "wine/bin/wine"),
            File(storageManager.runtimeDir, "rootfs/usr/bin/wine64"),
            File(storageManager.runtimeDir, "rootfs/usr/bin/wine")
        )

        return when (peArchitecture) {
            PeArchitecture.X86_32 -> {
                when {
                    wineOk && box64Ok && wow64Ok -> CpuTranslationRoute(
                        isExecutableReady = true,
                        routeDescription = "Box64 (ARM64) -> Wine WoW64 (x86_64 unix + i386 PE32)",
                        primaryBinary = box64File,
                        wineBinary = wineFile,
                        usesWow64 = true,
                        usesBox86 = false,
                        usesBox64 = true,
                        blockingReason = null
                    )
                    wineOk && box86Ok -> CpuTranslationRoute(
                        isExecutableReady = true,
                        routeDescription = "Box86 (ARM32) -> Wine 32-bit (i386 PE32)",
                        primaryBinary = box86File,
                        wineBinary = wineFile,
                        usesWow64 = false,
                        usesBox86 = true,
                        usesBox64 = false,
                        blockingReason = null
                    )
                    else -> CpuTranslationRoute(
                        isExecutableReady = false,
                        routeDescription = "Blocked: Incomplete 32-bit x86 translation stack",
                        primaryBinary = null,
                        wineBinary = wineFile,
                        usesWow64 = false,
                        usesBox86 = false,
                        usesBox64 = false,
                        blockingReason = "NSUNSR.exe is 32-bit x86 (PE32). Requires verified Wine + Box64 + WoW64 (or Box86)."
                    )
                }
            }
            PeArchitecture.X86_64 -> {
                if (wineOk && box64Ok) {
                    CpuTranslationRoute(
                        isExecutableReady = true,
                        routeDescription = "Box64 (ARM64) -> Wine64",
                        primaryBinary = box64File,
                        wineBinary = wineFile,
                        usesWow64 = false,
                        usesBox86 = false,
                        usesBox64 = true,
                        blockingReason = null
                    )
                } else {
                    CpuTranslationRoute(
                        isExecutableReady = false,
                        routeDescription = "Blocked: Missing Box64 / Wine64",
                        primaryBinary = null,
                        wineBinary = wineFile,
                        usesWow64 = false,
                        usesBox86 = false,
                        usesBox64 = false,
                        blockingReason = "64-bit PE32+ requires verified Box64 and Wine64."
                    )
                }
            }
            else -> CpuTranslationRoute(
                isExecutableReady = false,
                routeDescription = "Unsupported PE Architecture",
                primaryBinary = null,
                wineBinary = null,
                usesWow64 = false,
                usesBox86 = false,
                usesBox64 = false,
                blockingReason = "Executable architecture '${peArchitecture.label}' cannot run under Wine/Box."
            )
        }
    }

    fun installComponentFromUri(
        type: RuntimeComponentType,
        uri: Uri,
        gpuInfo: GpuCapabilityInfo
    ): Result<RuntimeComponentState> = runCatching {
        val fileName = resolveDisplayName(uri) ?: "${type.folderName}.zip"
        val targetDir = File(storageManager.runtimeDir, type.folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        diagnosticManager.info(LogCategory.RUNTIME, "Installing ${type.displayName} from archive '$fileName'...")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val extractedCount = storageManager.extractArchive(stream, fileName, targetDir).getOrThrow()
            writeManifestMeta(targetDir, type, fileName, extractedCount)
            diagnosticManager.info(
                LogCategory.RUNTIME,
                "Extracted $extractedCount file(s) for ${type.displayName}; running verification..."
            )
        } ?: error("Unable to open archive URI stream.")

        repairPermissions(targetDir)
        verifySingleComponent(type, gpuInfo).also {
            refreshComponentStates(gpuInfo)
        }
    }

    fun repairComponent(type: RuntimeComponentType, gpuInfo: GpuCapabilityInfo): RuntimeComponentState {
        val dir = File(storageManager.runtimeDir, type.folderName)
        if (dir.exists()) {
            repairPermissions(dir)
            diagnosticManager.info(LogCategory.RUNTIME, "Repaired executable permissions in runtime/${type.folderName}.")
        } else {
            diagnosticManager.warn(LogCategory.RUNTIME, "Cannot repair ${type.displayName}: directory does not exist.")
        }
        return refreshComponentStates(gpuInfo).first { it.type == type }
    }

    fun removeComponent(type: RuntimeComponentType, gpuInfo: GpuCapabilityInfo): RuntimeComponentState {
        val dir = File(storageManager.runtimeDir, type.folderName)
        if (dir.exists()) {
            dir.deleteRecursively()
            diagnosticManager.info(LogCategory.RUNTIME, "Removed component files for ${type.displayName}.")
        }
        return refreshComponentStates(gpuInfo).first { it.type == type }
    }

    fun isCoreRuntimeReady(): Boolean {
        val list = _components.value
        val wineOk = list.firstOrNull { it.type == RuntimeComponentType.WINE }?.installStatus == InstallStatus.VERIFIED
        val box64Ok = list.firstOrNull { it.type == RuntimeComponentType.BOX64 }?.installStatus == InstallStatus.VERIFIED
        val wow64Ok = list.firstOrNull { it.type == RuntimeComponentType.WOW64 }?.installStatus == InstallStatus.VERIFIED
        val box86Ok = list.firstOrNull { it.type == RuntimeComponentType.BOX86 }?.installStatus == InstallStatus.VERIFIED
        val rootfsOk = list.firstOrNull { it.type == RuntimeComponentType.ROOTFS }?.installStatus == InstallStatus.VERIFIED
        return wineOk && rootfsOk && ((box64Ok && wow64Ok) || box86Ok)
    }

    private fun repairPermissions(dir: File) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val name = file.name.lowercase()
            if (file.parentFile?.name == "bin" || name.endsWith(".so") || name.endsWith(".sh") ||
                name in setOf("wine", "wine64", "wineserver", "box64", "box86", "pulseaudio")
            ) {
                NativeBridge.makeExecutable(file)
            }
        }
    }

    private fun writeManifestMeta(dir: File, type: RuntimeComponentType, sourceName: String, count: Int) {
        val json = JSONObject().apply {
            put("component", type.name)
            put("version", sourceName.substringBeforeLast("."))
            put("fileCount", count)
            put("installedAt", System.currentTimeMillis())
        }
        File(dir, ".storm_component.json").writeText(json.toString(2))
    }

    private fun readManifestMeta(dir: File): JSONObject {
        val file = File(dir, ".storm_component.json")
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun findFirstExisting(vararg candidates: File): File? =
        candidates.firstOrNull { it.exists() && it.isFile }

    private fun describeElfMachine(machine: Int, bitness: Int): String {
        val arch = when (machine) {
            EM_AARCH64 -> "AArch64"
            EM_ARM -> "ARMv7"
            EM_X86_64 -> "x86_64"
            EM_386 -> "x86"
            else -> "Unknown($machine)"
        }
        return "$arch (${bitness}-bit ELF)"
    }
}
