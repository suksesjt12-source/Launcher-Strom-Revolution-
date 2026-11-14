package com.stormrevolution.launcher

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.stormrevolution.launcher.manager.AudioManager
import com.stormrevolution.launcher.manager.ControllerManager
import com.stormrevolution.launcher.manager.DiagnosticManager
import com.stormrevolution.launcher.manager.GraphicsManager
import com.stormrevolution.launcher.manager.PeInspector
import com.stormrevolution.launcher.manager.PerformanceMonitor
import com.stormrevolution.launcher.manager.ProcessManager
import com.stormrevolution.launcher.manager.RuntimeManager
import com.stormrevolution.launcher.manager.SettingsManager
import com.stormrevolution.launcher.manager.StorageManager
import com.stormrevolution.launcher.manager.WineManager
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.GamePreset
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GameValidationResult
import com.stormrevolution.launcher.model.GpuCapabilityInfo
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.InstallStatus
import com.stormrevolution.launcher.model.PeArchitecture
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.model.VirtualButtonId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeAndRuntimeVerificationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun buildMinimalPeHeader(machine: Int, optMagic: Int, subsystem: Int): ByteArray {
        val bytes = ByteArray(256)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, 0x80)            // e_lfanew = 128
        buf.putInt(0x80, 0x00004550)      // PE\0\0
        val coff = 0x84
        buf.putShort(coff, machine.toShort())
        buf.putShort(coff + 2, 5.toShort())   // 5 sections
        buf.putShort(coff + 16, 0x60.toShort()) // SizeOfOptionalHeader
        buf.putShort(coff + 18, 0x0122.toShort()) // LAA + Executable
        val opt = coff + 20
        buf.putShort(opt, optMagic.toShort())
        buf.putInt(opt + 16, 0x1000)
        buf.putShort(opt + 68, subsystem.toShort())
        return bytes
    }

    private fun buildMinimalElf64Aarch64(): ByteArray {
        val bytes = ByteArray(64)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2 // ELF64
        bytes[5] = 1 // Little endian
        bytes[18] = 183.toByte() // EM_AARCH64 = 0xB7
        bytes[19] = 0
        return bytes
    }

    @Test
    fun `TEST 10 - PE inspector detects 32-bit x86 NSUNSR binary and rejects invalid stubs`() {
        val pe32 = buildMinimalPeHeader(machine = 0x014C, optMagic = 0x010B, subsystem = 2)
        val res32 = PeInspector.inspectBytes(pe32)
        assertTrue(res32.isValidPe)
        assertEquals(PeArchitecture.X86_32, res32.architecture)
        assertEquals(5, res32.numberOfSections)
        assertTrue(res32.isLargeAddressAware)

        val pe64 = buildMinimalPeHeader(machine = 0x8664, optMagic = 0x020B, subsystem = 2)
        val res64 = PeInspector.inspectBytes(pe64)
        assertTrue(res64.isValidPe)
        assertEquals(PeArchitecture.X86_64, res64.architecture)

        val invalid = PeInspector.inspectBytes("<html>not an exe</html>".toByteArray())
        assertFalse(invalid.isValidPe)
        assertNotNull(invalid.errorReason)
    }

    @Test
    fun `TEST 6 and 7 - RuntimeManager does not fake installed state and verifies real ELF binaries`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storage = StorageManager(app)
        storage.runtimeDir.deleteRecursively()
        storage.ensureBaseDirectories()
        val diag = DiagnosticManager(app, storage)
        val runtimeMgr = RuntimeManager(app, storage, diag)
        val gpuMali = GpuCapabilityInfo(
            glVendor = "ARM",
            glRenderer = "Mali-G57 MC2",
            vulkanSupported = true,
            vulkanVersion = "1.1.0",
            isMaliGpu = true,
            turnipBlockedReason = "Mali GPU detected: Turnip blocked",
            recommendedBackend = GraphicsBackend.DXVK
        )

        val emptyStates = runtimeMgr.refreshComponentStates(gpuMali)
        assertEquals(InstallStatus.NOT_INSTALLED, emptyStates.first { it.type == RuntimeComponentType.WINE }.installStatus)
        assertEquals(InstallStatus.NOT_INSTALLED, emptyStates.first { it.type == RuntimeComponentType.BOX64 }.installStatus)
        assertFalse(runtimeMgr.isCoreRuntimeReady())

        // Route check must block 32-bit x86 without Wine + Box64 + WoW64
        val blockedRoute = runtimeMgr.resolveCpuTranslationRoute(PeArchitecture.X86_32)
        assertFalse(blockedRoute.isExecutableReady)

        // Stage real ELF64 AArch64 Box64, Wine, WoW64, and RootFS binaries on disk
        val box64Bin = File(storage.runtimeDir, "box64/bin/box64").apply {
            parentFile?.mkdirs()
            writeBytes(buildMinimalElf64Aarch64())
            setExecutable(true)
        }
        assertTrue(box64Bin.exists())
        File(storage.runtimeDir, "wine/bin/wine64").apply {
            parentFile?.mkdirs()
            writeBytes(buildMinimalElf64Aarch64())
            setExecutable(true)
        }
        File(storage.runtimeDir, "wow64/i386-windows/kernel32.dll").apply {
            parentFile?.mkdirs()
            writeBytes(buildMinimalPeHeader(0x014C, 0x010B, 2))
        }
        File(storage.runtimeDir, "wow64/x86_64-unix/ntdll.so").apply {
            parentFile?.mkdirs()
            writeBytes(buildMinimalElf64Aarch64())
        }
        File(storage.runtimeDir, "rootfs/lib/aarch64-linux-gnu/libc.so.6").apply {
            parentFile?.mkdirs()
            writeBytes(buildMinimalElf64Aarch64())
        }

        val readyStates = runtimeMgr.refreshComponentStates(gpuMali)
        assertEquals(InstallStatus.VERIFIED, readyStates.first { it.type == RuntimeComponentType.BOX64 }.installStatus)
        assertEquals(InstallStatus.VERIFIED, readyStates.first { it.type == RuntimeComponentType.WINE }.installStatus)
        assertEquals(InstallStatus.VERIFIED, readyStates.first { it.type == RuntimeComponentType.WOW64 }.installStatus)
        assertTrue(runtimeMgr.isCoreRuntimeReady())

        val validRoute = runtimeMgr.resolveCpuTranslationRoute(PeArchitecture.X86_32)
        assertTrue(validRoute.isExecutableReady)
        assertTrue(validRoute.usesWow64)
        assertTrue(validRoute.usesBox64)
    }

    @Test
    fun `TEST 8 and 9 - WineManager creates isolated prefix and GraphicsManager enforces Mali fallback`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storage = StorageManager(app)
        storage.prefixDir.deleteRecursively()
        val diag = DiagnosticManager(app, storage)
        val graphics = GraphicsManager(app, storage, diag)
        val audio = AudioManager(app, storage, diag)
        val runtime = RuntimeManager(app, storage, diag)
        val wine = WineManager(app, storage, graphics, audio, diag)
        val proc = ProcessManager(runtime, wine, graphics, diag)

        val maliNoVulkan = GpuCapabilityInfo(
            glVendor = "ARM",
            glRenderer = "Mali-G57 MC2",
            isMaliGpu = true,
            vulkanSupported = false
        )
        val resolved = graphics.resolveEffectiveBackend(GraphicsBackend.DXVK, maliNoVulkan, dxvkInstalled = true)
        assertEquals(GraphicsBackend.WINED3D, resolved)

        val profile = SettingsManager(app, storage).applyPreset(GameProfile(), GamePreset.BALANCED)
        assertEquals(960, profile.resolutionWidth)
        assertEquals(540, profile.resolutionHeight)
        assertEquals(30, profile.fpsLimit)

        val health = wine.createOrRepairPrefix(profile, resolved, null, proc)
        assertTrue(health.exists)
        assertTrue(health.hasDriveC)
        assertTrue(health.hasSystem32)
        assertTrue(health.hasRegistryFiles)
        assertTrue(health.hasD3d9Override)

        val backupZip = wine.backupPrefix().getOrNull()
        assertNotNull(backupZip)
        assertTrue(backupZip!!.exists() && backupZip.length() > 0)
    }

    @Test
    fun `TEST 13 and 12 - ControllerManager XInput mask and DiagnosticManager PII sanitization`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storage = StorageManager(app)
        val diag = DiagnosticManager(app, storage)
        val ctrl = ControllerManager(app, storage, diag)
        val cfg = ControllerConfig()

        ctrl.setVirtualButtonPressed(VirtualButtonId.BTN_B, true, cfg)
        ctrl.setVirtualLeftStick(0.8f, -0.5f, cfg)
        val state = ctrl.gamepadState.value
        assertTrue((state.buttonsMask and VirtualButtonId.BTN_B.xinputMask) != 0)
        assertTrue(state.thumbLX > 10000)
        assertTrue(state.thumbLY > 5000) // Android negative Y (up) maps to positive XInput Y

        val sanitized = diag.sanitizeSensitiveInfo(
            "Failed reading /data/user/0/com.aistudio.stormrev.kxmpzq/files/prefix user=tester@example.com"
        )
        assertFalse(sanitized.contains("tester@example.com"))
        assertTrue(sanitized.contains("<app_sandbox>"))

        val perf = PerformanceMonitor(app, storage, ctrl)
        val report = diag.runDiagnosticAudit(
            gameResult = GameValidationResult(status = GameStatus.GAME_NOT_FOUND),
            runtimeComponents = emptyList(),
            prefixHealth = com.stormrevolution.launcher.model.PrefixHealth(),
            gpuInfo = GpuCapabilityInfo(),
            telemetry = perf.collectTelemetry(),
            processState = com.stormrevolution.launcher.model.ProcessExecutionState(lastExitCode = 139),
            selectedBackend = GraphicsBackend.AUTOMATIC
        )
        assertTrue(report.findings.isNotEmpty())
    }

    @Test
    fun `TEST 3 - Launcher opens dashboard and PLAY stays locked until game and runtime verified`() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onNodeWithTag("play_button").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").assertIsNotEnabled()
    }
}
