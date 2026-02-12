package com.nku.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JaundiceDetector — Scleral icterus detection for jaundice screening.
 *
 * Detects yellowing of the sclera (white of the eye) and surrounding skin,
 * which is clinically correlated with elevated serum bilirubin (>2.5 mg/dL).
 *
 * **Method**: HSV color space analysis.  Unlike pallor detection (which keys on
 * low saturation of the conjunctiva), jaundice detection keys on the *hue*
 * shifting into the yellow band (H ≈ 15–45° in OpenCV convention / 0.04–0.13
 * in normalized 0–1 scale).  Elevated saturation in normally white scleral
 * tissue further confirms bilirubin deposition.
 *
 * **Clinical basis**:
 *   - Scleral icterus is considered the most reliable clinical sign of
 *     jaundice and can be detected at bilirubin levels of 2–3 mg/dL.
 *   - Studies show that smartphone-based scleral analysis can correlate with
 *     lab-measured bilirubin (Mariakakis et al., Proc ACM IMWUT 2017).
 *
 * **Scoring**: 0.0 = no icterus, 1.0 = severe icterus.
 * Signal: fraction of scleral-region pixels whose hue falls in the yellow band.
 *
 * **Skin-tone independence**: Scleral tissue is unpigmented in all ethnicities,
 * making this assessment inherently skin-tone agnostic.
 */

data class JaundiceResult(
    val jaundiceScore: Float = 0f,           // 0.0 = normal white sclera, 1.0 = severe icterus
    val confidence: Float = 0f,              // 0.0 - 1.0
    val severity: JaundiceSeverity = JaundiceSeverity.NORMAL,
    val recommendation: String = "No analysis",
    val hasBeenAnalyzed: Boolean = false,
    val yellowRatio: Float = 0f,             // Fraction of ROI pixels in yellow HSV band
    val avgHue: Float = 0f                   // Mean hue of scleral tissue pixels (0-1 normalized)
)

enum class JaundiceSeverity {
    NORMAL,           // jaundiceScore < 0.25
    MILD,             // 0.25 - 0.5
    MODERATE,         // 0.5 - 0.75
    SEVERE            // > 0.75
}

class JaundiceDetector {

    companion object {
        private const val TAG = "JaundiceDetector"

        // ── HSV thresholds (normalized 0–1 scale) ──────────────────────
        // Yellow hue band: ~15°–45° on the 0°–360° wheel → 0.04–0.125 normalized
        private const val YELLOW_HUE_MIN = 0.04f
        private const val YELLOW_HUE_MAX = 0.125f

        // Minimum saturation for a pixel to be considered "yellow" (not just off-white)
        private const val YELLOW_SAT_MIN = 0.12f

        // Scleral tissue filter: exclude very dark pixels (unlikely sclera)
        private const val MIN_VALUE = 0.40f

        // Scleral tissue filter: healthy sclera is bright and low-saturation
        // Pixels with V>0.6 and S<0.35 are candidates for scleral tissue
        private const val SCLERA_MAX_SAT = 0.35f
        private const val SCLERA_MIN_VAL = 0.60f
    }

    private val _result = MutableStateFlow(JaundiceResult())
    val result: StateFlow<JaundiceResult> = _result.asStateFlow()

    /**
     * Analyze a bitmap of the eye region (sclera) for jaundice.
     *
     * Expected input: cropped image of the eye region, ideally captured
     * with the rear camera in good lighting.  The entire bitmap is analyzed
     * — no landmark isolation is performed here (the caller or face detector
     * should provide an appropriately cropped region).
     *
     * The algorithm:
     * 1. Convert each pixel to HSV.
     * 2. Identify scleral tissue candidates (bright, low-to-moderate saturation).
     * 3. Among scleral candidates, count how many fall in the yellow hue band.
     * 4. yellowRatio = yellowPixels / scleralPixels.
     * 5. Map yellowRatio to jaundiceScore via a sigmoid-like transfer function.
     */
    fun analyzeSclera(bitmap: Bitmap) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val totalPixels = width * height

            if (totalPixels < 100) {
                Log.w(TAG, "Image too small for analysis: ${width}x${height}")
                _result.value = JaundiceResult(
                    recommendation = "Image too small — recapture",
                    hasBeenAnalyzed = true,
                    confidence = 0f
                )
                return
            }

            var scleralPixels = 0
            var yellowPixels = 0
            var hueSum = 0.0
            var satSum = 0.0

            val hsv = FloatArray(3)
            val pixels = IntArray(totalPixels)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            for (pixel in pixels) {
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                rgbToHsv(r, g, b, hsv)
                val h = hsv[0]  // 0-1 normalized
                val s = hsv[1]  // 0-1
                val v = hsv[2]  // 0-1

                // Skip very dark pixels (shadows, pupil, iris)
                if (v < MIN_VALUE) continue

                // Scleral tissue candidate: bright + not overly saturated
                // This captures both healthy (white) and jaundiced (yellowish) sclera
                if (v >= SCLERA_MIN_VAL && s < SCLERA_MAX_SAT) {
                    scleralPixels++
                    hueSum += h
                    satSum += s

                    // Is this pixel in the yellow band?
                    if (h in YELLOW_HUE_MIN..YELLOW_HUE_MAX && s >= YELLOW_SAT_MIN) {
                        yellowPixels++
                    }
                }
            }

            if (scleralPixels < 50) {
                Log.w(TAG, "Insufficient scleral tissue detected: $scleralPixels pixels")
                _result.value = JaundiceResult(
                    recommendation = "Could not identify enough scleral tissue — improve lighting and framing",
                    hasBeenAnalyzed = true,
                    confidence = 0.1f
                )
                return
            }

            val yellowRatio = yellowPixels.toFloat() / scleralPixels
            val avgHue = (hueSum / scleralPixels).toFloat()
            val avgSat = (satSum / scleralPixels).toFloat()

            // ── Score calculation ──────────────────────────────────────
            // Transfer function: sigmoid mapping yellowRatio to 0–1 score
            // Calibration points:
            //   yellowRatio ≈ 0.05 → score ≈ 0.1 (normal variation)
            //   yellowRatio ≈ 0.20 → score ≈ 0.4 (mild)
            //   yellowRatio ≈ 0.40 → score ≈ 0.7 (moderate)
            //   yellowRatio ≈ 0.60 → score ≈ 0.9 (severe)
            val jaundiceScore = (1.0f / (1.0f + Math.exp(-10.0 * (yellowRatio - 0.25)).toFloat()))
                .coerceIn(0f, 1f)

            // ── Confidence estimation ─────────────────────────────────
            // Higher confidence when:
            //   - More scleral pixels detected (better tissue coverage)
            //   - Image is bright enough (good lighting)
            val tissueCoverage = (scleralPixels.toFloat() / totalPixels).coerceIn(0f, 1f)
            val coverageConf = (tissueCoverage / 0.15f).coerceIn(0f, 1f)  // saturates at 15% coverage
            val pixelCountConf = (scleralPixels.toFloat() / 500f).coerceIn(0f, 1f)  // saturates at 500 px
            val confidence = (0.5f * coverageConf + 0.5f * pixelCountConf).coerceIn(0f, 1f)

            // ── Severity classification ───────────────────────────────
            val severity = when {
                jaundiceScore < 0.25f -> JaundiceSeverity.NORMAL
                jaundiceScore < 0.50f -> JaundiceSeverity.MILD
                jaundiceScore < 0.75f -> JaundiceSeverity.MODERATE
                else -> JaundiceSeverity.SEVERE
            }

            val recommendation = when (severity) {
                JaundiceSeverity.NORMAL ->
                    "Sclera appears normal — no jaundice detected."
                JaundiceSeverity.MILD ->
                    "Mild scleral yellowing detected. Consider liver function test at next clinic visit. " +
                    "Monitor for dark urine, pale stool, or abdominal pain."
                JaundiceSeverity.MODERATE ->
                    "Moderate scleral icterus detected. Liver function test recommended within 3-5 days. " +
                    "Check for hepatitis symptoms (fatigue, nausea, right upper abdominal pain). " +
                    "Review medications for hepatotoxicity."
                JaundiceSeverity.SEVERE ->
                    "Severe scleral icterus detected — likely significantly elevated bilirubin. " +
                    "URGENT: Refer for bilirubin level + liver function tests within 24-48 hours. " +
                    "Assess for signs of liver failure (confusion, easy bruising, ascites)."
            }

            _result.value = JaundiceResult(
                jaundiceScore = jaundiceScore,
                confidence = confidence,
                severity = severity,
                recommendation = recommendation,
                hasBeenAnalyzed = true,
                yellowRatio = yellowRatio,
                avgHue = avgHue
            )

            Log.d(TAG, "Jaundice analysis: score=${"%.2f".format(jaundiceScore)}, " +
                    "yellowRatio=${"%.3f".format(yellowRatio)}, " +
                    "severity=${severity.name}, confidence=${"%.2f".format(confidence)}, " +
                    "scleralPx=$scleralPixels, yellowPx=$yellowPixels")

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing sclera: ${e.message}", e)
            _result.value = JaundiceResult(
                recommendation = "Analysis error — please recapture",
                hasBeenAnalyzed = true,
                confidence = 0f
            )
        }
    }

    /**
     * Reset detector state to initial.
     */
    fun reset() {
        _result.value = JaundiceResult()
    }

    // ── Helper: RGB → HSV conversion (normalized 0–1) ──────────────
    // Android's Color.colorToHSV outputs H in [0,360], S in [0,1], V in [0,1].
    // We normalize H to [0,1] for consistent threshold comparisons.
    private fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val androidHsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, androidHsv)
        out[0] = androidHsv[0] / 360f   // Normalize hue to 0-1
        out[1] = androidHsv[1]           // Saturation already 0-1
        out[2] = androidHsv[2]           // Value already 0-1
    }
}
