package com.example

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream

data class GameScanReport(
    val folderName: String,
    val executableName: String?,
    val peInspection: PeInspection?,
    val dllCount: Int,
    val dllNames: List<String>,
    val cpkCount: Int,
    val hasDataFolder: Boolean,
    val scannedEntries: Int,
    val scanLimitReached: Boolean
)

object GameFolderScanner {
    private const val MAX_DEPTH = 6
    private const val MAX_ENTRIES = 12_000
    private const val MAX_HEADER_BYTES = 64 * 1024

    fun scan(context: Context, treeUri: Uri): GameScanReport {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return GameScanReport(
                "Folder tidak terbaca", null, null, 0, emptyList(),
                0, false, 0, false
            )

        var executableName: String? = null
        var peInspection: PeInspection? = null
        var dllCount = 0
        val dllNames = mutableListOf<String>()
        var cpkCount = 0
        var hasDataFolder = false
        var scannedEntries = 0
        var scanLimitReached = false

        val pending = ArrayDeque<Pair<DocumentFile, Int>>()
        pending.add(root to 0)

        while (pending.isNotEmpty()) {
            if (scannedEntries >= MAX_ENTRIES) {
                scanLimitReached = true
                break
            }

            val (entry, depth) = pending.removeFirst()
            scannedEntries++

            val name = entry.name.orEmpty()
            val lowerName = name.lowercase()

            if (entry.isDirectory) {
                if (lowerName == "data" || lowerName == "data_win32") {
                    hasDataFolder = true
                }
                if (depth < MAX_DEPTH) {
                    val children = runCatching { entry.listFiles() }.getOrDefault(emptyArray())
                    children.forEach { child -> pending.add(child to (depth + 1)) }
                }
                continue
            }

            when {
                lowerName == "nsunsr.exe" && executableName == null -> {
                    executableName = name
                    peInspection = runCatching {
                        context.contentResolver.openInputStream(entry.uri)?.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            var total = 0
                            while (total < MAX_HEADER_BYTES) {
                                val allowed = minOf(buffer.size, MAX_HEADER_BYTES - total)
                                val count = input.read(buffer, 0, allowed)
                                if (count <= 0) break
                                output.write(buffer, 0, count)
                                total += count
                            }
                            PeInspector.inspect(output.toByteArray())
                        }
                    }.getOrNull()
                }
                lowerName.endsWith(".dll") -> {
                    dllCount++
                    if (dllNames.size < 12) dllNames += name
                }
                lowerName.endsWith(".cpk") -> cpkCount++
            }
        }

        return GameScanReport(
            folderName = root.name ?: "Game folder",
            executableName = executableName,
            peInspection = peInspection,
            dllCount = dllCount,
            dllNames = dllNames,
            cpkCount = cpkCount,
            hasDataFolder = hasDataFolder,
            scannedEntries = scannedEntries,
            scanLimitReached = scanLimitReached
        )
    }
}
