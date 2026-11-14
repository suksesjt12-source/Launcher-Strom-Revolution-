package com.stormrevolution.launcher.manager

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.LogCategory
import java.io.File

/**
 * Probes real GPU / OpenGL ES / Vulkan capabilities on device, blocks incompatible Adreno Turnip
 * configs on ARM Mali GPUs (such as Helio G100's Mali-G57 MC2), and writes DXVK / WineD3D configs.
 */
class GraphicsManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val diagnosticManager: DiagnosticManager
) {

    fun probeGpuCapabilities(): GpuCapabilityInfo {
        val glStrings = queryEglRendererInfo()
        val vendor = glStrings.first
        val renderer = glStrings.second
        val version = glStrings.third

        val combined = "$vendor $renderer".lowercase()
        val isMali = combined.contains("mali") || combined.contains("arm")
        val isAdreno = combined.contains("adreno") || combined.contains("qualcomm")

        val pm = context.packageManager
        val hasVkFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && pm != null) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }
        val vkLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && pm != null) {
            @Suppress("UNNECESSARY_SAFE_CALL")
            pm.systemAvailableFeatures
                ?.firstOrNull { it?.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
                ?.version ?: -1
        } else {
            -1
        }

        val nativeProbe = NativeBridge.probeEnvironment()
        val vulkanAvailable = hasVkFeature || nativeProbe.vulkanLoaderFound
        val vulkanVersion = if (nativeProbe.vulkanLoaderFound && nativeProbe.vulkanVersion != "0.0.0") {
            nativeProbe.vulkanVersion
        } else if (hasVkFeature) {
            "1.1+ (System Feature)"
        } else {
            "Unavailable"
        }

        val turnipBlockedReason = if (isMali) {
            "Mali GPU detected ($renderer): Adreno/Turnip driver configurations are blocked."
        } else if (!isAdreno) {
            "Non-Adreno GPU ($renderer): Turnip driver disabled."
        } else {
            null
        }

        // Mali-G57 supports Vulkan 1.1+, however DXVK D3D9 requires verified Vulkan ICD + DXVK DLLs.
        val recommended = if (vulkanAvailable && !isAdreno) {
            // On Mali, DXVK is viable when Vulkan >= 1.1 is present, with WineD3D as safe fallback.
            GraphicsBackend.DXVK
        } else if (vulkanAvailable) {
            GraphicsBackend.DXVK
        } else {
            GraphicsBackend.WINED3D
        }

        diagnosticManager.info(
            LogCategory.GRAPHICS,
            "GPU Probe: renderer='$renderer', vendor='$vendor', vulkan=$vulkanAvailable ($vulkanVersion), recommended=${recommended.name}"
        )

        return GpuCapabilityInfo(
            glVendor = vendor,
            glRenderer = renderer,
            glVersion = version,
            isMaliGpu = isMali,
            isAdrenoGpu = isAdreno,
            vulkanSupported = vulkanAvailable,
            vulkanVersion = vulkanVersion,
            vulkanHardwareLevel = vkLevel,
            turnipBlockedReason = turnipBlockedReason,
            recommendedBackend = recommended
        )
    }

    fun resolveEffectiveBackend(
        requested: GraphicsBackend,
        gpuInfo: GpuCapabilityInfo,
        dxvkInstalled: Boolean
    ): GraphicsBackend {
        return when (requested) {
            GraphicsBackend.WINED3D -> GraphicsBackend.WINED3D
            GraphicsBackend.DXVK -> {
                if (!gpuInfo.vulkanSupported) {
                    diagnosticManager.warn(
                        LogCategory.DXVK,
                        "DXVK requested but Vulkan ICD is unavailable; falling back to WineD3D."
                    )
                    GraphicsBackend.WINED3D
                } else if (!dxvkInstalled) {
                    diagnosticManager.warn(
                        LogCategory.DXVK,
                        "DXVK requested but DXVK d3d9.dll is not installed; falling back to WineD3D."
                    )
                    GraphicsBackend.WINED3D
                } else {
                    GraphicsBackend.DXVK
                }
            }
            GraphicsBackend.AUTOMATIC -> {
                if (gpuInfo.vulkanSupported && dxvkInstalled) {
                    GraphicsBackend.DXVK
                } else {
                    GraphicsBackend.WINED3D
                }
            }
        }
    }

    fun writeDxvkConfiguration(profile: GameProfile, effectiveBackend: GraphicsBackend): File {
        storageManager.ensureBaseDirectories()
        val dxvkDir = File(storageManager.runtimeDir, "dxvk")
        dxvkDir.mkdirs()
        val confFile = File(dxvkDir, "dxvk.conf")
        val content = buildString {
            appendLine("# Storm Launcher Generated DXVK Configuration")
            appendLine("# Optimized for Direct3D 9 (NSUNSR.exe) on ARM64 Mali-G57 MC2")
            appendLine("d3d9.maxFrameRate = ${profile.fpsLimit}")
            appendLine("dxgi.syncInterval = ${if (profile.vsyncEnabled) 1 else 0}")
            appendLine("d3d9.presentInterval = ${if (profile.vsyncEnabled) 1 else 0}")
            appendLine("d3d9.samplerAnisotropy = ${profile.textureFiltering.anisotropyLevel}")
            appendLine("dxvk.enableStateCache = ${if (profile.shaderCacheEnabled) "True" else "False"}")
            appendLine("dxvk.enableGraphicsPipelineLibrary = ${if (profile.pipelineCacheEnabled) "Auto" else "False"}")
            appendLine("d3d9.deferSurfaceCreation = True")
            appendLine("d3d9.lenientClear = True")
            appendLine("# Active backend resolved: ${effectiveBackend.name}")
        }
        confFile.writeText(content)
        diagnosticManager.info(LogCategory.GRAPHICS, "Updated dxvk.conf (FPS=${profile.fpsLimit}, VSync=${profile.vsyncEnabled}, Aniso=${profile.textureFiltering.anisotropyLevel}x)")
        return confFile
    }

    fun buildGraphicsEnvVars(
        profile: GameProfile,
        effectiveBackend: GraphicsBackend,
        gpuInfo: GpuCapabilityInfo
    ): Map<String, String> {
        val env = linkedMapOf<String, String>()
        env["DXVK_STATE_CACHE"] = if (profile.shaderCacheEnabled) "1" else "0"
        env["DXVK_STATE_CACHE_PATH"] = storageManager.shaderCacheDir.absolutePath
        env["DXVK_CONFIG_FILE"] = File(storageManager.runtimeDir, "dxvk/dxvk.conf").absolutePath
        env["DXVK_FRAME_RATE"] = profile.fpsLimit.toString()
        env["DXVK_HUD"] = if (profile.showFpsOverlay) "fps,frametimes,api" else "0"

        if (effectiveBackend == GraphicsBackend.DXVK) {
            env["WINEDLLOVERRIDES"] = "d3d9=n,b"
            if (gpuInfo.isMaliGpu) {
                // Use system Android/Mali Vulkan ICD, never Turnip/Freedreno
                env["MESA_VK_WSI_PRESENT_MODE"] = if (profile.vsyncEnabled) "fifo" else "immediate"
            }
        } else {
            // WineD3D OpenGL / GLES fallback
            env["WINEDLLOVERRIDES"] = "d3d9=b"
            env["WINE_D3D_CONFIG"] = "renderer=gl;csmt=on"
            env["MESA_GLSL_CACHE_DISABLE"] = if (profile.shaderCacheEnabled) "0" else "1"
            env["MESA_GLSL_CACHE_DIR"] = storageManager.shaderCacheDir.absolutePath
            env["vblank_mode"] = if (profile.vsyncEnabled) "1" else "0"
        }
        return env
    }

    fun clearShaderCache(): Long {
        val dir = storageManager.shaderCacheDir
        val freed = storageManager.computeDirectorySizeBytes(dir)
        dir.deleteRecursively()
        dir.mkdirs()
        diagnosticManager.info(LogCategory.GRAPHICS, "Cleared shader cache (${freed / 1024} KB freed).")
        return freed
    }

    private fun queryEglRendererInfo(): Triple<String, String, String> {
        var display: EGLDisplay? = null
        var contextEgl: EGLContext? = null
        var surface: EGLSurface? = null
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                return Triple("Unknown", "No EGL Display", "Unknown")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return Triple("Unknown", "EGL Init Failed", "Unknown")
            }
            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                return Triple("Unknown", "No EGL Config", "Unknown")
            }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            contextEgl = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
            if (contextEgl == null || contextEgl == EGL14.EGL_NO_CONTEXT || surface == null || surface == EGL14.EGL_NO_SURFACE) {
                return Triple("Unknown", "EGL Surface Error", "Unknown")
            }
            EGL14.eglMakeCurrent(display, surface, surface, contextEgl)
            val glVendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
            Triple(glVendor, glRenderer, glVersion)
        } catch (_: Throwable) {
            Triple("Unknown", "Headless / Robolectric", "OpenGL ES")
        } finally {
            runCatching {
                if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (contextEgl != null && contextEgl != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, contextEgl)
                    EGL14.eglTerminate(display)
                }
            }
        }
    }
}
