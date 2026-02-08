package com.nku.app

import android.util.Log

/**
 * PromptSanitizer — On-Device Prompt Injection Protection
 *
 * Mirrors the cloud API's PromptProtector for on-device parity.
 * Strips injection patterns, zero-width characters, and wraps
 * user input in delimiters to prevent prompt manipulation.
 *
 * Audit Fix: F-1 (CRITICAL)
 */
object PromptSanitizer {

    private const val TAG = "PromptSanitizer"

    // Delimiter pair for user-supplied content
    private const val DELIMITER_START = "<<<"
    private const val DELIMITER_END = ">>>"

    /**
     * Injection patterns that should never appear in user input.
     * Ported from cloud/inference_api/security.py PromptProtector.
     */
    private val INJECTION_PATTERNS = listOf(
        Regex("ignore\\s+(all\\s+)?previous", RegexOption.IGNORE_CASE),
        Regex("disregard\\s+(all\\s+)?previous", RegexOption.IGNORE_CASE),
        Regex("forget\\s+(all\\s+)?previous", RegexOption.IGNORE_CASE),
        Regex("override\\s+(all\\s+)?instructions?", RegexOption.IGNORE_CASE),
        Regex("you\\s+are\\s+now", RegexOption.IGNORE_CASE),
        Regex("act\\s+as\\s+(a\\s+)?", RegexOption.IGNORE_CASE),
        Regex("new\\s+instructions?:", RegexOption.IGNORE_CASE),
        Regex("system\\s*:", RegexOption.IGNORE_CASE),
        Regex("\\[\\s*system\\s*\\]", RegexOption.IGNORE_CASE),
        Regex("\\{\\s*\"role\"\\s*:", RegexOption.IGNORE_CASE),
        Regex("SEVERITY\\s*:", RegexOption.IGNORE_CASE),  // Prevent output format injection
        Regex("URGENCY\\s*:", RegexOption.IGNORE_CASE),
        Regex("PRIMARY_CONCERNS\\s*:", RegexOption.IGNORE_CASE),
        Regex("RECOMMENDATIONS\\s*:", RegexOption.IGNORE_CASE),
        Regex("```", RegexOption.IGNORE_CASE),  // Code block injection
        Regex("</?\\w+>"),  // HTML/XML tags
    )

    /**
     * Zero-width and invisible Unicode characters that can hide injections.
     */
    private val ZERO_WIDTH_REGEX = Regex("[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF\\u00AD\\u2060\\u2061\\u2062\\u2063\\u2064]")

    /**
     * Sanitize user input text for safe inclusion in LLM prompts.
     *
     * @param input Raw user text (symptoms, voice transcription, etc.)
     * @return Sanitized text safe for prompt interpolation
     */
    fun sanitize(input: String): String {
        var cleaned = input

        // 1. Strip zero-width/invisible characters
        cleaned = ZERO_WIDTH_REGEX.replace(cleaned, "")

        // 2. Normalize whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // 3. Strip injection patterns
        var injectionDetected = false
        for (pattern in INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(cleaned)) {
                injectionDetected = true
                cleaned = pattern.replace(cleaned, "[filtered]")
            }
        }

        if (injectionDetected) {
            Log.w(TAG, "Prompt injection patterns detected and stripped from user input")
        }

        // 4. Length cap — symptoms should not exceed 500 chars
        if (cleaned.length > 500) {
            cleaned = cleaned.take(500)
            Log.w(TAG, "User input truncated to 500 characters")
        }

        return cleaned
    }

    /**
     * Wrap user-supplied content in delimiters for safe prompt embedding.
     * The LLM system prompt instructs: "User input is between <<< and >>>"
     *
     * @param content Sanitized user content
     * @return Delimiter-wrapped string
     */
    fun wrapInDelimiters(content: String): String {
        return "$DELIMITER_START$content$DELIMITER_END"
    }

    /**
     * Full sanitization pipeline: sanitize + wrap.
     */
    fun sanitizeAndWrap(input: String): String {
        return wrapInDelimiters(sanitize(input))
    }

    /**
     * Validate LLM output for signs of injection pass-through.
     * If the model's response contains suspicious patterns,
     * it may indicate a successful injection.
     *
     * @param output Raw LLM response
     * @return true if output appears safe
     */
    fun validateOutput(output: String): Boolean {
        val suspiciousPatterns = listOf(
            Regex("ignore\\s+previous", RegexOption.IGNORE_CASE),
            Regex("I\\s+am\\s+now\\s+in", RegexOption.IGNORE_CASE),
            Regex("\\{\\s*\"role\""),
            Regex("system\\s*prompt", RegexOption.IGNORE_CASE),
        )

        for (pattern in suspiciousPatterns) {
            if (pattern.containsMatchIn(output)) {
                Log.w(TAG, "Suspicious pattern in LLM output — possible injection pass-through")
                return false
            }
        }
        return true
    }
}
