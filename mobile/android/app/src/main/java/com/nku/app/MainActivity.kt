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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.nku.app.ui.NkuTheme
import com.nku.app.ui.NkuColors
import com.nku.app.screens.*
import com.nku.app.data.NkuDatabase
import com.nku.app.data.ScreeningEntity

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
 *
 * Screen composables extracted to com.nku.app.screens package (F-UI-3 / F-CQ-1).
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
    
    // Voice I/O (Android system TTS)
    private lateinit var nkuTTS: NkuTTS
    
    // MediaPipe face detection (for rPPG ROI + edema landmarks)
    private lateinit var faceDetectorHelper: FaceDetectorHelper
    
    // On-device MedGemma inference (used when models are sideloaded)
    private lateinit var nkuEngine: NkuInferenceEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        thermalManager = ThermalManager(this)
        sensorFusion = SensorFusion(rppgProcessor, pallorDetector, edemaDetector)
        clinicalReasoner = ClinicalReasoner()
        
        // Initialize TTS for spoken results
        nkuTTS = NkuTTS(this)
        nkuTTS.initialize()
        
        // Initialize MediaPipe face detection
        faceDetectorHelper = FaceDetectorHelper(this)
        faceDetectorHelper.initializeDetector()
        faceDetectorHelper.initializeLandmarker()
        
        // Initialize MedGemma engine
        nkuEngine = NkuInferenceEngine(this)
        
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
                nkuTTS = nkuTTS,
                faceDetectorHelper = faceDetectorHelper,
                nkuEngine = nkuEngine
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        nkuTTS.shutdown()
        faceDetectorHelper.close()
        // F-8: Release camera executor and clear processor buffers
        rppgProcessor.reset()
        pallorDetector.reset()
        edemaDetector.reset()
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
    nkuTTS: NkuTTS,
    faceDetectorHelper: FaceDetectorHelper,
    nkuEngine: NkuInferenceEngine
) {
    val thermalStatus by thermalManager.thermalStatus.collectAsState()
    val rppgResult by rppgProcessor.result.collectAsState()
    val pallorResult by pallorDetector.result.collectAsState()
    val edemaResult by edemaDetector.result.collectAsState()
    val vitalSigns by sensorFusion.vitalSigns.collectAsState()
    val assessment by clinicalReasoner.assessment.collectAsState()
    val ttsState by nkuTTS.state.collectAsState()
    val engineState by nkuEngine.state.collectAsState()
    val engineProgress by nkuEngine.progress.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPregnant by remember { mutableStateOf(false) }
    var gestationalWeeks by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en") }
    val strings = LocalizedStrings.forLanguage(selectedLanguage)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { NkuDatabase.getInstance(context) }
    val screeningDao = remember { db.screeningDao() }
    val screeningCount by screeningDao.getCount().collectAsState(initial = 0)
    
    val tabs = listOf(strings.tabHome, strings.tabCardio, strings.tabAnemia, strings.tabPreE, strings.tabTriage)
    
    // Dark medical theme (F-10: extracted to NkuTheme.kt)
    NkuTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text("Nku Sentinel", fontWeight = FontWeight.Bold) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NkuColors.Surface
                    ),
                    actions = {
                        // Thermal status kept invisible to user (avoid confusion with body temp)
                        // ThermalManager still gates inference via canRun in the background
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = NkuColors.Surface) {
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
                            colors = listOf(NkuColors.Background, NkuColors.Surface)
                        )
                    )
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        rppgResult = rppgResult,
                        pallorResult = pallorResult,
                        edemaResult = edemaResult,
                        strings = strings,
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { selectedLanguage = it },
                        onNavigateToTab = { selectedTab = it },
                        savedScreeningCount = screeningCount,
                        onExportData = {
                            scope.launch {
                                val screenings = screeningDao.getAllScreeningsSnapshot()
                                if (screenings.isNotEmpty()) {
                                    val (_, intent) = com.nku.app.data.ScreeningExporter.exportToCsv(context, screenings)
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                    1 -> CardioScreen(
                        rppgResult = rppgResult,
                        rppgProcessor = rppgProcessor,
                        canRun = thermalStatus.safe,
                        faceDetectorHelper = faceDetectorHelper,
                        strings = strings
                    )
                    2 -> AnemiaScreen(
                        pallorResult = pallorResult,
                        pallorDetector = pallorDetector,
                        strings = strings
                    )
                    3 -> PreeclampsiaScreen(
                        edemaResult = edemaResult,
                        edemaDetector = edemaDetector,
                        faceDetectorHelper = faceDetectorHelper,
                        isPregnant = isPregnant,
                        gestationalWeeks = gestationalWeeks,
                        strings = strings,
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
                        nkuTTS = nkuTTS,
                        ttsState = ttsState,
                        engineState = engineState,
                        engineProgress = engineProgress,
                        selectedLanguage = selectedLanguage,  // F-11 fix
                        strings = strings,
                        onRunTriage = {
                            sensorFusion.updateVitalSigns()
                            val currentVitals = sensorFusion.vitalSigns.value
                            if (nkuEngine.areModelsReady()) {
                                // MedGemma available — run full Nku Cycle
                                scope.launch {
                                    val prompt = clinicalReasoner.generatePrompt(currentVitals)
                                    val result = nkuEngine.runNkuCycle(
                                        patientInput = prompt,
                                        language = selectedLanguage,
                                        thermalManager = thermalManager
                                    )
                                    clinicalReasoner.parseMedGemmaResponse(
                                        result.clinicalResponse, currentVitals
                                    )
                                    // Auto-save screening
                                    screeningDao.insert(ScreeningEntity(
                                        heartRateBpm = rppgResult.bpm?.toFloat(),
                                        heartRateConfidence = rppgResult.confidence,
                                        pallorSeverity = pallorResult.severity.name,
                                        edemaSeverity = edemaResult.severity.name,
                                        triageLevel = result.clinicalResponse.take(200),
                                        language = selectedLanguage,
                                        isPregnant = isPregnant,
                                        gestationalWeeks = gestationalWeeks.toIntOrNull()
                                    ))
                                }
                            } else {
                                // Models not sideloaded — use rule-based triage
                                clinicalReasoner.createRuleBasedAssessment(currentVitals)
                                // Auto-save screening
                                scope.launch {
                                    screeningDao.insert(ScreeningEntity(
                                        heartRateBpm = rppgResult.bpm?.toFloat(),
                                        heartRateConfidence = rppgResult.confidence,
                                        pallorSeverity = pallorResult.severity.name,
                                        edemaSeverity = edemaResult.severity.name,
                                        triageLevel = "Rule-based",
                                        language = selectedLanguage,
                                        isPregnant = isPregnant,
                                        gestationalWeeks = gestationalWeeks.toIntOrNull()
                                    ))
                                }
                            }
                        }
                    )
                }
            }
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
    enableAnalysis: Boolean = false,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    enableTorch: Boolean = false
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
