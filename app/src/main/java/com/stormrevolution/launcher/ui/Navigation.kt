package com.stormrevolution.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.theme.StormObsidian
import com.example.ui.theme.StormSurface
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.LauncherViewModel
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.ui.screens.ControlsSettingsScreen
import com.stormrevolution.launcher.ui.screens.DashboardScreen
import com.stormrevolution.launcher.ui.screens.DebugConsoleScreen
import com.stormrevolution.launcher.ui.screens.GameConfigurationScreen
import com.stormrevolution.launcher.ui.screens.GameLibraryScreen
import com.stormrevolution.launcher.ui.screens.GraphicsSettingsScreen
import com.stormrevolution.launcher.ui.screens.LogViewerScreen
import com.stormrevolution.launcher.ui.screens.RuntimeManagerScreen
import com.stormrevolution.launcher.ui.screens.SplashScreen
import com.stormrevolution.launcher.ui.screens.SystemInfoScreen
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object GameLibraryRoute
@Serializable data object GameConfigRoute
@Serializable data object RuntimeManagerRoute
@Serializable data object GraphicsSettingsRoute
@Serializable data object ControlsSettingsRoute
@Serializable data object SystemInfoRoute
@Serializable data object DebugConsoleRoute
@Serializable data object LogViewerRoute

private data class NavTab(
    val label: String,
    val icon: ImageVector,
    val route: Any
)

private val PrimaryTabs = listOf(
    NavTab("Home", Icons.Default.Dashboard, DashboardRoute),
    NavTab("Library", Icons.Default.FolderOpen, GameLibraryRoute),
    NavTab("Config", Icons.Default.Tune, GameConfigRoute),
    NavTab("Runtime", Icons.Default.DeveloperBoard, RuntimeManagerRoute),
    NavTab("Debug", Icons.Default.BugReport, DebugConsoleRoute)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StormLauncherRoot(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onPickGameFolder: () -> Unit,
    onPickRuntimeArchive: (RuntimeComponentType) -> Unit,
    onPickPrefixBackup: () -> Unit,
    onLaunchGameViewport: (editLayoutMode: Boolean) -> Unit
) {
    AnimatedContent(
        targetState = state.isSplashComplete,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "splash_transition"
    ) { ready ->
        if (!ready) {
            SplashScreen()
        } else {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(state.statusBannerMessage) {
                val msg = state.statusBannerMessage
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.dismissBanner()
                }
            }

            val screenTitle = when {
                currentDestination?.hasRoute<GameLibraryRoute>() == true -> "Game Library"
                currentDestination?.hasRoute<GameConfigRoute>() == true -> "Game Configuration"
                currentDestination?.hasRoute<RuntimeManagerRoute>() == true -> "Runtime Manager"
                currentDestination?.hasRoute<GraphicsSettingsRoute>() == true -> "Graphics Settings"
                currentDestination?.hasRoute<ControlsSettingsRoute>() == true -> "Controls Settings"
                currentDestination?.hasRoute<SystemInfoRoute>() == true -> "System Information"
                currentDestination?.hasRoute<DebugConsoleRoute>() == true -> "Debug Console"
                currentDestination?.hasRoute<LogViewerRoute>() == true -> "Log Viewer"
                else -> "Storm Launcher"
            }
            val isSecondaryScreen = currentDestination != null &&
                !PrimaryTabs.any { tab -> currentDestination.hasRoute(tab.route::class) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StormObsidian)
            ) {
                val useNavRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = StormSurface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        ) {
                            PrimaryTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<GraphicsSettingsRoute>() == true,
                                onClick = { navController.navigate(GraphicsSettingsRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Graphics") },
                                label = { Text("GPU") }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<ControlsSettingsRoute>() == true,
                                onClick = { navController.navigate(ControlsSettingsRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Gamepad, contentDescription = "Controls") },
                                label = { Text("Pad") }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<SystemInfoRoute>() == true,
                                onClick = { navController.navigate(SystemInfoRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Info, contentDescription = "System Info") },
                                label = { Text("Sys") }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<LogViewerRoute>() == true,
                                onClick = { navController.navigate(LogViewerRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs") },
                                label = { Text("Logs") }
                            )
                        }
                    }

                    Scaffold(
                        containerColor = StormObsidian,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = { Text(screenTitle, style = MaterialTheme.typography.titleLarge) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = StormSurface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                navigationIcon = {
                                    if (isSecondaryScreen) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.cd_back)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useNavRail) {
                                NavigationBar(
                                    containerColor = StormSurface,
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    PrimaryTabs.forEach { tab ->
                                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(tab.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = DashboardRoute,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<DashboardRoute> {
                                    DashboardScreen(
                                        state = state,
                                        onPlayClick = {
                                            viewModel.launchGameRuntime {
                                                onLaunchGameViewport(false)
                                            }
                                        },
                                        onOpenConfig = { navController.navigate(GameConfigRoute) },
                                        onOpenRuntime = { navController.navigate(RuntimeManagerRoute) },
                                        onOpenGameFiles = { navController.navigate(GameLibraryRoute) },
                                        onOpenDebug = { navController.navigate(DebugConsoleRoute) },
                                        onOpenControls = { navController.navigate(ControlsSettingsRoute) },
                                        onOpenGraphics = { navController.navigate(GraphicsSettingsRoute) },
                                        onOpenSystemInfo = { navController.navigate(SystemInfoRoute) }
                                    )
                                }
                                composable<GameLibraryRoute> {
                                    GameLibraryScreen(
                                        state = state,
                                        onPickGameFolder = onPickGameFolder,
                                        onToggleCopyToPrivate = viewModel::setCopyGameToPrivate,
                                        onRescanGame = viewModel::revalidateGame,
                                        onApplyPreset = viewModel::applyPreset
                                    )
                                }
                                composable<GameConfigRoute> {
                                    GameConfigurationScreen(
                                        state = state,
                                        onApplyPreset = viewModel::applyPreset,
                                        onUpdateProfile = viewModel::updateProfile,
                                        onCreateOrRepairPrefix = viewModel::createOrRepairPrefix,
                                        onResetPrefix = viewModel::resetPrefix,
                                        onBackupPrefix = viewModel::backupPrefix,
                                        onRestorePrefix = onPickPrefixBackup
                                    )
                                }
                                composable<RuntimeManagerRoute> {
                                    RuntimeManagerScreen(
                                        state = state,
                                        onInstallComponent = onPickRuntimeArchive,
                                        onRepairComponent = viewModel::repairRuntimeComponent,
                                        onRemoveComponent = viewModel::removeRuntimeComponent,
                                        onVerifyAll = viewModel::verifyAllRuntime
                                    )
                                }
                                composable<GraphicsSettingsRoute> {
                                    GraphicsSettingsScreen(
                                        state = state,
                                        onUpdateProfile = viewModel::updateProfile,
                                        onClearShaderCache = viewModel::clearShaderCache
                                    )
                                }
                                composable<ControlsSettingsRoute> {
                                    ControlsSettingsScreen(
                                        state = state,
                                        onUpdateController = viewModel::updateControllerConfig,
                                        onResetLayout = viewModel::resetControllerLayout,
                                        onOpenLayoutEditorPreview = { onLaunchGameViewport(true) }
                                    )
                                }
                                composable<SystemInfoRoute> {
                                    SystemInfoScreen(state = state)
                                }
                                composable<DebugConsoleRoute> {
                                    DebugConsoleScreen(
                                        state = state,
                                        onRunDiagnostic = { viewModel.runDiagnostic() },
                                        onCopyLogs = viewModel::copyLogs,
                                        onExportLogs = viewModel::exportLogs,
                                        onClearLogs = viewModel::clearLogs,
                                        onOpenLogViewer = { navController.navigate(LogViewerRoute) }
                                    )
                                }
                                composable<LogViewerRoute> {
                                    LogViewerScreen(
                                        state = state,
                                        onCopyLogs = viewModel::copyLogs,
                                        onExportLogs = viewModel::exportLogs,
                                        onClearLogs = viewModel::clearLogs
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
