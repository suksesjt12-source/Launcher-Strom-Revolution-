package com.stormrevolution.launcher

/**
 * Bounded CRILAYLA decoder for individual CRI CPK entries.
 *
 * The 0x100-byte file header is stored verbatim after the compressed stream.
 * The LZ stream is decoded backwards at the byte level, then the decoded body
 * is reversed into its original byte order.
 */
object CriLaylaDecoder {
    private const val HEADER_SIZE = 0x10
    private const val PREFIX_SIZE = 0x100
    private const val MIN_FILE_SIZE = HEADER_SIZE + PREFIX_SIZE
    const val DEFAULT_MAX_OUTPUT_BYTES = 128 * 1024 * 1024

    fun isCompressed(data: ByteArray): Boolean =
        data.size >= 8 && String(data, 0, 8, Charsets.US_ASCII) == "CRILAYLA"

    fun decode(
        data: ByteArray,
        expectedOutputSize: Long? = null,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES
    ): ByteArray {
        require(isCompressed(data)) { "Signature CRILAYLA tidak ditemukan." }
        require(data.size >= MIN_FILE_SIZE) { "Data CRILAYLA terlalu pendek." }
        require(maxOutputBytes >= PREFIX_SIZE)

        val bodySize = u32le(data, 8)
        val compressedSize = u32le(data, 12)
        require(bodySize > 0L) { "Ukuran data hasil dekompresi kosong." }
        require(bodySize <= (maxOutputBytes - PREFIX_SIZE).toLong()) {
            "Ukuran hasil melebihi batas memori decoder."
        }
        require(compressedSize <= Int.MAX_VALUE.toLong())
        require(HEADER_SIZE.toLong() + compressedSize + PREFIX_SIZE == data.size.toLong()) {
            "Ukuran blok CRILAYLA tidak cocok dengan ukuran entri."
        }

        val outputSize = bodySize + PREFIX_SIZE
        if (expectedOutputSize != null && expectedOutputSize > 0L) {
            require(outputSize == expectedOutputSize) {
                "Ukuran hasil CRILAYLA berbeda dari ExtractSize CPK."
            }
        }

        val compressedStart = HEADER_SIZE
        val compressedEnd = HEADER_SIZE + compressedSize.toInt()
        val rawHeaderStart = compressedEnd
        val body = ByteArray(bodySize.toInt())
        val bits = ReverseBitReader(data, compressedStart, compressedEnd)
        val vleWidths = intArrayOf(2, 3, 5, 8)
        var produced = 0

        while (produced < body.size) {
            if (bits.read(1) != 0) {
                val distance = bits.read(13) + 3
                var length = 3
                var fullyExtended = true

                for (width in vleWidths) {
                    val value = bits.read(width)
                    length += value
                    if (value != ((1 shl width) - 1)) {
                        fullyExtended = false
                        break
                    }
                }

                if (fullyExtended) {
                    while (true) {
                        val value = bits.read(8)
                        length += value
                        if (value != 0xFF) break
                    }
                }

                require(length > 0 && length <= body.size - produced) {
                    "Panjang back-reference melewati ukuran hasil."
                }
                var source = produced - distance
                require(source >= 0) {
                    "Back-reference menunjuk sebelum awal data hasil."
                }

                repeat(length) {
                    require(source < produced) {
                        "Back-reference menunjuk ke byte yang belum didekode."
                    }
                    body[produced] = body[source]
                    produced++
                    source++
                }
            } else {
                body[produced++] = bits.read(8).toByte()
            }
        }

        val output = ByteArray(outputSize.toInt())
        data.copyInto(output, 0, rawHeaderStart, rawHeaderStart + PREFIX_SIZE)
        for (index in body.indices) {
            output[PREFIX_SIZE + index] = body[body.lastIndex - index]
        }
        return output
    }

    private class ReverseBitReader(
        private val source: ByteArray,
        private val start: Int,
        endExclusive: Int
    ) {
        private var cursor = endExclusive - 1
        private var pool = 0
        private var bitsLeft = 0

        fun read(count: Int): Int {
            require(count in 1..16)
            var result = 0
            var produced = 0

            while (produced < count) {
                if (bitsLeft == 0) {
                    require(cursor >= start) { "Bitstream CRILAYLA berakhir terlalu awal." }
                    pool = source[cursor].toInt() and 0xFF
                    cursor--
                    bitsLeft = 8
                }

                val take = minOf(bitsLeft, count - produced)
                val mask = (1 shl take) - 1
                result = (result shl take) or ((pool ushr (bitsLeft - take)) and mask)
                bitsLeft -= take
                produced += take
            }
            return result
        }
    }

    private fun u32le(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)
}
