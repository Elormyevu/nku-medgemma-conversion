# Nku Architecture

This document describes the technical architecture of the Nku Clinical AI system.

## Overview

Nku is an **offline-first Android application** that provides medical triage in resource-constrained environments. The system operates entirely on-device, requiring no network connectivity for core clinical functionality.

## System Requirements

| Component | Minimum | Recommended |
|:----------|:--------|:------------|
| **RAM** | 2GB | 4GB |
| **Storage** | 4GB | 8GB |
| **Android** | 9.0 (API 28) | 12+ (API 31+) |
| **Network** | None | Optional (for updates) |

## Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                      │
├─────────────────────────────────────────────────────────────┤
│  MainActivity.kt                                             │
│  ├── NkuSentinelApp()         (Jetpack Compose)             │
│  ├── LocalizedStrings.kt      (46 languages)                │
│  └── GlassCard/VitalCard      (Premium UI components)       │
├─────────────────────────────────────────────────────────────┤
│                      BUSINESS LOGIC LAYER                    │
├─────────────────────────────────────────────────────────────┤
│  NkuInferenceEngine.kt                                       │
│  ├── areModelsReady()         (Model discovery)             │
│  ├── runNkuCycle()            (Full triage cycle)           │
│  ├── loadModel()              (GGUF loading via mmap)       │
│  └── unloadModel()            (RAM management)              │
├─────────────────────────────────────────────────────────────┤
│  CloudInferenceClient.kt      (Cloud fallback for emulators) │
├─────────────────────────────────────────────────────────────┤
│  NkuTTS.kt                                                    │
│  ├── speak()                  (Text-to-speech)              │
│  └── getVoiceForLanguage()    (Language selection)          │
├─────────────────────────────────────────────────────────────┤
│                      NATIVE LAYER                            │
├─────────────────────────────────────────────────────────────┤
│  SmolLM.kt (JNI Bridge)                                      │
│  ├── init()                   (GGUF context creation)       │
│  ├── getResponse()            (Token generation)            │
│  └── close()                  (Resource cleanup)            │
├─────────────────────────────────────────────────────────────┤
│  libsmollm.so (llama.cpp)                                    │
│  ├── ARM64-v8a               (NEON/SME optimized)           │
│  └── x86_64                  (Emulator support)             │
└─────────────────────────────────────────────────────────────┘
```

## The Nku Cycle

The core innovation is the **Nku Cycle** — a memory-efficient orchestration pattern that performs clinical triage on budget devices (2–3GB RAM). MedGemma is loaded via `mmap` (pages loaded on demand by the OS), while ML Kit handles translation in a separate process — eliminating the model-swapping overhead of the earlier TranslateGemma approach.

### Flow

1. **Input**: User enters symptoms in local language (e.g., Twi)
2. **Translation**: Android ML Kit translates to English (on-device for 59 languages; Cloud Translate fallback for indigenous languages)
3. **Reasoning**: MedGemma performs clinical triage (100% on-device)
4. **Localization**: ML Kit / Cloud Translate converts result back to local language
5. **Output**: Android System TTS speaks the result

All African official languages (English, French, Portuguese) are fully on-device via ML Kit — CHWs always have a fully offline path.

### Memory Management

```kotlin
// MedGemma loaded via mmap; ML Kit handles translation separately
fun runNkuCycleLocal(patientInput: String, language: String): NkuResult {
    // Step 1: ML Kit translates to English (on-device, ~30MB/lang)
    val englishSymptoms = mlKitTranslate(patientInput, language, "en")
    
    // Step 2: Load MedGemma, perform triage (100% on-device)
    loadModel(medGemmaPath)
    val triage = runCompletion(triagePrompt)
    unloadModel()  // Free ~2.3GB
    
    // Step 3: ML Kit translates result back
    val localizedResult = mlKitTranslate(triage, "en", language)
    
    return NkuResult(triage, localizedResult)
}
```

## Model Architecture

### MedGemma 4B (Q4_K_M)

- **Base**: google/medgemma-4b-it
- **Quantization**: Q4_K_M (4-bit with importance matrix)
- **Size**: 2.3 GB
- **MedQA Accuracy**: 56% (81% of published 69% baseline)
- **Purpose**: Clinical reasoning and symptom triage (100% on-device)

### Android ML Kit Translation

- **Type**: Google ML Kit on-device translation API
- **Size**: ~30 MB per language pack
- **Supported languages**: 59 (including English, French, Portuguese, Afrikaans, Swahili)
- **Fallback**: Google Cloud Translate API for unsupported indigenous languages (Twi, Hausa, Yoruba)
- **Purpose**: Bi-directional language translation

### Android System TTS (NkuTTS.kt)

- **Format**: Android platform TextToSpeech API
- **Size**: 0 MB (uses device-installed TTS engine)
- **Languages**: All languages supported by device Google TTS

## Quantization Pipeline

```bash
# 1. Convert HuggingFace to GGUF
python llama.cpp/convert_hf_to_gguf.py \
    google/medgemma-4b-it \
    --outfile medgemma-4b-f16.gguf

# 2. Generate medical calibration imatrix
./llama-imatrix \
    -m medgemma-4b-f16.gguf \
    -f calibration/african_primary_care.txt \
    --chunks 64 \
    -o medgemma-medical.imatrix

# 3. Quantize with calibration (Q4_K_M — see Appendix D for comparison)
./llama-quantize \
    medgemma-4b-f16.gguf \
    medgemma-4b-Q4_K_M.gguf \
    Q4_K_M \
    --imatrix medgemma-medical.imatrix
```

## Directory Structure

```
mobile/android/app/src/main/
├── java/com/nku/app/
│   ├── MainActivity.kt         # UI + Compose (Jetpack Compose)
│   ├── NkuInferenceEngine.kt   # MedGemma orchestration
│   ├── NkuTranslator.kt        # ML Kit translation wrapper
│   ├── RPPGProcessor.kt        # Heart rate via rPPG
│   ├── PallorDetector.kt       # Anemia via conjunctival pallor
│   ├── EdemaDetector.kt        # Preeclampsia via facial edema
│   ├── SensorFusion.kt         # Vital signs aggregation
│   ├── ClinicalReasoner.kt     # MedGemma prompts + WHO/IMCI fallback
│   ├── ThermalManager.kt       # 42°C auto-throttle
│   ├── LocalizedStrings.kt     # 46-language UI strings
│   ├── NkuTTS.kt               # Android System TTS wrapper
│   └── CloudInferenceClient.kt # Cloud fallback (dev/emulator only)
├── assets/                      # (models loaded from device storage)
└── jniLibs/
    ├── arm64-v8a/libsmollm.so  # ARM64 native library
    └── x86_64/libsmollm.so     # Emulator native library
```

## Performance Characteristics

| Metric | Value | Notes |
|:-------|:------|:------|
| **Model Load** | ~2s | Memory-mapped Q4_K_M GGUF |
| **Inference** | 4-6 tok/s | Pure ARM CPU |
| **Full Cycle** | 15-30s | End-to-end triage |
| **Peak RAM** | ~2.3 GB | MedGemma active (ML Kit translation is lightweight) |
| **APK Size** | ~60 MB base | + MedGemma via PAD or sideloading |

## Safety Guardrails

1. **Abstention**: Sensors below 75% confidence excluded from triage (`ClinicalReasoner.CONFIDENCE_THRESHOLD`)
2. **Severity Classification**: High/Medium/Low with escalation guidance
3. **Disclaimer**: "Consult a healthcare professional" always shown
4. **Privacy**: All processing on-device, no data transmission
