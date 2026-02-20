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
 * Yoruba), the translator returns null and the inference engine degrades
 * gracefully — passing raw input directly to MedGemma, which may still
 * partially understand common medical terms in major African languages.
 *
 * Key advantages: ~30MB per language pack (lightweight), ML Kit runs in a
 * separate process — zero RAM contention with MedGemma during the Nku Cycle.
 */
class NkuTranslator(private val context: Context) {

    companion object {
        private const val TAG = "NkuTranslator"

        /**
         * Maps Nku language codes (ISO 639-1) to ML Kit TranslateLanguage constants.
         * Languages NOT in this map are unsupported for on-device translation
         * in the current mobile build.
         *
         * ML Kit translate:17.0.3 supports 59 languages, but most are
         * European/Asian. Only 3 African languages are available on-device:
         * Afrikaans, Swahili, and Arabic. All indigenous African languages
         * (Hausa, Yoruba, Igbo, Amharic, Zulu, Xhosa, etc.) are currently
         * passed through untranslated to preserve full offline operation.
         */
        private val ML_KIT_LANGUAGE_MAP: Map<String, String> = mapOf(
            // ── Official/colonial languages (on-device) ──
            "en" to TranslateLanguage.ENGLISH,
            "fr" to TranslateLanguage.FRENCH,
            "pt" to TranslateLanguage.PORTUGUESE,

            // ── ML Kit-supported languages used in Africa ──
            "af" to TranslateLanguage.AFRIKAANS,
            "sw" to TranslateLanguage.SWAHILI,
            "ar" to TranslateLanguage.ARABIC
        )

        /**
         * Languages NOT supported by ML Kit on-device translation.
         * These are African indigenous languages beyond Afrikaans, Swahili, and Arabic.
         *
         * On-device behavior: NkuTranslator returns null → NkuInferenceEngine
         * gracefully degrades by passing raw input directly to MedGemma.
         *
         * Note: A cloud translation client can be layered on top of this list
         * in future releases, but is not wired into the shipped mobile app.
         */
        val CLOUD_ONLY_LANGUAGES = setOf(
            // Tier 1 — Nku clinically verified languages (unsupported on-device)
            "ha",  // Hausa
            "yo",  // Yoruba
            "ig",  // Igbo
            "am",  // Amharic
            "ee",  // Ewe
            "ak",  // Twi (Akan)
            "wo",  // Wolof
            "zu",  // Zulu
            "xh",  // Xhosa
            "om",  // Oromo
            "ti",  // Tigrinya
            // Tier 2 — additional languages (unsupported on-device)
            "bm",  // Bambara
            "ny",  // Chichewa
            "din", // Dinka
            "ff",  // Fula
            "gaa", // Ga
            "ki",  // Kikuyu
            "rw",  // Kinyarwanda
            "kg",  // Kongo
            "ln",  // Lingala
            "luo", // Luo
            "lg",  // Luganda
            "mg",  // Malagasy
            "nd",  // Ndebele
            "nus", // Nuer
            "pcm", // Pidgin (Nigerian)
            "wes", // Pidgin (Cameroonian)
            "rn",  // Rundi
            "st",  // Sesotho
            "sn",  // Shona
            "so",  // Somali
            "tn",  // Tswana
            "ts",  // Tsonga
            "ve",  // Venda
            "ss",  // Swati
            "nso", // Northern Sotho
            "bem", // Bemba
            "tum", // Tumbuka
            "lua", // Luba-Kasai
            "kj"   // Kuanyama
        )

        /**
         * Check if a language can be translated on-device via ML Kit.
         */
        fun isOnDeviceSupported(languageCode: String): Boolean {
            return ML_KIT_LANGUAGE_MAP.containsKey(languageCode)
        }

        /**
         * Check if a language is unsupported by ML Kit on-device.
         * (Helper retained for optional future cloud-client integration.)
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
            return null
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
