package com.stormrevolution.launcher.manager

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.stormrevolution.launcher.NativeBridge
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.GamepadState
import com.stormrevolution.launcher.model.InputResponseCurve
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.VirtualButtonId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

class ControllerManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val diagnosticManager: DiagnosticManager
) {
    companion object {
        private const val XINPUT_PORT = 28475
    }

    private val _gamepadState = MutableStateFlow(GamepadState())
    val gamepadState: StateFlow<GamepadState> = _gamepadState.asStateFlow()

    private val _lastDispatchStatus = MutableStateFlow("Idle")
    val lastDispatchStatus: StateFlow<String> = _lastDispatchStatus.asStateFlow()

    private var udpSocket: DatagramSocket? = null
    private val loopbackAddress: InetAddress by lazy { InetAddress.getByName("127.0.0.1") }

    fun getConnectedPhysicalGamepads(): List<String> {
        val inputMgr = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return emptyList()
        return inputMgr.inputDeviceIds.toList().mapNotNull { id ->
            val dev = InputDevice.getDevice(id) ?: return@mapNotNull null
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepad && !dev.isVirtual) dev.name else null
        }
    }

    fun setVirtualButtonPressed(
        buttonId: VirtualButtonId,
        pressed: Boolean,
        config: ControllerConfig
    ) {
        if (pressed && config.hapticFeedback) {
            triggerLightHaptic()
        }

        _gamepadState.update { cur ->
            var mask = cur.buttonsMask
            var lt = cur.leftTrigger
            var rt = cur.rightTrigger
            when (buttonId) {
                VirtualButtonId.BTN_LT -> lt = if (pressed) 255 else 0
                VirtualButtonId.BTN_RT -> rt = if (pressed) 255 else 0
                else -> {
                    mask = if (pressed) {
                        mask or buttonId.xinputMask
                    } else {
                        mask and buttonId.xinputMask.inv()
                    }
                }
            }
            cur.copy(buttonsMask = mask, leftTrigger = lt, rightTrigger = rt)
        }
        dispatchCurrentState(config)
    }

    fun setVirtualLeftStick(rawX: Float, rawY: Float, config: ControllerConfig) {
        val mag = hypot(rawX, rawY)
        val deadZone = config.analogDeadZone.coerceIn(0f, 0.45f)
        val (scaledX, scaledY) = if (mag <= deadZone) {
            0f to 0f
        } else {
            val normalizedMag = ((mag - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
            val curvedMag = when (config.responseCurve) {
                InputResponseCurve.LINEAR -> normalizedMag
                InputResponseCurve.COMPETITIVE -> normalizedMag.pow(0.82f)
                InputResponseCurve.SMOOTH -> normalizedMag.pow(1.35f)
            }
            val factor = (curvedMag * config.analogSensitivity).coerceIn(0f, 1f) / mag
            (rawX * factor).coerceIn(-1f, 1f) to (rawY * factor).coerceIn(-1f, 1f)
        }

        val lx = (scaledX * 32767f).toInt().coerceIn(-32768, 32767)
        // Windows XInput Y+ is Up, Android touch Y+ is Down -> invert Y
        val ly = (-scaledY * 32767f).toInt().coerceIn(-32768, 32767)

        _gamepadState.update { it.copy(thumbLX = lx, thumbLY = ly) }
        dispatchCurrentState(config)
    }

    fun handlePhysicalKeyEvent(event: KeyEvent, config: ControllerConfig): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false

        // Map standard Android gamepad & keyboard keys to Storm Revolution XInput
        val mappedButton = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE -> VirtualButtonId.BTN_A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_K -> VirtualButtonId.BTN_B
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_J -> VirtualButtonId.BTN_X
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_L -> VirtualButtonId.BTN_Y
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> VirtualButtonId.BTN_LB
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> VirtualButtonId.BTN_RB
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_U -> VirtualButtonId.BTN_LT
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_O -> VirtualButtonId.BTN_RT
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> VirtualButtonId.BTN_START
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_ESCAPE -> VirtualButtonId.BTN_BACK
            KeyEvent.KEYCODE_DPAD_UP -> VirtualButtonId.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> VirtualButtonId.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> VirtualButtonId.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> VirtualButtonId.DPAD_RIGHT
            else -> null
        }

        if (mappedButton != null) {
            setVirtualButtonPressed(mappedButton, isDown, config)
            return true
        }

        if (config.keyboardMappingEnabled) {
            val wasd = when (event.keyCode) {
                KeyEvent.KEYCODE_W -> 0f to -1f
                KeyEvent.KEYCODE_S -> 0f to 1f
                KeyEvent.KEYCODE_A -> -1f to 0f
                KeyEvent.KEYCODE_D -> 1f to 0f
                else -> null
            }
            if (wasd != null) {
                if (isDown) setVirtualLeftStick(wasd.first, wasd.second, config)
                else setVirtualLeftStick(0f, 0f, config)
                return true
            }
        }
        return false
    }

    fun handlePhysicalMotionEvent(event: MotionEvent, config: ControllerConfig): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return false
        }
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val cleanX = if (abs(x) < config.analogDeadZone) 0f else sign(x) * ((abs(x) - config.analogDeadZone) / (1f - config.analogDeadZone))
        val cleanY = if (abs(y) < config.analogDeadZone) 0f else sign(y) * ((abs(y) - config.analogDeadZone) / (1f - config.analogDeadZone))
        setVirtualLeftStick(cleanX.coerceIn(-1f, 1f), cleanY.coerceIn(-1f, 1f), config)
        return true
    }

    fun dispatchCurrentState(config: ControllerConfig): Boolean {
        if (!config.xinputBridgeEnabled) {
            _lastDispatchStatus.value = "XInput Bridge Disabled"
            return false
        }
        val state = _gamepadState.value
        val unixSent = NativeBridge.sendGamepadState(
            socketPath = storageManager.xinputSocketFile.absolutePath,
            buttons = state.buttonsMask,
            leftTrigger = state.leftTrigger,
            rightTrigger = state.rightTrigger,
            thumbLX = state.thumbLX,
            thumbLY = state.thumbLY,
            thumbRX = state.thumbRX,
            thumbRY = state.thumbRY
        )

        val udpSent = sendUdpPacket(state)
        _lastDispatchStatus.value = when {
            unixSent -> "Unix IPC + UDP Active (mask=0x${state.buttonsMask.toString(16)})"
            udpSent -> "Loopback UDP :$XINPUT_PORT (mask=0x${state.buttonsMask.toString(16)})"
            else -> "Prepared (waiting for Wine listener)"
        }
        return unixSent || udpSent
    }

    fun logControllerCalibration(config: ControllerConfig) {
        diagnosticManager.info(
            LogCategory.INPUT,
            "Controller '${config.profileName}': opacity=${(config.buttonOpacity * 100).toInt()}%, deadzone=${config.analogDeadZone}, sensitivity=${config.analogSensitivity}, curve=${config.responseCurve.name}"
        )
    }

    private fun sendUdpPacket(state: GamepadState): Boolean {
        return runCatching {
            val sock = udpSocket ?: DatagramSocket().also { udpSocket = it }
            val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x5354524D) // 'STRM'
                putShort((state.buttonsMask and 0xFFFF).toShort())
                put((state.leftTrigger and 0xFF).toByte())
                put((state.rightTrigger and 0xFF).toByte())
                putShort(state.thumbLX.toShort())
                putShort(state.thumbLY.toShort())
                putShort(state.thumbRX.toShort())
                putShort(state.thumbRY.toShort())
            }.array()
            sock.send(DatagramPacket(buf, buf.size, loopbackAddress, XINPUT_PORT))
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun triggerLightHaptic() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v?.vibrate(14)
                }
            }
        }
    }
}
