package com.nku.app

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Remote Photoplethysmography (rPPG) Processor
 * Extracts heart rate from camera video frames via green channel analysis.
 * Part of "Sensorless Sentinel" - camera-based vitals without specialized hardware.
 */
data class RPPGResult(
    val bpm: Float?,
    val confidence: Float,
    val signalQuality: String,  // "insufficient", "poor", "good", "excellent"
    val bufferFillPercent: Float
)

class RPPGProcessor(
    private val fps: Float = 30.0f,
    private val bufferSeconds: Float = 10.0f
) {
    companion object {
        const val MIN_BPM = 40.0f
        const val MAX_BPM = 200.0f
    }
    
    private val bufferSize = (fps * bufferSeconds).toInt()
    private val minAnalysisFrames = (fps * 5).toInt()  // Need 5 seconds minimum
    
    // F-1 fix: ArrayDeque for O(1) removeFirst() instead of O(n) removeAt(0)
    private val signalBuffer = ArrayDeque<Float>(bufferSize)
    
    // F-2/P-1 fix: Throttle DFT to every 5th frame (80% CPU reduction)
    private var frameCounter = 0
    private var cachedBpmResult: Pair<Float, Float>? = null  // (bpm, confidence)
    
    private val _result = MutableStateFlow(RPPGResult(null, 0f, "insufficient", 0f))
    val result: StateFlow<RPPGResult> = _result.asStateFlow()
    
    /**
     * Process a camera frame and update heart rate estimate.
     * 
     * @param bitmap Camera frame (ideally face region cropped)
     * @param faceROI Optional face bounding box [x, y, width, height]
     * @return RPPGResult with current BPM estimate and confidence
     */
    fun processFrame(bitmap: Bitmap?, faceROI: IntArray? = null): RPPGResult {
        if (bitmap == null) {
            val result = RPPGResult(null, 0f, "insufficient", 0f)
            _result.value = result
            return result
        }
        
        // Extract green channel mean from the frame (or face ROI)
        val greenMean = extractGreenChannelMean(bitmap, faceROI)
        signalBuffer.addLast(greenMean)
        
        // F-1 fix: O(1) removeFirst() instead of O(n) removeAt(0)
        while (signalBuffer.size > bufferSize) {
            signalBuffer.removeFirst()
        }
        
        frameCounter++
        
        val fillPercent = 100f * signalBuffer.size / bufferSize
        
        // Need minimum frames for analysis
        if (signalBuffer.size < minAnalysisFrames) {
            val result = RPPGResult(null, 0f, "insufficient", fillPercent)
            _result.value = result
            return result
        }
        
        // F-2/P-1 fix: Only run DFT every 5th frame (BPM changes slowly)
        val (bpm, confidence) = if (frameCounter % 5 == 0 || cachedBpmResult == null) {
            estimateBPM().also { cachedBpmResult = it }
        } else {
            cachedBpmResult!!
        }
        
        val quality = when {
            confidence >= 0.8f -> "excellent"
            confidence >= 0.6f -> "good"
            confidence >= 0.4f -> "poor"
            else -> "insufficient"
        }
        
        val result = RPPGResult(bpm, confidence, quality, fillPercent)
        _result.value = result
        return result
    }
    
    /**
     * Extract average green channel intensity from bitmap.
     * Green channel shows strongest plethysmographic signal.
     */
    private fun extractGreenChannelMean(bitmap: Bitmap, faceROI: IntArray?): Float {
        val startX = faceROI?.getOrNull(0) ?: 0
        val startY = faceROI?.getOrNull(1) ?: 0
        val roiWidth = faceROI?.getOrNull(2) ?: bitmap.width
        val roiHeight = faceROI?.getOrNull(3) ?: bitmap.height
        
        val endX = minOf(startX + roiWidth, bitmap.width)
        val endY = minOf(startY + roiHeight, bitmap.height)
        val actualWidth = endX - startX
        val actualHeight = endY - startY
        
        if (actualWidth <= 0 || actualHeight <= 0) return 0f
        
        // F-PF-1: Batch pixel copy — orders of magnitude faster than per-pixel getPixel()
        val pixels = IntArray(actualWidth * actualHeight)
        bitmap.getPixels(pixels, 0, actualWidth, startX, startY, actualWidth, actualHeight)
        
        var greenSum = 0L
        var pixelCount = 0
        
        // Sample every 4th pixel for performance (stride across the batch array)
        for (y in 0 until actualHeight step 4) {
            val rowOffset = y * actualWidth
            for (x in 0 until actualWidth step 4) {
                val pixel = pixels[rowOffset + x]
                val green = (pixel shr 8) and 0xFF
                greenSum += green
                pixelCount++
            }
        }
        
        return if (pixelCount > 0) greenSum.toFloat() / pixelCount else 0f
    }
    
    /**
     * Estimate BPM using simplified frequency analysis.
     * Finds dominant frequency in the 40-200 BPM (0.67-3.33 Hz) range.
     */
    private fun estimateBPM(): Pair<Float, Float> {
        val data = signalBuffer.toList().toFloatArray()
        
        // Detrend (remove DC component and linear trend)
        val mean = data.average().toFloat()
        val detrended = data.map { it - mean }.toFloatArray()
        
        // Apply Hamming window
        val windowed = detrended.mapIndexed { i, v ->
            v * (0.54f - 0.46f * cos(2 * PI * i / (data.size - 1)).toFloat())
        }.toFloatArray()
        
        // Simple DFT for frequency detection (optimized for heart rate range)
        val minFreq = MIN_BPM / 60f  // Hz
        val maxFreq = MAX_BPM / 60f  // Hz
        val freqStep = 0.05f  // Hz resolution
        
        var maxMagnitude = 0f
        var peakFreq = 0f
        val magnitudes = mutableListOf<Float>()
        
        var freq = minFreq
        while (freq <= maxFreq) {
            var real = 0f
            var imag = 0f
            
            for (i in windowed.indices) {
                val angle = 2 * PI.toFloat() * freq * i / fps
                real += windowed[i] * cos(angle)
                imag += windowed[i] * kotlin.math.sin(angle)
            }
            
            val magnitude = sqrt(real.pow(2) + imag.pow(2))
            magnitudes.add(magnitude)
            
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                peakFreq = freq
            }
            
            freq += freqStep
        }
        
        // Calculate confidence based on peak prominence
        val avgMagnitude = magnitudes.average().toFloat()
        val confidence = if (avgMagnitude > 0) {
            ((maxMagnitude / avgMagnitude - 1f) / 4f).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val bpm = (peakFreq * 60f).coerceIn(MIN_BPM, MAX_BPM)
        return Pair(bpm, confidence)
    }
    
    /**
     * Reset the processor state.
     */
    fun reset() {
        signalBuffer.clear()
        frameCounter = 0
        cachedBpmResult = null
        _result.value = RPPGResult(null, 0f, "insufficient", 0f)
    }
}
