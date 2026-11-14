package com.stormrevolution.launcher.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stormrevolution.launcher.model.GameStatus
import com.stormrevolution.launcher.model.GameValidationResult
import com.stormrevolution.launcher.model.LogCategory
import com.stormrevolution.launcher.model.PeArchitecture
import com.stormrevolution.launcher.model.PeInspectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class GameManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val runtimeManager: RuntimeManager,
    private val diagnosticManager: DiagnosticManager
) {
    companion object {
        const val TARGET_EXECUTABLE = "NSUNSR.exe"
    }

    private val _gameState = MutableStateFlow(GameValidationResult())
    val gameState: StateFlow<GameValidationResult> = _gameState.asStateFlow()

    /**
     * Scans a SAF directory selected via Android Storage Access Framework without modifying originals.
     */
    fun scanSafGameDirectory(treeUri: Uri, copyToPrivate: Boolean): GameValidationResult {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        diagnosticManager.info(LogCategory.GAME, "Scanning user-selected SAF directory for $TARGET_EXECUTABLE...")
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            val failed = GameValidationResult(
                status = GameStatus.GAME_NOT_FOUND,
                sourceUriString = treeUri.toString(),
                validationMessages = listOf("Selected directory could not be opened via Storage Access Framework."),
                lastScannedAtMillis = System.currentTimeMillis()
            )
            _gameState.value = failed
            return failed
        }

        val collectedFiles = mutableListOf<DocumentFile>()
        collectDocumentFiles(rootDoc, collectedFiles, maxDepth = 4, currentDepth = 0)

        val exeDoc = collectedFiles.firstOrNull {
            it.isFile && it.name.equals(TARGET_EXECUTABLE, ignoreCase = true)
        }

        if (exeDoc == null) {
            val notFound = GameValidationResult(
                status = GameStatus.GAME_NOT_FOUND,
                sourceUriString = treeUri.toString(),
                fileCount = collectedFiles.count { it.isFile },
                totalDirectorySizeBytes = collectedFiles.sumOf { if (it.isFile) it.length() else 0L },
                validationMessages = listOf(
                    "Could not find $TARGET_EXECUTABLE in the selected directory (${collectedFiles.count { it.isFile }} files scanned)."
                ),
                lastScannedAtMillis = System.currentTimeMillis()
            )
            diagnosticManager.warn(LogCategory.GAME, "Scan finished: $TARGET_EXECUTABLE not found.")
            _gameState.value = notFound
            return notFound
        }

        val peResult = context.contentResolver.openInputStream(exeDoc.uri)?.use { stream ->
            PeInspector.inspectStream(stream)
        } ?: PeInspectionResult(isValidPe = false, errorReason = "Unable to read executable stream")

        val fileNamesLower = collectedFiles.mapNotNull { it.name?.lowercase() }
        val hasDataWin32 = collectedFiles.any {
            it.isDirectory && it.name.equals("data_win32", ignoreCase = true)
        } || fileNamesLower.any { it.endsWith(".cpk") }
        val cpkCount = fileNamesLower.count { it.endsWith(".cpk") }
        val hasDxLibs = fileNamesLower.any { it.startsWith("d3dx9_") || it == "d3d9.dll" }
        val hasXInput = fileNamesLower.any { it.startsWith("xinput") }
        val totalSize = collectedFiles.sumOf { if (it.isFile) it.length() else 0L }
        val fileCount = collectedFiles.count { it.isFile }
        val exeSize = exeDoc.length()

        val messages = mutableListOf<String>()
        var validationPassed = true

        if (!peResult.isValidPe) {
            validationPassed = false
            messages += "PE Header check failed: ${peResult.errorReason ?: "Invalid executable"}"
        } else {
            messages += "PE Architecture: ${peResult.architecture.label}, ${peResult.subsystem}, ${peResult.numberOfSections} sections"
        }

        if (exeSize < 64 * 1024L) {
            validationPassed = false
            messages += "NSUNSR.exe size (${exeSize / 1024} KB) is suspiciously small for the game binary."
        }

        if (!hasDataWin32 && cpkCount == 0) {
            validationPassed = false
            messages += "Missing game data structure (data_win32/ or *.cpk archives not found)."
        } else {
            messages += "Detected $cpkCount CPK asset archive(s) and data_win32 structure."
        }

        var localExePath: String? = null
        var localWorkDir: String? = null
        var copiedOk = false

        if (validationPassed && copyToPrivate) {
            if (!storageManager.hasSufficientStorageFor(totalSize)) {
                validationPassed = false
                val freeMb = storageManager.getAvailableInternalStorageBytes() / (1024 * 1024)
                val needMb = totalSize / (1024 * 1024)
                messages += "Insufficient internal storage to copy game files (need ~${needMb} MB, free ${freeMb} MB)."
            } else {
                val copyResult = copySafTreeToPrivateGameDir(rootDoc)
                if (copyResult.isSuccess) {
                    val copiedExe = File(storageManager.gameDir, exeDoc.name ?: TARGET_EXECUTABLE)
                    val foundCopied = findExeInPrivateDir(storageManager.gameDir)
                    localExePath = foundCopied?.absolutePath ?: copiedExe.absolutePath
                    localWorkDir = foundCopied?.parentFile?.absolutePath ?: storageManager.gameDir.absolutePath
                    copiedOk = true
                    messages += "Game files safely copied to private app storage (originals untouched)."
                } else {
                    validationPassed = false
                    messages += "Failed copying game files: ${copyResult.exceptionOrNull()?.message}"
                }
            }
        }

        val status = computeFinalStatus(validationPassed)
        val result = GameValidationResult(
            status = status,
            executableName = exeDoc.name ?: TARGET_EXECUTABLE,
            sourceUriString = treeUri.toString(),
            localExecutablePath = localExePath,
            workingDirectoryPath = localWorkDir,
            executableSizeBytes = exeSize,
            totalDirectorySizeBytes = totalSize,
            fileCount = fileCount,
            cpkArchiveCount = cpkCount,
            hasDataWin32 = hasDataWin32,
            hasDirectXLibs = hasDxLibs,
            hasXInputLib = hasXInput,
            isCopiedToPrivateStorage = copiedOk,
            peInspection = peResult,
            validationMessages = messages,
            lastScannedAtMillis = System.currentTimeMillis()
        )
        diagnosticManager.info(
            LogCategory.GAME,
            "Game validation status: ${status.label} | PE=${peResult.architecture.label} | CPK=$cpkCount | Files=$fileCount"
        )
        _gameState.value = result
        return result
    }

    /**
     * Re-evaluates already imported private game directory and updates status with current Runtime readiness.
     */
    fun revalidateCurrentGame(persistedUriString: String?): GameValidationResult {
        val existingPrivateExe = findExeInPrivateDir(storageManager.gameDir)
        if (existingPrivateExe != null) {
            val pe = PeInspector.inspectFile(existingPrivateExe)
            val workDir = existingPrivateExe.parentFile ?: storageManager.gameDir
            val allFiles = workDir.walkTopDown().filter { it.isFile }.toList()
            val cpkCount = allFiles.count { it.name.endsWith(".cpk", ignoreCase = true) }
            val hasDataWin32 = File(workDir, "data_win32").exists() || cpkCount > 0
            val hasDx = allFiles.any { it.name.startsWith("d3dx9_", ignoreCase = true) || it.name.equals("d3d9.dll", true) }
            val hasXInput = allFiles.any { it.name.startsWith("xinput", ignoreCase = true) }
            val valid = pe.isValidPe && existingPrivateExe.length() >= 64 * 1024L && hasDataWin32
            val status = computeFinalStatus(valid)
            val msgs = buildList {
                if (pe.isValidPe) add("PE Architecture: ${pe.architecture.label}, ${pe.subsystem}")
                else add("Invalid PE header: ${pe.errorReason}")
                if (hasDataWin32) add("Detected $cpkCount CPK asset archive(s) in private game workspace.")
                else add("Missing data_win32/*.cpk asset archives.")
            }
            val result = GameValidationResult(
                status = status,
                executableName = existingPrivateExe.name,
                sourceUriString = persistedUriString,
                localExecutablePath = existingPrivateExe.absolutePath,
                workingDirectoryPath = workDir.absolutePath,
                executableSizeBytes = existingPrivateExe.length(),
                totalDirectorySizeBytes = allFiles.sumOf { it.length() },
                fileCount = allFiles.size,
                cpkArchiveCount = cpkCount,
                hasDataWin32 = hasDataWin32,
                hasDirectXLibs = hasDx,
                hasXInputLib = hasXInput,
                isCopiedToPrivateStorage = true,
                peInspection = pe,
                validationMessages = msgs,
                lastScannedAtMillis = System.currentTimeMillis()
            )
            _gameState.value = result
            return result
        }

        if (!persistedUriString.isNullOrBlank()) {
            return runCatching {
                scanSafGameDirectory(Uri.parse(persistedUriString), copyToPrivate = false)
            }.getOrElse {
                _gameState.value
            }
        }

        val current = _gameState.value
        val updatedStatus = if (current.peInspection.isValidPe && current.hasDataWin32) {
            computeFinalStatus(true)
        } else {
            GameStatus.GAME_NOT_FOUND
        }
        return current.copy(status = updatedStatus).also { _gameState.value = it }
    }

    private fun computeFinalStatus(gameValidationPassed: Boolean): GameStatus {
        if (!gameValidationPassed) return GameStatus.GAME_VALIDATION_FAILED
        return if (runtimeManager.isCoreRuntimeReady()) {
            GameStatus.READY_TO_LAUNCH
        } else {
            GameStatus.RUNTIME_MISSING
        }
    }

    private fun collectDocumentFiles(
        dir: DocumentFile,
        out: MutableList<DocumentFile>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth) return
        val children = dir.listFiles()
        for (child in children) {
            out += child
            if (child.isDirectory && currentDepth < maxDepth) {
                collectDocumentFiles(child, out, maxDepth, currentDepth + 1)
            }
        }
    }

    private fun copySafTreeToPrivateGameDir(rootDoc: DocumentFile): Result<Unit> = runCatching {
        storageManager.ensureBaseDirectories()
        val destRoot = storageManager.gameDir
        if (destRoot.exists()) destRoot.deleteRecursively()
        destRoot.mkdirs()
        copyDocRecursive(rootDoc, destRoot, depth = 0)
    }

    private fun copyDocRecursive(doc: DocumentFile, targetDir: File, depth: Int) {
        if (depth > 5) return
        for (child in doc.listFiles()) {
            val name = child.name ?: continue
            val destFile = File(targetDir, name)
            if (child.isDirectory) {
                destFile.mkdirs()
                copyDocRecursive(child, destFile, depth + 1)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 32768)
                    }
                }
            }
        }
    }

    private fun findExeInPrivateDir(dir: File): File? {
        if (!dir.exists()) return null
        return dir.walkTopDown()
            .maxDepth(4)
            .firstOrNull { it.isFile && it.name.equals(TARGET_EXECUTABLE, ignoreCase = true) }
    }
}
