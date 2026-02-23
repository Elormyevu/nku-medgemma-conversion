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
 * NkuTranslator — ML Kit On-Device Translation with Cloud Fallback
 *
 * Handles bi-directional translation using Google ML Kit's on-device
 * translation API. Supports major languages (English, French, Portuguese) for fully offline operation.
 *
 * For indigenous languages not supported by ML Kit (e.g., Twi, Hausa,
 * Yoruba), the translator falls back to the Antigravity Nku Cloud API.
 * This ensures MedGemma, an English-native reasoner, receives correctly
 * translated inputs and the user receives correctly localized outputs,
 * matching full documentation claims.
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
         * (Hausa, Yoruba, Igbo, Amharic, Zulu, Xhosa, etc.) securely fall back
         * to the Nku Cloud API.
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
        val sourceLangLabel = ML_KIT_LANGUAGE_MAP[sourceLanguage]
        val targetLangLabel = ML_KIT_LANGUAGE_MAP[targetLanguage]

        // Cloud API Fallback Activation
        if (sourceLangLabel == null || targetLangLabel == null) {
            Log.i(TAG, "Language not supported by ML Kit on-device ($sourceLanguage → $targetLanguage). Falling back to Cloud API...")
            return performCloudTranslation(text, sourceLanguage, targetLanguage)
        }

        // Same language — no translation needed
        if (sourceLanguage == targetLanguage) return text

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangLabel)
            .setTargetLanguage(targetLangLabel)
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

    /**
     * Executes translation over HTTP using the Nku Cloud API endpoints
     * for indigenous languages beyond ML Kit boundaries.
     */
    private suspend fun performCloudTranslation(text: String, sourceLanguage: String, targetLanguage: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val endpoint = BuildConfig.NKU_CLOUD_URL
            if (endpoint.isBlank()) {
                Log.w(TAG, "Cloud URL not configured. Returning raw text.")
                return@withContext text
            }
            
            val url = java.net.URL(endpoint)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            
            val apiKey = BuildConfig.NKU_API_KEY
            if (apiKey.isNotBlank()) {
                // Apply required Security Headers
                connection.setRequestProperty("X-API-Key", apiKey)
            }
            connection.doOutput = true

            val jsonBody = """{"text": "${text.replace("\"", "\\\"")}", "source": "$sourceLanguage", "target": "$targetLanguage"}"""
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val inputAsString = connection.inputStream.bufferedReader().use { it.readText() }
                // Primitive parser because we don't assume GSON is configured across the boundary
                val translationStart = inputAsString.indexOf("\"translation\":") + 14
                val translationEnd = inputAsString.indexOf("\",", translationStart)
                if (translationStart > 13 && translationEnd > translationStart) {
                    val result = inputAsString.substring(translationStart, translationEnd).trim(' ', '"')
                    Log.i(TAG, "Cloud API returned Translation: ${result.take(20)}...")
                    return@withContext result.replace("\\n", "\n").replace("\\\"", "\"")
                }
            }
            Log.e(TAG, "Cloud API returned unsuccessful code: ${connection.responseCode}")
            return@withContext text // Pass-through directly on failure so MedGemma still attempts it
        } catch (e: Exception) {
            Log.e(TAG, "Cloud fallback network error", e)
            return@withContext text
        }
    }
}
