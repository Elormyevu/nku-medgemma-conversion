package com.nku.app

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies reviewer-style sideload integration for MedGemma model discovery.
 *
 * This test intentionally skips when no large GGUF model is present on-device.
 * It is meant for reviewer and pre-release device validation, not CI emulators.
 */
@RunWith(AndroidJUnit4::class)
class ModelIntegrationInstrumentedTest {

    @Test
    fun medGemma_sideloadedModel_isDiscoverableAndTrusted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "medgemma-4b-it-q4_k_m.gguf"
        )
        assumeTrue(
            "Requires a sideloaded MedGemma GGUF in app-specific external downloads directory",
            modelFile.exists() && modelFile.length() >= 1024L * 1024L * 1024L
        )

        val engine = NkuInferenceEngine(context)
        assertTrue("Sideloaded MedGemma model should be detected as ready", engine.areModelsReady())
    }
}
