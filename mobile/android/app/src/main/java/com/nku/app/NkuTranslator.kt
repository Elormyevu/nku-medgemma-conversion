package com.nku.app

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * NkuTranslator — ML Kit On-Device Translation Wrapper
 *
 * Handles bi-directional translation using Google ML Kit's on-device
 * translation API. Supports 59 languages including all African official
 * languages (English, French, Portuguese) for fully offline operation.
 *
 * For indigenous languages not supported by ML Kit (e.g., Twi, Hausa,
 * Yoruba), translations fall back to the cloud inference API.
 *
 * Key advantage over TranslateGemma: ~30MB per language pack vs ~2.3GB
 * GGUF model, and ML Kit runs in a separate process — no RAM contention
 * with MedGemma during the Nku Cycle.
 */
class NkuTranslator(private val context: Context) {

    companion object {
        private const val TAG = "NkuTranslator"

        /**
         * Maps Nku language codes (ISO 639-1) to ML Kit TranslateLanguage constants.
         * Languages NOT in this map require cloud fallback (CloudInferenceClient).
         *
         * ML Kit on-device coverage for Nku's target regions:
         * - Official languages: English, French, Portuguese → always on-device
         * - Regional: Afrikaans, Swahili → on-device
         * - Not supported by ML Kit: Twi, Hausa, Yoruba, Igbo, Wolof, Amharic, etc.
         */
        private val ML_KIT_LANGUAGE_MAP: Map<String, String> = mapOf(
            // ── Official languages (always on-device) ──
            "en" to TranslateLanguage.ENGLISH,
            "fr" to TranslateLanguage.FRENCH,
            "pt" to TranslateLanguage.PORTUGUESE,

            // ── ML Kit-supported African languages ──
            "af" to TranslateLanguage.AFRIKAANS,
            "sw" to TranslateLanguage.SWAHILI,
            "zu" to TranslateLanguage.ZULU,

            // ── Other ML Kit-supported languages used in Africa ──
            "ar" to TranslateLanguage.ARABIC,
            "es" to TranslateLanguage.SPANISH,

            // ── Widely used international languages ──
            "de" to TranslateLanguage.GERMAN,
            "hi" to TranslateLanguage.HINDI,
            "zh" to TranslateLanguage.CHINESE,
            "ja" to TranslateLanguage.JAPANESE,
            "ko" to TranslateLanguage.KOREAN,
            "ru" to TranslateLanguage.RUSSIAN
        )

        /**
         * Languages that require Google Cloud Translate API (not in ML Kit).
         * These are primarily indigenous African languages.
         */
        val CLOUD_ONLY_LANGUAGES = setOf(
            "tw",  // Twi (Akan)
            "ha",  // Hausa
            "yo",  // Yoruba
            "ig",  // Igbo
            "wo",  // Wolof
            "am",  // Amharic
            "om",  // Oromo
            "rw",  // Kinyarwanda
            "sn",  // Shona
            "so",  // Somali
            "ti",  // Tigrinya
            "ee",  // Ewe
            "ak",  // Akan
            "ln",  // Lingala
            "kg",  // Kongo
            "ff",  // Fula
            "bm",  // Bambara
            "tn",  // Tswana
            "ts",  // Tsonga
            "ve",  // Venda
            "xh",  // Xhosa
            "st",  // Sesotho
            "ss",  // Swazi
            "nr",  // Ndebele
            "ny",  // Chichewa
            "mg",  // Malagasy
            "rn",  // Kirundi
            "lu",  // Luba-Katanga
            "sg",  // Sango
            "lg",  // Ganda
            "nso", // Northern Sotho
        )

        /**
         * Check if a language can be translated on-device via ML Kit.
         */
        fun isOnDeviceSupported(languageCode: String): Boolean {
            return ML_KIT_LANGUAGE_MAP.containsKey(languageCode)
        }

        /**
         * Check if a language requires cloud-only translation.
         */
        fun requiresCloud(languageCode: String): Boolean {
            return CLOUD_ONLY_LANGUAGES.contains(languageCode)
        }
    }

    /**
     * Translate text from source language to target language using ML Kit.
     *
     * @param text The text to translate
     * @param sourceLanguage ISO 639-1 code of source language
     * @param targetLanguage ISO 639-1 code of target language
     * @return Translated text, or null if translation fails
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? {
        // Both languages must be supported by ML Kit
        val sourceLang = ML_KIT_LANGUAGE_MAP[sourceLanguage]
        val targetLang = ML_KIT_LANGUAGE_MAP[targetLanguage]

        if (sourceLang == null || targetLang == null) {
            Log.w(TAG, "Language not supported by ML Kit on-device: " +
                    "source=$sourceLanguage ($sourceLang), target=$targetLanguage ($targetLang)")
            return null  // Caller should use CloudInferenceClient
        }

        // Same language — no translation needed
        if (sourceLang == targetLang) return text

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)

        try {
            // Ensure language model is downloaded (Wi-Fi not required — small ~30MB packs)
            val conditions = DownloadConditions.Builder()
                .build()  // No Wi-Fi requirement for small language packs

            // Download model if needed
            val downloadSuccess = suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to download language model: ${e.message}", e)
                        cont.resume(false)
                    }
            }

            if (!downloadSuccess) {
                Log.e(TAG, "Language model download failed for $sourceLanguage → $targetLanguage")
                translator.close()
                return null
            }

            // Perform translation
            val result = suspendCancellableCoroutine<String?> { cont ->
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        Log.i(TAG, "ML Kit translated: $sourceLanguage → $targetLanguage " +
                                "(${text.length} → ${translatedText.length} chars)")
                        cont.resume(translatedText)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Translation failed: ${e.message}", e)
                        cont.resume(null)
                    }
            }

            translator.close()
            return result

        } catch (e: Exception) {
            Log.e(TAG, "NkuTranslator error: ${e.message}", e)
            translator.close()
            return null
        }
    }

    /**
     * Translate to English — convenience method for the Nku Cycle Stage 1.
     */
    suspend fun translateToEnglish(text: String, sourceLanguage: String): String? {
        return translate(text, sourceLanguage, "en")
    }

    /**
     * Translate from English — convenience method for the Nku Cycle Stage 3.
     */
    suspend fun translateFromEnglish(text: String, targetLanguage: String): String? {
        return translate(text, "en", targetLanguage)
    }
}
