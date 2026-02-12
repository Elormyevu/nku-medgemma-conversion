package com.nku.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensor Fusion - Aggregates all Nku Sentinel outputs
 * 
 * Collects data from:
 * - RPPGProcessor (heart rate)
 * - PallorDetector (anemia screening)
 * - JaundiceDetector (jaundice screening)
 * - EdemaDetector (preeclampsia screening)
 * - User-reported symptoms
 * 
 * Outputs structured VitalSigns for ClinicalReasoner
 */

data class VitalSigns(
    // Cardio Check (rPPG)
    val heartRateBpm: Float? = null,
    val heartRateConfidence: Float = 0f,
    val heartRateQuality: String = "unknown",
    
    // Anemia Screening (Pallor)
    val pallorScore: Float? = null,
    val pallorSeverity: PallorSeverity? = null,
    val pallorConfidence: Float = 0f,
    val conjunctivalSaturation: Float? = null,   // Raw HSV saturation of conjunctival tissue
    val conjunctivalTissueCoverage: Float? = null, // Fraction of ROI classified as tissue
    
    // Jaundice Screening (Scleral Icterus)
    val jaundiceScore: Float? = null,
    val jaundiceSeverity: JaundiceSeverity? = null,
    val jaundiceConfidence: Float = 0f,
    val scleralYellowRatio: Float? = null,       // Raw yellow-band pixel fraction in sclera
    
    // Preeclampsia Screening (Edema)
    val edemaScore: Float? = null,
    val edemaSeverity: EdemaSeverity? = null,
    val periorbitalScore: Float? = null,
    val edemaConfidence: Float = 0f,
    val eyeAspectRatio: Float? = null,           // Raw EAR from MediaPipe landmarks
    val facialSwellingScore: Float? = null,      // Overall facial swelling component
    
    // User-reported symptoms
    val reportedSymptoms: List<String> = emptyList(),
    
    // Pregnancy context (important for preeclampsia)
    val isPregnant: Boolean = false,
    val gestationalWeeks: Int? = null,
    
    // Metadata
    val captureTimestamp: Long = System.currentTimeMillis(),
    val allSensorsComplete: Boolean = false
)

data class SymptomEntry(
    val symptom: String,
    val duration: String? = null,  // e.g., "2 days", "1 week"
    val severity: String? = null   // "mild", "moderate", "severe"
)

class SensorFusion(
    private val rppgProcessor: RPPGProcessor,
    private val pallorDetector: PallorDetector,
    private val jaundiceDetector: JaundiceDetector,
    private val edemaDetector: EdemaDetector
) {
    private val _vitalSigns = MutableStateFlow(VitalSigns())
    val vitalSigns: StateFlow<VitalSigns> = _vitalSigns.asStateFlow()
    
    private val _symptoms = MutableStateFlow<List<SymptomEntry>>(emptyList())
    val symptoms: StateFlow<List<SymptomEntry>> = _symptoms.asStateFlow()
    
    private var isPregnant = false
    private var gestationalWeeks: Int? = null
    
    /**
     * Update vital signs by collecting from all sensors
     */
    fun updateVitalSigns() {
        val rppgResult = rppgProcessor.result.value
        val pallorResult = pallorDetector.result.value
        val jaundiceResult = jaundiceDetector.result.value
        val edemaResult = edemaDetector.result.value
        
        val hasHeartRate = rppgResult.bpm != null && rppgResult.confidence > 0.5f
        val hasPallor = pallorResult.confidence > 0.3f
        val hasJaundice = jaundiceResult.confidence > 0.3f
        val hasEdema = edemaResult.confidence > 0.3f
        
        _vitalSigns.value = VitalSigns(
            // Heart rate
            heartRateBpm = rppgResult.bpm,
            heartRateConfidence = rppgResult.confidence,
            heartRateQuality = rppgResult.signalQuality,
            
            // Pallor — raw biomarkers for clinically explicit prompt
            pallorScore = if (hasPallor) pallorResult.pallorScore else null,
            pallorSeverity = if (hasPallor) pallorResult.severity else null,
            pallorConfidence = pallorResult.confidence,
            conjunctivalSaturation = if (hasPallor) pallorResult.avgSaturation else null,
            conjunctivalTissueCoverage = if (hasPallor) pallorResult.tissueRatio else null,
            
            // Jaundice — raw biomarkers for clinically explicit prompt
            jaundiceScore = if (hasJaundice) jaundiceResult.jaundiceScore else null,
            jaundiceSeverity = if (hasJaundice) jaundiceResult.severity else null,
            jaundiceConfidence = jaundiceResult.confidence,
            scleralYellowRatio = if (hasJaundice) jaundiceResult.yellowRatio else null,
            
            // Edema — raw biomarkers for clinically explicit prompt
            edemaScore = if (hasEdema) edemaResult.edemaScore else null,
            edemaSeverity = if (hasEdema) edemaResult.severity else null,
            periorbitalScore = if (hasEdema) edemaResult.periorbitalScore else null,
            edemaConfidence = edemaResult.confidence,
            eyeAspectRatio = if (hasEdema) edemaResult.avgEyeAspectRatio else null,
            facialSwellingScore = if (hasEdema) edemaResult.facialScore else null,
            
            // Context
            reportedSymptoms = _symptoms.value.map { it.symptom },
            isPregnant = isPregnant,
            gestationalWeeks = gestationalWeeks,
            
            // Completion check
            allSensorsComplete = hasHeartRate && hasPallor && hasJaundice && hasEdema,
            captureTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Add a symptom reported by the user
     */
    fun addSymptom(symptom: String, duration: String? = null, severity: String? = null) {
        val entry = SymptomEntry(symptom, duration, severity)
        _symptoms.value = _symptoms.value + entry
        updateVitalSigns()
    }
    
    /**
     * Remove a symptom
     */
    fun removeSymptom(symptom: String) {
        _symptoms.value = _symptoms.value.filter { it.symptom != symptom }
        updateVitalSigns()
    }
    
    /**
     * Clear all symptoms
     */
    fun clearSymptoms() {
        _symptoms.value = emptyList()
        updateVitalSigns()
    }
    
    /**
     * Set pregnancy context (important for preeclampsia risk)
     */
    fun setPregnancyContext(pregnant: Boolean, weeks: Int? = null) {
        isPregnant = pregnant
        gestationalWeeks = weeks
        updateVitalSigns()
    }
    
    /**
     * Get summary string for display
     */
    fun getVitalsSummary(): String {
        val vitals = _vitalSigns.value
        val parts = mutableListOf<String>()
        
        vitals.heartRateBpm?.let {
            parts.add("HR: ${it.toInt()} bpm")
        }
        
        vitals.pallorScore?.let {
            val label = when (vitals.pallorSeverity) {
                PallorSeverity.NORMAL -> "Normal"
                PallorSeverity.MILD -> "Mild pallor"
                PallorSeverity.MODERATE -> "Moderate pallor"
                PallorSeverity.SEVERE -> "Severe pallor"
                null -> "Unknown"
            }
            parts.add("Pallor: $label")
        }
        
        vitals.jaundiceScore?.let {
            val label = when (vitals.jaundiceSeverity) {
                JaundiceSeverity.NORMAL -> "Normal"
                JaundiceSeverity.MILD -> "Mild jaundice"
                JaundiceSeverity.MODERATE -> "Moderate jaundice"
                JaundiceSeverity.SEVERE -> "Severe jaundice"
                null -> "Unknown"
            }
            parts.add("Jaundice: $label")
        }
        
        vitals.edemaScore?.let {
            val label = when (vitals.edemaSeverity) {
                EdemaSeverity.NORMAL -> "Normal"
                EdemaSeverity.MILD -> "Mild swelling"
                EdemaSeverity.MODERATE -> "Moderate swelling"
                EdemaSeverity.SIGNIFICANT -> "Significant swelling"
                null -> "Unknown"
            }
            parts.add("Edema: $label")
        }
        
        if (vitals.reportedSymptoms.isNotEmpty()) {
            parts.add("Symptoms: ${vitals.reportedSymptoms.joinToString(", ")}")
        }
        
        return if (parts.isEmpty()) "No vitals captured yet" else parts.joinToString(" | ")
    }
    
    /**
     * Check if any screening indicates high risk
     */
    fun hasHighRiskIndicators(): Boolean {
        val vitals = _vitalSigns.value
        
        // High heart rate
        val highHR = vitals.heartRateBpm?.let { it > 100 || it < 50 } ?: false
        
        // Moderate+ pallor
        val significantPallor = vitals.pallorSeverity in listOf(
            PallorSeverity.MODERATE, PallorSeverity.SEVERE
        )
        
        // Moderate+ jaundice
        val significantJaundice = vitals.jaundiceSeverity in listOf(
            JaundiceSeverity.MODERATE, JaundiceSeverity.SEVERE
        )
        
        // F-5 fix: SIGNIFICANT edema always flags high risk regardless of pregnancy.
        // MODERATE edema remains pregnancy-gated. Significant facial swelling
        // warrants referral even without confirmed pregnancy status.
        val significantEdema = vitals.edemaSeverity == EdemaSeverity.SIGNIFICANT
        val moderateEdemaPregnant = vitals.edemaSeverity == EdemaSeverity.MODERATE && isPregnant
        
        return highHR || significantPallor || significantJaundice || significantEdema || moderateEdemaPregnant
    }
    
    /**
     * Reset all sensors and symptoms
     */
    fun reset() {
        rppgProcessor.reset()
        pallorDetector.reset()
        jaundiceDetector.reset()
        edemaDetector.reset()
        _symptoms.value = emptyList()
        isPregnant = false
        gestationalWeeks = null
        _vitalSigns.value = VitalSigns()
    }
}
