package com.stormlauncher.nsunsr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class GameZipImporterTest {
    @Test
    fun acceptsNormalGamePaths() {
        assertTrue(GameZipImporter.isSafeEntryPath("Naruto/data_win32/archive.cpk"))
        assertTrue(GameZipImporter.isSafeEntryPath("NSUNSR.exe"))
        assertTrue(GameZipImporter.isSafeEntryPath("data_win32/"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertFalse(GameZipImporter.isSafeEntryPath("../NSUNSR.exe"))
        assertFalse(GameZipImporter.isSafeEntryPath("Naruto/../../outside.txt"))
        assertFalse(GameZipImporter.isSafeEntryPath("/system/file"))
        assertFalse(GameZipImporter.isSafeEntryPath("C:\\Windows\\NSUNSR.exe"))
        assertFalse(GameZipImporter.isSafeEntryPath("folder/\u0000payload"))
    }

    @Test
    fun validatesWindowsPeHeader() {
        val file = File.createTempFile("storm-pe-test", ".exe")
        try {
            val bytes = ByteArray(0x44)
            bytes[0] = 0x4D
            bytes[1] = 0x5A
            bytes[0x3C] = 0x40
            bytes[0x40] = 0x50
            bytes[0x41] = 0x45
            bytes[0x42] = 0x00
            bytes[0x43] = 0x00
            FileOutputStream(file).use { it.write(bytes) }
            assertTrue(GameZipImporter.isPortableExecutable(file))

            bytes[0] = 0x00
            FileOutputStream(file).use { it.write(bytes) }
            assertFalse(GameZipImporter.isPortableExecutable(file))
        } finally {
            file.delete()
        }
    }
}
