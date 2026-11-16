package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeInspectorTest {
    @Test
    fun detectsPe32IntelX86() {
        val bytes = ByteArray(512)
        bytes[0] = 0x4d
        bytes[1] = 0x5a
        bytes[0x3c] = 0x80.toByte()
        bytes[0x80] = 0x50
        bytes[0x81] = 0x45
        bytes[0x82] = 0
        bytes[0x83] = 0
        bytes[0x84] = 0x4c
        bytes[0x85] = 0x01
        bytes[0x98] = 0x0b
        bytes[0x99] = 0x01

        val result = PeInspector.inspect(bytes)

        assertTrue(result.isPeImage)
        assertEquals("PE32 / Intel x86 (Windows 32-bit)", result.description)
    }

    @Test
    fun rejectsNonPeInput() {
        val result = PeInspector.inspect("not an exe".toByteArray())

        assertFalse(result.hasMzHeader)
        assertFalse(result.isPeImage)
    }

    @Test
    fun handlesTruncatedMzHeader() {
        val result = PeInspector.inspect(byteArrayOf(0x4d, 0x5a))

        assertTrue(result.hasMzHeader)
        assertFalse(result.hasPeHeader)
    }
}
