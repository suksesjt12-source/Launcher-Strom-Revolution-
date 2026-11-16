package com.example

import org.junit.Assert.assertEquals
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
    fun parsesExportedWinlatorShortcut() {
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
                "primary:../NSUNSR.desktop",
                "/storage/emulated/0"
            )
        }
    }

    @Test
    fun rejectsShortcutWithoutContainerId() {
        assertThrows(IllegalArgumentException::class.java) {
            WinlatorShortcutParser.parse(
                "[Desktop Entry]\nName=Game",
                "primary:Winlator/Shortcuts/Game.desktop",
                "/storage/emulated/0"
            )
        }
    }
}
