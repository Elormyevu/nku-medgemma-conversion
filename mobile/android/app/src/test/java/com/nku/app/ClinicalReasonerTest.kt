package com.nku.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ClinicalReasoner — verifies rule-based triage
 * severity scoring, vital-sign interpretation, and confidence-based abstention.
 * (Audit Fix F-TC-1)
 */
class ClinicalReasonerTest {

    private val reasoner = ClinicalReasoner()

    // ── Triage Category Assignment ──

    @Test
    fun `normal vitals produce GREEN triage`() {
        val vitals = VitalSigns(
            heartRateBpm = 72f,
            heartRateConfidence = 0.9f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            pallorConfidence = 0.85f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f,
            edemaConfidence = 0.8f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        assertEquals(TriageCategory.GREEN, assessment!!.triageCategory)
    }

    @Test
    fun `tachycardia elevates triage to YELLOW or higher`() {
        val vitals = VitalSigns(
            heartRateBpm = 125f,
            heartRateConfidence = 0.9f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            pallorConfidence = 0.85f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f,
            edemaConfidence = 0.8f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        assertTrue(assessment!!.triageCategory != TriageCategory.GREEN)
    }

    @Test
    fun `severe pallor produces ORANGE or RED triage`() {
        val vitals = VitalSigns(
            heartRateBpm = 80f,
            heartRateConfidence = 0.8f,
            pallorSeverity = PallorSeverity.SEVERE,
            pallorScore = 0.9f,
            pallorConfidence = 0.85f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f,
            edemaConfidence = 0.8f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        val cat = assessment!!.triageCategory
        assertTrue(cat == TriageCategory.ORANGE || cat == TriageCategory.RED)
    }

    @Test
    fun `significant edema in pregnancy produces ORANGE or RED triage`() {
        val vitals = VitalSigns(
            heartRateBpm = 80f,
            heartRateConfidence = 0.8f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            pallorConfidence = 0.85f,
            edemaSeverity = EdemaSeverity.SIGNIFICANT,
            edemaScore = 0.85f,
            edemaConfidence = 0.9f,
            isPregnant = true,
            gestationalWeeks = 28
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        val cat = assessment!!.triageCategory
        assertTrue(cat == TriageCategory.ORANGE || cat == TriageCategory.RED)
    }

    // ── Confidence-Based Abstention ──

    @Test
    fun `low confidence sensors trigger abstention`() {
        val vitals = VitalSigns(
            heartRateBpm = 120f,
            heartRateConfidence = 0.3f,   // below 0.75 threshold
            pallorSeverity = PallorSeverity.SEVERE,
            pallorScore = 0.9f,
            pallorConfidence = 0.4f,       // below 0.75 threshold
            edemaSeverity = EdemaSeverity.SIGNIFICANT,
            edemaScore = 0.8f,
            edemaConfidence = 0.2f         // below 0.75 threshold
        )
        val assessment = reasoner.createRuleBasedAssessment(vitals)
        assertTrue(
            "Should abstain when all sensors below confidence threshold",
            assessment.prompt.contains("ABSTAINED")
        )
    }

    @Test
    fun `mixed confidence excludes low-confidence sensors only`() {
        val vitals = VitalSigns(
            heartRateBpm = 130f,
            heartRateConfidence = 0.9f,    // above threshold — should be used
            pallorSeverity = PallorSeverity.SEVERE,
            pallorScore = 0.9f,
            pallorConfidence = 0.3f,       // below threshold — should be excluded
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f,
            edemaConfidence = 0.8f
        )
        val assessment = reasoner.createRuleBasedAssessment(vitals)
        // Should NOT abstain because HR has good confidence
        assertFalse(assessment.prompt.contains("ABSTAINED"))
        // Should note that pallor was excluded
        val hasPallorExcluded = assessment.primaryConcerns.any { it.contains("Pallor") && it.contains("excluded") }
        assertTrue("Should note low-confidence pallor was excluded", hasPallorExcluded)
        // Tachycardia should still be detected
        val hasTachy = assessment.primaryConcerns.any { it.contains("tachycardia", ignoreCase = true) }
        assertTrue("Should still detect tachycardia from high-confidence HR", hasTachy)
    }

    // ── Prompt Generation ──

    @Test
    fun `generatePrompt includes vital signs`() {
        val vitals = VitalSigns(
            heartRateBpm = 90f,
            heartRateConfidence = 0.85f,
            pallorSeverity = PallorSeverity.MILD,
            pallorScore = 0.35f,
            pallorConfidence = 0.8f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.1f,
            edemaConfidence = 0.8f
        )
        val prompt = reasoner.generatePrompt(vitals)
        assertTrue(prompt.contains("90"))
        assertTrue(prompt.contains("MILD", ignoreCase = true))
    }

    @Test
    fun `assessment includes disclaimer`() {
        val vitals = VitalSigns(
            heartRateBpm = 72f,
            heartRateConfidence = 0.9f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            pallorConfidence = 0.85f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f,
            edemaConfidence = 0.8f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        assertTrue(assessment!!.disclaimer.isNotBlank())
    }
}
