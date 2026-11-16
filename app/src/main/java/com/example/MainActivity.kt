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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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

    val shortcutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: throw IllegalArgumentException("File shortcut tidak dapat dibaca.")
                val documentId = DocumentsContract.getDocumentId(uri)
                shortcut = WinlatorShortcutParser.parse(
                    desktopText = content,
                    documentId = documentId,
                    primaryStorageRoot = Environment.getExternalStorageDirectory().absolutePath
                )
                launcherMessage = "Shortcut siap. Tekan MAIN KAN DI WINLATOR."
            } catch (exception: Exception) {
                shortcut = null
                launcherMessage = exception.message ?: "Shortcut tidak valid atau lokasinya tidak didukung."
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
                            "APK ini menghubungkan shortcut game yang sudah dibuat di Winlator " +
                                "ke layar game Winlator. Game tetap dijalankan oleh Wine/Box64 di Winlator, " +
                                "bukan oleh runtime native di APK ini."
                        )
                        Button(
                            onClick = {
                                launcherMessage = openWinlator(context)
                                    ?: "Winlator dibuka. Buat container, tambahkan NSUNSR.exe sebagai shortcut, lalu ekspor shortcut tersebut."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("BUKA WINLATOR MALI")
                        }
                        Button(
                            onClick = { shortcutPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PILIH SHORTCUT GAME (.DESKTOP)")
                        }
                        shortcut?.let { selected ->
                            Divider()
                            Text("Game: " + selected.name, fontWeight = FontWeight.SemiBold)
                            Text("Container ID: " + selected.containerId)
                            Text("File: " + selected.fileName)
                            Button(
                                onClick = {
                                    launcherMessage = launchWinlatorShortcut(context, selected)
                                        ?: "Perintah dikirim ke Winlator. Game akan terbuka bila container dan shortcut masih valid."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("MAIN KAN DI WINLATOR")
                            }
                        }
                        launcherMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Cara menyiapkan: di Winlator, buat container dan shortcut untuk NSUNSR.exe. " +
                                "Pada menu shortcut, pilih Export Shortcut. Pilih file hasil ekspor di sini. " +
                                "Lokasi ekspor bawaan Winlator: /storage/emulated/0/Winlator/Shortcuts.",
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

private fun launchWinlatorShortcut(context: Context, shortcut: WinlatorShortcut): String? {
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
        putExtra("container_id", shortcut.containerId)
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
