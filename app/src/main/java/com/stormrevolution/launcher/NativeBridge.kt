package com.stormrevolution.launcher

import android.os.Build
import android.system.Os
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

data class NativeEnvProbe(
    val abi: String,
    val kernel: String,
    val cpuHardware: String,
    val cpuCores: Int,
    val pageSize: Long,
    val totalRamMb: Long,
    val freeRamMb: Long,
    val vulkanLoaderFound: Boolean,
    val vulkanVersion: String
)

data class ElfBinaryInfo(
    val valid: Boolean,
    val bitness: Int,
    val machine: Int,
    val executable: Boolean
)

/**
 * JNI interface to libstorm_runtime_bridge.so (built with Android NDK + CMake).
 * Gracefully detects if the native .so is absent (e.g. host builds without NDK) and provides
 * deterministic POSIX / JVM fallbacks without fabricating runtime success.
 */
object NativeBridge {

    val isLoaded: Boolean
    val loadError: String?

    init {
        var loaded = false
        var err: String? = null
        try {
            System.loadLibrary("storm_runtime_bridge")
            loaded = true
        } catch (t: UnsatisfiedLinkError) {
            err = t.message ?: "libstorm_runtime_bridge.so not packaged in this APK build"
        } catch (t: Throwable) {
            err = t.message
        }
        isLoaded = loaded
        loadError = err
    }

    private external fun nativeProbeEnvironment(): String
    private external fun nativeInspectElfBinary(path: String): String
    private external fun nativeChmodExecutable(path: String): Boolean
    private external fun nativeSpawnProcess(
        executable: String,
        args: Array<String>,
        envPairs: Array<String>,
        workingDir: String
    ): IntArray?

    private external fun nativeReadFdNonBlocking(fd: Int): String
    private external fun nativeCloseFd(fd: Int)
    private external fun nativePollWaitPid(pid: Int): Int
    private external fun nativeTerminateProcess(pid: Int, forceKill: Boolean): Boolean
    private external fun nativeSendGamepadState(
        socketPath: String,
        buttons: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        thumbLX: Int,
        thumbLY: Int,
        thumbRX: Int,
        thumbRY: Int
    ): Boolean

    fun probeEnvironment(): NativeEnvProbe {
        if (isLoaded) {
            runCatching {
                val json = JSONObject(nativeProbeEnvironment())
                return NativeEnvProbe(
                    abi = json.optString("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"),
                    kernel = json.optString("kernel", System.getProperty("os.version") ?: "unknown"),
                    cpuHardware = json.optString("cpuHardware", Build.HARDWARE),
                    cpuCores = json.optInt("cpuCores", Runtime.getRuntime().availableProcessors()),
                    pageSize = json.optLong("pageSize", 4096L),
                    totalRamMb = json.optLong("totalRamMb", 0L),
                    freeRamMb = json.optLong("freeRamMb", 0L),
                    vulkanLoaderFound = json.optBoolean("vulkanLoaderFound", false),
                    vulkanVersion = json.optString("vulkanVersion", "0.0.0")
                )
            }
        }
        val vkFile = File("/system/lib64/libvulkan.so")
        return NativeEnvProbe(
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            kernel = System.getProperty("os.version") ?: "unknown",
            cpuHardware = "${Build.HARDWARE} (${Build.SOC_MODEL})",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            pageSize = 4096L,
            totalRamMb = 0L,
            freeRamMb = 0L,
            vulkanLoaderFound = vkFile.exists(),
            vulkanVersion = if (vkFile.exists()) "1.1+" else "0.0.0"
        )
    }

    fun inspectElfBinary(file: File): ElfBinaryInfo {
        if (!file.exists() || !file.isFile) {
            return ElfBinaryInfo(valid = false, bitness = 0, machine = 0, executable = false)
        }
        if (isLoaded) {
            runCatching {
                val json = JSONObject(nativeInspectElfBinary(file.absolutePath))
                return ElfBinaryInfo(
                    valid = json.optBoolean("valid", false),
                    bitness = json.optInt("bitness", 0),
                    machine = json.optInt("machine", 0),
                    executable = json.optBoolean("executable", false)
                )
            }
        }
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 20) {
                    return ElfBinaryInfo(false, 0, 0, file.canExecute())
                }
                val hdr = ByteArray(20)
                raf.readFully(hdr)
                val isElf = hdr[0] == 0x7F.toByte() &&
                    hdr[1] == 'E'.code.toByte() &&
                    hdr[2] == 'L'.code.toByte() &&
                    hdr[3] == 'F'.code.toByte()
                if (!isElf) {
                    return ElfBinaryInfo(false, 0, 0, file.canExecute())
                }
                val bitness = when (hdr[4].toInt()) {
                    1 -> 32
                    2 -> 64
                    else -> 0
                }
                val machine = (hdr[18].toInt() and 0xFF) or ((hdr[19].toInt() and 0xFF) shl 8)
                ElfBinaryInfo(true, bitness, machine, file.canExecute())
            }
        }.getOrElse {
            ElfBinaryInfo(false, 0, 0, false)
        }
    }

    fun makeExecutable(file: File): Boolean {
        if (!file.exists()) return false
        if (isLoaded) {
            if (nativeChmodExecutable(file.absolutePath)) return true
        }
        return runCatching {
            Os.chmod(file.absolutePath, 493) // 0755 octal
            true
        }.getOrElse {
            file.setExecutable(true, false)
        }
    }

    fun spawnNativeProcess(
        executable: String,
        args: Array<String>,
        envPairs: Array<String>,
        workingDir: String
    ): IntArray? {
        if (!isLoaded) return null
        return nativeSpawnProcess(executable, args, envPairs, workingDir)
    }

    fun readFdNonBlocking(fd: Int): String =
        if (isLoaded) nativeReadFdNonBlocking(fd) else ""

    fun closeFd(fd: Int) {
        if (isLoaded) nativeCloseFd(fd)
    }

    fun pollWaitPid(pid: Int): Int =
        if (isLoaded) nativePollWaitPid(pid) else -1

    fun terminateNativeProcess(pid: Int, forceKill: Boolean): Boolean =
        if (isLoaded) nativeTerminateProcess(pid, forceKill) else false

    fun sendGamepadState(
        socketPath: String,
        buttons: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        thumbLX: Int,
        thumbLY: Int,
        thumbRX: Int,
        thumbRY: Int
    ): Boolean {
        if (!isLoaded) return false
        return nativeSendGamepadState(
            socketPath, buttons, leftTrigger, rightTrigger, thumbLX, thumbLY, thumbRX, thumbRY
        )
    }
}
