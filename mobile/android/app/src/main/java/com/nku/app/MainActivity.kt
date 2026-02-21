package com.nku.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
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
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executors
import com.nku.app.ui.NkuTheme
import com.nku.app.ui.NkuColors
import com.nku.app.utils.MemoryManager
import com.nku.app.screens.*

/**
 * MainActivity - Nku Sentinel
 * 
 * Complete on-device medical screening:
 * - Cardio Check (rPPG heart rate)
 * - Anemia Screening (HSV pallor detection)
 * - Jaundice Screening (scleral icterus detection)
 * - Preeclampsia Screening (edema geometry analysis)
 * - Respiratory/TB Screening (HeAR cough analysis)
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
    private val jaundiceDetector = JaundiceDetector()
    private val edemaDetector = EdemaDetector()
    private val respiratoryDetector by lazy { RespiratoryDetector(applicationContext) }
    
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
        val onCreateStartMs = SystemClock.elapsedRealtime()
        
        // Initialize components
        thermalManager = ThermalManager(this)
        sensorFusion = SensorFusion(rppgProcessor, pallorDetector, jaundiceDetector, edemaDetector, respiratoryDetector)
        clinicalReasoner = ClinicalReasoner()
        
        // Initialize TTS for spoken results
        nkuTTS = NkuTTS(this)
        nkuTTS.initialize()
        
        // Initialize MediaPipe helper.
        // Models are lazily initialized on first use to avoid blocking cold start.
        faceDetectorHelper = FaceDetectorHelper(this)
        
        // Initialize MedGemma engine
        nkuEngine = NkuInferenceEngine(this)
        
        // C-01 fix: extract models from assets on first launch
        lifecycleScope.launch {
            nkuEngine.extractModelsFromAssets()
        }
        
        // Request camera + audio permissions
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Permissions result handled — camera & mic
        }
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
        
        Log.i("NkuPerf", "Cold-start: onCreate init took ${SystemClock.elapsedRealtime() - onCreateStartMs}ms")

        setContent {
            NkuSentinelApp(
                thermalManager = thermalManager,
                rppgProcessor = rppgProcessor,
                pallorDetector = pallorDetector,
                jaundiceDetector = jaundiceDetector,
                edemaDetector = edemaDetector,
                respiratoryDetector = respiratoryDetector,
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
        jaundiceDetector.reset()
        edemaDetector.reset()
        respiratoryDetector.close()
        respiratoryDetector.reset()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NkuSentinelApp(
    thermalManager: ThermalManager,
    rppgProcessor: RPPGProcessor,
    pallorDetector: PallorDetector,
    jaundiceDetector: JaundiceDetector,
    edemaDetector: EdemaDetector,
    respiratoryDetector: RespiratoryDetector,
    sensorFusion: SensorFusion,
    clinicalReasoner: ClinicalReasoner,
    nkuTTS: NkuTTS,
    faceDetectorHelper: FaceDetectorHelper,
    nkuEngine: NkuInferenceEngine
) {
    val thermalStatus by thermalManager.thermalStatus.collectAsState()
    val rppgResult by rppgProcessor.result.collectAsState()
    val pallorResult by pallorDetector.result.collectAsState()
    val jaundiceResult by jaundiceDetector.result.collectAsState()
    val edemaResult by edemaDetector.result.collectAsState()
    val respiratoryResult by respiratoryDetector.result.collectAsState()
    val vitalSigns by sensorFusion.vitalSigns.collectAsState()
    val assessment by clinicalReasoner.assessment.collectAsState()
    val ttsState by nkuTTS.state.collectAsState()
    val engineState by nkuEngine.state.collectAsState()
    val engineProgress by nkuEngine.progress.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPregnant by remember { mutableStateOf(false) }
    var gestationalWeeks by remember { mutableStateOf("") }
    
    var showLowMemoryDialog by remember { mutableStateOf(false) }
    var pendingTriagePrompt by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val appSettingsStore = remember { AppSettingsStore(context) }
    val currentTheme by appSettingsStore.themePreference.collectAsState(initial = ThemePreference.SYSTEM)
    val selectedLanguage by appSettingsStore.languagePreference.collectAsState(initial = "en")
    
    val strings = LocalizedStrings.forLanguage(selectedLanguage)
    val scope = rememberCoroutineScope()
    
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (currentTheme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemDark
    }

    val tabs = listOf(strings.tabHome, strings.tabCardio, strings.tabAnemia, strings.tabJaundice, strings.tabPreE, strings.tabRespiratory, strings.tabTriage, strings.tabSettings)
    
    NkuTheme(isDarkTheme = isDarkTheme) {
        if (showLowMemoryDialog) {
            AlertDialog(
                onDismissRequest = { showLowMemoryDialog = false },
                title = { Text("⚠ Low Memory Warning", fontWeight = FontWeight.Bold) },
                text = { Text("Your phone's RAM is currently running low due to background apps. This may cause the Nku Reasoning AI to crash. \n\nPlease close other apps to run the AI, or continue using standard WHO guidelines.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLowMemoryDialog = false
                        val vitals = sensorFusion.vitalSigns.value
                        scope.launch { clinicalReasoner.createRuleBasedAssessment(vitals) }
                    }) {
                        Text("Use WHO Guidelines")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLowMemoryDialog = false
                        val prompt = pendingTriagePrompt
                        if (prompt != null) {
                            val vitals = sensorFusion.vitalSigns.value
                            scope.launch {
                                val medGemmaResponse = nkuEngine.runMedGemmaOnly(prompt)
                                if (medGemmaResponse != null) {
                                    clinicalReasoner.parseMedGemmaResponse(medGemmaResponse, vitals)
                                } else {
                                    clinicalReasoner.createRuleBasedAssessment(vitals)
                                }
                            }
                        }
                    }) {
                        Text("Force Load AI")
                    }
                },
                containerColor = NkuColors.Surface,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                textContentColor = MaterialTheme.colorScheme.onBackground
            )
        }
    
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
                                        3 -> Icons.Default.Visibility
                                        4 -> Icons.Default.Warning
                                        5 -> Icons.Default.Mic
                                        6 -> Icons.Default.CheckCircle
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = title
                                )
                            },
                            label = { Text(title, fontSize = 9.sp, maxLines = 1, softWrap = false) },
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
                        jaundiceResult = jaundiceResult,
                        edemaResult = edemaResult,
                        respiratoryResult = respiratoryResult,
                        strings = strings,
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { newLang ->
                            scope.launch { appSettingsStore.saveLanguagePreference(newLang) } 
                        },
                        onNavigateToTab = { selectedTab = it },
                        engineState = engineState,
                        engineProgress = engineProgress,
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
                    3 -> JaundiceScreen(
                        jaundiceResult = jaundiceResult,
                        jaundiceDetector = jaundiceDetector,
                        strings = strings
                    )
                    4 -> PreeclampsiaScreen(
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
                    5 -> RespiratoryScreen(
                        respiratoryResult = respiratoryResult,
                        respiratoryDetector = respiratoryDetector,
                        strings = strings
                    )
                    6 -> TriageScreen(
                        vitalSigns = vitalSigns,
                        rppgResult = rppgResult,
                        pallorResult = pallorResult,
                        jaundiceResult = jaundiceResult,
                        edemaResult = edemaResult,
                        respiratoryResult = respiratoryResult,
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
                            val prompt = clinicalReasoner.generatePrompt(currentVitals)

                            // F-03: Thermal gate — route to WHO/IMCI if device is overheating
                            if (thermalStatus.temperatureCelsius > 42f) {
                                scope.launch { clinicalReasoner.createRuleBasedAssessment(currentVitals) }
                            } else if (!MemoryManager.isRamAvailableForMedGemma(context)) {
                                pendingTriagePrompt = prompt
                                showLowMemoryDialog = true
                            } else {
                                scope.launch {
                                    val medGemmaResponse = nkuEngine.runMedGemmaOnly(prompt)
                                    if (medGemmaResponse != null) {
                                        clinicalReasoner.parseMedGemmaResponse(medGemmaResponse, currentVitals)
                                    } else {
                                        clinicalReasoner.createRuleBasedAssessment(currentVitals)
                                    }
                                }
                            }
                        }
                    )
                    7 -> SettingsScreen(
                        strings = strings,
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { newLang ->
                            scope.launch { appSettingsStore.saveLanguagePreference(newLang) }
                        },
                        currentTheme = currentTheme,
                        onThemeChange = { newTheme ->
                            scope.launch { appSettingsStore.saveThemePreference(newTheme) }
                        }
                    )
                }
            }
        }
    }
}
