package com.nku.app

import android.content.Context
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies reviewer-style sideload integration for MedGemma model discovery.
 */
@RunWith(AndroidJUnit4::class)
class ModelIntegrationInstrumentedTest {

    @Test
    fun medGemma_negative_noModel_returnsNotReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "medgemma-4b-it-q4_k_m.gguf"
        )
        // Ensure the environment is clean for the negative test
        if (modelFile.exists()) {
            assumeTrue("Skipping negative test because model is present", false)
        }
        val engine = NkuInferenceEngine(context)
        assertFalse("Engine should report not ready when model is absent", engine.areModelsReady())
    }

    @Test
    fun medGemma_positive_sideloadedModel_isDiscoverableAndTrusted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "medgemma-4b-it-q4_k_m.gguf"
        )
        // The positive test MUST be executed in release lanes with a provisioned artifact.
        // We use assumeTrue so local dev runs gracefully skip, but CI gates should
        // enforce that this test actually ran (status=passed, not skipped).
        assumeTrue(
            "Requires a sideloaded MedGemma GGUF in app-specific external downloads directory",
            modelFile.exists() && modelFile.length() >= 1024L * 1024L * 1024L
        )

        val engine = NkuInferenceEngine(context)
        assertTrue("Sideloaded MedGemma model should be detected as ready", engine.areModelsReady())
    }
}
