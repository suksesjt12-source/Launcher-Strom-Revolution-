package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class WinlatorShortcutParserTest {
    private val desktopText = """
        [Desktop Entry]
        Name=Naruto Storm Revolution
        Exec=wine /storage/emulated/0/Windows/NSUNSR.exe
        Type=Application
        container_id:12
    """.trimIndent()

    @Test
    fun parsesExportedWinlatorShortcutWithContainerId() {
        val root = File("/storage/emulated/0")
        val result = WinlatorShortcutParser.parse(
            desktopText = desktopText,
            documentId = "primary:Winlator/Shortcuts/NSUNSR.desktop",
            primaryStorageRoot = root.path
        )

        assertEquals(12, result.containerId)
        assertEquals("Naruto Storm Revolution", result.name)
        assertEquals("NSUNSR.desktop", result.fileName)
        assertEquals(
            File(root, "Winlator/Shortcuts/NSUNSR.desktop").canonicalPath,
            result.absolutePath
        )
    }

    @Test
    fun acceptsDesktopShortcutWithoutContainerIdForManualEntry() {
        val result = WinlatorShortcutParser.parse(
            desktopText = "[Desktop Entry]\nName=Naruto",
            documentId = "primary:Winlator/Shortcuts/Naruto.desktop",
            primaryStorageRoot = "/storage/emulated/0"
        )

        assertNull(result.containerId)
        assertEquals("Naruto", result.name)
    }

    @Test
    fun acceptsDirectExeSelection() {
        val result = WinlatorShortcutParser.parse(
            desktopText = null,
            documentId = "primary:Games/NSUNSR.exe",
            primaryStorageRoot = "/storage/emulated/0"
        )

        assertNull(result.containerId)
        assertEquals("NSUNSR", result.name)
        assertEquals("NSUNSR.exe", result.fileName)
    }

    @Test
    fun rejectsNonPrimaryStorageDocument() {
        assertThrows(IllegalArgumentException::class.java) {
            WinlatorShortcutParser.parse(
                desktopText,
                "ABCD-1234:Winlator/Shortcuts/NSUNSR.desktop",
                "/storage/emulated/0"
            )
        }
    }

    @Test
    fun rejectsPathTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            WinlatorShortcutParser.parse(
                desktopText,
                "primary:../NSUNSR.exe",
                "/storage/emulated/0"
            )
        }
    }

    @Test
    fun rejectsOtherFileTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            WinlatorShortcutParser.parse(
                null,
                "primary:Games/readme.txt",
                "/storage/emulated/0"
            )
        }
    }
}
