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

    fun isValidGguf(file: File?, minSizeBytes: Long = DEFAULT_MIN_GGUF_BYTES, expectedSha256: String? = null): Boolean {
        if (file == null || !file.exists() || !file.isFile || !file.canRead()) {
            return false
        }
        if (file.length() < minSizeBytes) {
            return false
        }

        val hasValidHeader = try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBytes = ByteArray(HEADER_SIZE)
                val bytesRead = raf.read(headerBytes)
                bytesRead == HEADER_SIZE && String(headerBytes, Charsets.US_ASCII) == GGUF_HEADER
            }
        } catch (_: Exception) {
            false
        }
        
        if (!hasValidHeader) return false
        
        if (expectedSha256 != null) {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                file.inputStream().use { fis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                val hashBytes = digest.digest()
                val hashString = hashBytes.joinToString("") { "%02x".format(it) }
                if (hashString != expectedSha256.lowercase()) {
                    android.util.Log.w("ModelFileValidator", "SHA-256 mismatch for ${file.name}")
                    return false
                }
            } catch (_: Exception) {
                return false
            }
        }
        
        return true
    }
}
