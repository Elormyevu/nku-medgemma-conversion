package com.nku.app

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Respiratory Risk Classification.
 * Maps HeAR event detector output to clinical categories.
 */
enum class RespiratoryRisk {
    NORMAL,         // No concerning respiratory patterns
    LOW_RISK,       // Minor anomalies, routine follow-up
    MODERATE_RISK,  // Patterns suggestive of respiratory illness
    HIGH_RISK       // Patterns strongly suggestive of TB or severe respiratory disease
}

/**
 * Identifies which analysis tier produced the result.
 */
enum class AnalysisSource {
    EVENT_DETECTOR,  // HeAR Event Detector only (MobileNetV3, TFLite)
    VIT_L_ENCODER,   // Full HeAR ViT-L encoder (ONNX Runtime) + Event Detector
    HEURISTIC        // Audio heuristic fallback (no ML model available)
}

/**
 * Result from respiratory audio analysis.
 */
data class RespiratoryResult(
    val riskScore: Float = 0f,              // 0.0 = healthy, 1.0 = high risk
    val classification: RespiratoryRisk = RespiratoryRisk.NORMAL,
    val confidence: Float = 0f,             // 0.0–1.0 analysis confidence
    val coughDetected: Boolean = false,     // Whether cough events were found in audio
    val coughCount: Int = 0,                // Number of cough events detected
    val audioQuality: String = "unknown",   // "good", "noisy", "too_quiet", "unknown"
    val soundClassScores: Map<String, Float> = emptyMap(),  // Per-class probabilities from HeAR

    // ── ViT-L encoder fields (null when encoder unavailable or cough not detected) ──
    val embedding: FloatArray? = null,      // 512-dim HeAR health acoustic embedding
    val analysisSource: AnalysisSource = AnalysisSource.HEURISTIC
) {
    /** Embedding dimension for the HeAR ViT-L encoder. */
    companion object {
        const val EMBEDDING_DIM = 512
    }

    // Override equals/hashCode because FloatArray doesn't have structural equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RespiratoryResult) return false
        return riskScore == other.riskScore &&
                classification == other.classification &&
                confidence == other.confidence &&
                coughDetected == other.coughDetected &&
                coughCount == other.coughCount &&
                audioQuality == other.audioQuality &&
                soundClassScores == other.soundClassScores &&
                java.util.Arrays.equals(embedding, other.embedding) &&
                analysisSource == other.analysisSource
    }

    override fun hashCode(): Int {
        var result = riskScore.hashCode()
        result = 31 * result + classification.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + coughDetected.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + analysisSource.hashCode()
        return result
    }

    /** Helper: true if the ViT-L embedding is present and valid. */
    fun hasEmbedding(): Boolean = embedding != null && embedding.size == EMBEDDING_DIM
}

/**
 * RespiratoryDetector — Two-tier HeAR respiratory/TB screening from cough audio.
 *
 * ## Architecture (Two-Tier, Sequential Loading)
 *
 *   Audio (16kHz mono, 2s)
 *       → Tier 1: HeAR Event Detector (MobileNetV3, TFLite, 1.1MB, always loaded)
 *           → 8 class probabilities (Cough, Snore, Baby Cough, Breathe, ...)
 *       → if cough probability ≥ 0.3:
 *           → Tier 2: HeAR ViT-L Encoder (ONNX Runtime, ~300MB INT8, loaded on demand)
 *               → 512-dim health acoustic embedding
 *               → Encoder unloaded immediately after (frees ~350MB RAM)
 *       → Risk assessment + optional embedding passed to ClinicalReasoner/MedGemma
 *
 * ## Sequential Loading
 *
 * At most ONE large model is in RAM at any time:
 *   - Event Detector (1.1MB) — always loaded, negligible RAM
 *   - ViT-L Encoder (~300MB) — loaded on demand, unloaded before MedGemma
 *   - MedGemma (2.3GB) — loaded separately by NkuInferenceEngine
 *
 * Peak RAM never exceeds MedGemma's 2.3GB.
 *
 * ## Models
 *
 * - **Event Detector**: MobileNetV3-Small, TFLite INT8 (1.14MB)
 *   Input: 1×32000 float32 (2s @16kHz) → Output: 1×8 class probabilities
 *
 * - **ViT-L Encoder**: Vision Transformer Large (Masked AutoEncoder)
 *   ONNX Runtime, INT8 quantized (~300MB)
 *   Input: 1×32000 float32 (2s @16kHz) → Output: 1×512 float32 embedding
 *   Trained on 300M+ health audio clips (Tobin et al., arXiv:2403.02522)
 *
 * Reference: Tobin et al., "HeAR — Health Acoustic Representations"
 *   arXiv:2403.02522, 2024.
 */
class RespiratoryDetector(private val context: Context? = null) {

    companion object {
        private const val TAG = "RespiratoryDetector"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val CLIP_DURATION_SAMPLES = TARGET_SAMPLE_RATE * 2  // 2 seconds = 32000 samples

        // HeAR event detector model files (in assets/)
        private const val MODEL_FP32 = "event_detector_small_fp32.tflite"
        private const val MODEL_INT8 = "event_detector_small_int8.tflite"

        // HeAR ViT-L encoder model file (delivered via Play Asset Delivery)
        private const val VIT_L_MODEL_FILENAME = "hear_encoder_int8.onnx"

        // HeAR sound class labels (in order of model output)
        val LABELS = arrayOf(
            "Cough", "Snore", "Baby Cough", "Breathe",
            "Sneeze", "Throat Clear", "Laugh", "Speech"
        )
        private const val NUM_CLASSES = 8
        private const val EMBEDDING_DIM = 512

        // Risk classification thresholds
        private const val HIGH_RISK_THRESHOLD = 0.75f
        private const val MODERATE_RISK_THRESHOLD = 0.50f
        private const val LOW_RISK_THRESHOLD = 0.25f

        // Cough threshold to trigger ViT-L deep analysis
        private const val COUGH_TRIGGER_THRESHOLD = 0.3f

        // Audio quality thresholds
        private const val MIN_AUDIO_RMS = 0.01f
        private const val MAX_NOISE_FLOOR = 0.80f
    }

    private val _result = MutableStateFlow(RespiratoryResult())
    val result: StateFlow<RespiratoryResult> = _result.asStateFlow()

    // TFLite interpreter — lazy-initialized on first use (Event Detector)
    private var interpreter: Interpreter? = null
    private var usesTflite: Boolean = false

    // ViT-L ONNX encoder availability
    private var vitLModelPath: String? = null

    init {
        if (context != null) {
            loadModel()
            discoverViTLEncoder()
        }
    }

    /**
     * Load HeAR event detector TFLite model from assets.
     * Tries INT8 first (1.14MB, fastest), falls back to FP32 (3.83MB).
     */
    private fun loadModel() {
        val ctx = context ?: return
        try {
            // Prefer INT8 for smaller size and faster inference on mobile
            val modelBuffer = try {
                loadModelFile(ctx.assets, MODEL_INT8).also {
                    Log.i(TAG, "Loaded HeAR event detector (INT8, ~1.1MB)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "INT8 model not found, trying FP32: ${e.message}")
                loadModelFile(ctx.assets, MODEL_FP32).also {
                    Log.i(TAG, "Loaded HeAR event detector (FP32, ~3.8MB)")
                }
            }

            val options = Interpreter.Options().apply {
                setNumThreads(2)  // Budget phones: 2 threads is safe
            }
            interpreter = Interpreter(modelBuffer, options)
            usesTflite = true

            // Verify model I/O
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            Log.i(TAG, "HeAR model loaded. Input: ${inputTensor.shape().toList()}, " +
                    "Output: ${outputTensor.shape().toList()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load HeAR TFLite model, falling back to heuristics: ${e.message}", e)
            usesTflite = false
        }
    }

    /**
     * Discover HeAR ViT-L encoder ONNX model.
     *
     * NOTE: The ViT-L encoder is a future upgrade path. Google's HeAR ViT-L
     * uses XlaCallModule/StableHLO ops that cannot be converted to ONNX or TFLite
     * by any current tool. The model validates correctly in TF eager mode but the
     * serialized StableHLO bytecode has no ONNX/TFLite equivalent.
     *
     * For now, the app ships with the Event Detector only (MobileNetV3, 1.1MB TFLite).
     * If a pre-converted ViT-L model is placed on device storage, this function will
     * discover and use it.
     *
     * Search order:
     * 1. Internal storage (filesDir)
     * 2. External storage (/sdcard/Download/) — dev/testing fallback
     */
    private fun discoverViTLEncoder() {
        val ctx = context ?: return
        val searchPaths = listOf(
            File(ctx.filesDir, VIT_L_MODEL_FILENAME),
            File("/sdcard/Download/$VIT_L_MODEL_FILENAME"),
            File(ctx.getExternalFilesDir(null), VIT_L_MODEL_FILENAME)
        )

        for (path in searchPaths) {
            if (path.exists() && path.length() > 0) {
                vitLModelPath = path.absolutePath
                val sizeMb = path.length() / (1024.0 * 1024.0)
                Log.i(TAG, "HeAR ViT-L encoder discovered: ${path.absolutePath} (${String.format("%.1f", sizeMb)} MB)")
                return
            }
        }

        Log.i(TAG, "HeAR ViT-L encoder not found on device — Event Detector only mode")
    }

    /**
     * Check if the ViT-L encoder model is available on device.
     */
    fun isViTLAvailable(): Boolean = vitLModelPath != null

    private fun loadModelFile(assets: AssetManager, filename: String): MappedByteBuffer {
        val fd = assets.openFd(filename)
        val fis = FileInputStream(fd.fileDescriptor)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /**
     * Process raw audio buffer for respiratory screening (Tier 1 only — synchronous).
     *
     * Uses the Event Detector for quick classification. For deep analysis with
     * ViT-L embeddings, use [processAudioDeep] instead.
     *
     * @param audioBuffer PCM audio samples (16-bit signed integers)
     * @param sampleRate Sample rate of the input audio
     */
    fun processAudio(audioBuffer: ShortArray, sampleRate: Int) {
        if (audioBuffer.isEmpty()) {
            Log.w(TAG, "Empty audio buffer received")
            _result.value = RespiratoryResult(audioQuality = "no_audio", confidence = 0f)
            return
        }

        try {
            // Step 1: Convert to float and normalize
            val floatSamples = normalizeAudio(audioBuffer)

            // Step 2: Resample to 16kHz if needed
            val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
                resample(floatSamples, sampleRate, TARGET_SAMPLE_RATE)
            } else {
                floatSamples
            }

            // Step 3: Assess audio quality
            val quality = assessAudioQuality(resampled)

            // Step 4: Pad/trim to exactly 2 seconds (32000 samples)
            val clip = selectBest2SecondWindow(resampled)

            // Step 5: Run HeAR inference or heuristic fallback
            val (riskScore, confidence, classScores) = if (usesTflite && interpreter != null) {
                runHeARInference(clip)
            } else {
                runHeuristicFallback(clip)
            }

            // Step 6: Determine cough detection from class scores
            val coughScore = classScores.getOrDefault("Cough", 0f)
            val babyCoughScore = classScores.getOrDefault("Baby Cough", 0f)
            val coughDetected = coughScore > COUGH_TRIGGER_THRESHOLD || babyCoughScore > COUGH_TRIGGER_THRESHOLD

            // Step 7: Classify risk
            val classification = classifyRisk(riskScore)

            val source = if (usesTflite && interpreter != null) {
                AnalysisSource.EVENT_DETECTOR
            } else {
                AnalysisSource.HEURISTIC
            }

            _result.value = RespiratoryResult(
                riskScore = riskScore,
                classification = classification,
                confidence = confidence,
                coughDetected = coughDetected,
                coughCount = if (coughDetected) 1 else 0,
                audioQuality = quality,
                soundClassScores = classScores,
                embedding = null,  // No ViT-L in sync path
                analysisSource = source
            )

            Log.d(TAG, "Respiratory analysis [${source.name}]: " +
                    "risk=$riskScore, class=$classification, cough=$coughScore, " +
                    "quality=$quality, confidence=$confidence")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio: ${e.message}", e)
            _result.value = RespiratoryResult(audioQuality = "error", confidence = 0f)
        }
    }

    /**
     * Process audio with full two-tier HeAR pipeline (Event Detector + ViT-L encoder).
     *
     * This is the deep analysis coroutine path:
     * 1. Event Detector classifies audio (always, ~50ms)
     * 2. If cough detected AND ViT-L available:
     *    - Load ViT-L ONNX encoder (~300MB)
     *    - Extract 512-dim health acoustic embedding (~5-10s on mobile)
     *    - Unload encoder (free ~350MB RAM)
     * 3. Result includes embedding for MedGemma clinical reasoning
     *
     * @param audioBuffer PCM audio samples (16-bit signed integers)
     * @param sampleRate Sample rate of the input audio
     * @return RespiratoryResult with optional 512-dim embedding
     */
    suspend fun processAudioDeep(audioBuffer: ShortArray, sampleRate: Int): RespiratoryResult {
        if (audioBuffer.isEmpty()) {
            return RespiratoryResult(audioQuality = "no_audio", confidence = 0f)
        }

        return withContext(Dispatchers.Default) {
            try {
                // Step 1: Preprocess audio
                val floatSamples = normalizeAudio(audioBuffer)
                val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
                    resample(floatSamples, sampleRate, TARGET_SAMPLE_RATE)
                } else {
                    floatSamples
                }
                val quality = assessAudioQuality(resampled)
                val clip = selectBest2SecondWindow(resampled)

                // Step 2: Tier 1 — Event Detector
                val (riskScore, confidence, classScores) = if (usesTflite && interpreter != null) {
                    runHeARInference(clip)
                } else {
                    runHeuristicFallback(clip)
                }

                val coughScore = classScores.getOrDefault("Cough", 0f)
                val babyCoughScore = classScores.getOrDefault("Baby Cough", 0f)
                val coughDetected = coughScore > COUGH_TRIGGER_THRESHOLD || babyCoughScore > COUGH_TRIGGER_THRESHOLD

                // Step 3: Tier 2 — ViT-L encoder (only if cough detected AND model available)
                var embedding: FloatArray? = null
                var source = if (usesTflite) AnalysisSource.EVENT_DETECTOR else AnalysisSource.HEURISTIC

                if (coughDetected && vitLModelPath != null) {
                    Log.i(TAG, "Cough detected (score=$coughScore) — loading ViT-L encoder for deep analysis")
                    embedding = runViTLEncoder(clip)
                    if (embedding != null) {
                        source = AnalysisSource.VIT_L_ENCODER
                        Log.i(TAG, "ViT-L embedding extracted: ${embedding.size}-dim, " +
                                "L2 norm=${calculateL2Norm(embedding)}")
                    }
                }

                // Step 4: Build result
                val classification = classifyRisk(riskScore)
                val finalConfidence = if (source == AnalysisSource.VIT_L_ENCODER) {
                    // Boost confidence when ViT-L embedding is available
                    (confidence * 1.2f).coerceAtMost(1.0f)
                } else {
                    confidence
                }

                val result = RespiratoryResult(
                    riskScore = riskScore,
                    classification = classification,
                    confidence = finalConfidence,
                    coughDetected = coughDetected,
                    coughCount = if (coughDetected) 1 else 0,
                    audioQuality = quality,
                    soundClassScores = classScores,
                    embedding = embedding,
                    analysisSource = source
                )

                _result.value = result

                Log.d(TAG, "Deep respiratory analysis [${source.name}]: " +
                        "risk=$riskScore, class=$classification, cough=$coughScore, " +
                        "quality=$quality, confidence=$finalConfidence, " +
                        "hasEmbedding=${embedding != null}")

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error in deep audio processing: ${e.message}", e)
                RespiratoryResult(audioQuality = "error", confidence = 0f)
            }
        }
    }

    // ── ViT-L Encoder (ONNX Runtime) ────────────────────────────────

    /**
     * Run the HeAR ViT-L encoder to extract a 512-dim health acoustic embedding.
     *
     * Lifecycle:
     * 1. Create OrtEnvironment + OrtSession (loads model, ~2-5s)
     * 2. Run inference (extract embedding, ~3-8s on mobile)
     * 3. Close session + environment (frees ~350MB RAM)
     *
     * The model is NEVER kept loaded — sequential loading ensures no RAM
     * contention with MedGemma.
     *
     * @param audioClip Preprocessed audio: 32000 float samples (2s @16kHz)
     * @return 512-dim embedding, or null if inference fails
     */
    private fun runViTLEncoder(audioClip: FloatArray): FloatArray? {
        val modelPath = vitLModelPath ?: return null
        var env: OrtEnvironment? = null
        var session: OrtSession? = null

        try {
            val startTime = System.nanoTime()

            // Create ONNX Runtime environment and session
            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)  // Budget phones: 2 threads
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(modelPath, sessionOptions)

            val loadTimeMs = (System.nanoTime() - startTime) / 1_000_000.0
            Log.d(TAG, "ViT-L encoder loaded in ${loadTimeMs}ms")

            // Prepare input tensor: [1, 32000] float32
            val inputShape = longArrayOf(1, audioClip.size.toLong())
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioClip),
                inputShape
            )

            // Run inference
            val inferenceStart = System.nanoTime()
            val results = session.run(mapOf("x" to inputTensor))
            val inferenceTimeMs = (System.nanoTime() - inferenceStart) / 1_000_000.0
            Log.d(TAG, "ViT-L inference completed in ${inferenceTimeMs}ms")

            // Extract embedding — output is [1, 512] float32
            val outputTensor = results[0] as OnnxTensor
            val outputData = outputTensor.floatBuffer
            val embedding = FloatArray(EMBEDDING_DIM)
            outputData.get(embedding)

            // Validate embedding
            if (embedding.all { it == 0f }) {
                Log.w(TAG, "ViT-L produced all-zero embedding — may indicate model issue")
                return null
            }

            val totalTimeMs = (System.nanoTime() - startTime) / 1_000_000.0
            Log.i(TAG, "ViT-L total: ${totalTimeMs}ms (load=${loadTimeMs}ms, " +
                    "infer=${inferenceTimeMs}ms)")

            // Cleanup input tensor
            inputTensor.close()
            results.close()

            return embedding

        } catch (e: Exception) {
            Log.e(TAG, "ViT-L encoder inference failed: ${e.message}", e)
            return null
        } finally {
            // CRITICAL: Always close session and environment to free ~350MB RAM
            try { session?.close() } catch (_: Exception) {}
            try { env?.close() } catch (_: Exception) {}
            Log.d(TAG, "ViT-L encoder resources released")
        }
    }

    /**
     * Run HeAR event detector TFLite inference.
     *
     * Input: 1×32000 float32 (2s audio at 16kHz)
     * Output: 1×8 float32 (class probabilities)
     *
     * @return Triple(riskScore, confidence, classScores)
     */
    private fun runHeARInference(clip: FloatArray): Triple<Float, Float, Map<String, Float>> {
        val interp = interpreter ?: return runHeuristicFallback(clip)

        // Prepare input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * CLIP_DURATION_SAMPLES)
            .order(ByteOrder.nativeOrder())
        for (sample in clip) {
            inputBuffer.putFloat(sample)
        }
        inputBuffer.rewind()

        // Prepare output buffer (8 classes)
        val outputBuffer = ByteBuffer.allocateDirect(4 * NUM_CLASSES)
            .order(ByteOrder.nativeOrder())

        // Run inference
        val startTime = System.nanoTime()
        interp.run(inputBuffer, outputBuffer)
        val latencyMs = (System.nanoTime() - startTime) / 1_000_000.0
        Log.d(TAG, "HeAR inference: ${latencyMs}ms")

        // Parse output
        outputBuffer.rewind()
        val classScores = mutableMapOf<String, Float>()
        for (i in 0 until NUM_CLASSES) {
            classScores[LABELS[i]] = outputBuffer.getFloat()
        }

        // Calculate risk score from relevant classes
        // Cough + Baby Cough = respiratory concern indicators
        // Breathe patterns also relevant (abnormal breathing)
        val coughProb = classScores.getOrDefault("Cough", 0f)
        val babyCoughProb = classScores.getOrDefault("Baby Cough", 0f)
        val breatheProb = classScores.getOrDefault("Breathe", 0f)

        // Risk score: weighted combination of respiratory-relevant classes
        // Cough is primary indicator, breathing patterns secondary
        val riskScore = (coughProb * 0.5f + babyCoughProb * 0.3f + breatheProb * 0.2f)
            .coerceIn(0f, 1f)

        // Confidence based on how decisively the model classifies
        val maxProb = classScores.values.maxOrNull() ?: 0f
        val confidence = maxProb.coerceIn(0f, 1f)

        return Triple(riskScore, confidence, classScores)
    }

    /**
     * Heuristic fallback when TFLite model is unavailable.
     * Uses audio features as a functional stand-in.
     */
    private fun runHeuristicFallback(clip: FloatArray): Triple<Float, Float, Map<String, Float>> {
        val rms = calculateRMS(clip)
        val zcr = calculateZeroCrossingRate(clip)

        // Simple energy + ZCR heuristic
        val energyScore = (rms / 0.3f).coerceIn(0f, 1f)
        val zcrScore = (zcr * 2f).coerceIn(0f, 1f)
        val riskScore = (energyScore * 0.4f + zcrScore * 0.6f).coerceIn(0f, 1f)
        val confidence = 0.4f  // Low confidence for heuristic approach

        val classScores = mapOf(
            "Cough" to energyScore * 0.5f,
            "Breathe" to zcrScore * 0.3f,
            "Speech" to (1f - energyScore) * 0.2f
        )

        return Triple(riskScore, confidence, classScores)
    }

    // ── Audio Processing Utilities ─────────────────────────────────

    /**
     * Select the best 2-second window (32000 samples at 16kHz) from the audio.
     *
     * For inputs longer than 2 seconds (e.g., the 5s cough recording), this
     * slides a 2s window and picks the segment with the highest RMS energy —
     * most likely to contain the cough. This avoids silently discarding
     * coughs that occur after the first 2 seconds.
     *
     * For inputs exactly 2s: returned as-is.
     * For inputs shorter than 2s: zero-padded to 32000 samples.
     */
    private fun selectBest2SecondWindow(samples: FloatArray): FloatArray {
        return when {
            samples.size == CLIP_DURATION_SAMPLES -> samples
            samples.size > CLIP_DURATION_SAMPLES -> {
                // Slide a 2s window across the buffer, pick highest energy
                var bestStart = 0
                var bestEnergy = -1.0

                // Step size: 1600 samples (100ms) for reasonable granularity
                val stepSize = TARGET_SAMPLE_RATE / 10
                var offset = 0
                while (offset + CLIP_DURATION_SAMPLES <= samples.size) {
                    var sumSq = 0.0
                    for (i in offset until offset + CLIP_DURATION_SAMPLES) {
                        sumSq += samples[i] * samples[i]
                    }
                    if (sumSq > bestEnergy) {
                        bestEnergy = sumSq
                        bestStart = offset
                    }
                    offset += stepSize
                }

                samples.sliceArray(bestStart until bestStart + CLIP_DURATION_SAMPLES)
            }
            else -> {
                // Zero-pad to 2 seconds
                val padded = FloatArray(CLIP_DURATION_SAMPLES)
                samples.copyInto(padded)
                padded
            }
        }
    }

    /**
     * Convert 16-bit PCM to normalized float array [-1.0, 1.0].
     */
    private fun normalizeAudio(buffer: ShortArray): FloatArray {
        return FloatArray(buffer.size) { buffer[it].toFloat() / Short.MAX_VALUE }
    }

    /**
     * Simple linear resampling to target rate.
     */
    private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples
        val ratio = toRate.toDouble() / fromRate
        val newLength = (samples.size * ratio).toInt()
        return FloatArray(newLength) { i ->
            val srcIdx = i / ratio
            val srcIdxInt = srcIdx.toInt().coerceIn(0, samples.size - 2)
            val frac = (srcIdx - srcIdxInt).toFloat()
            samples[srcIdxInt] * (1f - frac) + samples[srcIdxInt + 1] * frac
        }
    }

    /**
     * Assess overall audio quality for screening reliability.
     */
    private fun assessAudioQuality(samples: FloatArray): String {
        val rms = calculateRMS(samples)
        val zcr = calculateZeroCrossingRate(samples)
        return when {
            rms < MIN_AUDIO_RMS -> "too_quiet"
            zcr > MAX_NOISE_FLOOR -> "noisy"
            else -> "good"
        }
    }

    /**
     * Classify risk level from score.
     */
    private fun classifyRisk(score: Float): RespiratoryRisk {
        return when {
            score >= HIGH_RISK_THRESHOLD -> RespiratoryRisk.HIGH_RISK
            score >= MODERATE_RISK_THRESHOLD -> RespiratoryRisk.MODERATE_RISK
            score >= LOW_RISK_THRESHOLD -> RespiratoryRisk.LOW_RISK
            else -> RespiratoryRisk.NORMAL
        }
    }

    /**
     * Calculate RMS energy of the full sample array.
     */
    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.sumOf { (it * it).toDouble() }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Calculate zero-crossing rate (fraction of adjacent samples with sign change).
     */
    private fun calculateZeroCrossingRate(samples: FloatArray): Float {
        if (samples.size < 2) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) ||
                (samples[i] < 0 && samples[i - 1] >= 0)
            ) {
                crossings++
            }
        }
        return crossings.toFloat() / (samples.size - 1)
    }

    /**
     * Calculate L2 norm of embedding (useful for logging and validation).
     */
    private fun calculateL2Norm(embedding: FloatArray): Float {
        return sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
    }

    /**
     * Release TFLite interpreter resources.
     * Note: ViT-L encoder is always closed immediately after use.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        usesTflite = false
    }

    /**
     * Reset detector state.
     */
    fun reset() {
        _result.value = RespiratoryResult()
    }
}
