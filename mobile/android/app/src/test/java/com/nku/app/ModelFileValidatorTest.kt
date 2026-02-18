package com.nku.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelFileValidatorTest {

    private fun tempFileWithBytes(bytes: ByteArray): File {
        val file = File.createTempFile("gguf-validator", ".bin")
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `rejects null file`() {
        assertFalse(ModelFileValidator.isValidGguf(null))
    }

    @Test
    fun `rejects tiny file even with gguf header`() {
        val bytes = "GGUF".toByteArray(Charsets.US_ASCII) + ByteArray(4)
        val file = tempFileWithBytes(bytes)
        assertFalse(ModelFileValidator.isValidGguf(file, minSizeBytes = 1024))
    }

    @Test
    fun `rejects file with wrong header`() {
        val bytes = "NOTG".toByteArray(Charsets.US_ASCII) + ByteArray(2048)
        val file = tempFileWithBytes(bytes)
        assertFalse(ModelFileValidator.isValidGguf(file, minSizeBytes = 1024))
    }

    @Test
    fun `accepts file with gguf header and sufficient size`() {
        val bytes = "GGUF".toByteArray(Charsets.US_ASCII) + ByteArray(4096)
        val file = tempFileWithBytes(bytes)
        assertTrue(ModelFileValidator.isValidGguf(file, minSizeBytes = 1024))
    }
}
