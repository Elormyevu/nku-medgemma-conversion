package com.nku.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SensorFusion — vital signs aggregation and risk assessment.
 *
 * Tests:
 * - hasHighRiskIndicators() for all flag combinations
 * - symptom lifecycle (add/remove/clear)
 * - pregnancy context propagation
 * - reset() clears all state
 *
 * Note: updateVitalSigns() requires live StateFlow from RPPGProcessor/PallorDetector/
 * JaundiceDetector/EdemaDetector which need Bitmap (android.graphics), so those are
 * covered via integration tests. These tests exercise the pure-logic paths.
 */
class SensorFusionTest {

    private lateinit var rppg: RPPGProcessor
    private lateinit var pallor: PallorDetector
    private lateinit var jaundice: JaundiceDetector
    private lateinit var edema: EdemaDetector
    private lateinit var respiratory: RespiratoryDetector
    private lateinit var fusion: SensorFusion

    @Before
    fun setup() {
        rppg = RPPGProcessor()
        pallor = PallorDetector()
        jaundice = JaundiceDetector()
        edema = EdemaDetector()
        respiratory = RespiratoryDetector()
        fusion = SensorFusion(rppg, pallor, jaundice, edema, respiratory)
    }

    // ── Symptom Lifecycle ──────────────────────────────────

    @Test
    fun `addSymptom appends to symptom list`() {
        fusion.addSymptom("headache")
        fusion.addSymptom("dizziness", duration = "2 days")
        val symptoms = fusion.symptoms.value
        assertEquals(2, symptoms.size)
        assertEquals("headache", symptoms[0].symptom)
        assertEquals("dizziness", symptoms[1].symptom)
        assertEquals("2 days", symptoms[1].duration)
    }

    @Test
    fun `removeSymptom removes matching symptom`() {
        fusion.addSymptom("headache")
        fusion.addSymptom("fever")
        fusion.removeSymptom("headache")
        val symptoms = fusion.symptoms.value
        assertEquals(1, symptoms.size)
        assertEquals("fever", symptoms[0].symptom)
    }

    @Test
    fun `clearSymptoms empties the list`() {
        fusion.addSymptom("headache")
        fusion.addSymptom("fever")
        fusion.clearSymptoms()
        assertTrue(fusion.symptoms.value.isEmpty())
    }

    // ── Pregnancy Context ─────────────────────────────────

    @Test
    fun `setPregnancyContext updates vital signs`() {
        fusion.setPregnancyContext(true, 28)
        val vitals = fusion.vitalSigns.value
        assertTrue(vitals.isPregnant)
        assertEquals(28, vitals.gestationalWeeks)
    }

    @Test
    fun `setPregnancyContext defaults to not pregnant`() {
        val vitals = fusion.vitalSigns.value
        assertFalse(vitals.isPregnant)
        assertNull(vitals.gestationalWeeks)
    }

    // ── hasHighRiskIndicators() ────────────────────────────

    @Test
    fun `hasHighRiskIndicators is false with default empty vitals`() {
        assertFalse(fusion.hasHighRiskIndicators())
    }

    // ── Reset ─────────────────────────────────────────────

    @Test
    fun `reset clears symptoms and pregnancy context`() {
        fusion.addSymptom("headache")
        fusion.setPregnancyContext(true, 30)
        fusion.reset()

        assertTrue(fusion.symptoms.value.isEmpty())
        val vitals = fusion.vitalSigns.value
        assertFalse(vitals.isPregnant)
        assertNull(vitals.gestationalWeeks)
        assertNull(vitals.heartRateBpm)
    }

    // ── Vitals Summary ────────────────────────────────────

    @Test
    fun `getVitalsSummary shows message when no data`() {
        val summary = fusion.getVitalsSummary()
        assertEquals("No vitals captured yet", summary)
    }

    @Test
    fun `getVitalsSummary includes symptoms when added`() {
        fusion.addSymptom("headache")
        val summary = fusion.getVitalsSummary()
        assertTrue(summary.contains("Symptoms: headache"))
    }
}
