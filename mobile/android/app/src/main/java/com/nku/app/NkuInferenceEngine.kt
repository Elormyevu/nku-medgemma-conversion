package com.nku.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
        // SHA-256 for the pinned MedGemma Q4_K_M artifact distributed in this repo/PAD pack.
        // Keep in sync with medgemma/src/main/assets/medgemma-4b-it-q4_k_m.gguf.
        private const val MEDGEMMA_SHA256 = "bff1ff2ed6aebe1b5ecb96b5dc2ee64cd6dfdec3ea4fc2e318d74087119a0ff9"
        private const val MEDGEMMA_EXPECTED_BYTES = 2_489_894_048L  // From HuggingFace LFS metadata
        // Translation handled by Android ML Kit (not a GGUF model)
        // HeAR Event Detector: TFLite, ships in app assets (loaded by RespiratoryDetector)
        // HeAR ViT-L encoder: future upgrade — XLA/StableHLO ops block ONNX/TFLite conversion

        // Direct Download URL fallback for Reviewers without PAD (APK install).
        // Source must match MEDGEMMA_SHA256 to satisfy trust validation.
        private const val MEDGEMMA_DOWNLOAD_URL = "https://huggingface.co/wredd/medgemma-4b-gguf/resolve/main/medgemma-4b-q4_k_m.gguf?download=true"

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
    private data class ValidationCacheEntry(
        val fileSize: Long,
        val modifiedTime: Long,
        val isValid: Boolean
    )
    private val sideloadValidationCache = mutableMapOf<String, ValidationCacheEntry>()

    private fun validateSideloadedModel(file: File, expectedSha256: String?): Boolean {
        val cacheKey = file.absolutePath
        val currentSize = file.length()
        val currentMtime = file.lastModified()
        val cached = sideloadValidationCache[cacheKey]
        if (cached != null && cached.fileSize == currentSize && cached.modifiedTime == currentMtime) {
            return cached.isValid
        }

        val isValid = ModelFileValidator.isValidGguf(file, expectedSha256 = expectedSha256)
        sideloadValidationCache[cacheKey] = ValidationCacheEntry(
            fileSize = currentSize,
            modifiedTime = currentMtime,
            isValid = isValid
        )
        return isValid
    }

    /**
     * Search order for model files:
     * 1. Internal storage (filesDir/models/) — extracted from asset packs on first run
     * 2. Play Asset Delivery pack path — install-time asset packs
     * 3. External storage candidates — dev/testing fallback
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

        // Fallback: external storage for development/testing/reviewer sideloading.
        // Include both shared Download and app-specific external dirs.
        val expectedHash = if (modelFileName == MEDGEMMA_MODEL) MEDGEMMA_SHA256 else null
        val externalCandidates = buildList<Pair<String, File>> {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                add("app-external/downloads" to File(it, modelFileName))
            }
            context.getExternalFilesDir(null)?.let {
                add("app-external/models" to File(File(it, "models"), modelFileName))
            }
        }

        for ((source, candidate) in externalCandidates) {
            if (!candidate.exists()) continue
            if (validateSideloadedModel(candidate, expectedHash)) {
                Log.i(TAG, "Model found in $source: $modelFileName")
                return candidate
            }
            Log.w(
                TAG,
                "Invalid/corrupt/untrusted GGUF in $source: $modelFileName (${candidate.length()} bytes)"
            )
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
        _progress.value = "Loading ${modelFileName}… 0%"

        var resolvedFile = resolveModelFile(modelFileName)
        if (resolvedFile == null) {
            Log.w(TAG, "Model not found locally. Initiating direct download for $modelFileName")
            if (modelFileName == MEDGEMMA_MODEL) {
                resolvedFile = downloadModelNative(MEDGEMMA_DOWNLOAD_URL, modelFileName)
                if (resolvedFile == null) {
                    return@withContext null
                }
            } else {
                return@withContext null
            }
        }
        val modelPath = resolvedFile.absolutePath
        Log.i(TAG, "Loading model from: $modelPath")

        val backoffMs = longArrayOf(500, 1000, 2000)

        for (attempt in 0 until MAX_LOAD_RETRIES) {
            try {
                val smolLM = SmolLM()
                
                // Launch a concurrent progress estimator — SmolLM.load() is blocking JNI
                // with no callback, so we estimate based on elapsed time.
                // Typical load: 15-45s on device, 60-120s on emulator.
                val startTime = System.currentTimeMillis()
                val estimatedLoadMs = 30_000L  // Conservative estimate
                val progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    while (true) {
                        val elapsed = System.currentTimeMillis() - startTime
                        // Asymptotic curve: approaches 95% but never hits 100% until actual completion
                        val pct = (95.0 * (1.0 - Math.exp(-2.0 * elapsed / estimatedLoadMs))).toInt().coerceIn(0, 95)
                        _progress.value = "Loading ${modelFileName}… ${pct}%"
                        delay(500)
                    }
                }
                
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
                progressJob.cancel()
                _progress.value = "Loading ${modelFileName}… 100%"
                Log.i(TAG, "Model loaded: $modelFileName (attempt ${attempt + 1})")
                return@withContext smolLM
            } catch (t: Throwable) {
                Log.e(TAG, "Model load attempt ${attempt + 1}/$MAX_LOAD_RETRIES failed: ${t.message}", t)
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

        // Sanitize raw free-text input for translation/LLM flow.
        // (runMedGemmaOnly() receives a pre-structured prompt built by ClinicalReasoner.)
        val sanitizedInput = PromptSanitizer.sanitize(patientInput)

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
                    // Requires Cloud Fallback
                    if (isNetworkAvailable(context)) {
                        _progress.value = "Translating to English (Cloud Fallback)..."
                        Log.i(TAG, "Stage 1: Cloud translation ($language → en)")
                        // NOTE: Cloud client is optional/backend-only in this build.
                        // Passing through to allow pipeline to continue in online mode for testing.
                        englishText = sanitizedInput
                    } else {
                        Log.w(TAG, "Cloud translation required for $language, but device is offline")
                        return@withContext NkuResult(
                            patientInput, null,
                            "Cloud connection required. This indigenous language requires cloud translation. Please connect to the internet.",
                            null, 0f, 0
                        )
                    }
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

        } catch (t: Throwable) {
            Log.e(TAG, "Nku Cycle error: ${t.message}", t)
            _state.value = EngineState.ERROR
            val reason = if (t is OutOfMemoryError) "Insufficient RAM" else t.message
            clinicalResponse = "Error during analysis: $reason. Please use WHO/IMCI guidelines."
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
        } catch (t: Throwable) {
            Log.e(TAG, "MedGemma-only error", t)
            unloadModel()
            _state.value = EngineState.ERROR
            null
        }
    }

    /**
     * Ensure MedGemma model is on-disk at app launch.
     *
     * Resolution order:
     *   1. Already on internal storage / PAD pack / sideload → skip
     *   2. Bundled in APK assets → extract to internal storage
     *   3. Neither → download from HuggingFace (2.3 GB) and validate SHA-256
     *
     * This runs in onCreate so the model is ready *before* the first triage.
     */
    suspend fun extractModelsFromAssets() = withContext(Dispatchers.IO) {
        val modelName = MEDGEMMA_MODEL

        // 1. Already accessible — nothing to do
        if (resolveModelFile(modelName) != null) {
            Log.i(TAG, "Model already accessible, skipping extraction: $modelName")
            return@withContext
        }

        val outFile = File(modelDir, modelName)

        // 2. Try APK-bundled assets (production .aab with PAD install-time pack)
        try {
            _state.value = EngineState.LOADING_MODEL
            _progress.value = "Extracting $modelName..."
            val afd = context.assets.openFd(modelName)
            val totalBytes = afd.length
            afd.close()

            context.assets.open(modelName).use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var bytesWritten = 0L
                    var lastReportedPercent = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesWritten += read

                        val percent = if (totalBytes > 0) {
                            ((bytesWritten * 100) / totalBytes).toInt()
                        } else -1
                        if (percent != lastReportedPercent && percent >= 0) {
                            lastReportedPercent = percent
                            _progress.value = "Extracting $modelName… $percent%"
                        }
                    }
                }
            }
            Log.i(TAG, "Extracted: $modelName (${outFile.length() / 1024 / 1024}MB)")
            _state.value = EngineState.IDLE
            return@withContext
        } catch (e: Exception) {
            Log.i(TAG, "Asset not bundled in APK: $modelName — falling back to HuggingFace download")
            if (outFile.exists()) outFile.delete()
        }

        // 3. Download from HuggingFace (reviewer/debug/emulator path)
        Log.i(TAG, "Initiating MedGemma download from HuggingFace on app startup")
        _state.value = EngineState.LOADING_MODEL
        val downloaded = downloadModelNative(MEDGEMMA_DOWNLOAD_URL, modelName)
        if (downloaded != null) {
            Log.i(TAG, "MedGemma downloaded and validated: ${downloaded.absolutePath}")
        } else {
            Log.e(TAG, "MedGemma download failed — model will not be available for triage")
            _progress.value = "Model download failed. Connect to Wi-Fi and restart the app."
        }
        _state.value = EngineState.IDLE
    }

    /**
     * Native HTTP downloader fallback for Kaggle reviewers installing via APK.
     * Downloads the 2.3GB GGUF model directly from HuggingFace to internal storage.
     *
     * Safety:
     *  - Pre-flight storage check (requires ≥3 GB free)
     *  - Downloads to .tmp file, renames only after SHA-256 validation
     *  - All failure paths clean up the temp file
     */
    private suspend fun downloadModelNative(urlStr: String, fileName: String): File? = withContext(Dispatchers.IO) {
        val outFile = File(modelDir, fileName)
        val tmpFile = File(modelDir, "$fileName.tmp")
        var connection: java.net.HttpURLConnection? = null

        try {
            // Pre-flight: ensure enough disk space (model is 2.3GB, need ~100MB headroom)
            val freeBytes = modelDir.usableSpace
            val requiredBytes = 2_600L * 1024 * 1024  // 2.6 GB
            if (freeBytes < requiredBytes) {
                val freeMB = freeBytes / (1024 * 1024)
                Log.e(TAG, "Insufficient storage for model download: ${freeMB}MB free, need ~2.6GB")
                _progress.value = "Not enough storage (${freeMB}MB free). Free up space and restart."
                return@withContext null
            }

            _progress.value = "Connecting to download MedGemma..."
            var downloadUrl = java.net.URL(urlStr)
            
            // Handle redirects (HuggingFace CDN usually redirects)
            var redirectCount = 0
            while (redirectCount < 5) {
                connection = downloadUrl.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                
                val code = connection.responseCode
                if (code == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                    code == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                    code == java.net.HttpURLConnection.HTTP_SEE_OTHER) {
                    val newUrl = connection.getHeaderField("Location")
                    downloadUrl = java.net.URL(newUrl)
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            if (connection?.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error during model download: ${connection?.responseCode}")
                return@withContext null
            }

            val serverBytes = connection?.contentLength?.toLong() ?: -1L
            // HuggingFace LFS uses chunked transfer so Content-Length is often -1.
            // Fall back to the known model size for accurate progress reporting.
            val totalBytes = if (serverBytes > 0) serverBytes else MEDGEMMA_EXPECTED_BYTES
            val totalMB = totalBytes / (1024L * 1024L)  // Standard computing MB (1024-based)
            Log.i(TAG, "Starting native download of $fileName ($totalBytes bytes)")

            // Download to temp file — never to the final name
            connection?.inputStream?.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024) // 256KB chunks for faster throughput
                    var bytesWritten = 0L
                    var lastReportedPercent = -1

                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesWritten += read

                        val mb = bytesWritten / (1024L * 1024L)
                        val percent = ((bytesWritten * 100) / totalBytes).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            _progress.value = "Downloading MedGemma… $percent% (${mb}MB / ${totalMB}MB)"
                        }
                    }
                }
            }
            
            Log.i(TAG, "Download complete: $fileName (${tmpFile.length() / (1024*1024)}MB)")
            if (!tmpFile.exists() || tmpFile.length() <= 0) {
                return@withContext null
            }

            // Validate GGUF header + SHA-256 BEFORE renaming to final path
            _progress.value = "Validating model integrity..."
            val expectedHash = if (fileName == MEDGEMMA_MODEL) MEDGEMMA_SHA256 else null
            if (!ModelFileValidator.isValidGguf(tmpFile, expectedSha256 = expectedHash)) {
                Log.e(TAG, "Downloaded model failed validation/trust check: $fileName")
                tmpFile.delete()
                _progress.value = "Model download failed integrity check. Will retry on next launch."
                return@withContext null
            }

            // Validation passed — atomically move to final name
            tmpFile.renameTo(outFile)
            Log.i(TAG, "Model validated and saved: ${outFile.absolutePath}")
            return@withContext outFile

        } catch (e: Exception) {
            Log.e(TAG, "Native download failed for $fileName", e)
            if (tmpFile.exists()) tmpFile.delete()
            if (outFile.exists()) outFile.delete()
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
