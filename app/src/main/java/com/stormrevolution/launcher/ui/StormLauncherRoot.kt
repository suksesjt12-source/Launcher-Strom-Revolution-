
package com.stormrevolution.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stormrevolution.launcher.LauncherUiState
import com.stormrevolution.launcher.LauncherViewModel
import com.stormrevolution.launcher.model.RuntimeComponentType

@Composable
fun StormLauncherRoot(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onPickGameFolder: () -> Unit,
    onPickRuntimeArchive: (RuntimeComponentType) -> Unit,
    onPickPrefixBackup: () -> Unit,
    onLaunchGameViewport: (Boolean) -> Unit
) {
    if (!state.isSplashComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "STORM LAUNCHER",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("Preparing Android runtime")
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "STORM REVOLUTION",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text("Windows game launcher • Android ARM64")

            if (state.isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.statusBannerMessage?.takeIf { it.isNotBlank() }?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // GAME FILES
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "GAME FILES",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text("Game status: ${state.gameResult.status.name}")

                    Text(
                        state.gameResult.localExecutablePath
                            ?: "NSUNSR.exe belum dipilih."
                    )

                    Button(
                        onClick = onPickGameFolder,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECT GAME FOLDER")
                    }

                    Button(
                        onClick = {
                            viewModel.launchGameRuntime {
                                onLaunchGameViewport(false)
                            }
                        },
                        enabled = !state.isBusy &&
                            state.gameResult.status.name == "READY_TO_LAUNCH",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("LAUNCH GAME")
                    }

                    if (state.processState.isRunning) {
                        OutlinedButton(
                            onClick = {
                                viewModel.stopGameRuntime()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("STOP PROCESS")
                        }
                    }
                }
            }

            // RUNTIME COMPONENTS
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "RUNTIME COMPONENTS",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "Import runtime packages yang sudah kamu miliki."
                    )

                    state.runtimeComponents.forEach { component ->

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    component.type.name.replace('_', ' ')
                                )

                                Text(
                                    component.installStatus.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onPickRuntimeArchive(component.type)
                                },
                                enabled = !state.isBusy
                            ) {
                                Text("IMPORT")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.verifyAllRuntime()
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VERIFY RUNTIME")
                    }
                }
            }

            // WINE PREFIX
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "WINE PREFIX",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Button(
                        onClick = {
                            viewModel.createOrRepairPrefix()
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CREATE / REPAIR PREFIX")
                    }

                    OutlinedButton(
                        onClick = onPickPrefixBackup,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RESTORE PREFIX BACKUP")
                    }
                }
            }

            // CONTROLS AND DIAGNOSTICS
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "CONTROLS & DIAGNOSTICS",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Button(
                        onClick = {
                            onLaunchGameViewport(true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EDIT TOUCH CONTROLS")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Copy game files to private storage")

                        Switch(
                            checked = state.copyGameToPrivate,
                            onCheckedChange = {
                                viewModel.setCopyGameToPrivate(it)
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.exportLogs()
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EXPORT DIAGNOSTIC LOGS")
                    }

                    Text(
                        text = "Native bridge: ${
                            if (state.telemetry.nativeBridgeLoaded)
                                "LOADED"
                            else
                                "NOT LOADED"
                        }",
                        style = MaterialTheme.typography.labelSmall
                    )

                    state.telemetry.nativeBridgeError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
