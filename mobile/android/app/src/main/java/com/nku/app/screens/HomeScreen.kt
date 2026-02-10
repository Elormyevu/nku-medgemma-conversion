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

/**
 * HomeScreen — Dashboard overview with screening progress and language selector.
 * Extracted from MainActivity.kt (F-UI-3 / F-CQ-1).
 */

@Composable
fun HomeScreen(
    rppgResult: RPPGResult,
    pallorResult: PallorResult,
    edemaResult: EdemaResult,
    strings: LocalizedStrings.UiStrings,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    // Progress tracking
    val hasHR = rppgResult.bpm != null && rppgResult.confidence > 0.4f
    val hasAnemia = pallorResult.hasBeenAnalyzed
    val hasPreE = edemaResult.hasBeenAnalyzed
    val completedCount = listOf(hasHR, hasAnemia, hasPreE).count { it }
    
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
            color = Color.White
        )
        
        Text(
            strings.appSubtitle,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // ── Language Selector ──
        var languageExpanded by remember { mutableStateOf(false) }
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
                Text(strings.language, color = Color.White, fontSize = 13.sp)
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
                                    onLanguageChange(code)
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
        
        Spacer(Modifier.height(12.dp))
        
        // ── Progress Indicator ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = NkuColors.ProgressBackground.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    strings.screeningsProgress.format(completedCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completedCount == 3) NkuColors.Success else NkuColors.MutedBlue
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = completedCount / 3f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (completedCount) {
                        3 -> NkuColors.Success
                        0 -> NkuColors.InactiveText
                        else -> NkuColors.Secondary
                    },
                    trackColor = NkuColors.CardBackground
                )
                if (completedCount == 3) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.readyForTriage,
                        fontSize = 12.sp,
                        color = NkuColors.Success
                    )
                } else if (completedCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Follow the steps below to screen a patient",
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
                !hasHR -> "Tap \"Cardio\" tab to measure"
                rppgResult.bpm!! > 100 -> "⚠ Elevated — may indicate stress or anemia"
                rppgResult.bpm!! < 60 -> "⚠ Low — monitor closely"
                else -> "✓ Within normal range"
            },
            isComplete = hasHR,
            statusColor = when {
                !hasHR -> Color.Gray
                rppgResult.bpm!! > 100 || rppgResult.bpm!! < 50 -> NkuColors.ListeningIndicator
                else -> NkuColors.Success
            }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 2: Anemia ──
        GuidedStepCard(
            stepNumber = 2,
            title = strings.anemiaScreen,
            value = if (hasAnemia) pallorResult.severity.name else "—",
            subtitle = when {
                !hasAnemia -> "Tap \"Anemia\" tab to capture eyelid"
                pallorResult.severity == PallorSeverity.NORMAL -> "✓ No pallor detected"
                pallorResult.severity == PallorSeverity.MILD -> "Mild pallor — monitor weekly"
                pallorResult.severity == PallorSeverity.MODERATE -> "⚠ Moderate — get hemoglobin test"
                pallorResult.severity == PallorSeverity.SEVERE -> "🚨 Severe — urgent referral"
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
            }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 3: Preeclampsia ──
        GuidedStepCard(
            stepNumber = 3,
            title = strings.preeclampsiaScreen,
            value = if (hasPreE) edemaResult.severity.name else "—",
            subtitle = when {
                !hasPreE -> "Tap \"Pre-E\" tab to capture face"
                edemaResult.severity == EdemaSeverity.NORMAL -> "✓ No facial swelling"
                edemaResult.severity == EdemaSeverity.MILD -> "Mild swelling — check blood pressure"
                edemaResult.severity == EdemaSeverity.MODERATE -> "⚠ Check BP and urine protein"
                edemaResult.severity == EdemaSeverity.SIGNIFICANT -> "🚨 Urgent evaluation needed"
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
            }
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
                modifier = Modifier.padding(12.dp)
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
    statusColor: Color
) {
    Card(
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
            modifier = Modifier.padding(16.dp),
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
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "$stepNumber",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
                    color = if (value == "—") NkuColors.InactiveText else Color.White
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
            )
        }
    }
}
