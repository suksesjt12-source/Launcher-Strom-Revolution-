package com.stormrevolution.launcher

import android.view.Surface

/** JNI access to the native Storm Engine core and Android Vulkan backend. */
object StormEngine {
    private var libraryLoaded = false

    private external fun nativeGetVulkanReport(): String
    private external fun nativeGetEngineReport(): String
    private external fun nativeStartRenderer(surface: Surface)
    private external fun nativeStopRenderer()
    private external fun nativeSendTouch(pointerId: Int, x: Float, y: Float, action: Int)
    private external fun nativeSetAssetRoot(path: String)
    private external fun nativeLoadAsset(relativePath: String): Boolean

    @Synchronized
    private fun ensureLibraryLoaded(): Boolean = try {
        if (!libraryLoaded) {
            System.loadLibrary("storm_engine")
            libraryLoaded = true
        }
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun setAssetRoot(path: String) {
        if (ensureLibraryLoaded()) {
            try { nativeSetAssetRoot(path) } catch (_: Exception) { }
        }
    }

    fun loadAsset(relativePath: String): Boolean {
        if (!ensureLibraryLoaded()) return false
        return try { nativeLoadAsset(relativePath) } catch (_: Exception) { false }
    }

    fun getVulkanReport(): String =
        if (!ensureLibraryLoaded()) "Modul native gagal dimuat."
        else try { nativeGetVulkanReport() } catch (e: Exception) { "Vulkan: ${e.message}" }

    fun getEngineReport(): String =
        if (!ensureLibraryLoaded()) "Engine native gagal dimuat."
        else try { nativeGetEngineReport() } catch (e: Exception) { "Engine: ${e.message}" }

    @Synchronized
    fun startRenderer(surface: Surface) {
        if (surface.isValid && ensureLibraryLoaded()) {
            try { nativeStartRenderer(surface) } catch (_: Exception) { }
        }
    }

    @Synchronized
    fun stopRenderer() {
        if (libraryLoaded) {
            try { nativeStopRenderer() } catch (_: Exception) { }
        }
    }

    fun sendTouch(pointerId: Int, x: Float, y: Float, action: Int) {
        if (ensureLibraryLoaded()) {
            try {
                nativeSendTouch(pointerId, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), action)
            } catch (_: Exception) { }
        }
    }
}
