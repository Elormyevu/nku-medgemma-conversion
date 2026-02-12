package com.nku.app.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nku.app.*
import com.nku.app.ui.NkuColors
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * CardioScreen — rPPG heart rate measurement via camera (finger-on-lens PPG).
 * Extracted from MainActivity.kt (F-UI-3 / F-CQ-1).
 */

@Composable
fun CardioScreen(
    rppgResult: RPPGResult,
    rppgProcessor: RPPGProcessor,
    canRun: Boolean,
    faceDetectorHelper: FaceDetectorHelper,
    strings: LocalizedStrings.UiStrings
) {
    var isMeasuring by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            rppgProcessor.reset()
            isMeasuring = true
            cameraPermissionDenied = false
        } else {
            cameraPermissionDenied = true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strings.cardioTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(strings.cardioSubtitle, fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        if (!canRun) {
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.ListeningIndicator.copy(alpha = 0.2f))) {
                Text(
                    strings.deviceCooling,
                    modifier = Modifier.padding(16.dp),
                    color = NkuColors.ListeningIndicator
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Camera permission denied feedback
        if (cameraPermissionDenied) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NkuColors.Warning.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.cameraPermissionTitle,
                        fontWeight = FontWeight.Bold,
                        color = NkuColors.Warning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strings.cameraPermissionCardio,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    ) {
                        Text(strings.openSettings, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        
        // Camera viewfinder
        if (isMeasuring) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        enableAnalysis = true,
                        lensFacing = CameraSelector.LENS_FACING_BACK,
                        enableTorch = true,
                        onFrameAnalyzed = { bitmap ->
                            // F-10: Error boundary — prevent processor crash from killing UI
                            try {
                                rppgProcessor.processFrame(bitmap)
                            } catch (e: Exception) {
                                android.util.Log.e("CardioScreen", "Frame processing error: ${e.message}")
                            }
                        }
                    )
                    
                    // Buffer fill indicator overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${strings.bufferLabel}: ${rppgResult.bufferFillPercent.toInt()}%",
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Heart rate display
        Box(contentAlignment = Alignment.Center) {
            val hasResult = rppgResult.bpm != null && rppgResult.confidence > 0.4f
            
            Canvas(modifier = Modifier.size(180.dp)) {
                // Background ring
                drawArc(
                    color = NkuColors.CardBackground,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                )
                
                if (isMeasuring) {
                    drawArc(
                        color = if (hasResult) NkuColors.Success else NkuColors.Secondary,
                        startAngle = -90f,
                        sweepAngle = 360f * (rppgResult.bufferFillPercent / 100f),
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics {
                    contentDescription = if (hasResult) "Heart rate: ${rppgResult.bpm!!.toInt()} beats per minute" else "Heart rate: not yet measured"
                }) {
                Text(
                    if (hasResult) "${rppgResult.bpm!!.toInt()}" else "—",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasResult) NkuColors.Success else Color.White
                )
                Text(strings.bpm, fontSize = 16.sp, color = Color.Gray)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (isMeasuring) {
            Text("${strings.signalLabel}: ${strings.localizedSignalQuality(rppgResult.signalQuality)}", color = Color.Gray, fontSize = 13.sp)
            Text("${strings.confidenceLabel}: ${(rppgResult.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 13.sp)
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Start / Stop button
        Button(
            onClick = {
                if (isMeasuring) {
                    isMeasuring = false
                } else {
                    // Check camera permission before starting
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        rppgProcessor.reset()
                        isMeasuring = true
                        cameraPermissionDenied = false
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMeasuring) NkuColors.ListeningIndicator else NkuColors.Success
            ),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                if (isMeasuring) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isMeasuring) strings.stopMeasurement else strings.startMeasurement, fontSize = 16.sp)
        }

        // OBS-3: Rear camera usage hint
        Spacer(Modifier.height(6.dp))
        Text(
            strings.rearCameraHintCardio,
            fontSize = 11.sp,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Instructions
        Card(
            colors = CardDefaults.cardColors(containerColor = NkuColors.InstructionCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.howItWorks, fontWeight = FontWeight.Bold, color = NkuColors.Secondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.cardioInstructions,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
