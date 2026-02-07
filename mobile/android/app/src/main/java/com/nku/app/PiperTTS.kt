package com.nku.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * PiperTTS — Offline Voice Synthesis
 *
 * Wraps text-to-speech for spoken clinical results.
 * Primary: Android built-in TTS (available on all devices)
 * Future: Piper ONNX voices for higher-quality offline African language TTS
 *
 * For Community Health Workers supporting low-literacy patients,
 * spoken results are essential for accessibility.
 */

enum class TTSState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR
}

class PiperTTS(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "PiperTTS"

        // Piper ONNX voice models (for future integration)
        // These provide higher-quality offline voices for African languages
        private val PIPER_VOICES = mapOf(
            "sw" to "swahili-medium.onnx",
            "en" to "english-medium.onnx",
            "fr" to "french-medium.onnx",
            "pt" to "portuguese-medium.onnx"
        )
    }

    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow(TTSState.IDLE)
    val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Initialize TTS engine.
     */
    fun initialize() {
        _state.value = TTSState.INITIALIZING
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _state.value = TTSState.READY
            _isReady.value = true
            Log.i(TAG, "TTS initialized successfully")
        } else {
            _state.value = TTSState.ERROR
            _isReady.value = false
            Log.e(TAG, "TTS initialization failed: $status")
        }
    }

    /**
     * Speak text in the appropriate language.
     *
     * @param text The text to speak
     * @param languageCode ISO language code (e.g., "sw", "en", "fr")
     */
    fun speak(text: String, languageCode: String = "en") {
        val engine = tts ?: run {
            Log.w(TAG, "TTS not initialized")
            return
        }

        // Set language
        val locale = getLocaleForLanguage(languageCode)
        val result = engine.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if language not available
            Log.w(TAG, "Language $languageCode not available, falling back to English")
            engine.setLanguage(Locale.ENGLISH)
        }

        // Set speech rate slightly slower for clinical content
        engine.setSpeechRate(0.85f)
        engine.setPitch(1.0f)

        _state.value = TTSState.SPEAKING
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nku_tts_${System.currentTimeMillis()}")

        // Monitor completion
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TTSState.SPEAKING
            }
            override fun onDone(utteranceId: String?) {
                _state.value = TTSState.READY
            }
            override fun onError(utteranceId: String?) {
                _state.value = TTSState.ERROR
            }
        })
    }

    /**
     * Stop any ongoing speech.
     */
    fun stop() {
        tts?.stop()
        _state.value = TTSState.READY
    }

    /**
     * Check if a specific language is supported by the TTS engine.
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        val engine = tts ?: return false
        val locale = getLocaleForLanguage(languageCode)
        val result = engine.isLanguageAvailable(locale)
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    /**
     * Get the voice for a given language.
     * Selects the best available voice (prefers Piper ONNX when available).
     */
    fun getVoiceForLanguage(languageCode: String): String {
        // Check if Piper ONNX voice is available
        if (PIPER_VOICES.containsKey(languageCode)) {
            val piperPath = "${context.filesDir}/piper/${PIPER_VOICES[languageCode]}"
            if (java.io.File(piperPath).exists()) {
                return "piper:$languageCode"
            }
        }
        // Fallback to system TTS
        return "system:$languageCode"
    }

    /**
     * Map language code to Locale for Android TTS.
     */
    private fun getLocaleForLanguage(code: String): Locale = when (code) {
        "en" -> Locale.ENGLISH
        "fr" -> Locale.FRENCH
        "sw" -> Locale("sw")
        "ha" -> Locale("ha")
        "yo" -> Locale("yo")
        "ig" -> Locale("ig")
        "am" -> Locale("am")
        "ee" -> Locale("ee")
        "ak" -> Locale("ak")
        "wo" -> Locale("wo")
        "zu" -> Locale("zu")
        "xh" -> Locale("xh")
        "om" -> Locale("om")
        "ti" -> Locale("ti")
        "pt" -> Locale("pt")
        "ar" -> Locale("ar")
        else -> Locale.ENGLISH
    }

    /**
     * Release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _state.value = TTSState.IDLE
        _isReady.value = false
    }
}
