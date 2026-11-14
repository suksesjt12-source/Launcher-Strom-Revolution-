package com.stormrevolution.launcher.manager

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager as AndroidAudioManager
import android.os.Build
import com.stormrevolution.launcher.model.AudioBackend
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.LogCategory
import java.io.File

data class AudioHardwareProbe(
    val sampleRateHz: Int,
    val framesPerBuffer: Int,
    val estimatedLatencyMs: Int,
    val outputDeviceNames: List<String>,
    val pulseBinaryPresent: Boolean
)

class AudioManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val diagnosticManager: DiagnosticManager
) {
    fun probeAudioHardware(): AudioHardwareProbe {
        val sysAudio = context.getSystemService(Context.AUDIO_SERVICE) as? AndroidAudioManager
        val rate = sysAudio?.getProperty(AndroidAudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val frames = sysAudio?.getProperty(AndroidAudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val baseLatencyMs = ((frames.toFloat() / rate.toFloat()) * 1000f).toInt().coerceAtLeast(8)

        val devices = if (sysAudio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("UNNECESSARY_SAFE_CALL")
            sysAudio.getDevices(AndroidAudioManager.GET_DEVICES_OUTPUTS)
                ?.map { "${it.productName} (${describeDeviceType(it.type)})" }
                ?.distinct()
                ?: emptyList()
        } else {
            listOf("Android Primary Audio Output")
        }

        val pulseBinary = File(storageManager.runtimeDir, "audio/bin/pulseaudio").exists() ||
            File(storageManager.runtimeDir, "rootfs/usr/bin/pulseaudio").exists()

        return AudioHardwareProbe(
            sampleRateHz = rate,
            framesPerBuffer = frames,
            estimatedLatencyMs = baseLatencyMs,
            outputDeviceNames = devices.ifEmpty { listOf("Default Speaker") },
            pulseBinaryPresent = pulseBinary
        )
    }

    fun writeAudioConfiguration(profile: GameProfile): Map<String, String> {
        storageManager.ensureBaseDirectories()
        val audioEtc = File(storageManager.runtimeDir, "audio/etc")
        audioEtc.mkdirs()

        // Enforce >= 48ms fragment/buffer latency to prevent audio crackling under CPU translation load
        val safeBufferMs = profile.audioBufferMs.coerceIn(48, 200)
        val probe = probeAudioHardware()
        val env = linkedMapOf<String, String>()

        when (profile.audioBackend) {
            AudioBackend.PULSE_AUDIO -> {
                val pulseSocket = File(storageManager.ipcDir, "pulse.sock").absolutePath
                val daemonConf = File(audioEtc, "daemon.conf")
                daemonConf.writeText(
                    """
                    # Storm Launcher PulseAudio Config (Anti-Underrun)
                    default-sample-format = s16le
                    default-sample-rate = ${probe.sampleRateHz}
                    default-fragments = 4
                    default-fragment-size-msec = ${safeBufferMs / 4}
                    exit-idle-time = -1
                    """.trimIndent() + "\n"
                )
                val defaultPa = File(audioEtc, "default.pa")
                defaultPa.writeText(
                    """
                    load-module module-native-protocol-unix socket=$pulseSocket auth-anonymous=1
                    load-module module-sles-sink
                    """.trimIndent() + "\n"
                )
                env["PULSE_SERVER"] = "unix:$pulseSocket"
                env["PULSE_LATENCY_MSEC"] = safeBufferMs.toString()
                env["STORM_AUDIO_VOLUME"] = profile.audioVolumePercent.toString()
                diagnosticManager.info(
                    LogCategory.AUDIO,
                    "Configured PulseAudio (${probe.sampleRateHz} Hz, ${safeBufferMs}ms anti-underrun buffer)."
                )
            }
            AudioBackend.ALSA -> {
                val asound = File(audioEtc, "asound.conf")
                asound.writeText(
                    """
                    pcm.!default {
                        type plug
                        slave.pcm "dmixer"
                    }
                    pcm.dmixer {
                        type dmix
                        ipc_key 1024
                        slave {
                            pcm "hw:0,0"
                            rate ${probe.sampleRateHz}
                            period_time 0
                            period_size 2048
                            buffer_size 8192
                        }
                    }
                    """.trimIndent() + "\n"
                )
                env["ALSA_CONFIG_PATH"] = asound.absolutePath
                diagnosticManager.info(
                    LogCategory.AUDIO,
                    "Configured ALSA fallback (period=2048, buffer=8192 @ ${probe.sampleRateHz} Hz)."
                )
            }
            AudioBackend.DISABLED -> {
                env["AUDIODEV"] = "null"
                diagnosticManager.info(LogCategory.AUDIO, "Audio output disabled by profile.")
            }
        }
        return env
    }

    private fun describeDeviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
        else -> "Audio Out"
    }
}
