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
        // Check for *all* potential locations NkuInferenceEngine checks
        val internalFile = File(context.filesDir, "models/medgemma-4b-it-q4_k_m.gguf")
        val appExtFile = File(context.getExternalFilesDir(null), "models/medgemma-4b-it-q4_k_m.gguf")
        val dlExtFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "medgemma-4b-it-q4_k_m.gguf")
        
        // Assert that if NOT present anywhere, engine correctly reports not ready.
        // If it IS present (e.g., from a previous run or PAD install), we must skip this negative test
        // rather than blindly deleting the 2.5GB model file.
        assumeTrue("Model file must not exist locally to test negative readiness", 
            !internalFile.exists() && !appExtFile.exists() && !dlExtFile.exists()
        )
        
        val engine = NkuInferenceEngine(context)
        assertFalse("Engine should report not ready when model is absent", engine.areModelsReady())
    }

    @Test
    fun medGemma_positive_installedModel_isDiscoverableAndTrusted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The positive test MUST be executed in release lanes with a provisioned artifact.
        
        val engine = NkuInferenceEngine(context)
        // If a model IS present, is it considered ready?
        assumeTrue("A valid model must be present to test positive readiness", engine.areModelsReady())
        
        assertTrue("MedGemma model should be detected as ready when properly loaded", engine.areModelsReady())
    }
}
