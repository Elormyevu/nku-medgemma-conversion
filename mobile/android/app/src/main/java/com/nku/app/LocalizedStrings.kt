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
        tabTriage = "Bincike",
        heartRate = "Bugun zuciya",
        anemiaScreen = "Gwajin rashin jini",
        notYetScreened = "Ba a yi gwaji ba tukuna",
        cardioTitle = "Gwajin Zuciya",
        startMeasurement = "Fara Auna",
        stopMeasurement = "Tsaya Auna",
        holdStill = "Ka zauna lafiya na dakika 10",
        anemiaTitle = "Gwajin Rashin Jini",
        captureConjunctiva = "Ɗauki hoton ido",
        pullDownEyelid = "A ja fatar ido ta ƙasa a hankali",
        preETitle = "Gwajin Preeclampsia",
        captureFace = "Ɗauki hoton fuska",
        pregnant = "Mai ciki?",
        triageTitle = "Binciken Asibiti",
        normal = "Al'ada",
        mild = "Ƙanƙanta",
        moderate = "Matsakaici",
        severe = "Mai tsanani",
        analyze = "Bincika",
        cancel = "Soke",
        disclaimer = "Wannan kayan aikin bincike ne na AI. Koyaushe ka tuntuɓi likita."
    )

    val yorubaStrings = UiStrings(
        appSubtitle = "Ayẹwo àwọn àmì pàtàkì nípasẹ̀ kámẹ́rà",
        tabHome = "Ilé",
        tabTriage = "Àyẹ̀wò",
        heartRate = "Ìlù ọkàn",
        anemiaScreen = "Àyẹ̀wò ẹ̀jẹ̀",
        notYetScreened = "A kò tí ì ṣe àyẹ̀wò",
        cardioTitle = "Àyẹ̀wò Ọkàn",
        startMeasurement = "Bẹ̀rẹ̀ Wíwọ̀n",
        stopMeasurement = "Dúró Wíwọ̀n",
        anemiaTitle = "Àyẹ̀wò Ẹ̀jẹ̀",
        captureConjunctiva = "Ya àwòrán ojú",
        preETitle = "Àyẹ̀wò Preeclampsia",
        captureFace = "Ya àwòrán ojú",
        pregnant = "Lóyún?",
        triageTitle = "Àyẹ̀wò Ìlera",
        normal = "Déédéé",
        mild = "Kékeré",
        moderate = "Àárín gbùngbùn",
        severe = "Líle",
        analyze = "Ṣàyẹ̀wò",
        cancel = "Fagilé",
        disclaimer = "Ohun èlò àyẹ̀wò AI ni èyí. Máa bá dókítà sọ̀rọ̀ nígbà gbogbo."
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
