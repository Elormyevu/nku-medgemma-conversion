package com.nku.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Conjunctival Pallor Detector for Anemia Screening
 *
 * Uses HSV color histogram analysis of the palpebral conjunctiva
 * (lower eyelid inner surface) to detect pallor as an indicator of anemia.
 *
 * Literature basis:
 * - Conjunctival pallor is a validated clinical sign for anemia screening,
 *   independent of Fitzpatrick skin type [Zucker et al., Bull WHO, 1997:
 *   80% sensitivity, 82% specificity for moderate anemia by clinician assessment]
 * - HSV color space analysis of conjunctival images is an established approach
 *   in smartphone-based anemia detection [Mannino et al., Nat Commun, 2018;
 *   Dimauro et al., J Biomed Inform, 2018]
 * - Published systems typically use ML regression trained on hemoglobin data
 *   (e.g., High Hue Ratio method); our fixed-threshold approach is a lighter
 *   alternative for resource-constrained on-device screening
 *
 * Architecture role:
 * - This detector provides quantitative pallor features to MedGemma for
 *   clinical reasoning; it is a feature extractor, not a standalone diagnostic
 * - MedGemma receives the raw score + confidence and applies medical knowledge
 * - Severity thresholds are conservative screening estimates designed to
 *   over-refer rather than miss cases; field calibration against clinician
 *   pallor grading is required to optimize sensitivity/specificity tradeoff
 *
 * No ML model required — pure signal processing.
 * Footprint: ~0 MB storage, ~1 MB RAM
 */

data class PallorResult(
    val pallorScore: Float = 0f,           // 0.0 = healthy pink, 1.0 = severe pallor
    val confidence: Float = 0f,            // 0.0 - 1.0
    val severity: PallorSeverity = PallorSeverity.NORMAL,
    val recommendation: String = "No analysis",
    val hasBeenAnalyzed: Boolean = false
)

enum class PallorSeverity {
    NORMAL,           // pallorScore < 0.3
    MILD,             // 0.3 - 0.5
    MODERATE,         // 0.5 - 0.7
    SEVERE            // > 0.7
}

class PallorDetector {
    
    companion object {
        // HSV saturation thresholds for conjunctival pallor screening.
        // Healthy conjunctiva: rich pink/red (high saturation)
        // Anemic conjunctiva: pale, washed out (low saturation)
        //
        // Note: Published smartphone anemia systems (Mannino 2018, Dimauro 2018)
        // use ML regression trained on paired Hb data rather than fixed cutoffs.
        // These thresholds are conservative screening estimates for on-device use
        // where Hb training data is unavailable. The raw pallor score is passed
        // to MedGemma for clinical interpretation regardless of severity label.
        private const val HEALTHY_SATURATION_MIN = 0.20f
        private const val PALLOR_SATURATION_THRESHOLD = 0.10f
        
        // Hue range for conjunctival tissue (pink/red, 0-40 degrees + wrap)
        private const val TISSUE_HUE_MIN = 0f
        private const val TISSUE_HUE_MAX = 45f
        private const val TISSUE_HUE_WRAP_MIN = 330f
        
        // Minimum valid tissue pixel coverage
        private const val MIN_TISSUE_PIXEL_RATIO = 0.25f
        
        // Conjunctiva sensitivity boost (more vascular = more sensitive indicator)
        private const val CONJUNCTIVA_SENSITIVITY = 1.2f
    }
    
    private val _result = MutableStateFlow(
        PallorResult(0f, 0f, PallorSeverity.NORMAL, "No analysis", hasBeenAnalyzed = false)
    )
    val result: StateFlow<PallorResult> = _result.asStateFlow()
    
    // F-PF-3: Reusable pixel buffer to avoid per-call allocations
    private var pixelBuffer: IntArray = IntArray(0)
    private var lastBufferWidth = 0
    private var lastBufferHeight = 0
    
    /**
     * Analyze conjunctiva (lower eyelid) for pallor
     * 
     * Instructions for CHW:
     * 1. Gently pull down the patient's lower eyelid
     * 2. Point the camera at the inner surface (palpebral conjunctiva)
     * 3. Ensure good lighting (natural daylight preferred)
     * 4. Hold steady for 2-3 seconds
     */
    fun analyzeConjunctiva(bitmap: Bitmap): PallorResult {
        // Sample pixels efficiently
        val stepSize = max(1, min(bitmap.width, bitmap.height) / 100)
        
        var tissuePixelCount = 0
        var totalPixels = 0
        var saturationSum = 0f
        var valueSum = 0f
        var hueSum = 0f
        
        // Histogram buckets for saturation distribution analysis
        val satHistogram = IntArray(10) // 10 bins from 0.0 to 1.0
        
        val hsv = FloatArray(3)
        
        // F-PF-3: Reuse pixel buffer if dimensions match, otherwise resize lazily
        val totalPixelCount = bitmap.width * bitmap.height
        if (bitmap.width != lastBufferWidth || bitmap.height != lastBufferHeight) {
            pixelBuffer = IntArray(totalPixelCount)
            lastBufferWidth = bitmap.width
            lastBufferHeight = bitmap.height
        }
        bitmap.getPixels(pixelBuffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (x in 0 until bitmap.width step stepSize) {
            for (y in 0 until bitmap.height step stepSize) {
                val pixel = pixelBuffer[y * bitmap.width + x]
                Color.colorToHSV(pixel, hsv)
                
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                
                totalPixels++
                
                // Check if pixel is within conjunctival tissue range
                val isTissue = ((hue in TISSUE_HUE_MIN..TISSUE_HUE_MAX) || 
                                (hue >= TISSUE_HUE_WRAP_MIN)) &&
                               value > 0.15f && value < 0.95f
                
                if (isTissue) {
                    tissuePixelCount++
                    saturationSum += saturation
                    valueSum += value
                    hueSum += if (hue > 180) hue - 360 else hue
                    
                    // Build saturation histogram
                    val bin = (saturation * 9).toInt().coerceIn(0, 9)
                    satHistogram[bin]++
                }
            }
        }
        
        // Check if we have enough tissue pixels
        val tissueRatio = tissuePixelCount.toFloat() / totalPixels
        if (tissueRatio < MIN_TISSUE_PIXEL_RATIO || tissuePixelCount < 50) {
            val result = PallorResult(
                pallorScore = 0f,
                confidence = 0f,
                severity = PallorSeverity.NORMAL,
                recommendation = "Unable to detect conjunctival tissue. Gently pull down the lower eyelid and ensure good lighting."
            )
            _result.value = result
            return result
        }
        
        // Calculate averages
        val avgSaturation = saturationSum / tissuePixelCount
        val avgValue = valueSum / tissuePixelCount
        
        // Calculate pallor score from saturation
        // Lower saturation = more pallor (less blood perfusion)
        val rawPallorScore = when {
            avgSaturation >= HEALTHY_SATURATION_MIN -> 0f
            avgSaturation <= PALLOR_SATURATION_THRESHOLD -> 1f
            else -> {
                val range = HEALTHY_SATURATION_MIN - PALLOR_SATURATION_THRESHOLD
                1f - ((avgSaturation - PALLOR_SATURATION_THRESHOLD) / range)
            }
        }
        
        // Apply conjunctiva sensitivity boost
        val pallorScore = (rawPallorScore * CONJUNCTIVA_SENSITIVITY).coerceIn(0f, 1f)
        
        // Confidence based on tissue coverage and signal quality
        val coverageConfidence = (tissueRatio * 1.5f).coerceIn(0.3f, 0.95f)
        
        // Higher confidence if saturation histogram is unimodal (consistent tissue)
        val peakBin = satHistogram.indexOfFirst { it == satHistogram.max() }
        val peakRatio = satHistogram[peakBin].toFloat() / tissuePixelCount
        val histogramConfidence = (peakRatio * 2f).coerceIn(0.3f, 0.95f)
        
        val confidence = (coverageConfidence + histogramConfidence) / 2f
        
        // Determine severity
        val severity = when {
            pallorScore < 0.3f -> PallorSeverity.NORMAL
            pallorScore < 0.5f -> PallorSeverity.MILD
            pallorScore < 0.7f -> PallorSeverity.MODERATE
            else -> PallorSeverity.SEVERE
        }
        
        // Generate recommendation
        val recommendation = when (severity) {
            PallorSeverity.NORMAL -> 
                "Conjunctiva appears well-perfused. No significant pallor detected. Continue routine monitoring."
            PallorSeverity.MILD -> 
                "Mild conjunctival pallor detected. Consider dietary iron intake assessment. Recheck in 1 week."
            PallorSeverity.MODERATE -> 
                "Moderate conjunctival pallor detected. Recommend hemoglobin test at nearest health facility within 3 days."
            PallorSeverity.SEVERE -> 
                "Significant conjunctival pallor detected. URGENT: Seek immediate medical evaluation for possible severe anemia."
        }
        
        val result = PallorResult(
            pallorScore = pallorScore,
            confidence = confidence,
            severity = severity,
            recommendation = recommendation,
            hasBeenAnalyzed = true
        )
        
        _result.value = result
        return result
    }
    
    /**
     * Quick check if image appears to be valid conjunctival tissue
     */
    fun isValidConjunctivaImage(bitmap: Bitmap): Boolean {
        val stepSize = max(1, min(bitmap.width, bitmap.height) / 50)
        var tissuePixels = 0
        var totalPixels = 0
        val hsv = FloatArray(3)
        
        // F-3 fix: Batch getPixels() for consistency with main analysis path
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (x in 0 until bitmap.width step stepSize) {
            for (y in 0 until bitmap.height step stepSize) {
                Color.colorToHSV(pixels[y * bitmap.width + x], hsv)
                totalPixels++
                
                val hue = hsv[0]
                val value = hsv[2]
                
                if (((hue in TISSUE_HUE_MIN..TISSUE_HUE_MAX) || hue >= TISSUE_HUE_WRAP_MIN) &&
                    value > 0.15f && value < 0.95f) {
                    tissuePixels++
                }
            }
        }
        
        return (tissuePixels.toFloat() / totalPixels) >= MIN_TISSUE_PIXEL_RATIO
    }
    
    fun reset() {
        _result.value = PallorResult(0f, 0f, PallorSeverity.NORMAL, "No analysis", hasBeenAnalyzed = false)
    }
}
