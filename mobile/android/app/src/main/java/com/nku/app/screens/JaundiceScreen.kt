package com.nku.app.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nku.app.*
import com.nku.app.ui.NkuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * JaundiceScreen — Scleral icterus detection for jaundice screening.
 * Mirrors AnemiaScreen.kt architecture (F-UI-3 / F-CQ-1).
 */

@Composable
fun JaundiceScreen(
    jaundiceResult: JaundiceResult,
    jaundiceDetector: JaundiceDetector,
    strings: LocalizedStrings.UiStrings
) {
    var isCapturing by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isCapturing = true
            cameraPermissionDenied = false
        } else {
            cameraPermissionDenied = true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strings.jaundiceTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary)
        Text(strings.jaundiceSubtitle, fontSize = 14.sp, color = NkuColors.TextSecondary)
        
        Spacer(Modifier.height(12.dp))

        // Camera permission denied feedback
        if (cameraPermissionDenied) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NkuColors.Warning.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.cameraPermissionTitle,
                        fontWeight = FontWeight.Bold,
                        color = NkuColors.Warning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strings.cameraPermissionJaundice,
                        fontSize = 12.sp,
                        color = NkuColors.TextSecondary,
                        textAlign = TextAlign.Center
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
            Spacer(Modifier.height(8.dp))
        }
        
        if (!jaundiceResult.hasBeenAnalyzed && !isCapturing) {
            // ---- NOT YET SCREENED STATE ----
            Card(
                colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = NkuColors.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings.notYetScreened,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NkuColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strings.pointAtSclera,
                        fontSize = 13.sp,
                        color = NkuColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
        }
        
        // Camera viewfinder for capture
        if (isCapturing) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        enableAnalysis = true,
                        lensFacing = CameraSelector.LENS_FACING_BACK,
                        onFrameAnalyzed = { bitmap ->
                            lastCapturedBitmap = bitmap
                        }
                    )
                    
                    // Capture overlay guide
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            strings.pointAtSclera,
                            fontSize = 11.sp,
                            color = NkuColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Analyze button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isCapturing = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(strings.cancel)
                }
                
                Button(
                    onClick = {
                        lastCapturedBitmap?.let { bmp ->
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.Default) {
                                        jaundiceDetector.analyzeSclera(bmp)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("JaundiceScreen", "Jaundice analysis error: ${e.message}")
                                } finally {
                                    isAnalyzing = false
                                    isCapturing = false
                                }
                            }
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Success),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NkuColors.TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.analyzing)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.analyze)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Results (only show after analysis)
        if (jaundiceResult.hasBeenAnalyzed) {
            val scoreColor = when (jaundiceResult.severity) {
                JaundiceSeverity.NORMAL -> NkuColors.Success
                JaundiceSeverity.MILD -> NkuColors.TriageYellow
                JaundiceSeverity.MODERATE -> NkuColors.TriageOrange
                JaundiceSeverity.SEVERE -> NkuColors.ListeningIndicator
            }
            
            Text(
                strings.localizedSeverity(jaundiceResult.severity),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor,
                modifier = Modifier.semantics {
                    contentDescription = "Jaundice severity: ${strings.localizedSeverity(jaundiceResult.severity)}"
                }
            )
            
            Text(
                "${strings.jaundiceScoreLabel}: ${(jaundiceResult.jaundiceScore * 100).toInt()}%",
                fontSize = 16.sp,
                color = NkuColors.TextSecondary
            )
            
            Text(
                "${strings.confidenceLabel}: ${(jaundiceResult.confidence * 100).toInt()}%",
                fontSize = 13.sp,
                color = NkuColors.TextSecondary
            )

            // FT-2: Low-confidence recapture warning
            if (jaundiceResult.confidence < 0.75f) {
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.lowConfidenceWarning,
                    fontSize = 12.sp,
                    color = NkuColors.TriageOrange
                )
            }
            
            // ── Completion confirmation banner ──
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NkuColors.Success.copy(alpha = 0.15f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, NkuColors.Success.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NkuColors.Success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.dataSavedForTriage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NkuColors.Success
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            
            // Recommendation
            Card(
                colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Recommendation: ${jaundiceResult.recommendation}"
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(strings.recommendationsTitle, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(jaundiceResult.recommendation, color = NkuColors.TextSecondary, fontSize = 14.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Reset button
            OutlinedButton(
                onClick = { jaundiceDetector.reset() },
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.resetReading, fontSize = 14.sp)
            }
        }
        
        // Capture / Re-capture button (when not in capture mode)
        if (!isCapturing) {
            Button(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        isCapturing = true
                        cameraPermissionDenied = false
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Secondary),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (jaundiceResult.hasBeenAnalyzed) strings.recapture else strings.captureSclera,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                strings.rearCameraHintJaundice,
                fontSize = 11.sp,
                color = NkuColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(20.dp))
        }
        
        // How to capture guide
        Card(
            colors = CardDefaults.cardColors(containerColor = NkuColors.InstructionCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(strings.howToCapture, fontWeight = FontWeight.Bold, color = NkuColors.Secondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.jaundiceInstructions,
                    color = NkuColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            strings.worksAllSkinTones,
            textAlign = TextAlign.Center,
            color = NkuColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}
