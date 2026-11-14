package com.stormrevolution.launcher.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameStatus(val label: String) {
    GAME_NOT_FOUND("Game Not Found"),
    GAME_FOUND("Game Found"),
    GAME_VALIDATION_FAILED("Game Validation Failed"),
    GAME_READY("Game Ready"),
    RUNTIME_MISSING("Runtime Missing"),
    READY_TO_LAUNCH("Ready to Launch")
}

@Serializable
enum class PeArchitecture(val label: String, val is32BitX86: Boolean) {
    X86_32("x86 (32-bit PE32)", true),
    X86_64("x86_64 (64-bit PE32+)", false),
    ARM64("ARM64 (PE32+)", false),
    UNKNOWN("Unknown Architecture", false)
}

@Serializable
data class PeInspectionResult(
    val isValidPe: Boolean = false,
    val architecture: PeArchitecture = PeArchitecture.UNKNOWN,
    val machineCode: Int = 0,
    val numberOfSections: Int = 0,
    val subsystem: String = "Unknown",
    val isLargeAddressAware: Boolean = false,
    val entryPointRva: Long = 0L,
    val errorReason: String? = null
)

@Serializable
data class GameValidationResult(
    val status: GameStatus = GameStatus.GAME_NOT_FOUND,
    val executableName: String = "NSUNSR.exe",
    val sourceUriString: String? = null,
    val localExecutablePath: String? = null,
    val workingDirectoryPath: String? = null,
    val executableSizeBytes: Long = 0L,
    val totalDirectorySizeBytes: Long = 0L,
    val fileCount: Int = 0,
    val cpkArchiveCount: Int = 0,
    val hasDataWin32: Boolean = false,
    val hasDirectXLibs: Boolean = false,
    val hasXInputLib: Boolean = false,
    val isCopiedToPrivateStorage: Boolean = false,
    val peInspection: PeInspectionResult = PeInspectionResult(),
    val validationMessages: List<String> = emptyList(),
    val lastScannedAtMillis: Long = 0L
)

@Serializable
enum class RuntimeComponentType(
    val displayName: String,
    val folderName: String,
    val licenseName: String,
    val requiredForLaunch: Boolean
) {
    WINE("Wine Compatibility Layer", "wine", "LGPL-2.1", true),
    BOX86("Box86 (x86 32-bit Translator)", "box86", "MIT", false),
    BOX64("Box64 (x86_64 / WoW64 Translator)", "box64", "MIT", true),
    WOW64("Wine WoW64 32-bit Subsystem", "wow64", "LGPL-2.1", true),
    DXVK("DXVK (D3D9 -> Vulkan)", "dxvk", "zlib/libpng", false),
    VULKAN_BACKEND("Android Mali Vulkan Backend", "vulkan", "System ICD", true),
    GRAPHICS_DRIVER("Graphics Driver Profile", "graphics", "Vendor / Mesa", true),
    AUDIO_BACKEND("Audio Backend (PulseAudio / ALSA)", "audio", "LGPL-2.1", false),
    ROOTFS("ARM64 Linux RootFS", "rootfs", "Multi-OSS", true),
    RUNTIME_LIBRARIES("Runtime Support Libraries", "libs", "Multi-OSS", true)
}

@Serializable
enum class InstallStatus(val label: String) {
    NOT_INSTALLED("Not Installed"),
    VERIFIED("Verified"),
    MISSING_DEPENDENCY("Missing Dependency"),
    CORRUPTED("Corrupted / Incomplete"),
    SYSTEM_AVAILABLE("System Available"),
    SYSTEM_UNAVAILABLE("System Unavailable")
}

@Serializable
data class RuntimeComponentState(
    val type: RuntimeComponentType,
    val version: String,
    val architecture: String,
    val installStatus: InstallStatus,
    val compatibilityStatus: String,
    val dependencyStatus: String,
    val installPath: String,
    val details: String,
    val isCompatibleWithDevice: Boolean = true
)

@Serializable
enum class GraphicsBackend(val displayName: String) {
    AUTOMATIC("Automatic Detection"),
    DXVK("DXVK (D3D9 -> Vulkan)"),
    WINED3D("WineD3D (D3D9 -> OpenGL ES)")
}

@Serializable
enum class TextureFilteringMode(val displayName: String, val anisotropyLevel: Int) {
    BILINEAR("Bilinear (Fastest)", 1),
    TRILINEAR("Trilinear", 2),
    ANISOTROPIC_4X("Anisotropic 4x", 4),
    ANISOTROPIC_8X("Anisotropic 8x", 8)
}

@Serializable
enum class CpuMode(val displayName: String, val dynarecBigBlock: Int, val strongMem: Int) {
    CONSERVATIVE("Conservative (Stability)", 1, 2),
    BALANCED("Balanced (Helio G100)", 2, 1),
    PERFORMANCE("Performance (Aggressive)", 3, 0)
}

@Serializable
enum class AudioBackend(val displayName: String) {
    PULSE_AUDIO("PulseAudio (Recommended)"),
    ALSA("ALSA Fallback"),
    DISABLED("Muted / No Audio")
}

@Serializable
enum class GamePreset(val displayName: String) {
    LOW("LOW (854x480 • 30 FPS)"),
    BALANCED("BALANCED (960x540 • 30 FPS)"),
    HIGH("HIGH (1280x720 • 30/60 FPS)"),
    CUSTOM("CUSTOM")
}

@Serializable
enum class AspectRatioMode(val displayName: String, val ratioValue: Float?) {
    RATIO_16_9("16:9 Widescreen (Native)", 16f / 9f),
    FIT_SCREEN("Fit Screen (Stretch)", null),
    PIXEL_PERFECT("Maintain Exact Resolution", 16f / 9f)
}

@Serializable
data class GameProfile(
    val profileName: String = "NSUNSR_OPTIMIZED",
    val preset: GamePreset = GamePreset.BALANCED,
    val resolutionWidth: Int = 960,
    val resolutionHeight: Int = 540,
    val fpsLimit: Int = 30,
    val vsyncEnabled: Boolean = false,
    val graphicsBackend: GraphicsBackend = GraphicsBackend.AUTOMATIC,
    val textureFiltering: TextureFilteringMode = TextureFilteringMode.ANISOTROPIC_4X,
    val shaderCacheEnabled: Boolean = true,
    val pipelineCacheEnabled: Boolean = true,
    val cpuMode: CpuMode = CpuMode.BALANCED,
    val audioBackend: AudioBackend = AudioBackend.PULSE_AUDIO,
    val audioBufferMs: Int = 64,
    val audioVolumePercent: Int = 90,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.RATIO_16_9,
    val maintainAspectRatio: Boolean = true,
    val showFpsOverlay: Boolean = true
)

@Serializable
enum class VirtualButtonId(val label: String, val xinputMask: Int, val defaultX: Float, val defaultY: Float) {
    DPAD_UP("UP", 0x0001, 0.14f, 0.48f),
    DPAD_DOWN("DN", 0x0002, 0.14f, 0.66f),
    DPAD_LEFT("LT", 0x0004, 0.08f, 0.57f),
    DPAD_RIGHT("RT", 0x0008, 0.20f, 0.57f),
    BTN_START("MENU", 0x0010, 0.57f, 0.12f),
    BTN_BACK("PAUSE", 0x0020, 0.43f, 0.12f),
    BTN_LB("SUP-L", 0x0100, 0.12f, 0.20f),
    BTN_RB("GUARD", 0x0200, 0.88f, 0.20f),
    BTN_LT("SUB-L", 0x10000, 0.23f, 0.20f),
    BTN_RT("SUB-R", 0x20000, 0.77f, 0.20f),
    BTN_A("JUMP", 0x1000, 0.82f, 0.78f),
    BTN_B("ATK", 0x2000, 0.90f, 0.64f),
    BTN_X("SHUR", 0x4000, 0.74f, 0.64f),
    BTN_Y("CHKR", 0x8000, 0.82f, 0.50f)
}

@Serializable
data class VirtualButtonLayout(
    val id: VirtualButtonId,
    val normalizedX: Float,
    val normalizedY: Float,
    val scale: Float = 1.0f
)

@Serializable
enum class InputResponseCurve(val displayName: String) {
    LINEAR("Linear (1:1)"),
    COMPETITIVE("Snappy (Fighting)"),
    SMOOTH("Smooth Ramp")
}

@Serializable
data class ControllerConfig(
    val profileName: String = "Storm Default",
    val overlayEnabled: Boolean = true,
    val buttonOpacity: Float = 0.72f,
    val buttonSizeScale: Float = 1.0f,
    val analogSensitivity: Float = 1.15f,
    val analogDeadZone: Float = 0.14f,
    val analogNormalizedX: Float = 0.16f,
    val analogNormalizedY: Float = 0.76f,
    val responseCurve: InputResponseCurve = InputResponseCurve.COMPETITIVE,
    val hapticFeedback: Boolean = true,
    val keyboardMappingEnabled: Boolean = true,
    val xinputBridgeEnabled: Boolean = true,
    val layouts: List<VirtualButtonLayout> = VirtualButtonId.entries.map {
        VirtualButtonLayout(it, it.defaultX, it.defaultY, 1.0f)
    }
)

data class GamepadState(
    val buttonsMask: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val thumbLX: Int = 0,
    val thumbLY: Int = 0,
    val thumbRX: Int = 0,
    val thumbRY: Int = 0
)

enum class LogCategory(val tag: String) {
    RUNTIME("RUNTIME"),
    WINE("WINE"),
    BOX64("BOX64"),
    BOX86("BOX86"),
    DXVK("DXVK"),
    GRAPHICS("GRAPHICS"),
    PROC("PROC"),
    INPUT("INPUT"),
    AUDIO("AUDIO"),
    GAME("GAME"),
    DIAG("DIAG")
}

enum class LogSeverity {
    INFO,
    WARN,
    ERROR
}

data class RuntimeLogEntry(
    val id: Long,
    val timestamp: String,
    val category: LogCategory,
    val severity: LogSeverity,
    val message: String
)

enum class DiagnosticErrorCode(val title: String) {
    WINE_INIT_FAILED("Wine initialization failed"),
    BOX86_INIT_FAILED("Box86 initialization failed"),
    BOX64_INIT_FAILED("Box64 initialization failed"),
    MISSING_DLL("Missing DLL"),
    MISSING_DIRECTX("Missing DirectX component"),
    VULKAN_UNAVAILABLE("Vulkan unavailable"),
    GRAPHICS_RENDERER_FAILED("Graphics renderer failed"),
    INVALID_GAME_DIRECTORY("Invalid game directory"),
    MISSING_EXECUTABLE("Missing executable"),
    PERMISSION_DENIED("Permission denied"),
    INSUFFICIENT_STORAGE("Insufficient storage"),
    INSUFFICIENT_MEMORY("Insufficient memory"),
    GAME_PROCESS_CRASH("Game process crash")
}

data class DiagnosticFinding(
    val code: DiagnosticErrorCode,
    val severity: LogSeverity,
    val summary: String,
    val remediation: String
)

data class DiagnosticReport(
    val generatedAt: String,
    val androidVersion: String,
    val deviceModel: String,
    val cpuArchitecture: String,
    val gpuName: String,
    val runtimeVersion: String,
    val wineVersion: String,
    val translationLayerVersion: String,
    val graphicsBackend: String,
    val gameExecutableDisplay: String,
    val lastErrorMessage: String,
    val findings: List<DiagnosticFinding>,
    val recentLogs: List<String>
)

data class GpuCapabilityInfo(
    val glVendor: String = "Unknown",
    val glRenderer: String = "Unknown",
    val glVersion: String = "Unknown",
    val isMaliGpu: Boolean = false,
    val isAdrenoGpu: Boolean = false,
    val vulkanSupported: Boolean = false,
    val vulkanVersion: String = "Unavailable",
    val vulkanHardwareLevel: Int = -1,
    val turnipBlockedReason: String? = null,
    val recommendedBackend: GraphicsBackend = GraphicsBackend.WINED3D
)

data class SystemTelemetry(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val primaryAbi: String = "",
    val cpuHardware: String = "",
    val cpuCores: Int = 0,
    val cpuLoadPercent: Int = 0,
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val appHeapUsedMb: Long = 0L,
    val internalStorageFreeMb: Long = 0L,
    val shaderCacheSizeMb: Double = 0.0,
    val thermalStatusLabel: String = "Normal",
    val nativeBridgeLoaded: Boolean = false,
    val nativeBridgeError: String? = null,
    val connectedGamepads: List<String> = emptyList()
)

data class ProcessExecutionState(
    val isRunning: Boolean = false,
    val pid: Int? = null,
    val commandPreview: String = "",
    val workingDirectory: String = "",
    val activeTranslationPath: String = "Unverified",
    val activeGraphicsPath: String = "Unverified",
    val lastExitCode: Int? = null,
    val crashReason: String? = null,
    val startedAtMillis: Long = 0L
)

data class PrefixHealth(
    val exists: Boolean = false,
    val prefixPath: String = "",
    val hasDriveC: Boolean = false,
    val hasSystem32: Boolean = false,
    val hasSysWow64: Boolean = false,
    val hasRegistryFiles: Boolean = false,
    val hasD3d9Override: Boolean = false,
    val missingComponents: List<String> = emptyList()
)
