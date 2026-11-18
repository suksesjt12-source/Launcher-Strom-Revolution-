package com.stormlauncher.nsunsr

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.ZipFile

internal data class InstalledGame(
    val directory: File,
    val executable: File,
    val fileCount: Int,
    val cpkCount: Int,
    val hasDataFolder: Boolean
)

internal object GameZipImporter {
    private const val PREFS = "storm_game_library"
    private const val MAX_ARCHIVE_BYTES = 40L * 1024L * 1024L * 1024L
    private const val MAX_EXPANDED_BYTES = 80L * 1024L * 1024L * 1024L
    private const val MAX_ENTRIES = 150_000
    private const val REQUIRED_FREE_BYTES = 512L * 1024L * 1024L

    fun loadInstalledGame(context: Context): InstalledGame? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val directory = prefs.getString("game_directory", null)?.let(::File) ?: return null
        val executable = prefs.getString("game_executable", null)?.let(::File) ?: return null
        if (!directory.isDirectory || !executable.isFile) {
            prefs.edit().clear().apply()
            return null
        }
        return buildGameInfo(directory, executable)
    }

    fun importZip(context: Context, uri: Uri): InstalledGame {
        val baseDirectory = File(context.getExternalFilesDir(null) ?: context.filesDir, "StormLauncher/games")
        if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
            throw IllegalStateException("Tidak dapat membuat folder penyimpanan game.")
        }

        val archive = File(context.cacheDir, "storm-import-${UUID.randomUUID()}.zip")
        val staging = File(baseDirectory, ".import-${UUID.randomUUID()}")
        val destination = File(baseDirectory, "Naruto-Storm-Revolution")
        val backup = File(baseDirectory, ".previous-install")
        var oldInstallMoved = false
        var installCommitted = false

        try {
            copyUriToFile(context, uri, archive)
            if (!staging.mkdirs()) {
                throw IllegalStateException("Tidak dapat menyiapkan folder ekstraksi.")
            }

            var extractedBytes = 0L
            var knownExpandedBytes = 0L

            ZipFile(archive).use { zip ->
                val entries = mutableListOf<java.util.zip.ZipEntry>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (entries.size >= MAX_ENTRIES) {
                        throw IllegalArgumentException("ZIP berisi terlalu banyak entri.")
                    }
                    if (entry.name.isBlank()) continue
                    if (!isSafeEntryPath(entry.name)) {
                        throw IllegalArgumentException("Arsip ditolak: ditemukan nama path yang tidak aman.")
                    }
                    if (entry.size > 0L) {
                        knownExpandedBytes += entry.size
                        if (knownExpandedBytes > MAX_EXPANDED_BYTES) {
                            throw IllegalArgumentException("Ukuran hasil ekstraksi melewati batas keamanan 80 GB.")
                        }
                    }
                    entries += entry
                }

                val requiredSpace = knownExpandedBytes + REQUIRED_FREE_BYTES
                val availableSpace = StatFs(baseDirectory.absolutePath).availableBytes
                if (availableSpace < requiredSpace) {
                    throw IllegalStateException("Ruang penyimpanan tidak cukup. ZIP membutuhkan sekitar ${knownExpandedBytes / (1024L * 1024L)} MB ruang ekstraksi tambahan.")
                }

                for (entry in entries) {
                    val normalizedName = entry.name.replace('\\', '/')
                    val outputFile = File(staging, normalizedName).canonicalFile
                    val stagingCanonical = staging.canonicalPath
                    val stagingRoot = stagingCanonical + File.separator
                    val isStagingRootDirectory = entry.isDirectory && outputFile.path == stagingCanonical
                    if (!isStagingRootDirectory && !outputFile.path.startsWith(stagingRoot)) {
                        throw IllegalArgumentException("Arsip ditolak: entri mencoba keluar dari folder ekstraksi.")
                    }

                    if (entry.isDirectory) {
                        if (!outputFile.exists() && !outputFile.mkdirs()) {
                            throw IllegalStateException("Gagal membuat folder di dalam arsip.")
                        }
                        continue
                    }

                    val parent = outputFile.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw IllegalStateException("Tidak dapat membuat folder tujuan file.")
                    }

                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                extractedBytes += read.toLong()
                                if (extractedBytes > MAX_EXPANDED_BYTES) {
                                    throw IllegalArgumentException("Arsip melebihi batas hasil ekstraksi 80 GB.")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }

            val executableInStaging = staging.walkTopDown()
                .firstOrNull { it.isFile && it.name.equals("NSUNSR.exe", ignoreCase = true) }
                ?: throw IllegalArgumentException("NSUNSR.exe tidak ditemukan. Pastikan ZIP berisi folder game yang benar.")

            if (!isPortableExecutable(executableInStaging)) {
                throw IllegalArgumentException("NSUNSR.exe ditemukan, tetapi header Windows PE tidak valid.")
            }

            val relativeExecutable = executableInStaging.relativeTo(staging).path
            if (backup.exists()) backup.deleteRecursively()
            if (destination.exists()) {
                if (!destination.renameTo(backup)) {
                    throw IllegalStateException("Tidak dapat memindahkan instalasi lama ke folder cadangan.")
                }
                oldInstallMoved = true
            }

            if (!staging.renameTo(destination)) {
                if (oldInstallMoved) backup.renameTo(destination)
                oldInstallMoved = false
                throw IllegalStateException("Ekstraksi selesai, tetapi folder game tidak dapat dipasang.")
            }

            installCommitted = true
            if (oldInstallMoved) {
                backup.deleteRecursively()
                oldInstallMoved = false
            }

            val installedExecutable = File(destination, relativeExecutable)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("game_directory", destination.absolutePath)
                .putString("game_executable", installedExecutable.absolutePath)
                .apply()

            return buildGameInfo(destination, installedExecutable)
        } finally {
            archive.delete()
            if (!installCommitted) staging.deleteRecursively()
            if (oldInstallMoved && !destination.exists() && backup.exists()) {
                backup.renameTo(destination)
            } else if (backup.exists() && destination.exists()) {
                backup.deleteRecursively()
            }
        }
    }

    internal fun isSafeEntryPath(name: String): Boolean {
        if (name.isBlank() || name.contains('\u0000')) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(normalized)) return false
        return normalized.split('/').none { it == ".." }
    }

    internal fun isPortableExecutable(file: File): Boolean {
        if (!file.isFile || file.length() < 0x40L) return false
        return try {
            RandomAccessFile(file, "r").use { input ->
                if (input.readUnsignedByte() != 0x4D || input.readUnsignedByte() != 0x5A) {
                    return@use false
                }
                input.seek(0x3C)
                val peOffset = Integer.reverseBytes(input.readInt())
                if (peOffset < 0x40 || peOffset.toLong() + 4L > input.length()) {
                    return@use false
                }
                input.seek(peOffset.toLong())
                input.readUnsignedByte() == 0x50 &&
                    input.readUnsignedByte() == 0x45 &&
                    input.readUnsignedByte() == 0x00 &&
                    input.readUnsignedByte() == 0x00
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Android tidak dapat membuka file ZIP yang dipilih.")
        var copied = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    copied += read.toLong()
                    if (copied > MAX_ARCHIVE_BYTES) {
                        throw IllegalArgumentException("Ukuran file ZIP melebihi batas impor 40 GB.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        if (copied == 0L) {
            throw IllegalArgumentException("File ZIP kosong.")
        }
    }

    private fun buildGameInfo(directory: File, executable: File): InstalledGame {
        var files = 0
        var cpk = 0
        var hasDataFolder = false
        directory.walkTopDown().forEach { item ->
            if (item.isFile) {
                files += 1
                if (item.extension.equals("cpk", ignoreCase = true)) cpk += 1
            } else if (item.isDirectory && item.name.equals("data_win32", ignoreCase = true)) {
                hasDataFolder = true
            }
        }
        return InstalledGame(directory, executable, files, cpk, hasDataFolder)
    }
}
