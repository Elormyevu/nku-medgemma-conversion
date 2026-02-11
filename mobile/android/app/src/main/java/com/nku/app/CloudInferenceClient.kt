package com.nku.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * CloudInferenceClient — Cloud Fallback for Emulators & Development ONLY
 *
 * ⚠️ GATED BEHIND BuildConfig.DEBUG (F-2)
 * All public methods return null/false in release builds.
 * Nku is 100% offline in production — this class exists solely for
 * development and emulator testing.
 *
 * L-02: Marked as deprecated. Consider removing in a future cleanup
 * pass once emulator testing workflows are fully validated with
 * on-device model sideloading.
 *
 * Endpoint: Nku Cloud Run API (cloud/inference_api/)
 */
@Deprecated("Dev/emulator only — all methods return null in release builds. See L-02.")
class CloudInferenceClient(
    private val baseUrl: String = BuildConfig.NKU_CLOUD_URL,
    private val apiKey: String? = null,
    private val timeoutMs: Int = 60_000
) {
    companion object {
        private const val TAG = "NkuCloud"
    }

    /** Returns true only in debug builds — blocks all cloud access in release. */
    private fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    /**
     * Check if the cloud API is reachable.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!isDebugBuild()) return@withContext false  // F-2: offline-only in release
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
        if (!isDebugBuild()) return@withContext null  // F-2: offline-only in release
        try {
            val url = URL("$baseUrl/triage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true

            // Build request body
            // C-03 fix: field name matches backend /triage endpoint contract
            val body = JSONObject().apply {
                put("symptoms", prompt)
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
            if (!isDebugBuild()) return@withContext null  // F-2: offline-only in release
            try {
                val url = URL("$baseUrl/translate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.doOutput = true

                // C-03 fix: field names match backend /translate endpoint contract
                val body = JSONObject().apply {
                    put("text", text)
                    put("source", sourceLang)
                    put("target", targetLang)
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
