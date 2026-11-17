package com.stormrevolution.launcher

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.Locale

/** Read-only NUCC/XFBIN page reader with guarded NUD/NUT payload extraction. */
object XfbinPayloadExtractor {
    data class Result(
        val report: String,
        val models: Int,
        val textures: Int,
        val chunks: Int,
        val pages: Int
    )

    private data class ChunkMap(val typeIndex: Int, val pathIndex: Int, val nameIndex: Int)

    private const val MAX_TABLE_BYTES = 32L * 1024L * 1024L
    private const val MAX_STRING_COUNT = 1_000_000
    private const val MAX_CHUNKS = 20_000
    private const val MAX_SINGLE_PAYLOAD = 128L * 1024L * 1024L
    private const val MAX_TOTAL_OUTPUT = 384L * 1024L * 1024L

    fun extract(
        resolver: ContentResolver,
        uri: Uri,
        displayName: String,
        filesDir: File
    ): Result {
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: return failure("File descriptor XFBIN tidak tersedia.")
            descriptor.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    extractFromChannel(channel, displayName, filesDir)
                }
            }
        } catch (error: Exception) {
            failure("Ekstraksi gagal: " + error.javaClass.simpleName + ": " +
                (error.message ?: "kesalahan I/O"))
        }
    }

    private fun extractFromChannel(channel: FileChannel, displayName: String, filesDir: File): Result {
        val fileSize = channel.size()
        if (fileSize < 68L) return failure("File terlalu kecil untuk struktur XFBIN.")
        val header = readBytes(channel, 0L, 28)
        if (ascii(header, 0, 4) != "NUCC") {
            return failure("Signature NUCC tidak ditemukan pada file yang dipilih.")
        }

        val xfbinVersion = u32be(header, 4)
        val chunkTableSize = u32be(header, 16)
        if (chunkTableSize < 40L || chunkTableSize > MAX_TABLE_BYTES) {
            return failure("Ukuran chunk table tidak valid atau terlalu besar.")
        }

        val counts = readBytes(channel, 28L, 40)
        val typeCount = u32be(counts, 0)
        val typeSize = u32be(counts, 4)
        val pathCount = u32be(counts, 8)
        val pathSize = u32be(counts, 12)
        val nameCount = u32be(counts, 16)
        val nameSize = u32be(counts, 20)
        val mapCount = u32be(counts, 24)
        val mapSize = u32be(counts, 28)
        val mapIndicesCount = u32be(counts, 32)
        val referenceCount = u32be(counts, 36)

        val countsValid = listOf(typeCount, pathCount, nameCount, mapCount,
            mapIndicesCount, referenceCount).all { it <= MAX_STRING_COUNT.toLong() }
        val stringBytes = typeSize + pathSize + nameSize
        if (!countsValid || stringBytes > MAX_TABLE_BYTES ||
            stringBytes < typeSize || mapSize > MAX_TABLE_BYTES) {
            return failure("Jumlah entri atau ukuran string table tidak masuk akal.")
        }
        if (mapCount * 12L > MAX_TABLE_BYTES ||
            mapIndicesCount * 4L > MAX_TABLE_BYTES ||
            referenceCount * 8L > MAX_TABLE_BYTES) {
            return failure("Tabel map XFBIN melampaui batas keamanan.")
        }

        var cursor = 68L
        val typeData = readBytes(channel, cursor, typeSize)
        cursor += typeSize
        val types = readStringTable(typeData, typeCount.toInt())
            ?: return failure("String table tipe XFBIN tidak valid.")

        val pathData = readBytes(channel, cursor, pathSize)
        cursor += pathSize
        val paths = readStringTable(pathData, pathCount.toInt())
            ?: return failure("String table path XFBIN tidak valid.")

        val nameData = readBytes(channel, cursor, nameSize)
        cursor += nameSize
        val names = readStringTable(nameData, nameCount.toInt())
            ?: return failure("String table nama XFBIN tidak valid.")

        cursor = align4(cursor)
        val mapData = readBytes(channel, cursor, mapCount * 12L)
        cursor += mapCount * 12L
        val maps = ArrayList<ChunkMap>(mapCount.toInt())
        val mapBuffer = ByteBuffer.wrap(mapData).order(ByteOrder.BIG_ENDIAN)
        repeat(mapCount.toInt()) {
            val typeIndex = unsignedInt(mapBuffer)
            val pathIndex = unsignedInt(mapBuffer)
            val nameIndex = unsignedInt(mapBuffer)
            if (typeIndex >= types.size || pathIndex >= paths.size || nameIndex >= names.size) {
                return failure("Indeks chunk map menunjuk ke string yang tidak tersedia.")
            }
            maps.add(ChunkMap(typeIndex.toInt(), pathIndex.toInt(), nameIndex.toInt()))
        }

        val referenceBytes = referenceCount * 8L
        cursor += referenceBytes
        if (cursor > fileSize) return failure("Reference table melewati akhir file.")

        val indexBytes = mapIndicesCount * 4L
        val indexData = readBytes(channel, cursor, indexBytes)
        cursor += indexBytes
        val indexBuffer = ByteBuffer.wrap(indexData).order(ByteOrder.BIG_ENDIAN)
        val pageMapIndices = IntArray(mapIndicesCount.toInt())
        for (i in pageMapIndices.indices) {
            val value = unsignedInt(indexBuffer)
            if (value >= maps.size) return failure("Chunk map index di luar rentang.")
            pageMapIndices[i] = value.toInt()
        }

        val expectedPageStart = 28L + chunkTableSize + referenceBytes
        if (cursor != expectedPageStart) {
            return failure("Ukuran chunk table tidak cocok. XFBIN mungkin memakai varian layout.")
        }
        if (cursor > fileSize) return failure("Awal halaman XFBIN melewati ukuran file.")

        val baseName = safeSegment(displayName.substringBeforeLast('.', displayName))
        val outputRoot = File(File(filesDir, "retail_extract"),
            baseName + "_" + System.currentTimeMillis())
        if (!outputRoot.mkdirs() && !outputRoot.isDirectory) {
            return failure("Folder output privat tidak dapat dibuat.")
        }

        var offset = cursor
        var pageMapBase = 0
        var chunksSeen = 0
        var pagesSeen = 0
        var modelCount = 0
        var textureCount = 0
        var totalOutput = 0L
        var skippedLarge = 0
        var badPayloads = 0
        var pageMarkerFound = false
        val typeCounts = linkedMapOf<String, Int>()
        val savedFiles = ArrayList<String>()

        while (offset + 12L <= fileSize && chunksSeen < MAX_CHUNKS) {
            val chunkHeader = readBytes(channel, offset, 12)
            val payloadSize = u32be(chunkHeader, 0)
            val localMapIndex = u32be(chunkHeader, 4)
            val chunkVersion = u16be(chunkHeader, 8)
            if (payloadSize > fileSize || offset + 12L + payloadSize > fileSize) {
                badPayloads++
                break
            }
            if (pageMapBase < 0 || pageMapBase >= pageMapIndices.size ||
                localMapIndex >= (pageMapIndices.size - pageMapBase).toLong()) {
                badPayloads++
                break
            }

            val globalMapIndex = pageMapIndices[pageMapBase + localMapIndex.toInt()]
            val map = maps.getOrNull(globalMapIndex)
            if (map == null) {
                badPayloads++
                break
            }

            val typeName = types[map.typeIndex]
            val pathName = paths[map.pathIndex]
            val chunkName = names[map.nameIndex]
            typeCounts[typeName] = (typeCounts[typeName] ?: 0) + 1
            chunksSeen++
            val payloadOffset = offset + 12L

            if (typeName.equals("nuccChunkPage", ignoreCase = true)) {
                if (payloadSize < 8L) {
                    badPayloads++
                    break
                }
                val pageData = readBytes(channel, payloadOffset, 8)
                val pageSize = u32be(pageData, 0)
                if (pageSize <= 0L || pageSize > (pageMapIndices.size - pageMapBase).toLong()) {
                    badPayloads++
                    break
                }
                pageMapBase += pageSize.toInt()
                pagesSeen++
                pageMarkerFound = true
            } else if (typeName.equals("nuccChunkModel", ignoreCase = true) ||
                typeName.equals("nuccChunkTexture", ignoreCase = true)) {
                if (payloadSize > MAX_SINGLE_PAYLOAD || totalOutput >= MAX_TOTAL_OUTPUT) {
                    skippedLarge++
                } else {
                    val payload = readBytes(channel, payloadOffset, payloadSize)
                    val isModel = typeName.equals("nuccChunkModel", ignoreCase = true)
                    val extracted = if (isModel) extractModelNud(payload, chunkVersion)
                        else extractTextureNut(payload)
                    if (extracted == null || extracted.isEmpty()) {
                        badPayloads++
                    } else if (totalOutput + extracted.size > MAX_TOTAL_OUTPUT) {
                        skippedLarge++
                    } else {
                        val extension = if (isModel) "nud" else "nut"
                        val category = if (isModel) "models" else "textures"
                        val saved = writeSafePayload(
                            outputRoot, category, pathName, chunkName, extension, extracted
                        )
                        if (saved == null) {
                            badPayloads++
                        } else {
                            totalOutput += extracted.size.toLong()
                            savedFiles.add(saved.relativeTo(outputRoot).path)
                            if (isModel) modelCount++ else textureCount++
                        }
                    }
                }
            }

            val nextOffset = payloadOffset + payloadSize
            if (nextOffset <= offset) break
            offset = nextOffset
        }

        if (chunksSeen >= MAX_CHUNKS) skippedLarge++
        val report = StringBuilder()
        report.append("XFBIN PAYLOAD EXTRACTION\n")
        report.append("File: ").append(displayName).append('\n')
        report.append("Versi NUCC: ").append(xfbinVersion).append('\n')
        report.append("Ukuran: ").append(formatBytes(fileSize)).append('\n')
        report.append("Halaman: ").append(pagesSeen).append('\n')
        report.append("Chunk diperiksa: ").append(chunksSeen).append('\n')
        report.append("Model NUD diekstrak: ").append(modelCount).append('\n')
        report.append("Tekstur NUT diekstrak: ").append(textureCount).append('\n')
        report.append("Output: ").append(formatBytes(totalOutput)).append('\n')
        report.append("Payload dilewati karena batas ukuran: ").append(skippedLarge).append('\n')
        report.append("Chunk/payload tidak valid: ").append(badPayloads).append('\n')
        report.append("Page marker ditemukan: ").append(if (pageMarkerFound) "ya" else "tidak").append('\n')
        report.append("Folder privat: ").append(outputRoot.absolutePath).append('\n')
        if (typeCounts.isNotEmpty()) {
            report.append("\nTIPE CHUNK\n")
            typeCounts.entries.sortedByDescending { it.value }.take(18).forEach {
                report.append(it.key).append(" : ").append(it.value).append('\n')
            }
        }
        if (savedFiles.isNotEmpty()) {
            report.append("\nCONTOH FILE HASIL\n")
            savedFiles.take(16).forEach { report.append("- ").append(it).append('\n') }
            if (savedFiles.size > 16) {
                report.append("... dan ").append(savedFiles.size - 16).append(" file lain\n")
            }
        }
        report.append("\nCatatan: NUD/NUT baru diekstrak sebagai payload mentah. ")
            .append("Vertex/index buffer, skeleton, material, texture pixels, dan animasi belum diterjemahkan ke renderer.")
        return Result(report.toString(), modelCount, textureCount, chunksSeen, pagesSeen)
    }

    private fun extractModelNud(data: ByteArray, version: Int): ByteArray? {
        if (data.size < 24) return null
        var cursor = 0
        cursor += 2 // field00
        cursor += 2 // rigging flag
        val attributes = u16be(data, cursor)
        cursor += 2
        var nudSize = 0L

        if (version > 0x73 && version < 0x76) {
            cursor += 2 // reserved
            cursor += 12 // clump, hit, mesh-bone references
            if (cursor + 4 > data.size) return null
            nudSize = u32be(data, cursor)
            cursor += 4
            cursor += 4 // light category, render layer and light mode
        } else {
            cursor += 2 // render layer and light mode
            if (version > 0x73) cursor += 4 // light category
            cursor += 12 // clump, hit, mesh-bone references
            if (cursor + 4 > data.size) return null
            nudSize = u32be(data, cursor)
            cursor += 4
        }

        if (attributes and 0x04 != 0) cursor += 24 // bounding box
        if (cursor < 0 || cursor > data.size || nudSize <= 0L ||
            nudSize > data.size || cursor.toLong() + nudSize > data.size) return null
        return data.copyOfRange(cursor, cursor + nudSize.toInt())
    }

    private fun extractTextureNut(data: ByteArray): ByteArray? {
        if (data.size < 12) return null
        val nutSize = u32be(data, 8)
        if (nutSize <= 0L || nutSize > data.size || 12L + nutSize > data.size) return null
        return data.copyOfRange(12, 12 + nutSize.toInt())
    }

    private fun writeSafePayload(
        root: File,
        category: String,
        virtualPath: String,
        chunkName: String,
        extension: String,
        bytes: ByteArray
    ): File? {
        return try {
            val categoryRoot = File(root, category)
            val pieces = virtualPath.replace('\\', '/').split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .map { safeSegment(it) }
            var parent = categoryRoot
            for (piece in pieces) parent = File(parent, piece)
            if (!parent.exists() && !parent.mkdirs()) return null
            val safeName = safeSegment(chunkName)
            var output = File(parent, safeName + "." + extension)
            var suffix = 1
            while (output.exists()) {
                output = File(parent, safeName + "_" + suffix + "." + extension)
                suffix++
                if (suffix > 1000) return null
            }
            val rootPath = root.canonicalFile.path + File.separator
            if (!output.canonicalFile.path.startsWith(rootPath)) return null
            output.writeBytes(bytes)
            output
        } catch (_: Exception) {
            null
        }
    }

    private fun readStringTable(data: ByteArray, count: Int): List<String>? {
        if (count < 0 || count > MAX_STRING_COUNT) return null
        val result = ArrayList<String>(count)
        var cursor = 0
        repeat(count) {
            if (cursor >= data.size) return null
            var end = cursor
            while (end < data.size && data[end] != 0.toByte()) end++
            if (end >= data.size) return null
            result.add(decodeText(data.copyOfRange(cursor, end)))
            cursor = end + 1
        }
        return result
    }

    private fun safeSegment(value: String): String {
        return value.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim('.', ' ')
            .take(96)
            .ifBlank { "unnamed" }
    }

    private fun readBytes(channel: FileChannel, position: Long, length: Long): ByteArray {
        require(position >= 0L && length >= 0L && length <= Int.MAX_VALUE.toLong())
        require(position <= channel.size() && length <= channel.size() - position)
        val buffer = ByteBuffer.allocate(length.toInt())
        var offset = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, offset)
            if (read <= 0) throw IllegalStateException("Tidak semua byte dapat dibaca.")
            offset += read.toLong()
        }
        return buffer.array()
    }

    private fun u32be(data: ByteArray, offset: Int): Long {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return buffer.getInt(offset).toLong() and 0xFFFFFFFFL
    }

    private fun u16be(data: ByteArray, offset: Int): Int {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return buffer.getShort(offset).toInt() and 0xFFFF
    }

    private fun unsignedInt(buffer: ByteBuffer): Long =
        buffer.int.toLong() and 0xFFFFFFFFL

    private fun ascii(data: ByteArray, offset: Int, length: Int): String =
        String(data, offset, length, Charsets.US_ASCII)

    private fun decodeText(bytes: ByteArray): String {
        return try {
            Charset.forName("Shift_JIS").newDecoder()
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun align4(value: Long): Long = (value + 3L) and -4L

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return value.toString() + " B"
        val kib = value / 1024.0
        if (kib < 1024.0) return String.format(Locale.US, "%.1f KiB", kib)
        val mib = kib / 1024.0
        return if (mib < 1024.0) String.format(Locale.US, "%.1f MiB", mib)
        else String.format(Locale.US, "%.2f GiB", mib / 1024.0)
    }

    private fun failure(message: String): Result =
        Result("XFBIN EXTRACTION ERROR\n" + message, 0, 0, 0, 0)
}
