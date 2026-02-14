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
        val patientSymptoms: String = "Patient-Reported Symptoms",
        val micOrType: String = "Type or tap the mic to speak symptoms",
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
        val lowConfidenceWarning: String = "\u26A0 Low confidence \u2014 this reading may be excluded from triage. Re-capture in better lighting."
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
    }

    // ─── Tier 1 Languages ───────────────────────────────────────

    val englishStrings = UiStrings()  // Default

    val eweStrings = UiStrings(
        appSubtitle = "Kamera dzi gbugbɔgbalẽ ƒe nukpɔkpɔ",
        tabHome = "Aƒeme",
        tabCardio = "Dzi",
        tabAnemia = "Ʋu",
        tabTriage = "Kpɔkpɔ",
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
        lowConfidenceWarning = "\u26A0 Ŋuɖoɖo me ga o — nusi wotsoe ƒe axa makpɔ ɖe kpɔkpɔ me o. Tsɔ foto bubu le kekeli nyui me."
    )

    val frenchStrings = UiStrings(
        appSubtitle = "Dépistage des signes vitaux par caméra",
        tabHome = "Accueil",
        tabTriage = "Triage",
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
        lowConfidenceWarning = "\u26A0 Confiance faible \u2014 cette mesure pourrait être exclue du triage. Recapturez avec un meilleur éclairage."
    )

    val swahiliStrings = UiStrings(
        appSubtitle = "Uchunguzi wa dalili za maisha kwa kamera",
        tabHome = "Nyumbani",
        tabTriage = "Hatua",
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
        lowConfidenceWarning = "\u26A0 Uhakika mdogo \u2014 usomaji huu unaweza kutengwa na hatua. Chukua picha tena katika mwanga bora."
    )

    val hausaStrings = UiStrings(
        appSubtitle = "Nazarin alamomin lafiya ta kyamara",
        tabHome = "Gida",
        tabCardio = "Zuciya",
        tabAnemia = "Jini",
        tabPreE = "Ciki",
        tabTriage = "Bincike",
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
        screeningsProgress = "%d cikin 3 gwaje-gwaje an kammala",
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
        lowConfidenceWarning = "\u26A0 Tabbaci ya yi ƙasa \u2014 wannan sakamakon bazai shiga bincike ba. Sake ɗauka a haske mai kyau."
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
        lowConfidenceWarning = "\u26A0 Ìgbàgbọ́ kéré jù \u2014 àbájáde yìí lè máa kòpà nínú àyẹ̀wò. Tún ya nínú ìmọ́lẹ̀ tó dára."
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
        lowConfidenceWarning = "\u26A0 Ễkwèsịrị dị ala \u2014 a gaghị etinye nke a na nlele. Tugharịa na ọ kụ karịa."
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
        lowConfidenceWarning = "\u26A0 የተባበሮ ሙንጩ ዝቅተኛ \u2014 ይህ ስዋመ ወደ ምርመራ ላይጋበር ይችላል። በተሽለ ብርሃን ዳግም አንሱ።"
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
        lowConfidenceWarning = "\u26A0 Ŋuɖoɖo sɔ \u2014 ebia wɔrenfa nkyerɛwdeɛ yi nhwɛhwɛmu no mu. San kɔ foto no wɔ hann nyinaa mu."
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
        lowConfidenceWarning = "\u26A0 Gis-gis bu néew \u2014 natalu bii bées na ko ci saytul bi. Def ko kenn ci leer bu baax."
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
        lowConfidenceWarning = "\u26A0 Ithemba eliphansi \u2014 lokhu kungase kungafakwa ekuhlolweni. Thatha kabusha ekukhanyeni okuhle."
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
        lowConfidenceWarning = "\u26A0 Ukuthemba okuphantsi \u2014 oku kungangeniswa ekuhlolweni. Thatha kwakhona ekukhanyeni okuhle."
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
        lowConfidenceWarning = "\u26A0 Amantaa gad-aanaa \u2014 lakkoofsi kun qorannoo keessa hin galuu ta'uu. Ifa keessatti irra deebi'i."
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
        lowConfidenceWarning = "\u26A0 ትሑት ዙሁል ኢሉ \u2014 እዚ ንባብ ኣብ ምርመራ ኣይእተውን ይኽእል። ኣብ ጽቡሕ ብርሃን ዳግም ኣንሱ።"
    )
}
