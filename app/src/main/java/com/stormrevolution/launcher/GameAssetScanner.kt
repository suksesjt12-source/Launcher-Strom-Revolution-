package com.stormrevolution.launcher

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/**
 * One-folder game importer.
 *
 * It recursively inventories the selected installation folder, extracts XFBIN
 * members from supported named-TOC CPK archives, then extracts NUD/NUT payloads
 * from those XFBIN files and loose XFBIN files. Source game data is read-only.
 */
object GameAssetScanner {
    data class Result(
        val report: String,
        val files: Int,
        val directories: Int,
        val bytesKnown: Long,
        val truncated: Boolean
    )

    private data class Folder(
        val documentId: String,
        val relativePath: String,
        val depth: Int
    )

    private data class Sample(
        val path: String,
        val size: Long,
        val extension: String,
        val header: String
    )

    private data class CpkCandidate(val path: String, val uri: Uri)
    private data class LooseXfbinCandidate(val path: String, val uri: Uri)
    private data class XfbinFileCandidate(val path: String, val file: File)

    private const val MAX_ENTRIES = 12_000
    private const val MAX_DEPTH = 18
    private const val MAX_SAMPLES = 42
    private const val MAX_CPK_CANDIDATES = 96
    private const val MAX_LOOSE_XFBIN_CANDIDATES = 64
    private const val MAX_CPK_ARCHIVES_TO_IMPORT = 8
    private const val MAX_IMPORTED_XFBINS_TO_PARSE = 48
    private const val MAX_CPK_FILES_PER_ARCHIVE = 128
    private const val MAX_TOTAL_IMPORT_BYTES = 512L * 1024L * 1024L

    private val relevantExtensions = setOf(
        "exe", "cpk", "xfbin", "nud", "nut", "anm", "bin",
        "dds", "png", "jpg", "wav", "adx", "hca", "ogg"
    )

    fun scan(resolver: ContentResolver, treeUri: Uri, filesDir: File): Result {
        var directoryCount = 0
        var fileCount = 0
        var knownBytes = 0L
        var visitedEntries = 0
        var truncated = false
        var unreadableFolders = 0
        val extensionCounts = sortedMapOf<String, Int>()
        val samples = mutableListOf<Sample>()
        val cpkCandidates = mutableListOf<CpkCandidate>()
        val looseXfbins = mutableListOf<LooseXfbinCandidate>()
        val errors = mutableListOf<String>()

        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (error: Exception) {
            return Result(
                report = "GAGAL MEMBACA FOLDER\nURI folder tidak dapat dibaca: ${error.message ?: "akses ditolak"}",
                files = 0,
                directories = 0,
                bytesKnown = 0,
                truncated = false
            )
        }

        val rootName = queryName(resolver, treeUri)
            ?: rootId.substringAfter(':').substringAfterLast('/').ifBlank { "Folder game" }

        val queue = ArrayDeque<Folder>()
        val visitedDocumentIds = HashSet<String>()
        queue.add(Folder(rootId, "", 0))
        visitedDocumentIds.add(rootId)

        while (queue.isNotEmpty()) {
            if (visitedEntries >= MAX_ENTRIES) {
                truncated = true
                break
            }

            val folder = queue.removeLast()
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.documentId)
            } catch (_: Exception) {
                unreadableFolders++
                if (errors.size < 4) errors.add(
                    "${folder.relativePath.ifBlank { rootName }}: URI anak folder tidak dapat dibangun"
                )
                continue
            }

            try {
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                        unreadableFolders++
                        if (errors.size < 4) errors.add(
                            "${folder.relativePath.ifBlank { rootName }}: metadata tidak lengkap"
                        )
                        return@use
                    }

                    while (cursor.moveToNext()) {
                        if (visitedEntries >= MAX_ENTRIES) {
                            truncated = true
                            break
                        }
                        visitedEntries++

                        val childId = cursor.getString(idColumn) ?: continue
                        val childName = cursor.getString(nameColumn)
                            ?.takeIf { it.isNotBlank() } ?: "unnamed"
                        val mimeType = cursor.getString(mimeColumn).orEmpty()
                        val relativePath = if (folder.relativePath.isEmpty()) {
                            childName
                        } else {
                            folder.relativePath + "/" + childName
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directoryCount++
                            if (folder.depth < MAX_DEPTH && visitedDocumentIds.add(childId)) {
                                queue.add(Folder(childId, relativePath, folder.depth + 1))
                            } else if (folder.depth >= MAX_DEPTH) {
                                truncated = true
                            }
                            continue
                        }

                        fileCount++
                        val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                            cursor.getLong(sizeColumn).coerceAtLeast(0L)
                        } else {
                            -1L
                        }
                        if (size >= 0L) knownBytes += size

                        val extension = extensionOf(childName)
                        extensionCounts[extension] = (extensionCounts[extension] ?: 0) + 1
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

                        when (extension) {
                            "cpk" -> if (cpkCandidates.size < MAX_CPK_CANDIDATES) {
                                cpkCandidates.add(CpkCandidate(relativePath, documentUri))
                            }
                            "xfbin" -> if (looseXfbins.size < MAX_LOOSE_XFBIN_CANDIDATES) {
                                looseXfbins.add(LooseXfbinCandidate(relativePath, documentUri))
                            }
                        }

                        val isRelevant = extension in relevantExtensions
                        if (samples.size < MAX_SAMPLES && (isRelevant || samples.size < 12)) {
                            samples.add(
                                Sample(
                                    path = relativePath,
                                    size = size,
                                    extension = extension,
                                    header = readHeader(resolver, documentUri)
                                )
                            )
                        }
                    }
                } ?: run {
                    unreadableFolders++
                    if (errors.size < 4) errors.add(
                        "${folder.relativePath.ifBlank { rootName }}: penyedia file tidak mengembalikan daftar"
                    )
                }
            } catch (_: SecurityException) {
                unreadableFolders++
                if (errors.size < 4) errors.add(
                    "${folder.relativePath.ifBlank { rootName }}: izin baca tidak tersedia"
                )
            } catch (error: Exception) {
                unreadableFolders++
                if (errors.size < 4) errors.add(
                    "${folder.relativePath.ifBlank { rootName }}: ${error.javaClass.simpleName}"
                )
            }
        }

        // Import archives in a stable priority order so the common data archives
        // are processed before optional sound/video or region-specific archives.
        val orderedCpk = cpkCandidates.sortedBy { cpkPriority(it.path) }
            .take(MAX_CPK_ARCHIVES_TO_IMPORT)
        val importedXfbins = mutableListOf<XfbinFileCandidate>()
        val cpkReports = mutableListOf<RetailContainerInspector.Report>()
        var cpkBytes = 0L
        var cpkFiles = 0

        for (candidate in orderedCpk) {
            val remaining = MAX_TOTAL_IMPORT_BYTES - cpkBytes
            if (remaining <= 0L) break
            val report = RetailContainerInspector.extractXfbinsFromCpk(
                resolver = resolver,
                uri = candidate.uri,
                displayName = candidate.path,
                filesDir = filesDir,
                maxOutputBytes = remaining,
                maxExtractedFiles = minOf(
                    MAX_CPK_FILES_PER_ARCHIVE,
                    MAX_IMPORTED_XFBINS_TO_PARSE - importedXfbins.size
                ).coerceAtLeast(0)
            )
            cpkReports.add(report)
            cpkBytes += report.outputBytes
            cpkFiles += report.extractedFiles
            report.outputDirectory?.let { outputDirectory ->
                collectXfbins(outputDirectory, MAX_IMPORTED_XFBINS_TO_PARSE - importedXfbins.size)
                    .forEach { file ->
                        importedXfbins.add(XfbinFileCandidate(
                            candidate.path + " -> " + file.name,
                            file
                        ))
                    }
            }
            if (importedXfbins.size >= MAX_IMPORTED_XFBINS_TO_PARSE) break
        }

        // Add loose XFBIN files after archive-derived files. This also allows
        // game installations that unpack some resources next to the CPK files.
        val looseToParse = looseXfbins.take(
            (MAX_IMPORTED_XFBINS_TO_PARSE - importedXfbins.size).coerceAtLeast(0)
        )

        var parsedXfbins = 0
        var modelCount = 0
        var textureCount = 0
        var xfbinPayloadBytes = 0L
        val xfbinReports = mutableListOf<String>()

        for (candidate in importedXfbins) {
            val remaining = MAX_TOTAL_IMPORT_BYTES - cpkBytes - xfbinPayloadBytes
            if (remaining <= 0L) break
            val result = XfbinPayloadExtractor.extractFile(candidate.file, filesDir, remaining)
            parsedXfbins++
            modelCount += result.models
            textureCount += result.textures
            xfbinPayloadBytes += result.outputBytes
            if (xfbinReports.size < 8) {
                xfbinReports.add(candidate.path + ": " +
                    "model ${result.models}, tekstur ${result.textures}, " +
                    "chunk ${result.chunks}, halaman ${result.pages}")
            }
        }

        for (candidate in looseToParse) {
            val remaining = MAX_TOTAL_IMPORT_BYTES - cpkBytes - xfbinPayloadBytes
            if (remaining <= 0L) break
            val result = XfbinPayloadExtractor.extract(
                resolver, candidate.uri, candidate.path, filesDir, remaining
            )
            parsedXfbins++
            modelCount += result.models
            textureCount += result.textures
            xfbinPayloadBytes += result.outputBytes
            if (xfbinReports.size < 8) {
                xfbinReports.add(candidate.path + ": " +
                    "model ${result.models}, tekstur ${result.textures}, " +
                    "chunk ${result.chunks}, halaman ${result.pages}")
            }
        }

        val output = StringBuilder()
        output.append("IMPOR OTOMATIS FOLDER GAME\n")
        output.append("Folder: ").append(rootName).append('\n')
        output.append("Direktori: ").append(directoryCount).append('\n')
        output.append("File: ").append(fileCount).append('\n')
        output.append("Ukuran total terdeteksi: ").append(formatBytes(knownBytes)).append('\n')
        output.append("Folder gagal dibaca: ").append(unreadableFolders).append('\n')
        if (truncated) {
            output.append("Peringatan: pemindaian dibatasi maksimal ")
                .append(MAX_ENTRIES).append(" entri dan kedalaman ")
                .append(MAX_DEPTH).append(" folder.\n")
        }

        output.append("\nHASIL IMPOR ASET\n")
        output.append("Arsip CPK ditemukan: ").append(cpkCandidates.size).append('\n')
        output.append("Arsip CPK diproses: ").append(cpkReports.size).append('\n')
        output.append("XFBIN ditemukan di luar CPK: ").append(looseXfbins.size).append('\n')
        output.append("XFBIN dikeluarkan dari CPK: ").append(cpkFiles).append('\n')
        output.append("XFBIN dibaca: ").append(parsedXfbins).append('\n')
        output.append("Model NUD diekstrak: ").append(modelCount).append('\n')
        output.append("Tekstur NUT diekstrak: ").append(textureCount).append('\n')
        output.append("Total data hasil impor: ")
            .append(formatBytes(cpkBytes + xfbinPayloadBytes)).append('\n')
        output.append("Batas impor otomatis: ")
            .append(formatBytes(MAX_TOTAL_IMPORT_BYTES)).append('\n')

        if (cpkReports.isNotEmpty()) {
            output.append("\nARSIP CPK\n")
            cpkReports.forEach { report ->
                output.append("- ").append(report.title).append(": ")
                    .append(report.extractedFiles).append(" XFBIN, ")
                    .append(formatBytes(report.outputBytes)).append('\n')
                if (!report.recognized || report.extractedFiles == 0) {
                    output.append("  ").append(report.details.lineSequence().take(3).joinToString(" "))
                        .append('\n')
                }
            }
        }

        if (xfbinReports.isNotEmpty()) {
            output.append("\nXFBIN YANG DIPROSES\n")
            xfbinReports.forEach { output.append("- ").append(it).append('\n') }
            if (parsedXfbins > xfbinReports.size) {
                output.append("... ").append(parsedXfbins - xfbinReports.size)
                    .append(" file lain diproses.\n")
            }
        }

        if (cpkCandidates.isEmpty() && looseXfbins.isEmpty()) {
            output.append("\nTidak ditemukan arsip CPK atau file XFBIN di folder ini.")
        } else if (cpkFiles == 0 && modelCount == 0 && textureCount == 0) {
            output.append("\nTidak ada payload model/tekstur yang berhasil diimpor. ")
                .append("Arsip mungkin memakai ITOC atau varian format yang belum didukung.")
        }

        output.append("\nPEMERIKSAAN FILE\n")
        output.append("Ekstensi terbanyak:\n")
        extensionCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(14)
            .forEach { entry ->
                val label = if (entry.key == "[tanpa ekstensi]") entry.key else "." + entry.key
                output.append(label).append(" : ").append(entry.value).append('\n')
            }

        output.append("\nSAMPEL FILE & HEADER (maks. ").append(MAX_SAMPLES).append(")\n")
        if (samples.isEmpty()) {
            output.append("Tidak ada sampel file untuk diperiksa.\n")
        } else {
            samples.forEach { sample ->
                output.append(sample.path).append(" | ")
                    .append(if (sample.size >= 0L) formatBytes(sample.size) else "ukuran tidak diketahui")
                    .append(" | ").append(sample.extension)
                    .append(" | ").append(sample.header).append('\n')
            }
        }

        if (errors.isNotEmpty()) {
            output.append("\nCATATAN AKSES\n")
            errors.forEach { output.append("- ").append(it).append('\n') }
        }

        output.append("\nCatatan: pemilihan folder sudah mengimpor aset yang formatnya didukung. ")
            .append("Aset NUD/NUT masih berupa payload; decoder mesh, tekstur GPU, skeleton, ")
            .append("animasi, stage, dan runtime gameplay belum terhubung ke engine.")
        return Result(output.toString(), fileCount, directoryCount, knownBytes, truncated)
    }

    private fun cpkPriority(path: String): Int {
        return when (path.substringAfterLast('/').lowercase(Locale.ROOT)) {
            "data.cpk" -> 0
            "data_win32.cpk" -> 1
            "dataregion.cpk" -> 2
            "dataregion_win32.cpk" -> 3
            "stage.cpk" -> 4
            else -> 5
        }
    }

    private fun collectXfbins(root: File, limit: Int): List<File> {
        if (limit <= 0 || !root.isDirectory) return emptyList()
        val result = ArrayList<File>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty() && result.size < limit) {
            val directory = pending.removeFirst()
            val children = directory.listFiles()?.sortedBy { it.name } ?: continue
            for (child in children) {
                if (result.size >= limit) break
                when {
                    child.isDirectory -> pending.add(child)
                    child.isFile && child.extension.equals("xfbin", ignoreCase = true) ->
                        result.add(child)
                }
            }
        }
        return result
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return "[tanpa ekstensi]"
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    private fun readHeader(resolver: ContentResolver, uri: Uri): String {
        return try {
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(16)
                val count = stream.read(buffer)
                if (count <= 0) byteArrayOf() else buffer.copyOf(count)
            } ?: byteArrayOf()
            if (bytes.isEmpty()) return "header kosong/tidak dapat dibaca"

            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            val ascii = bytes.map { value ->
                val unsigned = value.toInt() and 0xFF
                if (unsigned in 0x20..0x7E) unsigned.toChar() else '.'
            }.joinToString("")
            val marker = when {
                bytes.size >= 2 && bytes[0] == 'M'.code.toByte() &&
                    bytes[1] == 'Z'.code.toByte() -> "PE/DOS MZ"
                ascii.startsWith("CPK ") -> "CRI CPK"
                ascii.startsWith("CRILAYLA") -> "CRI CRILAYLA"
                ascii.startsWith("NDP3") -> "NUD/NDP3 signature"
                ascii.startsWith("NTP3") -> "NUT/NTP3 signature"
                else -> "ASCII[$ascii]"
            }
            "$marker [$hex]"
        } catch (_: SecurityException) {
            "izin baca header ditolak"
        } catch (error: Exception) {
            "gagal baca header: ${error.javaClass.simpleName}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kib = bytes / 1024.0
        if (kib < 1024.0) return String.format(Locale.US, "%.1f KiB", kib)
        val mib = kib / 1024.0
        if (mib < 1024.0) return String.format(Locale.US, "%.1f MiB", mib)
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0)
    }
}
