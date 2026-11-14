package com.stormrevolution.launcher.manager

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.SystemTelemetry
import java.io.File
import java.io.RandomAccessFile

class PerformanceMonitor(
    private val context: Context,
    private val storageManager: StorageManager,
    private val controllerManager: ControllerManager
) {
    private var prevIdleTime = 0L
    private var prevTotalTime = 0L

    fun collectTelemetry(): SystemTelemetry {
        val activityMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityMgr?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val appHeapMb = (Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().totalMemory()) / (1024L * 1024L)
        val freeStorageMb = storageManager.getAvailableInternalStorageBytes() / (1024L * 1024L)
        val shaderCacheMb = storageManager.computeDirectorySizeBytes(storageManager.shaderCacheDir) / (1024.0 * 1024.0)

        val nativeProbe = NativeBridge.probeEnvironment()
        val thermalLabel = readThermalLabel()
        val cpuLoad = readCpuUsagePercent()

        return SystemTelemetry(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            primaryAbi = Build.SUPPORTED_ABIS.joinToString(", "),
            cpuHardware = nativeProbe.cpuHardware,
            cpuCores = nativeProbe.cpuCores.coerceAtLeast(Runtime.getRuntime().availableProcessors()),
            cpuLoadPercent = cpuLoad,
            totalRamMb = if (totalRamMb > 0) totalRamMb else nativeProbe.totalRamMb,
            availableRamMb = if (availRamMb > 0) availRamMb else nativeProbe.freeRamMb,
            appHeapUsedMb = appHeapMb,
            internalStorageFreeMb = freeStorageMb,
            shaderCacheSizeMb = shaderCacheMb,
            thermalStatusLabel = thermalLabel,
            nativeBridgeLoaded = NativeBridge.isLoaded,
            nativeBridgeError = NativeBridge.loadError,
            connectedGamepads = controllerManager.getConnectedPhysicalGamepads()
        )
    }

    private fun readThermalLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "API < 29"
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return "Unknown"
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Emergency"
            else -> "Normal"
        }
    }

    private fun readCpuUsagePercent(): Int {
        return runCatching {
            val file = File("/proc/stat")
            if (!file.canRead()) return 0
            RandomAccessFile(file, "r").use { raf ->
                val line = raf.readLine() ?: return 0
                val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size < 4) return 0
                val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
                val total = parts.sum()
                val diffIdle = idle - prevIdleTime
                val diffTotal = total - prevTotalTime
                prevIdleTime = idle
                prevTotalTime = total
                if (diffTotal <= 0L) 0 else (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(0, 100)
            }
        }.getOrDefault(0)
    }
}
