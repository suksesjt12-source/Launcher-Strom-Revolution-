package com.stormrevolution.launcher

/**
 * Loads the native Vulkan bootstrap module and reports GPU availability.
 *
 * This is infrastructure diagnostics only. It is not a Windows EXE loader
 * and does not yet contain the game-specific ARM64 recompilation or renderer.
 */
object StormEngine {
    private var libraryLoaded = false

    private external fun nativeGetVulkanReport(): String

    fun getVulkanReport(): String {
        return try {
            if (!libraryLoaded) {
                System.loadLibrary("storm_engine")
                libraryLoaded = true
            }
            nativeGetVulkanReport()
        } catch (error: UnsatisfiedLinkError) {
            "Modul native gagal dimuat: ${error.message ?: "library tidak ditemukan"}"
        } catch (error: Exception) {
            "Pemeriksaan Vulkan gagal: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}
