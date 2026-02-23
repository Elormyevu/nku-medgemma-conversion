package com.nku.app

/**
 * LocalizedStrings — Pan-African Language Support (46 Languages)
 *
 * Provides UI strings and clinical text in 46 African languages.
 * Tier 1 (14 languages): Full clinical vocabulary with native UI strings
 * Tier 2 (32 languages): Language name listed in the selector, but UI falls back
 *   to English strings. L-01: Tier 2 entries display "(English UI)" suffix so CHWs
 *   understand the limitation.
 *
 * Usage:
 *   val strings = LocalizedStrings.forLanguage("ee")  // Ewe
 *   Text(strings.screenTitle)
 */
object LocalizedStrings {

    // All supported language codes
    val supportedLanguages: Map<String, String> = mapOf(
        // Tier 1: Clinically Verified (14) — full native UI strings
        "en" to "English",
        "fr" to "French",
        "sw" to "Swahili",
        "ha" to "Hausa",
        "yo" to "Yoruba",
        "ig" to "Igbo",
        "am" to "Amharic",
        "ee" to "Ewe",
        "ak" to "Twi (Akan)",
        "wo" to "Wolof",
        "zu" to "Zulu",
        "xh" to "Xhosa",
        "om" to "Oromo",
        "ti" to "Tigrinya",
        // Tier 2: UI falls back to English (32) — M-01 fix: explicit labeling
        "af" to "Afrikaans (English UI)",
        "bm" to "Bambara (English UI)",
        "ny" to "Chichewa (English UI)",
        "din" to "Dinka (English UI)",
        "ff" to "Fula (English UI)",
        "gaa" to "Ga (English UI)",
        "ki" to "Kikuyu (English UI)",
        "rw" to "Kinyarwanda (English UI)",
        "kg" to "Kongo (English UI)",
        "ln" to "Lingala (English UI)",
        "luo" to "Luo (English UI)",
        "lg" to "Luganda (English UI)",
        "mg" to "Malagasy (English UI)",
        "nd" to "Ndebele (English UI)",
        "nus" to "Nuer (English UI)",
        "pcm" to "Pidgin (Nigerian) (English UI)",
        "wes" to "Pidgin (Cameroonian) (English UI)",
        "rn" to "Rundi (English UI)",
        "st" to "Sesotho (English UI)",
        "sn" to "Shona (English UI)",
        "so" to "Somali (English UI)",
        "tn" to "Tswana (English UI)",
        "pt" to "Portuguese (English UI)",
        "ar" to "Arabic (English UI)",
        "ts" to "Tsonga (English UI)",
        "ve" to "Venda (English UI)",
        "ss" to "Swati (English UI)",
        "nso" to "Northern Sotho (English UI)",
        "bem" to "Bemba (English UI)",
        "tum" to "Tumbuka (English UI)",
        "lua" to "Luba-Kasai (English UI)",
        "kj" to "Kuanyama (English UI)"
    )

    /**
     * Get language name from code (for translation prompts).
     * Handles Twi/Akan aliases: 'twi', 'tw', 'akan' all map to 'ak' (Akan).
     */
    fun getLanguageName(code: String): String {
        val normalized = normalizeLangCode(code)
        return supportedLanguages[normalized] ?: "Unknown"
    }

    /**
     * Normalize language codes — Twi=Akan aliasing.
     * Cloud backend uses 'twi', Android/ML Kit uses 'ak'.
     */
    private fun normalizeLangCode(code: String): String = when (code.lowercase()) {
        "twi", "tw", "akan" -> "ak"
        else -> code
    }

    /**
     * Get UI strings for a given language.
     */
    fun forLanguage(code: String): UiStrings = when (normalizeLangCode(code)) {
        "ee" -> eweStrings
        "fr" -> frenchStrings
        "sw" -> swahiliStrings
        "ha" -> hausaStrings
        "yo" -> yorubaStrings
        "ig" -> igboStrings
        "am" -> amharicStrings
        "ak" -> twiStrings
        "wo" -> wolofStrings
        "zu" -> zuluStrings
        "xh" -> xhosaStrings
        "om" -> oromoStrings
        "ti" -> tigrinyaStrings
        "af" -> afrikaansStrings
        "bm" -> bambaraStrings
        "ny" -> chichewaStrings
        "din" -> dinkaStrings
        "ff" -> fulaStrings
        "gaa" -> gaStrings
        "ki" -> kikuyuStrings
        "rw" -> kinyarwandaStrings
        "kg" -> kongoStrings
        "ln" -> lingalaStrings
        "luo" -> luoStrings
        "lg" -> lugandaStrings
        "mg" -> malagasyStrings
        "nd" -> ndebeleStrings
        "nus" -> nuerStrings
        "pcm" -> pidginNgStrings
        "wes" -> pidginCmStrings
        "rn" -> rundiStrings
        "st" -> sesothoStrings
        "sn" -> shonaStrings
        "so" -> somaliStrings
        "tn" -> tswanaStrings
        "pt" -> portugueseStrings
        "ar" -> arabicStrings
        "ts" -> tsongaStrings
        "ve" -> vendaStrings
        "ss" -> swatiStrings
        "nso" -> northernSothoStrings
        "bem" -> bembaStrings
        "tum" -> tumbukaStrings
        "lua" -> lubaKasaiStrings
        "kj" -> kuanyamaStrings
        else -> englishStrings
    }

    /**
     * UI string container for all user-facing text.
     */
    data class UiStrings(
        // App chrome
        val appTitle: String = "Nku Sentinel",
        val appSubtitle: String = "Camera-based vital signs screening",

        // Tab labels
        val tabHome: String = "Home",
        val tabCardio: String = "Cardio",
        val tabAnemia: String = "Anemia",
        val tabPreE: String = "Pre-E",
        val tabJaundice: String = "Jaundice",
        val tabRespiratory: String = "Resp",
        val tabTriage: String = "Triage",
        val tabSettings: String = "Settings",

        // Home screen
        val heartRate: String = "Heart Rate",
        val anemiaScreen: String = "Anemia Screen",
        val jaundiceScreen: String = "Jaundice Screen",
        val preeclampsiaScreen: String = "Preeclampsia Screen",
        val respiratoryScreen: String = "Respiratory Screen",
        val goToTab: String = "Go to %s tab to measure",
        val notYetScreened: String = "Not yet screened",

        // Cardio
        val cardioTitle: String = "Cardio Check",
        val cardioSubtitle: String = "Heart rate via camera",
        val startMeasurement: String = "Start Measurement",
        val stopMeasurement: String = "Stop Measurement",
        val holdStill: String = "Hold still for 10 seconds",
        val bpm: String = "BPM",

        // Anemia
        val anemiaTitle: String = "Anemia Screening",
        val anemiaSubtitle: String = "Conjunctival pallor detection",
        val captureConjunctiva: String = "Capture Conjunctiva",
        val pullDownEyelid: String = "Gently pull down the lower eyelid",
        val pointAtConjunctiva: String = "Point camera at the inner surface",
        val ensureLighting: String = "Ensure good lighting",
        val tapAnalyze: String = "Tap Analyze when image is clear",
        val worksAllSkinTones: String = "Works across all skin tones",

        // Jaundice
        val jaundiceTitle: String = "Jaundice Screening",
        val jaundiceSubtitle: String = "Scleral icterus detection",
        val captureSclera: String = "Capture Eye (Sclera)",
        val pointAtSclera: String = "Point camera at the white of the eye",
        val jaundiceScoreLabel: String = "Jaundice Score",
        val tapToCaptureEye: String = "Tap here to capture eye",
        val noJaundice: String = "✓ No jaundice detected",
        val mildJaundice: String = "Mild yellowing — check liver function",
        val moderateJaundice: String = "⚠ Moderate — get liver function test",
        val severeJaundice: String = "🚨 Severe — urgent referral",
        val jaundiceInstructions: String = "1. Ask the patient to look up or to the side\n" +
            "2. Point the rear camera at the white of the eye (sclera)\n" +
            "3. Ensure good lighting (daylight preferred)\n" +
            "4. Tap \"Analyze\" when the image is clear",

        // Preeclampsia
        val preETitle: String = "Preeclampsia Screen",
        val preESubtitle: String = "Facial edema detection",
        val captureFace: String = "Capture Face",
        val pregnant: String = "Pregnant?",
        val gestationalWeeks: String = "Gestational weeks",
        val centerFace: String = "Center your face",

        // Respiratory
        val respiratoryTitle: String = "Respiratory Screen",
        val respiratorySubtitle: String = "TB/respiratory screening via cough analysis",
        val startRecording: String = "Start Recording",
        val stopRecording: String = "Stop Recording",
        val recording: String = "Recording…",
        val tapToRecordCough: String = "Tap here to record cough",
        val respiratoryNormal: String = "✓ No respiratory concerns",
        val respiratoryLowRisk: String = "Low risk — monitor symptoms",
        val respiratoryModerateRisk: String = "⚠ Moderate — refer for testing",
        val respiratoryHighRisk: String = "🚨 High risk — urgent TB referral",
        val respiratoryInstructions: String = "1. Ask the patient to cough 3 times into the microphone\n" +
            "2. Hold the phone 15-30 cm from the patient's mouth\n" +
            "3. Tap \"Start Recording\" and record for 5 seconds\n" +
            "4. Ensure a quiet environment for best results",
        val coughsDetected: String = "Coughs detected",
        val audioQualityLabel: String = "Audio quality",
        val micPermissionTitle: String = "⚠ Microphone permission required",
        val micPermissionMessage: String = "Respiratory screening needs microphone access. Please enable in Settings.",
        val poweredByHeAR: String = "Powered by HeAR",
        val hearDescription: String = "Health Acoustic Representations — Google's audio foundation model pre-trained on 300M+ health audio clips for respiratory screening.",


        // Triage
        val triageTitle: String = "Clinical Triage",
        val triageSubtitle: String = "AI-assisted severity assessment",
        val dataAvailable: String = "Data Available",
        val notDone: String = "Not done",
        val runTriage: String = "Run Triage Assessment",
        val noDataWarning: String = "No screening data collected yet. Go to other tabs first to capture vital signs.",
        val translationUnavailableWarning: String = "Translation not available for this language — triage will use English.",
        val lowConfidenceNote: String = "Some measurements have low confidence and may be excluded from AI analysis.",

        // Clinical terms
        val normal: String = "Normal",
        val mild: String = "Mild",
        val moderate: String = "Moderate",
        val severe: String = "Severe",
        val elevated: String = "Elevated",
        val low: String = "Low",

        // Actions
        val analyze: String = "Analyze",
        val cancel: String = "Cancel",
        val recapture: String = "Re-capture",
        val resetReading: String = "Reset Reading",
        val howTo: String = "How to Capture",

        // F-UI-1: Formerly hardcoded strings now localized
        val language: String = "Language",
        val howItWorks: String = "How it works",
        val howToCapture: String = "How to Capture",
        val captureForEdema: String = "Capture a photo to check for facial edema",
        val centerFaceKeepNeutral: String = "Center your face, keep neutral expression",
        val riskFactors: String = "Risk Factors",
        val recommendationsTitle: String = "Recommendation",
        val screeningData: String = "Screening Data",

        // Instruction card steps (F-1 fix)
        val cardioInstructions: String = "1. Tap \"Start Measurement\" above\n" +
            "2. Place fingertip over the back camera\n" +
            "3. The flashlight turns on automatically\n" +
            "4. Hold still for 10 seconds\n" +
            "5. Heart rate appears when the buffer fills",
        val anemiaInstructions: String = "1. Gently pull down the patient's lower eyelid\n" +
            "2. Point camera at the inner surface (conjunctiva)\n" +
            "3. Ensure good lighting (daylight preferred)\n" +
            "4. Tap \"Analyze\" when the image is clear",

        // Progress text (F-2 fix)
        val screeningsProgress: String = "%d of 5 screenings complete",
        val readyForTriage: String = "✓ Ready for triage — go to Triage tab",
        val followSteps: String = "Follow the steps below to screen a patient",

        // Step card prompts (HCD: tappable cards)
        val tapToMeasureHR: String = "Tap here to measure heart rate",
        val tapToCaptureEyelid: String = "Tap here to capture eyelid",
        val tapToCaptureFace: String = "Tap here to capture face",

        // Step card clinical status
        val hrElevated: String = "⚠ Elevated — may indicate stress or anemia",
        val hrLow: String = "⚠ Low — monitor closely",
        val hrNormal: String = "✓ Within normal range",
        val noPallor: String = "✓ No pallor detected",
        val mildPallor: String = "Mild pallor — monitor weekly",
        val moderatePallor: String = "⚠ Moderate — get hemoglobin test",
        val severePallor: String = "🚨 Severe — urgent referral",
        val noSwelling: String = "✓ No facial swelling",
        val mildSwelling: String = "Mild swelling — check blood pressure",
        val moderateSwelling: String = "⚠ Check BP and urine protein",
        val significantSwelling: String = "🚨 Urgent evaluation needed",

        // Triage data labels (F-3 fix)
        val swellingCheck: String = "Swelling Check",

        // Symptom input labels
        val patientSymptoms: String = "Vital Signs & Patient-Reported Symptoms",
        val micOrType: String = "Type or tap the mic to speak",
        val micPermissionRequired: String = "⚠ Microphone permission required for voice input. Please enable in Settings.",

        // Safety
        val disclaimer: String = "This is an AI-assisted screening tool. Always consult a healthcare professional for diagnosis and treatment.",
        val deviceCooling: String = "Device cooling down — AI paused",

        // Camera permission (F-CAM fix)
        val cameraPermissionTitle: String = "⚠ Camera permission required",
        val cameraPermissionCardio: String = "Heart rate measurement needs camera access. Please enable in Settings.",
        val cameraPermissionAnemia: String = "Anemia screening needs camera access. Please enable in Settings.",
        val openSettings: String = "Open Settings",

        // Data export
        val exportData: String = "Export Screening Data",

        // L-01 fix: Previously hardcoded English strings now localized
        val cameraPermissionPreE: String = "Preeclampsia screening needs camera access. Please enable in Settings.",
        val cameraPermissionJaundice: String = "Jaundice screening needs camera access. Please enable in Settings.",
        val loadingAiModel: String = "Loading AI model…",
        val translatingToEnglish: String = "Translating to English…",
        val medgemmaAnalyzing: String = "MedGemma analyzing…",
        val translatingResult: String = "Translating result…",
        val errorOccurred: String = "Error occurred",
        val processing: String = "Processing…",
        val primaryConcerns: String = "Primary Concerns",
        val savedScreenings: String = "💾 %d screening(s) saved",
        val stopLabel: String = "Stop",
        val listenLabel: String = "🔊 Listen",

        // I-1 fix: Previously hardcoded strings in screen composables
        val signalLabel: String = "Signal",
        val confidenceLabel: String = "Confidence",
        val pallorScoreLabel: String = "Pallor Score",
        val edemaScoreLabel: String = "Edema Score",
        val periorbitalLabel: String = "Periorbital",
        val severityLabel: String = "Severity",
        val urgencyLabel: String = "Urgency",
        val voiceInput: String = "Voice input",
        val addSymptom: String = "Add symptom",
        val symptomPlaceholder: String = "e.g. headache, dizziness...",
        val listeningPrompt: String = "🎤 Listening... speak now",
        val geometryInstructions: String = "Uses geometry-based analysis (facial proportions). Works across all skin tones. Best with photos in consistent lighting.",

        // L-01 fix: Localize remaining hardcoded English strings
        // Signal quality display values (used in CardioScreen)
        val signalInsufficient: String = "Insufficient",
        val signalPoor: String = "Poor",
        val signalGood: String = "Good",
        val signalExcellent: String = "Excellent",
        val bufferLabel: String = "Buffer",
        val removeLabel: String = "Remove",

        // Severity display names (used in TriageScreen)
        val severityLow: String = "Low",
        val severityMedium: String = "Medium",
        val severityHigh: String = "High",
        val severityCritical: String = "Critical",

        // Urgency display names
        val urgencyRoutine: String = "Routine",
        val urgencyWithinWeek: String = "Within 1 week",
        val urgencyWithin48h: String = "Within 48 hours",
        val urgencyImmediate: String = "Immediate",

        // Triage category names
        val triageGreen: String = "Green",
        val triageYellow: String = "Yellow",
        val triageOrange: String = "Orange",
        val triageRed: String = "Red",

        // TTS section headers
        val ttsConcerns: String = "Concerns",
        val ttsRecommendations: String = "Recommendations",

        // OBS-1: Loading spinner during analysis
        val analyzing: String = "Analyzing…",

        // OBS-3: Rear camera usage hints (CHW workflow)
        val rearCameraHintAnemia: String = "📷 Uses rear camera — point at patient's lower eyelid",
        val rearCameraHintFace: String = "📷 Uses rear camera — point at patient's face",
        val rearCameraHintCardio: String = "📷 Uses rear camera — place patient's fingertip over lens",
        val rearCameraHintJaundice: String = "📷 Uses rear camera — point at white of patient's eye",

        // USER-1: Theme toggle labels
        val themeLabel: String = "Theme",
        val themeLight: String = "Light",
        val themeDark: String = "Dark",
        val themeSystem: String = "System",

        // Fallback transparency banner (FT-1)
        val triageSourceAI: String = "AI-Assisted Triage (MedGemma)",
        val triageSourceGuideline: String = "Guideline-Based Triage",
        val fallbackExplanation: String = "AI model not available. Results use WHO/IMCI clinical guidelines \u2014 safe and validated.",
        val fallbackRecoveryTip: String = "To restore AI: close background apps or restart Nku.",
        val lowConfidenceWarning: String = "\u26A0 Low confidence \u2014 this reading may be excluded from triage. Re-capture in better lighting.",
        
        // Cloud Translation Warning
        val internetRequiredTitle: String = "Internet Required",
        val internetRequiredMessage: String = "This language requires an internet connection for translation. Clinical reasoning will still run offline.",
        val continueLabel: String = "Continue",

        // Model download banner (DL-1)
        val downloadingModel: String = "Downloading MedGemma…",
        val downloadSlowWarning: String = "The app may be slower while the AI model downloads. You can still use the screening tools.",
        val downloadFailedWarning: String = "The AI model could not be downloaded. Triage will use rule-based assessment until resolved.",
        val notEnoughStorage: String = "Not enough storage",
        val validatingModel: String = "Validating model integrity…",

        // Auto-stop completion feedback (UX-1)
        val dataSavedForTriage: String = "✓ Data saved for triage",
        val measurementComplete: String = "Measurement complete",

        // Engine progress messages (i18n-1)
        val retryDownload: String = "Retry Download",
        val downloadRetrying: String = "Retrying download (attempt %d/%d)…",
        val downloadRetryingIn: String = "Download failed. Retrying in %ds… (attempt %d/%d)",
        val downloadFailedFull: String = "Model download failed. Connect to Wi-Fi and restart the app.",
        val connectingToDownload: String = "Connecting to download MedGemma...",
        val notEnoughStorageFull: String = "Not enough storage (%dMB free). Free up space and restart.",
        val downloadingProgress: String = "Downloading MedGemma… %d%% (%dMB / %dMB)",
        val extractingModel: String = "Extracting %s…",
        val extractingModelPct: String = "Extracting %s… %d%%",
        val loadingModelPct: String = "Loading %s… %d%%",
        val assessmentComplete: String = "Assessment complete",
        val lowMemoryTitle: String = "⚠ Low Memory Warning",
        val lowMemoryMessage: String = "Your phone's RAM is currently running low due to background apps. This may cause the Nku Reasoning AI to crash. \n\nPlease close other apps to run the AI, or continue using standard WHO guidelines.",
        val useWhoGuidelines: String = "Use WHO Guidelines",
        val forceLoadAi: String = "Force Load AI",
        val retryingLoad: String = "Retrying load (attempt %d/%d)...",
        val analyzingSymptoms: String = "Analyzing symptoms… (this may take 30-60s)",
        val analyzingSymptomsSec: String = "Analyzing symptoms… %ds elapsed",
        val translatingSymptoms: String = "Translating symptoms to English...",
        val translatingResultOnDevice: String = "Translating result (ML Kit, on-device)...",
        val translatingResultUnavailable: String = "On-device result translation unavailable — returning English result...",
        val integrityCheckFailed: String = "Model download failed integrity check. Will retry on next launch."
    ) {
        /** Map signal quality string to localized display name. */
        fun localizedSignalQuality(quality: String): String = when (quality) {
            "excellent" -> signalExcellent
            "good" -> signalGood
            "poor" -> signalPoor
            else -> signalInsufficient
        }

        /** Map Severity enum to localized display name. */
        fun localizedSeverity(severity: Severity): String = when (severity) {
            Severity.LOW -> severityLow
            Severity.MEDIUM -> severityMedium
            Severity.HIGH -> severityHigh
            Severity.CRITICAL -> severityCritical
        }

        /** Map PallorSeverity enum to localized display name. */
        fun localizedSeverity(severity: PallorSeverity): String = when (severity) {
            PallorSeverity.NORMAL -> normal
            PallorSeverity.MILD -> mild
            PallorSeverity.MODERATE -> moderate
            PallorSeverity.SEVERE -> severe
        }

        /** Map JaundiceSeverity enum to localized display name. */
        fun localizedSeverity(severity: JaundiceSeverity): String = when (severity) {
            JaundiceSeverity.NORMAL -> normal
            JaundiceSeverity.MILD -> mild
            JaundiceSeverity.MODERATE -> moderate
            JaundiceSeverity.SEVERE -> severe
        }

        /** Map EdemaSeverity enum to localized display name. */
        fun localizedSeverity(severity: EdemaSeverity): String = when (severity) {
            EdemaSeverity.NORMAL -> normal
            EdemaSeverity.MILD -> mild
            EdemaSeverity.MODERATE -> moderate
            EdemaSeverity.SIGNIFICANT -> severe
        }

        /** Map Urgency enum to localized display name. */
        fun localizedUrgency(urgency: Urgency): String = when (urgency) {
            Urgency.ROUTINE -> urgencyRoutine
            Urgency.WITHIN_WEEK -> urgencyWithinWeek
            Urgency.WITHIN_48_HOURS -> urgencyWithin48h
            Urgency.IMMEDIATE -> urgencyImmediate
        }

        /** Map TriageCategory enum to localized display name. */
        fun localizedTriageCategory(category: TriageCategory): String = when (category) {
            TriageCategory.GREEN -> triageGreen
            TriageCategory.YELLOW -> triageYellow
            TriageCategory.ORANGE -> triageOrange
            TriageCategory.RED -> triageRed
        }

        /** Map RespiratoryRisk enum to localized display name. */
        fun localizedRespiratoryClassification(classification: RespiratoryRisk): String = when (classification) {
            RespiratoryRisk.NORMAL -> normal
            RespiratoryRisk.LOW_RISK -> respiratoryLowRisk
            RespiratoryRisk.MODERATE_RISK -> respiratoryModerateRisk
            RespiratoryRisk.HIGH_RISK -> respiratoryHighRisk
        }
    }

    // ─── Tier 1 Languages ───────────────────────────────────────

    val englishStrings = UiStrings()  // Default

    val eweStrings = UiStrings(
        appSubtitle = "Kamera dzi gbugbɔgbalẽ ƒe nukpɔkpɔ",
        tabHome = "Aƒeme",
        tabCardio = "Dzi",
        tabAnemia = "Ʋu",
        tabTriage = "Kpɔkpɔ",
        tabSettings = "Ɖoɖowo",
        heartRate = "Dzi ƒe ɖoɖo",
        anemiaScreen = "Ʋu kpɔkpɔ",
        notYetScreened = "Womekpɔe haɖe o",
        cardioTitle = "Dzi Kpɔkpɔ",
        cardioSubtitle = "Dzi ƒe ɖoɖo le kamera dzi",
        startMeasurement = "Dze egɔme",
        stopMeasurement = "Etsɔ asi le eŋu",
        holdStill = "Nànɔ anyi kpɔ tsã 10",
        bpm = "BPM",
        anemiaTitle = "Ʋu Kpɔkpɔ",
        captureConjunctiva = "Tsɔ ŋku ƒe foto",
        pullDownEyelid = "Dɔ ŋkuƒometi sia dzi blewuu",
        ensureLighting = "Kpɔ be kekeli li",
        worksAllSkinTones = "Ewɔ dɔ na anyigba ƒe amewo katã",
        preETitle = "Futɔ Kpɔkpɔ",
        captureFace = "Tsɔ nkume ƒe foto",
        pregnant = "Efufu le ŋuwò?",
        triageTitle = "Klinikla Kpɔkpɔ",
        noDataWarning = "Woɖu data aɖeke haɖe o. Yi tabwo din bubuwo me gbã.",
        normal = "Dedie",
        mild = "Vĩe tɔ",
        moderate = "Titina",
        severe = "Vevie",
        analyze = "Dzraɖoƒe",
        cancel = "Ɖuƒe",
        howTo = "Alesi nàwɔe",
        disclaimer = "Elime kpɔkpɔ dɔwɔnu enye. Fia ɖe dɔkta gɔme hafi nàwɔ nane.",
        cameraPermissionPreE = "Futɔ kpɔkpɔ hia kamera. Ɖe edzi le Ɖoɖowo me.",
        loadingAiModel = "AI ƒe dɔwɔnu le dzadzram…",
        translatingToEnglish = "Ɖe eŋlisigbe me dzi…",
        medgemmaAnalyzing = "MedGemma le kpɔkpɔ wɔm…",
        translatingResult = "Ðe gbe me dzi…",
        errorOccurred = "Vodada aɖe dzɔ",
        processing = "Le dɔ wɔm…",
        primaryConcerns = "Nuŋlɔɖiwo tiatia",
        savedScreenings = "💾 Kpɔkpɔ %d wotsɔ axa",
        stopLabel = "Etsɔ asi le eŋu",
        listenLabel = "🔊 Ɖo to",
        signalLabel = "Dzesi",
        confidenceLabel = "Ŋuɖoɖo",
        pallorScoreLabel = "Ŋku ƒe xɔxɔ",
        edemaScoreLabel = "Fuflu ƒe xɔxɔ",
        periorbitalLabel = "Ŋku ƒe ŋgɔ",
        severityLabel = "Sesẽ",
        urgencyLabel = "Kpekpeame",
        voiceInput = "Gbe ƒe nyaŋuɖoɖo",
        addSymptom = "Tsɔ dɔlele kpe ɖe eŋu",
        symptomPlaceholder = "ke.n. ta ƒe veve, akpɔme…",
        listeningPrompt = "🎤 Le toto sem… ƒo nu fifia",
        geometryInstructions = "Ezãa nkume ƒe ɖoɖo kpɔkpɔ. Ewɔ dɔ na anyigba ƒe amewo katã. Enyo wu le kekeli maɖe la te.",
        signalInsufficient = "Mese o",
        signalPoor = "Menya o",
        signalGood = "Enyo",
        signalExcellent = "Nyui ŋutɔ",
        bufferLabel = "Agbalẽ",
        removeLabel = "Ɖe asi le eŋu",
        severityLow = "Kpui",
        severityMedium = "Titina",
        severityHigh = "Lolo",
        severityCritical = "Vevie ŋutɔ",
        urgencyRoutine = "Gbɔdonuma",
        urgencyWithinWeek = "Le kɔsiɖa 1 me",
        urgencyWithin48h = "Le gaƒoƒo 48 me",
        urgencyImmediate = "Fifia",
        triageGreen = "Gbemɔ",
        triageYellow = "Akpɔ",
        triageOrange = "Aŋɔ̃",
        triageRed = "Dzĩ",
        ttsConcerns = "Nusiwo le enu",
        ttsRecommendations = "Kpɔɖeŋuwo",
        analyzing = "Le dzraɖoƒe wɔm…",
        rearCameraHintAnemia = "📷 Kamera megbea — tso ŋku ƒe te",
        rearCameraHintFace = "📷 Kamera megbea — tso nkume",
        rearCameraHintCardio = "📷 Kamera megbea — ɖo alɔ ɖeka ŋu",
        themeLabel = "Nuŋɔŋlɔ",
        themeLight = "Kekeli",
        themeDark = "Viviti",
        themeSystem = "Mɔfiame",
        triageSourceAI = "AI Kpɔkpɔ (MedGemma)",
        triageSourceGuideline = "Alɔdza Kpɔkpɔ",
        fallbackExplanation = "AI ƒe dɔwɔnu meli o. Esiwo wotsɔ WHO/IMCI ƒe alɔdza — ewɔ dɔ nyuie.",
        fallbackRecoveryTip = "Nàtrɔ AI: tsɔ dɔwɔnu bubuwo ɖa le megbe alo gbugbɔ Nku.",
        lowConfidenceWarning = "\u26A0 Ŋuɖoɖo me ga o — nusi wotsoe ƒe axa makpɔ ɖe kpɔkpɔ me o. Tsɔ foto bubu le kekeli nyui me.",

        jaundiceScreen = "Anyidi kpɔkpɔ",
        preeclampsiaScreen = "Futɔ kpɔkpɔ",
        respiratoryScreen = "Gbɔmefafa kpɔkpɔ",
        tapToCaptureEyelid = "Tso ŋku ƒe foto",
        tapToCaptureFace = "Tso nkume ƒe foto",
        tapToCaptureEye = "Tso ŋku ƒe foto",
        tapToRecordCough = "Matsa nan don tsɔ kɔ ƒe gbe",
        tapToMeasureHR = "Matsa nan don auna dzi ƒe ɖoɖo",
        screeningsProgress = "%d le 5 me wotsɔ",
        readyForTriage = "✓ Ewɔ dɔ — yi Kpɔkpɔ ƒe tab",
        followSteps = "Ɖo mɔ siwo le ete don kpɔkpɔ",
        hrElevated = "⚠ Ega — aɖe nye damuwa alo ʋu",
        hrLow = "⚠ Edzidzi — lé kpɔ nyuie",
        hrNormal = "✓ Enɔ dedie me",
        noPallor = "✓ Womekpɔ farar ido o",
        noSwelling = "✓ Kumburi aɖeke meli o",
        downloadingModel = "MedGemma dzadzram…",
        downloadSlowWarning = "App ateŋu anɔ blewu ke AI ƒe dɔwɔnu le dzadzram. Àteŋu azã kpɔkpɔ dɔwɔnuwo.",
        downloadFailedWarning = "Wometeŋu wɔ AI ƒe dɔwɔnu dzadzram o. Kpɔkpɔ azã alɔdzawo.",
        dataSavedForTriage = "✓ Wotsɔ data da na kpɔkpɔ",
        measurementComplete = "Kpɔkpɔ vɔ",
        retryDownload = "Gbugbɔ dzadzram",
        downloadRetrying = "Le dzadzram (%d/%d)…",
        downloadRetryingIn = "Emekpɔ o. Agbugbɔ le %ds me… (%d/%d)",
        downloadFailedFull = "Dzadzram mekpɔ o. Tsɔ Wi-Fi ɖe edzi eye nàgbugbɔ.",
        connectingToDownload = "Le MedGemma dzadzram…",
        notEnoughStorageFull = "Teƒe mesɔ o (%dMB kpɔ). Ɖe nusiwo le enu.",
        downloadingProgress = "MedGemma dzadzram… %d%% (%dMB / %dMB)",
        extractingModel = "%s ƒe ɖeɖe…",
        extractingModelPct = "%s ƒe ɖeɖe… %d%%",
        loadingModelPct = "%s dzadzram… %d%%",
        assessmentComplete = "Kpɔkpɔ vɔ",
        lowMemoryTitle = "⚠ Nuxexe mesɔ o",
        lowMemoryMessage = "Fɔn ƒe nuxexe mele o. AI ateŋu atsri.\n\nTsɔ dɔwɔnu bubuwo ɖa alo zã OMS ƒe alɔdza.",
        useWhoGuidelines = "OMS ƒe alɔdza",
        forceLoadAi = "AI ƒe dɔ",
        retryingLoad = "Le dzadzram (%d/%d)...",
        analyzingSymptoms = "Le kpɔkpɔ wɔm… (30-60s)",
        analyzingSymptomsSec = "Le kpɔkpɔ wɔm… %ds",
        translatingSymptoms = "Le eŋlisigbe me ɖem…",
        translatingResultOnDevice = "Le gbe me ɖem (ML Kit)…",
        translatingResultUnavailable = "Gbe ɖeɖe meli o — eŋlisi me…",
        integrityCheckFailed = "Dzadzram mekpɔ ŋuɖoɖo o. Agbugbɔ."
    )

    val frenchStrings = UiStrings(
        appSubtitle = "Dépistage des signes vitaux par caméra",
        tabHome = "Accueil",
        tabTriage = "Triage",
        tabSettings = "Paramètres",
        heartRate = "Fréquence cardiaque",
        anemiaScreen = "Dépistage anémie",
        notYetScreened = "Pas encore dépisté",
        cardioTitle = "Bilan Cardiaque",
        cardioSubtitle = "Fréquence cardiaque par caméra",
        startMeasurement = "Démarrer la mesure",
        stopMeasurement = "Arrêter la mesure",
        holdStill = "Restez immobile 10 secondes",
        anemiaTitle = "Dépistage Anémie",
        captureConjunctiva = "Capturer la conjonctive",
        pullDownEyelid = "Tirez doucement la paupière inférieure",
        ensureLighting = "Assurez un bon éclairage",
        worksAllSkinTones = "Fonctionne sur tous les tons de peau",
        preETitle = "Dépistage Prééclampsie",
        captureFace = "Capturer le visage",
        pregnant = "Enceinte ?",
        gestationalWeeks = "Semaines de grossesse",
        triageTitle = "Triage Clinique",
        noDataWarning = "Aucune donnée collectée. Allez d'abord aux autres onglets.",
        normal = "Normal",
        mild = "Léger",
        moderate = "Modéré",
        severe = "Sévère",
        analyze = "Analyser",
        cancel = "Annuler",
        howTo = "Comment capturer",
        disclaimer = "Outil de dépistage assisté par IA. Consultez toujours un professionnel de santé.",
        cameraPermissionPreE = "Le dépistage de la prééclampsie nécessite la caméra. Activez dans Paramètres.",
        loadingAiModel = "Chargement du modèle IA…",
        translatingToEnglish = "Traduction en anglais…",
        medgemmaAnalyzing = "MedGemma analyse…",
        translatingResult = "Traduction du résultat…",
        errorOccurred = "Une erreur s'est produite",
        processing = "Traitement…",
        primaryConcerns = "Préoccupations principales",
        savedScreenings = "💾 %d dépistage(s) enregistré(s)",
        stopLabel = "Arrêter",
        listenLabel = "🔊 Écouter",
        signalLabel = "Signal",
        confidenceLabel = "Confiance",
        pallorScoreLabel = "Score de pâleur",
        edemaScoreLabel = "Score d'œdème",
        periorbitalLabel = "Périorbitaire",
        severityLabel = "Gravité",
        urgencyLabel = "Urgence",
        voiceInput = "Saisie vocale",
        addSymptom = "Ajouter un symptôme",
        symptomPlaceholder = "ex. maux de tête, vertiges…",
        listeningPrompt = "🎤 Écoute en cours… parlez maintenant",
        geometryInstructions = "Utilise l'analyse géométrique (proportions faciales). Fonctionne sur tous les tons de peau. Meilleur avec un éclairage constant.",
        signalInsufficient = "Insuffisant",
        signalPoor = "Faible",
        signalGood = "Bon",
        signalExcellent = "Excellent",
        bufferLabel = "Tampon",
        removeLabel = "Supprimer",
        severityLow = "Faible",
        severityMedium = "Moyen",
        severityHigh = "Élevé",
        severityCritical = "Critique",
        urgencyRoutine = "Routine",
        urgencyWithinWeek = "Sous 1 semaine",
        urgencyWithin48h = "Sous 48 heures",
        urgencyImmediate = "Immédiat",
        triageGreen = "Vert",
        triageYellow = "Jaune",
        triageOrange = "Orange",
        triageRed = "Rouge",
        ttsConcerns = "Préoccupations",
        ttsRecommendations = "Recommandations",
        analyzing = "Analyse en cours…",
        rearCameraHintAnemia = "📷 Caméra arrière — pointez vers la paupière du patient",
        rearCameraHintFace = "📷 Caméra arrière — pointez vers le visage du patient",
        rearCameraHintCardio = "📷 Caméra arrière — placez le doigt du patient sur l'objectif",
        themeLabel = "Thème",
        themeLight = "Clair",
        themeDark = "Sombre",
        themeSystem = "Système",
        triageSourceAI = "Triage assisté par IA (MedGemma)",
        triageSourceGuideline = "Triage basé sur les lignes directrices",
        fallbackExplanation = "Modèle IA indisponible. Les résultats suivent les lignes directrices cliniques OMS/PCIME — sûres et validées.",
        fallbackRecoveryTip = "Pour restaurer l'IA : fermez les applications en arrière-plan ou redémarrez Nku.",
        lowConfidenceWarning = "\u26A0 Confiance faible \u2014 cette mesure pourrait être exclue du triage. Recapturez avec un meilleur éclairage.",

        // Missing HomeScreen translations
        jaundiceScreen = "Dépistage ictère",
        preeclampsiaScreen = "Dépistage prééclampsie",
        respiratoryScreen = "Dépistage respiratoire",
        goToTab = "Aller à l'onglet %s pour mesurer",
        anemiaSubtitle = "Détection de la pâleur",
        preESubtitle = "Détection de l'œdème facial",
        pointAtConjunctiva = "Pointez la caméra vers l'intérieur de la paupière",
        tapAnalyze = "Appuyez sur \"Analyser\" quand l'image est claire",
        centerFace = "Centrez le visage",
        triageSubtitle = "Évaluation assistée par IA",
        dataAvailable = "Données disponibles",
        notDone = "Non effectué",
        runTriage = "Lancer le triage",
        elevated = "Élevé",
        low = "Bas",
        recapture = "Recapturer",
        resetReading = "Réinitialiser",
        language = "Langue",
        howItWorks = "Comment ça marche",
        howToCapture = "Comment capturer",
        captureForEdema = "Prenez une photo pour vérifier l'œdème facial",
        centerFaceKeepNeutral = "Centrez le visage, gardez une expression neutre",
        riskFactors = "Facteurs de risque",
        recommendationsTitle = "Recommandation",
        screeningData = "Données de dépistage",
        cardioInstructions = "1. Appuyez sur \"Démarrer la mesure\" ci-dessus\n" +
            "2. Placez le bout du doigt sur la caméra arrière\n" +
            "3. Le flash s'allume automatiquement\n" +
            "4. Restez immobile pendant 10 secondes\n" +
            "5. La fréquence cardiaque apparaît quand le tampon est plein",
        anemiaInstructions = "1. Tirez doucement la paupière inférieure du patient\n" +
            "2. Pointez la caméra vers la surface intérieure (conjonctive)\n" +
            "3. Assurez un bon éclairage (lumière naturelle de préférence)\n" +
            "4. Appuyez sur \"Analyser\" quand l'image est claire",
        screeningsProgress = "%d dépistages sur 5 terminés",
        readyForTriage = "✓ Prêt pour le triage — allez à l'onglet Triage",
        followSteps = "Suivez les étapes ci-dessous pour dépister un patient",
        tapToMeasureHR = "Appuyez pour mesurer la fréquence cardiaque",
        tapToCaptureEyelid = "Appuyez pour capturer la paupière",
        tapToCaptureFace = "Appuyez pour capturer le visage",
        tapToCaptureEye = "Appuyez pour capturer l'œil",
        tapToRecordCough = "Appuyez pour enregistrer la toux",
        hrElevated = "⚠ Élevée — peut indiquer du stress ou une anémie",
        hrLow = "⚠ Basse — surveiller de près",
        hrNormal = "✓ Dans les limites normales",
        noPallor = "✓ Pas de pâleur détectée",
        mildPallor = "Pâleur légère — surveiller chaque semaine",
        moderatePallor = "⚠ Modérée — faire un test d'hémoglobine",
        severePallor = "🚨 Sévère — orientation urgente",
        noSwelling = "✓ Pas de gonflement facial",
        mildSwelling = "Gonflement léger — vérifier la tension",
        moderateSwelling = "⚠ Vérifier la tension et les protéines urinaires",
        significantSwelling = "🚨 Évaluation urgente nécessaire",
        swellingCheck = "Vérification du gonflement",
        patientSymptoms = "Signes vitaux et symptômes rapportés",
        micOrType = "Tapez ou appuyez sur le micro pour parler",
        micPermissionRequired = "⚠ Permission du microphone requise. Veuillez activer dans Paramètres.",
        deviceCooling = "Appareil en refroidissement — IA en pause",
        cameraPermissionTitle = "⚠ Permission caméra requise",
        cameraPermissionCardio = "La mesure cardiaque nécessite la caméra. Veuillez activer dans Paramètres.",
        cameraPermissionAnemia = "Le dépistage de l'anémie nécessite la caméra. Veuillez activer dans Paramètres.",
        openSettings = "Ouvrir les Paramètres",
        exportData = "Exporter les données de dépistage",
        cameraPermissionJaundice = "Le dépistage de l'ictère nécessite la caméra. Veuillez activer dans Paramètres.",
        respiratoryTitle = "Dépistage Respiratoire",
        respiratorySubtitle = "Analyse de la toux par IA",
        startRecording = "Démarrer l'enregistrement",
        stopRecording = "Arrêter l'enregistrement",
        respiratoryNormal = "✓ Normal",
        respiratoryLowRisk = "Risque faible",
        respiratoryModerateRisk = "⚠ Risque modéré",
        respiratoryHighRisk = "🚨 Risque élevé",
        respiratoryInstructions = "1. Demandez au patient de tousser 3 fois dans le micro\n" +
            "2. Tenez le téléphone à 15-30 cm de la bouche\n" +
            "3. Appuyez sur \"Démarrer\" et enregistrez 5 secondes\n" +
            "4. Assurez un environnement calme",
        coughsDetected = "Toux détectées",
        audioQualityLabel = "Qualité audio",
        micPermissionTitle = "⚠ Permission microphone requise",
        micPermissionMessage = "Le dépistage respiratoire nécessite l'accès au microphone. Veuillez activer dans Paramètres.",
        poweredByHeAR = "Propulsé par HeAR",
        hearDescription = "Health Acoustic Representations — modèle audio de Google pré-entraîné sur plus de 300M de clips audio de santé.",
        rearCameraHintJaundice = "📷 Caméra arrière — pointez vers le blanc de l'œil du patient",
        internetRequiredTitle = "Connexion Internet requise",
        internetRequiredMessage = "Cette langue nécessite une connexion Internet pour la traduction. Le raisonnement clinique fonctionne hors ligne.",
        continueLabel = "Continuer",

        // Download banner
        downloadingModel = "Téléchargement de MedGemma…",
        downloadSlowWarning = "L'application peut être plus lente pendant le téléchargement du modèle IA. Vous pouvez utiliser les outils de dépistage.",
        downloadFailedWarning = "Le modèle IA n'a pas pu être téléchargé. Le triage utilisera l'évaluation par règles.",
        notEnoughStorage = "Stockage insuffisant",
        validatingModel = "Vérification de l'intégrité du modèle…",
        dataSavedForTriage = "✓ Données enregistrées pour le triage",
        measurementComplete = "Mesure terminée",
        retryDownload = "Réessayer le téléchargement",
        downloadRetrying = "Nouvelle tentative (%d/%d)…",
        downloadRetryingIn = "Échec. Nouvelle tentative dans %ds… (%d/%d)",
        downloadFailedFull = "Échec du téléchargement. Connectez-vous au Wi-Fi et redémarrez.",
        connectingToDownload = "Connexion pour télécharger MedGemma...",
        notEnoughStorageFull = "Stockage insuffisant (%dMo libres). Libérez de l'espace.",
        downloadingProgress = "Téléchargement MedGemma… %d%% (%dMo / %dMo)",
        extractingModel = "Extraction de %s…",
        extractingModelPct = "Extraction de %s… %d%%",
        loadingModelPct = "Chargement de %s… %d%%",
        assessmentComplete = "Évaluation terminée",
        lowMemoryTitle = "⚠ Mémoire insuffisante",
        lowMemoryMessage = "La mémoire vive est faible. L'IA de raisonnement pourrait planter.\n\nFermez les applications en arrière-plan ou utilisez les directives OMS.",
        useWhoGuidelines = "Directives OMS",
        forceLoadAi = "Forcer l'IA",
        retryingLoad = "Rechargement (%d/%d)...",
        analyzingSymptoms = "Analyse des symptômes… (30-60s)",
        analyzingSymptomsSec = "Analyse des symptômes… %ds écoulées",
        translatingSymptoms = "Traduction des symptômes en anglais...",
        translatingResultOnDevice = "Traduction du résultat (ML Kit, sur l'appareil)...",
        translatingResultUnavailable = "Traduction sur l'appareil non disponible — résultat en anglais...",
        integrityCheckFailed = "Le modèle n'a pas passé la vérification. Réessai au prochain démarrage."
    )

    val swahiliStrings = UiStrings(
        appSubtitle = "Uchunguzi wa dalili za maisha kwa kamera",
        tabHome = "Nyumbani",
        tabTriage = "Hatua",
        tabSettings = "Mipangilio",
        heartRate = "Kiwango cha moyo",
        anemiaScreen = "Uchunguzi wa anemia",
        notYetScreened = "Bado haijachunguzwa",
        cardioTitle = "Uchunguzi wa Moyo",
        startMeasurement = "Anza Kupima",
        stopMeasurement = "Simamisha Kupima",
        holdStill = "Kaa kimya sekunde 10",
        anemiaTitle = "Uchunguzi wa Anemia",
        captureConjunctiva = "Chukua picha ya jicho",
        pullDownEyelid = "Vuta kope ya chini polepole",
        ensureLighting = "Hakikisha mwanga mzuri",
        worksAllSkinTones = "Inafanya kazi kwa rangi zote za ngozi",
        preETitle = "Uchunguzi wa Preeclampsia",
        captureFace = "Chukua picha ya uso",
        pregnant = "Mjamzito?",
        gestationalWeeks = "Wiki za ujauzito",
        triageTitle = "Hatua za Kliniki",
        noDataWarning = "Hakuna data iliyokusanywa bado. Nenda kwenye tabo nyingine kwanza.",
        normal = "Kawaida",
        mild = "Kidogo",
        moderate = "Wastani",
        severe = "Kali",
        analyze = "Changanua",
        cancel = "Ghairi",
        howTo = "Jinsi ya kuchukua",
        disclaimer = "Hii ni zana ya uchunguzi inayosaidiwa na AI. Wasiliana na mtaalamu wa afya kila wakati.",
        cameraPermissionPreE = "Uchunguzi wa preeclampsia unahitaji kamera. Tafadhali washa kwenye Mipangilio.",
        loadingAiModel = "Kupakia muundo wa AI…",
        translatingToEnglish = "Kutafsiri kwa Kiingereza…",
        medgemmaAnalyzing = "MedGemma inachambua…",
        translatingResult = "Kutafsiri matokeo…",
        errorOccurred = "Hitilafu imetokea",
        processing = "Inachakata…",
        primaryConcerns = "Wasiwasi Wakuu",
        savedScreenings = "💾 Uchunguzi %d umehifadhiwa",
        stopLabel = "Simamisha",
        listenLabel = "🔊 Sikiliza",
        signalLabel = "Ishara",
        confidenceLabel = "Uhakika",
        pallorScoreLabel = "Alama ya weupe",
        edemaScoreLabel = "Alama ya uvimbe",
        periorbitalLabel = "Karibu na jicho",
        severityLabel = "Ukali",
        urgencyLabel = "Haraka",
        voiceInput = "Ingizo la sauti",
        addSymptom = "Ongeza dalili",
        symptomPlaceholder = "mf. maumivu ya kichwa, kizunguzungu…",
        listeningPrompt = "🎤 Inasikiliza… sema sasa",
        geometryInstructions = "Inatumia uchambuzi wa jiometri (uwiano wa uso). Inafanya kazi kwa rangi zote za ngozi. Bora na picha katika mwanga thabiti.",
        signalInsufficient = "Haitoshi",
        signalPoor = "Duni",
        signalGood = "Nzuri",
        signalExcellent = "Bora",
        bufferLabel = "Kihifadhi",
        removeLabel = "Ondoa",
        severityLow = "Chini",
        severityMedium = "Wastani",
        severityHigh = "Juu",
        severityCritical = "Hatari sana",
        urgencyRoutine = "Kawaida",
        urgencyWithinWeek = "Ndani ya wiki 1",
        urgencyWithin48h = "Ndani ya saa 48",
        urgencyImmediate = "Mara moja",
        triageGreen = "Kijani",
        triageYellow = "Njano",
        triageOrange = "Machungwa",
        triageRed = "Nyekundu",
        ttsConcerns = "Wasiwasi",
        ttsRecommendations = "Mapendekezo",
        analyzing = "Inachambua…",
        rearCameraHintAnemia = "📷 Kamera ya nyuma — elekeza kwenye kope ya mgonjwa",
        rearCameraHintFace = "📷 Kamera ya nyuma — elekeza kwenye uso wa mgonjwa",
        rearCameraHintCardio = "📷 Kamera ya nyuma — weka kidole cha mgonjwa kwenye lenzi",
        themeLabel = "Mandhari",
        themeLight = "Angavu",
        themeDark = "Giza",
        themeSystem = "Mfumo",
        triageSourceAI = "Hatua za AI (MedGemma)",
        triageSourceGuideline = "Hatua za Miongozo",
        fallbackExplanation = "Muundo wa AI haupatikani. Matokeo yanafuata miongozo ya WHO/IMCI — salama na yaliyothibitishwa.",
        fallbackRecoveryTip = "Kurudisha AI: funga programu za nyuma au anzisha upya Nku.",
        lowConfidenceWarning = "\u26A0 Uhakika mdogo \u2014 usomaji huu unaweza kutengwa na hatua. Chukua picha tena katika mwanga bora.",

        jaundiceScreen = "Uchunguzi wa manjano",
        preeclampsiaScreen = "Uchunguzi wa preeclampsia",
        respiratoryScreen = "Uchunguzi wa kupumua",
        tapToCaptureEyelid = "Gusa hapa kupiga picha ya kope",
        tapToCaptureFace = "Gusa hapa kupiga picha ya uso",
        tapToCaptureEye = "Gusa hapa kupiga picha ya jicho",
        tapToRecordCough = "Gusa hapa kurekodi kikohozi",
        tapToMeasureHR = "Gusa hapa kupima mapigo ya moyo",
        screeningsProgress = "Uchunguzi %d kati ya 5 umekamilika",
        readyForTriage = "✓ Tayari kwa hatua — nenda kwenye tabo ya Hatua",
        followSteps = "Fuata hatua zilizo hapa chini kuchunguza mgonjwa",
        hrElevated = "⚠ Juu — inaweza kuonyesha msongo au upungufu wa damu",
        hrLow = "⚠ Chini — fuatilia kwa karibu",
        hrNormal = "✓ Katika kiwango cha kawaida",
        noPallor = "✓ Hakuna weupe uliogunduliwa",
        noSwelling = "✓ Hakuna uvimbe wa uso",
        downloadingModel = "Kupakua MedGemma…",
        downloadSlowWarning = "Programu inaweza kuwa polepole wakati muundo wa AI unapakua. Unaweza kutumia zana za uchunguzi.",
        downloadFailedWarning = "Muundo wa AI haukuweza kupakuliwa. Hatua zitatumia tathmini ya miongozo.",
        dataSavedForTriage = "✓ Data imehifadhiwa kwa hatua",
        measurementComplete = "Kipimo kimekamilika"

    )

    val hausaStrings = UiStrings(
        appSubtitle = "Nazarin alamomin lafiya ta kyamara",
        tabHome = "Gida",
        tabCardio = "Zuciya",
        tabAnemia = "Jini",
        tabPreE = "Ciki",
        tabTriage = "Bincike",
        tabSettings = "Saituna",
        heartRate = "Bugun zuciya",
        anemiaScreen = "Gwajin rashin jini",
        preeclampsiaScreen = "Gwajin hawan jini na ciki",
        goToTab = "Je zuwa shafi %s don auna",
        notYetScreened = "Ba a yi gwaji ba tukuna",
        cardioTitle = "Gwajin Zuciya",
        cardioSubtitle = "Bugun zuciya ta kyamara",
        startMeasurement = "Fara Auna",
        stopMeasurement = "Tsaya Auna",
        holdStill = "Ka zauna lafiya na daƙiƙa 10",
        bpm = "BPM",
        anemiaTitle = "Gwajin Rashin Jini",
        anemiaSubtitle = "Gano farar ido",
        captureConjunctiva = "Ɗauki hoton ido",
        pullDownEyelid = "A ja fatar ido ta ƙasa a hankali",
        pointAtConjunctiva = "Nuna kyamara zuwa ciki ido",
        ensureLighting = "Tabbatar haske ya yi kyau",
        tapAnalyze = "Matsa \"Bincika\" idan hoton ya bayyana",
        worksAllSkinTones = "Yana aiki da kowane launin fata",
        preETitle = "Gwajin Preeclampsia",
        preESubtitle = "Gano kumburin fuska",
        captureFace = "Ɗauki hoton fuska",
        pregnant = "Mai ciki?",
        gestationalWeeks = "Makonni na ciki",
        centerFace = "Sanya fuskar a tsakiya",
        triageTitle = "Binciken Asibiti",
        triageSubtitle = "Kimantawa ta AI",
        dataAvailable = "Bayanan da ake da su",
        notDone = "Ba a yi ba",
        runTriage = "Gudanar da Bincike",
        noDataWarning = "Ba a tattara bayanai ba tukuna. Je zuwa wasu shafuffuka da farko.",
        normal = "Al'ada",
        mild = "Ƙanƙanta",
        moderate = "Matsakaici",
        severe = "Mai tsanani",
        elevated = "Ya hauhawa",
        low = "Ƙasa",
        analyze = "Bincika",
        cancel = "Soke",
        recapture = "Sake ɗauka",
        howTo = "Yadda ake ɗauka",
        language = "Harshe",
        howItWorks = "Yadda yake aiki",
        howToCapture = "Yadda ake ɗauka",
        captureForEdema = "Ɗauki hoto don duba kumburin fuska",
        centerFaceKeepNeutral = "Sanya fuskar a tsakiya, ka riƙe hali na al'ada",
        riskFactors = "Abubuwan haɗari",
        recommendationsTitle = "Shawarwari",
        screeningData = "Bayanan gwaji",
        cardioInstructions = "1. Matsa \"Fara Auna\" a sama\n" +
            "2. Ɗora yatsa a kan kyamara na baya\n" +
            "3. Fitila zai haska kai tsaye\n" +
            "4. Ka zauna lafiya na daƙiƙa 10\n" +
            "5. Bugun zuciya zai bayyana idan ya cika",
        anemiaInstructions = "1. A ja fatar ido ta ƙasa a hankali\n" +
            "2. Nuna kyamara zuwa ciki ido\n" +
            "3. Tabbatar haske ya yi kyau\n" +
            "4. Matsa \"Bincika\" idan hoton ya bayyana",
        screeningsProgress = "%d cikin 5 gwaje-gwaje an kammala",
        readyForTriage = "✓ A shirye don bincike — je zuwa shafin Bincike",
        followSteps = "Bi matakai don gwada majiyyaci",
        tapToMeasureHR = "Matsa nan don auna bugun zuciya",
        tapToCaptureEyelid = "Matsa nan don ɗaukar hoton ido",
        tapToCaptureFace = "Matsa nan don ɗaukar hoton fuska",
        hrElevated = "⚠ Ya hauhawa — yana iya nuna damuwa ko rashin jini",
        hrLow = "⚠ Ƙasa — ka lura sosai",
        hrNormal = "✓ A cikin al'ada",
        noPallor = "✓ Ba a gano farar ido ba",
        mildPallor = "Farar ido kaɗan — lura a kowane mako",
        moderatePallor = "⚠ Matsakaici — yi gwajin hemoglobin",
        severePallor = "🚨 Mai tsanani — aika da gaggawa",
        noSwelling = "✓ Babu kumburin fuska",
        mildSwelling = "Kumburi kaɗan — duba hawan jini",
        moderateSwelling = "⚠ Duba hawan jini da fitsarin protein",
        significantSwelling = "🚨 Ana buƙatar kimantawa na gaggawa",
        swellingCheck = "Duba kumburi",
        patientSymptoms = "Alamomin da majiyyaci ya ba da rahoto",
        micOrType = "Rubuta ko matsa makirufo don yin magana",
        micPermissionRequired = "⚠ Ana buƙatar izinin makirufo. Don Allah a kunna a Saituna.",
        disclaimer = "Wannan kayan aikin bincike ne na AI. Koyaushe ka tuntuɓi likita.",
        deviceCooling = "Na'urar tana hucewa — AI ya tsaya",
        cameraPermissionTitle = "⚠ Ana buƙatar izinin kyamara",
        cameraPermissionCardio = "Gwajin bugun zuciya yana buƙatar kyamara. Don Allah a kunna a Saituna.",
        cameraPermissionAnemia = "Gwajin rashin jini yana buƙatar kyamara. Don Allah a kunna a Saituna.",
        openSettings = "Buɗe Saituna",
        exportData = "Fitar da bayanan bincike",
        cameraPermissionPreE = "Gwajin preeclampsia yana buƙatar kyamara. Don Allah a kunna a Saituna.",
        loadingAiModel = "Ana lodi tsarin AI…",
        translatingToEnglish = "Ana fassara zuwa Turanci…",
        medgemmaAnalyzing = "MedGemma yana bincike…",
        translatingResult = "Ana fassara sakamako…",
        errorOccurred = "Kuskure ya faru",
        processing = "Ana sarrafa…",
        primaryConcerns = "Manyan Damuwa",
        savedScreenings = "💾 An ajiye gwaje-gwaje %d",
        stopLabel = "Tsaya",
        listenLabel = "🔊 Saurara",
        signalLabel = "Sigina",
        confidenceLabel = "Tabbaci",
        pallorScoreLabel = "Makin farar ido",
        edemaScoreLabel = "Makin kumburi",
        periorbitalLabel = "Kewayen ido",
        severityLabel = "Tsanani",
        urgencyLabel = "Gaggawa",
        voiceInput = "Shigar da murya",
        addSymptom = "Ƙara alamar rashin lafiya",
        symptomPlaceholder = "misali ciwon kai, jiri…",
        listeningPrompt = "🎤 Yana saurara… yi magana yanzu",
        geometryInstructions = "Yana amfani da nazarin siffar fuska. Yana aiki da kowane launin fata. Ya fi kyau da hotuna a cikin haske daidai.",
        signalInsufficient = "Bai isa ba",
        signalPoor = "Mara kyau",
        signalGood = "Mai kyau",
        signalExcellent = "Nagari sosai",
        bufferLabel = "Ma'ajiya",
        removeLabel = "Cire",
        severityLow = "Ƙasa",
        severityMedium = "Matsakaici",
        severityHigh = "Babba",
        severityCritical = "Mai tsanani sosai",
        urgencyRoutine = "Na yau da kullum",
        urgencyWithinWeek = "A cikin mako 1",
        urgencyWithin48h = "A cikin awa 48",
        urgencyImmediate = "Nan da nan",
        triageGreen = "Kore",
        triageYellow = "Rawaya",
        triageOrange = "Ruwan lemu",
        triageRed = "Ja",
        ttsConcerns = "Damuwa",
        ttsRecommendations = "Shawarwari",
        analyzing = "Ana bincike…",
        rearCameraHintAnemia = "📷 Kyamara na baya — nuna zuwa fatar ido ta majiyyaci",
        rearCameraHintFace = "📷 Kyamara na baya — nuna zuwa fuskar majiyyaci",
        rearCameraHintCardio = "📷 Kyamara na baya — ɗora yatsar majiyyaci a kan lensi",
        themeLabel = "Jigo",
        themeLight = "Haske",
        themeDark = "Duhu",
        themeSystem = "Tsarin na'ura",
        triageSourceAI = "Bincike na AI (MedGemma)",
        triageSourceGuideline = "Bincike bisa ka'idoji",
        fallbackExplanation = "Ba a samu tsarin AI ba. Sakamakon yana bin ka'idojin asibiti na WHO/IMCI — mai aminci kuma tabbatacce.",
        fallbackRecoveryTip = "Don dawo da AI: rufe manhajar baya ko sake kunna Nku.",
        lowConfidenceWarning = "\u26A0 Tabbaci ya yi ƙasa \u2014 wannan sakamakon bazai shiga bincike ba. Sake ɗauka a haske mai kyau.",
        dataSavedForTriage = "✓ An ajiye bayanan don bincike",
        measurementComplete = "An gama auna"

    )

    val yorubaStrings = UiStrings(
        appSubtitle = "Ayẹwo àwọn àmì pàtàkì nípasẹ̀ kámẹ́rà",
        tabHome = "Ilé",
        tabCardio = "Ọkàn",
        tabAnemia = "Ẹ̀jẹ̀",
        tabPreE = "Oyún",
        tabTriage = "Àyẹ̀wò",
        heartRate = "Ìlù ọkàn",
        anemiaScreen = "Àyẹ̀wò ẹ̀jẹ̀",
        preeclampsiaScreen = "Àyẹ̀wò ìgbóná ẹ̀jẹ̀ oyún",
        goToTab = "Lọ sí ojú-ìwé %s láti wọ̀n",
        notYetScreened = "A kò tí ì ṣe àyẹ̀wò",
        cardioTitle = "Àyẹ̀wò Ọkàn",
        cardioSubtitle = "Ìlù ọkàn nípasẹ̀ kámẹ́rà",
        startMeasurement = "Bẹ̀rẹ̀ Wíwọ̀n",
        stopMeasurement = "Dúró Wíwọ̀n",
        holdStill = "Jókòó rẹ̀ fún ìṣẹ́jú-àáyá 10",
        bpm = "BPM",
        anemiaTitle = "Àyẹ̀wò Ẹ̀jẹ̀",
        anemiaSubtitle = "Wíwá ìfúnpá ojú",
        captureConjunctiva = "Ya àwòrán ojú",
        pullDownEyelid = "Fà ìpèníjà ojú sísàlẹ̀ díẹ̀díẹ̀",
        pointAtConjunctiva = "Tọ́ka kámẹ́rà sí ojú inú",
        ensureLighting = "Rí i dájú pé ìmọ́lẹ̀ dára",
        tapAnalyze = "Tẹ \"Ṣàyẹ̀wò\" nígbà tí àwòrán bá hàn gbangba",
        worksAllSkinTones = "Ó ṣiṣẹ́ fún gbogbo àwọ̀ ara",
        preETitle = "Àyẹ̀wò Preeclampsia",
        preESubtitle = "Wíwá wíwú ojú",
        captureFace = "Ya àwòrán ojú",
        pregnant = "Lóyún?",
        gestationalWeeks = "Ọ̀sẹ̀ oyún",
        centerFace = "Fi ojú sí àárín",
        triageTitle = "Àyẹ̀wò Ìlera",
        triageSubtitle = "Ìṣirò ìlera nípasẹ̀ AI",
        dataAvailable = "Dátà tó wà",
        notDone = "A kò tí ì ṣe",
        runTriage = "Ṣe Àyẹ̀wò Ìlera",
        noDataWarning = "Kò sí dátà tí a kó jọ. Lọ sí àwọn ojú-ìwé mìíràn lákọ̀ọ́kọ́.",
        normal = "Déédéé",
        mild = "Kékeré",
        moderate = "Àárín gbùngbùn",
        severe = "Líle",
        elevated = "Ga jù",
        low = "Kéré jù",
        analyze = "Ṣàyẹ̀wò",
        cancel = "Fagilé",
        recapture = "Tún ya",
        howTo = "Bí o ṣe lè ya",
        language = "Èdè",
        howItWorks = "Bí ó ṣe ń ṣiṣẹ́",
        howToCapture = "Bí o ṣe lè ya",
        captureForEdema = "Ya àwòrán láti ṣàyẹ̀wò wíwú ojú",
        centerFaceKeepNeutral = "Fi ojú sí àárín, má ṣe yí ojú",
        riskFactors = "Àwọn ohun ewu",
        recommendationsTitle = "Ìmọ̀ràn",
        screeningData = "Dátà àyẹ̀wò",
        cardioInstructions = "1. Tẹ \"Bẹ̀rẹ̀ Wíwọ̀n\" lókè\n" +
            "2. Fi ìka sí orí kámẹ́rà ẹ̀yìn\n" +
            "3. Àtùpà yóò tan fúnra rẹ̀\n" +
            "4. Jókòó rẹ̀ fún ìṣẹ́jú-àáyá 10\n" +
            "5. Ìlù ọkàn yóò hàn nígbà tí ó bá kún",
        anemiaInstructions = "1. Fà ìpèníjà ojú sísàlẹ̀ díẹ̀díẹ̀\n" +
            "2. Tọ́ka kámẹ́rà sí ojú inú\n" +
            "3. Rí i dájú pé ìmọ́lẹ̀ dára\n" +
            "4. Tẹ \"Ṣàyẹ̀wò\" nígbà tí àwòrán bá ṣe kedere",
        screeningsProgress = "%d nínú 3 àyẹ̀wò ti parí",
        readyForTriage = "✓ Ó ṣetán fún àyẹ̀wò — lọ sí ojú-ìwé Àyẹ̀wò",
        followSteps = "Tẹ̀lé àwọn ìgbésẹ̀ láti ṣàyẹ̀wò aláìsàn",
        tapToMeasureHR = "Tẹ ibí yìí láti wọ̀n ìlù ọkàn",
        tapToCaptureEyelid = "Tẹ ibí yìí láti ya àwòrán ojú",
        tapToCaptureFace = "Tẹ ibí yìí láti ya àwòrán ojú",
        hrElevated = "⚠ Ga jù — ó lè jẹ́ àmì ìpayà tàbí àìní ẹ̀jẹ̀",
        hrLow = "⚠ Kéré jù — ṣàkíyèsí dáadáa",
        hrNormal = "✓ Ó wà ní ìwọ̀n déédéé",
        noPallor = "✓ Kò sí ìfúnpá tí a rí",
        mildPallor = "Ìfúnpá díẹ̀ — ṣàkíyèsí lọ́ṣọọṣẹ",
        moderatePallor = "⚠ Àárín gbùngbùn — ṣe ìdánwò hemoglobin",
        severePallor = "🚨 Líle — ránṣẹ́ ní kíákíá",
        noSwelling = "✓ Kò sí wíwú ojú",
        mildSwelling = "Wíwú díẹ̀ — ṣàyẹ̀wò ìfúnpá ẹ̀jẹ̀",
        moderateSwelling = "⚠ Ṣàyẹ̀wò ìfúnpá ẹ̀jẹ̀ àti protein nínú ìtọ̀",
        significantSwelling = "🚨 Àyẹ̀wò ní kíákíá ni a nílò",
        swellingCheck = "Ṣàyẹ̀wò wíwú",
        patientSymptoms = "Àwọn àmì àìsàn tí aláìsàn sọ",
        micOrType = "Tẹ̀ tàbí tẹ maikirofóònù láti sọ àmì àìsàn",
        micPermissionRequired = "⚠ A nílò àṣẹ maikirofóònù. Jọ̀wọ́ mú ṣiṣẹ́ ní Ètò.",
        disclaimer = "Ohun èlò àyẹ̀wò AI ni èyí. Máa bá dókítà sọ̀rọ̀ nígbà gbogbo.",
        deviceCooling = "Ẹ̀rọ ń tutù — AI ti dúró",
        cameraPermissionTitle = "⚠ A nílò àṣẹ kámẹ́rà",
        cameraPermissionCardio = "Wíwọn ìlù ọkàn nílò kámẹ́rà. Jọ̀wọ́ mú ṣiṣẹ́ ní Ètò.",
        cameraPermissionAnemia = "Àyẹ̀wò ẹ̀jẹ̀ nílò kámẹ́rà. Jọ̀wọ́ mú ṣiṣẹ́ ní Ètò.",
        openSettings = "Ṣí Ètò",
        exportData = "Gbejàde dátà àyẹ̀wò",
        cameraPermissionPreE = "Àyẹ̀wò preeclampsia nílò kámẹ́rà. Jọ̀wọ́ mú ṣiṣẹ́ ní Ètò.",
        loadingAiModel = "Ń ṣí àwòṣe AI sílẹ̀…",
        translatingToEnglish = "Ń yí padà sí Gẹ̀ẹ́sì…",
        medgemmaAnalyzing = "MedGemma ń ṣàyẹ̀wò…",
        translatingResult = "Ń yí èsì padà…",
        errorOccurred = "Àṣìṣe ti ṣẹlẹ̀",
        processing = "Ń ṣe iṣẹ́…",
        primaryConcerns = "Àwọn Àníyàn Pàtàkì",
        savedScreenings = "💾 Àyẹ̀wò %d ti fipamọ́",
        stopLabel = "Dúró",
        listenLabel = "🔊 Gbọ́",
        signalLabel = "Àmì",
        confidenceLabel = "Ìgbàgbọ́",
        pallorScoreLabel = "Iye ìfúnpá",
        edemaScoreLabel = "Iye wíwú",
        periorbitalLabel = "Àyíká ojú",
        severityLabel = "Ìwọ̀n líle",
        urgencyLabel = "Ìkánjú",
        voiceInput = "Ohùn ìsọ̀rọ̀",
        addSymptom = "Fi àmì àìsàn kún",
        symptomPlaceholder = "àp. orí fífọ́, ìyípo…",
        listeningPrompt = "🎤 Ó ń tẹ́tí sí… sọ̀rọ̀ báyìí",
        geometryInstructions = "Ó ń lo àyẹ̀wò geometry (ìwọ̀n ojú). Ó ṣiṣẹ́ fún gbogbo àwọ̀ ara. Ó dára jù pẹ̀lú àwòrán nínú ìmọ́lẹ̀ kan náà.",
        signalInsufficient = "Kò tó",
        signalPoor = "Kò dára",
        signalGood = "Dára",
        signalExcellent = "Dára púpọ̀",
        bufferLabel = "Ìpamọ́",
        removeLabel = "Yọ kúrò",
        severityLow = "Kékeré",
        severityMedium = "Àárín",
        severityHigh = "Ga",
        severityCritical = "Pàtàkì jù",
        urgencyRoutine = "Déédéé",
        urgencyWithinWeek = "Nínú ọ̀sẹ̀ kan",
        urgencyWithin48h = "Nínú wákàtí 48",
        urgencyImmediate = "Lẹ́sẹ̀kẹsẹ̀",
        triageGreen = "Ewé",
        triageYellow = "Ìyẹ̀fun",
        triageOrange = "Ọsan",
        triageRed = "Pupa",
        ttsConcerns = "Àwọn àníyàn",
        ttsRecommendations = "Àwọn ìmọ̀ràn",
        analyzing = "Ń ṣàyẹ̀wò…",
        rearCameraHintAnemia = "📷 Kámẹ́rà ẹ̀yìn — tọ́ka sí ìpèníjà ojú aláìsàn",
        rearCameraHintFace = "📷 Kámẹ́rà ẹ̀yìn — tọ́ka sí ojú aláìsàn",
        rearCameraHintCardio = "📷 Kámẹ́rà ẹ̀yìn — fi ìka aláìsàn sí orí lẹ́nsì",
        themeLabel = "Àwòṣe",
        themeLight = "Ìmọ́lẹ̀",
        themeDark = "Òkùnkùn",
        themeSystem = "Ètò ẹ̀rọ",
        triageSourceAI = "Àyẹ̀wò AI (MedGemma)",
        triageSourceGuideline = "Àyẹ̀wò bí ìlànà ṣe sọ",
        fallbackExplanation = "Àwòṣe AI kò sí. Àbájáde tẹ̀lé àwọn ìlànà ìṣègùn WHO/IMCI — ó wà láàbò, ó sì jẹ́ ẹ̀rí.",
        fallbackRecoveryTip = "Láti mú AI padà: pa àwọn ohun èlò ẹ̀yìn tàbí tún Nku bẹ̀rẹ̀.",
        lowConfidenceWarning = "\u26A0 Ìgbàgbọ́ kéré jù \u2014 àbájáde yìí lè máa kòpà nínú àyẹ̀wò. Tún ya nínú ìmọ́lẹ̀ tó dára.",
        dataSavedForTriage = "✓ Dátà ti fipamọ́ fún àyẹ̀wò",
        measurementComplete = "Wíwọ̀n ti parí"

    )

    val igboStrings = UiStrings(
        appSubtitle = "Nlele ihe ọmụma site na kamera",
        tabHome = "Ụlọ",
        heartRate = "Ịgba obi",
        anemiaScreen = "Nlele ọbara",
        notYetScreened = "Elechabeghị ya",
        cardioTitle = "Nlele Obi",
        startMeasurement = "Malite Ọnụ Ọgụgụ",
        anemiaTitle = "Nlele Ọbara",
        captureConjunctiva = "Were foto anya",
        preETitle = "Nlele Preeclampsia",
        captureFace = "Were foto ihu",
        pregnant = "Dị ime?",
        triageTitle = "Nlele Ahụike",
        normal = "Nkịtị",
        mild = "Obere",
        moderate = "Etiti",
        severe = "Ike",
        analyze = "Nyochaa",
        cancel = "Kagbuo",
        disclaimer = "Ngwá ọrụ nlele AI bụ nke a. Jụrụ dọkịợta oge nịile.",
        cameraPermissionPreE = "Nlele preeclampsia chọrọ kamera. Biko mee ya na Ntọrị.",
        loadingAiModel = "Na-ebuli ùdị AI…",
        translatingToEnglish = "Na-asụgharị n'asusu Bekèe…",
        medgemmaAnalyzing = "MedGemma na-enyocha…",
        translatingResult = "Na-asụgharị nsopùta…",
        errorOccurred = "Mmerụ mere",
        processing = "Na-arụ ọ rụ…",
        primaryConcerns = "Ihe Na-Echè Gị",
        savedScreenings = "💾 E chekwara nlele %d",
        stopLabel = "Kwụsị",
        listenLabel = "🔊 Nụrị ntị",
        triageSourceAI = "Nlele AI (MedGemma)",
        triageSourceGuideline = "Nlele iwu ndụ",
        fallbackExplanation = "Ùdị AI adịghị. Nsoputara na-eso usoro WHO/IMCI — nchekwa ma enyochaala.",
        fallbackRecoveryTip = "Iji weghachi AI: mechie ngwa ndị ọzọ ma ọ bụ malitegharịa Nku.",
        lowConfidenceWarning = "\u26A0 Ễkwèsịrị dị ala \u2014 a gaghị etinye nke a na nlele. Tugharịa na ọ kụ karịa.",

        jaundiceScreen = "Nlele ọcha anya",
        preeclampsiaScreen = "Nlele ọrịa ime",
        respiratoryScreen = "Nlele iku ume",
        tapToCaptureEyelid = "Pịa ebe a ka ị see anya",
        tapToCaptureFace = "Pịa ebe a ka ị see ihu",
        tapToCaptureEye = "Pịa ebe a ka ị see anya",
        tapToRecordCough = "Pịa ebe a ka ị dee ụkwara",
        tapToMeasureHR = "Pịa ebe a ka ị tuo ọkụ obi",
        screeningsProgress = "%d n'ime 5 nlele emechara",
        readyForTriage = "✓ Dị njikere maka nlele — gaa na taabụ Nlele",
        followSteps = "Soro usoro ndị a ka ị nyochaa onye ọrịa",
        hrElevated = "⚠ Elu — nwere ike igosi nchegbu ma ọ bụ ọbara ala",
        hrLow = "⚠ Ala — lekwasị anya nke ọma",
        hrNormal = "✓ Ọ nọ n'ọkwa nkịtị",
        noPallor = "✓ Enweghị ọcha achọpụtara",
        noSwelling = "✓ Enweghị etu ihu",
        downloadingModel = "Na-ebudata MedGemma…",
        downloadSlowWarning = "Ngwa nwere ike ịnọ nwayọọ ka a na-ebudata AI. Ị nwere ike iji ngwa nlele.",
        downloadFailedWarning = "Enweghị ike ibudata ihe AI. Nlele ga-eji iwu.",
        dataSavedForTriage = "✓ Echekwara data maka nlele",
        measurementComplete = "Ntụle emechara"

    )

    val amharicStrings = UiStrings(
        appSubtitle = "በካሜራ ላይ ስሕተት ምልክቶች ማጣራት",
        tabHome = "ቤት",
        heartRate = "የልብ ምት",
        notYetScreened = "ገና አልተጣራም",
        startMeasurement = "ልኬት ጀምር",
        anemiaTitle = "የደም ማነስ ምርመራ",
        preETitle = "ፕሪኤክላምፕሲያ",
        triageTitle = "ክሊኒካል ምርመራ",
        normal = "መደበኛ",
        mild = "ቀላል",
        moderate = "መካከለኛ",
        severe = "ከባድ",
        analyze = "ተንትን",
        cancel = "ሰርዝ",
        disclaimer = "ይህ በ AI የሚደገፍ የማጣሪያ መሳሪያ ነው። ሁልጊዜ ሐኪም ያማክሩ።",
        cameraPermissionPreE = "የፕሪኤክላምፕሲያ ምርመራ ካሜራ ያስፈልጋል። በቅንብሮች ውስጥ ያብሩ።",
        loadingAiModel = "የ AI ሞዴል በመጫን ላይ ነው…",
        translatingToEnglish = "ወደ እንግሊዝኛ በመተርገም ላይ…",
        medgemmaAnalyzing = "MedGemma በመመርመር ላይ…",
        translatingResult = "ውጤቱን በመተርገም…",
        errorOccurred = "ስህተት ተከስቶል",
        processing = "በማክመም ላይ…",
        primaryConcerns = "ውይን ስግጊቶች",
        savedScreenings = "💾 %d ምርመራዎች ተቀምጠው",
        stopLabel = "አቃም",
        listenLabel = "🔊 አዳምጥ",
        triageSourceAI = "በ AI የተደገፈ ምርመራ (MedGemma)",
        triageSourceGuideline = "በመመሪያ ላይ የተመሰረተ ምርመራ",
        fallbackExplanation = "የ AI ሞዴል አልተገኘም። ውጤቶች የ WHO/IMCI ክሊኒካል መመሪያዎችን ይከተላሉ — ደህንና የተረጋገጠ።",
        fallbackRecoveryTip = "AI ን ለመመለስ: የበስተ ጀርባ መተግበሪያዎችን ይዝጉ ወይም Nku ን ዳግም ያስጀምሩ።",
        lowConfidenceWarning = "\u26A0 የተባበሮ ሙንጩ ዝቅተኛ \u2014 ይህ ስዋመ ወደ ምርመራ ላይጋበር ይችላል። በተሽለ ብርሃን ዳግም አንሱ።",

        jaundiceScreen = "የጃንዲስ ምርመራ",
        preeclampsiaScreen = "የእርግዝና ግፊት ምርመራ",
        respiratoryScreen = "የመተንፈስ ምርመራ",
        tapToCaptureEyelid = "የዓይን ሽፋን ለመቅረፅ እዚህ ይጫኑ",
        tapToCaptureFace = "ፊት ለመቅረፅ እዚህ ይጫኑ",
        tapToCaptureEye = "ዓይን ለመቅረፅ እዚህ ይጫኑ",
        tapToRecordCough = "ሳል ለመቅዳት እዚህ ይጫኑ",
        tapToMeasureHR = "የልብ ምት ለመለካት እዚህ ይጫኑ",
        screeningsProgress = "%d ከ5 ምርመራዎች ተጠናቅቀዋል",
        readyForTriage = "✓ ለምርመራ ዝግጁ — ወደ ምርመራ ትር ሂዱ",
        followSteps = "ታካሚውን ለመመርመር ከዚህ በታች ያሉትን ደረጃዎች ይከተሉ",
        hrElevated = "⚠ ከፍ ያለ — ጭንቀት ወይም የደም ማነስ ሊያመለክት ይችላል",
        hrLow = "⚠ ዝቅ ያለ — በቅርበት ይከታተሉ",
        hrNormal = "✓ በመደበኛ ክልል ውስጥ",
        noPallor = "✓ ነጭነት አልተገኘም",
        noSwelling = "✓ የፊት እብጠት የለም",
        downloadingModel = "MedGemma በማውረድ ላይ…",
        downloadSlowWarning = "የAI ሞዴሉ በሚወርድበት ጊዜ መተግበሪያው ሊዘገይ ይችላል። የምርመራ መሳሪያዎቹን መጠቀም ይችላሉ።",
        downloadFailedWarning = "የAI ሞዴሉ ሊወርድ አልቻለም። ምርመራ መመሪያ-ተኮር ግምገማ ይጠቀማል።",
        dataSavedForTriage = "✓ ውሂብ ለምርመራ ተቀምጧል",
        measurementComplete = "መለኪያ ተጠናቅቋል"

    )

    val twiStrings = UiStrings(
        appSubtitle = "Kamera so nkwa nsɛnkyerɛnne hwehwɛ",
        tabHome = "Fie",
        heartRate = "Koma pae",
        notYetScreened = "Yɛnhwɛ no ase da",
        startMeasurement = "Fi ase susu",
        anemiaTitle = "Mogya yare hwehwɛ",
        preETitle = "Preeclampsia hwehwɛ",
        triageTitle = "Apɔmuhyɛ hwehwɛ",
        normal = "Eye",
        mild = "Kakraa bi",
        moderate = "Ntam",
        severe = "Emu yɛ den",
        analyze = "Hwehwɛ mu",
        cancel = "Twa mu",
        disclaimer = "AI nhwehwɛmu adwumayɛdeɛ ni yi. Bisa dɔkota bere biara.",
        cameraPermissionPreE = "Preeclampsia hwehwɛ hia kamera. Yɛ so wɔ Nhɛhyemu mu.",
        loadingAiModel = "Yɛrehɛre AI model…",
        translatingToEnglish = "Yɛrekyekyerem Borofó kasa mu…",
        medgemmaAnalyzing = "MedGemma rehwehwɛ mu…",
        translatingResult = "Yɛrekyekyerem nsoano…",
        errorOccurred = "Mfomso bi abɛɖɛ ba",
        processing = "Yɛredi adwuma…",
        primaryConcerns = "Nkyerɛdɛɛ titīre",
        savedScreenings = "💾 Wɔakora nhwehwɛmu %d",
        stopLabel = "Gyina",
        listenLabel = "🔊 Tie",
        triageSourceAI = "AI Nhwehwɛmu (MedGemma)",
        triageSourceGuideline = "Nkyerɛwdeɛ so nhwehwɛmu",
        fallbackExplanation = "AI model no nni hɔ. Nsoano di WHO/IMCI nkyerɛwdeɛ akyi — eye safe na wɔaɛserɛ.",
        fallbackRecoveryTip = "Sɛ wopɛ AI: to apps a ɛwɔ akyi no mu anaa san bue Nku.",
        lowConfidenceWarning = "\u26A0 Ŋuɖoɖo sɔ \u2014 ebia wɔrenfa nkyerɛwdeɛ yi nhwɛhwɛmu no mu. San kɔ foto no wɔ hann nyinaa mu.",

        jaundiceScreen = "Jaundice hwehwɛ",
        preeclampsiaScreen = "Nyinsɛn mogya kɔ soro hwehwɛ",
        respiratoryScreen = "Ahome hwehwɛ",
        tapToCaptureEyelid = "Mia ha na fa aniwa ho mfonini",
        tapToCaptureFace = "Mia ha na fa anim mfonini",
        tapToCaptureEye = "Mia ha na fa aniwa mfonini",
        tapToRecordCough = "Mia ha na kyerɛw wa",
        tapToMeasureHR = "Mia ha na susu koma pɛm",
        screeningsProgress = "%d wɔ 5 mu no awie",
        readyForTriage = "✓ Awie — kɔ Hwehwɛ tab no so",
        followSteps = "Di anammɔn a ɛwɔ ase yi akyi na hwehwɛ ayaresafo no",
        hrElevated = "⚠ Ɛkɔ soro — ebia ɛkyerɛ adwene mu haw anaa mogya sua",
        hrLow = "⚠ Ɛwɔ fam — hwɛ so yiye",
        hrNormal = "✓ Ɛwɔ deɛ ɛsɛ mu",
        noPallor = "✓ Wɔanhu aniwa mu fitaa",
        noSwelling = "✓ Anim mu ahoninono biara nni hɔ",
        downloadingModel = "Ɛretwi MedGemma…",
        downloadSlowWarning = "App no bɛyɛ nwanwa bere a AI model retwi no. Wubetumi de hwehwɛ nnwinnade no adi dwuma.",
        downloadFailedWarning = "Wɔantumi antwi AI model no. Hwehwɛ bɛfa mmara so.",
        dataSavedForTriage = "✓ Data no akyerɛ hwehwɛ",
        measurementComplete = "Susu no awie"

    )

    val wolofStrings = UiStrings(
        tabHome = "Kër",
        heartRate = "Xel bu xol",
        notYetScreened = "Leeruñu ko",
        startMeasurement = "Tàmbalee",
        normal = "Baax",
        mild = "Tuuti",
        moderate = "Diggante",
        severe = "Lëndëm",
        analyze = "Saytul",
        cancel = "Bàyyi",
        disclaimer = "Jumtukaay bi dafa jëm ci AI. Laajte ak doktoor.",
        cameraPermissionPreE = "Saytul preeclampsia daf lay kamera. Moytu ko ci Téere yi.",
        loadingAiModel = "Yey bi AI model…",
        translatingToEnglish = "Yey bi ci Angale…",
        medgemmaAnalyzing = "MedGemma di na ko saytul…",
        translatingResult = "Yey bi natalu bi…",
        errorOccurred = "Njum bu bon jëm na",
        processing = "Di na li liggéey…",
        primaryConcerns = "Xalaat yu ndaw yi",
        savedScreenings = "💾 %d saytul yi des na ko",
        stopLabel = "Téédél",
        listenLabel = "🔊 Dègg",
        triageSourceAI = "Saytul AI (MedGemma)",
        triageSourceGuideline = "Saytul bu yoon yi",
        fallbackExplanation = "Model AI bi amul. Natalu yi di jëm ci yoonu WHO/IMCI — bu aar te.",
        fallbackRecoveryTip = "Ngir délusi AI: tëj appli yi ci ginnaaw wala dooraat Nku.",
        lowConfidenceWarning = "\u26A0 Gis-gis bu néew \u2014 natalu bii bées na ko ci saytul bi. Def ko kenn ci leer bu baax.",
        dataSavedForTriage = "✓ Njàng yi dañu ko denc ngir saafara",
        measurementComplete = "Nataal bi jeexna"

    )

    val zuluStrings = UiStrings(
        tabHome = "Ikhaya",
        heartRate = "Inhliziyo",
        notYetScreened = "Akukahlolelwa",
        startMeasurement = "Qala Ukukala",
        normal = "Kujwayelekile",
        mild = "Kancane",
        moderate = "Maphakathi",
        severe = "Kakhulu",
        analyze = "Hlola",
        cancel = "Khansela",
        disclaimer = "Lesi yithuluzi lokuhlola le-AI. Xhumana nodokotela njalo.",
        cameraPermissionPreE = "Ukuhlola i-preeclampsia kudinga ikhamera. Sicela uvule kuZilungiselelo.",
        loadingAiModel = "Kulayisha imodeli ye-AI…",
        translatingToEnglish = "Kuhumushelwa esiNgesini…",
        medgemmaAnalyzing = "I-MedGemma iyahlola…",
        translatingResult = "Kuhumusha umphumela…",
        errorOccurred = "Kukhona iphutha",
        processing = "Iyasebenza…",
        primaryConcerns = "Okukhathazayo Okukhulu",
        savedScreenings = "💾 Ukuhlolwa %d kulondolozwe",
        stopLabel = "Misa",
        listenLabel = "🔊 Lalela",
        triageSourceAI = "Ukuhlola nge-AI (MedGemma)",
        triageSourceGuideline = "Ukuhlola okwemithetho",
        fallbackExplanation = "Imodeli ye-AI ayitholakali. Imiphumela ilandela imihlahlandlela ye-WHO/IMCI — ephephile futhi eqinisekisiwe.",
        fallbackRecoveryTip = "Ukubuyisela i-AI: vala izinhlelo ezingemuva noma uqale kabusha i-Nku.",
        lowConfidenceWarning = "\u26A0 Ithemba eliphansi \u2014 lokhu kungase kungafakwa ekuhlolweni. Thatha kabusha ekukhanyeni okuhle.",

        jaundiceScreen = "Ukuhlolwa kwe-jaundice",
        preeclampsiaScreen = "Ukuhlolwa kwe-preeclampsia",
        respiratoryScreen = "Ukuhlolwa kokuphefumula",
        tapToCaptureEyelid = "Thepha lapha ukuthatha isithombe senkophe",
        tapToCaptureFace = "Thepha lapha ukuthatha isithombe sobuso",
        tapToCaptureEye = "Thepha lapha ukuthatha isithombe seso",
        tapToRecordCough = "Thepha lapha ukuqopha ukukhwehlela",
        tapToMeasureHR = "Thepha lapha ukukala inhliziyo",
        screeningsProgress = "%d kwezingu-5 zokuhlola kuqediwe",
        readyForTriage = "✓ Kulungiswe ukuhlolwa — iya kuthebhu ye-Triage",
        followSteps = "Landela izinyathelo ezingezansi ukuhlola isiguli",
        hrElevated = "⚠ Iphezulu — kungabonisa ukukhathazeka noma ukuncipha kwegazi",
        hrLow = "⚠ Iphansi — qapha ngokuseduze",
        hrNormal = "✓ Isezingeni elivamile",
        noPallor = "✓ Akukho ukuhloba okutholakele",
        noSwelling = "✓ Akukho ukuvuvukala kobuso",
        downloadingModel = "Ilanda i-MedGemma…",
        downloadSlowWarning = "Uhlelo lungahamba kancane ngenkathi kulandwa imodeli ye-AI. Ungasebenzisa amathuluzi okuhlola.",
        downloadFailedWarning = "Imodeli ye-AI ayikwazanga ukulandwa. Ukuhlolwa kuzosebenzisa imithetho.",
        dataSavedForTriage = "✓ Idatha igcinwe ngokuhlolwa",
        measurementComplete = "Ukukalwa kuqediwe"

    )

    val xhosaStrings = UiStrings(
        tabHome = "Ikhaya",
        heartRate = "Intliziyo",
        notYetScreened = "Ayikahlolwa",
        startMeasurement = "Qala Ukulinganisa",
        normal = "Iqhelekile",
        mild = "Kancinane",
        moderate = "Phakathi",
        severe = "Kakhulu",
        analyze = "Hlola",
        cancel = "Rhoxisa",
        disclaimer = "Esi sisixhobo sokuhlola se-AI. Thetha nogqirha rhoqo.",
        cameraPermissionPreE = "Ukuhlolwa kwe-preeclampsia kufuna ikhamera. Nceda uvule kwiiSetingi.",
        loadingAiModel = "Kulayishwa imodeli ye-AI…",
        translatingToEnglish = "Iguqulelwa esiNgesini…",
        medgemmaAnalyzing = "I-MedGemma iyahlola…",
        translatingResult = "Iguqulela isiphumo…",
        errorOccurred = "Kukhona impazamo",
        processing = "Iyasebenza…",
        primaryConcerns = "Iinkxalabo Eziphambili",
        savedScreenings = "💾 Ukuhlolwa %d kugcinwe",
        stopLabel = "Yima",
        listenLabel = "🔊 Mamela",
        triageSourceAI = "Ukuhlolwa nge-AI (MedGemma)",
        triageSourceGuideline = "Ukuhlolwa ngemigaqo",
        fallbackExplanation = "Imodeli ye-AI ayifumaneki. Iziphumo zilandela imigaqo ye-WHO/IMCI — ikhuselekile kwaye iqinisekisiwe.",
        fallbackRecoveryTip = "Ukubuyisela i-AI: vala izicelo ezingemva okanye uqalise kwakhona i-Nku.",
        lowConfidenceWarning = "\u26A0 Ukuthemba okuphantsi \u2014 oku kungangeniswa ekuhlolweni. Thatha kwakhona ekukhanyeni okuhle.",

        jaundiceScreen = "Ukuhlolwa kwe-jaundice",
        preeclampsiaScreen = "Ukuhlolwa kwe-preeclampsia",
        respiratoryScreen = "Ukuhlolwa kokuphefumla",
        tapToCaptureEyelid = "Cofa apha ukuthatha umfanekiso wenkophe",
        tapToCaptureFace = "Cofa apha ukuthatha umfanekiso wobuso",
        tapToCaptureEye = "Cofa apha ukuthatha umfanekiso weliso",
        tapToRecordCough = "Cofa apha ukurekhodisha ukukhohlela",
        tapToMeasureHR = "Cofa apha ukulinganisa intliziyo",
        screeningsProgress = "%d kwezi-5 zokuhlola zigqityiwe",
        readyForTriage = "✓ Kulungile ukuhlolwa — yiya kwitab ye-Triage",
        followSteps = "Landela amanyathelo angezantsi ukuhlola isigulane",
        hrElevated = "⚠ Iphezulu — inokubonisa uxinezelelo okanye ukuncipha kwegazi",
        hrLow = "⚠ Iphantsi — jonga ngokusondeleyo",
        hrNormal = "✓ Kwibakala eliqhelekileyo",
        noPallor = "✓ Akukho kufiphala okufunyenweyo",
        noSwelling = "✓ Akukho ukudumba kobuso",
        downloadingModel = "Ikhuphela i-MedGemma…",
        downloadSlowWarning = "I-app inokucotha ngexesha lokukhuphela imodeli ye-AI. Ungasebenzisa izixhobo zokuhlola.",
        downloadFailedWarning = "Imodeli ye-AI ayikwazanga ukukhutshwa. Ukuhlolwa kuza kusebenzisa imithetho.",
        dataSavedForTriage = "✓ Idatha igcinwe ngokuhlolwa",
        measurementComplete = "Ukulinganiswa kugqityiwe"

    )

    val oromoStrings = UiStrings(
        tabHome = "Mana",
        heartRate = "Dhahannaa onnee",
        notYetScreened = "Ammallee hin qoratamne",
        startMeasurement = "Safaruu Jalqabi",
        normal = "Idilee",
        mild = "Xiqqoo",
        moderate = "Giddu galeessa",
        severe = "Cimaa",
        analyze = "Qoradhu",
        cancel = "Haquu",
        disclaimer = "Meeshaan kun AI irratti. Ogeessa fayyaa mariyadhaa.",
        cameraPermissionPreE = "Qorannoo preeclampsia kaameeraa barbaada. Maaloo Settings keessatti banaa.",
        loadingAiModel = "Moodeela AI fe'aa jira…",
        translatingToEnglish = "Gara Ingiliffaatti hiikaa jira…",
        medgemmaAnalyzing = "MedGemma xiinxalaa jira…",
        translatingResult = "Bu'aa hiikaa jira…",
        errorOccurred = "Dogoggorri uumame",
        processing = "Hojjechaa jira…",
        primaryConcerns = "Dhimmoota Ijoo",
        savedScreenings = "💾 Qorannoo %d kuufame",
        stopLabel = "Dhaabi",
        listenLabel = "🔊 Dhaggeeffadhu",
        triageSourceAI = "Qorannoo AI (MedGemma)",
        triageSourceGuideline = "Qorannoo qajeelfama irratti hundaa'e",
        fallbackExplanation = "Moodeelli AI hin argamne. Bu'aan qajeelfama kilinika WHO/IMCI hordofa — nageenya fi mirkanaa'e.",
        fallbackRecoveryTip = "AI deebisuuf: appii duubatti jiran cufi ykn Nku irra deebi'i.",
        lowConfidenceWarning = "\u26A0 Amantaa gad-aanaa \u2014 lakkoofsi kun qorannoo keessa hin galuu ta'uu. Ifa keessatti irra deebi'i.",

        jaundiceScreen = "Qorannoo jaundice",
        preeclampsiaScreen = "Qorannoo dhiibbaa dhiigaa ulfaa",
        respiratoryScreen = "Qorannoo hafuuraa",
        tapToCaptureEyelid = "As tuqi suuraa ija fuudhii",
        tapToCaptureFace = "As tuqi suuraa fuulaa",
        tapToCaptureEye = "As tuqi suuraa ijaa",
        tapToRecordCough = "As tuqi qufaa galmeessuu",
        tapToMeasureHR = "As tuqi rukuttaa onnee safaruu",
        screeningsProgress = "%d keessaa 5 qorannoon xumurameera",
        readyForTriage = "✓ Qophaa'e — gara tab Qorannoo",
        followSteps = "Tartiiba armaan gadii hordofi dhukkubsataa qorachuuf",
        hrElevated = "⚠ Ol ka'e — cinqii ykn dhiiga hir'uu agarsiisuu danda'a",
        hrLow = "⚠ Gad bu'e — sirritti hordofi",
        hrNormal = "✓ Sadarkaa idilee keessa jira",
        noPallor = "✓ Adiin hin argamne",
        noSwelling = "✓ Dhiitoon fuulaa hin jiru",
        downloadingModel = "MedGemma buufachaa jira…",
        downloadSlowWarning = "Appiin suuta ta'uu dandeeysii yeroo moodeelli AI buufatamu. Meeshaalee qorannoo fayyadamuu dandeeysa.",
        downloadFailedWarning = "Moodeelli AI buufachuun hin danda'amne. Qorannoon seerawwan fayyadama.",
        dataSavedForTriage = "✓ Daataan qorannootiif qabame",
        measurementComplete = "Safartuun xumurameera"

    )

    val tigrinyaStrings = UiStrings(
        tabHome = "ገዛ",
        heartRate = "ልቢ",
        notYetScreened = "ገና ኣይተመርመረን",
        startMeasurement = "ምዕቃብ ጀምር",
        normal = "ንቡር",
        mild = "ቀሊል",
        moderate = "ማእከላይ",
        severe = "ከቢድ",
        analyze = "ምርመራ",
        cancel = "ሰርዝ",
        disclaimer = "እዚ ብ AI ዝተሓገዘ መሳርሒ ምርመራ እዩ። ኩሉ ግዜ ሓኪም ኣማኽሩ።",
        cameraPermissionPreE = "መርመራ ፕሪኤክላምፕሲያ ካሜራ የዐል። ብጡብሓክ እቡ ብመንባብ እቶ ክፈት።",
        loadingAiModel = "ሞዴል AI ይጫን አሎ…",
        translatingToEnglish = "ናብ እንግሊዝኛ ይትርገም አሎ…",
        medgemmaAnalyzing = "MedGemma ይመርምር አሎ…",
        translatingResult = "ውጤት ይትርገም አሎ…",
        errorOccurred = "ሳሕቲ ተፈጢዓል",
        processing = "ይሰራሕ አሎ…",
        primaryConcerns = "ቀነውን ስጋታት",
        savedScreenings = "💾 %d ምርመራታት ተዓቊቡ’ት",
        stopLabel = "አቅሩዕ",
        listenLabel = "🔊 ስማዕ",
        triageSourceAI = "ብ AI ዝተሓገዘ ምርመራ (MedGemma)",
        triageSourceGuideline = "ብመምርሒ ዝተመስረተ ምርመራ",
        fallbackExplanation = "ሞዴል AI ኣይተረኽበን። ውጽኢታት ናይ WHO/IMCI ክሊኒካዊ መምርሒታት ይኽተሉ — ውሑስን ዝተረጋገጸን።",
        fallbackRecoveryTip = "AI ንምምላስ: ናይ ድሕሪት ኣፕሊኬሽናት ዕጸው ወይ Nku ዳግም ጀምር።",
        lowConfidenceWarning = "\u26A0 ትሑት ዙሁል ኢሉ \u2014 እዚ ንባብ ኣብ ምርመራ ኣይእተውን ይኽእል። ኣብ ጽቡሕ ብርሃን ዳግም ኣንሱ።",

        jaundiceScreen = "ምርመራ ጃንዲስ",
        preeclampsiaScreen = "ምርመራ ጸቕጢ ደም ጥንሲ",
        respiratoryScreen = "ምርመራ ምስትንፋስ",
        tapToCaptureEyelid = "ናይ ዓይኒ ሽፋን ንምስኣል ኣብዚ ጠቕዑ",
        tapToCaptureFace = "ገጽ ንምስኣል ኣብዚ ጠቕዑ",
        tapToCaptureEye = "ዓይኒ ንምስኣል ኣብዚ ጠቕዑ",
        tapToRecordCough = "ስዓል ንምቕዳሕ ኣብዚ ጠቕዑ",
        tapToMeasureHR = "ህርመት ልቢ ንምዕቃን ኣብዚ ጠቕዑ",
        screeningsProgress = "%d ካብ 5 ምርመራታት ተዛዚሙ",
        readyForTriage = "✓ ንምርመራ ድሉው — ናብ ታብ ምርመራ ኺዱ",
        followSteps = "ንሕሙም ንምምርማር ኣብ ታሕቲ ዘለዉ ስጉምትታት ተኸተሉ",
        hrElevated = "⚠ ልዑል — ጸቕጢ ወይ ውሒድ ደም ከመልክት ይኽእል",
        hrLow = "⚠ ትሑት — ብቐረባ ተኸታተሉ",
        hrNormal = "✓ ኣብ ንቡር ደረጃ",
        noPallor = "✓ ጻዕዳ ኣይተረኸበን",
        noSwelling = "✓ ምሕባጥ ገጽ የለን",
        downloadingModel = "MedGemma ይወርድ ኣሎ…",
        downloadSlowWarning = "ናይ AI ሞዴል ክወርድ ከሎ ኣፕ ክደንጊ ይኽእል። ናይ ምርመራ መሳርሒታት ክትጥቀሙ ትኽእሉ።",
        downloadFailedWarning = "ናይ AI ሞዴል ክወርድ ኣይከኣለን። ምርመራ ናይ መምርሒ ግምገማ ክጥቀም እዩ።",
        dataSavedForTriage = "✓ ዳታ ንምርመራ ተዓቂቡ",
        measurementComplete = "ዕቃን ተዛዚሙ"

    )

    // ─── Tier 2 Languages ───────────────────────────────────────

    val afrikaansStrings = UiStrings(
        appSubtitle = "Kamera-gebaseerde lewensteken sifting",
        tabHome = "Tuis",
        tabCardio = "Hart",
        tabAnemia = "Bloedarmoede",
        tabPreE = "PreE",
        tabTriage = "Triage",
        tabSettings = "Instellings",
        heartRate = "Hartklop",
        anemiaScreen = "Bloedarmoede sifting",
        jaundiceScreen = "Geelsug sifting",
        preeclampsiaScreen = "Preeklamsie sifting",
        respiratoryScreen = "Asemhaling sifting",
        tapToMeasureHR = "Tik hier om hartklop te meet",
        tapToCaptureEyelid = "Tik hier om ooglid vas te vang",
        tapToCaptureFace = "Tik hier om gesig vas te vang",
        tapToCaptureEye = "Tik hier om oog vas te vang",
        tapToRecordCough = "Tik hier om hoes op te neem",
        screeningsProgress = "%d van 5 siftings voltooi",
        readyForTriage = "✓ Gereed vir triage — gaan na Triage-oortjie",
        followSteps = "Volg die stappe hieronder om 'n pasiënt te sif",
        hrElevated = "⚠ Verhoog — kan stres of bloedarmoede aandui",
        hrLow = "⚠ Laag — monitor noukeurig",
        hrNormal = "✓ Binne normale reeks",
        noPallor = "✓ Geen bleekheid bespeur",
        noSwelling = "✓ Geen gesigswelling",
        normal = "Normaal",
        mild = "Lig",
        moderate = "Matig",
        severe = "Ernstig",
        analyze = "Ontleed",
        cancel = "Kanselleer",
        disclaimer = "KI-ondersteunde siftingsinstrument. Raadpleeg altyd 'n gesondheidswerker.",
        downloadingModel = "Laai MedGemma af…",
        downloadSlowWarning = "Die toep kan stadiger wees terwyl die KI-model aflaai. U kan steeds die siftingsinstrumente gebruik.",
        downloadFailedWarning = "Die KI-model kon nie afgelaai word nie. Triage sal reëlgebaseerde assessering gebruik.",
        dataSavedForTriage = "✓ Data gestoor vir triage",
        measurementComplete = "Meting voltooi"

    )

    val bambaraStrings = UiStrings(
        appSubtitle = "Kaamera baara la sɛgɛsɛgɛli",
        tabHome = "So",
        tabTriage = "Sɛgɛsɛgɛli",
        tabSettings = "Labɛnni",
        jaundiceScreen = "Jaundice sɛgɛsɛgɛli",
        preeclampsiaScreen = "Kɔnɔboli sɛgɛsɛgɛli",
        respiratoryScreen = "Nisɔndiya sɛgɛsɛgɛli",
        tapToMeasureHR = "Digi yan ka dusukun sɛgɛsɛgɛ",
        tapToCaptureEyelid = "Digi yan ka ɲɛ ja ta",
        tapToCaptureFace = "Digi yan ka ɲɛda ja ta",
        tapToCaptureEye = "Digi yan ka ɲɛ ja ta",
        tapToRecordCough = "Digi yan ka sugɔsugu sɛbɛn",
        screeningsProgress = "%d bɛ 5 la sɛgɛsɛgɛli banna",
        readyForTriage = "✓ A labɛnna — taa Sɛgɛsɛgɛli tab la",
        followSteps = "Nɔmɔgɔya ninnu kɛ ka banabagatɔ sɛgɛsɛgɛ",
        downloadingModel = "MedGemma bɛ jiginni na…",
        downloadSlowWarning = "Porogaramu bɛ se ka dɔgɔya AI modɛli jiginni waati la.",
        downloadFailedWarning = "AI modɛli ma se ka jigi. Sɛgɛsɛgɛli bɛna sariya baara.",
        disclaimer = "AI sɛgɛsɛgɛli baarakɛminɛn ye. Dɔgɔtɔrɔ ɲininka tuma bɛɛ.",
        dataSavedForTriage = "✓ Bayanw mara sɛgɛsɛgɛli kama",
        measurementComplete = "Jateminɛ banna"

    )

    val chichewaStrings = UiStrings(
        appSubtitle = "Kuyeza ndi kamera",
        tabHome = "Kwathu",
        tabTriage = "Kuyeza",
        tabSettings = "Zosintha",
        jaundiceScreen = "Kuyeza jaundice",
        preeclampsiaScreen = "Kuyeza preeclampsia",
        respiratoryScreen = "Kuyeza kupuma mpweya",
        tapToMeasureHR = "Dinani apa kuyeza mtima",
        tapToCaptureEyelid = "Dinani apa kutenga chithunzi cha diso",
        tapToCaptureFace = "Dinani apa kutenga chithunzi cha nkhope",
        tapToCaptureEye = "Dinani apa kutenga chithunzi cha diso",
        tapToRecordCough = "Dinani apa kujambula chifuwa",
        screeningsProgress = "%d mwa 5 zoyeza zatheka",
        readyForTriage = "✓ Zokonzeka — pitani ku tab ya Triage",
        followSteps = "Tsatirani njira izi kuyeza wodwala",
        downloadingModel = "Kukopa MedGemma…",
        downloadSlowWarning = "Pulogalamu ikhoza kuchedwa pomwe modeli ya AI ikukopedwa.",
        downloadFailedWarning = "Modeli ya AI siinakopedwe. Kuyeza kudzagwiritsa ntchito malamulo.",
        disclaimer = "Chida choyeza cha AI. Funsani dotolo nthawi zonse.",
        dataSavedForTriage = "✓ Data yasungidwa kuti iyezedwe",
        measurementComplete = "Kuyeza kwatheka"

    )

    val dinkaStrings = UiStrings(
        tabHome = "Baai",
        tabTriage = "Ŋic",
        tabSettings = "Guɛ̈ɛ̈r",
        jaundiceScreen = "Ŋic de jaundice",
        preeclampsiaScreen = "Ŋic de preeclampsia",
        respiratoryScreen = "Ŋic de wëi",
        tapToMeasureHR = "Gät ë tɛ̈n ba piɔ̈u ŋic",
        tapToCaptureEyelid = "Gät ë tɛ̈n ba nyin thiëëk",
        tapToCaptureFace = "Gät ë tɛ̈n ba nhiɛ̈m thiëëk",
        tapToRecordCough = "Gät ë tɛ̈n ba gɔ̈l gɔ̈t",
        tapToCaptureEye = "Gät ë tɛ̈n ba nyin thiëëk",
        downloadingModel = "MedGemma ëë gäm…",
        downloadSlowWarning = "App abë dɔm alɔŋ AI model ëë gäm.",
        downloadFailedWarning = "AI model akëc gäm. Ŋic abë luɔi kë thɛɛr.",
        disclaimer = "Kä ŋic de AI yen. Thiëëc ë dɛktɔr aköl ëbɛ̈n.",
        dataSavedForTriage = "✓ Data akëc muk ë ŋic",
        measurementComplete = "Ŋic acï thöl"

    )

    val fulaStrings = UiStrings(
        tabHome = "Suudu",
        tabTriage = "Ƴeewndoo",
        tabSettings = "Teelte",
        jaundiceScreen = "Ƴeewndoo nyawu ɓale",
        preeclampsiaScreen = "Ƴeewndoo reedu",
        respiratoryScreen = "Ƴeewndoo foolde",
        tapToMeasureHR = "Soor ɗoo ngam ƴeewde ɓernde",
        tapToCaptureEyelid = "Soor ɗoo ngam nangude natal yitere",
        tapToCaptureFace = "Soor ɗoo ngam nangude natal yeeso",
        tapToCaptureEye = "Soor ɗoo ngam nangude natal yitere",
        tapToRecordCough = "Soor ɗoo ngam winndude doole",
        downloadingModel = "MedGemma ina aartee…",
        downloadSlowWarning = "Jaaɓnirgo ngo waawi leelde tuma modeel AI nde ina aartee.",
        downloadFailedWarning = "Modeel AI nde waawaa aartaade. Ƴeewndoo huutoroyta jamirooje.",
        disclaimer = "Kuutorgal ƴeewndoo AI. Haalan cafroowo sahaa kala.",
        dataSavedForTriage = "✓ Keɓe ɗe ndaaratee ngam ƴeewndoo",
        measurementComplete = "Ƴeewndoo gasii"

    )

    val gaStrings = UiStrings(
        tabHome = "Shishi",
        tabTriage = "Kɛha",
        tabSettings = "Lɛbaa",
        jaundiceScreen = "Jaundice kɛha",
        preeclampsiaScreen = "Preeclampsia kɛha",
        respiratoryScreen = "Gbɔmɔ kɛha",
        tapToMeasureHR = "Kɛ fɛɛ jiemɔ akɛ hee shishi",
        tapToCaptureEyelid = "Kɛ fɛɛ jiemɔ akɛ nyɛ foto",
        tapToCaptureFace = "Kɛ fɛɛ jiemɔ akɛ gbee foto",
        tapToCaptureEye = "Kɛ fɛɛ jiemɔ akɛ nyɛ foto",
        tapToRecordCough = "Kɛ fɛɛ jiemɔ akɛ shishia",
        downloadingModel = "MedGemma eshi ba lɛ…",
        downloadSlowWarning = "App lɛ abaakɛ dɔŋ AI model eshi ba lɛ wɔ.",
        downloadFailedWarning = "AI model mli eba. Kɛha amli shikpɔŋ hewɔ.",
        disclaimer = "AI kɛha jiemɔ ni lɛ. Bii dɔktɔ he sahaa kɛjogbaŋ.",
        dataSavedForTriage = "✓ Data yɛ tsɔɔ kpɔkpɔ",
        measurementComplete = "Kɛha yɛ wie"

    )

    val kikuyuStrings = UiStrings(
        tabHome = "Mũciĩ",
        tabTriage = "Thibitho",
        tabSettings = "Ũhoro",
        jaundiceScreen = "Thibitho ya jaundice",
        preeclampsiaScreen = "Thibitho ya preeclampsia",
        respiratoryScreen = "Thibitho ya gũthithĩria",
        tapToMeasureHR = "Tinia haha gũthima ngoro",
        tapToCaptureEyelid = "Tinia haha gũthiũra foto ya riitho",
        tapToCaptureFace = "Tinia haha gũthiũra foto ya ũthiũ",
        tapToCaptureEye = "Tinia haha gũthiũra foto ya riitho",
        tapToRecordCough = "Tinia haha kũandĩka kĩhuti",
        downloadingModel = "MedGemma nĩ ĩrahũthĩrwo…",
        downloadSlowWarning = "App ĩngĩhota gũteithia rĩrĩa modeli ya AI ĩrĩ gũhũthĩrwo.",
        downloadFailedWarning = "Modeli ya AI ndĩrahota gũhũthĩrwo. Thibitho nĩĩgagĩrĩria mawatho.",
        disclaimer = "Kĩrĩa gĩa thibitho gĩa AI. Ũria daktarĩ hĩndĩ ciothe.",
        dataSavedForTriage = "✓ Data ĩhiũrĩtwo nĩ thibitho",
        measurementComplete = "Gũthima gwathira"

    )

    val kinyarwandaStrings = UiStrings(
        appSubtitle = "Isuzuma ry'ibimenyetso by'ubuzima hakoreshejwe kamera",
        tabHome = "Ahabanza",
        tabTriage = "Isuzuma",
        tabSettings = "Igenamiterere",
        jaundiceScreen = "Isuzuma ry'umuhondo",
        preeclampsiaScreen = "Isuzuma rya preeclampsia",
        respiratoryScreen = "Isuzuma ry'ubuhumekero",
        tapToMeasureHR = "Kanda hano gupima umutima",
        tapToCaptureEyelid = "Kanda hano gufata ifoto y'urupfunguzo rw'ijisho",
        tapToCaptureFace = "Kanda hano gufata ifoto y'isura",
        tapToCaptureEye = "Kanda hano gufata ifoto y'ijisho",
        tapToRecordCough = "Kanda hano gufata inkorora",
        screeningsProgress = "%d mu 5 amasuzuma yarasohotse",
        readyForTriage = "✓ Biteguye — jya ku gice cya Triage",
        followSteps = "Kurikiza intambwe ziri hepfo gusuzuma umurwayi",
        hrElevated = "⚠ Hejuru — bishobora kwerekana umuhangayiko cyangwa amaraso make",
        hrLow = "⚠ Hasi — kurikirana hafi",
        hrNormal = "✓ Mu rwego rusanzwe",
        noPallor = "✓ Nta gucya kwabonetse",
        noSwelling = "✓ Nta kubyimba kw'isura",
        downloadingModel = "Gukuramo MedGemma…",
        downloadSlowWarning = "Porogaramu ishobora gutinda mugihe moderi ya AI ikururwa.",
        downloadFailedWarning = "Moderi ya AI ntiyashoboye gukururwa. Isuzuma rizakoresha amategeko.",
        disclaimer = "Igikoresho cyo gusuzuma gishyigikiwe na AI. Buri gihe mubaze umuganga.",
        dataSavedForTriage = "✓ Amakuru yabitswe ku isuzuma",
        measurementComplete = "Igipimo cyarangiye"

    )

    val kongoStrings = UiStrings(
        tabHome = "Nzo",
        tabTriage = "Talela",
        tabSettings = "Bidimbu",
        jaundiceScreen = "Talela ya jaundice",
        preeclampsiaScreen = "Talela ya preeclampsia",
        respiratoryScreen = "Talela ya mvimba",
        tapToMeasureHR = "Fiota awa mpo na kotala motema",
        tapToCaptureEyelid = "Fiota awa mpo na kozwa foto ya liso",
        tapToCaptureFace = "Fiota awa mpo na kozwa foto ya elongi",
        tapToCaptureEye = "Fiota awa mpo na kozwa foto ya liso",
        tapToRecordCough = "Fiota awa mpo na kokoma nsongo",
        downloadingModel = "MedGemma ezali ko kita…",
        downloadSlowWarning = "Application ekoki kozala malembe tango modèle ya AI ezali ko kita.",
        downloadFailedWarning = "Modèle ya AI ekokaki ko kita te. Talela ekosalela mibeko.",
        disclaimer = "Esaleli ya AI mpo na kotala. Tuna monganga ntango nyonso.",
        dataSavedForTriage = "✓ Data ebombami mpo na talela",
        measurementComplete = "Kotala esili"

    )

    val lingalaStrings = UiStrings(
        appSubtitle = "Kotala bilembo ya bomoi na kamera",
        tabHome = "Ndako",
        tabTriage = "Kotala",
        tabSettings = "Mibeko",
        jaundiceScreen = "Kotala jaundice",
        preeclampsiaScreen = "Kotala preeclampsia",
        respiratoryScreen = "Kotala kopema",
        tapToMeasureHR = "Fiota awa mpo na kotala motema",
        tapToCaptureEyelid = "Fiota awa mpo na kozwa foto ya liso",
        tapToCaptureFace = "Fiota awa mpo na kozwa foto ya elongi",
        tapToCaptureEye = "Fiota awa mpo na kozwa foto ya liso",
        tapToRecordCough = "Fiota awa mpo na kokoma kosenga",
        downloadingModel = "MedGemma ezali ko kita…",
        downloadSlowWarning = "Appli ekoki kozala malembe tango modèle ya AI ezali ko kita.",
        downloadFailedWarning = "Modèle ya AI ekokaki ko kita te. Kotala ekosalela mibeko.",
        disclaimer = "Esaleli ya AI mpo na kotala. Tuna monganga ntango nyonso.",
        dataSavedForTriage = "✓ Data ebombami mpo na kotala",
        measurementComplete = "Kotala esili"

    )

    val luoStrings = UiStrings(
        tabHome = "Dala",
        tabTriage = "Nono",
        tabSettings = "Ter",
        jaundiceScreen = "Nono jaundice",
        preeclampsiaScreen = "Nono preeclampsia",
        respiratoryScreen = "Nono yueyo",
        tapToMeasureHR = "Mul ka mondo ipim chuny",
        tapToCaptureEyelid = "Mul ka mondo igam picha mar wang",
        tapToCaptureFace = "Mul ka mondo igam picha mar lep wang",
        tapToCaptureEye = "Mul ka mondo igam picha mar wang",
        tapToRecordCough = "Mul ka mondo indik ahonda",
        downloadingModel = "MedGemma dhi piny…",
        downloadSlowWarning = "App nyalo dhi mos ka model mar AI dhi piny.",
        downloadFailedWarning = "Model mar AI ok onyalo lor. Nono biro tiyo gi chike.",
        disclaimer = "Gir nono mar AI. Penj laktar kinde duto.",
        dataSavedForTriage = "✓ Data okan ni nono",
        measurementComplete = "Pimo orumo"

    )

    val lugandaStrings = UiStrings(
        appSubtitle = "Okukebera obubonero bw'obulamu nga okozesa kamera",
        tabHome = "Awaka",
        tabTriage = "Okukebera",
        tabSettings = "Entegeka",
        jaundiceScreen = "Okukebera jaundice",
        preeclampsiaScreen = "Okukebera preeclampsia",
        respiratoryScreen = "Okukebera okussa",
        tapToMeasureHR = "Nyiga wano okupima omutima",
        tapToCaptureEyelid = "Nyiga wano okukwata ekifaananyi ky'ekisenge",
        tapToCaptureFace = "Nyiga wano okukwata ekifaananyi ky'ekyenyi",
        tapToCaptureEye = "Nyiga wano okukwata ekifaananyi ky'eriiso",
        tapToRecordCough = "Nyiga wano okurekodinga okukola",
        screeningsProgress = "%d ku 5 ebikeberwa biwedde",
        readyForTriage = "✓ Wetegese — genda ku tab Triage",
        followSteps = "Goberera emitendera gino okukebera omulwadde",
        hrElevated = "⚠ Waggulu — kiyinza okulaga ennyike oba omusaayi ogutono",
        hrLow = "⚠ Wansi — kebera bulungi",
        hrNormal = "✓ Mu mwetwegero ewekyama",
        noPallor = "✓ Tewali kufuuka okuzuuliddwa",
        noSwelling = "✓ Tewali kuzimba kw'ekyenyi",
        downloadingModel = "Ekitabo kya MedGemma kikkutuka…",
        downloadSlowWarning = "App eyinza okutegeera ng'emodeli ya AI ekuttuka.",
        downloadFailedWarning = "Emodeli ya AI teyasobola kkuttuka. Okukeberera kujja kukozesa ebiragiro.",
        disclaimer = "Ekikozesebwa ky'okukebera ekya AI. Buuza omusawo buli kiseera.",
        dataSavedForTriage = "✓ Data ekuumiddwa kukebera",
        measurementComplete = "Okupima kuwedde"

    )

    val malagasyStrings = UiStrings(
        tabHome = "Fandraisana",
        tabTriage = "Fizahana",
        tabSettings = "Fanovana",
        jaundiceScreen = "Fizahana jaundice",
        preeclampsiaScreen = "Fizahana preeclampsia",
        respiratoryScreen = "Fizahana rivotra",
        tapToMeasureHR = "Tsindrio eto handrefesana fo",
        tapToCaptureEyelid = "Tsindrio eto haka sary ny maso",
        tapToCaptureFace = "Tsindrio eto haka sary ny endrika",
        tapToCaptureEye = "Tsindrio eto haka sary ny maso",
        tapToRecordCough = "Tsindrio eto handraiketana kohaka",
        downloadingModel = "Misintona MedGemma…",
        downloadSlowWarning = "Mety ho miadana ny rindranasa raha misintona ny modely AI.",
        downloadFailedWarning = "Tsy afaka nisintona ny modely AI. Hampiasa fitsipika ny fizahana.",
        disclaimer = "Fitaovana fizahana AI. Manontania dokotera foana.",
        dataSavedForTriage = "✓ Data voatahiry ho an ny fizahana",
        measurementComplete = "Ny fandrefesana vita"

    )

    val ndebeleStrings = UiStrings(
        tabHome = "Ekhaya",
        tabTriage = "Ukuhlola",
        tabSettings = "Izilungiselelo",
        jaundiceScreen = "Ukuhlolwa kwe-jaundice",
        preeclampsiaScreen = "Ukuhlolwa kwe-preeclampsia",
        respiratoryScreen = "Ukuhlolwa kokuphefumula",
        tapToMeasureHR = "Thinta lapha ukukala inhliziyo",
        tapToCaptureEyelid = "Thinta lapha ukuthatha isithombe senkophe",
        tapToCaptureFace = "Thinta lapha ukuthatha isithombe sobuso",
        tapToCaptureEye = "Thinta lapha ukuthatha isithombe seso",
        tapToRecordCough = "Thinta lapha ukuqopha ukukhwehlela",
        downloadingModel = "Ilanda i-MedGemma…",
        downloadSlowWarning = "Uhlelo lungahamba kancane ngenkathi kulandwa imodeli ye-AI.",
        downloadFailedWarning = "Imodeli ye-AI ayikwazanga ukulandwa. Ukuhlolwa kuzosebenzisa imithetho.",
        disclaimer = "Isixhobo sokuhlola se-AI. Buza udokotela ngaso sonke isikhathi.",
        dataSavedForTriage = "✓ Idatha igcinwe ngokuhlola",
        measurementComplete = "Ukukala kuqediwe"

    )

    val nuerStrings = UiStrings(
        tabHome = "Ciɛŋ",
        tabTriage = "Ŋic",
        tabSettings = "Guɛ̈ɛ̈r",
        jaundiceScreen = "Ŋic jaundice",
        preeclampsiaScreen = "Ŋic preeclampsia",
        respiratoryScreen = "Ŋic wëi",
        tapToMeasureHR = "Gät tɛ̈n ba piɔ̈u ŋic",
        tapToCaptureEyelid = "Gät tɛ̈n ba nyin thiëëk",
        tapToCaptureFace = "Gät tɛ̈n ba nhiɛ̈m thiëëk",
        tapToCaptureEye = "Gät tɛ̈n ba nyin thiëëk",
        tapToRecordCough = "Gät tɛ̈n ba gɔ̈l gɔ̈t",
        downloadingModel = "MedGemma ëë gäm…",
        downloadSlowWarning = "App abë dɔm alɔŋ AI model ëë gäm.",
        downloadFailedWarning = "AI model akëc gäm. Ŋic abë luɔi kë thɛɛr.",
        disclaimer = "Kä ŋic AI yen. Thiëëc dɛktɔr aköl ëbɛ̈n.",
        dataSavedForTriage = "✓ Data acï muk ë ŋic",
        measurementComplete = "Ŋic acï thöl"

    )

    val pidginNgStrings = UiStrings(
        appSubtitle = "Camera screening for body signs",
        tabHome = "Home",
        tabTriage = "Check",
        tabSettings = "Settings",
        jaundiceScreen = "Yellow eye check",
        preeclampsiaScreen = "Belle woman check",
        respiratoryScreen = "Cough check",
        tapToMeasureHR = "Press here to check heartbeat",
        tapToCaptureEyelid = "Press here to snap eye",
        tapToCaptureFace = "Press here to snap face",
        tapToCaptureEye = "Press here to snap eye",
        tapToRecordCough = "Press here to record cough",
        screeningsProgress = "%d for 5 check don finish",
        readyForTriage = "✓ E don ready — go Triage tab",
        followSteps = "Follow dis steps to check patient",
        downloadingModel = "E dey download MedGemma…",
        downloadSlowWarning = "App fit slow small as AI dey download. You fit still use di check tools.",
        downloadFailedWarning = "AI no fit download. Check go use normal rules.",
        disclaimer = "Na AI screening tool be this. Always ask doctor.",
        dataSavedForTriage = "✓ Data don save for check",
        measurementComplete = "Check don finish"

    )

    val pidginCmStrings = UiStrings(
        tabHome = "House",
        tabTriage = "Check",
        tabSettings = "Fix",
        jaundiceScreen = "Yellow eye check",
        preeclampsiaScreen = "Belle woman check",
        respiratoryScreen = "Cough check",
        tapToMeasureHR = "Touch for here check heart",
        tapToCaptureEyelid = "Touch for here snap eye",
        tapToCaptureFace = "Touch for here snap face",
        tapToCaptureEye = "Touch for here snap eye",
        tapToRecordCough = "Touch for here record cough",
        downloadingModel = "MedGemma di come down…",
        downloadSlowWarning = "App fit go slow as AI di come down.",
        downloadFailedWarning = "AI no fit come down. Check go use normal way.",
        disclaimer = "Na AI check tool dis. Ask doctor every time.",
        dataSavedForTriage = "✓ Data don save for check",
        measurementComplete = "Check don finish"

    )

    val rundiStrings = UiStrings(
        tabHome = "Muhira",
        tabTriage = "Isuzuma",
        tabSettings = "Amategeko",
        jaundiceScreen = "Isuzuma ry'umuhondo",
        preeclampsiaScreen = "Isuzuma rya preeclampsia",
        respiratoryScreen = "Isuzuma ry'uguhumeka",
        tapToMeasureHR = "Kanda ng'aha gupima umutima",
        tapToCaptureEyelid = "Kanda ng'aha gufata ifoto y'ijisho",
        tapToCaptureFace = "Kanda ng'aha gufata ifoto y'mu maso",
        tapToCaptureEye = "Kanda ng'aha gufata ifoto y'ijisho",
        tapToRecordCough = "Kanda ng'aha kwandika inkorora",
        downloadingModel = "MedGemma irakururwa…",
        downloadSlowWarning = "Porogaramu ishobora guteba mugihe modeli ya AI ikururwa.",
        downloadFailedWarning = "Modeli ya AI ntiyashobotse gukururwa. Isuzuma rizakoresha amategeko.",
        disclaimer = "Igikoresho co gusuzuma ca AI. Baza muganga igihe cose.",
        dataSavedForTriage = "✓ Amakuru yarabitswe ku isuzuma",
        measurementComplete = "Igipimo carangiye"

    )

    val sesothoStrings = UiStrings(
        tabHome = "Hae",
        tabTriage = "Tlhahlobo",
        tabSettings = "Ditlhophiso",
        jaundiceScreen = "Tlhahlobo ya jaundice",
        preeclampsiaScreen = "Tlhahlobo ya preeclampsia",
        respiratoryScreen = "Tlhahlobo ya ho hema",
        tapToMeasureHR = "Tobetsa mona ho lekanya pelo",
        tapToCaptureEyelid = "Tobetsa mona ho nka setshwantsho sa leihlo",
        tapToCaptureFace = "Tobetsa mona ho nka setshwantsho sa sefahleho",
        tapToCaptureEye = "Tobetsa mona ho nka setshwantsho sa leihlo",
        tapToRecordCough = "Tobetsa mona ho hatisa sefuba",
        downloadingModel = "Ho jarolla MedGemma…",
        downloadSlowWarning = "App e ka ba butle ha modele ea AI e ntse e jarollwa.",
        downloadFailedWarning = "Modele ea AI ha ea ka ea jarollwa. Tlhahlobo e tla sebelisa melao.",
        disclaimer = "Sesebelisoa sa tlhahlobo sa AI. Botsa ngaka kamehla.",
        dataSavedForTriage = "✓ Data e bolokiloe ho tlhahlobo",
        measurementComplete = "Ho lekanya ho phethiloe"

    )

    val shonaStrings = UiStrings(
        tabHome = "Kumba",
        tabTriage = "Kuongorora",
        tabSettings = "Zvigadziriso",
        jaundiceScreen = "Kuongorora jaundice",
        preeclampsiaScreen = "Kuongorora preeclampsia",
        respiratoryScreen = "Kuongorora kufema",
        tapToMeasureHR = "Bata pano kuyera moyo",
        tapToCaptureEyelid = "Bata pano kutora mufananidzo weziso",
        tapToCaptureFace = "Bata pano kutora mufananidzo wechiso",
        tapToCaptureEye = "Bata pano kutora mufananidzo weziso",
        tapToRecordCough = "Bata pano kurekodha chikosoro",
        downloadingModel = "Kudhawunirodha MedGemma…",
        downloadSlowWarning = "App inogona kunonoka AI modeli iri kudhawunirodha.",
        downloadFailedWarning = "AI modeli haina kukwanisa kudhawunirodha. Kuongorora kuchashandisa mitemo.",
        disclaimer = "Chishandiswa chekuongorora cheAI. Bvunza chiremba nguva dzose.",
        dataSavedForTriage = "✓ Data yakachengetwa kuongorora",
        measurementComplete = "Kuyera kwapera"

    )

    val somaliStrings = UiStrings(
        appSubtitle = "Baaritaanka calaamadaha nolosha kamaradda",
        tabHome = "Guriga",
        tabTriage = "Baaritaan",
        tabSettings = "Dejinta",
        jaundiceScreen = "Baaritaan jaundice",
        preeclampsiaScreen = "Baaritaan preeclampsia",
        respiratoryScreen = "Baaritaan neefsashada",
        tapToMeasureHR = "Taabo halkan si aad u cabbirto wadnaha",
        tapToCaptureEyelid = "Taabo halkan si aad u qaadato sawir isha",
        tapToCaptureFace = "Taabo halkan si aad u qaadato sawir wajiga",
        tapToCaptureEye = "Taabo halkan si aad u qaadato sawir isha",
        tapToRecordCough = "Taabo halkan si aad u duubto qufaca",
        downloadingModel = "Waa la soo dejinayaa MedGemma…",
        downloadSlowWarning = "App-ka wuxuu noqon karaa gaabis inta AI-da la soo dejinayo.",
        downloadFailedWarning = "AI modeli lama soo dejin karin. Baaritaanku wuxuu isticmaali doonaa xeerarka.",
        disclaimer = "Qalabka baaritaanka AI. Mar walba la tasho dhakhtarka.",
        dataSavedForTriage = "✓ Xogta waa la keydiyay baaritaanka",
        measurementComplete = "Cabbirka waa dhammaaday"

    )

    val tswanaStrings = UiStrings(
        tabHome = "Gae",
        tabTriage = "Tlhatlhobo",
        tabSettings = "Dithulaganyo",
        jaundiceScreen = "Tlhatlhobo ya jaundice",
        preeclampsiaScreen = "Tlhatlhobo ya preeclampsia",
        respiratoryScreen = "Tlhatlhobo ya go hema",
        tapToMeasureHR = "Tobetsa fano go lekanya pelo",
        tapToCaptureEyelid = "Tobetsa fano go tsaya setshwantsho sa leitlho",
        tapToCaptureFace = "Tobetsa fano go tsaya setshwantsho sa sefatlhego",
        tapToCaptureEye = "Tobetsa fano go tsaya setshwantsho sa leitlho",
        tapToRecordCough = "Tobetsa fano go gatisa mokgotlhelo",
        downloadingModel = "Go tsenya MedGemma…",
        downloadSlowWarning = "App e ka nna bonya fa modele ya AI e ntse e tsenywa.",
        downloadFailedWarning = "Modele ya AI ga e a kgona go tsenywa. Tlhatlhobo e tla dirisa melao.",
        disclaimer = "Sedirisiwa sa tlhatlhobo sa AI. Botsa ngaka ka metlha.",
        dataSavedForTriage = "✓ Data e bolokilwe go tlhatlhobo",
        measurementComplete = "Go lekanya go fedile"

    )

    val portugueseStrings = UiStrings(
        appSubtitle = "Triagem de sinais vitais por câmara",
        tabHome = "Início",
        tabCardio = "Coração",
        tabAnemia = "Anemia",
        tabPreE = "PreE",
        tabTriage = "Triagem",
        tabSettings = "Definições",
        heartRate = "Frequência cardíaca",
        anemiaScreen = "Rastreio de anemia",
        jaundiceScreen = "Rastreio de icterícia",
        preeclampsiaScreen = "Rastreio de pré-eclâmpsia",
        respiratoryScreen = "Rastreio respiratório",
        tapToMeasureHR = "Toque aqui para medir a frequência cardíaca",
        tapToCaptureEyelid = "Toque aqui para capturar a pálpebra",
        tapToCaptureFace = "Toque aqui para capturar o rosto",
        tapToCaptureEye = "Toque aqui para capturar o olho",
        tapToRecordCough = "Toque aqui para gravar a tosse",
        screeningsProgress = "%d de 5 rastreios concluídos",
        readyForTriage = "✓ Pronto para triagem — vá ao separador Triagem",
        followSteps = "Siga os passos abaixo para rastrear um paciente",
        hrElevated = "⚠ Elevada — pode indicar stress ou anemia",
        hrLow = "⚠ Baixa — monitorizar de perto",
        hrNormal = "✓ Dentro dos limites normais",
        noPallor = "✓ Sem palidez detetada",
        noSwelling = "✓ Sem inchaço facial",
        normal = "Normal",
        mild = "Ligeiro",
        moderate = "Moderado",
        severe = "Grave",
        analyze = "Analisar",
        cancel = "Cancelar",
        downloadingModel = "A transferir MedGemma…",
        downloadSlowWarning = "A aplicação pode ficar mais lenta durante a transferência do modelo de IA.",
        downloadFailedWarning = "Não foi possível transferir o modelo de IA. A triagem usará avaliação baseada em regras.",
        disclaimer = "Ferramenta de rastreio assistida por IA. Consulte sempre um profissional de saúde.",
        dataSavedForTriage = "✓ Dados guardados para triagem",
        measurementComplete = "Medição concluída"

    )

    val arabicStrings = UiStrings(
        appSubtitle = "فحص العلامات الحيوية بالكاميرا",
        tabHome = "الرئيسية",
        tabCardio = "القلب",
        tabAnemia = "فقر الدم",
        tabPreE = "تسمم",
        tabTriage = "الفرز",
        tabSettings = "الإعدادات",
        heartRate = "معدل ضربات القلب",
        anemiaScreen = "فحص فقر الدم",
        jaundiceScreen = "فحص اليرقان",
        preeclampsiaScreen = "فحص تسمم الحمل",
        respiratoryScreen = "فحص التنفس",
        tapToMeasureHR = "اضغط هنا لقياس معدل ضربات القلب",
        tapToCaptureEyelid = "اضغط هنا لالتقاط صورة الجفن",
        tapToCaptureFace = "اضغط هنا لالتقاط صورة الوجه",
        tapToCaptureEye = "اضغط هنا لالتقاط صورة العين",
        tapToRecordCough = "اضغط هنا لتسجيل السعال",
        screeningsProgress = "%d من 5 فحوصات مكتملة",
        readyForTriage = "✓ جاهز للفرز — اذهب إلى علامة تبويب الفرز",
        followSteps = "اتبع الخطوات أدناه لفحص المريض",
        hrElevated = "⚠ مرتفع — قد يشير إلى توتر أو فقر دم",
        hrLow = "⚠ منخفض — راقب عن كثب",
        hrNormal = "✓ ضمن النطاق الطبيعي",
        noPallor = "✓ لم يتم اكتشاف شحوب",
        noSwelling = "✓ لا يوجد تورم في الوجه",
        normal = "طبيعي",
        mild = "خفيف",
        moderate = "متوسط",
        severe = "شديد",
        analyze = "تحليل",
        cancel = "إلغاء",
        downloadingModel = "جاري تنزيل MedGemma…",
        downloadSlowWarning = "قد يكون التطبيق أبطأ أثناء تنزيل نموذج الذكاء الاصطناعي.",
        downloadFailedWarning = "تعذر تنزيل نموذج الذكاء الاصطناعي. سيستخدم الفرز التقييم القائم على القواعد.",
        disclaimer = "أداة فحص بمساعدة الذكاء الاصطناعي. استشر دائماً أخصائي الرعاية الصحية.",
        dataSavedForTriage = "✓ تم حفظ البيانات للفرز",
        measurementComplete = "اكتمل القياس"

    )

    val tsongaStrings = UiStrings(
        tabHome = "Kaya",
        tabTriage = "Ku kambela",
        tabSettings = "Swiendlekano",
        jaundiceScreen = "Ku kambela ka jaundice",
        preeclampsiaScreen = "Ku kambela ka preeclampsia",
        respiratoryScreen = "Ku kambela ka ku hefemula",
        tapToMeasureHR = "Kanya laha ku pima mbilu",
        tapToCaptureEyelid = "Kanya laha ku teka xifaniso xa tihlo",
        tapToCaptureFace = "Kanya laha ku teka xifaniso xa xikandza",
        tapToCaptureEye = "Kanya laha ku teka xifaniso xa tihlo",
        tapToRecordCough = "Kanya laha ku rekhoda xikhohloyana",
        downloadingModel = "Ku downloda MedGemma…",
        downloadSlowWarning = "App yi nga ha yima loko modele ya AI yi downlodiwa.",
        downloadFailedWarning = "Modele ya AI a yi downlodiwanga. Ku kambela ku ta tirhisa milawu.",
        disclaimer = "Xitirhisiwa xa ku kambela xa AI. Vutisa n'anga nkarhana wun'wana.",
        dataSavedForTriage = "✓ Data yi hlayisiwile ku kambela",
        measurementComplete = "Ku pima ku herile"

    )

    val vendaStrings = UiStrings(
        tabHome = "Hayani",
        tabTriage = "U sedzulusa",
        tabSettings = "Nyito",
        jaundiceScreen = "U sedzulusa ha jaundice",
        preeclampsiaScreen = "U sedzulusa ha preeclampsia",
        respiratoryScreen = "U sedzulusa ha u vuwa",
        tapToMeasureHR = "Kwamani fhano u ela mbilu",
        tapToCaptureEyelid = "Kwamani fhano u dzhia tshifanyiso tsha ḽiṱo",
        tapToCaptureFace = "Kwamani fhano u dzhia tshifanyiso tsha tshifhaṱuwo",
        tapToCaptureEye = "Kwamani fhano u dzhia tshifanyiso tsha ḽiṱo",
        tapToRecordCough = "Kwamani fhano u rekhodisa tshikoho",
        downloadingModel = "MedGemma i khou ḓiselwa…",
        downloadSlowWarning = "App i nga vha yo ṱavha musi modele ya AI i tshi khou ḓiselwa.",
        downloadFailedWarning = "Modele ya AI a yo ngo kona u ḓiselwa. U sedzulusa hu ḓo shumisa milayo.",
        disclaimer = "Tshishumiswa tsha u sedzulusa tsha AI. Vhudzisani ṅanga tshifhinga tshoṱhe.",
        dataSavedForTriage = "✓ Data yo vhulungwa u sedzulusa",
        measurementComplete = "U ela ho fhela"

    )

    val swatiStrings = UiStrings(
        tabHome = "Ekhaya",
        tabTriage = "Kuhlola",
        tabSettings = "Tilungiselelo",
        jaundiceScreen = "Kuhlolwa kwe-jaundice",
        preeclampsiaScreen = "Kuhlolwa kwe-preeclampsia",
        respiratoryScreen = "Kuhlolwa kwekuphefumula",
        tapToMeasureHR = "Chafata lapha kulinganisa inhlitiyo",
        tapToCaptureEyelid = "Chafata lapha kutfola sitfombe seliso",
        tapToCaptureFace = "Chafata lapha kutfola sitfombe sebuso",
        tapToCaptureEye = "Chafata lapha kutfola sitfombe seliso",
        tapToRecordCough = "Chafata lapha kurekhodisha kukhohlela",
        downloadingModel = "Kulanda i-MedGemma…",
        downloadSlowWarning = "Luhlelo lungahamba kancane ngesikhatsi kulandwa imodeli ye-AI.",
        downloadFailedWarning = "Imodeli ye-AI ayikwazanga kulandwa. Kuhlolwa kutawusebentisa imitsetfo.",
        disclaimer = "Sifaneli sekuhlola se-AI. Buta dokotela ngaso sonkhe sikhatsi.",
        dataSavedForTriage = "✓ Idatha igcinwe kuhlolwa",
        measurementComplete = "Kulinganisa kuphelile"

    )

    val northernSothoStrings = UiStrings(
        tabHome = "Gae",
        tabTriage = "Tlhahlobo",
        tabSettings = "Dipeakanyo",
        jaundiceScreen = "Tlhahlobo ya jaundice",
        preeclampsiaScreen = "Tlhahlobo ya preeclampsia",
        respiratoryScreen = "Tlhahlobo ya go hema",
        tapToMeasureHR = "Kgotla mo go lekanya pelo",
        tapToCaptureEyelid = "Kgotla mo go tšea seswantšho sa leihlo",
        tapToCaptureFace = "Kgotla mo go tšea seswantšho sa sefahlego",
        tapToCaptureEye = "Kgotla mo go tšea seswantšho sa leihlo",
        tapToRecordCough = "Kgotla mo go rekhoda sefuba",
        downloadingModel = "Go tsenya MedGemma…",
        downloadSlowWarning = "App e ka ba bonya ge modele ya AI e ntše e tsenywa.",
        downloadFailedWarning = "Modele ya AI ga e a kgona go tsenywa. Tlhahlobo e tla šomiša melao.",
        disclaimer = "Sedirišwa sa tlhahlobo sa AI. Botšiša ngaka ka mehla.",
        dataSavedForTriage = "✓ Data e bolokilwe go tlhahlobo",
        measurementComplete = "Go lekanya go fedile"

    )

    val bembaStrings = UiStrings(
        tabHome = "Kuŋanda",
        tabTriage = "Ukupima",
        tabSettings = "Ifikala",
        jaundiceScreen = "Ukupima kwa jaundice",
        preeclampsiaScreen = "Ukupima kwa preeclampsia",
        respiratoryScreen = "Ukupima kwa kupuma",
        tapToMeasureHR = "Pama pano ukupima umutima",
        tapToCaptureEyelid = "Pama pano ukukwata icifanishingo ca linso",
        tapToCaptureFace = "Pama pano ukukwata icifanishingo ca busu",
        tapToCaptureEye = "Pama pano ukukwata icifanishingo ca linso",
        tapToRecordCough = "Pama pano ukulemba ukukolola",
        downloadingModel = "Ukukopela MedGemma…",
        downloadSlowWarning = "App ikakwata panono ilyo modeli ya AI ilikukopelwa.",
        downloadFailedWarning = "Modeli ya AI tailikukopelwa. Ukupima kukalabomfya amafunde.",
        disclaimer = "Icisebensho ca ukupima ca AI. Ipusha ŋanga inshita yonse.",
        dataSavedForTriage = "✓ Data yalembwa ukupima",
        measurementComplete = "Ukupima kwafika"

    )

    val tumbukaStrings = UiStrings(
        tabHome = "Kunyumba",
        tabTriage = "Kuyeza",
        tabSettings = "Masintha",
        jaundiceScreen = "Kuyeza jaundice",
        preeclampsiaScreen = "Kuyeza preeclampsia",
        respiratoryScreen = "Kuyeza kupuma mphepo",
        tapToMeasureHR = "Khomsani apa kuyeza mtima",
        tapToCaptureEyelid = "Khomsani apa kutora chithunzi cha diso",
        tapToCaptureFace = "Khomsani apa kutora chithunzi cha nkhope",
        tapToCaptureEye = "Khomsani apa kutora chithunzi cha diso",
        tapToRecordCough = "Khomsani apa kurekodha chifuwa",
        downloadingModel = "Kukopa MedGemma…",
        downloadSlowWarning = "Pulogalamu yikukhalira mutu modeli ya AI yikukopedwa.",
        downloadFailedWarning = "Modeli ya AI yilephera kukopedwa. Kuyeza kukagwiritsa ntchito malango.",
        disclaimer = "Chikwezeso cha AI. Finsani dotolo nyengo yose.",
        dataSavedForTriage = "✓ Data yasungidwa kuyeza",
        measurementComplete = "Kuyeza kwatheka"

    )

    val lubaKasaiStrings = UiStrings(
        tabHome = "Ku nzubu",
        tabTriage = "Kutala",
        tabSettings = "Bilondeshilu",
        jaundiceScreen = "Kutala kwa jaundice",
        preeclampsiaScreen = "Kutala kwa preeclampsia",
        respiratoryScreen = "Kutala kwa kuhema",
        tapToMeasureHR = "Fina pa apa kutala mutshima",
        tapToCaptureEyelid = "Fina pa apa kuangata tshifanyiso tsha disu",
        tapToCaptureFace = "Fina pa apa kuangata tshifanyiso tsha mpala",
        tapToCaptureEye = "Fina pa apa kuangata tshifanyiso tsha disu",
        tapToRecordCough = "Fina pa apa kulembela kushikuta",
        downloadingModel = "MedGemma udi ukuselua…",
        downloadSlowWarning = "App udi ukuya bulelela mu tshikondo tshia modeli AI ukuselua.",
        downloadFailedWarning = "Modeli AI kayivua mukuya kuselua. Kutala kudi ne kusadikila mashinyi.",
        disclaimer = "Tshikwezeu tsha kutala tsha AI. Ebeja muganga ntshikondo yosele.",
        dataSavedForTriage = "✓ Data udi ulembedibue kutala",
        measurementComplete = "Kutala kudi ne kufika"

    )

    val kuanyamaStrings = UiStrings(
        tabHome = "Megumbo",
        tabTriage = "Okukondjitha",
        tabSettings = "Eengundafano",
        jaundiceScreen = "Okukondjitha jaundice",
        preeclampsiaScreen = "Okukondjitha preeclampsia",
        respiratoryScreen = "Okukondjitha okufuda",
        tapToMeasureHR = "Kunyata mpaka oku okupima omutima",
        tapToCaptureEyelid = "Kunyata mpaka oku okukuata efano leliho",
        tapToCaptureFace = "Kunyata mpaka oku okukuata efano loshipa",
        tapToCaptureEye = "Kunyata mpaka oku okukuata efano leliho",
        tapToRecordCough = "Kunyata mpaka oku okunyola okukohola",
        downloadingModel = "MedGemma tai shitwa…",
        downloadSlowWarning = "App otai dulu okuya kanini AI modeli tai shitwa.",
        downloadFailedWarning = "AI modeli kai dulile okushitwa. Okukondjitha otaku longitha oompango.",
        disclaimer = "Oshihalifa shokukondjitha sha AI. Pula ndokotola alushe.",
        dataSavedForTriage = "✓ Data oyi shitwa okukondjitha",
        measurementComplete = "Okupima okwa pita"

    )

}
