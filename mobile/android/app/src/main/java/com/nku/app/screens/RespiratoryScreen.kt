package com.nku.app.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RespiratoryScreen — HeAR-based respiratory/TB screening via cough audio analysis.
 *
 * CHW workflow:
 * 1. Ask patient to cough 3 times into phone microphone
 * 2. Press record button, capture ~5 seconds of coughs
 * 3. HeAR analyzes audio and produces respiratory risk assessment
 *
 * Uses RECORD_AUDIO permission (already requested in MainActivity).
 */

@Composable
fun RespiratoryScreen(
    respiratoryResult: RespiratoryResult,
    respiratoryDetector: RespiratoryDetector,
    strings: LocalizedStrings.UiStrings
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var micPermissionDenied by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
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
        // Title
        Text(
            strings.respiratoryTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            strings.respiratorySubtitle,
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(16.dp))

        // Mic permission denied feedback
        if (micPermissionDenied) {
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
                        strings.micPermissionTitle,
                        fontWeight = FontWeight.Bold,
                        color = NkuColors.Warning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strings.micPermissionMessage,
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

        // Risk display ring
        Box(contentAlignment = Alignment.Center) {
            val hasResult = respiratoryResult.confidence > 0.4f

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

                if (isRecording) {
                    // Recording progress
                    drawArc(
                        color = NkuColors.Secondary,
                        startAngle = -90f,
                        sweepAngle = 360f * recordingProgress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                    )
                } else if (hasResult) {
                    // Result ring colored by risk level
                    val ringColor = when (respiratoryResult.classification) {
                        RespiratoryRisk.NORMAL -> NkuColors.Success
                        RespiratoryRisk.LOW_RISK -> NkuColors.Warning
                        RespiratoryRisk.MODERATE_RISK -> Color(0xFFFF9800)
                        RespiratoryRisk.HIGH_RISK -> Color(0xFFF44336)
                    }
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics {
                    contentDescription = if (hasResult) {
                        "Respiratory risk: ${respiratoryResult.classification.name}"
                    } else {
                        "Respiratory risk: not yet assessed"
                    }
                }
            ) {
                if (isRecording) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = NkuColors.ListeningIndicator,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        strings.recording,
                        fontSize = 14.sp,
                        color = NkuColors.ListeningIndicator
                    )
                } else if (hasResult) {
                    val (label, color) = when (respiratoryResult.classification) {
                        RespiratoryRisk.NORMAL -> Pair(strings.respiratoryNormal, NkuColors.Success)
                        RespiratoryRisk.LOW_RISK -> Pair(strings.respiratoryLowRisk, NkuColors.Warning)
                        RespiratoryRisk.MODERATE_RISK -> Pair(strings.respiratoryModerateRisk, Color(0xFFFF9800))
                        RespiratoryRisk.HIGH_RISK -> Pair(strings.respiratoryHighRisk, Color(0xFFF44336))
                    }
                    Text(
                        label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("—", fontSize = 24.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Result details
        if (respiratoryResult.confidence > 0.4f) {
            Text(
                "${strings.confidenceLabel}: ${(respiratoryResult.confidence * 100).toInt()}%",
                color = Color.Gray,
                fontSize = 13.sp
            )
            if (respiratoryResult.coughDetected) {
                Text(
                    "${strings.coughsDetected}: ${respiratoryResult.coughCount}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
            Text(
                "${strings.audioQualityLabel}: ${respiratoryResult.audioQuality}",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Record button
        Button(
            onClick = {
                if (isRecording) {
                    // P1 fix: Cancel the active recording coroutine — this breaks
                    // the read loop and stops the AudioRecord immediately.
                    recordingJob?.cancel()
                    recordingJob = null
                    isRecording = false
                    recordingProgress = 0f
                } else {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        micPermissionDenied = false
                        isRecording = true
                        recordingProgress = 0f
                        respiratoryDetector.reset()

                        recordingJob = scope.launch {
                            recordAndAnalyze(
                                respiratoryDetector = respiratoryDetector,
                                onProgress = { progress -> recordingProgress = progress },
                                onComplete = { isRecording = false }
                            )
                        }
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) NkuColors.ListeningIndicator else NkuColors.Success
            ),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                if (isRecording) Icons.Default.Close else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isRecording) strings.stopRecording else strings.startRecording,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // Instructions
        Card(
            colors = CardDefaults.cardColors(containerColor = NkuColors.InstructionCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    strings.howItWorks,
                    fontWeight = FontWeight.Bold,
                    color = NkuColors.Secondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.respiratoryInstructions,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // HeAR model info card
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = NkuColors.CardBackground.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    strings.poweredByHeAR,
                    fontWeight = FontWeight.Bold,
                    color = NkuColors.Secondary,
                    fontSize = 12.sp
                )
                Text(
                    strings.hearDescription,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Record audio from microphone and run respiratory analysis.
 *
 * Records 5 seconds of 16kHz mono audio via AudioRecord,
 * then passes the buffer to RespiratoryDetector for analysis.
 */
private suspend fun recordAndAnalyze(
    respiratoryDetector: RespiratoryDetector,
    onProgress: (Float) -> Unit,
    onComplete: () -> Unit
) {
    withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val recordDurationMs = 5000L
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate * 2 * 5)  // At least 5 seconds

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("RespiratoryScreen", "AudioRecord failed to initialize")
                withContext(Dispatchers.Main) { onComplete() }
                return@withContext
            }

            audioRecord.startRecording()

            val totalSamples = (sampleRate * recordDurationMs / 1000).toInt()
            val allSamples = ShortArray(totalSamples)
            var samplesRead = 0
            val chunkSize = sampleRate / 10  // Read in 100ms chunks
            var wasCancelled = false

            while (samplesRead < totalSamples) {
                // P1 fix: Check coroutine cancellation each iteration.
                // When the user presses Stop, the Job is cancelled and
                // ensureActive() throws CancellationException, breaking the read loop.
                try {
                    kotlin.coroutines.coroutineContext.ensureActive()
                } catch (_: kotlinx.coroutines.CancellationException) {
                    wasCancelled = true
                    break
                }
                val toRead = minOf(chunkSize, totalSamples - samplesRead)
                val read = audioRecord.read(allSamples, samplesRead, toRead)
                if (read > 0) {
                    samplesRead += read
                    val progress = samplesRead.toFloat() / totalSamples
                    withContext(Dispatchers.Main) { onProgress(progress) }
                } else {
                    break
                }
            }

            audioRecord.stop()

            // P1 fix: Skip analysis if recording was cancelled by the user.
            // Partial audio would produce unreliable results.
            if (wasCancelled) {
                Log.i("RespiratoryScreen", "Recording cancelled by user, skipping analysis")
                return@withContext
            }

            // Run analysis on captured audio
            val capturedSamples = if (samplesRead < totalSamples) {
                allSamples.copyOf(samplesRead)
            } else {
                allSamples
            }

            // Use full two-tier path when ViT-L encoder is available on-device.
            // Falls back to Event Detector only mode otherwise.
            if (respiratoryDetector.isViTLAvailable()) {
                respiratoryDetector.processAudioDeep(capturedSamples, sampleRate)
            } else {
                respiratoryDetector.processAudio(capturedSamples, sampleRate)
            }

        } catch (e: SecurityException) {
            Log.e("RespiratoryScreen", "Microphone permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("RespiratoryScreen", "Recording error: ${e.message}", e)
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("RespiratoryScreen", "Error releasing AudioRecord: ${e.message}")
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }
}
