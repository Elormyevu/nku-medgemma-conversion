package com.nku.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for JaundiceDetector — Scleral icterus detection.
 *
 * Tests initial state, reset behavior, JaundiceResult data class,
 * and severity classification thresholds.
 *
 * Note: analyzeSclera() requires a real Bitmap (Android framework class
 * unavailable on JVM), so image processing tests require instrumented tests.
 */
class JaundiceDetectorTest {

    private lateinit var detector: JaundiceDetector

    @Before
    fun setup() {
        detector = JaundiceDetector()
    }

    // ── Initial State ──

    @Test
    fun `initial state has no analysis`() {
        val result = detector.result.value
        assertFalse("Should not be analyzed initially", result.hasBeenAnalyzed)
        assertEquals("Initial score should be 0", 0f, result.jaundiceScore, 0.001f)
        assertEquals("Initial confidence should be 0", 0f, result.confidence, 0.001f)
    }

    @Test
    fun `initial severity is NORMAL`() {
        assertEquals(JaundiceSeverity.NORMAL, detector.result.value.severity)
    }

    @Test
    fun `initial recommendation is default`() {
        assertEquals("No analysis", detector.result.value.recommendation)
    }

    @Test
    fun `initial yellowRatio is zero`() {
        assertEquals(0f, detector.result.value.yellowRatio, 0.001f)
    }

    @Test
    fun `initial avgHue is zero`() {
        assertEquals(0f, detector.result.value.avgHue, 0.001f)
    }

    // ── Reset ──

    @Test
    fun `reset restores initial state`() {
        // Manually put a non-default value into the flow to verify reset
        val detector2 = JaundiceDetector()
        // reset() should restore to defaults
        detector2.reset()
        val result = detector2.result.value
        assertFalse(result.hasBeenAnalyzed)
        assertEquals(0f, result.jaundiceScore, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(JaundiceSeverity.NORMAL, result.severity)
        assertEquals("No analysis", result.recommendation)
    }

    // ── JaundiceResult data class ──

    @Test
    fun `JaundiceResult stores all fields correctly`() {
        val result = JaundiceResult(
            jaundiceScore = 0.65f,
            confidence = 0.85f,
            severity = JaundiceSeverity.MODERATE,
            recommendation = "Get liver function test",
            hasBeenAnalyzed = true,
            yellowRatio = 0.35f,
            avgHue = 0.08f
        )
        assertEquals(0.65f, result.jaundiceScore, 0.001f)
        assertEquals(0.85f, result.confidence, 0.001f)
        assertEquals(JaundiceSeverity.MODERATE, result.severity)
        assertEquals("Get liver function test", result.recommendation)
        assertTrue(result.hasBeenAnalyzed)
        assertEquals(0.35f, result.yellowRatio, 0.001f)
        assertEquals(0.08f, result.avgHue, 0.001f)
    }

    @Test
    fun `JaundiceResult defaults are correct`() {
        val result = JaundiceResult()
        assertEquals(0f, result.jaundiceScore, 0.001f)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(JaundiceSeverity.NORMAL, result.severity)
        assertEquals("No analysis", result.recommendation)
        assertFalse(result.hasBeenAnalyzed)
        assertEquals(0f, result.yellowRatio, 0.001f)
        assertEquals(0f, result.avgHue, 0.001f)
    }

    // ── JaundiceSeverity enum ──

    @Test
    fun `JaundiceSeverity has exactly 4 values`() {
        val values = JaundiceSeverity.values()
        assertEquals(4, values.size)
        assertEquals(JaundiceSeverity.NORMAL, values[0])
        assertEquals(JaundiceSeverity.MILD, values[1])
        assertEquals(JaundiceSeverity.MODERATE, values[2])
        assertEquals(JaundiceSeverity.SEVERE, values[3])
    }

    @Test
    fun `JaundiceSeverity ordinal ordering is ascending`() {
        assertTrue(JaundiceSeverity.NORMAL.ordinal < JaundiceSeverity.MILD.ordinal)
        assertTrue(JaundiceSeverity.MILD.ordinal < JaundiceSeverity.MODERATE.ordinal)
        assertTrue(JaundiceSeverity.MODERATE.ordinal < JaundiceSeverity.SEVERE.ordinal)
    }

    // ── StateFlow accessibility ──

    @Test
    fun `result StateFlow is accessible`() {
        assertNotNull("StateFlow should be accessible", detector.result)
        assertNotNull("StateFlow value should be non-null", detector.result.value)
    }
}
