package com.nku.app.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nku.app.*
import com.nku.app.ui.NkuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PreeclampsiaScreen — Facial edema detection for preeclampsia screening.
 * Extracted from MainActivity.kt (F-UI-3 / F-CQ-1).
 */

@Composable
fun PreeclampsiaScreen(
    edemaResult: EdemaResult,
    edemaDetector: EdemaDetector,
    faceDetectorHelper: FaceDetectorHelper,
    isPregnant: Boolean,
    gestationalWeeks: String,
    strings: LocalizedStrings.UiStrings,
    onPregnancyChange: (Boolean, String) -> Unit
) {
    var isCapturing by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }  // OBS-1: Loading spinner
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // H-01 fix: Runtime camera permission gate (mirrors CardioScreen pattern)
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
        Text(strings.preETitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(strings.preESubtitle, fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        // Pregnancy context
        Card(
            colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.pregnant, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isPregnant, onCheckedChange = { onPregnancyChange(it, gestationalWeeks) })
                }
                if (isPregnant) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gestationalWeeks,
                        onValueChange = { input ->
                            // F-12: Validate gestational weeks — digits only, range 1-45
                            val filtered = input.filter { it.isDigit() }
                            val weeks = filtered.toIntOrNull()
                            if (filtered.isEmpty() || (weeks != null && weeks in 1..45)) {
                                onPregnancyChange(isPregnant, filtered)
                            }
                        },
                        label = { Text(strings.gestationalWeeks) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        if (!edemaResult.hasBeenAnalyzed && !isCapturing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Text(strings.notYetScreened, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.captureForEdema, fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        
        // H-01 fix: Camera permission denied feedback
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
                        strings.cameraPermissionPreE,
                        fontSize = 12.sp,
                        color = Color.Gray,
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
            Spacer(Modifier.height(16.dp))
        }
        
        
        if (isCapturing) {
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(), enableAnalysis = true,
                        lensFacing = CameraSelector.LENS_FACING_BACK,
                        onFrameAnalyzed = { bitmap -> lastCapturedBitmap = bitmap }
                    )
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(strings.centerFaceKeepNeutral, fontSize = 11.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { isCapturing = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(strings.cancel)
                }
                Button(
                    onClick = {
                        lastCapturedBitmap?.let { bmp ->
                            // OBS-1: Show loading spinner during analysis
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.Default) {
                                        val landmarks = faceDetectorHelper.detectLandmarks(bmp)
                                        if (landmarks != null) edemaDetector.analyzeFaceWithLandmarks(bmp, landmarks)
                                        else edemaDetector.analyzeFace(bmp)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PreeclampsiaScreen", "Edema analysis error: ${e.message}")
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
                            color = Color.White,
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
        
        if (edemaResult.hasBeenAnalyzed) {
            val scoreColor = when (edemaResult.severity) {
                EdemaSeverity.NORMAL -> NkuColors.Success
                EdemaSeverity.MILD -> NkuColors.TriageYellow
                EdemaSeverity.MODERATE -> NkuColors.TriageOrange
                EdemaSeverity.SIGNIFICANT -> NkuColors.ListeningIndicator
            }
            Text(strings.localizedSeverity(edemaResult.severity), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = scoreColor,
                modifier = Modifier.semantics { contentDescription = "Edema severity: ${strings.localizedSeverity(edemaResult.severity)}" })
            Text("${strings.edemaScoreLabel}: ${(edemaResult.edemaScore * 100).toInt()}%", color = Color.Gray)
            Text("${strings.periorbitalLabel}: ${(edemaResult.periorbitalScore * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
            Text("${strings.confidenceLabel}: ${(edemaResult.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)

            // FT-2: Low-confidence recapture warning
            if (edemaResult.confidence < 0.75f) {
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.lowConfidenceWarning,
                    fontSize = 12.sp,
                    color = NkuColors.TriageOrange
                )
            }

            Spacer(Modifier.height(16.dp))
            
            if (edemaResult.riskFactors.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = NkuColors.ListeningIndicator.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(strings.riskFactors, fontWeight = FontWeight.Bold, color = NkuColors.ListeningIndicator)
                        edemaResult.riskFactors.forEach { risk -> Text("• $risk", color = Color.White, fontSize = 14.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Recommendation: ${edemaResult.recommendation}"
                }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(strings.recommendationsTitle, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(edemaResult.recommendation, color = Color.Gray, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            // Reset button — clears result without starting camera
            OutlinedButton(
                onClick = { edemaDetector.reset() },
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.resetReading, fontSize = 14.sp)
            }
        }
        
        if (!isCapturing) {
            Button(
                onClick = {
                    // H-01 fix: Check camera permission before starting capture
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        isCapturing = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Secondary),
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (edemaResult.hasBeenAnalyzed) strings.recapture else strings.captureFace, fontSize = 16.sp)
            }

            // OBS-3: Rear camera usage hint
            Spacer(Modifier.height(6.dp))
            Text(
                strings.rearCameraHintFace,
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
        
        Card(colors = CardDefaults.cardColors(containerColor = NkuColors.InstructionCard), modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.geometryInstructions,
                color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(12.dp)
            )
        }
    }
}
