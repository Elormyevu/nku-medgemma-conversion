package com.nku.app

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * NkuInferenceEngine — Core Model Orchestration
 *
 * Implements the "Nku Cycle": MedGemma clinical reasoning with
 * ML Kit handling translation separately.
 *
 * Flow:
 *   1. ML Kit translates local language → English (on-device; unsupported languages pass through raw)
 *   2. Load MedGemma → clinical reasoning (100% on-device)
 *   3. Unload MedGemma
 *   4. ML Kit translates English → local language
 *
 * Peak RAM: ~2.3GB (MedGemma Q4_K_M active)
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

        // MedGemma 4B Q4_K_M — the only model used for clinical reasoning.
        // Filename matches HuggingFace artifact (hungqbui/medgemma-4b-it-Q4_K_M-GGUF).
        private const val MEDGEMMA_MODEL = "medgemma-4b-it-q4_k_m.gguf"
        // Translation handled by Android ML Kit (not a GGUF model)
        // HeAR Event Detector: TFLite, ships in app assets (loaded by RespiratoryDetector)
        // HeAR ViT-L encoder: future upgrade — XLA/StableHLO ops block ONNX/TFLite conversion

        // Play Asset Delivery pack names → model file mapping
        private val MODEL_PACK_MAP = mapOf(
            MEDGEMMA_MODEL to "medgemma"
        )

        // Retry config for model loading on budget devices (F-7)
        private const val MAX_LOAD_RETRIES = 3
    }

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private var currentModel: SmolLM? = null
    private val nkuTranslator: NkuTranslator by lazy { NkuTranslator(context) }
    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }
    private val assetPackManager by lazy {
        AssetPackManagerFactory.getInstance(context)
    }

    /**
     * Search order for model files:
     * 1. Internal storage (filesDir/models/) — extracted from asset packs on first run
     * 2. Play Asset Delivery pack path — install-time asset packs
     * 3. External storage (/sdcard/Download/) — dev/testing fallback
     */
    private fun resolveModelFile(modelFileName: String): File? {
        // Primary: extracted from asset packs (cached in internal storage)
        val internal = File(modelDir, modelFileName)
        if (internal.exists()) {
            if (ModelFileValidator.isValidGguf(internal)) {
                return internal
            }
            Log.w(TAG, "Invalid/corrupt GGUF in internal storage: $modelFileName (${internal.length()} bytes)")
        }

        // Secondary: Play Asset Delivery install-time asset pack
        val packName = MODEL_PACK_MAP[modelFileName]
        if (packName != null) {
            try {
                val packLocation = assetPackManager.getPackLocation(packName)
                if (packLocation != null) {
                    val packFile = File(packLocation.assetsPath(), modelFileName)
                    if (packFile.exists() && ModelFileValidator.isValidGguf(packFile)) {
                        Log.i(TAG, "Model found in PAD pack '$packName': $modelFileName")
                        return packFile
                    } else if (packFile.exists()) {
                        Log.w(TAG, "Invalid/corrupt GGUF in PAD pack '$packName': $modelFileName (${packFile.length()} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "PAD pack lookup failed for $packName: ${e.message}")
            }
        }

        // Fallback: /sdcard/Download/ (for development/testing/sideloading)
        val sdcard = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), modelFileName)
        
        // F9 protection: sideloaded models must match expected cryptographic checksum.
        // Using placeholder hash. Replace with actual MedGemma Q4_K_M SHA-256 for production sideloading.
        val expectedHash = if (modelFileName == MEDGEMMA_MODEL) "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" else null
        
        if (sdcard.exists() && ModelFileValidator.isValidGguf(sdcard, expectedSha256 = expectedHash)) {
            Log.i(TAG, "Model found on sdcard: $modelFileName")
            return sdcard
        } else if (sdcard.exists()) {
            Log.w(TAG, "Invalid/corrupt/untrusted GGUF on sdcard: $modelFileName (${sdcard.length()} bytes)")
        }

        Log.w(TAG, "Model not found anywhere: $modelFileName")
        return null
    }

    /**
     * Check if GGUF model file is available on-device.
     */
    fun areModelsReady(): Boolean {
        return resolveModelFile(MEDGEMMA_MODEL) != null
        // Translation handled by Android ML Kit (no GGUF model needed)
    }

    /**
     * Get paths of available models (for status display).
     */
    fun getModelStatus(): Map<String, Boolean> = mapOf(
        "MedGemma 4B (Q4_K_M)" to (resolveModelFile(MEDGEMMA_MODEL) != null),
        "ML Kit Translation" to true  // ML Kit is always available via SDK
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
        // Intentional System.gc() — after unloading a ~2.3GB mmap'd Q4_K_M GGUF model,
        // the native allocator holds stale references. On budget devices, we MUST
        // aggressively reclaim before any subsequent operations or the next inference
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

        // Finding 4 fix: Do NOT sanitize patientInput here — ClinicalReasoner.generatePrompt()
        // already sanitizes individual user symptoms (line 115). Re-sanitizing the full prompt
        // would strip legitimate instruction tokens (SEVERITY:, URGENCY:, etc.) that guide
        // MedGemma's structured output format.
        val sanitizedInput = patientInput

        try {
            // ── Stage 1: Translate to English via ML Kit (skip if already English) ──
            if (language != "en") {
                if (thermalManager?.canRunInference() == false) {
                    return@withContext NkuResult(
                        patientInput, null,
                        "Device too hot. Please wait and try again.",
                        null, 0f, 0
                    )
                }

                _state.value = EngineState.TRANSLATING_TO_ENGLISH
                if (NkuTranslator.isOnDeviceSupported(language)) {
                    _progress.value = "Translating to English (ML Kit, on-device)..."
                    Log.i(TAG, "Stage 1: ML Kit on-device translation ($language → en)")
                    val translated = nkuTranslator.translateToEnglish(sanitizedInput, language)
                    if (translated != null) {
                        englishText = translated
                    } else {
                        Log.w(TAG, "ML Kit translation failed for $language, using raw input")
                        _progress.value = "Translation failed — processing directly..."
                        englishText = sanitizedInput
                    }
                } else {
                    // Unsupported on-device and no cloud client in current mobile build.
                    Log.w(TAG, "ML Kit unsupported for $language on-device — processing raw input")
                    _progress.value = "On-device translation unavailable for this language — processing directly..."
                    englishText = sanitizedInput
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

                // ── Stage 3: Translate back to local language via ML Kit ──
                if (language != "en") {
                    _state.value = EngineState.TRANSLATING_TO_LOCAL
                    if (NkuTranslator.isOnDeviceSupported(language)) {
                        _progress.value = "Translating result (ML Kit, on-device)..."
                        val translated = nkuTranslator.translateFromEnglish(clinicalResponse, language)
                        localizedResponse = translated ?: clinicalResponse  // Fallback to English
                    } else {
                        _progress.value = "On-device result translation unavailable — returning English result..."
                        localizedResponse = clinicalResponse
                    }
                }

                _state.value = EngineState.COMPLETE
                _progress.value = "Assessment complete"
                delay(500)  // P0 fix: let UI display "complete" briefly before re-enabling Run button
                _state.value = EngineState.IDLE

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
                delay(500)  // P0 fix: reset to IDLE so subsequent runs are possible
                _state.value = EngineState.IDLE
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
     * With Play Asset Delivery, install-time packs provide direct filesystem
     * access — extraction is only needed for legacy APK-bundled assets. (F-4)
     */
    suspend fun extractModelsFromAssets() = withContext(Dispatchers.IO) {
        // Only MedGemma needs extraction — translation is handled by ML Kit SDK
        val modelName = MEDGEMMA_MODEL

        // Skip if model is already accessible (filesDir, PAD pack, or sdcard)
        if (resolveModelFile(modelName) != null) {
            Log.i(TAG, "Model already accessible, skipping extraction: $modelName")
            return@withContext
        }

        val outFile = File(modelDir, modelName)
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
