package com.stormrevolution.launcher

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.ArrayDeque
import java.util.Locale

/**
 * Read-only inventory of a user-selected game folder using Android's
 * Storage Access Framework. The scanner never modifies or extracts files.
 *
 * Only a small header sample is read from selected files. No proprietary
 * payload is interpreted until its actual format has been verified.
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

    private const val MAX_ENTRIES = 12000
    private const val MAX_DEPTH = 18
    private const val MAX_SAMPLES = 42
    private val relevantExtensions = setOf(
        "exe", "cpk", "xfbin", "nud", "nut", "anm", "bin",
        "dds", "png", "jpg", "wav", "adx", "hca", "ogg"
    )

    fun scan(resolver: ContentResolver, treeUri: Uri): Result {
        var directoryCount = 0
        var fileCount = 0
        var knownBytes = 0L
        var visitedEntries = 0
        var truncated = false
        var unreadableFolders = 0
        val extensionCounts = sortedMapOf<String, Int>()
        val samples = mutableListOf<Sample>()
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
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    folder.documentId
                )
            } catch (error: Exception) {
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
                    val idColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )
                    val nameColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    val mimeColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                    val sizeColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_SIZE
                    )

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
                            if (folder.depth < MAX_DEPTH &&
                                visitedDocumentIds.add(childId)) {
                                queue.add(
                                    Folder(childId, relativePath, folder.depth + 1)
                                )
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

                        val isRelevant = extension in relevantExtensions ||
                            childName.equals("data.cpk", ignoreCase = true)
                        if (samples.size < MAX_SAMPLES &&
                            (isRelevant || samples.size < 12)) {
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                childId
                            )
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
            } catch (error: SecurityException) {
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

        val output = StringBuilder()
        output.append("INVENTARIS FOLDER GAME\n")
        output.append("Folder: ").append(rootName).append('\n')
        output.append("Direktori: ").append(directoryCount).append('\n')
        output.append("File: ").append(fileCount).append('\n')
        output.append("Ukuran total terdeteksi: ")
            .append(formatBytes(knownBytes)).append('\n')
        output.append("Folder gagal dibaca: ").append(unreadableFolders).append('\n')
        if (truncated) {
            output.append("Batas pemindaian tercapai: hasil mungkin belum lengkap (maks. ")
                .append(MAX_ENTRIES).append(" entri / kedalaman ")
                .append(MAX_DEPTH).append(").\n")
        }

        output.append("\nEKSTENSI TERBANYAK\n")
        if (extensionCounts.isEmpty()) {
            output.append("Tidak ada file yang terdeteksi.\n")
        } else {
            extensionCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .take(18)
                .forEach { entry ->
                    val label = if (entry.key == "[tanpa ekstensi]") {
                        entry.key
                    } else {
                        "." + entry.key
                    }
                    output.append(label).append(" : ").append(entry.value).append('\n')
                }
        }

        output.append("\nFORMAT YANG PERLU DIPERIKSA\n")
        val targetExtensions = listOf("cpk", "xfbin", "nud", "nut", "anm", "bin")
        var foundTarget = false
        for (extension in targetExtensions) {
            val count = extensionCounts[extension] ?: 0
            if (count > 0) {
                foundTarget = true
                output.append('.').append(extension).append(" : ")
                    .append(count).append(" file\n")
            }
        }
        if (!foundTarget) {
            output.append("Ekstensi CPK/XFBIN/NUD/NUT/ANM/BIN tidak ditemukan dari nama file.\n")
        }

        output.append("\nSAMPEL FILE & HEADER (maks. ").append(MAX_SAMPLES).append(")\n")
        if (samples.isEmpty()) {
            output.append("Tidak ada sampel file untuk diperiksa.\n")
        } else {
            samples.forEach { sample ->
                output.append(sample.path)
                    .append(" | ")
                    .append(if (sample.size >= 0L) formatBytes(sample.size) else "ukuran tidak diketahui")
                    .append(" | ")
                    .append(sample.extension)
                    .append(" | ")
                    .append(sample.header)
                    .append('\n')
            }
        }

        if (errors.isNotEmpty()) {
            output.append("\nCATATAN AKSES\n")
            errors.forEach { output.append("- ").append(it).append('\n') }
        }

        output.append("\nCatatan: ini hanya inventaris dan pembacaan 16 byte awal. ")
            .append("Belum ada aset yang diekstrak atau diubah.")
        return Result(
            report = output.toString(),
            files = fileCount,
            directories = directoryCount,
            bytesKnown = knownBytes,
            truncated = truncated
        )
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameColumn)
                } else {
                    null
                }
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
