package com.stormrevolution.launcher.manager

import android.content.Context
import android.os.StatFs
import com.stormrevolution.launcher.NativeBridge
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages Storm Launcher private filesystem hierarchy:
 * - /data/user/0/<pkg>/files/prefix/
 * - /data/user/0/<pkg>/files/runtime/
 * - /data/user/0/<pkg>/files/game/
 * - /data/user/0/<pkg>/files/cache/shaders/
 * - /data/user/0/<pkg>/files/backups/
 * - /data/user/0/<pkg>/files/logs/
 * - /data/user/0/<pkg>/files/ipc/
 */
class StorageManager(private val context: Context) {

    val filesRoot: File get() = context.filesDir
    val prefixDir: File get() = File(filesRoot, "prefix")
    val runtimeDir: File get() = File(filesRoot, "runtime")
    val gameDir: File get() = File(filesRoot, "game")
    val shaderCacheDir: File get() = File(filesRoot, "cache/shaders")
    val backupsDir: File get() = File(filesRoot, "backups")
    val logsDir: File get() = File(filesRoot, "logs")
    val ipcDir: File get() = File(filesRoot, "ipc")
    val xinputSocketFile: File get() = File(ipcDir, "xinput.sock")

    fun ensureBaseDirectories() {
        listOf(
            prefixDir,
            runtimeDir,
            gameDir,
            shaderCacheDir,
            backupsDir,
            logsDir,
            ipcDir
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    fun getAvailableInternalStorageBytes(): Long {
        return runCatching {
            val stat = StatFs(filesRoot.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun hasSufficientStorageFor(requiredBytes: Long, safetyMarginBytes: Long = 128L * 1024L * 1024L): Boolean {
        val available = getAvailableInternalStorageBytes()
        return available > (requiredBytes + safetyMarginBytes)
    }

    fun computeDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        return total
    }

    fun zipDirectory(sourceDir: File, outputZip: File): Result<File> = runCatching {
        require(sourceDir.exists() && sourceDir.isDirectory) {
            "Source directory does not exist: ${sourceDir.name}"
        }
        outputZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            val baseUri = sourceDir.toURI()
            sourceDir.walkTopDown().forEach { file ->
                val relPath = baseUri.relativize(file.toURI()).path
                if (relPath.isEmpty()) return@forEach
                if (file.isDirectory) {
                    val entryName = if (relPath.endsWith("/")) relPath else "$relPath/"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(relPath))
                    FileInputStream(file).use { fis -> fis.copyTo(zos, bufferSize = 16384) }
                    zos.closeEntry()
                }
            }
        }
        outputZip
    }

    fun extractArchive(
        inputStream: InputStream,
        fileName: String,
        targetDir: File
    ): Result<Int> = runCatching {
        if (!targetDir.exists()) targetDir.mkdirs()
        val lower = fileName.lowercase()
        when {
            lower.endsWith(".zip") || lower.endsWith(".wcp") -> {
                extractZip(inputStream, targetDir)
            }
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                GZIPInputStream(BufferedInputStream(inputStream)).use { gz ->
                    extractTar(gz, targetDir)
                }
            }
            lower.endsWith(".tar") -> {
                extractTar(BufferedInputStream(inputStream), targetDir)
            }
            else -> {
                // Attempt Zip first, fallback error for unsupported compression (e.g., .xz without xz lib)
                throw IllegalArgumentException(
                    "Unsupported archive format '$fileName'. Supported formats: .zip, .tar.gz, .tgz, .tar, .wcp"
                )
            }
        }
    }

    private fun extractZip(inputStream: InputStream, targetDir: File): Int {
        var count = 0
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator) &&
                    outFile.canonicalPath != canonicalTarget
                ) {
                    throw SecurityException("Blocked zip path traversal entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos, bufferSize = 16384) }
                    if (isLikelyUnixExecutable(entry.name)) {
                        NativeBridge.makeExecutable(outFile)
                    }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    /**
     * Pure-Kotlin POSIX USTAR Tar reader for .tar / .tar.gz runtime bundles.
     */
    private fun extractTar(input: InputStream, targetDir: File): Int {
        var count = 0
        val header = ByteArray(512)
        val canonicalTarget = targetDir.canonicalPath

        while (true) {
            val read = readFullyOrEof(input, header)
            if (read < 512 || header.all { it == 0.toByte() }) break

            val rawName = extractNullTerminatedAscii(header, 0, 100)
            val prefix = extractNullTerminatedAscii(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            val sizeOctal = extractNullTerminatedAscii(header, 124, 12).trim()
            val fileSize = sizeOctal.toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()
            val modeOctal = extractNullTerminatedAscii(header, 100, 8).trim().toIntOrNull(8) ?: 420

            val outFile = File(targetDir, fullName)
            if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator) &&
                outFile.canonicalPath != canonicalTarget
            ) {
                throw SecurityException("Blocked tar path traversal entry: $fullName")
            }

            if (typeFlag == '5' || fullName.endsWith("/")) {
                outFile.mkdirs()
            } else if (typeFlag == '0' || typeFlag == '\u0000') {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    var remaining = fileSize
                    val buf = ByteArray(8192)
                    while (remaining > 0) {
                        val toRead = minOf(buf.size.toLong(), remaining).toInt()
                        val n = input.read(buf, 0, toRead)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        remaining -= n
                    }
                }
                if ((modeOctal and 0b001001001) != 0 || isLikelyUnixExecutable(fullName)) {
                    NativeBridge.makeExecutable(outFile)
                }
                count++
            } else {
                // Skip non-regular entry payload
                skipExact(input, fileSize)
            }

            val remainder = (512 - (fileSize % 512)) % 512
            if (remainder > 0) skipExact(input, remainder)
        }
        return count
    }

    private fun isLikelyUnixExecutable(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("/bin/") ||
            lower.endsWith(".sh") ||
            lower.endsWith(".so") ||
            lower.endsWith("wine") ||
            lower.endsWith("wine64") ||
            lower.endsWith("wineserver") ||
            lower.endsWith("box64") ||
            lower.endsWith("box86")
    }

    private fun readFullyOrEof(input: InputStream, dest: ByteArray): Int {
        var offset = 0
        while (offset < dest.size) {
            val n = input.read(dest, offset, dest.size - offset)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    private fun skipExact(input: InputStream, bytes: Long) {
        var rem = bytes
        val dump = ByteArray(2048)
        while (rem > 0) {
            val n = input.read(dump, 0, minOf(dump.size.toLong(), rem).toInt())
            if (n <= 0) break
            rem -= n
        }
    }

    private fun extractNullTerminatedAscii(buf: ByteArray, offset: Int, len: Int): String {
        val end = (offset until minOf(buf.size, offset + len)).firstOrNull { buf[it] == 0.toByte() }
            ?: minOf(buf.size, offset + len)
        return String(buf, offset, end - offset, Charsets.US_ASCII)
    }
}
