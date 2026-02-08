package com.nku.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.Locale

/**
 * MainActivity - Nku Sentinel
 * 
 * Complete on-device medical screening:
 * - Cardio Check (rPPG heart rate)
 * - Anemia Screening (HSV pallor detection)
 * - Preeclampsia Screening (edema geometry analysis)
 * - Clinical reasoning via MedGemma or rule-based fallback
 * 
 * All processing on-device, zero cloud dependency.
 * Target: 2GB RAM Android devices.
 */

class MainActivity : ComponentActivity() {
    
    // Core processors (zero additional ML model footprint)
    private val rppgProcessor = RPPGProcessor(fps = 30f, bufferSeconds = 10f)
    private val pallorDetector = PallorDetector()
    private val edemaDetector = EdemaDetector()
    
    // Integration layer
    private lateinit var thermalManager: ThermalManager
    private lateinit var sensorFusion: SensorFusion
    private lateinit var clinicalReasoner: ClinicalReasoner
    
    // Voice I/O
    private lateinit var piperTTS: PiperTTS
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        thermalManager = ThermalManager(this)
        sensorFusion = SensorFusion(rppgProcessor, pallorDetector, edemaDetector)
        clinicalReasoner = ClinicalReasoner()
        
        // Initialize TTS for spoken results
        piperTTS = PiperTTS(this)
        piperTTS.initialize()
        
        // Request camera + audio permissions
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Permissions result handled — camera & mic
        }
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
        
        setContent {
            NkuSentinelApp(
                thermalManager = thermalManager,
                rppgProcessor = rppgProcessor,
                pallorDetector = pallorDetector,
                edemaDetector = edemaDetector,
                sensorFusion = sensorFusion,
                clinicalReasoner = clinicalReasoner,
                piperTTS = piperTTS
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        piperTTS.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NkuSentinelApp(
    thermalManager: ThermalManager,
    rppgProcessor: RPPGProcessor,
    pallorDetector: PallorDetector,
    edemaDetector: EdemaDetector,
    sensorFusion: SensorFusion,
    clinicalReasoner: ClinicalReasoner,
    piperTTS: PiperTTS
) {
    val thermalStatus by thermalManager.thermalStatus.collectAsState()
    val rppgResult by rppgProcessor.result.collectAsState()
    val pallorResult by pallorDetector.result.collectAsState()
    val edemaResult by edemaDetector.result.collectAsState()
    val vitalSigns by sensorFusion.vitalSigns.collectAsState()
    val assessment by clinicalReasoner.assessment.collectAsState()
    val ttsState by piperTTS.state.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPregnant by remember { mutableStateOf(false) }
    var gestationalWeeks by remember { mutableStateOf("") }
    
    val tabs = listOf("Home", "Cardio", "Anemia", "Swelling", "Triage")
    
    // Dark medical theme
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFF2196F3),
            surface = Color(0xFF1A1A2E),
            background = Color(0xFF0F0F1A)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text("Nku Sentinel", fontWeight = FontWeight.Bold) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A1A2E)
                    ),
                    actions = {
                        // Thermal status kept invisible to user (avoid confusion with body temp)
                        // ThermalManager still gates inference via canRun in the background
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    when (index) {
                                        0 -> Icons.Default.Home
                                        1 -> Icons.Default.Favorite
                                        2 -> Icons.Default.Face
                                        3 -> Icons.Default.Warning
                                        else -> Icons.Default.CheckCircle
                                    },
                                    contentDescription = title
                                )
                            },
                            label = { Text(title, fontSize = 10.sp) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))
                        )
                    )
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(rppgResult, pallorResult, edemaResult)
                    1 -> CardioScreen(rppgResult, rppgProcessor, thermalStatus.safe)
                    2 -> AnemiaScreen(pallorResult, pallorDetector)
                    3 -> PreeclampsiaScreen(
                        edemaResult = edemaResult,
                        edemaDetector = edemaDetector,
                        isPregnant = isPregnant,
                        gestationalWeeks = gestationalWeeks,
                        onPregnancyChange = { pregnant, weeks ->
                            isPregnant = pregnant
                            gestationalWeeks = weeks
                            sensorFusion.setPregnancyContext(pregnant, weeks.toIntOrNull())
                        }
                    )
                    4 -> TriageScreen(
                        vitalSigns = vitalSigns,
                        rppgResult = rppgResult,
                        pallorResult = pallorResult,
                        edemaResult = edemaResult,
                        assessment = assessment,
                        sensorFusion = sensorFusion,
                        piperTTS = piperTTS,
                        ttsState = ttsState,
                        onRunTriage = {
                            sensorFusion.updateVitalSigns()
                            clinicalReasoner.createRuleBasedAssessment(sensorFusion.vitalSigns.value)
                        }
                    )
                }
            }
        }
    }
}

// ============================================================
// HOME SCREEN
// ============================================================

@Composable
fun HomeScreen(rppgResult: RPPGResult, pallorResult: PallorResult, edemaResult: EdemaResult) {
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
            "Nku Sentinel",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            "Camera-based vital signs screening",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // ── Progress Indicator ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E38).copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$completedCount of 3 screenings complete",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completedCount == 3) Color(0xFF4CAF50) else Color(0xFF90CAF9)
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = completedCount / 3f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (completedCount) {
                        3 -> Color(0xFF4CAF50)
                        0 -> Color(0xFF666666)
                        else -> Color(0xFF2196F3)
                    },
                    trackColor = Color(0xFF252540)
                )
                if (completedCount == 3) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✓ Ready for triage — go to Triage tab",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
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
            title = "Heart Rate",
            value = if (hasHR) "${rppgResult.bpm!!.toInt()} BPM" else "—",
            subtitle = when {
                !hasHR -> "Tap \"Cardio\" tab to measure"
                rppgResult.bpm!! > 100 -> "⚠ Elevated — may indicate stress or anemia"
                rppgResult.bpm!! < 60 -> "⚠ Low — monitor closely"
                else -> "✓ Within normal range"
            },
            isComplete = hasHR,
            statusColor = when {
                !hasHR -> Color.Gray
                rppgResult.bpm!! > 100 || rppgResult.bpm!! < 50 -> Color(0xFFFF5722)
                else -> Color(0xFF4CAF50)
            }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 2: Anemia ──
        GuidedStepCard(
            stepNumber = 2,
            title = "Anemia Screen",
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
                pallorResult.severity == PallorSeverity.NORMAL -> Color(0xFF4CAF50)
                pallorResult.severity == PallorSeverity.MILD -> Color(0xFFFFEB3B)
                pallorResult.severity == PallorSeverity.MODERATE -> Color(0xFFFF9800)
                pallorResult.severity == PallorSeverity.SEVERE -> Color(0xFFFF5722)
                else -> Color.Gray
            }
        )
        
        Spacer(Modifier.height(10.dp))
        
        // ── Step 3: Preeclampsia ──
        GuidedStepCard(
            stepNumber = 3,
            title = "Swelling Check",
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
                edemaResult.severity == EdemaSeverity.NORMAL -> Color(0xFF4CAF50)
                edemaResult.severity == EdemaSeverity.MILD -> Color(0xFFFFEB3B)
                edemaResult.severity == EdemaSeverity.MODERATE -> Color(0xFFFF9800)
                edemaResult.severity == EdemaSeverity.SIGNIFICANT -> Color(0xFFFF5722)
                else -> Color.Gray
            }
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Disclaimer
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D44).copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚕ This is an AI-assisted screening tool. Always consult a healthcare professional for diagnosis and treatment.",
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
                Color(0xFF1A2A1A).copy(alpha = 0.8f)
            else
                Color(0xFF252540).copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        border = if (isComplete) 
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
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
                        if (isComplete) Color(0xFF4CAF50) else Color(0xFF3D3D5C)
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
                    color = if (value == "—") Color(0xFF666666) else Color.White
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

// ============================================================
// CAMERA PREVIEW COMPOSABLE (Reusable)
// ============================================================

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null,
    onImageCaptured: ((Bitmap) -> Unit)? = null,
    enableAnalysis: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
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
                    
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                    
                    cameraProvider.unbindAll()
                    
                    if (enableAnalysis && onFrameAnalyzed != null) {
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val bitmap = imageProxy.toBitmap()
                            onFrameAnalyzed(bitmap)
                            imageProxy.close()
                        }
                        
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }
                } catch (e: Exception) {
                    Log.e("NkuCamera", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

// ============================================================
// CARDIO SCREEN — rPPG Heart Rate via Camera
// ============================================================

@Composable
fun CardioScreen(rppgResult: RPPGResult, rppgProcessor: RPPGProcessor, canRun: Boolean) {
    var isMeasuring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cardio Check", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Heart rate via camera (rPPG)", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        if (!canRun) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722).copy(alpha = 0.2f))) {
                Text(
                    "Device cooling down — AI paused",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFFF5722)
                )
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
                        onFrameAnalyzed = { bitmap ->
                            rppgProcessor.processFrame(bitmap)
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
                            "Buffer: ${rppgResult.bufferFillPercent.toInt()}%",
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
            // Circular progress indicator
            val hasResult = rppgResult.bpm != null && rppgResult.confidence > 0.4f
            
            Canvas(modifier = Modifier.size(180.dp)) {
                // Background ring
                drawArc(
                    color = Color(0xFF252540),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                )
                
                if (isMeasuring) {
                    // Fill ring based on buffer
                    drawArc(
                        color = if (hasResult) Color(0xFF4CAF50) else Color(0xFF2196F3),
                        startAngle = -90f,
                        sweepAngle = 360f * (rppgResult.bufferFillPercent / 100f),
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx())
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (hasResult) "${rppgResult.bpm!!.toInt()}" else "—",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasResult) Color(0xFF4CAF50) else Color.White
                )
                Text("BPM", fontSize = 16.sp, color = Color.Gray)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (isMeasuring) {
            Text("Signal: ${rppgResult.signalQuality}", color = Color.Gray, fontSize = 13.sp)
            Text("Confidence: ${(rppgResult.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 13.sp)
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Start / Stop button
        Button(
            onClick = {
                if (isMeasuring) {
                    isMeasuring = false
                } else {
                    rppgProcessor.reset()
                    isMeasuring = true
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMeasuring) Color(0xFFFF5722) else Color(0xFF4CAF50)
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
            Text(if (isMeasuring) "Stop Measurement" else "Start Measurement", fontSize = 16.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Instructions
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("How it works", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. Tap \"Start Measurement\" above\n" +
                    "2. Face the front camera in good lighting\n" +
                    "3. Hold still for 10 seconds\n" +
                    "4. Your heart rate appears when the buffer fills",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ============================================================
// ANEMIA SCREEN — Conjunctival Pallor Detection
// ============================================================

@Composable
fun AnemiaScreen(pallorResult: PallorResult, pallorDetector: PallorDetector) {
    var isCapturing by remember { mutableStateOf(false) }
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Anemia Screening", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Conjunctival pallor detection", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(20.dp))
        
        if (!pallorResult.hasBeenAnalyzed && !isCapturing) {
            // ---- NOT YET SCREENED STATE ----
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Not yet screened",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Capture the conjunctiva (inner lower eyelid) to screen for anemia",
                        fontSize = 13.sp,
                        color = Color.Gray,
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
                            "Point at conjunctiva (inner lower eyelid)",
                            fontSize = 11.sp,
                            color = Color.White,
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
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        lastCapturedBitmap?.let { bmp ->
                            pallorDetector.analyzeConjunctiva(bmp)
                            isCapturing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Analyze")
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Results (only show after analysis)
        if (pallorResult.hasBeenAnalyzed) {
            val scoreColor = when (pallorResult.severity) {
                PallorSeverity.NORMAL -> Color(0xFF4CAF50)
                PallorSeverity.MILD -> Color(0xFFFFEB3B)
                PallorSeverity.MODERATE -> Color(0xFFFF9800)
                PallorSeverity.SEVERE -> Color(0xFFFF5722)
            }
            
            Text(
                pallorResult.severity.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            
            Text(
                "Pallor Score: ${(pallorResult.pallorScore * 100).toInt()}%",
                fontSize = 16.sp,
                color = Color.Gray
            )
            
            Text(
                "Confidence: ${(pallorResult.confidence * 100).toInt()}%",
                fontSize = 13.sp,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Recommendation
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recommendation", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(pallorResult.recommendation, color = Color.Gray, fontSize = 14.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Capture / Re-capture button (when not in capture mode)
        if (!isCapturing) {
            Button(
                onClick = { isCapturing = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pallorResult.hasBeenAnalyzed) "Re-capture Eyelid" else "Capture Inner Eyelid",
                    fontSize = 16.sp
                )
            }
            
            Spacer(Modifier.height(20.dp))
        }
        
        // How to capture guide
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How to Capture", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Gently pull down the patient's lower eyelid\n" +
                    "2. Point camera at the inner surface (conjunctiva)\n" +
                    "3. Ensure good lighting (daylight preferred)\n" +
                    "4. Tap \"Analyze\" when the image is clear",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            "Conjunctival analysis works across all skin tones",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

// ============================================================
// PREECLAMPSIA SCREEN — Facial Edema Detection
// ============================================================

@Composable
fun PreeclampsiaScreen(
    edemaResult: EdemaResult,
    edemaDetector: EdemaDetector,
    isPregnant: Boolean,
    gestationalWeeks: String,
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
        Text("Preeclampsia Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Facial edema detection", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        // Pregnancy context
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pregnant?", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = isPregnant,
                        onCheckedChange = { onPregnancyChange(it, gestationalWeeks) }
                    )
                }
                
                if (isPregnant) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gestationalWeeks,
                        onValueChange = { onPregnancyChange(isPregnant, it) },
                        label = { Text("Gestational weeks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        if (!edemaResult.hasBeenAnalyzed && !isCapturing) {
            // ---- NOT YET SCREENED STATE ----
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Not yet screened",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Capture a front-facing photo to check for facial edema",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
        }
        
        // Camera viewfinder
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
                        onFrameAnalyzed = { bitmap ->
                            lastCapturedBitmap = bitmap
                        }
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Center your face, keep neutral expression",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isCapturing = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        lastCapturedBitmap?.let { bmp ->
                            edemaDetector.analyzeFace(bmp)
                            isCapturing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Analyze")
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Results (only after analysis)
        if (edemaResult.hasBeenAnalyzed) {
            val scoreColor = when (edemaResult.severity) {
                EdemaSeverity.NORMAL -> Color(0xFF4CAF50)
                EdemaSeverity.MILD -> Color(0xFFFFEB3B)
                EdemaSeverity.MODERATE -> Color(0xFFFF9800)
                EdemaSeverity.SIGNIFICANT -> Color(0xFFFF5722)
            }
            
            Text(
                edemaResult.severity.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            
            Text(
                "Edema Score: ${(edemaResult.edemaScore * 100).toInt()}%",
                color = Color.Gray
            )
            
            Text(
                "Periorbital: ${(edemaResult.periorbitalScore * 100).toInt()}%",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            Text(
                "Confidence: ${(edemaResult.confidence * 100).toInt()}%",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Risk factors
            if (edemaResult.riskFactors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722).copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Risk Factors", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                        edemaResult.riskFactors.forEach { risk ->
                            Text("• $risk", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Recommendation
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recommendation", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(edemaResult.recommendation, color = Color.Gray, fontSize = 14.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Capture / Re-capture button
        if (!isCapturing) {
            Button(
                onClick = { isCapturing = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (edemaResult.hasBeenAnalyzed) "Re-capture Face" else "Capture Face Photo",
                    fontSize = 16.sp
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Detection note
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Uses geometry-based analysis (facial proportions). Works across all skin tones. Best with front-facing photos in consistent lighting.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ============================================================
// TRIAGE SCREEN — Clinical Reasoning
// ============================================================

@Composable
fun TriageScreen(
    vitalSigns: VitalSigns,
    rppgResult: RPPGResult,
    pallorResult: PallorResult,
    edemaResult: EdemaResult,
    assessment: ClinicalAssessment?,
    sensorFusion: SensorFusion,
    piperTTS: PiperTTS,
    ttsState: TTSState,
    onRunTriage: () -> Unit
) {
    val context = LocalContext.current
    val hasAnyData = (rppgResult.bpm != null && rppgResult.confidence > 0.4f) ||
                     pallorResult.hasBeenAnalyzed ||
                     edemaResult.hasBeenAnalyzed
    var symptomText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val symptoms by sensorFusion.symptoms.collectAsState()
    
    // Speech-to-text launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                // Add spoken text directly as a symptom
                sensorFusion.addSymptom(spokenText.trim())
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Clinical Triage", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("AI-assisted severity assessment", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(20.dp))
        
        // Data completeness indicator
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Screening Data", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                
                DataCheckRow(
                    label = "Heart Rate",
                    isComplete = rppgResult.bpm != null && rppgResult.confidence > 0.4f,
                    detail = rppgResult.bpm?.let { "${it.toInt()} BPM" }
                )
                DataCheckRow(
                    label = "Anemia Screen",
                    isComplete = pallorResult.hasBeenAnalyzed,
                    detail = if (pallorResult.hasBeenAnalyzed) pallorResult.severity.name else null
                )
                DataCheckRow(
                    label = "Swelling Check",
                    isComplete = edemaResult.hasBeenAnalyzed,
                    detail = if (edemaResult.hasBeenAnalyzed) edemaResult.severity.name else null
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // ── Symptom Input (Voice OR Text) ──
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Patient-Reported Symptoms",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    "Type or tap the mic to speak symptoms",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 🎤 Voice input button
                    IconButton(
                        onClick = {
                            val hasAudioPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasAudioPermission) {
                                isListening = true
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe symptoms...")
                                    // Allow recognition in any language the device supports
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                }
                                speechLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFFFF5722).copy(alpha = 0.3f)
                                else Color(0xFF3D3D5C)
                            )
                    ) {
                        Icon(
                            if (isListening) Icons.Default.Hearing else Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = if (isListening) Color(0xFFFF5722) else Color(0xFF00D4FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    // ⌨️ Text input field
                    OutlinedTextField(
                        value = symptomText,
                        onValueChange = { symptomText = it },
                        placeholder = { Text("e.g. headache, dizziness...", color = Color(0xFF555555)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color(0xFF3D3D5C),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (symptomText.isNotBlank()) {
                                sensorFusion.addSymptom(symptomText.trim())
                                symptomText = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add symptom",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Listening indicator
                if (isListening) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🎤 Listening... speak now",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5722)
                    )
                }
                
                // Show added symptoms
                if (symptoms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    symptoms.forEach { symptom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "• ${symptom.symptom}",
                                color = Color(0xFF90CAF9),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { sensorFusion.removeSymptom(symptom.symptom) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (!hasAnyData) {
            // Warning: no data
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No screening data collected yet. Go to other tabs first to capture vital signs before running triage.",
                        color = Color(0xFFFF9800),
                        fontSize = 13.sp
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        Button(
            onClick = onRunTriage,
            enabled = hasAnyData,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color(0xFF333344)
            ),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Run Triage Assessment", fontSize = 16.sp)
        }
        
        Spacer(Modifier.height(24.dp))
        
        assessment?.let { result ->
            // Triage category badge
            val categoryColor = when (result.triageCategory) {
                TriageCategory.GREEN -> Color(0xFF4CAF50)
                TriageCategory.YELLOW -> Color(0xFFFFEB3B)
                TriageCategory.ORANGE -> Color(0xFFFF9800)
                TriageCategory.RED -> Color(0xFFFF5722)
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = categoryColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        result.triageCategory.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Severity: ${result.overallSeverity.name}",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        "Urgency: ${result.urgency.name.replace("_", " ")}",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // ── 🔊 Listen Button — Speak the triage result aloud ──
            val speakableText = buildString {
                append("Severity: ${result.overallSeverity.name}. ")
                append("Urgency: ${result.urgency.name.replace("_", " ")}. ")
                if (result.primaryConcerns.isNotEmpty()) {
                    append("Concerns: ")
                    result.primaryConcerns.forEach { append("$it. ") }
                }
                if (result.recommendations.isNotEmpty()) {
                    append("Recommendations: ")
                    result.recommendations.forEach { append("$it. ") }
                }
            }
            
            Button(
                onClick = {
                    if (ttsState == TTSState.SPEAKING) {
                        piperTTS.stop()
                    } else {
                        piperTTS.speak(speakableText, "en")
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ttsState == TTSState.SPEAKING)
                        Color(0xFFFF5722) else Color(0xFF00D4FF)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    if (ttsState == TTSState.SPEAKING) Icons.Default.Stop
                    else Icons.Default.VolumeUp,
                    contentDescription = if (ttsState == TTSState.SPEAKING) "Stop" else "Listen",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ttsState == TTSState.SPEAKING) "Stop" else "🔊 Listen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Concerns
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Primary Concerns", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    result.primaryConcerns.forEach { concern ->
                        Text("• $concern", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Recommendations
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recommendations", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Spacer(Modifier.height(8.dp))
                    result.recommendations.forEach { rec ->
                        Text("• $rec", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Disclaimer
            Text(
                result.disclaimer,
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DataCheckRow(label: String, isComplete: Boolean, detail: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isComplete) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isComplete) Color(0xFF4CAF50) else Color(0xFF666666),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            detail ?: "Not done",
            color = if (isComplete) Color(0xFF4CAF50) else Color(0xFF666666),
            fontSize = 12.sp
        )
    }
}
