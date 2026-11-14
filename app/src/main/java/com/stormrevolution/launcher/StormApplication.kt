package com.stormrevolution.launcher

import android.app.Application
import com.stormrevolution.launcher.manager.AudioManager
import com.stormrevolution.launcher.manager.ControllerManager
import com.stormrevolution.launcher.manager.DiagnosticManager
import com.stormrevolution.launcher.manager.GameManager
import com.stormrevolution.launcher.manager.GraphicsManager
import com.stormrevolution.launcher.manager.PerformanceMonitor
import com.stormrevolution.launcher.manager.ProcessManager
import com.stormrevolution.launcher.manager.RuntimeManager
import com.stormrevolution.launcher.manager.SettingsManager
import com.stormrevolution.launcher.manager.StorageManager
import com.stormrevolution.launcher.manager.WineManager

class StormContainer(app: Application) {
    val storageManager = StorageManager(app)
    val diagnosticManager = DiagnosticManager(app, storageManager)
    val graphicsManager = GraphicsManager(app, storageManager, diagnosticManager)
    val audioManager = AudioManager(app, storageManager, diagnosticManager)
    val runtimeManager = RuntimeManager(app, storageManager, diagnosticManager)
    val wineManager = WineManager(app, storageManager, graphicsManager, audioManager, diagnosticManager)
    val gameManager = GameManager(app, storageManager, runtimeManager, diagnosticManager)
    val controllerManager = ControllerManager(app, storageManager, diagnosticManager)
    val processManager = ProcessManager(runtimeManager, wineManager, graphicsManager, diagnosticManager)
    val settingsManager = SettingsManager(app, storageManager)
    val performanceMonitor = PerformanceMonitor(app, storageManager, controllerManager)
}

class StormApplication : Application() {
    lateinit var container: StormContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = StormContainer(this)
        container.storageManager.ensureBaseDirectories()
    }
}
