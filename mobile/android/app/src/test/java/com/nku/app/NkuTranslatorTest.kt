package com.nku.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NkuTranslator — On-Device Translation Support.
 *
 * Tests the static companion methods: isOnDeviceSupported(), requiresCloud(),
 * and the language map/set consistency. These are JVM-side tests (no Android
 * context needed) since they exercise pure Kotlin companion object logic.
 *
 * translate() itself requires ML Kit (Android context) and is covered
 * by instrumented tests.
 */
class NkuTranslatorTest {

    // ── isOnDeviceSupported() ──────────────────────────────

    @Test
    fun `isOnDeviceSupported returns true for English`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("en"))
    }

    @Test
    fun `isOnDeviceSupported returns true for French`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("fr"))
    }

    @Test
    fun `isOnDeviceSupported returns true for Portuguese`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("pt"))
    }

    @Test
    fun `isOnDeviceSupported returns true for Swahili`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("sw"))
    }

    @Test
    fun `isOnDeviceSupported returns true for Afrikaans`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("af"))
    }

    @Test
    fun `isOnDeviceSupported returns true for Arabic`() {
        assertTrue(NkuTranslator.isOnDeviceSupported("ar"))
    }

    @Test
    fun `isOnDeviceSupported returns false for Hausa`() {
        assertFalse(NkuTranslator.isOnDeviceSupported("ha"))
    }

    @Test
    fun `isOnDeviceSupported returns false for Yoruba`() {
        assertFalse(NkuTranslator.isOnDeviceSupported("yo"))
    }

    @Test
    fun `isOnDeviceSupported returns false for Twi`() {
        assertFalse(NkuTranslator.isOnDeviceSupported("ak"))
    }

    @Test
    fun `isOnDeviceSupported returns false for Igbo`() {
        assertFalse(NkuTranslator.isOnDeviceSupported("ig"))
    }

    @Test
    fun `isOnDeviceSupported returns false for unknown code`() {
        assertFalse(NkuTranslator.isOnDeviceSupported("zz"))
    }

    @Test
    fun `isOnDeviceSupported returns false for empty string`() {
        assertFalse(NkuTranslator.isOnDeviceSupported(""))
    }

    // ── requiresCloud() ────────────────────────────────────

    @Test
    fun `requiresCloud returns true for Hausa`() {
        assertTrue(NkuTranslator.requiresCloud("ha"))
    }

    @Test
    fun `requiresCloud returns true for Yoruba`() {
        assertTrue(NkuTranslator.requiresCloud("yo"))
    }

    @Test
    fun `requiresCloud returns true for Igbo`() {
        assertTrue(NkuTranslator.requiresCloud("ig"))
    }

    @Test
    fun `requiresCloud returns true for Amharic`() {
        assertTrue(NkuTranslator.requiresCloud("am"))
    }

    @Test
    fun `requiresCloud returns true for Zulu`() {
        assertTrue(NkuTranslator.requiresCloud("zu"))
    }

    @Test
    fun `requiresCloud returns true for Somali`() {
        assertTrue(NkuTranslator.requiresCloud("so"))
    }

    @Test
    fun `requiresCloud returns false for English`() {
        assertFalse(NkuTranslator.requiresCloud("en"))
    }

    @Test
    fun `requiresCloud returns false for French`() {
        assertFalse(NkuTranslator.requiresCloud("fr"))
    }

    @Test
    fun `requiresCloud returns false for Swahili`() {
        assertFalse(NkuTranslator.requiresCloud("sw"))
    }

    // ── CLOUD_ONLY_LANGUAGES Set ───────────────────────────

    @Test
    fun `CLOUD_ONLY_LANGUAGES is not empty`() {
        assertTrue(NkuTranslator.CLOUD_ONLY_LANGUAGES.isNotEmpty())
    }

    @Test
    fun `CLOUD_ONLY_LANGUAGES has at least 10 entries`() {
        assertTrue("Should have at least 10 cloud-only languages",
            NkuTranslator.CLOUD_ONLY_LANGUAGES.size >= 10)
    }

    @Test
    fun `CLOUD_ONLY_LANGUAGES does not overlap with on-device supported`() {
        // No language should be simultaneously on-device and cloud-only
        for (lang in NkuTranslator.CLOUD_ONLY_LANGUAGES) {
            assertFalse("$lang should not be on-device if cloud-only",
                NkuTranslator.isOnDeviceSupported(lang))
        }
    }

    // ── Coverage of Tier 1 clinically verified languages ───

    @Test
    fun `all Tier 1 languages are in CLOUD_ONLY set`() {
        val tier1 = listOf("ha", "yo", "ig", "am", "ee", "ak", "wo", "zu", "xh", "om", "ti")
        for (lang in tier1) {
            assertTrue("$lang should be in CLOUD_ONLY_LANGUAGES",
                NkuTranslator.CLOUD_ONLY_LANGUAGES.contains(lang))
        }
    }
}
