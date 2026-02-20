package com.nku.app.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nku.app.*
import com.nku.app.ui.NkuColors
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * HomeScreen — Dashboard overview with screening progress and language selector.
 * Extracted from MainActivity.kt (F-UI-3 / F-CQ-1).
 */

@Composable
fun HomeScreen(
    rppgResult: RPPGResult,
    pallorResult: PallorResult,
    jaundiceResult: JaundiceResult,
    edemaResult: EdemaResult,
    respiratoryResult: RespiratoryResult,
    strings: LocalizedStrings.UiStrings,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit = {}
) {
    // Progress tracking
    val hasHR = rppgResult.bpm != null && rppgResult.confidence > 0.4f
    val hasAnemia = pallorResult.hasBeenAnalyzed
    val hasJaundice = jaundiceResult.hasBeenAnalyzed
    val hasPreE = edemaResult.hasBeenAnalyzed
    val hasRespiratory = respiratoryResult.confidence > 0.4f
    val completedCount = listOf(hasHR, hasAnemia, hasJaundice, hasPreE, hasRespiratory).count { it }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            strings.appTitle,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            strings.appSubtitle,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // ── Language Selector ──
        var languageExpanded by remember { mutableStateOf(false) }
        var showCloudWarningForLanguage by remember { mutableStateOf<String?>(null) }
        Card(
            colors = CardDefaults.cardColors(containerColor = NkuColors.CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = NkuColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.language, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { languageExpanded = true }) {
                        Text(
                            LocalizedStrings.getLanguageName(selectedLanguage),
                            color = NkuColors.Primary,
                            fontSize = 13.sp
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = NkuColors.Primary
                        )
                    }
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        LocalizedStrings.supportedLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    if (NkuTranslator.requiresCloud(code)) {
                                        showCloudWarningForLanguage = code
                                    } else {
                                        onLanguageChange(code)
                                    }
                                    languageExpanded = false
                                },
                                leadingIcon = {
                                    if (code == selectedLanguage) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = NkuColors.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        

        showCloudWarningForLanguage?.let { code ->
            AlertDialog(
                onDismissRequest = { showCloudWarningForLanguage = null },
                title = { Text(strings.internetRequiredTitle, fontWeight = FontWeight.Bold) },
                text = { 
                    Column {
                        Text(strings.internetRequiredMessage)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onLanguageChange(code)
                            showCloudWarningForLanguage = null
                        }
                    ) {
                        Text(strings.continueLabel, color = NkuColors.Primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloudWarningForLanguage = null }) {
                        Text(strings.cancel, color = Color.Gray)
                    }
                },
                containerColor = NkuColors.CardBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                textContentColor = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(12.dp))
        
        // ── Progress Indicator ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = NkuColors.ProgressBackground.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    strings.screeningsProgress.format(completedCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completedCount == 5) NkuColors.Success else NkuColors.MutedBlue
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = completedCount / 5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (completedCount) {
                        5 -> NkuColors.Success
                        0 -> NkuColors.InactiveText
                        else -> NkuColors.Secondary
                    },
                    trackColor = NkuColors.CardBackground
                )
                if (completedCount == 5) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.readyForTriage,
                        fontSize = 12.sp,
                        color = NkuColors.Success
                    )
                } else if (completedCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.followSteps,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }


        
        Spacer(Modifier.height(16.dp))
        
        // ── Step 1: Heart Rate ──
        GuidedStepCard(
            stepNumber = 1,
            title = strings.heartRate,
            value = if (hasHR) "${rppgResult.bpm!!.toInt()} ${strings.bpm}" else "—",
            subtitle = when {
                !hasHR -> strings.tapToMeasureHR
                rppgResult.bpm!! > 100 -> strings.hrElevated
                rppgResult.bpm!! < 60 -> strings.hrLow
                else -> strings.hrNormal
            },
            isComplete = hasHR,
            statusColor = when {
                !hasHR -> Color.Gray
                rppgResult.bpm!! > 100 || rppgResult.bpm!! < 50 -> NkuColors.ListeningIndicator
                else -> NkuColors.Success
            },
            onClick = { onNavigateToTab(1) }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 2: Anemia ──
        GuidedStepCard(
            stepNumber = 2,
            title = strings.anemiaScreen,
            value = if (hasAnemia) strings.localizedSeverity(pallorResult.severity) else "—",
            subtitle = when {
                !hasAnemia -> strings.tapToCaptureEyelid
                pallorResult.severity == PallorSeverity.NORMAL -> strings.noPallor
                pallorResult.severity == PallorSeverity.MILD -> strings.mildPallor
                pallorResult.severity == PallorSeverity.MODERATE -> strings.moderatePallor
                pallorResult.severity == PallorSeverity.SEVERE -> strings.severePallor
                else -> "—"
            },
            isComplete = hasAnemia,
            statusColor = when {
                !hasAnemia -> Color.Gray
                pallorResult.severity == PallorSeverity.NORMAL -> NkuColors.Success
                pallorResult.severity == PallorSeverity.MILD -> NkuColors.TriageYellow
                pallorResult.severity == PallorSeverity.MODERATE -> NkuColors.TriageOrange
                pallorResult.severity == PallorSeverity.SEVERE -> NkuColors.ListeningIndicator
                else -> Color.Gray
            },
            onClick = { onNavigateToTab(2) }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 3: Jaundice ──
        GuidedStepCard(
            stepNumber = 3,
            title = strings.jaundiceScreen,
            value = if (hasJaundice) strings.localizedSeverity(jaundiceResult.severity) else "—",
            subtitle = when {
                !hasJaundice -> strings.tapToCaptureEye
                jaundiceResult.severity == JaundiceSeverity.NORMAL -> strings.noJaundice
                jaundiceResult.severity == JaundiceSeverity.MILD -> strings.mildJaundice
                jaundiceResult.severity == JaundiceSeverity.MODERATE -> strings.moderateJaundice
                jaundiceResult.severity == JaundiceSeverity.SEVERE -> strings.severeJaundice
                else -> "—"
            },
            isComplete = hasJaundice,
            statusColor = when {
                !hasJaundice -> Color.Gray
                jaundiceResult.severity == JaundiceSeverity.NORMAL -> NkuColors.Success
                jaundiceResult.severity == JaundiceSeverity.MILD -> NkuColors.TriageYellow
                jaundiceResult.severity == JaundiceSeverity.MODERATE -> NkuColors.TriageOrange
                jaundiceResult.severity == JaundiceSeverity.SEVERE -> NkuColors.ListeningIndicator
                else -> Color.Gray
            },
            onClick = { onNavigateToTab(3) }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 4: Preeclampsia ──
        GuidedStepCard(
            stepNumber = 4,
            title = strings.preeclampsiaScreen,
            value = if (hasPreE) strings.localizedSeverity(edemaResult.severity) else "—",
            subtitle = when {
                !hasPreE -> strings.tapToCaptureFace
                edemaResult.severity == EdemaSeverity.NORMAL -> strings.noSwelling
                edemaResult.severity == EdemaSeverity.MILD -> strings.mildSwelling
                edemaResult.severity == EdemaSeverity.MODERATE -> strings.moderateSwelling
                edemaResult.severity == EdemaSeverity.SIGNIFICANT -> strings.significantSwelling
                else -> "—"
            },
            isComplete = hasPreE,
            statusColor = when {
                !hasPreE -> Color.Gray
                edemaResult.severity == EdemaSeverity.NORMAL -> NkuColors.Success
                edemaResult.severity == EdemaSeverity.MILD -> NkuColors.TriageYellow
                edemaResult.severity == EdemaSeverity.MODERATE -> NkuColors.TriageOrange
                edemaResult.severity == EdemaSeverity.SIGNIFICANT -> NkuColors.ListeningIndicator
                else -> Color.Gray
            },
            onClick = { onNavigateToTab(4) }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 5: Respiratory/TB ──
        GuidedStepCard(
            stepNumber = 5,
            title = strings.respiratoryScreen,
            value = if (hasRespiratory) strings.localizedRespiratoryClassification(respiratoryResult.classification) else "—",
            subtitle = when {
                !hasRespiratory -> strings.tapToRecordCough
                respiratoryResult.classification == RespiratoryRisk.NORMAL -> strings.respiratoryNormal
                respiratoryResult.classification == RespiratoryRisk.LOW_RISK -> strings.respiratoryLowRisk
                respiratoryResult.classification == RespiratoryRisk.MODERATE_RISK -> strings.respiratoryModerateRisk
                respiratoryResult.classification == RespiratoryRisk.HIGH_RISK -> strings.respiratoryHighRisk
                else -> "—"
            },
            isComplete = hasRespiratory,
            statusColor = when {
                !hasRespiratory -> Color.Gray
                respiratoryResult.classification == RespiratoryRisk.NORMAL -> NkuColors.Success
                respiratoryResult.classification == RespiratoryRisk.LOW_RISK -> NkuColors.TriageYellow
                respiratoryResult.classification == RespiratoryRisk.MODERATE_RISK -> NkuColors.TriageOrange
                respiratoryResult.classification == RespiratoryRisk.HIGH_RISK -> NkuColors.ListeningIndicator
                else -> Color.Gray
            },
            onClick = { onNavigateToTab(5) }
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Disclaimer
        Card(
            colors = CardDefaults.cardColors(
                containerColor = NkuColors.InstructionCard.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚕ ${strings.disclaimer}",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

@Composable
fun GuidedStepCard(
    stepNumber: Int,
    title: String,
    value: String,
    subtitle: String,
    isComplete: Boolean,
    statusColor: Color,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete)
                NkuColors.CompletedCard.copy(alpha = 0.8f)
            else
                NkuColors.CardBackground.copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        border = if (isComplete) 
            BorderStroke(1.dp, NkuColors.Success.copy(alpha = 0.3f))
        else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isComplete) NkuColors.Success else NkuColors.InactiveElement
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isComplete) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "$stepNumber",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            
            Spacer(Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Color.Gray)
                Text(
                    value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (value == "—") NkuColors.InactiveText else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = statusColor.copy(alpha = 0.8f)
                )
            }
            
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(statusColor)
                    .semantics {
                        contentDescription = if (isComplete) "$title: complete" else "$title: not yet measured"
                    }
            )
        }
    }
}
