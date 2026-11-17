package com.stormrevolution.launcher

import android.view.Surface

/**
 * JNI bridge for the native Android Vulkan subsystem.
 * The renderer currently presents a diagnostic clear-color frame only.
 */
object StormEngine {
    private var libraryLoaded = false

    private external fun nativeGetVulkanReport(): String
    private external fun nativeStartRenderer(surface: Surface)
    private external fun nativeStopRenderer()

    @Synchronized
    private fun ensureLibraryLoaded(): Boolean {
        return try {
            if (!libraryLoaded) {
                System.loadLibrary("storm_engine")
                libraryLoaded = true
            }
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun getVulkanReport(): String {
        return if (!ensureLibraryLoaded()) {
            "Modul native gagal dimuat."
        } else {
            try {
                nativeGetVulkanReport()
            } catch (error: Exception) {
                "Pemeriksaan Vulkan gagal: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    @Synchronized
    fun startRenderer(surface: Surface) {
        if (!surface.isValid || !ensureLibraryLoaded()) return
        try {
            nativeStartRenderer(surface)
        } catch (_: Exception) {
            // Keep the launcher alive if the device driver rejects the surface.
        }
    }

    @Synchronized
    fun stopRenderer() {
        if (!libraryLoaded) return
        try {
            nativeStopRenderer()
        } catch (_: Exception) {
            // Cleanup is best-effort during Android surface lifecycle changes.
        }
    }
}
