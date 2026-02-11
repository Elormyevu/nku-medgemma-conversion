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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nku.app.*
import com.nku.app.ui.NkuColors

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
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
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
            Column(modifier = Modifier.padding(16.dp)) {
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
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Text(strings.notYetScreened, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.captureForEdema, fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(20.dp))
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
                            // F-10: Error boundary — prevent processor crash from killing UI
                            try {
                                val landmarks = faceDetectorHelper.detectLandmarks(bmp)
                                if (landmarks != null) edemaDetector.analyzeFaceWithLandmarks(bmp, landmarks)
                                else edemaDetector.analyzeFace(bmp)
                            } catch (e: Exception) {
                                android.util.Log.e("PreeclampsiaScreen", "Edema analysis error: ${e.message}")
                            }
                            isCapturing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Success),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.analyze)
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
            Text(edemaResult.severity.name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = scoreColor)
            Text("Edema Score: ${(edemaResult.edemaScore * 100).toInt()}%", color = Color.Gray)
            Text("Periorbital: ${(edemaResult.periorbitalScore * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
            Text("Confidence: ${(edemaResult.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            
            if (edemaResult.riskFactors.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = NkuColors.ListeningIndicator.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.riskFactors, fontWeight = FontWeight.Bold, color = NkuColors.ListeningIndicator)
                        edemaResult.riskFactors.forEach { risk -> Text("• $risk", color = Color.White, fontSize = 14.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.recommendationsTitle, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(edemaResult.recommendation, color = Color.Gray, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        
        if (!isCapturing) {
            Button(
                onClick = { isCapturing = true },
                colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Secondary),
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (edemaResult.hasBeenAnalyzed) strings.recapture else strings.captureFace, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
        
        Card(colors = CardDefaults.cardColors(containerColor = NkuColors.InstructionCard), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Uses geometry-based analysis (facial proportions). Works across all skin tones. Best with photos in consistent lighting.",
                color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(12.dp)
            )
        }
    }
}
