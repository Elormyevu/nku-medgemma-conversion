package com.nku.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * CloudInferenceClient — Cloud Fallback for Emulators & Development
 *
 * When GGUF models cannot be loaded (e.g., on emulators without ARM64 native
 * libraries, or during development), this client sends inference requests to
 * the Nku Cloud Inference API instead.
 *
 * In production, this is NEVER used — 100% offline via NkuInferenceEngine.
 * This exists solely for development/demo purposes.
 *
 * Endpoint: Nku Cloud Run API (cloud/inference_api/)
 */
class CloudInferenceClient(
    private val baseUrl: String = "https://nku-inference-api-run.app",
    private val apiKey: String? = null,
    private val timeoutMs: Int = 60_000
) {
    companion object {
        private const val TAG = "NkuCloud"
    }

    /**
     * Check if the cloud API is reachable.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            Log.w(TAG, "Cloud API not available: ${e.message}")
            false
        }
    }

    /**
     * Send a triage request to the cloud MedGemma API.
     *
     * @param prompt The clinical prompt (from ClinicalReasoner.generatePrompt)
     * @return The clinical assessment text, or null if the request failed
     */
    suspend fun runInference(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/v1/triage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true

            // Build request body
            val body = JSONObject().apply {
                put("prompt", prompt)
                put("max_tokens", 512)
                put("temperature", 0.3)
            }

            // Send request
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            // Read response
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseBody)
                val response = json.optString("response", json.optString("text", ""))
                conn.disconnect()
                Log.i(TAG, "Cloud inference complete (${response.length} chars)")
                response
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                conn.disconnect()
                Log.e(TAG, "Cloud API error ($responseCode): $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud inference failed: ${e.message}", e)
            null
        }
    }

    /**
     * Send a translation request to the cloud API.
     *
     * @param text Text to translate
     * @param sourceLang Source language code
     * @param targetLang Target language code
     * @return Translated text, or null if failed
     */
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/v1/translate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("text", text)
                    put("source_language", sourceLang)
                    put("target_language", targetLang)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseBody = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseBody)
                    conn.disconnect()
                    json.optString("translation", null)
                } else {
                    conn.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloud translate failed: ${e.message}")
                null
            }
        }
}
