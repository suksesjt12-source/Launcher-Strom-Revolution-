package com.stormrevolution.launcher

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.Locale

/**
 * Read-only metadata parser for CRI CPK/UTF containers and NUCC/XFBIN indexes.
 *
 * It intentionally does not extract payloads, decrypt game assets, or modify
 * user files. Each parser validates every offset/length before reading.
 */
object RetailContainerInspector {
    private const val MAX_UTF_TABLE_BYTES = 16 * 1024 * 1024
    private const val MAX_CPK_FRAME_SCAN = 8L * 1024 * 1024
    private const val MAX_CPK_SAMPLE_ROWS = 12
    private const val MAX_XFBIN_CHUNKS = 128
    private const val MAX_STRING_BYTES = 4096

    data class Report(
        val title: String,
        val details: String,
        val recognized: Boolean
    )

    private data class UtfColumn(
        val name: String,
        val storage: Int,
        val type: Int,
        val constantValue: Any? = null
    )

    private data class UtfTable(
        val name: String,
        val columns: List<UtfColumn>,
        val rowCount: Long,
        val rowWidth: Int,
        val rows: List<Map<String, Any?>>
    )

    fun inspect(
        resolver: ContentResolver,
        uri: Uri,
        displayName: String,
        extension: String
    ): Report {
        val normalized = extension.lowercase(Locale.ROOT)
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: return Report(displayName, "File descriptor tidak tersedia.", false)
            descriptor.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    when (normalized) {
                        "cpk" -> inspectCpk(channel, displayName)
                        "xfbin" -> inspectXfbin(channel, displayName)
                        else -> Report(displayName, "Format belum didukung oleh inspector.", false)
                    }
                }
            }
        } catch (error: Exception) {
            Report(
                displayName,
                "Gagal membaca metadata: ${error.javaClass.simpleName}: ${error.message ?: "kesalahan I/O"}",
                false
            )
        }
    }

    private fun inspectCpk(channel: FileChannel, displayName: String): Report {
        val fileSize = channel.size()
        if (fileSize < 32L) {
            return Report(displayName, "File terlalu kecil untuk menjadi CPK.", false)
        }

        val firstHeader = readBytes(channel, 0L, 16)
        val marker = ascii(firstHeader, 0, 4)
        if (!marker.startsWith("CPK")) {
            val utfLocation = findUtfMarker(channel, minOf(fileSize, 4096L))
            return Report(
                displayName,
                if (utfLocation >= 0L) {
                    "Ditemukan @UTF pada offset 0x${utfLocation.toString(16)}, tetapi header CPK tidak ditemukan di offset 0. Format mungkin berupa UTF mandiri atau varian container."
                } else {
                    "Signature CPK tidak ditemukan pada awal file. Header: ${hex(firstHeader, 16)}"
                },
                false
            )
        }

        val cpkPayloadLength = u32le(firstHeader, 4)
        val cpkPayloadStart = 16L
        if (cpkPayloadLength <= 0L ||
            cpkPayloadStart + cpkPayloadLength > fileSize) {
            return Report(
                displayName,
                "Header CPK dikenali, tetapi ukuran frame utama tidak valid (payload=$cpkPayloadLength, file=$fileSize).",
                false
            )
        }

        val cpkTable = parseUtfAt(channel, cpkPayloadStart, cpkPayloadLength)
        if (cpkTable == null) {
            return Report(
                displayName,
                "Frame CPK ditemukan. Tabel @UTF tidak dapat diparse (mungkin terenkripsi/varian atau layout berbeda).",
                true
            )
        }

        val output = StringBuilder()
        output.append("CRI CPK / UTF dikenali\n")
        output.append("Ukuran arsip: ").append(formatBytes(fileSize)).append('\n')
        output.append("Tabel header: ").append(cpkTable.name).append('\n')
        output.append("Kolom header: ")
            .append(cpkTable.columns.joinToString(", ") { it.name })
            .append('\n')

        val headerRow = cpkTable.rows.firstOrNull().orEmpty()
        val fileCount = headerRow["Files"]
        val contentOffset = headerRow["ContentOffset"]
        val tocOffset = headerRow["TocOffset"]
        if (fileCount != null) output.append("Jumlah file tercatat: ").append(fileCount).append('\n')
        if (contentOffset != null) output.append("ContentOffset: ").append(contentOffset).append('\n')
        if (tocOffset != null) output.append("TocOffset: ").append(tocOffset).append('\n')

        val tocTable = findCpkToc(channel, cpkPayloadStart + cpkPayloadLength, fileSize)
        if (tocTable != null) {
            output.append("\nTabel TOC: ").append(tocTable.name).append('\n')
            output.append("Entri pada TOC: ").append(tocTable.rowCount).append('\n')
            output.append("Kolom penting: ")
                .append(tocTable.columns.filter {
                    it.name in setOf("DirName", "FileName", "FileSize", "ExtractSize", "FileOffset")
                }.joinToString(", ") { it.name })
                .append('\n')
            output.append("Contoh entri:\n")
            for ((index, row) in tocTable.rows.withIndex()) {
                val directory = row["DirName"]?.toString().orEmpty()
                val name = row["FileName"]?.toString().orEmpty()
                val path = listOf(directory.trim('/'), name.trim('/'))
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                val size = row["FileSize"]?.toString() ?: "?"
                val unpacked = row["ExtractSize"]?.toString() ?: "?"
                val offset = row["FileOffset"]?.toString() ?: "?"
                output.append(index + 1).append(". ")
                    .append(path.ifBlank { "(nama tidak tersedia)" })
                    .append(" | file=").append(size)
                    .append(" | extract=").append(unpacked)
                    .append(" | offset=").append(offset)
                    .append('\n')
            }
            if (tocTable.rowCount > tocTable.rows.size) {
                output.append("... menampilkan ")
                    .append(tocTable.rows.size)
                    .append(" dari ")
                    .append(tocTable.rowCount)
                    .append(" entri.\n")
            }
        } else {
            output.append("\nFrame TOC tidak ditemukan dalam area metadata awal arsip.\n")
        }

        output.append("\nCatatan: parser ini baru membaca indeks metadata. ")
            .append("Data CRILAYLA dan payload XFBIN belum diekstrak.")
        return Report(displayName, output.toString(), true)
    }

    private fun findCpkToc(
        channel: FileChannel,
        afterCpkFrame: Long,
        fileSize: Long
    ): UtfTable? {
        var offset = align16(afterCpkFrame)
        val scanEnd = minOf(fileSize, MAX_CPK_FRAME_SCAN)
        var framesVisited = 0

        while (offset + 16L <= scanEnd && framesVisited++ < 512) {
            val header = readBytes(channel, offset, 16)
            if (header.all { it == 0.toByte() }) {
                offset += 16L
                continue
            }

            val signature = ascii(header, 0, 4)
            when {
                signature.startsWith("TOC") ||
                    signature == "ITOC" ||
                    signature == "ETOC" -> {
                    val length = u32le(header, 4)
                    if (length <= 0L || offset + 16L + length > fileSize) return null
                    val table = parseUtfAt(channel, offset + 16L, length)
                    if (signature.startsWith("TOC")) return table
                    offset = align16(offset + 16L + length)
                }

                signature.startsWith("CPK") -> {
                    val length = u32le(header, 4)
                    if (length <= 0L || offset + 16L + length > fileSize) return null
                    offset = align16(offset + 16L + length)
                }

                signature.startsWith("CRILAYLA") -> {
                    val compressedLength = u32le(header, 12)
                    val next = offset + 16L + compressedLength + 0x100L
                    if (compressedLength <= 0L || next > fileSize) return null
                    offset = align16(next)
                }

                ascii(header, 10, 6) == "(c)CRI" ||
                    header.copyOfRange(0, 4).contentEquals(
                        byteArrayOf(0xE5.toByte(), 0x56, 0xD1.toByte(), 0x9D.toByte())
                    ) -> offset += 16L

                else -> return null
            }
        }
        return null
    }

    private fun inspectXfbin(channel: FileChannel, displayName: String): Report {
        val fileSize = channel.size()
        if (fileSize < 0x1CL) {
            return Report(displayName, "File terlalu kecil untuk XFBIN.", false)
        }

        val fixedHeader = readBytes(channel, 0L, 0x1CL)
        if (ascii(fixedHeader, 0, 4) != "NUCC") {
            return Report(
                displayName,
                "Signature NUCC tidak ditemukan. Header: ${hex(fixedHeader, 16)}",
                false
            )
        }

        val header = ByteBuffer.wrap(fixedHeader).order(ByteOrder.BIG_ENDIAN)
        val version = unsignedInt(header, 4)
        val language = ascii(fixedHeader, 10, 2)
        val indexPayloadSize = unsignedInt(header, 16)
        val indexMapIndex = unsignedInt(header, 20)
        val indexVersion = unsignedShort(header, 24)
        val indexUnknown = unsignedShort(header, 26)
        val indexPayloadStart = 0x1CL
        if (indexPayloadSize < 40L ||
            indexPayloadSize > fileSize - indexPayloadStart ||
            indexPayloadSize > MAX_UTF_TABLE_BYTES.toLong()) {
            return Report(
                displayName,
                "Header NUCC dikenali, tetapi ukuran index chunk tidak valid: $indexPayloadSize byte.",
                false
            )
        }

        val payload = readBytes(channel, indexPayloadStart, indexPayloadSize.toInt())
        val index = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val typeCount = unsignedInt(index, 0)
        val typeSize = unsignedInt(index, 4)
        val pathCount = unsignedInt(index, 8)
        val pathSize = unsignedInt(index, 12)
        val nameCount = unsignedInt(index, 16)
        val nameSize = unsignedInt(index, 20)
        val mapCount = unsignedInt(index, 24)
        val mapSize = unsignedInt(index, 28)
        val mapIndicesCount = unsignedInt(index, 32)
        val extraMapCount = unsignedInt(index, 36)

        val stringBytes = typeSize + pathSize + nameSize
        if (listOf(typeCount, typeSize, pathCount, pathSize, nameCount, nameSize,
                mapCount, mapSize, mapIndicesCount, extraMapCount).any { it > 1_000_000L } ||
            stringBytes > indexPayloadSize - 40L) {
            return Report(
                displayName,
                "Header NUCC ditemukan, tetapi ukuran tabel index tidak masuk akal. "
                    + "Parser menghentikan pembacaan untuk melindungi file.",
                true
            )
        }

        var cursor = 40L
        val typeStrings = readNullStrings(payload, cursor.toInt(), typeSize.toInt())
        cursor += typeSize
        val pathStrings = readNullStrings(payload, cursor.toInt(), pathSize.toInt())
        cursor += pathSize
        val nameStrings = readNullStrings(payload, cursor.toInt(), nameSize.toInt())
        cursor += nameSize
        cursor = align4(cursor)

        val requiredMapBytes = mapCount * 12L +
            extraMapCount * 8L +
            mapIndicesCount * 4L
        val indexTablesFit = cursor + requiredMapBytes <= indexPayloadSize

        val output = StringBuilder()
        output.append("NUCC / XFBIN dikenali\n")
        output.append("Ukuran file: ").append(formatBytes(fileSize)).append('\n')
        output.append("Versi XFBIN: ").append(version).append('\n')
        output.append("Bahasa header: ").append(language).append('\n')
        output.append("Index chunk: versi ").append(indexVersion)
            .append(", mapIndex=").append(indexMapIndex)
            .append(", unknown=").append(indexUnknown).append('\n')
        output.append("Jenis chunk: ").append(typeCount)
            .append(" (string ditemukan ").append(typeStrings.size).append(")\n")
        output.append("File path: ").append(pathCount)
            .append(" (string ditemukan ").append(pathStrings.size).append(")\n")
        output.append("Nama chunk: ").append(nameCount)
            .append(" (string ditemukan ").append(nameStrings.size).append(")\n")
        output.append("Peta chunk: ").append(mapCount)
            .append(" | ukuran tercatat=").append(mapSize).append('\n')
        output.append("Indeks peta: ").append(mapIndicesCount)
            .append(" | extra map=").append(extraMapCount).append('\n')
        output.append("Tabel index valid secara ukuran: ")
            .append(if (indexTablesFit) "ya" else "tidak / belum lengkap")
            .append('\n')
        if (typeStrings.isNotEmpty()) {
            output.append("Tipe chunk terdaftar:\n")
            typeStrings.take(24).forEach { output.append("- ").append(it).append('\n') }
        }

        val chunks = inspectXfbinChunkHeaders(
            channel,
            indexPayloadStart + indexPayloadSize,
            fileSize
        )
        output.append("Chunk setelah index yang dapat dibaca: ")
            .append(chunks.size).append('\n')
        chunks.take(24).forEachIndexed { i, chunk ->
            output.append(i + 1).append(". offset=0x")
                .append(chunk.first.toString(16))
                .append(" payload=").append(chunk.second)
                .append(" byte\n")
        }
        output.append("\nCatatan: index dan header payload diperiksa saja. ")
            .append("NUD/NUT/ANM belum diubah menjadi model, tekstur, atau animasi.")
        return Report(displayName, output.toString(), true)
    }

    private fun inspectXfbinChunkHeaders(
        channel: FileChannel,
        start: Long,
        fileSize: Long
    ): List<Pair<Long, Long>> {
        val chunks = ArrayList<Pair<Long, Long>>()
        var offset = start
        while (offset + 12L <= fileSize && chunks.size < MAX_XFBIN_CHUNKS) {
            val header = readBytes(channel, offset, 12)
            if (header.all { it == 0.toByte() }) break
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            val payloadSize = unsignedInt(buffer, 0)
            if (payloadSize > fileSize || offset + 12L + payloadSize > fileSize) break
            chunks.add(offset to payloadSize)
            val next = offset + 12L + payloadSize
            if (next <= offset) break
            offset = next
        }
        return chunks
    }

    private fun parseUtfAt(
        channel: FileChannel,
        payloadOffset: Long,
        payloadLength: Long
    ): UtfTable? {
        if (payloadLength < 8L) return null
        val firstEight = readBytes(channel, payloadOffset, 8)
        val encrypted = firstEight.copyOfRange(0, 4).contentEquals(
            byteArrayOf(0x1F, 0x9E.toByte(), 0xF3.toByte(), 0xF5.toByte())
        )
        val decodedHeader = if (encrypted) decryptUtf(firstEight) else firstEight
        if (ascii(decodedHeader, 0, 4) != "@UTF") return null

        val tableSize = u32be(decodedHeader, 4)
        val totalSize = 8L + tableSize
        if (tableSize < 0x18L ||
            totalSize > payloadLength ||
            totalSize > MAX_UTF_TABLE_BYTES.toLong() ||
            payloadOffset + totalSize > channel.size()) return null

        val raw = readBytes(channel, payloadOffset, totalSize.toInt())
        val data = if (encrypted) decryptUtf(raw) else raw
        if (ascii(data, 0, 4) != "@UTF") return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val bodyStart = 8
        val rowsOffset = unsignedInt(buffer, bodyStart)
        val stringTableOffset = unsignedInt(buffer, bodyStart + 4)
        val tableDataOffset = unsignedInt(buffer, bodyStart + 8)
        val tableNameOffset = unsignedInt(buffer, bodyStart + 12)
        val columnCount = unsignedShort(buffer, bodyStart + 16)
        val rowWidth = unsignedShort(buffer, bodyStart + 18)
        val rowCount = unsignedInt(buffer, bodyStart + 20)

        if (columnCount > 512 ||
            rowCount > 10_000_000L ||
            rowsOffset > tableSize ||
            stringTableOffset > tableSize ||
            tableDataOffset > tableSize ||
            rowsOffset < 0x18L ||
            stringTableOffset < rowsOffset) return null

        val stringStart = bodyStart.toLong() + stringTableOffset
        val stringEnd = bodyStart.toLong() + tableSize
        fun getString(offset: Long): String {
            val absolute = stringStart + offset
            if (absolute < stringStart || absolute >= stringEnd) return "<invalid-string-offset>"
            val maxLength = minOf(MAX_STRING_BYTES.toLong(), stringEnd - absolute).toInt()
            val bytes = data.copyOfRange(absolute.toInt(), absolute.toInt() + maxLength)
            val terminator = bytes.indexOf(0)
            val slice = if (terminator >= 0) bytes.copyOfRange(0, terminator) else bytes
            return decodeText(slice)
        }

        val tableName = getString(tableNameOffset)
        var schemaCursor = bodyStart + 0x18
        val columns = ArrayList<UtfColumn>(columnCount)
        repeat(columnCount) {
            if (schemaCursor + 5 > data.size) return null
            val typeId = data[schemaCursor].toInt() and 0xFF
            val nameOffset = u32be(data, schemaCursor + 1)
            schemaCursor += 5
            val storage = typeId and 0xF0
            val type = typeId and 0x0F
            val columnName = getString(nameOffset)
            val valueSize = typeSize(type) ?: return null
            var constantValue: Any? = null
            if (storage == 0x30) {
                if (schemaCursor + valueSize > data.size) return null
                constantValue = parseUtfValue(data, schemaCursor, type, ::getString)
                schemaCursor += valueSize
            } else if (storage != 0x50 && storage != 0x10) {
                return null
            }
            columns.add(UtfColumn(columnName, storage, type, constantValue))
        }

        if (schemaCursor.toLong() != bodyStart.toLong() + rowsOffset) {
            return null
        }

        var calculatedWidth = 0
        for (column in columns) {
            if (column.storage == 0x50) calculatedWidth += typeSize(column.type) ?: return null
        }
        if (calculatedWidth != rowWidth) return null

        val rows = ArrayList<Map<String, Any?>>()
        val displayedRows = minOf(rowCount, MAX_CPK_SAMPLE_ROWS.toLong()).toInt()
        for (rowIndex in 0 until displayedRows) {
            val rowOffset = bodyStart.toLong() + rowsOffset + rowIndex.toLong() * rowWidth
            if (rowOffset < 0L || rowOffset + rowWidth > bodyStart.toLong() + tableSize) return null
            var fieldOffset = rowOffset.toInt()
            val row = LinkedHashMap<String, Any?>()
            for (column in columns) {
                val value = when (column.storage) {
                    0x30 -> column.constantValue
                    0x10 -> null
                    0x50 -> {
                        val size = typeSize(column.type) ?: return null
                        val value = parseUtfValue(data, fieldOffset, column.type, ::getString)
                        fieldOffset += size
                        value
                    }
                    else -> return null
                }
                row[column.name] = value
            }
            if (fieldOffset.toLong() != rowOffset + rowWidth) return null
            rows.add(row)
        }

        return UtfTable(tableName, columns, rowCount, rowWidth, rows)
    }

    private fun typeSize(type: Int): Int? = when (type) {
        0x00, 0x01 -> 1
        0x02, 0x03 -> 2
        0x04, 0x05, 0x08, 0x0A -> 4
        0x06 -> 8
        0x0B -> 8
        else -> null
    }

    private fun parseUtfValue(
        data: ByteArray,
        offset: Int,
        type: Int,
        stringResolver: (Long) -> String
    ): Any? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return when (type) {
            0x00 -> data[offset].toInt() and 0xFF
            0x01 -> data[offset].toInt()
            0x02 -> buffer.getShort(offset).toInt() and 0xFFFF
            0x03 -> buffer.getShort(offset).toInt()
            0x04 -> unsignedInt(buffer, offset)
            0x05 -> buffer.getInt(offset)
            0x06 -> buffer.getLong(offset).toULong().toString()
            0x08 -> buffer.getFloat(offset)
            0x0A -> stringResolver(unsignedInt(buffer, offset))
            0x0B -> "offset=${unsignedInt(buffer, offset)},size=${unsignedInt(buffer, offset + 4)}"
            else -> null
        }
    }

    private fun decryptUtf(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        var key = 0x5F
        for (index in input.indices) {
            output[index] = (input[index].toInt() xor key).toByte()
            key = (key * 0x15) and 0xFF
        }
        return output
    }

    private fun findUtfMarker(channel: FileChannel, length: Long): Long {
        val bytes = readBytes(channel, 0L, length.toInt())
        for (i in 0..bytes.size - 4) {
            if (ascii(bytes, i, 4) == "@UTF") return i.toLong()
        }
        return -1L
    }

    private fun readNullStrings(data: ByteArray, start: Int, length: Int): List<String> {
        if (length <= 0 || start < 0 || start > data.size - length) return emptyList()
        val result = ArrayList<String>()
        var cursor = start
        val end = start + length
        while (cursor < end) {
            var next = cursor
            while (next < end && data[next] != 0.toByte()) next++
            if (next > cursor) result.add(decodeText(data.copyOfRange(cursor, next)))
            cursor = next + 1
        }
        return result
    }

    private fun readBytes(channel: FileChannel, position: Long, length: Int): ByteArray {
        require(position >= 0L && length >= 0)
        require(position + length.toLong() <= channel.size())
        val buffer = ByteBuffer.allocate(length)
        var current = position
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, current)
            if (count <= 0) throw IllegalStateException("File berhenti sebelum seluruh data terbaca.")
            current += count
        }
        return buffer.array()
    }

    private fun readBytes(channel: FileChannel, position: Long, length: Long): ByteArray {
        require(length in 0..Int.MAX_VALUE.toLong())
        return readBytes(channel, position, length.toInt())
    }

    private fun u32le(data: ByteArray, offset: Int): Long {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return unsignedInt(b, offset)
    }

    private fun u32be(data: ByteArray, offset: Int): Long {
        val b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return unsignedInt(b, offset)
    }

    private fun unsignedInt(buffer: ByteBuffer, offset: Int): Long =
        buffer.getInt(offset).toLong() and 0xFFFFFFFFL

    private fun unsignedShort(buffer: ByteBuffer, offset: Int): Int =
        buffer.getShort(offset).toInt() and 0xFFFF

    private fun ascii(data: ByteArray, offset: Int, length: Int): String =
        String(data, offset, length, Charsets.US_ASCII)

    private fun hex(data: ByteArray, length: Int): String =
        data.take(minOf(length, data.size))
            .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun decodeText(bytes: ByteArray): String {
        return try {
            Charset.forName("Shift_JIS").newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun align16(value: Long): Long = (value + 15L) and -16L
    private fun align4(value: Long): Long = (value + 3L) and -4L

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        val kib = value / 1024.0
        if (kib < 1024.0) return String.format(Locale.US, "%.1f KiB", kib)
        val mib = kib / 1024.0
        if (mib < 1024.0) return String.format(Locale.US, "%.1f MiB", mib)
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0)
    }
}
