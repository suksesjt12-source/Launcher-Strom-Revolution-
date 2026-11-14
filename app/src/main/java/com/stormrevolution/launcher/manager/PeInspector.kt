package com.stormrevolution.launcher.manager

import com.stormrevolution.launcher.model.PeArchitecture
import com.stormrevolution.launcher.model.PeInspectionResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses real Windows PE / COFF headers (MZ -> e_lfanew -> PE\0\0 -> Machine & OptionalHeader).
 * Used to accurately verify NSUNSR.exe architecture (x86 32-bit PE32 vs x86_64 PE32+) before
 * selecting Wine WoW64 / Box86 / Box64 execution routing.
 */
object PeInspector {

    private const val IMAGE_DOS_SIGNATURE = 0x5A4D // "MZ"
    private const val IMAGE_NT_SIGNATURE = 0x00004550 // "PE\0\0"
    private const val IMAGE_FILE_MACHINE_I386 = 0x014C
    private const val IMAGE_FILE_MACHINE_AMD64 = 0x8664
    private const val IMAGE_FILE_MACHINE_ARM64 = 0xAA64
    private const val IMAGE_FILE_LARGE_ADDRESS_AWARE = 0x0020
    private const val MAX_HEADER_READ_BYTES = 4096

    fun inspectFile(file: File): PeInspectionResult {
        if (!file.exists() || !file.isFile) {
            return PeInspectionResult(isValidPe = false, errorReason = "Executable file does not exist")
        }
        return runCatching {
            file.inputStream().use { inspectStream(it) }
        }.getOrElse { ex ->
            PeInspectionResult(isValidPe = false, errorReason = "Failed reading executable: ${ex.message}")
        }
    }

    fun inspectStream(inputStream: InputStream): PeInspectionResult {
        val headerBytes = readPrefix(inputStream, MAX_HEADER_READ_BYTES)
        return inspectBytes(headerBytes)
    }

    fun inspectBytes(data: ByteArray): PeInspectionResult {
        if (data.size < 64) {
            return PeInspectionResult(isValidPe = false, errorReason = "File smaller than 64-byte DOS header")
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val dosMagic = buf.getShort(0).toInt() and 0xFFFF
        if (dosMagic != IMAGE_DOS_SIGNATURE) {
            return PeInspectionResult(
                isValidPe = false,
                errorReason = "Missing MZ DOS signature (found 0x${dosMagic.toString(16)})"
            )
        }

        val eLfanew = buf.getInt(0x3C)
        if (eLfanew < 0 || eLfanew + 24 > data.size) {
            return PeInspectionResult(
                isValidPe = false,
                errorReason = "Invalid e_lfanew offset ($eLfanew) outside PE header bounds"
            )
        }

        val peSignature = buf.getInt(eLfanew)
        if (peSignature != IMAGE_NT_SIGNATURE) {
            return PeInspectionResult(
                isValidPe = false,
                errorReason = "Missing PE\\0\\0 signature at offset $eLfanew"
            )
        }

        val coffOffset = eLfanew + 4
        val machine = buf.getShort(coffOffset).toInt() and 0xFFFF
        val numberOfSections = buf.getShort(coffOffset + 2).toInt() and 0xFFFF
        val sizeOfOptionalHeader = buf.getShort(coffOffset + 16).toInt() and 0xFFFF
        val characteristics = buf.getShort(coffOffset + 18).toInt() and 0xFFFF

        val architecture = when (machine) {
            IMAGE_FILE_MACHINE_I386 -> PeArchitecture.X86_32
            IMAGE_FILE_MACHINE_AMD64 -> PeArchitecture.X86_64
            IMAGE_FILE_MACHINE_ARM64 -> PeArchitecture.ARM64
            else -> PeArchitecture.UNKNOWN
        }

        val optOffset = coffOffset + 20
        var entryPointRva = 0L
        var subsystemName = "Unknown"
        if (sizeOfOptionalHeader >= 70 && optOffset + 70 <= data.size) {
            val optMagic = buf.getShort(optOffset).toInt() and 0xFFFF
            entryPointRva = buf.getInt(optOffset + 16).toLong() and 0xFFFFFFFFL
            val subsystem = buf.getShort(optOffset + 68).toInt() and 0xFFFF
            subsystemName = when (subsystem) {
                2 -> "Windows GUI (${if (optMagic == 0x10B) "PE32" else "PE32+"})"
                3 -> "Windows CUI (${if (optMagic == 0x10B) "PE32" else "PE32+"})"
                else -> "Subsystem #$subsystem"
            }
        }

        return PeInspectionResult(
            isValidPe = true,
            architecture = architecture,
            machineCode = machine,
            numberOfSections = numberOfSections,
            subsystem = subsystemName,
            isLargeAddressAware = (characteristics and IMAGE_FILE_LARGE_ADDRESS_AWARE) != 0,
            entryPointRva = entryPointRva,
            errorReason = null
        )
    }

    private fun readPrefix(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }
}
