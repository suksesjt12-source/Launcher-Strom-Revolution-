package com.stormrevolution.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CriLaylaDecoderTest {
    @Test
    fun decodesLiteralOnlyStreamAndRestoresRawHeader() {
        val original = byteArrayOf(0x41, 0x42, 0x43)
        val compressed = literalCrilayla(original)
        val decoded = CriLaylaDecoder.decode(compressed, 0x103L)

        assertEquals(0x103, decoded.size)
        assertArrayEquals(ByteArray(0x100), decoded.copyOfRange(0, 0x100))
        assertArrayEquals(original, decoded.copyOfRange(0x100, decoded.size))
    }

    @Test
    fun rejectsMismatchedCpkExtractSize() {
        val compressed = literalCrilayla(byteArrayOf(0x41))
        assertThrows(IllegalArgumentException::class.java) {
            CriLaylaDecoder.decode(compressed, 0x999L)
        }
    }

    @Test
    fun rejectsTruncatedCompressedMember() {
        val compressed = literalCrilayla(byteArrayOf(0x41))
        assertThrows(IllegalArgumentException::class.java) {
            CriLaylaDecoder.decode(compressed.copyOf(compressed.size - 1))
        }
    }

    private fun literalCrilayla(original: ByteArray): ByteArray {
        val bits = StringBuilder()
        original.reversedArray().forEach { byte ->
            bits.append('0')
            bits.append((byte.toInt() and 0xFF).toString(2).padStart(8, '0'))
        }
        while (bits.length % 8 != 0) bits.append('0')

        val packed = ByteArray(bits.length / 8)
        for (i in packed.indices) {
            packed[i] = bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }
        val stream = packed.reversedArray()
        val output = ByteArray(0x10 + stream.size + 0x100)
        "CRILAYLA".toByteArray(Charsets.US_ASCII).copyInto(output, 0)
        putU32le(output, 8, original.size)
        putU32le(output, 12, stream.size)
        stream.copyInto(output, 0x10)
        return output
    }

    private fun putU32le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
