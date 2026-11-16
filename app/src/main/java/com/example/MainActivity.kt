package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StormLauncherApp() }
    }
}

@Composable
private fun StormLauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<GameScanReport?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shortcut by remember { mutableStateOf<WinlatorShortcut?>(null) }
    var containerIdInput by remember { mutableStateOf("") }
    var launcherMessage by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            isScanning = true
            report = null
            error = null
            scope.launch {
                try {
                    report = withContext(Dispatchers.IO) {
                        GameFolderScanner.scan(context, uri)
                    }
                } catch (exception: Exception) {
                    error = exception.message ?: "Pemindaian folder gagal."
                } finally {
                    isScanning = false
                }
            }
        }
    }

    val gameFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val documentId = DocumentsContract.getDocumentId(uri)
                val selected = if (documentId.substringAfterLast(":").endsWith(".desktop", true)) {
                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } ?: throw IllegalArgumentException("File shortcut tidak dapat dibaca.")
                    WinlatorShortcutParser.parse(
                        desktopText = content,
                        documentId = documentId,
                        primaryStorageRoot = Environment.getExternalStorageDirectory().absolutePath
                    )
                } else {
                    WinlatorShortcutParser.parse(
                        desktopText = null,
                        documentId = documentId,
                        primaryStorageRoot = Environment.getExternalStorageDirectory().absolutePath
                    )
                }
                shortcut = selected
                containerIdInput = selected.containerId?.toString().orEmpty()
                launcherMessage = if (selected.containerId == null) {
                    "File terbaca. Masukkan ID container Winlator sebelum meluncurkan game."
                } else {
                    "File dan ID container terbaca. Periksa ID lalu tekan MAIN KAN DI WINLATOR."
                }
            } catch (exception: Exception) {
                shortcut = null
                containerIdInput = ""
                launcherMessage = exception.message ?: "File game atau shortcut tidak valid."
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "STORM REVOLUTION",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Android companion · Winlator Mali",
                    style = MaterialTheme.typography.titleSmall
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Jalankan game melalui Winlator", fontWeight = FontWeight.Bold)
                        Text(
                            "Game tetap dijalankan oleh Wine/Box64 di Winlator. APK ini hanya " +
                                "membuka file game atau shortcut di container Winlator yang dipilih."
                        )
                        Button(
                            onClick = {
                                launcherMessage = openWinlator(context)
                                    ?: "Winlator dibuka. Buat container lalu pilih file game atau shortcut di aplikasi ini."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("BUKA WINLATOR MALI")
                        }
                        Button(
                            onClick = { gameFilePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PILIH NSUNSR.EXE / SHORTCUT")
                        }
                        shortcut?.let { selected ->
                            Divider()
                            Text("Game: " + selected.name, fontWeight = FontWeight.SemiBold)
                            Text("File: " + selected.fileName)
                            OutlinedTextField(
                                value = containerIdInput,
                                onValueChange = { value ->
                                    containerIdInput = value.filter { it.isDigit() }.take(6)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("ID container Winlator") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Text(
                                "Jika nama container masih bawaan, ID biasanya terlihat pada nama seperti Container-1. " +
                                    "Gunakan angka ID container yang berisi game tersebut.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    val id = containerIdInput.toIntOrNull()
                                    if (id == null || id <= 0) {
                                        launcherMessage = "Masukkan ID container berupa angka lebih dari 0."
                                    } else {
                                        launcherMessage = launchWinlatorShortcut(context, selected, id)
                                            ?: "Perintah peluncuran dikirim ke Winlator. Game hanya akan terbuka jika container dan file game valid."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("MAINKAN DI WINLATOR")
                            }
                        }
                        launcherMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Pilih NSUNSR.exe yang berada di penyimpanan bersama, atau pilih file .desktop hasil Export Shortcut dari Winlator. " +
                                "Jika shortcut tidak menyimpan ID container, masukkan ID secara manual.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Perangkat", fontWeight = FontWeight.Bold)
                        Text("Model: " + Build.MODEL)
                        Text("Android API: " + Build.VERSION.SDK_INT)
                        Text("ABI: " + (Build.SUPPORTED_ABIS.firstOrNull() ?: "Tidak diketahui"))
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Audit folder PC (opsional)", fontWeight = FontWeight.Bold)
                        Text(
                            "Pemindaian ini hanya memeriksa nama file dan header NSUNSR.exe. " +
                                "Ini tidak mengubah atau menyalin file game."
                        )
                        Button(
                            onClick = { folderPicker.launch(null) },
                            enabled = !isScanning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PILIH FOLDER GAME PC")
                        }
                        if (isScanning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Memeriksa NSUNSR.exe, DLL, dan folder data…")
                        }
                        error?.let {
                            Text("Kesalahan: " + it, color = MaterialTheme.colorScheme.error)
                        }
                        report?.let { result ->
                            ScanResultCard(result)
                        }
                    }
                }

                TextButton(
                    onClick = {
                        report = null
                        error = null
                        shortcut = null
                        containerIdInput = ""
                        launcherMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BERSIHKAN HASIL")
                }
            }
        }
    }
}

private fun resolveWinlatorPackage(context: Context): String? {
    val candidates = listOf("com.ludashi.benchmark", "com.winlator.mali")
    return candidates.firstOrNull { packageName ->
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    }
}

private fun openWinlator(context: Context): String? {
    val packageName = resolveWinlatorPackage(context)
        ?: return "Winlator Mali tidak ditemukan. Pasang Winlator Mali Ludashi atau varian standar terlebih dahulu."
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        ?: return "Aktivitas utama Winlator tidak ditemukan."
    return try {
        context.startActivity(intent)
        null
    } catch (exception: Exception) {
        "Tidak dapat membuka Winlator: " + (exception.message ?: "kesalahan tidak diketahui")
    }
}

private fun launchWinlatorShortcut(
    context: Context,
    shortcut: WinlatorShortcut,
    containerId: Int
): String? {
    val packageName = resolveWinlatorPackage(context)
        ?: return "Winlator Mali tidak ditemukan. Pasang aplikasinya terlebih dahulu."
    val component = ComponentName(packageName, "com.winlator.cmod.XServerDisplayActivity")
    val activityInfo = runCatching {
        context.packageManager.getActivityInfo(component, 0)
    }.getOrNull()

    if (activityInfo?.exported != true) {
        return "Versi Winlator yang terpasang tidak menyediakan aktivitas peluncuran shortcut yang kompatibel."
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        this.component = component
        putExtra("container_id", containerId)
        putExtra("shortcut_path", shortcut.absolutePath)
        putExtra("shortcut_name", shortcut.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(intent)
        null
    } catch (exception: Exception) {
        "Winlator menolak peluncuran shortcut: " + (exception.message ?: "kesalahan tidak diketahui")
    }
}

@Composable
private fun ScanResultCard(report: GameScanReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Hasil audit folder", fontWeight = FontWeight.Bold)
            Text("Folder: " + report.folderName)
            Text("Executable: " + (report.executableName ?: "NSUNSR.exe tidak ditemukan"))
            Text("Format: " + (report.peInspection?.description ?: "Belum dapat dibaca"))
            Text("Folder data: " + if (report.hasDataFolder) "Ditemukan" else "Belum ditemukan")
            Text("DLL ditemukan: " + report.dllCount)
            Text("File CPK ditemukan: " + report.cpkCount)
            Text("Entri diperiksa: " + report.scannedEntries)
            if (report.scanLimitReached) {
                Text(
                    "Batas pemindaian tercapai; hasil mungkin belum mencakup semua subfolder.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (report.dllNames.isNotEmpty()) {
                Divider()
                Text("Contoh DLL:", fontWeight = FontWeight.SemiBold)
                report.dllNames.forEach { Text("• " + it) }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (report.peInspection?.isPeImage == true) {
                    "Executable Windows terdeteksi. File tetap harus dijalankan melalui runtime kompatibilitas seperti Winlator."
                } else {
                    "Executable PE yang valid belum terverifikasi. Periksa apakah folder yang dipilih benar."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
