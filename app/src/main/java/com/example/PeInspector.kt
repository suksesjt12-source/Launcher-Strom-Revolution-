package com.example

data class PeInspection(
    val hasMzHeader: Boolean,
    val hasPeHeader: Boolean,
    val machine: Int?,
    val optionalHeaderMagic: Int?
) {
    val description: String
        get() {
            if (!hasMzHeader) return "Bukan executable DOS/Windows (MZ tidak ditemukan)"
            if (!hasPeHeader) return "Header MZ ditemukan, tetapi header PE belum dapat diverifikasi"

            return when {
                machine == 0x014c && optionalHeaderMagic == 0x010b ->
                    "PE32 / Intel x86 (Windows 32-bit)"
                machine == 0x8664 && optionalHeaderMagic == 0x020b ->
                    "PE32+ / x86-64 (Windows 64-bit)"
                machine == 0xAA64 && optionalHeaderMagic == 0x020b ->
                    "PE32+ / ARM64 Windows"
                else ->
                    "PE valid (machine=0x${machine?.toString(16) ?: "?"}, magic=0x${optionalHeaderMagic?.toString(16) ?: "?"})"
            }
        }

    val isPeImage: Boolean
        get() = hasMzHeader && hasPeHeader
}

object PeInspector {
    fun inspect(bytes: ByteArray): PeInspection {
        if (bytes.size < 2 ||
            (bytes[0].toInt() and 0xff) != 0x4d ||
            (bytes[1].toInt() and 0xff) != 0x5a
        ) {
            return PeInspection(false, false, null, null)
        }

        if (bytes.size < 64) {
            return PeInspection(true, false, null, null)
        }

        val peOffsetLong = readU32(bytes, 0x3c)
        if (peOffsetLong > Int.MAX_VALUE) {
            return PeInspection(true, false, null, null)
        }

        val peOffset = peOffsetLong.toInt()
        if (peOffset < 0 || peOffset > bytes.size - 26) {
            return PeInspection(true, false, null, null)
        }

        val peSignatureValid =
            (bytes[peOffset].toInt() and 0xff) == 0x50 &&
            (bytes[peOffset + 1].toInt() and 0xff) == 0x45 &&
            (bytes[peOffset + 2].toInt() and 0xff) == 0x00 &&
            (bytes[peOffset + 3].toInt() and 0xff) == 0x00

        if (!peSignatureValid) {
            return PeInspection(true, false, null, null)
        }

        val machine = readU16(bytes, peOffset + 4)
        val optionalHeaderMagic = readU16(bytes, peOffset + 24)
        return PeInspection(true, true, machine, optionalHeaderMagic)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }
}
