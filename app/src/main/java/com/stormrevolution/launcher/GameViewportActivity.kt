package com.stormrevolution.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StormAmber
import com.example.ui.theme.StormCardBorder
import com.example.ui.theme.StormCyan
import com.example.ui.theme.StormError
import com.example.ui.theme.StormLauncherTheme
import com.example.ui.theme.StormObsidian
import com.example.ui.theme.StormSuccess
import com.example.ui.theme.StormSurface
import com.example.ui.theme.StormTextSecondary
import com.stormrevolution.launcher.model.AspectRatioMode
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.VirtualButtonId
import com.stormrevolution.launcher.model.VirtualButtonLayout
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

class GameViewportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LAYOUT_EDITOR = "extra_layout_editor"
    }

    private lateinit var container: StormContainer
    private var currentConfig: ControllerConfig = ControllerConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as StormApplication).container
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val initialEditMode = intent.getBooleanExtra(EXTRA_LAYOUT_EDITOR, false)

        setContent {
            StormLauncherTheme {
                val controllerCfg by container.settingsManager.controllerConfigFlow
                    .collectAsStateWithLifecycle(initialValue = ControllerConfig())
                val profile by container.settingsManager.gameProfileFlow
                    .collectAsStateWithLifecycle(initialValue = com.stormrevolution.launcher.model.GameProfile())
                val procState by container.processManager.executionState.collectAsStateWithLifecycle()
                val gamepadState by container.controllerManager.gamepadState.collectAsStateWithLifecycle()
                val dispatchStatus by container.controllerManager.lastDispatchStatus.collectAsStateWithLifecycle()

                currentConfig = controllerCfg
                val scope = rememberCoroutineScope()
                var isEditMode by remember { mutableStateOf(initialEditMode) }
                var overlayVisible by remember(controllerCfg.overlayEnabled) {
                    mutableStateOf(controllerCfg.overlayEnabled || initialEditMode)
                }

                GameViewportScreen(
                    isEditMode = isEditMode,
                    overlayVisible = overlayVisible,
                    profile = profile,
                    controllerConfig = controllerCfg,
                    processState = procState,
                    gamepadButtonsMask = gamepadState.buttonsMask,
                    thumbLX = gamepadState.thumbLX,
                    thumbLY = gamepadState.thumbLY,
                    dispatchStatus = dispatchStatus,
                    onToggleEditMode = { isEditMode = !isEditMode },
                    onToggleOverlay = { overlayVisible = !overlayVisible },
                    onButtonPressChange = { id, pressed ->
                        if (!isEditMode) {
                            container.controllerManager.setVirtualButtonPressed(id, pressed, controllerCfg)
                        }
                    },
                    onAnalogChange = { dx, dy ->
                        if (!isEditMode) {
                            container.controllerManager.setVirtualLeftStick(dx, dy, controllerCfg)
                        }
                    },
                    onMoveButton = { id, normX, normY ->
                        scope.launch {
                            val updated = controllerCfg.copy(
                                layouts = controllerCfg.layouts.map {
                                    if (it.id == id) it.copy(
                                        normalizedX = normX.coerceIn(0.05f, 0.95f),
                                        normalizedY = normY.coerceIn(0.08f, 0.92f)
                                    ) else it
                                }
                            )
                            container.settingsManager.saveControllerConfig(updated)
                        }
                    },
                    onMoveAnalog = { normX, normY ->
                        scope.launch {
                            container.settingsManager.saveControllerConfig(
                                controllerCfg.copy(
                                    analogNormalizedX = normX.coerceIn(0.08f, 0.90f),
                                    analogNormalizedY = normY.coerceIn(0.15f, 0.88f)
                                )
                            )
                        }
                    },
                    onExitViewport = {
                        if (!initialEditMode) {
                            container.processManager.stopActiveProcess(force = false)
                        }
                        finish()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (container.controllerManager.handlePhysicalKeyEvent(event, currentConfig)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (container.controllerManager.handlePhysicalMotionEvent(ev, currentConfig)) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun GameViewportScreen(
    isEditMode: Boolean,
    overlayVisible: Boolean,
    profile: com.stormrevolution.launcher.model.GameProfile,
    controllerConfig: ControllerConfig,
    processState: com.stormrevolution.launcher.model.ProcessExecutionState,
    gamepadButtonsMask: Int,
    thumbLX: Int,
    thumbLY: Int,
    dispatchStatus: String,
    onToggleEditMode: () -> Unit,
    onToggleOverlay: () -> Unit,
    onButtonPressChange: (VirtualButtonId, Boolean) -> Unit,
    onAnalogChange: (Float, Float) -> Unit,
    onMoveButton: (VirtualButtonId, Float, Float) -> Unit,
    onMoveAnalog: (Float, Float) -> Unit,
    onExitViewport: () -> Unit
) {
    var measuredFps by remember { mutableIntStateOf(0) }
    var frameTimeMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNs = 0L
        var frames = 0
        var accumNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val delta = now - lastNs
                    accumNs += delta
                    frames++
                    if (accumNs >= 500_000_000L) {
                        measuredFps = ((frames * 1_000_000_000.0) / accumNs).roundToInt()
                        frameTimeMs = (accumNs / frames.toFloat()) / 1_000_000f
                        frames = 0
                        accumNs = 0L
                    }
                }
                lastNs = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Aspect-ratio managed display surface (no stretching or clipping unless user picks Fit Screen)
        val surfaceModifier = when (profile.aspectRatioMode) {
            AspectRatioMode.FIT_SCREEN -> Modifier.fillMaxSize()
            else -> Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
        }

        Box(
            modifier = surfaceModifier
                .background(Color(0xFF060A11))
                .border(1.dp, StormCardBorder),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = if (isEditMode) "VIRTUAL CONTROLLER LAYOUT EDITOR" else "STORM REVOLUTION RUNTIME SURFACE",
                    style = MaterialTheme.typography.titleLarge,
                    color = StormAmber
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${profile.resolutionWidth}x${profile.resolutionHeight} • Cap ${profile.fpsLimit} FPS • ${profile.aspectRatioMode.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StormCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (isEditMode) {
                    Text(
                        text = "Drag any virtual button or the analog stick to reposition it for your grip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StormTextSecondary
                    )
                } else {
                    Text(
                        text = "Process: ${if (processState.isRunning) "RUNNING (PID ${processState.pid ?: "?"})" else "STOPPED (Exit ${processState.lastExitCode ?: "-"})"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (processState.isRunning) StormSuccess else StormError
                    )
                    Text(
                        text = "Route: ${processState.activeTranslationPath} | GPU: ${processState.activeGraphicsPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StormTextSecondary
                    )
                    processState.crashReason?.let { crash ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Error: $crash",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StormError
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "XInput IPC: $dispatchStatus | LX=$thumbLX LY=$thumbLY",
                        style = MaterialTheme.typography.labelSmall,
                        color = StormTextSecondary
                    )
                }
            }
        }

        // Top HUD Bar: FPS / Frame Time + Quick Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile.showFpsOverlay) {
                Surface(
                    color = StormSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StormCardBorder)
                ) {
                    Text(
                        text = "UI ${measuredFps} FPS (${"%.1f".format(frameTimeMs)}ms) | Mask 0x${gamepadButtonsMask.toString(16)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = StormCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleOverlay,
                    modifier = Modifier
                        .size(48.dp)
                        .background(StormSurface.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (overlayVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle touch overlay",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onToggleEditMode,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isEditMode) StormAmber else StormSurface.copy(alpha = 0.8f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenWith,
                        contentDescription = "Toggle layout editor",
                        tint = if (isEditMode) StormObsidian else Color.White
                    )
                }
                Button(
                    onClick = onExitViewport,
                    colors = ButtonDefaults.buttonColors(containerColor = StormError),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_exit_viewport")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Exit")
                }
            }
        }

        // Virtual Gamepad Overlay
        if (overlayVisible) {
            VirtualGamepadLayer(
                isEditMode = isEditMode,
                config = controllerConfig,
                onButtonPressChange = onButtonPressChange,
                onAnalogChange = onAnalogChange,
                onMoveButton = onMoveButton,
                onMoveAnalog = onMoveAnalog
            )
        }
    }
}

@Composable
private fun VirtualGamepadLayer(
    isEditMode: Boolean,
    config: ControllerConfig,
    onButtonPressChange: (VirtualButtonId, Boolean) -> Unit,
    onAnalogChange: (Float, Float) -> Unit,
    onMoveButton: (VirtualButtonId, Float, Float) -> Unit,
    onMoveAnalog: (Float, Float) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Virtual Left Analog Stick
        VirtualAnalogStick(
            normX = config.analogNormalizedX,
            normY = config.analogNormalizedY,
            containerWidthPx = widthPx,
            containerHeightPx = heightPx,
            opacity = config.buttonOpacity,
            sizeScale = config.buttonSizeScale,
            isEditMode = isEditMode,
            onAnalogChange = onAnalogChange,
            onReposition = onMoveAnalog
        )

        // All Virtual Buttons
        config.layouts.forEach { layout ->
            VirtualActionButton(
                layout = layout,
                containerWidthPx = widthPx,
                containerHeightPx = heightPx,
                opacity = config.buttonOpacity,
                globalScale = config.buttonSizeScale,
                isEditMode = isEditMode,
                onPressChange = { pressed -> onButtonPressChange(layout.id, pressed) },
                onReposition = { nx, ny -> onMoveButton(layout.id, nx, ny) }
            )
        }
    }
}

@Composable
private fun VirtualAnalogStick(
    normX: Float,
    normY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    opacity: Float,
    sizeScale: Float,
    isEditMode: Boolean,
    onAnalogChange: (Float, Float) -> Unit,
    onReposition: (Float, Float) -> Unit
) {
    val baseDiameter = (118f * sizeScale).dp
    val density = LocalDensity.current
    val radiusPx = with(density) { (baseDiameter / 2).toPx() }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPosX by remember(normX) { mutableFloatStateOf(normX * containerWidthPx) }
    var dragPosY by remember(normY) { mutableFloatStateOf(normY * containerHeightPx) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragPosX - radiusPx).roundToInt(),
                    (dragPosY - radiusPx).roundToInt()
                )
            }
            .size(baseDiameter)
            .alpha(opacity)
            .clip(CircleShape)
            .background(StormSurface.copy(alpha = 0.55f))
            .border(2.dp, if (isEditMode) StormAmber else StormCyan.copy(alpha = 0.7f), CircleShape)
            .pointerInput(isEditMode, containerWidthPx, containerHeightPx) {
                if (isEditMode) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragPosX = (dragPosX + dragAmount.x).coerceIn(radiusPx, containerWidthPx - radiusPx)
                            dragPosY = (dragPosY + dragAmount.y).coerceIn(radiusPx, containerHeightPx - radiusPx)
                        },
                        onDragEnd = {
                            onReposition(dragPosX / containerWidthPx, dragPosY / containerHeightPx)
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val rel = Offset(offset.x - radiusPx, offset.y - radiusPx)
                            val maxDist = radiusPx * 0.78f
                            val dist = hypot(rel.x, rel.y)
                            val clamped = if (dist > maxDist) rel * (maxDist / dist) else rel
                            knobOffset = clamped
                            onAnalogChange((clamped.x / maxDist).coerceIn(-1f, 1f), (clamped.y / maxDist).coerceIn(-1f, 1f))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val next = knobOffset + dragAmount
                            val maxDist = radiusPx * 0.78f
                            val dist = hypot(next.x, next.y)
                            val clamped = if (dist > maxDist) next * (maxDist / dist) else next
                            knobOffset = clamped
                            onAnalogChange((clamped.x / maxDist).coerceIn(-1f, 1f), (clamped.y / maxDist).coerceIn(-1f, 1f))
                        },
                        onDragEnd = {
                            knobOffset = Offset.Zero
                            onAnalogChange(0f, 0f)
                        },
                        onDragCancel = {
                            knobOffset = Offset.Zero
                            onAnalogChange(0f, 0f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = StormCyan.copy(alpha = 0.85f),
                radius = size.minDimension * 0.22f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
private fun VirtualActionButton(
    layout: VirtualButtonLayout,
    containerWidthPx: Float,
    containerHeightPx: Float,
    opacity: Float,
    globalScale: Float,
    isEditMode: Boolean,
    onPressChange: (Boolean) -> Unit,
    onReposition: (Float, Float) -> Unit
) {
    val sizeDp = (54f * globalScale * layout.scale).coerceAtLeast(48f).dp
    val density = LocalDensity.current
    val halfPx = with(density) { (sizeDp / 2).toPx() }
    var posX by remember(layout.normalizedX) { mutableFloatStateOf(layout.normalizedX * containerWidthPx) }
    var posY by remember(layout.normalizedY) { mutableFloatStateOf(layout.normalizedY * containerHeightPx) }
    var pressed by remember { mutableStateOf(false) }

    val accent = when (layout.id) {
        VirtualButtonId.BTN_B -> StormAmber
        VirtualButtonId.BTN_Y -> StormCyan
        VirtualButtonId.BTN_A -> StormSuccess
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset((posX - halfPx).roundToInt(), (posY - halfPx).roundToInt())
            }
            .size(sizeDp)
            .alpha(opacity)
            .clip(CircleShape)
            .background(
                if (pressed) accent.copy(alpha = 0.45f) else StormSurface.copy(alpha = 0.65f)
            )
            .border(
                width = if (isEditMode) 2.dp else 1.5.dp,
                color = if (isEditMode) StormAmber else accent.copy(alpha = 0.75f),
                shape = CircleShape
            )
            .pointerInput(isEditMode, containerWidthPx, containerHeightPx) {
                if (isEditMode) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(halfPx, containerWidthPx - halfPx)
                            posY = (posY + dragAmount.y).coerceIn(halfPx, containerHeightPx - halfPx)
                        },
                        onDragEnd = {
                            onReposition(posX / containerWidthPx, posY / containerHeightPx)
                        }
                    )
                } else {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.any { it.pressed }
                            if (down != pressed) {
                                pressed = down
                                onPressChange(down)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = layout.id.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}
