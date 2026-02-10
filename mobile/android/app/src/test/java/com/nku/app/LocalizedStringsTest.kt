package com.nku.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for LocalizedStrings — verifies language lookup,
 * string completeness, and fallback behavior.
 * (Audit Fix F-TC-1)
 */
class LocalizedStringsTest {

    @Test
    fun `english is default language`() {
        val strings = LocalizedStrings.forLanguage("en")
        assertEquals("Nku Sentinel", strings.appTitle)
        assertEquals("Home", strings.tabHome)
    }

    @Test
    fun `unknown language falls back to english`() {
        val strings = LocalizedStrings.forLanguage("zz_unknown")
        assertEquals("Nku Sentinel", strings.appTitle)
    }

    @Test
    fun `ewe strings have localized content`() {
        val strings = LocalizedStrings.forLanguage("ee")
        assertEquals("Aƒeme", strings.tabHome)
        assertNotEquals("Home", strings.tabHome) // Confirm it's actually translated
    }

    @Test
    fun `french strings have localized content`() {
        val strings = LocalizedStrings.forLanguage("fr")
        assertEquals("Accueil", strings.tabHome)
    }

    @Test
    fun `all tier 1 languages have non-null strings`() {
        val tier1 = listOf("en", "fr", "sw", "ha", "yo", "ig", "am", "ee", "ak", "wo", "zu", "xh", "om", "ti")
        tier1.forEach { code ->
            val strings = LocalizedStrings.forLanguage(code)
            assertTrue("$code: appTitle blank", strings.appTitle.isNotBlank())
            assertTrue("$code: disclaimer blank", strings.disclaimer.isNotBlank())
        }
    }

    @Test
    fun `getLanguageName returns correct name`() {
        assertEquals("English", LocalizedStrings.getLanguageName("en"))
        assertEquals("Ewe", LocalizedStrings.getLanguageName("ee"))
        assertEquals("French", LocalizedStrings.getLanguageName("fr"))
    }

    @Test
    fun `getLanguageName returns Unknown for bad code`() {
        assertEquals("Unknown", LocalizedStrings.getLanguageName("zz_bad"))
    }

    @Test
    fun `supportedLanguages contains expected languages`() {
        val count = LocalizedStrings.supportedLanguages.size
        assertTrue("Expected at least 40 supported languages, got $count", count >= 40)
        assertTrue("Tier 1 languages present", LocalizedStrings.supportedLanguages.containsKey("en"))
        assertTrue("Tier 1 languages present", LocalizedStrings.supportedLanguages.containsKey("ha"))
        assertTrue("Tier 1 languages present", LocalizedStrings.supportedLanguages.containsKey("yo"))
    }
}
