package com.stormrevolution.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 13, 18)
    private val panel = Color.rgb(22, 27, 38)
    private val orange = Color.rgb(255, 122, 26)
    private val white = Color.rgb(245, 246, 248)
    private val muted = Color.rgb(157, 166, 181)
    private lateinit var pageHost: FrameLayout
    private var selectedName = "Belum ada file dipilih"
    private var selectedGameUri: Uri? = null
    private var selectedFolderUri: Uri? = null
    private var selectedFolderName = "Folder game belum dipilih"
    private var assetScanReport = "Pilih folder instalasi game untuk mulai inventaris aset."
    private var scanInProgress = false
    private var extractionInProgress = false
    private var extractionReport = "Belum ada payload XFBIN yang diekstrak."
    private var cpkInspectionInProgress = false
    private var cpkInspectionReport = "Pilih data.cpk atau stage.cpk untuk membaca indeks tanpa mengekstrak arsip."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        restoreSelectedGame()
        prepareRendererAssets()
        buildShell()
        showHome()
    }

    /**
     * Shader source is compiled to SPIR-V by the Android CI workflow and
     * packaged in APK assets. Copy it to app-private storage for native Vulkan.
     */
    private fun prepareRendererAssets() {
        try {
            val shaderDirectory = File(filesDir, "shaders")
            if (!shaderDirectory.exists()) shaderDirectory.mkdirs()
            listOf("storm.vert.spv", "storm.frag.spv").forEach { name ->
                assets.open("shaders/$name").use { input ->
                    File(shaderDirectory, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {
            // The native renderer reports a readable error if shader assets are absent.
        }
        StormEngine.setAssetRoot(filesDir.absolutePath)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(14))
        }
        header.addView(label("STORM", 23, white, true))
        header.addView(label("REVOLUTION LAUNCHER", 11, orange, true))
        root.addView(header)

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundColor(Color.rgb(16, 20, 29))
        }
        listOf("BERANDA", "GAME", "PENGATURAN", "TENTANG").forEachIndexed { index, title ->
            val b = Button(this).apply {
                text = title
                textSize = 9f
                setTextColor(white)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    when (index) {
                        0 -> showHome()
                        1 -> showLibrary()
                        2 -> showSettings()
                        else -> showAbout()
                    }
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        val content = page()
        content.addView(card {
            addView(label("NINJA BATTLE EXPERIENCE", 11, orange, true))
            addView(label("STORM REVOLUTION", 27, white, true))
            addView(label("Launcher Android mandiri dengan preview mesin 3D Vulkan.", 14, muted))
        })
        content.addView(card {
            addView(label("GAME TERPILIH", 12, orange, true))
            addView(label(selectedName, 15, white, true))
            addView(action("PILIH FILE GAME (.EXE)") { pickExe() })
            addView(action("MAIN SEKARANG") { showEngineNotice() })
        })
        content.addView(card {
            addView(label("FOLDER DATA GAME", 12, orange, true))
            addView(label(selectedFolderName, 14, white, true))
            addView(action("PILIH FOLDER INSTALASI GAME") { pickGameFolder() })
            addView(action(if (scanInProgress) "MEMERIKSA ASET..." else "INVENTARIS ASET GAME") {
                scanGameFolder()
            })
            addView(label(assetScanReport, 11, muted))
            addView(label("0.9.0: tabel XFBIN, halaman, dan payload NUD/NUT dapat diekstrak ke penyimpanan privat aplikasi. Decoder mesh/tekstur dan gameplay masih dikerjakan.", 10, muted))
            addView(action(if (extractionInProgress) "MENGEKSTRAK PAYLOAD..." else "PILIH XFBIN & EKSTRAK NUD/NUT") { pickXfbin() })
            addView(label(extractionReport, 10, muted))
            addView(action(if (cpkInspectionInProgress) "MEMBACA INDEKS CPK..." else "PILIH CPK & BACA INDEKS") { pickCpk() })
            addView(label(cpkInspectionReport, 10, muted))
        })
        content.addView(card {
            addView(label("STATUS PROYEK", 12, orange, true))
            addView(label("Launcher: aktif", 14, white))
            addView(label("Storm Engine: Vulkan 3D pipeline, depth buffer, kamera perspektif, mesh uji, input multi-sentuh.", 13, white))
            addView(label("Runtime khusus Naruto native ARM64: belum tersedia.", 13, muted))
            addView(label("Tidak ada Wine, Box64, DXVK, atau Winlator.", 12, muted))
        })
        content.addView(card {
            addView(label("PREVIEW RENDERER 3D VULKAN", 12, orange, true))
            addView(label(StormEngine.getVulkanReport(), 12, white))
            addView(label(StormEngine.getEngineReport(), 11, muted))
            addView(label("Kontrol uji: kiri untuk bergerak; area kanan atas dan bawah disiapkan untuk input lompat/serang. Model ini hanya aset uji mesin.", 11, muted))
            addView(NativeVulkanSurfaceView(this@MainActivity), LinearLayout.LayoutParams(-1, dp(230)))
        })
        show(content)
    }

    private fun showLibrary() {
        val content = page()
        content.addView(label("PUSTAKA GAME", 24, white, true))
        content.addView(label("Pilih file game untuk dicatat di launcher.", 13, muted))
        content.addView(card {
            addView(label("Naruto Shippuden: Ultimate Ninja Storm Revolution", 16, white, true))
            addView(label(selectedName, 12, muted))
            addView(action("PILIH FILE") { pickExe() })
            addView(action("MAIN SEKARANG") { showEngineNotice() })
        })
        show(content)
    }

    private fun showSettings() {
        val content = page()
        content.addView(label("PENGATURAN", 24, white, true))
        content.addView(card {
            addView(label("TAMPILAN", 14, orange, true))
            addView(label("Tema: Gelap dengan aksen oranye", 13, white))
            addView(label("Orientasi: dapat disesuaikan pada versi berikutnya", 12, muted))
        })
        content.addView(card {
            addView(label("KOMPATIBILITAS GAME", 14, orange, true))
            addView(label("Renderer Vulkan 3D untuk uji engine sudah ditambahkan. Pipeline game belum tersedia.", 13, muted))
            addView(label("Launcher ini tidak memuat runtime Windows.", 12, muted))
        })
        show(content)
    }

    private fun showAbout() {
        val content = page()
        content.addView(label("TENTANG", 24, white, true))
        content.addView(card {
            addView(label("STORM REVOLUTION LAUNCHER", 17, white, true))
            addView(label("Versi 0.9.0 · XFBIN Model/Texture Payload Extractor", 12, orange, true))
            addView(label("Engine native ARM64 dengan render pipeline 3D awal. Belum memuat runtime khusus Naruto atau menjalankan EXE Windows.", 13, muted))
        })
        show(content)
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(20))
    }

    private fun show(content: LinearLayout) {
        pageHost.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content)
        pageHost.addView(scroll)
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, 20)
            block()
        }
        val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        box.layoutParams = params
        return box
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(6))
        }

    private fun action(title: String, run: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.BLACK)
        background = rounded(orange, 14)
        setOnClickListener { run() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun restoreSelectedGame() {
        val preferences = getSharedPreferences("storm_launcher", MODE_PRIVATE)
        selectedName = preferences.getString("selected_name", null)
            ?: "Belum ada file dipilih"
        selectedGameUri = preferences.getString("selected_uri", null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        selectedFolderName = preferences.getString("selected_folder_name", null)
            ?: "Folder game belum dipilih"
        selectedFolderUri = preferences.getString("selected_folder_uri", null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        assetScanReport = preferences.getString("asset_scan_report", null)
            ?: "Pilih folder instalasi game untuk mulai inventaris aset."
        extractionReport = preferences.getString("extraction_report", null)
            ?: "Belum ada payload XFBIN yang diekstrak."
    }

    private fun pickExe() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, 21)
    }

    private fun pickXfbin() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 23)
    }

    private fun pickCpk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 24)
    }

    private fun pickGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, 22)
    }

    /**
     * A Storage Access Framework document ID is not always the real filename.
     * Ask the provider for DISPLAY_NAME instead of inspecting lastPathSegment.
     */
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val providerName = queryDisplayName(uri)?.trim()?.takeIf { it.isNotEmpty() }
        if (providerName != null) return providerName.substringAfterLast('/')
        val fallback = Uri.decode(uri.lastPathSegment.orEmpty())
            .substringAfterLast('/')
            .substringAfterLast(':')
            .trim()
        return fallback.ifEmpty { "File dipilih" }
    }

    private fun handleGameFile(uri: Uri, data: Intent?) {
        val fileName = resolveDisplayName(uri)
        if (!fileName.endsWith(".exe", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Format file tidak didukung")
                .setMessage(
                    "File yang dipilih: $fileName\n" +
                        "Pilih executable Windows berakhiran .exe."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        selectedName = fileName
        selectedGameUri = uri
        runCatching {
            val granted = (data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (granted != 0) contentResolver.takePersistableUriPermission(uri, granted)
        }
        getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
            .putString("selected_name", selectedName)
            .putString("selected_uri", uri.toString())
            .apply()
        showHome()
    }

    private fun handleXfbin(uri: Uri, data: Intent?) {
        val fileName = resolveDisplayName(uri)
        if (!fileName.endsWith(".xfbin", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Bukan file XFBIN")
                .setMessage("Pilih satu file dengan ekstensi .xfbin. File lain belum dapat diproses oleh extractor ini.")
                .setPositiveButton("OK", null).show()
            return
        }
        runCatching {
            val granted = (data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (granted != 0) contentResolver.takePersistableUriPermission(uri, granted)
        }
        if (extractionInProgress) return
        extractionInProgress = true
        extractionReport = "Membaca tabel XFBIN dan mengekstrak payload NUD/NUT..."
        showHome()
        Thread {
            val result = XfbinPayloadExtractor.extract(contentResolver, uri, fileName, filesDir)
            runOnUiThread {
                extractionInProgress = false
                extractionReport = result.report
                getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
                    .putString("extraction_report", extractionReport).apply()
                showHome()
            }
        }.start()
    }

    private fun handleCpk(uri: Uri, data: Intent?) {
        val fileName = resolveDisplayName(uri)
        if (!fileName.endsWith(".cpk", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Bukan file CPK")
                .setMessage("Pilih data.cpk, stage.cpk, atau arsip lain dengan ekstensi .cpk.")
                .setPositiveButton("OK", null).show()
            return
        }
        runCatching {
            val granted = (data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (granted != 0) contentResolver.takePersistableUriPermission(uri, granted)
        }
        if (cpkInspectionInProgress) return
        cpkInspectionInProgress = true
        cpkInspectionReport = "Membaca header CPK dan tabel UTF/TOC. Payload arsip tidak diekstrak."
        showHome()
        Thread {
            val result = RetailContainerInspector.inspect(contentResolver, uri, fileName, "cpk")
            runOnUiThread {
                cpkInspectionInProgress = false
                cpkInspectionReport = result.details
                getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
                    .putString("cpk_inspection_report", cpkInspectionReport).apply()
                showHome()
            }
        }.start()
    }

    private fun handleGameFolder(uri: Uri, data: Intent?) {
        runCatching {
            val granted = (data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (granted != 0) contentResolver.takePersistableUriPermission(uri, granted)
        }
        selectedFolderUri = uri
        selectedFolderName = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
                .substringAfter(':')
                .substringAfterLast('/')
                .ifBlank { "Folder game dipilih" }
        }.getOrDefault("Folder game dipilih")
        assetScanReport = "Folder dipilih. Jalankan inventaris untuk memeriksa file game."
        getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
            .putString("selected_folder_name", selectedFolderName)
            .putString("selected_folder_uri", uri.toString())
            .putString("asset_scan_report", assetScanReport)
            .apply()
        showHome()
    }

    private fun scanGameFolder() {
        val folderUri = selectedFolderUri
        if (folderUri == null) {
            pickGameFolder()
            return
        }
        if (scanInProgress) return

        scanInProgress = true
        assetScanReport = "Sedang memeriksa daftar file dan signature awal..."
        showHome()

        Thread {
            val result = GameAssetScanner.scan(contentResolver, folderUri)
            runOnUiThread {
                scanInProgress = false
                assetScanReport = result.report
                getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
                    .putString("asset_scan_report", assetScanReport)
                    .apply()
                showHome()
            }
        }.start()
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            21 -> handleGameFile(uri, data)
            22 -> handleGameFolder(uri, data)
            23 -> handleXfbin(uri, data)
            24 -> handleCpk(uri, data)
        }
    }

    private fun showEngineNotice() {
        AlertDialog.Builder(this)
            .setTitle("Engine game native belum tersedia")
            .setMessage(
                if (selectedGameUri == null)
                    "Pilih file executable game (.exe) terlebih dahulu. Preview Vulkan 3D hanya menguji mesin dasar; belum ada engine game Naruto native Android."
                else
                    "$selectedName sudah dipilih dan tersimpan. File EXE Windows tidak dapat dijalankan langsung oleh Android. Port ARM64 native masih diperlukan."
            )
            .setPositiveButton("MENGERTI", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
