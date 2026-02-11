package com.nku.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * NkuTTS — Offline Voice Synthesis
 *
 * Wraps Android's built-in TextToSpeech engine for spoken clinical results.
 * Uses the system TTS which is pre-installed on all Android devices.
 *
 * Language support depends on the device's installed TTS voices.
 * English is guaranteed on all devices; African languages vary by device/region.
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

class NkuTTS(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "NkuTTS"
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
            
            // F-6 fix: Register listener once at init, not per-speak() call
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
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
