package com.nku.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ClinicalReasoner — verifies rule-based triage
 * severity scoring and vital-sign interpretation.
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
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        assertEquals(TriageCategory.GREEN, assessment!!.triageCategory)
    }

    @Test
    fun `tachycardia elevates triage to YELLOW or higher`() {
        val vitals = VitalSigns(
            heartRateBpm = 115f,
            heartRateConfidence = 0.9f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f
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
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        val cat = assessment!!.triageCategory
        assertTrue(cat == TriageCategory.ORANGE || cat == TriageCategory.RED)
    }

    @Test
    fun `significant edema produces ORANGE or RED triage`() {
        val vitals = VitalSigns(
            heartRateBpm = 80f,
            heartRateConfidence = 0.8f,
            pallorSeverity = PallorSeverity.NORMAL,
            pallorScore = 0.1f,
            edemaSeverity = EdemaSeverity.SIGNIFICANT,
            edemaScore = 0.85f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        val cat = assessment!!.triageCategory
        assertTrue(cat == TriageCategory.ORANGE || cat == TriageCategory.RED)
    }

    // ── Prompt Generation ──

    @Test
    fun `generatePrompt includes vital signs`() {
        val vitals = VitalSigns(
            heartRateBpm = 90f,
            heartRateConfidence = 0.85f,
            pallorSeverity = PallorSeverity.MILD,
            pallorScore = 0.35f,
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.1f
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
            edemaSeverity = EdemaSeverity.NORMAL,
            edemaScore = 0.05f
        )
        reasoner.createRuleBasedAssessment(vitals)
        val assessment = reasoner.assessment.value
        assertNotNull(assessment)
        assertTrue(assessment!!.disclaimer.isNotBlank())
    }
}
