package com.nku.app

import java.io.File
import java.io.RandomAccessFile

/**
 * Validates local GGUF model files before inference starts.
 *
 * This prevents false readiness from tiny/corrupt placeholder files.
 */
object ModelFileValidator {

    private const val GGUF_HEADER = "GGUF"
    private const val HEADER_SIZE = 4

    // Conservative lower bound to reject placeholder/corrupt files.
    private const val DEFAULT_MIN_GGUF_BYTES = 64L * 1024L * 1024L // 64 MB

    fun isValidGguf(file: File?, minSizeBytes: Long = DEFAULT_MIN_GGUF_BYTES): Boolean {
        if (file == null || !file.exists() || !file.isFile || !file.canRead()) {
            return false
        }
        if (file.length() < minSizeBytes) {
            return false
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBytes = ByteArray(HEADER_SIZE)
                val bytesRead = raf.read(headerBytes)
                bytesRead == HEADER_SIZE && String(headerBytes, Charsets.US_ASCII) == GGUF_HEADER
            }
        } catch (_: Exception) {
            false
        }
    }
}
