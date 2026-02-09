package com.nku.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * FaceDetectorHelper — MediaPipe Face Detection & Landmarking
 *
 * Provides two capabilities using MediaPipe tasks-vision:
 * 1. Face Detection: bounding box for rPPG face ROI
 * 2. Face Landmarking: 478 landmarks for EdemaDetector geometry
 *
 * Both run on-device via MediaPipe's TFLite delegates.
 * Models are bundled inside the mediapipe-tasks-vision AAR.
 */

data class FaceROI(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun toIntArray() = intArrayOf(x, y, width, height)
}

data class FaceLandmarks(
    // Eye landmarks (for periorbital edema detection)
    val leftEyeTop: List<Float>,      // [x, y] normalized 0-1
    val leftEyeBottom: List<Float>,
    val leftEyeLeft: List<Float>,
    val leftEyeRight: List<Float>,
    val rightEyeTop: List<Float>,
    val rightEyeBottom: List<Float>,
    val rightEyeLeft: List<Float>,
    val rightEyeRight: List<Float>,
    // Cheek landmarks (for facial swelling detection)
    val leftCheek: List<Float>,
    val rightCheek: List<Float>,
    // Jaw landmarks (for face contour/swelling)
    val jawLeft: List<Float>,
    val jawRight: List<Float>,
    val chin: List<Float>,
    // Overall face bounding box from landmarks
    val faceBounds: FaceROI
)

class FaceDetectorHelper(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorHelper"

        // MediaPipe Face Detection model (bundled in tasks-vision AAR)
        private const val FACE_DETECTION_MODEL = "face_detection_short_range.tflite"

        // MediaPipe Face Landmarker model
        private const val FACE_LANDMARKER_MODEL = "face_landmarker.task"

        // MediaPipe landmark indices (478 landmark mesh)
        // Reference: https://github.com/google/mediapipe/blob/master/mediapipe/modules/face_geometry/data/canonical_face_model_uv_visualization.png
        private const val LEFT_EYE_TOP = 159
        private const val LEFT_EYE_BOTTOM = 145
        private const val LEFT_EYE_LEFT = 33
        private const val LEFT_EYE_RIGHT = 133

        private const val RIGHT_EYE_TOP = 386
        private const val RIGHT_EYE_BOTTOM = 374
        private const val RIGHT_EYE_LEFT = 362
        private const val RIGHT_EYE_RIGHT = 263

        private const val LEFT_CHEEK = 234
        private const val RIGHT_CHEEK = 454

        private const val JAW_LEFT = 172
        private const val JAW_RIGHT = 397
        private const val CHIN = 152
    }

    private var faceDetector: FaceDetector? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var detectorInitialized = false
    private var landmarkerInitialized = false

    /**
     * Initialize the face detector for bounding box detection.
     * Used by RPPGProcessor for face ROI.
     */
    fun initializeDetector() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(FACE_DETECTION_MODEL)
                .build()

            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, options)
            detectorInitialized = true
            Log.i(TAG, "Face detector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face detector: ${e.message}", e)
            detectorInitialized = false
        }
    }

    /**
     * Initialize the face landmarker for 478-point mesh.
     * Used by EdemaDetector for precise region analysis.
     */
    fun initializeLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(FACE_LANDMARKER_MODEL)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumFaces(1)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            landmarkerInitialized = true
            Log.i(TAG, "Face landmarker initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face landmarker: ${e.message}", e)
            landmarkerInitialized = false
        }
    }

    /**
     * Detect face bounding box in a bitmap.
     * Returns FaceROI or null if no face detected.
     */
    fun detectFace(bitmap: Bitmap): FaceROI? {
        if (!detectorInitialized) {
            initializeDetector()
            if (!detectorInitialized) return null
        }

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceDetectorResult = faceDetector!!.detect(mpImage)

            if (result.detections().isNotEmpty()) {
                val detection = result.detections()[0]
                val bbox = detection.boundingBox()
                FaceROI(
                    x = bbox.left.toInt().coerceAtLeast(0),
                    y = bbox.top.toInt().coerceAtLeast(0),
                    width = bbox.width().toInt().coerceAtMost(bitmap.width),
                    height = bbox.height().toInt().coerceAtMost(bitmap.height)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face detection error: ${e.message}")
            null
        }
    }

    /**
     * Detect face landmarks (478-point mesh) in a bitmap.
     * Returns FaceLandmarks or null if no face detected.
     */
    fun detectLandmarks(bitmap: Bitmap): FaceLandmarks? {
        if (!landmarkerInitialized) {
            initializeLandmarker()
            if (!landmarkerInitialized) return null
        }

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceLandmarkerResult = faceLandmarker!!.detect(mpImage)

            if (result.faceLandmarks().isNotEmpty()) {
                val landmarks = result.faceLandmarks()[0]

                // Extract key landmarks as [x, y] normalized coordinates
                fun landmarkXY(index: Int): List<Float> {
                    val lm = landmarks[index]
                    return listOf(lm.x(), lm.y())
                }

                // Calculate face bounding box from all landmarks
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE
                for (lm in landmarks) {
                    minX = minOf(minX, lm.x())
                    minY = minOf(minY, lm.y())
                    maxX = maxOf(maxX, lm.x())
                    maxY = maxOf(maxY, lm.y())
                }

                val faceBounds = FaceROI(
                    x = (minX * bitmap.width).toInt().coerceAtLeast(0),
                    y = (minY * bitmap.height).toInt().coerceAtLeast(0),
                    width = ((maxX - minX) * bitmap.width).toInt().coerceAtMost(bitmap.width),
                    height = ((maxY - minY) * bitmap.height).toInt().coerceAtMost(bitmap.height)
                )

                FaceLandmarks(
                    leftEyeTop = landmarkXY(LEFT_EYE_TOP),
                    leftEyeBottom = landmarkXY(LEFT_EYE_BOTTOM),
                    leftEyeLeft = landmarkXY(LEFT_EYE_LEFT),
                    leftEyeRight = landmarkXY(LEFT_EYE_RIGHT),
                    rightEyeTop = landmarkXY(RIGHT_EYE_TOP),
                    rightEyeBottom = landmarkXY(RIGHT_EYE_BOTTOM),
                    rightEyeLeft = landmarkXY(RIGHT_EYE_LEFT),
                    rightEyeRight = landmarkXY(RIGHT_EYE_RIGHT),
                    leftCheek = landmarkXY(LEFT_CHEEK),
                    rightCheek = landmarkXY(RIGHT_CHEEK),
                    jawLeft = landmarkXY(JAW_LEFT),
                    jawRight = landmarkXY(JAW_RIGHT),
                    chin = landmarkXY(CHIN),
                    faceBounds = faceBounds
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face landmark detection error: ${e.message}")
            null
        }
    }

    /**
     * Release all resources.
     */
    fun close() {
        faceDetector?.close()
        faceLandmarker?.close()
        faceDetector = null
        faceLandmarker = null
        detectorInitialized = false
        landmarkerInitialized = false
    }
}
