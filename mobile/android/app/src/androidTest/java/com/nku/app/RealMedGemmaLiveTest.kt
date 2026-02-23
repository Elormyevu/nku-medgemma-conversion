package com.nku.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

@RunWith(AndroidJUnit4::class)
class RealMedGemmaLiveTest {

    @Test
    fun testRealMedGemmaInference() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NkuInferenceEngine(appContext)
        
        Log.i("RealMedGemmaLiveTest", "Checking if model is ready...")
        assertTrue("MedGemma model must be ready (sideloaded previously)", engine.areModelsReady())
        
        Log.i("RealMedGemmaLiveTest", "Starting inference cycle for Severe Malaria/Anemia...")
        
        // This simulates the ClinicalReasoner output that goes into runNkuCycle
        val symptoms = "Patient has severe fever, chills, vomiting, and yellowish eyes. Heart rate is 120 bpm. Conjunctival pallor is 85%."
        
        val result = engine.runNkuCycle(symptoms, "en")
        
        Log.i("RealMedGemmaLiveTest", "INFERENCE OUTPUT:\n${result.clinicalResponse}")
        
        assertTrue("Result must not be empty", result.clinicalResponse.isNotBlank())
        assertTrue(
            "Result must contain SEVERITY or URGENCY or fallbacks",
            result.clinicalResponse.contains("SEVERITY", ignoreCase = true) || 
            result.clinicalResponse.contains("filtered for safety")
        )
    }
}
