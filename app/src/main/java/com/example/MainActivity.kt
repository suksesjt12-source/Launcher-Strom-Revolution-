package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.StormLauncherTheme
import com.stormrevolution.launcher.GameViewportActivity
import com.stormrevolution.launcher.LauncherViewModel
import com.stormrevolution.launcher.model.RuntimeComponentType
import com.stormrevolution.launcher.ui.StormLauncherRoot

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StormLauncherTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var pendingRuntimeType by remember { mutableStateOf<RuntimeComponentType?>(null) }

                val gameFolderPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onGameDirectorySelected(uri)
                    }
                }

                val runtimeArchivePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    val targetType = pendingRuntimeType
                    pendingRuntimeType = null
                    if (uri != null && targetType != null) {
                        viewModel.installRuntimeComponent(targetType, uri)
                    }
                }

                val prefixRestorePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.restorePrefix(uri)
                    }
                }

                StormLauncherRoot(
                    state = uiState,
                    viewModel = viewModel,
                    onPickGameFolder = { gameFolderPicker.launch(null) },
                    onPickRuntimeArchive = { type ->
                        pendingRuntimeType = type
                        runtimeArchivePicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/gzip",
                                "application/x-tar",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onPickPrefixBackup = {
                        prefixRestorePicker.launch(arrayOf("application/zip", "*/*"))
                    },
                    onLaunchGameViewport = { editLayout ->
                        val intent = Intent(this, GameViewportActivity::class.java).apply {
                            putExtra(GameViewportActivity.EXTRA_LAYOUT_EDITOR, editLayout)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
