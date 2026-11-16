package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
        setContent { StormAuditApp() }
    }
}

@Composable
private fun StormAuditApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<GameScanReport?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                    text = "Android ARM64 · Port research build",
                    style = MaterialTheme.typography.titleSmall
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Status proyek", fontWeight = FontWeight.Bold)
                        Text("Aplikasi audit folder game sedang disiapkan.")
                        Text(
                            "Ini belum menjalankan game dan bukan hasil recompile native. " +
                                "EXE Windows x86 tidak dapat dijalankan langsung sebagai kode ARM64."
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Perangkat", fontWeight = FontWeight.Bold)
                        Text("Model: ${Build.MODEL}")
                        Text("Android API: ${Build.VERSION.SDK_INT}")
                        Text("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Tidak diketahui"}")
                    }
                }

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
                    Text("Kesalahan: $it", color = MaterialTheme.colorScheme.error)
                }

                report?.let { result ->
                    ScanResultCard(result)
                }

                Text(
                    text = "Catatan: folder game tetap berada di penyimpanan perangkat. " +
                        "Aplikasi hanya membaca nama file dan header executable; file game " +
                        "tidak diunggah ke GitHub.",
                    style = MaterialTheme.typography.bodySmall
                )

                TextButton(
                    onClick = {
                        report = null
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("HAPUS HASIL PEMINDAIAN")
                }
            }
        }
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
            Text("Folder: ${report.folderName}")
            Text("Executable: ${report.executableName ?: "NSUNSR.exe tidak ditemukan"}")
            Text("Format: ${report.peInspection?.description ?: "Belum dapat dibaca"}")
            Text("Folder data: ${if (report.hasDataFolder) "Ditemukan" else "Belum ditemukan"}")
            Text("DLL ditemukan: ${report.dllCount}")
            Text("File CPK ditemukan: ${report.cpkCount}")
            Text("Entri diperiksa: ${report.scannedEntries}")
            if (report.scanLimitReached) {
                Text(
                    "Batas pemindaian tercapai; hasil mungkin belum mencakup semua subfolder.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (report.dllNames.isNotEmpty()) {
                Divider()
                Text("Contoh DLL:", fontWeight = FontWeight.SemiBold)
                report.dllNames.forEach { Text("• $it") }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (report.peInspection?.isPeImage == true) {
                    "Executable PE terdeteksi. Audit file berhasil; proses porting native masih memerlukan source code atau implementasi ulang engine."
                } else {
                    "Executable PE yang valid belum terverifikasi. Periksa apakah folder yang dipilih benar."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
