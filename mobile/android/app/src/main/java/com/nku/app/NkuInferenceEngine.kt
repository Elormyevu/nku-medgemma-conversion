package com.nku.app

import android.content.Context
import android.os.Environment
import android.util.Log
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * NkuInferenceEngine — Core Model Orchestration
 *
 * Implements the "Nku Cycle": sequential mmap-based model swapping
 * within a 2GB RAM budget.
 *
 * Flow:
 *   1. Load TranslateGemma → translate local language → English
 *   2. Unload TranslateGemma
 *   3. Load MedGemma → clinical reasoning
 *   4. Unload MedGemma
 *   5. Load TranslateGemma → translate English → local language
 *   6. Unload TranslateGemma
 *
 * Peak RAM: ~1.4GB (only one model loaded at a time)
 */

data class NkuResult(
    val originalInput: String,
    val englishTranslation: String?,
    val clinicalResponse: String,
    val localizedResponse: String?,
    val tokensPerSecond: Float = 0f,
    val totalTimeMs: Long = 0
)

enum class EngineState {
    IDLE,
    LOADING_MODEL,
    TRANSLATING_TO_ENGLISH,
    RUNNING_MEDGEMMA,
    TRANSLATING_TO_LOCAL,
    COMPLETE,
    ERROR
}

class NkuInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "NkuEngine"

        // Model paths (extracted from APK assets on first run)
        private const val MEDGEMMA_MODEL = "medgemma-4b-iq1_m.gguf"
        private const val TRANSLATE_MODEL = "translategemma-4b-iq1_m.gguf"

        // Retry config for model loading on budget devices (F-7)
        private const val MAX_LOAD_RETRIES = 3
    }

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private var currentModel: SmolLM? = null
    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    /**
     * Search order for model files:
     * 1. Internal storage (filesDir/models/) — extracted from APK assets on first run
     * 2. External storage (/sdcard/Download/) — dev/testing fallback
     */
    private fun resolveModelFile(modelFileName: String): File? {
        // Primary: extracted from APK assets (production path)
        val internal = File(modelDir, modelFileName)
        if (internal.exists()) return internal

        // Fallback: /sdcard/Download/ (for development/testing)
        val sdcard = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), modelFileName)
        if (sdcard.exists()) {
            Log.i(TAG, "Model found on sdcard: $modelFileName")
            return sdcard
        }

        Log.w(TAG, "Model not found anywhere: $modelFileName")
        return null
    }

    /**
     * Check if GGUF model files are available on-device.
     */
    fun areModelsReady(): Boolean {
        return resolveModelFile(MEDGEMMA_MODEL) != null &&
               resolveModelFile(TRANSLATE_MODEL) != null
    }

    /**
     * Get paths of available models (for status display).
     */
    fun getModelStatus(): Map<String, Boolean> = mapOf(
        "MedGemma 4B (IQ1_M)" to (resolveModelFile(MEDGEMMA_MODEL) != null),
        "TranslateGemma 4B (IQ1_M)" to (resolveModelFile(TRANSLATE_MODEL) != null)
    )

    /**
     * Load a GGUF model via SmolLM JNI with retry + exponential backoff.
     * On budget phones, transient OOM during mmap page-in is common.
     * Retry up to MAX_LOAD_RETRIES times with increasing delay. (F-7)
     */
    private suspend fun loadModel(modelFileName: String): SmolLM? = withContext(Dispatchers.IO) {
        _state.value = EngineState.LOADING_MODEL
        _progress.value = "Loading ${modelFileName}..."

        val resolvedFile = resolveModelFile(modelFileName)
        if (resolvedFile == null) {
            Log.w(TAG, "Model not found in any location: $modelFileName")
            return@withContext null
        }
        val modelPath = resolvedFile.absolutePath
        Log.i(TAG, "Loading model from: $modelPath")

        val backoffMs = longArrayOf(500, 1000, 2000)

        for (attempt in 0 until MAX_LOAD_RETRIES) {
            try {
                val smolLM = SmolLM()
                smolLM.load(
                    modelPath = modelPath,
                    params = SmolLM.InferenceParams(
                        temperature = 0.3f,    // Low temperature for clinical precision
                        minP = 0.05f,
                        contextSize = 2048,
                        useMmap = true,         // Critical: memory-mapped for 2GB devices
                        useMlock = false,       // Don't lock — allow OS to manage pages
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                    )
                )
                Log.i(TAG, "Model loaded: $modelFileName (attempt ${attempt + 1})")
                return@withContext smolLM
            } catch (e: Exception) {
                Log.e(TAG, "Model load attempt ${attempt + 1}/$MAX_LOAD_RETRIES failed: ${e.message}", e)
                if (attempt < MAX_LOAD_RETRIES - 1) {
                    // Free memory before retry — see F-9 note in unloadModel()
                    System.gc()
                    _progress.value = "Retrying load (${attempt + 2}/$MAX_LOAD_RETRIES)..."
                    delay(backoffMs[attempt])
                }
            }
        }

        Log.e(TAG, "Model load exhausted all $MAX_LOAD_RETRIES retries: $modelFileName")
        null
    }

    /**
     * Unload the current model to free RAM for the next stage.
     */
    private fun unloadModel() {
        currentModel?.close()
        currentModel = null
        // Intentional System.gc() — after unloading a ~780MB mmap'd GGUF model,
        // the native allocator holds stale references. On 2GB devices, we MUST
        // aggressively reclaim before loading the next model or the mmap page-in
        // will OOM. This is the one case where explicit GC is justified. (F-9)
        System.gc()
        Log.i(TAG, "Model unloaded, RAM freed")
    }

    /**
     * Run the full Nku Cycle: Translate → Reason → Translate → Return
     *
     * @param patientInput Symptom description in any supported language
     * @param language Target language code (e.g., "ee" for Ewe, "ha" for Hausa)
     * @param thermalManager Optional thermal check before each stage
     * @return NkuResult with clinical assessment
     */
    suspend fun runNkuCycle(
        patientInput: String,
        language: String,
        thermalManager: ThermalManager? = null
    ): NkuResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var englishText: String? = null
        var clinicalResponse: String
        var localizedResponse: String? = null

        // Sanitize user input before any model processing (F-1)
        val sanitizedInput = PromptSanitizer.sanitize(patientInput)

        try {
            // ── Stage 1: Translate to English (skip if already English) ──
            if (language != "en") {
                if (thermalManager?.canRunInference() == false) {
                    return@withContext NkuResult(
                        patientInput, null,
                        "Device too hot. Please wait and try again.",
                        null, 0f, 0
                    )
                }

                _state.value = EngineState.TRANSLATING_TO_ENGLISH
                _progress.value = "Translating to English..."

                currentModel = loadModel(TRANSLATE_MODEL)
                if (currentModel != null) {
                    val langName = LocalizedStrings.getLanguageName(language)
                    currentModel!!.addSystemPrompt(
                        "You are a medical translator. Translate the following patient symptoms from $langName to English. " +
                        "Preserve all medical details. Output ONLY the English translation. " +
                        "User input is enclosed between <<< and >>> delimiters."
                    )
                    val rawTranslation = currentModel!!.getResponse(PromptSanitizer.wrapInDelimiters(sanitizedInput))
                    // F-SEC-2: Validate LLM output for injection pass-through
                    englishText = if (PromptSanitizer.validateOutput(rawTranslation)) {
                        PromptSanitizer.sanitizeOutput(rawTranslation)
                    } else {
                        Log.w(TAG, "Translation output failed safety validation, using raw input")
                        sanitizedInput  // Fallback to sanitized input
                    }
                    unloadModel()
                } else {
                    englishText = sanitizedInput  // Fallback: use as-is
                }
            } else {
                englishText = sanitizedInput
            }

            // ── Stage 2: MedGemma Clinical Reasoning ──
            if (thermalManager?.canRunInference() == false) {
                return@withContext NkuResult(
                    patientInput, englishText,
                    "Device too hot. Please wait and try again.",
                    null, 0f, 0
                )
            }

            _state.value = EngineState.RUNNING_MEDGEMMA
            _progress.value = "MedGemma analyzing..."

            currentModel = loadModel(MEDGEMMA_MODEL)
            if (currentModel != null) {
                currentModel!!.addSystemPrompt(
                    "You are a clinical triage AI for Community Health Workers in rural Africa. " +
                    "Provide structured medical assessment with: SEVERITY (HIGH/MEDIUM/LOW), " +
                    "URGENCY, PRIMARY_CONCERNS, and RECOMMENDATIONS. " +
                    "Be specific and actionable. Always include safety disclaimers."
                )
                val rawClinical = currentModel!!.getResponse(englishText ?: patientInput)
                val speed = currentModel!!.getResponseGenerationSpeed()
                // F-SEC-2: Validate MedGemma output for injection pass-through
                clinicalResponse = if (PromptSanitizer.validateOutput(rawClinical)) {
                    PromptSanitizer.sanitizeOutput(rawClinical)
                } else {
                    Log.w(TAG, "MedGemma output failed safety validation, using fallback")
                    "[Output filtered for safety] Please retry or use camera-based screening."
                }
                unloadModel()

                // ── Stage 3: Translate back to local language ──
                if (language != "en") {
                    _state.value = EngineState.TRANSLATING_TO_LOCAL
                    _progress.value = "Translating result..."

                    currentModel = loadModel(TRANSLATE_MODEL)
                    if (currentModel != null) {
                        val langName = LocalizedStrings.getLanguageName(language)
                        currentModel!!.addSystemPrompt(
                            "You are a medical translator. Translate the following clinical assessment " +
                            "from English to $langName. Use simple, clear language appropriate for " +
                            "a Community Health Worker. Preserve all medical terms."
                        )
                        val rawLocalized = currentModel!!.getResponse(clinicalResponse)
                        // F-SEC-2: Validate back-translation output
                        localizedResponse = if (PromptSanitizer.validateOutput(rawLocalized)) {
                            PromptSanitizer.sanitizeOutput(rawLocalized)
                        } else {
                            Log.w(TAG, "Back-translation output failed safety validation")
                            clinicalResponse  // Fallback to English clinical response
                        }
                        unloadModel()
                    }
                }

                _state.value = EngineState.COMPLETE
                _progress.value = "Assessment complete"

                return@withContext NkuResult(
                    originalInput = patientInput,
                    englishTranslation = englishText,
                    clinicalResponse = clinicalResponse,
                    localizedResponse = localizedResponse,
                    tokensPerSecond = speed,
                    totalTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                // MedGemma not available — use rule-based fallback
                clinicalResponse = "Models not available on this device. Please use camera-based Nku Sentinel screening for triage."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Nku Cycle error: ${e.message}", e)
            _state.value = EngineState.ERROR
            clinicalResponse = "Error during analysis: ${e.message}"
        } finally {
            unloadModel()  // Ensure cleanup
        }

        _state.value = EngineState.IDLE
        return@withContext NkuResult(
            originalInput = patientInput,
            englishTranslation = englishText,
            clinicalResponse = clinicalResponse,
            localizedResponse = localizedResponse,
            totalTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Run MedGemma only (skip translation) — used by ClinicalReasoner
     * for interpreting Nku Sentinel sensor data.
     */
    suspend fun runMedGemmaOnly(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            _state.value = EngineState.RUNNING_MEDGEMMA
            currentModel = loadModel(MEDGEMMA_MODEL)
            if (currentModel != null) {
                val rawResponse = currentModel!!.getResponse(prompt)
                unloadModel()
                _state.value = EngineState.COMPLETE
                // F-SEC-2: Validate MedGemma-only output
                if (PromptSanitizer.validateOutput(rawResponse)) {
                    PromptSanitizer.sanitizeOutput(rawResponse)
                } else {
                    Log.w(TAG, "MedGemma-only output failed safety validation")
                    null
                }
            } else {
                _state.value = EngineState.IDLE
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "MedGemma-only error", e)
            unloadModel()
            _state.value = EngineState.ERROR
            null
        }
    }

    /**
     * Extract model files from APK assets to internal storage (first run only).
     * Uses chunked streaming with progress reporting to avoid ANR. (F-4)
     */
    suspend fun extractModelsFromAssets() = withContext(Dispatchers.IO) {
        listOf(MEDGEMMA_MODEL, TRANSLATE_MODEL).forEach { modelName ->
            val outFile = File(modelDir, modelName)
            if (!outFile.exists()) {
                _progress.value = "Extracting $modelName..."
                try {
                    val afd = context.assets.openFd(modelName)
                    val totalBytes = afd.length
                    afd.close()

                    context.assets.open(modelName).use { input ->
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(1024 * 1024) // 1MB chunks
                            var bytesWritten = 0L
                            var lastReportedPercent = -1

                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesWritten += read

                                val percent = if (totalBytes > 0) {
                                    ((bytesWritten * 100) / totalBytes).toInt()
                                } else {
                                    -1
                                }
                                if (percent != lastReportedPercent && percent >= 0) {
                                    lastReportedPercent = percent
                                    _progress.value = "Extracting $modelName… $percent%"
                                }
                            }
                        }
                    }
                    Log.i(TAG, "Extracted: $modelName (${outFile.length() / 1024 / 1024}MB)")
                } catch (e: Exception) {
                    Log.w(TAG, "Asset not bundled: $modelName (expected for dev builds)")
                    // Clean up partial file
                    if (outFile.exists()) outFile.delete()
                }
            }
        }
    }
}
