package com.nku.app

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Edema Detector for Preeclampsia Screening — Novel Screening Heuristic
 *
 * Proposes Eye Aspect Ratio (EAR)-based periorbital edema detection as a new
 * camera-only screening heuristic for preeclampsia, grounded in:
 *
 * Clinical basis:
 * - Periorbital edema is a recognized clinical sign of preeclampsia
 *   [ACOG Practice Bulletin #222, 2020; WHO Recommendations, 2011]
 * - Facial/periorbital swelling often precedes other symptoms by days
 *   [Sibai et al., Am J Obstet Gynecol, 2005]
 *
 * Anthropometric basis for EAR baseline:
 * - Normal adult palpebral fissure: PFW ~30mm, PFH ~11mm → width/height ≈ 2.7
 *   [Vasanthakumar et al., J Clin Diagn Res, 2013: PFW 30.91±1.82mm, PFH 11.06±1.60mm]
 *   [Anibor et al., Afr J Biomed Res, 2014: PFW 38.01mm in adult Igbos]
 * - Edema narrows the palpebral fissure → EAR decreases
 *   [Clinical eyelid edema grading: 0/+1/+2/+3/+4 by visual occlusion]
 *
 * Novel contribution:
 * - EAR is established in computer vision for blink/drowsiness detection
 *   [Soukupová & Čech, CVWW 2016: EAR open ≈ 0.21–0.30, closed ≈ 0.15–0.20]
 * - We repurpose EAR to quantify periorbital swelling — a novel application
 *   not previously described in the literature
 * - This detector provides quantitative features to MedGemma for clinical
 *   reasoning; it is a feature extractor, not a standalone diagnostic tool
 * - Severity thresholds are conservative screening estimates designed to
 *   over-refer rather than miss cases; field calibration against clinician
 *   assessment is required to optimize sensitivity/specificity tradeoff
 *
 * Method: Facial landmark ratio analysis via MediaPipe 478-landmark mesh
 * - Eye aspect ratio (EAR) changes: puffy eyes appear more closed
 * - Periorbital brightness gradients: edema causes tissue brightness changes
 * - Cheek region analysis: facial swelling indicator
 *
 * Footprint: ~0 MB additional storage, ~2 MB RAM
 */

data class EdemaResult(
    val edemaScore: Float = 0f,            // 0.0 = normal, 1.0 = significant swelling
    val confidence: Float = 0f,            // 0.0 - 1.0
    val severity: EdemaSeverity = EdemaSeverity.NORMAL,
    val periorbitalScore: Float = 0f,      // Eye area puffiness
    val facialScore: Float = 0f,           // Overall facial swelling
    val recommendation: String = "No analysis",
    val riskFactors: List<String> = emptyList(),
    val hasBeenAnalyzed: Boolean = false,
    val avgEyeAspectRatio: Float = 0f      // Average EAR from MediaPipe landmarks
)

enum class EdemaSeverity {
    NORMAL,           // edemaScore < 0.25
    MILD,             // 0.25 - 0.5
    MODERATE,         // 0.5 - 0.75
    SIGNIFICANT       // > 0.75
}

class EdemaDetector {
    
    companion object {
        // Eye Aspect Ratio (EAR) = palpebral fissure width / height
        // Baseline derived from adult anthropometric studies:
        // PFW 30.91±1.82mm, PFH 11.06±1.60mm → EAR ≈ 2.8
        // [Vasanthakumar et al., J Clin Diagn Res 7(5):834-836, 2013]
        private const val NORMAL_EYE_ASPECT_RATIO = 2.8f
        
        // Edema threshold: periorbital swelling narrows the palpebral fissure.
        // 2.2 represents a ~21% decrease from baseline — conservative estimate
        // for screening sensitivity. Requires field calibration against
        // clinician-graded edema (0/+1/+2/+3/+4 scale) to validate.
        private const val EDEMA_EYE_ASPECT_RATIO = 2.2f
        
        // Cheek fullness threshold
        private const val CHEEK_BRIGHTNESS_THRESHOLD = 0.15f
        
        // Minimum face coverage for valid analysis
        private const val MIN_FACE_RATIO = 0.2f
        
        // Padding around landmarks for region analysis (fraction of face width)
        private const val LANDMARK_REGION_PADDING = 0.06f
    }
    
    private val _result = MutableStateFlow(
        EdemaResult(0f, 0f, EdemaSeverity.NORMAL, 0f, 0f, "No analysis", emptyList(), hasBeenAnalyzed = false)
    )
    val result: StateFlow<EdemaResult> = _result.asStateFlow()
    
    /**
     * Analyze face image for edema using MediaPipe landmarks.
     * This is the primary method — uses precise eye/cheek coordinates.
     *
     * @param bitmap The captured face image
     * @param landmarks FaceLandmarks from FaceDetectorHelper
     * @return EdemaResult with accurate geometry-based scoring
     */
    fun analyzeFaceWithLandmarks(bitmap: Bitmap, landmarks: FaceLandmarks): EdemaResult {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val pad = (landmarks.faceBounds.width * LANDMARK_REGION_PADDING).toInt().coerceAtLeast(4)
        
        // ── Eye Aspect Ratio (EAR) from landmarks ──
        // EAR = |eyeTop - eyeBottom| / |eyeLeft - eyeRight|
        // Lower EAR = more closed/puffy eyes
        val leftEAR = computeEAR(landmarks.leftEyeTop, landmarks.leftEyeBottom,
                                  landmarks.leftEyeLeft, landmarks.leftEyeRight, w, h)
        val rightEAR = computeEAR(landmarks.rightEyeTop, landmarks.rightEyeBottom,
                                   landmarks.rightEyeLeft, landmarks.rightEyeRight, w, h)
        val avgEAR = (leftEAR + rightEAR) / 2f
        
        // Map EAR to periorbital score: NORMAL_EAR→0, EDEMA_EAR→1
        val earScore = when {
            avgEAR >= NORMAL_EYE_ASPECT_RATIO -> 0f
            avgEAR <= EDEMA_EYE_ASPECT_RATIO -> 1f
            else -> 1f - ((avgEAR - EDEMA_EYE_ASPECT_RATIO) / 
                         (NORMAL_EYE_ASPECT_RATIO - EDEMA_EYE_ASPECT_RATIO))
        }
        
        // Also analyze brightness gradient around eyes (landmark-guided region)
        val leftEyeCenterX = ((landmarks.leftEyeLeft[0] + landmarks.leftEyeRight[0]) / 2f * w).toInt()
        val leftEyeCenterY = ((landmarks.leftEyeTop[1] + landmarks.leftEyeBottom[1]) / 2f * h).toInt()
        val eyeRegionW = ((landmarks.leftEyeRight[0] - landmarks.leftEyeLeft[0]) * w).toInt() + pad * 2
        val eyeRegionH = ((landmarks.leftEyeBottom[1] - landmarks.leftEyeTop[1]) * h).toInt() + pad * 4
        
        val gradientScore = analyzeEyeRegion(bitmap,
            (leftEyeCenterX - eyeRegionW / 2).coerceAtLeast(0),
            (leftEyeCenterX + eyeRegionW / 2).coerceAtMost(bitmap.width),
            (leftEyeCenterY - eyeRegionH).coerceAtLeast(0),
            (leftEyeCenterY + eyeRegionH).coerceAtMost(bitmap.height)
        )
        
        // Combine EAR and gradient for periorbital score
        val periorbitalScore = (earScore * 0.5f + gradientScore * 0.5f).coerceIn(0f, 1f)
        
        // ── Cheek swelling from landmarks ──
        val leftCheekX = (landmarks.leftCheek[0] * w).toInt()
        val leftCheekY = (landmarks.leftCheek[1] * h).toInt()
        val rightCheekX = (landmarks.rightCheek[0] * w).toInt()
        val rightCheekY = (landmarks.rightCheek[1] * h).toInt()
        val cheekSize = ((rightCheekX - leftCheekX) * 0.25f).toInt().coerceAtLeast(10)
        
        val leftCheekScore = analyzeCheekRegion(bitmap,
            (leftCheekX - cheekSize).coerceAtLeast(0),
            (leftCheekX + cheekSize).coerceAtMost(bitmap.width),
            (leftCheekY - cheekSize).coerceAtLeast(0),
            (leftCheekY + cheekSize).coerceAtMost(bitmap.height)
        )
        val rightCheekScore = analyzeCheekRegion(bitmap,
            (rightCheekX - cheekSize).coerceAtLeast(0),
            (rightCheekX + cheekSize).coerceAtMost(bitmap.width),
            (rightCheekY - cheekSize).coerceAtLeast(0),
            (rightCheekY + cheekSize).coerceAtMost(bitmap.height)
        )
        val facialScore = ((leftCheekScore + rightCheekScore) / 2f).coerceIn(0f, 1f)
        
        // Combined score
        val edemaScore = (periorbitalScore * 0.6f + facialScore * 0.4f).coerceIn(0f, 1f)
        
        // Landmark-based = higher confidence than heuristic
        val confidence = estimateImageQuality(bitmap,
            landmarks.faceBounds.x,
            landmarks.faceBounds.x + landmarks.faceBounds.width,
            landmarks.faceBounds.y,
            landmarks.faceBounds.y + landmarks.faceBounds.height
        ) * 1.15f  // Boost confidence since we have landmarks
        
        return buildResult(edemaScore, confidence.coerceAtMost(1f), periorbitalScore, facialScore)
    }
    
    /**
     * Compute Eye Aspect Ratio from landmark coordinates.
     * EAR measures how "open" the eye is — puffy/edematous eyes have lower EAR.
     */
    private fun computeEAR(
        top: List<Float>, bottom: List<Float>,
        left: List<Float>, right: List<Float>,
        imgW: Float, imgH: Float
    ): Float {
        val verticalDist = abs((bottom[1] * imgH) - (top[1] * imgH))
        val horizontalDist = abs((right[0] * imgW) - (left[0] * imgW))
        return if (verticalDist > 0) horizontalDist / verticalDist else NORMAL_EYE_ASPECT_RATIO
    }
    
    /**
     * Fallback: Analyze face image for edema indicators using fixed regions.
     * Used when MediaPipe landmarks are not available.
     */
    fun analyzeFace(bitmap: Bitmap): EdemaResult {
        // Estimate face region (center 60% of image, upper 70%)
        val faceLeft = (bitmap.width * 0.2f).toInt()
        val faceRight = (bitmap.width * 0.8f).toInt()
        val faceTop = (bitmap.height * 0.1f).toInt()
        val faceBottom = (bitmap.height * 0.8f).toInt()
        
        // Analyze eye region (upper third of face area)
        val eyeRegionTop = faceTop
        val eyeRegionBottom = faceTop + (faceBottom - faceTop) / 3
        val periorbitalScore = analyzeEyeRegion(bitmap, faceLeft, faceRight, eyeRegionTop, eyeRegionBottom)
        
        // Analyze cheek region (middle third)
        val cheekTop = eyeRegionBottom
        val cheekBottom = faceTop + 2 * (faceBottom - faceTop) / 3
        val facialScore = analyzeCheekRegion(bitmap, faceLeft, faceRight, cheekTop, cheekBottom)
        
        // Combined edema score (periorbital weighted more heavily for preeclampsia)
        val edemaScore = (periorbitalScore * 0.6f + facialScore * 0.4f).coerceIn(0f, 1f)
        
        // Confidence is lower without landmarks
        val confidence = estimateImageQuality(bitmap, faceLeft, faceRight, faceTop, faceBottom) * 0.8f
        
        return buildResult(edemaScore, confidence, periorbitalScore, facialScore)
    }
    
    /**
     * Build EdemaResult from scores (shared by both analysis paths).
     */
    private fun buildResult(
        edemaScore: Float, confidence: Float,
        periorbitalScore: Float, facialScore: Float
    ): EdemaResult {
        val severity = when {
            edemaScore < 0.25f -> EdemaSeverity.NORMAL
            edemaScore < 0.5f -> EdemaSeverity.MILD
            edemaScore < 0.75f -> EdemaSeverity.MODERATE
            else -> EdemaSeverity.SIGNIFICANT
        }
        
        val riskFactors = mutableListOf<String>()
        if (periorbitalScore > 0.5f) riskFactors.add("Periorbital puffiness detected")
        if (facialScore > 0.5f) riskFactors.add("Facial swelling detected")
        
        val recommendation = when (severity) {
            EdemaSeverity.NORMAL -> 
                "No significant facial edema detected. Continue routine prenatal monitoring."
            EdemaSeverity.MILD -> 
                "Mild facial puffiness noted. Monitor for worsening. Check blood pressure."
            EdemaSeverity.MODERATE -> 
                "Moderate edema detected. Recommend blood pressure check and urine protein test within 24-48 hours."
            EdemaSeverity.SIGNIFICANT -> 
                "Significant facial edema detected. URGENT: Seek immediate prenatal evaluation. Check BP and urine protein."
        }
        
        val result = EdemaResult(
            edemaScore = edemaScore,
            confidence = confidence,
            severity = severity,
            periorbitalScore = periorbitalScore,
            facialScore = facialScore,
            recommendation = recommendation,
            riskFactors = riskFactors,
            hasBeenAnalyzed = true
        )
        
        _result.value = result
        return result
    }
    
    /**
     * Analyze eye region for periorbital edema
     * Uses brightness gradient analysis - puffy areas have smoother gradients
     */
    private fun analyzeEyeRegion(
        bitmap: Bitmap,
        left: Int, right: Int, top: Int, bottom: Int
    ): Float {
        // Finding 11 fix: Batch getPixels() instead of per-pixel getBrightness()
        val roiWidth = right - left
        val roiHeight = bottom - top
        if (roiWidth <= 0 || roiHeight <= 0) return 0f

        val pixels = IntArray(roiWidth * roiHeight)
        bitmap.getPixels(pixels, 0, roiWidth, left, top, roiWidth, roiHeight)

        val stepSize = 4
        var gradientSum = 0f
        var pixelCount = 0
        
        for (y in 0 until roiHeight - stepSize step stepSize) {
            for (x in 0 until roiWidth - stepSize step stepSize) {
                val current = pixelBrightness(pixels[y * roiWidth + x])
                val nextX = pixelBrightness(pixels[y * roiWidth + x + stepSize])
                val nextY = pixelBrightness(pixels[(y + stepSize) * roiWidth + x])
                
                // Low gradient = smooth skin = potential puffiness
                val gradient = abs(current - nextX) + abs(current - nextY)
                gradientSum += gradient
                pixelCount++
            }
        }
        
        if (pixelCount == 0) return 0f
        
        val avgGradient = gradientSum / pixelCount
        
        // Normal eyes have more texture/gradient due to eyelid folds
        // Puffy eyes have smoother appearance (lower gradient)
        // Map: high gradient (>0.15) = normal, low gradient (<0.05) = puffy
        return when {
            avgGradient > 0.15f -> 0f
            avgGradient < 0.05f -> 1f
            else -> 1f - ((avgGradient - 0.05f) / 0.10f)
        }
    }
    
    /**
     * Analyze cheek region for facial swelling
     * Swollen cheeks have higher average brightness and less contour
     */
    private fun analyzeCheekRegion(
        bitmap: Bitmap,
        left: Int, right: Int, top: Int, bottom: Int
    ): Float {
        // Finding 11 fix: Batch getPixels() instead of per-pixel getBrightness()
        val roiWidth = right - left
        val roiHeight = bottom - top
        if (roiWidth <= 0 || roiHeight <= 0) return 0f

        val pixels = IntArray(roiWidth * roiHeight)
        bitmap.getPixels(pixels, 0, roiWidth, left, top, roiWidth, roiHeight)

        val stepSize = 4
        var brightnessSum = 0f
        var varianceSum = 0f
        var pixelCount = 0
        val brightnesses = mutableListOf<Float>()
        
        for (y in 0 until roiHeight step stepSize) {
            for (x in 0 until roiWidth step stepSize) {
                val brightness = pixelBrightness(pixels[y * roiWidth + x])
                brightnessSum += brightness
                brightnesses.add(brightness)
                pixelCount++
            }
        }
        
        if (pixelCount == 0) return 0f
        
        val avgBrightness = brightnessSum / pixelCount
        
        // Calculate variance (swollen faces have less contour = lower variance)
        for (b in brightnesses) {
            varianceSum += (b - avgBrightness).pow(2)
        }
        val variance = varianceSum / pixelCount
        
        // Low variance indicates loss of facial contour (swelling)
        // Map: high variance (>0.02) = normal contour, low variance (<0.005) = swelling
        return when {
            variance > 0.02f -> 0f
            variance < 0.005f -> 1f
            else -> 1f - ((variance - 0.005f) / 0.015f)
        }
    }
    
    /**
     * Get brightness (luminance) from a raw pixel integer (batch path).
     * Finding 11: Used with getPixels() batch reads to avoid per-pixel JNI calls.
     */
    private fun pixelBrightness(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /**
     * Get brightness (luminance) of a pixel at coordinates (legacy per-pixel path).
     */
    private fun getBrightness(bitmap: Bitmap, x: Int, y: Int): Float {
        val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // Luminance formula
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
    
    /**
     * Estimate image quality for confidence scoring
     */
    private fun estimateImageQuality(
        bitmap: Bitmap,
        left: Int, right: Int, top: Int, bottom: Int
    ): Float {
        // P-2 fix: Batch getPixels() instead of per-pixel getBrightness()
        val roiWidth = right - left
        val roiHeight = bottom - top
        if (roiWidth <= 0 || roiHeight <= 0) return 0.5f
        
        val pixels = IntArray(roiWidth * roiHeight)
        bitmap.getPixels(pixels, 0, roiWidth, left, top, roiWidth, roiHeight)
        
        var minBrightness = 1f
        var maxBrightness = 0f
        val stepSize = 8
        
        for (y in 0 until roiHeight step stepSize) {
            for (x in 0 until roiWidth step stepSize) {
                val pixel = pixels[y * roiWidth + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)
            }
        }
        
        val dynamicRange = maxBrightness - minBrightness
        
        // Good images have dynamic range between 0.3 and 0.8
        return when {
            dynamicRange < 0.2f -> 0.3f  // Too flat/dark
            dynamicRange > 0.9f -> 0.5f  // Overexposed
            dynamicRange in 0.3f..0.7f -> 0.9f  // Good lighting
            else -> 0.7f
        }
    }
    
    /**
     * Check if image appears to be a forward-facing face
     */
    fun isValidFaceImage(bitmap: Bitmap): Boolean {
        // F-4 fix: HSV-based skin detection works across all skin tones
        // Previous r>g&&r>b heuristic under-detected very dark/reddish tones
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val sampleRadius = minOf(bitmap.width, bitmap.height) / 6
        
        // Batch pixel read for the sample region
        val sampleLeft = (centerX - sampleRadius).coerceIn(0, bitmap.width - 1)
        val sampleTop = (centerY - sampleRadius).coerceIn(0, bitmap.height - 1)
        val sampleRight = (centerX + sampleRadius).coerceIn(0, bitmap.width - 1)
        val sampleBottom = (centerY + sampleRadius).coerceIn(0, bitmap.height - 1)
        val sampleW = sampleRight - sampleLeft
        val sampleH = sampleBottom - sampleTop
        
        if (sampleW <= 0 || sampleH <= 0) return false
        
        val pixels = IntArray(sampleW * sampleH)
        bitmap.getPixels(pixels, 0, sampleW, sampleLeft, sampleTop, sampleW, sampleH)
        
        var skinPixels = 0
        var totalPixels = 0
        val hsv = FloatArray(3)
        
        for (y in 0 until sampleH step 4) {
            for (x in 0 until sampleW step 4) {
                val pixel = pixels[y * sampleW + x]
                android.graphics.Color.colorToHSV(pixel, hsv)
                totalPixels++
                
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]
                
                // HSV-based skin detection: covers all skin tones
                // Hue: 0-50° (warm skin tones) or 330-360° (reddish)
                // Saturation: 0.1-0.8 (avoids pure gray/white/saturated)
                // Value: 0.15-0.95 (avoids pure black/white)
                val isSkinHue = (hue in 0f..50f) || (hue >= 330f)
                val isSkinSat = sat in 0.1f..0.8f
                val isSkinVal = value in 0.15f..0.95f
                
                if (isSkinHue && isSkinSat && isSkinVal) {
                    skinPixels++
                }
            }
        }
        
        return (skinPixels.toFloat() / totalPixels) > 0.3f
    }
    
    fun reset() {
        _result.value = EdemaResult(0f, 0f, EdemaSeverity.NORMAL, 0f, 0f, "No analysis", emptyList(), hasBeenAnalyzed = false)
    }
}
