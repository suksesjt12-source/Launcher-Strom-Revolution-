package com.stormrevolution.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
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
    private var selectedFolderUri: Uri? = null
    private var selectedFolderName = "Folder game belum dipilih"
    private var assetScanReport = "Pilih folder instalasi game untuk mulai inventaris aset."
    private var scanInProgress = false
    private var battleMode = false
    private var hudText: TextView? = null
    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudUpdater = object : Runnable {
        override fun run() {
            val target = hudText ?: return
            target.text = StormEngine.getEngineReport()
            hudHandler.postDelayed(this, 500L)
        }
    }

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
            addView(label("NARUTO SHIPPUDEN", 11, orange, true))
            addView(label("ULTIMATE NINJA STORM REVOLUTION", 23, white, true))
            addView(label("Pemindai folder instalasi dan pengimpor aset otomatis.", 14, muted))
        })
        content.addView(card {
            addView(label("TARGET GAME", 12, orange, true))
            addView(label("Naruto Shippuden: Ultimate Ninja Storm Revolution", 15, white, true))
            addView(label("Pilih folder instalasi game saja. APK akan mencari CPK dan XFBIN di semua subfolder yang dapat dibaca.", 12, muted))
        })
        content.addView(card {
            addView(label("FOLDER INSTALASI GAME", 12, orange, true))
            addView(label(selectedFolderName, 14, white, true))
            addView(action("PILIH FOLDER GAME") { pickGameFolder() })
            addView(action(if (scanInProgress) "MEMINDAI & MENGIMPOR..." else "ULANGI PEMINDAIAN + IMPOR") {
                scanGameFolder()
            })
            addView(label(assetScanReport, 11, muted))
        })
        content.addView(card {
            addView(label("STATUS PROYEK", 12, orange, true))
            addView(label("Pencarian folder: otomatis hingga subfolder yang diizinkan Android.", 13, white))
            addView(label("Impor aset: CPK bernama-TOC, CRILAYLA, XFBIN, NUD, dan NUT yang didukung.", 13, white))
            addView(label("Runtime game native ARM64: belum tersedia. APK ini belum menjalankan NSUNSR.exe atau game retail.", 13, muted))
        })
        show(content)
    }

    private fun showBattleScreen() {
        battleMode = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        renderBattleContent()
    }

    private fun renderBattleContent() {
        hudHandler.removeCallbacks(hudUpdater)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 11, 18))
            setPadding(dp(6), dp(2), dp(6), dp(3))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("STORM COMBAT · 1 VS CPU", 12, orange, true),
            LinearLayout.LayoutParams(dp(150), -1))
        val status = TextView(this).apply {
            text = "Memulai simulasi..."
            textSize = 8f
            maxLines = 3
            setTextColor(white)
            gravity = Gravity.CENTER_VERTICAL
        }
        hudText = status
        top.addView(status, LinearLayout.LayoutParams(0, -1, 1f))
        val reset = Button(this).apply {
            text = "ULANGI"
            textSize = 9f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(Color.rgb(55, 63, 78))
            setOnClickListener { StormEngine.resetGame() }
        }
        top.addView(reset, LinearLayout.LayoutParams(dp(64), dp(34)))
        val back = Button(this).apply {
            text = "KELUAR"
            textSize = 9f
            isAllCaps = false
            setTextColor(Color.BLACK)
            background = rounded(orange, 8)
            setOnClickListener { closeBattle() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(64), dp(34)))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(36)))

        val scene = NativeVulkanSurfaceView(this)
        root.addView(scene, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(fightButton("LOMPAT", 1101, 0.12f),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(fightButton("SERANG", 1102, 0.37f),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(fightButton("BERTAHAN", 1103, 0.62f),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(fightButton("JURUS", 1104, 0.87f),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        hudHandler.post(hudUpdater)
    }

    private fun fightButton(title: String, pointerId: Int, zoneY: Float): Button =
        Button(this).apply {
            text = title
            textSize = 10f
            isAllCaps = false
            setTextColor(white)
            background = rounded(Color.rgb(36, 46, 64), 10)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                        StormEngine.sendTouch(pointerId, 0.84f, zoneY, MotionEvent.ACTION_DOWN)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                        StormEngine.sendTouch(pointerId, 0.84f, zoneY, MotionEvent.ACTION_UP)
                    MotionEvent.ACTION_CANCEL ->
                        StormEngine.sendTouch(pointerId, 0.84f, zoneY, MotionEvent.ACTION_CANCEL)
                }
                true
            }
        }

    private fun closeBattle() {
        battleMode = false
        hudHandler.removeCallbacks(hudUpdater)
        hudText = null
        StormEngine.stopRenderer()
        window.decorView.systemUiVisibility = 0
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        buildShell()
        showHome()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (battleMode) renderBattleContent()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (battleMode) closeBattle() else super.onBackPressed()
    }

    private fun showLibrary() {
        val content = page()
        content.addView(label("FOLDER GAME", 24, white, true))
        content.addView(label("Satu folder instalasi menjadi sumber pemindaian dan impor aset.", 13, muted))
        content.addView(card {
            addView(label("Folder terpilih", 12, orange, true))
            addView(label(selectedFolderName, 14, white, true))
            addView(action("PILIH FOLDER INSTALASI") { pickGameFolder() })
            addView(action(if (scanInProgress) "MEMINDAI & MENGIMPOR..." else "IMPOR ASET OTOMATIS") {
                scanGameFolder()
            })
            addView(label(assetScanReport, 11, muted))
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
            addView(label("Versi 0.13.0 · Automatic Folder Asset Import", 12, orange, true))
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
        selectedFolderName = preferences.getString("selected_folder_name", null)
            ?: "Folder game belum dipilih"
        selectedFolderUri = preferences.getString("selected_folder_uri", null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        assetScanReport = preferences.getString("asset_scan_report", null)
            ?: "Pilih folder instalasi game untuk mulai inventaris aset."
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
        assetScanReport = "Folder dipilih. Pemindaian dan impor aset dimulai otomatis."
        getSharedPreferences("storm_launcher", MODE_PRIVATE).edit()
            .putString("selected_folder_name", selectedFolderName)
            .putString("selected_folder_uri", uri.toString())
            .putString("asset_scan_report", assetScanReport)
            .apply()
        showHome()
        scanGameFolder()
    }

    private fun scanGameFolder() {
        val folderUri = selectedFolderUri
        if (folderUri == null) {
            pickGameFolder()
            return
        }
        if (scanInProgress) return

        scanInProgress = true
        assetScanReport = "Memindai subfolder, mengekstrak XFBIN dari CPK, lalu mengambil payload NUD/NUT..."
        showHome()

        Thread {
            val result = GameAssetScanner.scan(contentResolver, folderUri, filesDir)
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
        if (requestCode == 22) handleGameFolder(uri, data)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
