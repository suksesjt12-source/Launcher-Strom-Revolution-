package com.stormrevolution.launcher

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
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
    private const val MAX_CPK_SAMPLE_ROWS = 64
    private const val MAX_CPK_EXTRACT_ROWS = 100_000
    private const val MAX_CPK_EXTRACT_FILES = 512
    private const val MAX_CPK_ENTRY_BYTES = 128L * 1024L * 1024L
    private const val MAX_CPK_OUTPUT_BYTES = 512L * 1024L * 1024L
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


    /**
     * Extracts XFBIN entries from a named-TOC CPK archive.
     *
     * CPK input is opened read-only through SAF. Files are written to the
     * application's private directory; ITOC-only archives are intentionally
     * rejected until their index layout is implemented.
     */
    fun extractXfbinsFromCpk(
        resolver: ContentResolver,
        uri: Uri,
        displayName: String,
        filesDir: File
    ): Report {
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: return Report(displayName, "File descriptor CPK tidak tersedia.", false)
            descriptor.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    extractCpkEntries(channel, displayName, filesDir)
                }
            }
        } catch (error: Exception) {
            Report(
                displayName,
                "Ekstraksi CPK gagal: ${error.javaClass.simpleName}: ${error.message ?: "kesalahan I/O"}",
                false
            )
        }
    }

    private fun extractCpkEntries(
        channel: FileChannel,
        displayName: String,
        filesDir: File
    ): Report {
        val archiveSize = channel.size()
        if (archiveSize < 32L) {
            return Report(displayName, "File terlalu kecil untuk CPK.", false)
        }

        val cpkHeader = readBytes(channel, 0L, 16)
        if (ascii(cpkHeader, 0, 4) != "CPK ") {
            return Report(displayName, "Signature CPK tidak ditemukan.", false)
        }
        val cpkPacketSize = u64le(cpkHeader, 8)
        if (cpkPacketSize <= 0L || cpkPacketSize > MAX_UTF_TABLE_BYTES.toLong() ||
            cpkPacketSize > archiveSize - 16L) {
            return Report(displayName, "Ukuran header CPK tidak valid.", false)
        }
        val cpkTable = parseUtfAt(channel, 16L, cpkPacketSize)
            ?: return Report(displayName, "Header @UTF CPK gagal dibaca.", false)
        val header = cpkTable.rows.firstOrNull().orEmpty()

        fun asLong(value: Any?): Long? = value?.toString()?.toLongOrNull()
        val tocOffset = asLong(header["TocOffset"])
        val contentOffset = asLong(header["ContentOffset"])
        val itocOffset = asLong(header["ItocOffset"])
        if (tocOffset == null || contentOffset == null ||
            tocOffset < 0L || contentOffset < 0L ||
            tocOffset > archiveSize - 16L || contentOffset > archiveSize) {
            val note = if (itocOffset != null && itocOffset >= 0L) {
                "Arsip menggunakan ITOC. Ekstraksi ITOC belum didukung."
            } else {
                "TocOffset atau ContentOffset tidak tersedia/valid."
            }
            return Report(displayName, note, true)
        }

        val tocHeader = readBytes(channel, tocOffset, 16)
        if (ascii(tocHeader, 0, 4) != "TOC ") {
            return Report(displayName, "Paket TOC bernama tidak ditemukan. ITOC belum didukung.", true)
        }
        val tocPacketSize = u64le(tocHeader, 8)
        if (tocPacketSize <= 0L || tocPacketSize > MAX_UTF_TABLE_BYTES.toLong() ||
            tocPacketSize > archiveSize - tocOffset - 16L) {
            return Report(displayName, "Ukuran paket TOC tidak valid.", false)
        }
        val toc = parseUtfAt(
            channel,
            tocOffset + 16L,
            tocPacketSize,
            MAX_CPK_EXTRACT_ROWS
        ) ?: return Report(displayName, "Tabel TOC @UTF gagal dibaca.", false)

        val rowsToScan = minOf(toc.rowCount, MAX_CPK_EXTRACT_ROWS.toLong())
        val baseOffset = minOf(tocOffset, contentOffset)
        val outputRoot = File(
            File(filesDir, "retail_cpk"),
            safeFileSegment(displayName.substringBeforeLast('.', displayName)) +
                "_" + System.currentTimeMillis()
        )
        if (!outputRoot.mkdirs() && !outputRoot.isDirectory) {
            return Report(displayName, "Folder output privat tidak dapat dibuat.", false)
        }

        var candidates = 0
        var extracted = 0
        var compressed = 0
        var failed = 0
        var totalOutput = 0L
        val saved = ArrayList<String>()

        for (rowIndex in 0 until rowsToScan.toInt()) {
            val row = toc.rows.getOrNull(rowIndex)
            if (row == null) {
                failed++
                continue
            }
            val fileName = row["FileName"]?.toString()?.trim().orEmpty()
            if (!fileName.endsWith(".xfbin", ignoreCase = true)) continue
            candidates++

            if (extracted >= MAX_CPK_EXTRACT_FILES ||
                totalOutput >= MAX_CPK_OUTPUT_BYTES) continue

            val storedSize = asLong(row["FileSize"])
            val expandedSize = asLong(row["ExtractSize"])
            val relativeOffset = asLong(row["FileOffset"])
            if (storedSize == null || relativeOffset == null ||
                storedSize <= 0L || storedSize > MAX_CPK_ENTRY_BYTES ||
                relativeOffset < 0L || relativeOffset > Long.MAX_VALUE - baseOffset) {
                failed++
                continue
            }
            val absoluteOffset = baseOffset + relativeOffset
            if (absoluteOffset > archiveSize || storedSize > archiveSize - absoluteOffset ||
                storedSize > Int.MAX_VALUE.toLong()) {
                failed++
                continue
            }

            val storedBytes = readBytes(channel, absoluteOffset, storedSize.toInt())
            val outputBytes = try {
                if (CriLaylaDecoder.isCompressed(storedBytes)) {
                    compressed++
                    CriLaylaDecoder.decode(
                        storedBytes,
                        expandedSize,
                        minOf(MAX_CPK_ENTRY_BYTES, MAX_CPK_OUTPUT_BYTES - totalOutput).toInt()
                    )
                } else {
                    if (expandedSize != null && expandedSize > 0L && expandedSize != storedSize) {
                        failed++
                        continue
                    }
                    storedBytes
                }
            } catch (_: Exception) {
                failed++
                continue
            }

            if (outputBytes.isEmpty() ||
                totalOutput + outputBytes.size.toLong() > MAX_CPK_OUTPUT_BYTES) {
                failed++
                continue
            }

            val directory = row["DirName"]?.toString().orEmpty()
                .replace('\\', '/')
            val parentParts = (directory + "/" + fileName.substringBeforeLast('/', ""))
                .split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .map { safeFileSegment(it) }
            val safeName = safeFileSegment(fileName.substringAfterLast('/'))
            val parent = parentParts.fold(outputRoot) { current, part -> File(current, part) }
            if (!parent.exists() && !parent.mkdirs()) {
                failed++
                continue
            }
            val target = File(parent, safeName)
            val rootPath = outputRoot.canonicalFile.path + File.separator
            if (!target.canonicalFile.path.startsWith(rootPath)) {
                failed++
                continue
            }

            try {
                target.outputStream().use { stream -> stream.write(outputBytes) }
                totalOutput += outputBytes.size.toLong()
                extracted++
                if (saved.size < 20) saved.add(target.relativeTo(outputRoot).path)
            } catch (_: Exception) {
                failed++
            }
        }

        val report = StringBuilder()
        report.append("EKSTRAKSI XFBIN DARI CPK\n")
        report.append("Arsip: ").append(displayName).append('\n')
        report.append("Ukuran CPK: ").append(formatBytes(archiveSize)).append('\n')
        report.append("Entri TOC: ").append(toc.rowCount).append('\n')
        report.append("Entri diperiksa: ").append(rowsToScan).append('\n')
        report.append("File XFBIN ditemukan: ").append(candidates).append('\n')
        report.append("File XFBIN diekstrak: ").append(extracted).append('\n')
        report.append("File CRILAYLA didekompresi: ").append(compressed).append('\n')
        report.append("File gagal/dilewati: ").append(failed).append('\n')
        report.append("Output: ").append(formatBytes(totalOutput)).append('\n')
        if (toc.rowCount > rowsToScan) {
            report.append("Peringatan: TOC melebihi batas pemeriksaan ")
                .append(MAX_CPK_EXTRACT_ROWS).append(" entri.\n")
        }
        report.append("Folder privat: ").append(outputRoot.absolutePath).append('\n')
        if (saved.isNotEmpty()) {
            report.append("\nCONTOH FILE\n")
            saved.forEach { report.append("- ").append(it).append('\n') }
        }
        if (candidates == 0) {
            report.append("\nTOC bernama tidak memiliki entri .xfbin yang terdeteksi.")
        }
        report.append("\nCatatan: XFBIN sudah dikeluarkan dari arsip, tetapi mesh, tekstur, ")
            .append("skeleton, dan animasi masih belum diterjemahkan ke renderer.")
        return Report(displayName, report.toString(), true)
    }

    private fun safeFileSegment(value: String): String =
        value.trim()
            .map { character ->
                if (character == '/' || character == '\\' ||
                    character == ':' || character == '*' ||
                    character == '?' || character == '"' ||
                    character == '<' || character == '>' ||
                    character == '|' || character.code < 0x20) '_' else character
            }
            .joinToString("")
            .trim('.', ' ')
            .take(96)
            .ifBlank { "unnamed" }

    private fun inspectCpk(channel: FileChannel, displayName: String): Report {
        val fileSize = channel.size()
        if (fileSize < 32L) {
            return Report(displayName, "File terlalu kecil untuk menjadi CPK.", false)
        }

        // CRI packet header: magic[4], encflag[4], packetSize[8] (little-endian).
        val header = readBytes(channel, 0L, 16)
        val marker = ascii(header, 0, 4)
        if (marker != "CPK ") {
            val utfLocation = findUtfMarker(channel, minOf(fileSize, 4096L))
            return Report(
                displayName,
                if (utfLocation >= 0L) {
                    "Ditemukan @UTF pada offset 0x${utfLocation.toString(16)}, tetapi paket CPK tidak ditemukan di offset 0."
                } else {
                    "Signature CPK tidak ditemukan pada awal file. Header: ${hex(header, 16)}"
                },
                false
            )
        }

        val packetSize = u64le(header, 8)
        val packetStart = 16L
        if (packetSize <= 0L || packetSize > MAX_UTF_TABLE_BYTES.toLong() ||
            packetStart + packetSize > fileSize) {
            return Report(
                displayName,
                "Header CPK dikenali, tetapi ukuran paket @UTF tidak valid: $packetSize byte.",
                false
            )
        }

        val cpkTable = parseUtfAt(channel, packetStart, packetSize)
            ?: return Report(
                displayName,
                "Paket CPK ditemukan, tetapi tabel @UTF tidak dapat diparse. Arsip mungkin memakai varian atau enkripsi berbeda.",
                true
            )

        val output = StringBuilder()
        output.append("CRI CPK / UTF dikenali\n")
        output.append("Ukuran arsip: ").append(formatBytes(fileSize)).append('\n')
        output.append("Ukuran paket header: ").append(formatBytes(packetSize)).append('\n')
        output.append("Tabel header: ").append(cpkTable.name).append('\n')
        output.append("Kolom header: ")
            .append(cpkTable.columns.joinToString(", ") { it.name })
            .append('\n')

        val headerRow = cpkTable.rows.firstOrNull().orEmpty()
        val fileCount = headerRow["Files"]
        val contentOffset = headerRow["ContentOffset"]
        val tocOffsetValue = headerRow["TocOffset"]
        val itocOffsetValue = headerRow["ItocOffset"]
        if (fileCount != null) output.append("Jumlah file tercatat: ").append(fileCount).append('\n')
        if (contentOffset != null) output.append("ContentOffset: ").append(contentOffset).append('\n')
        if (tocOffsetValue != null) output.append("TocOffset: ").append(tocOffsetValue).append('\n')
        if (itocOffsetValue != null) output.append("ItocOffset: ").append(itocOffsetValue).append('\n')

        val tocOffset = tocOffsetValue?.toString()?.toLongOrNull()
        val tocTable = if (tocOffset != null && tocOffset >= 0L &&
            tocOffset <= fileSize - 16L) {
            val tocHeader = readBytes(channel, tocOffset, 16)
            val tocSignature = ascii(tocHeader, 0, 4)
            if (tocSignature == "TOC ") {
                val tocPacketSize = u64le(tocHeader, 8)
                if (tocPacketSize > 0L && tocPacketSize <= MAX_UTF_TABLE_BYTES.toLong() &&
                    tocOffset + 16L + tocPacketSize <= fileSize) {
                    parseUtfAt(channel, tocOffset + 16L, tocPacketSize)
                } else null
            } else null
        } else null

        if (tocTable != null) {
            output.append("\nTabel TOC: ").append(tocTable.name).append('\n')
            output.append("Entri pada TOC: ").append(tocTable.rowCount).append('\n')
            output.append("Kolom penting: ")
                .append(tocTable.columns.filter {
                    it.name in setOf("DirName", "FileName", "FileSize", "ExtractSize", "FileOffset")
                }.joinToString(", ") { it.name })
                .append('\n')
            output.append("Contoh entri (maksimum $MAX_CPK_SAMPLE_ROWS):\n")
            for ((index, row) in tocTable.rows.withIndex()) {
                val directory = row["DirName"]?.toString().orEmpty()
                val name = row["FileName"]?.toString().orEmpty()
                val path = listOf(directory.trim('/'), name.trim('/'))
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                output.append(index + 1).append(". ")
                    .append(path.ifBlank { "(nama tidak tersedia)" })
                    .append(" | file=").append(row["FileSize"] ?: "?")
                    .append(" | extract=").append(row["ExtractSize"] ?: "?")
                    .append(" | offset=").append(row["FileOffset"] ?: "?")
                    .append('\n')
            }
            if (tocTable.rowCount > tocTable.rows.size) {
                output.append("... menampilkan ").append(tocTable.rows.size)
                    .append(" dari ").append(tocTable.rowCount).append(" entri.\n")
            }
        } else {
            output.append("\nTOC bernama tidak tersedia atau tidak valid. ")
                .append("Arsip mungkin memakai ITOC; pembacaan ITOC belum diimplementasikan.\n")
        }

        output.append("\nCatatan: parser membaca metadata secara read-only; ")
            .append("payload file tidak diekstrak atau didekompresi.")
        return Report(displayName, output.toString(), true)
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
        payloadLength: Long,
        maxRows: Int = MAX_CPK_SAMPLE_ROWS
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
        val displayedRows = minOf(rowCount, maxRows.toLong()).toInt()
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

    private fun u64le(data: ByteArray, offset: Int): Long {
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(offset)
        require(value >= 0L) { "Ukuran paket CPK melebihi batas integer yang didukung." }
        return value
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
