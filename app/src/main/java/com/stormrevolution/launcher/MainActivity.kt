package com.stormrevolution.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 13, 18)
    private val panel = Color.rgb(22, 27, 38)
    private val orange = Color.rgb(255, 122, 26)
    private val white = Color.rgb(245, 246, 248)
    private val muted = Color.rgb(157, 166, 181)
    private lateinit var pageHost: FrameLayout
    private var selectedName = "Belum ada file dipilih"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
        showHome()
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
            addView(label("Launcher Android mandiri dengan tampilan khusus.", 14, muted))
        })
        content.addView(card {
            addView(label("GAME TERPILIH", 12, orange, true))
            addView(label(selectedName, 15, white, true))
            addView(action("PILIH FILE NSUNSR.EXE") { pickExe() })
            addView(action("MAIN SEKARANG") { showEngineNotice() })
        })
        content.addView(card {
            addView(label("STATUS PROYEK", 12, orange, true))
            addView(label("Launcher: aktif", 14, white))
            addView(label("Vulkan native bootstrap: terpasang", 13, white))
            addView(label("Engine game native ARM64: belum tersedia", 13, muted))
            addView(label("Tidak ada Wine, Box64, DXVK, atau Winlator.", 12, muted))
        })
        content.addView(card {
            addView(label("DIAGNOSTIK GPU VULKAN", 12, orange, true))
            addView(label(StormEngine.getVulkanReport(), 12, white))
            addView(label("Ini memeriksa driver Vulkan perangkat. Modul ini belum menjalankan NSUNSR.exe atau merender game.", 11, muted))
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
            addView(label("Engine native belum terpasang. Opsi grafis game belum aktif.", 13, muted))
            addView(label("Launcher ini tidak memuat runtime Windows.", 12, muted))
        })
        show(content)
    }

    private fun showAbout() {
        val content = page()
        content.addView(label("TENTANG", 24, white, true))
        content.addView(card {
            addView(label("STORM REVOLUTION LAUNCHER", 17, white, true))
            addView(label("Versi 0.1.0 · Native Android", 12, orange, true))
            addView(label("Aplikasi launcher mandiri berbasis Kotlin. Proyek ini belum memuat game atau engine untuk menjalankan EXE Windows.", 13, muted))
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

    private fun pickExe() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 21)
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val name = uri.lastPathSegment ?: "File dipilih"
            if (name.endsWith(".exe", ignoreCase = true)) {
                selectedName = name.substringAfterLast('/')
                showHome()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("File bukan EXE")
                    .setMessage("Pilih NSUNSR.exe dari folder game Windows.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showEngineNotice() {
        AlertDialog.Builder(this)
            .setTitle("Engine native belum tersedia")
            .setMessage(
                if (selectedName == "Belum ada file dipilih")
                    "Pilih NSUNSR.exe terlebih dahulu. Launcher ini belum memiliki engine game native Android."
                else
                    "$selectedName sudah dipilih. File EXE Windows tidak dapat dijalankan langsung oleh Android. Port ARM64 native masih diperlukan."
            )
            .setPositiveButton("MENGERTI", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
