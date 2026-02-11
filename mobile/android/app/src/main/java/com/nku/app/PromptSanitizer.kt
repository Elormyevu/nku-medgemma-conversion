package com.nku.app

import java.util.Base64 as JBase64
import android.util.Log

/**
 * PromptSanitizer — On-Device Prompt Injection Protection
 *
 * Mirrors the cloud API's PromptProtector for on-device parity.
 * Strips injection patterns, zero-width characters, normalizes
 * Unicode homoglyphs, detects base64-encoded payloads, and wraps
 * user input in delimiters to prevent prompt manipulation.
 *
 * Audit Fix: F-1 (CRITICAL), F-SEC-1 (homoglyph + base64 parity)
 */
object PromptSanitizer {

    private const val TAG = "PromptSanitizer"

    // Delimiter pair for user-supplied content
    private const val DELIMITER_START = "<<<"
    private const val DELIMITER_END = ">>>"

    /** Maximum safe output length from LLM responses */
    private const val MAX_OUTPUT_LENGTH = 5000

    /**
     * Cyrillic/Greek → Latin homoglyph mapping.
     * Ported from cloud/inference_api/security.py PromptProtector._normalize_homoglyphs()
     * Attackers use these lookalike chars to bypass regex-based injection filters.
     */
    private val HOMOGLYPH_MAP = mapOf(
        'А' to 'A', 'В' to 'B', 'С' to 'C', 'Е' to 'E', 'Н' to 'H',
        'К' to 'K', 'М' to 'M', 'О' to 'O', 'Р' to 'P', 'Т' to 'T',
        'Х' to 'X', 'а' to 'a', 'с' to 'c', 'е' to 'e', 'о' to 'o',
        'р' to 'p', 'х' to 'x', 'у' to 'y', 'і' to 'i', 'ј' to 'j',
        // Greek
        'Α' to 'A', 'Β' to 'B', 'Ε' to 'E', 'Ζ' to 'Z', 'Η' to 'H',
        'Ι' to 'I', 'Κ' to 'K', 'Μ' to 'M', 'Ν' to 'N', 'Ο' to 'O',
        'Ρ' to 'P', 'Τ' to 'T', 'Υ' to 'Y', 'Χ' to 'X', 'ο' to 'o',
    )

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
        Regex("do\\s+not\\s+translate", RegexOption.IGNORE_CASE),
        Regex("reveal\\s+(your|the)\\s+prompt", RegexOption.IGNORE_CASE),
    )

    /**
     * Zero-width and invisible Unicode characters that can hide injections.
     */
    private val ZERO_WIDTH_REGEX = Regex("[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF\\u00AD\\u2060\\u2061\\u2062\\u2063\\u2064]")

    /**
     * Regex to detect potential base64-encoded payloads (≥20 chars of base64 alphabet).
     * Ported from cloud/inference_api/security.py _check_base64_injection()
     */
    private val BASE64_CANDIDATE_REGEX = Regex("[A-Za-z0-9+/=]{20,}")

    /**
     * Normalize Unicode homoglyphs to their Latin equivalents.
     * Prevents attackers from using Cyrillic/Greek lookalike characters
     * to bypass regex-based injection pattern matching.
     *
     * Example: "іgnоrе рrеvіоus" (mixed Cyrillic) → "ignore previous" (Latin)
     */
    private fun normalizeHomoglyphs(input: String): String {
        val sb = StringBuilder(input.length)
        for (char in input) {
            sb.append(HOMOGLYPH_MAP[char] ?: char)
        }
        return sb.toString()
    }

    /**
     * Check for base64-encoded injection payloads.
     * Decodes candidate base64 strings and checks decoded content for injection patterns.
     *
     * @return The cleaned string with any base64-encoded injections replaced with [filtered]
     */
    private fun checkBase64Injection(input: String): Pair<String, Boolean> {
        var result = input
        var detected = false

        BASE64_CANDIDATE_REGEX.findAll(input).forEach { match ->
            try {
                val decoded = String(JBase64.getDecoder().decode(match.value), Charsets.UTF_8)
                // Check if the decoded content contains injection patterns
                for (pattern in INJECTION_PATTERNS) {
                    if (pattern.containsMatchIn(decoded)) {
                        result = result.replace(match.value, "[filtered]")
                        detected = true
                        Log.w(TAG, "Base64-encoded injection payload detected and stripped")
                        break
                    }
                }
            } catch (_: Exception) {
                // Not valid base64 — ignore
            }
        }

        return Pair(result, detected)
    }

    /**
     * Sanitize user input text for safe inclusion in LLM prompts.
     *
     * Pipeline: zero-width strip → homoglyph normalize → whitespace normalize →
     *           base64 check → injection pattern strip → length cap
     *
     * @param input Raw user text (symptoms, voice transcription, etc.)
     * @return Sanitized text safe for prompt interpolation
     */
    fun sanitize(input: String): String {
        var cleaned = input

        // 1. Strip zero-width/invisible characters
        cleaned = ZERO_WIDTH_REGEX.replace(cleaned, "")

        // 2. Normalize Unicode homoglyphs (Cyrillic/Greek → Latin)
        cleaned = normalizeHomoglyphs(cleaned)

        // 3. Normalize whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // 4. Check for base64-encoded injection payloads
        val (afterBase64, base64Detected) = checkBase64Injection(cleaned)
        cleaned = afterBase64
        if (base64Detected) {
            Log.w(TAG, "Base64 injection patterns detected and stripped from user input")
        }

        // 5. Strip injection patterns
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

        // S-1 fix: Medical character allowlist — warn on unexpected Unicode codepoints
        // Allows: Latin, Latin Extended, African scripts (Ethiopic, Tifinagh, Vai, etc.),
        // Arabic, Devanagari, Bengali, CJK, punctuation, digits, whitespace
        val unexpectedChars = cleaned.filter { ch ->
            !ch.isLetterOrDigit() &&
            !ch.isWhitespace() &&
            ch !in ".,;:!?'-/()[]{}@#&*+=\"" &&
            ch != '[' && ch != ']'  // Allow [filtered] markers
        }
        if (unexpectedChars.isNotEmpty()) {
            Log.w(TAG, "S-1: Unexpected characters detected after sanitization: " +
                "count=${unexpectedChars.length}, " +
                "codepoints=${unexpectedChars.take(10).map { "U+%04X".format(it.code) }}")
        }

        // 6. C-04 fix: Escape delimiter sequences to prevent delimiter spoofing
        if (cleaned.contains("<<<") || cleaned.contains(">>>")) {
            Log.w(TAG, "Delimiter sequences detected in user input — escaping")
            cleaned = cleaned.replace("<<<", "‹‹‹").replace(">>>", "›››")
        }

        // 7. Length cap — symptoms should not exceed 500 chars
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
     * Also enforces output length truncation for safety. (F-SEC-2)
     *
     * @param output Raw LLM response
     * @return Pair of (isSafe, sanitizedOutput) — sanitized output is truncated if needed
     */
    fun validateOutput(output: String): Boolean {
        // Check for delimiter leakage
        if (output.contains(DELIMITER_START) || output.contains(DELIMITER_END)) {
            Log.w(TAG, "Delimiter leakage detected in LLM output — possible injection pass-through")
            return false
        }

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

    /**
     * Sanitize and truncate LLM output. Called after validateOutput() passes.
     *
     * @param output Raw LLM response
     * @return Truncated output capped at MAX_OUTPUT_LENGTH characters
     */
    fun sanitizeOutput(output: String): String {
        return if (output.length > MAX_OUTPUT_LENGTH) {
            Log.w(TAG, "LLM output truncated from ${output.length} to $MAX_OUTPUT_LENGTH chars")
            output.take(MAX_OUTPUT_LENGTH)
        } else {
            output
        }
    }
}
