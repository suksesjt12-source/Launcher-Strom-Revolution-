package com.stormlauncher.nsunsr

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StormLauncherApp() }
    }
}

private val Ink = Color(0xFF080D17)
private val Panel = Color(0xFF151E2E)
private val PanelRaised = Color(0xFF202C40)
private val Ember = Color(0xFFFF7138)
private val Gold = Color(0xFFFFC36B)
private val Muted = Color(0xFFB5C0D0)

@Composable
private fun StormLauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var installedGame by remember { mutableStateOf(GameZipImporter.loadInstalledGame(context)) }
    var importing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importing = true
                notice = "Memeriksa ZIP dan mengekstrak game..."
                try {
                    val result = withContext(Dispatchers.IO) {
                        GameZipImporter.importZip(context, uri)
                    }
                    installedGame = result
                    notice = "Game berhasil diimpor. NSUNSR.exe ditemukan dan lolos pemeriksaan PE."
                } catch (error: Exception) {
                    notice = error.message ?: "Impor gagal. Periksa file ZIP dan ruang penyimpanan."
                } finally {
                    importing = false
                }
            }
        }
    }

    val importAction = {
        if (!importing) {
            zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ember,
            secondary = Gold,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onBackground = Color(0xFFF4F6FA),
            onSurface = Color(0xFFF4F6FA)
        )
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101827)) {
                    val labels = listOf("BERANDA", "GAME", "PENGATURAN")
                    val symbols = listOf("⌂", "◈", "⚙")
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(symbols[index], fontSize = 19.sp) },
                            label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF111B2C), Ink, Color(0xFF090C14))))
                    .padding(padding)
            ) {
                Header()
                when (selectedTab) {
                    0 -> HomeScreen(installedGame, importing, notice, importAction)
                    1 -> GameScreen(installedGame, importing, notice, importAction)
                    else -> SettingsScreen(context)
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(46.dp).height(46.dp).background(Ember, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("S/R", color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text("STORM REVOLUTION", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.2.sp)
            Text("ANDROID GAME LAUNCHER  •  ARM64", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun HomeScreen(game: InstalledGame?, importing: Boolean, notice: String, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("NARUTO SHIPPUDEN", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("ULTIMATE NINJA\nSTORM REVOLUTION", color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
        Text(
            "Launcher khusus untuk instalasi game PC milikmu. Impor satu arsip ZIP berisi folder game.",
            color = Muted, fontSize = 13.sp, lineHeight = 19.sp
        )

        PanelCard(title = "STATUS GAME") {
            if (game == null) {
                Text("BELUM ADA GAME", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Pilih ZIP game. Launcher akan mengekstrak arsip, mencari NSUNSR.exe, lalu memeriksa header executable.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            } else {
                Text("GAME TERDETEKSI", color = Color(0xFF8FE0AE), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(game.executable.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(game.executable.parentFile?.absolutePath ?: game.directory.absolutePath, color = Muted, fontSize = 10.sp)
                Text("${game.fileCount} file • ${game.cpkCount} file CPK", color = Gold, fontSize = 11.sp)
                Text(
                    if (game.hasDataFolder) "Folder data_win32 ditemukan." else "Folder data_win32 tidak terdeteksi; periksa kelengkapan ZIP.",
                    color = Muted, fontSize = 11.sp
                )
            }
        }

        Button(
            onClick = onImport,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink),
            shape = RoundedCornerShape(15.dp)
        ) {
            Text(if (importing) "SEDANG MEMPROSES ZIP..." else "IMPOR GAME DARI FILE ZIP", fontWeight = FontWeight.Black)
        }

        if (importing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ember, trackColor = PanelRaised)
        }
        if (notice.isNotBlank()) {
            PanelCard(title = "INFORMASI") {
                Text(notice, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        PanelCard(title = "MESIN EKSEKUSI WINDOWS") {
            Text("BELUM TERSEDIA", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "Fondasi launcher dan pengelola ZIP sedang dibangun. Runtime Windows ARM64, API Win32, translasi CPU x86/x64, dan jalur grafis belum terintegrasi. Game belum dapat dijalankan dari versi ini.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = PanelRaised, disabledContentColor = Muted)
            ) {
                Text("MAIN GAME — RUNTIME BELUM TERSEDIA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        PanelCard(title = "TARGET PERANGKAT") {
            StatRow("Perangkat sasaran", "Helio G100")
            StatRow("GPU sasaran", "Mali-G57 MC2")
            StatRow("Memori sasaran", "8 GB RAM")
            StatRow("Profil awal", "960 × 540 • 30 FPS")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GameScreen(game: InstalledGame?, importing: Boolean, notice: String, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("GAME LIBRARY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Naruto Storm Revolution", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
        if (game == null) {
            PanelCard(title = "BELUM ADA INSTALASI") {
                Text("Impor satu file ZIP yang berisi seluruh folder instalasi game PC.", color = Muted, fontSize = 12.sp)
                Button(onClick = onImport, enabled = !importing) { Text("PILIH FILE ZIP") }
            }
        } else {
            PanelCard(title = "INSTALASI AKTIF") {
                StatRow("Executable", game.executable.name)
                StatRow("Jumlah file", game.fileCount.toString())
                StatRow("File CPK", game.cpkCount.toString())
                StatRow("Folder data_win32", if (game.hasDataFolder) "Ditemukan" else "Tidak ditemukan")
                Text("Lokasi:", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(game.directory.absolutePath, color = Muted, fontSize = 10.sp)
                OutlinedButton(onClick = onImport, enabled = !importing) { Text("GANTI ARSIP ZIP") }
            }
        }
        if (notice.isNotBlank()) {
            PanelCard(title = "STATUS IMPOR") {
                Text(notice, color = Muted, fontSize = 12.sp)
            }
        }
        PanelCard(title = "STATUS KOMPATIBILITAS") {
            Text("Executable terdeteksi bukan berarti game sudah bisa dimainkan.", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                "Versi ini belum berisi runtime untuk menjalankan Windows PE executable. Deteksi file dan ekstraksi ZIP adalah tahap pengelolaan game, bukan emulasi Windows.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SettingsScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("storm_profile", Context.MODE_PRIVATE) }
    var resolution by remember { mutableStateOf(prefs.getString("resolution", "960 × 540") ?: "960 × 540") }
    var fps by remember { mutableFloatStateOf(prefs.getInt("fps", 30).toFloat()) }
    var cpuMode by remember { mutableStateOf(prefs.getString("cpu_mode", "BALANCED") ?: "BALANCED") }
    var renderer by remember { mutableStateOf(prefs.getString("renderer", "AUTO") ?: "AUTO") }
    var audio by remember { mutableStateOf(prefs.getString("audio", "PULSEAUDIO") ?: "PULSEAUDIO") }
    var resolutionMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("ADVANCED PROFILE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Pengaturan game", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)

        PanelCard(title = "RESOLUSI") {
            Box {
                OutlinedButton(onClick = { resolutionMenu = true }) { Text(resolution) }
                DropdownMenu(expanded = resolutionMenu, onDismissRequest = { resolutionMenu = false }) {
                    listOf("854 × 480", "960 × 540", "1024 × 576", "1280 × 720").forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice) },
                            onClick = {
                                resolution = choice
                                resolutionMenu = false
                            }
                        )
                    }
                }
            }
            Text("Resolusi tersimpan sebagai profil pilihan.", color = Muted, fontSize = 11.sp)
        }

        PanelCard(title = "BATAS FRAME RATE") {
            Text("${fps.toInt()} FPS", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Slider(value = fps, onValueChange = { fps = it }, valueRange = 20f..60f, steps = 7)
            Text("Profil awal yang disarankan: 30 FPS.", color = Muted, fontSize = 11.sp)
        }

        PanelCard(title = "MODE CPU") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOW", "BALANCED", "PERFORMANCE").forEach { option ->
                    FilterChip(selected = cpuMode == option, onClick = { cpuMode = option }, label = { Text(option, fontSize = 9.sp) })
                }
            }
        }

        PanelCard(title = "RENDERER") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "VULKAN", "OPENGL").forEach { option ->
                    FilterChip(selected = renderer == option, onClick = { renderer = option }, label = { Text(option, fontSize = 10.sp) })
                }
            }
            Text("Pilihan renderer belum mengubah jalur grafis sampai backend runtime tersedia.", color = Muted, fontSize = 11.sp)
        }

        PanelCard(title = "AUDIO") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PULSEAUDIO", "ALSA").forEach { option ->
                    FilterChip(selected = audio == option, onClick = { audio = option }, label = { Text(option, fontSize = 10.sp) })
                }
            }
        }

        Button(
            onClick = {
                prefs.edit()
                    .putString("resolution", resolution)
                    .putInt("fps", fps.toInt())
                    .putString("cpu_mode", cpuMode)
                    .putString("renderer", renderer)
                    .putString("audio", audio)
                    .apply()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink)
        ) {
            Text("SIMPAN PROFIL", fontWeight = FontWeight.Black)
        }

        PanelCard(title = "CATATAN PENTING") {
            Text(
                "Pengaturan di atas baru disimpan sebagai preferensi launcher. Pengaturan belum diterapkan ke game karena mesin eksekusi Windows dan backend grafis/audio belum tersedia.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}
