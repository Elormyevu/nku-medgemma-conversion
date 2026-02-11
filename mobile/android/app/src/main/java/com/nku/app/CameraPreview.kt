package com.nku.app

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * CameraPreview — Reusable Camera Composable
 *
 * Provides a camera preview with optional frame analysis and torch control.
 * Supports lens fallback (preferred → opposite) for device compatibility.
 *
 * Extracted from MainActivity.kt (L-02 audit fix) for cleaner separation.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null,
    onImageCaptured: ((Bitmap) -> Unit)? = null,
    enableAnalysis: Boolean = false,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    enableTorch: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Finding 10 fix: Use DisposableEffect to properly shut down executor on disposal
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    
                    // Build selectors: preferred lens first, then fallback
                    val preferredFacing = lensFacing
                    val fallbackFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    
                    val preferredSelector = try {
                        CameraSelector.Builder()
                            .requireLensFacing(preferredFacing)
                            .build()
                    } catch (e: Exception) { null }
                    
                    val fallbackSelector = try {
                        CameraSelector.Builder()
                            .requireLensFacing(fallbackFacing)
                            .build()
                    } catch (e: Exception) { null }
                    
                    cameraProvider.unbindAll()
                    
                    val imageAnalysis = if (enableAnalysis && onFrameAnalyzed != null) {
                        ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val bitmap = imageProxy.toBitmap()
                                    onFrameAnalyzed(bitmap)
                                    imageProxy.close()
                                }
                            }
                    } else null
                    
                    // Try preferred lens first, then fallback
                    var bound = false
                    for (selector in listOfNotNull(preferredSelector, fallbackSelector)) {
                        try {
                            val camera = if (imageAnalysis != null) {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, selector, preview, imageAnalysis
                                )
                            } else {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, selector, preview
                                )
                            }
                            val facing = if (selector == preferredSelector) "PREFERRED" else "FALLBACK"
                            Log.i("NkuCamera", "Camera bound ($facing) with lens ${if (selector == preferredSelector) preferredFacing else fallbackFacing}")
                            
                            // Enable torch/flashlight if requested
                            if (enableTorch && camera.cameraInfo.hasFlashUnit()) {
                                camera.cameraControl.enableTorch(true)
                                Log.i("NkuCamera", "Torch enabled")
                            }
                            
                            bound = true
                            break
                        } catch (e: Exception) {
                            Log.w("NkuCamera", "Failed with selector, trying next: ${e.message}")
                            cameraProvider.unbindAll()
                        }
                    }
                    if (!bound) {
                        Log.e("NkuCamera", "No camera available on this device")
                    }
                } catch (e: Exception) {
                    Log.e("NkuCamera", "Camera init failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
