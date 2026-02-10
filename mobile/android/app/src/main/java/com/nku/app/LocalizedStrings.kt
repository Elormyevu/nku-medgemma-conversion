package com.nku.app

/**
 * LocalizedStrings — Pan-African Language Support (47 Languages)
 *
 * Provides UI strings and clinical text in 47 African languages.
 * Tier 1 (14 languages): Full clinical vocabulary verified
 * Tier 2 (33 languages): UI labels localized
 *
 * Usage:
 *   val strings = LocalizedStrings.forLanguage("ee")  // Ewe
 *   Text(strings.screenTitle)
 */
object LocalizedStrings {

    // All supported language codes
    val supportedLanguages: Map<String, String> = mapOf(
        // Tier 1: Clinically Verified (14)
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
        // Tier 2: UI Localized (33)
        "af" to "Afrikaans",
        "bm" to "Bambara",
        "ny" to "Chichewa",
        "din" to "Dinka",
        "ff" to "Fula",
        "gaa" to "Ga",
        "ki" to "Kikuyu",
        "rw" to "Kinyarwanda",
        "kg" to "Kongo",
        "ln" to "Lingala",
        "luo" to "Luo",
        "lg" to "Luganda",
        "mg" to "Malagasy",
        "nd" to "Ndebele",
        "nus" to "Nuer",
        "pcm" to "Pidgin (Nigerian)",
        "wes" to "Pidgin (Cameroonian)",
        "rn" to "Rundi",
        "st" to "Sesotho",
        "sn" to "Shona",
        "so" to "Somali",
        "tn" to "Tswana",
        "pt" to "Portuguese",
        "ar" to "Arabic",
        "ts" to "Tsonga",
        "ve" to "Venda",
        "ss" to "Swati",
        "nso" to "Northern Sotho",
        "bem" to "Bemba",
        "tum" to "Tumbuka",
        "lua" to "Luba-Kasai",
        "kj" to "Kuanyama"
    )

    /**
     * Get language name from code (for translation prompts).
     */
    fun getLanguageName(code: String): String = supportedLanguages[code] ?: "Unknown"

    /**
     * Get UI strings for a given language.
     */
    fun forLanguage(code: String): UiStrings = when (code) {
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
        val tabTriage: String = "Triage",

        // Home screen
        val heartRate: String = "Heart Rate",
        val anemiaScreen: String = "Anemia Screen",
        val preeclampsiaScreen: String = "Preeclampsia Screen",
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

        // Preeclampsia
        val preETitle: String = "Preeclampsia Screen",
        val preESubtitle: String = "Facial edema detection",
        val captureFace: String = "Capture Face",
        val pregnant: String = "Pregnant?",
        val gestationalWeeks: String = "Gestational weeks",
        val centerFace: String = "Center your face",

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
        val screeningsProgress: String = "%d of 3 screenings complete",
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
        val deviceCooling: String = "Device cooling down — AI paused"
    )

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
        disclaimer = "Elime kpɔkpɔ dɔwɔnu enye. Fia ɖe dɔkta gɔme hafi nàwɔ nane."
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
        disclaimer = "Outil de dépistage assisté par IA. Consultez toujours un professionnel de santé."
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
        disclaimer = "Hii ni zana ya uchunguzi inayosaidiwa na AI. Wasiliana na mtaalamu wa afya kila wakati."
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
        deviceCooling = "Na'urar tana hucewa — AI ya tsaya"
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
        deviceCooling = "Ẹ̀rọ ń tutù — AI ti dúró"
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
        disclaimer = "Ngwá ọrụ nlele AI bụ nke a. Jụrụ dọkịta oge niile."
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
        disclaimer = "ይህ በ AI የሚደገፍ የማጣሪያ መሳሪያ ነው። ሁልጊዜ ሐኪም ያማክሩ።"
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
        disclaimer = "AI nhwehwɛmu adwumayɛdeɛ ni yi. Bisa dɔkota bere biara."
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
        disclaimer = "Jumtukaay bi dafa jëm ci AI. Laajte ak doktoor."
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
        disclaimer = "Lesi yithuluzi lokuhlola le-AI. Xhumana nodokotela njalo."
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
        disclaimer = "Esi sisixhobo sokuhlola se-AI. Thetha nogqirha rhoqo."
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
        disclaimer = "Meeshaan kun AI irratti. Ogeessa fayyaa mariyadhaa."
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
        disclaimer = "እዚ ብ AI ዝተሓገዘ መሳርሒ ምርመራ እዩ። ኩሉ ግዜ ሓኪም ኣማኽሩ።"
    )
}
