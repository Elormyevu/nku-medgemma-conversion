package com.nku.app

import android.content.Context
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * P1-01 Audit Fix: Deterministic model integration tests.
 *
 * Previous version used `assumeTrue` skips based on environmental state,
 * causing flaky results depending on whether a model was present from a
 * prior run. This rewrite uses explicit setup/teardown and a tiny synthetic
 * GGUF-header file to make tests fully deterministic.
 *
 * Three suites:
 *   1. Negative: Force-clean all model locations → engine reports not ready.
 *   2. Corrupt rejection: Place a corrupt file → engine rejects it.
 *   3. Validator: Place a valid-header-but-undersized file → validator rejects.
 */
@RunWith(AndroidJUnit4::class)
class ModelIntegrationInstrumentedTest {

    private lateinit var context: Context
    private lateinit var modelDir: File
    private lateinit var appExtDir: File
    private lateinit var dlExtDir: File

    // Track files we create so we can clean them up without deleting
    // real model files that existed before our test.
    private val createdFiles = mutableListOf<File>()
    private val backedUpFiles = mutableMapOf<File, File>()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        modelDir = File(context.filesDir, "models")
        appExtDir = File(context.getExternalFilesDir(null), "models")
        dlExtDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            ""
        )
    }

    /**
     * Temporarily move real model files out of the way for negative tests,
     * restoring them after. This avoids deleting a 2.3GB file.
     */
    private fun backupModelFiles(): List<File> {
        val modelName = "medgemma-4b-q4_k_m.gguf"
        val candidates = listOf(
            File(modelDir, modelName),
            File(appExtDir, modelName),
            File(dlExtDir, modelName)
        )

        candidates.filter { it.exists() }.forEach { original ->
            val backup = File(original.parent, "${original.name}.test_backup")
            if (original.renameTo(backup)) {
                backedUpFiles[original] = backup
            }
        }
        return candidates
    }

    private fun restoreModelFiles() {
        backedUpFiles.forEach { (original, backup) ->
            if (backup.exists() && !original.exists()) {
                backup.renameTo(original)
            }
        }
        backedUpFiles.clear()
        // Clean up any synthetic files we created
        createdFiles.forEach { if (it.exists()) it.delete() }
        createdFiles.clear()
    }

    /**
     * Create a synthetic file with a valid GGUF magic header but undersized.
     * ModelFileValidator requires 64MB minimum, so a 1KB file with GGUF header
     * will be rejected as corrupt/incomplete.
     */
    private fun createSyntheticGgufFile(dir: File, name: String, sizeBytes: Int = 1024): File {
        dir.mkdirs()
        val file = File(dir, name)
        RandomAccessFile(file, "rw").use { raf ->
            // Write GGUF magic header (4 bytes)
            raf.write("GGUF".toByteArray(Charsets.US_ASCII))
            // Pad to requested size
            if (sizeBytes > 4) {
                raf.seek((sizeBytes - 1).toLong())
                raf.writeByte(0)
            }
        }
        createdFiles.add(file)
        return file
    }

    // ═══════════════════════════════════════════════════════════
    // Suite 1: Negative — No model → engine reports not ready
    // ═══════════════════════════════════════════════════════════

    @Test
    fun negative_noModel_engineReportsNotReady() {
        try {
            backupModelFiles()
            val engine = NkuInferenceEngine(context)
            assertFalse(
                "Engine must report not ready when no model file exists in any search path",
                engine.areModelsReady()
            )
        } finally {
            restoreModelFiles()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Suite 2: Corrupt rejection — Undersized fake GGUF → rejected
    // ═══════════════════════════════════════════════════════════

    @Test
    fun corrupt_undersizedGguf_isRejectedByValidator() {
        val modelName = "medgemma-4b-q4_k_m.gguf"
        val fakeFile = createSyntheticGgufFile(modelDir, modelName, sizeBytes = 1024)

        try {
            // Validator enforces 64MB minimum — 1KB file must fail
            val isValid = ModelFileValidator.isValidGguf(
                fakeFile,
                minSizeBytes = 64L * 1024L * 1024L
            )
            assertFalse(
                "Undersized GGUF file (1KB) must be rejected by validator",
                isValid
            )
        } finally {
            restoreModelFiles()
        }
    }

    @Test
    fun corrupt_wrongHeader_isRejectedByValidator() {
        // Create a file with wrong magic bytes
        modelDir.mkdirs()
        val modelName = "medgemma-4b-q4_k_m.gguf"
        val fakeFile = File(modelDir, modelName)
        fakeFile.writeBytes("NOT_GGUF_HEADER_PADDING".toByteArray())
        createdFiles.add(fakeFile)

        try {
            val isValid = ModelFileValidator.isValidGguf(
                fakeFile,
                minSizeBytes = 1L  // Low threshold to isolate header check
            )
            assertFalse(
                "File with wrong magic bytes must be rejected by validator",
                isValid
            )
        } finally {
            restoreModelFiles()
        }
    }

    @Test
    fun corrupt_nullFile_isRejectedByValidator() {
        assertFalse(
            "Null file must be rejected by validator",
            ModelFileValidator.isValidGguf(null)
        )
    }

    @Test
    fun corrupt_nonexistentFile_isRejectedByValidator() {
        val ghostFile = File(modelDir, "ghost_model_that_does_not_exist.gguf")
        assertFalse(
            "Nonexistent file must be rejected by validator",
            ModelFileValidator.isValidGguf(ghostFile)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Suite 3: SHA-256 trust — Valid header + wrong hash → rejected
    // ═══════════════════════════════════════════════════════════

    @Test
    fun trust_validHeaderWrongSha_isRejectedByValidator() {
        // Create a file with valid GGUF header but wrong SHA-256
        val fakeFile = createSyntheticGgufFile(modelDir, "test_sha_check.gguf", sizeBytes = 256)

        val isValid = ModelFileValidator.isValidGguf(
            fakeFile,
            minSizeBytes = 1L,  // Low threshold to isolate SHA check
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        assertFalse(
            "Valid GGUF header with wrong SHA-256 must be rejected",
            isValid
        )
        // Cleanup
        fakeFile.delete()
    }
}
