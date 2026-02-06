package com.nku.app

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        thermalManager = ThermalManager(this)
        sensorFusion = SensorFusion(rppgProcessor, pallorDetector, edemaDetector)
        clinicalReasoner = ClinicalReasoner()
        
        // Request camera permission
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // Camera permission result handled
        }
        permissionLauncher.launch(Manifest.permission.CAMERA)
        
        setContent {
            NkuSentinelApp(
                thermalManager = thermalManager,
                rppgProcessor = rppgProcessor,
                pallorDetector = pallorDetector,
                edemaDetector = edemaDetector,
                sensorFusion = sensorFusion,
                clinicalReasoner = clinicalReasoner
            )
        }
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
    clinicalReasoner: ClinicalReasoner
) {
    val thermalStatus by thermalManager.thermalStatus.collectAsState()
    val rppgResult by rppgProcessor.result.collectAsState()
    val pallorResult by pallorDetector.result.collectAsState()
    val edemaResult by edemaDetector.result.collectAsState()
    val vitalSigns by sensorFusion.vitalSigns.collectAsState()
    val assessment by clinicalReasoner.assessment.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPregnant by remember { mutableStateOf(false) }
    var gestationalWeeks by remember { mutableStateOf("") }
    
    val tabs = listOf("Home", "Cardio", "Anemia", "Preeclampsia", "Triage")
    
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
                        // Thermal indicator
                        val tempColor = if (thermalStatus.safe) Color.Green else Color.Red
                        Text(
                            "${thermalStatus.temperatureCelsius.toInt()}C",
                            color = tempColor,
                            modifier = Modifier.padding(end = 16.dp)
                        )
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
                    0 -> HomeScreen(vitalSigns, sensorFusion)
                    1 -> CardioScreen(rppgResult, thermalStatus.safe)
                    2 -> AnemiaScreen(pallorResult)
                    3 -> PreeclampsiaScreen(
                        edemaResult = edemaResult,
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
                        assessment = assessment,
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

@Composable
fun HomeScreen(vitalSigns: VitalSigns, sensorFusion: SensorFusion) {
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
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Status cards
        VitalCard(
            title = "Heart Rate",
            value = vitalSigns.heartRateBpm?.let { "${it.toInt()} BPM" } ?: "--",
            status = when {
                vitalSigns.heartRateBpm == null -> "Not measured"
                vitalSigns.heartRateBpm!! > 100 -> "Elevated"
                vitalSigns.heartRateBpm!! < 60 -> "Low"
                else -> "Normal"
            },
            color = when {
                vitalSigns.heartRateBpm == null -> Color.Gray
                vitalSigns.heartRateBpm!! > 100 || vitalSigns.heartRateBpm!! < 50 -> Color(0xFFFF5722)
                else -> Color(0xFF4CAF50)
            }
        )
        
        Spacer(Modifier.height(12.dp))
        
        VitalCard(
            title = "Anemia Screen",
            value = vitalSigns.pallorSeverity?.name ?: "--",
            status = when (vitalSigns.pallorSeverity) {
                PallorSeverity.NORMAL -> "No pallor detected"
                PallorSeverity.MILD -> "Mild pallor - monitor"
                PallorSeverity.MODERATE -> "Moderate - check hemoglobin"
                PallorSeverity.SEVERE -> "Severe - urgent referral"
                null -> "Not screened"
            },
            color = when (vitalSigns.pallorSeverity) {
                PallorSeverity.NORMAL -> Color(0xFF4CAF50)
                PallorSeverity.MILD -> Color(0xFFFFEB3B)
                PallorSeverity.MODERATE -> Color(0xFFFF9800)
                PallorSeverity.SEVERE -> Color(0xFFFF5722)
                null -> Color.Gray
            }
        )
        
        Spacer(Modifier.height(12.dp))
        
        VitalCard(
            title = "Preeclampsia Screen",
            value = vitalSigns.edemaSeverity?.name ?: "--",
            status = when (vitalSigns.edemaSeverity) {
                EdemaSeverity.NORMAL -> "No facial swelling"
                EdemaSeverity.MILD -> "Mild swelling - monitor BP"
                EdemaSeverity.MODERATE -> "Check BP and urine"
                EdemaSeverity.SIGNIFICANT -> "Urgent evaluation needed"
                null -> "Not screened"
            },
            color = when (vitalSigns.edemaSeverity) {
                EdemaSeverity.NORMAL -> Color(0xFF4CAF50)
                EdemaSeverity.MILD -> Color(0xFFFFEB3B)
                EdemaSeverity.MODERATE -> Color(0xFFFF9800)
                EdemaSeverity.SIGNIFICANT -> Color(0xFFFF5722)
                null -> Color.Gray
            }
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Disclaimer
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "This is an AI-assisted screening tool. Always consult a healthcare professional for diagnosis and treatment.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun VitalCard(title: String, value: String, status: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = Color.Gray)
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(status, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CardioScreen(rppgResult: RPPGResult, canRun: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cardio Check", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Heart rate via camera (rPPG)", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(32.dp))
        
        // Heart rate display
        Text(
            rppgResult.bpm?.let { "${it.toInt()}" } ?: "--",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Text("BPM", fontSize = 18.sp, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        Text("Signal: ${rppgResult.signalQuality}", color = Color.Gray)
        Text("Confidence: ${(rppgResult.confidence * 100).toInt()}%", color = Color.Gray)
        
        Spacer(Modifier.height(32.dp))
        
        if (!canRun) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722).copy(alpha = 0.2f))) {
                Text(
                    "Device cooling down - AI paused",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFFF5722)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Point camera at your face in good lighting.\nHold still for 10 seconds.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AnemiaScreen(pallorResult: PallorResult) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Anemia Screening", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Conjunctival pallor detection", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(24.dp))
        
        // Pallor score
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
        
        Spacer(Modifier.height(24.dp))
        
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
        
        Spacer(Modifier.height(24.dp))
        
        // Conjunctiva guidance
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
                    "4. Hold steady for 2-3 seconds",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Conjunctival analysis works across all skin tones",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PreeclampsiaScreen(
    edemaResult: EdemaResult,
    isPregnant: Boolean,
    gestationalWeeks: String,
    onPregnancyChange: (Boolean, String) -> Unit
) {
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
        
        Spacer(Modifier.height(24.dp))
        
        // Edema result
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
        
        edemaResult.periorbitalScore?.let {
            Text("Periorbital: ${(it * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
        }
        
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
        
        Spacer(Modifier.height(16.dp))
        
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

@Composable
fun TriageScreen(
    vitalSigns: VitalSigns,
    assessment: ClinicalAssessment?,
    onRunTriage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Clinical Triage", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("AI-assisted severity assessment", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onRunTriage,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Run Triage Assessment")
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
