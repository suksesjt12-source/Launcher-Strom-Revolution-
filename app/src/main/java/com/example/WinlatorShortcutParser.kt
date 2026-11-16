package com.example

import java.io.File

data class WinlatorShortcut(
    val containerId: Int,
    val name: String,
    val fileName: String,
    val absolutePath: String
)

object WinlatorShortcutParser {
    fun parse(
        desktopText: String,
        documentId: String,
        primaryStorageRoot: String
    ): WinlatorShortcut {
        require(documentId.startsWith("primary:")) {
            "Pilih shortcut dari penyimpanan internal bersama, bukan kartu SD atau penyedia cloud."
        }

        val relativePath = documentId.removePrefix("primary:")
        require(relativePath.isNotBlank() && !relativePath.startsWith("/")) {
            "Lokasi file shortcut tidak valid."
        }

        val pathSegments = relativePath.split("/")
        require(pathSegments.none { it.isBlank() || it == "." || it == ".." }) {
            "Lokasi file shortcut tidak aman atau tidak valid."
        }
        require(relativePath.substringAfterLast("/").endsWith(".desktop", ignoreCase = true)) {
            "Pilih file shortcut Winlator dengan ekstensi .desktop."
        }

        var inDesktopEntry = false
        var shortcutName: String? = null
        var containerId: Int? = null

        desktopText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                inDesktopEntry = line.equals("[Desktop Entry]", ignoreCase = true)
            } else {
                if (inDesktopEntry && line.startsWith("Name=") && shortcutName == null) {
                    shortcutName = line.substringAfter("=").trim()
                }
                if (line.startsWith("container_id:")) {
                    containerId = line.substringAfter(":").trim().toIntOrNull()
                }
            }
        }

        require(containerId != null && containerId!! > 0) {
            "Container ID tidak ditemukan. Ekspor ulang shortcut dari Winlator."
        }

        val fileName = relativePath.substringAfterLast("/")
        val fallbackName = fileName.removeSuffix(".desktop")
        val name = shortcutName?.takeIf { it.isNotBlank() } ?: fallbackName

        val root = File(primaryStorageRoot).canonicalFile
        val selectedFile = File(root, relativePath).canonicalFile
        require(selectedFile.path.startsWith(root.path + File.separator)) {
            "Path shortcut berada di luar penyimpanan bersama."
        }

        return WinlatorShortcut(
            containerId = containerId!!,
            name = name,
            fileName = fileName,
            absolutePath = selectedFile.path
        )
    }
}
