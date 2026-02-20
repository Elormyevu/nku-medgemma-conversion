package com.nku.app.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import java.util.Locale

/**
 * TriageScreen — Clinical reasoning with symptom input and AI assessment.
 * Extracted from MainActivity.kt (F-UI-3 / F-CQ-1).
 */

@Composable
fun TriageScreen(
    vitalSigns: VitalSigns,
    rppgResult: RPPGResult,
    pallorResult: PallorResult,
    jaundiceResult: JaundiceResult,
    edemaResult: EdemaResult,
    respiratoryResult: RespiratoryResult,
    assessment: ClinicalAssessment?,
    sensorFusion: SensorFusion,
    nkuTTS: NkuTTS,
    ttsState: TTSState,
    engineState: EngineState = EngineState.IDLE,
    engineProgress: String = "",
    selectedLanguage: String = "en",  // F-11 fix: pass actual language for TTS
    strings: LocalizedStrings.UiStrings = LocalizedStrings.UiStrings(),
    onRunTriage: () -> Unit
) {
    val context = LocalContext.current
    val symptoms by sensorFusion.symptoms.collectAsState()
    val hasAnyData = (rppgResult.bpm != null && rppgResult.confidence > 0.4f) ||
                     pallorResult.hasBeenAnalyzed ||
                     jaundiceResult.hasBeenAnalyzed ||
                     edemaResult.hasBeenAnalyzed ||
                     respiratoryResult.confidence > 0.4f ||
                     symptoms.isNotEmpty()  // F-10: Allow symptoms-only triage
    var symptomText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var micPermissionDenied by remember { mutableStateOf(false) }
    
    // S-3 Fix: Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                sensorFusion.addSymptom(spokenText.trim())
            }
        }
    }
    
    // S-3 Fix: Runtime permission request for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted — launch speech recognizer
            isListening = true
            micPermissionDenied = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, strings.micOrType)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechLauncher.launch(intent)
        } else {
            micPermissionDenied = true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strings.triageTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary)
        Text(strings.triageSubtitle, fontSize = 14.sp, color = NkuColors.TextSecondary)
        
        Spacer(Modifier.height(20.dp))

        // Fix 7: Show in-app warning when selected language is not ML Kit translatable
        val mlKitLanguages = setOf("en", "fr", "pt", "af", "sw", "ar")
        if (selectedLanguage !in mlKitLanguages) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NkuColors.Warning.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = null,
                        tint = NkuColors.Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Translation not available for this language — triage will use English.",
                        color = NkuColors.Warning,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        
        // Data completeness
        Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(strings.screeningData, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                DataCheckRow(strings.heartRate, rppgResult.bpm != null && rppgResult.confidence > 0.4f, rppgResult.bpm?.let { "${it.toInt()} ${strings.bpm}" }, strings.notDone)
                DataCheckRow(strings.anemiaScreen, pallorResult.hasBeenAnalyzed, if (pallorResult.hasBeenAnalyzed) strings.localizedSeverity(pallorResult.severity) else null, strings.notDone)
                DataCheckRow(strings.jaundiceScreen, jaundiceResult.hasBeenAnalyzed, if (jaundiceResult.hasBeenAnalyzed) strings.localizedSeverity(jaundiceResult.severity) else null, strings.notDone)
                DataCheckRow(strings.swellingCheck, edemaResult.hasBeenAnalyzed, if (edemaResult.hasBeenAnalyzed) strings.localizedSeverity(edemaResult.severity) else null, strings.notDone)
                DataCheckRow(strings.respiratoryScreen, respiratoryResult.confidence > 0.4f, if (respiratoryResult.confidence > 0.4f) strings.localizedRespiratoryClassification(respiratoryResult.classification) else null, strings.notDone)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Symptom Input
        Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(strings.patientSymptoms, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary, fontSize = 14.sp)
                Text(strings.micOrType, fontSize = 12.sp, color = NkuColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val hasAudioPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasAudioPermission) {
                                isListening = true
                                micPermissionDenied = false
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, strings.micOrType)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                }
                                speechLauncher.launch(intent)
                            } else {
                                // Request permission — launches system dialog
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (isListening) NkuColors.ListeningIndicator.copy(alpha = 0.3f) else NkuColors.InactiveElement)
                    ) {
                        Icon(
                            if (isListening) Icons.Default.Hearing else Icons.Default.Mic,
                            contentDescription = strings.voiceInput,
                            tint = if (isListening) NkuColors.ListeningIndicator else NkuColors.AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = symptomText, onValueChange = { symptomText = it },
                        placeholder = { Text(strings.symptomPlaceholder, color = Color(0xFF555555)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NkuColors.Success, unfocusedBorderColor = NkuColors.InactiveElement,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (symptomText.isNotBlank()) { sensorFusion.addSymptom(symptomText.trim()); symptomText = "" }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = strings.addSymptom, tint = NkuColors.Success, modifier = Modifier.size(28.dp))
                    }
                }
                if (isListening) {
                    Spacer(Modifier.height(6.dp))
                    Text(strings.listeningPrompt, fontSize = 12.sp, color = NkuColors.ListeningIndicator)
                }
                if (micPermissionDenied) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.micPermissionRequired,
                        fontSize = 12.sp, color = NkuColors.Warning
                    )
                }
                if (symptoms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    symptoms.forEach { symptom ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("• ${symptom.symptom}", color = NkuColors.MutedBlue, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { sensorFusion.removeSymptom(symptom.symptom) }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = strings.removeLabel, tint = NkuColors.InactiveText, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (!hasAnyData) {
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.Warning.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NkuColors.Warning, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(strings.noDataWarning, color = NkuColors.Warning, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        
        Button(
            onClick = onRunTriage,
            enabled = hasAnyData && engineState == EngineState.IDLE,
            colors = ButtonDefaults.buttonColors(containerColor = NkuColors.Primary, disabledContainerColor = Color(0xFF333344)),
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.runTriage, fontSize = 16.sp)
        }
        
        // Loading overlay
        if (engineState != EngineState.IDLE && engineState != EngineState.COMPLETE) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.SurfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NkuColors.Primary, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when (engineState) {
                            EngineState.LOADING_MODEL -> strings.loadingAiModel
                            EngineState.TRANSLATING_TO_ENGLISH -> strings.translatingToEnglish
                            EngineState.RUNNING_MEDGEMMA -> strings.medgemmaAnalyzing
                            EngineState.TRANSLATING_TO_LOCAL -> strings.translatingResult
                            EngineState.ERROR -> strings.errorOccurred
                            else -> strings.processing
                        },
                        fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary, fontSize = 15.sp
                    )
                    if (engineProgress.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(engineProgress, color = NkuColors.OnSurfaceMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Assessment results
        assessment?.let { result ->
            val categoryColor = when (result.triageCategory) {
                TriageCategory.GREEN -> NkuColors.TriageGreen
                TriageCategory.YELLOW -> NkuColors.TriageYellow
                TriageCategory.ORANGE -> NkuColors.TriageOrange
                TriageCategory.RED -> NkuColors.TriageRed
            }
            Card(colors = CardDefaults.cardColors(containerColor = categoryColor),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Triage category: ${strings.localizedTriageCategory(result.triageCategory)}, Severity: ${strings.localizedSeverity(result.overallSeverity)}, Urgency: ${strings.localizedUrgency(result.urgency)}"
                }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.localizedTriageCategory(result.triageCategory), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary)
                    Text("${strings.severityLabel}: ${strings.localizedSeverity(result.overallSeverity)}", color = NkuColors.TextPrimary.copy(alpha = 0.8f))
                    Text("${strings.urgencyLabel}: ${strings.localizedUrgency(result.urgency)}", color = NkuColors.TextPrimary.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(12.dp))

            // FT-1: Fallback transparency banner — tell the CHW which engine produced the result
            if (result.triageSource != TriageSource.MEDGEMMA) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = NkuColors.AccentCyan.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = NkuColors.AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                strings.triageSourceGuideline,
                                fontWeight = FontWeight.Bold,
                                color = NkuColors.AccentCyan,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.fallbackExplanation,
                                color = NkuColors.TextPrimary.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.fallbackRecoveryTip,
                                color = NkuColors.TextPrimary.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            
            // Listen button
            val speakableText = buildString {
                append("${strings.severityLabel}: ${strings.localizedSeverity(result.overallSeverity)}. ")
                append("${strings.urgencyLabel}: ${strings.localizedUrgency(result.urgency)}. ")
                if (result.primaryConcerns.isNotEmpty()) { append("${strings.ttsConcerns}: "); result.primaryConcerns.forEach { append("$it. ") } }
                if (result.recommendations.isNotEmpty()) { append("${strings.ttsRecommendations}: "); result.recommendations.forEach { append("$it. ") } }
            }
            Button(
                onClick = { if (ttsState == TTSState.SPEAKING) nkuTTS.stop() else nkuTTS.speak(speakableText, selectedLanguage) },
                colors = ButtonDefaults.buttonColors(containerColor = if (ttsState == TTSState.SPEAKING) NkuColors.ListeningIndicator else NkuColors.AccentCyan),
                modifier = Modifier.fillMaxWidth(0.7f).height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(if (ttsState == TTSState.SPEAKING) Icons.Default.Stop else Icons.Default.VolumeUp, contentDescription = if (ttsState == TTSState.SPEAKING) strings.stopLabel else strings.listenLabel, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (ttsState == TTSState.SPEAKING) strings.stopLabel else strings.listenLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Primary concerns: ${result.primaryConcerns.joinToString(", ")}"
                }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(strings.primaryConcerns, fontWeight = FontWeight.Bold, color = NkuColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    result.primaryConcerns.forEach { concern -> Text("• $concern", color = NkuColors.TextSecondary, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Recommendations: ${result.recommendations.joinToString(", ")}"
                }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(strings.recommendationsTitle, fontWeight = FontWeight.Bold, color = NkuColors.Success)
                    Spacer(Modifier.height(8.dp))
                    result.recommendations.forEach { rec -> Text("• $rec", color = NkuColors.TextSecondary, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.disclaimer, fontSize = 11.sp, color = NkuColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun DataCheckRow(label: String, isComplete: Boolean, detail: String?, notDoneText: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isComplete) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isComplete) NkuColors.Success else NkuColors.InactiveText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = NkuColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(detail ?: notDoneText, color = if (isComplete) NkuColors.Success else NkuColors.InactiveText, fontSize = 12.sp)
    }
}
