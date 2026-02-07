# Nku Architecture

This document describes the technical architecture of the Nku Clinical AI system.

## Overview

Nku is an **offline-first Android application** that provides medical triage in resource-constrained environments. The system operates entirely on-device, requiring no network connectivity for core clinical functionality.

## System Requirements

| Component | Minimum | Recommended |
|:----------|:--------|:------------|
| **RAM** | 2GB | 4GB |
| **Storage** | 4GB | 8GB |
| **Android** | 8.0 (API 26) | 12+ (API 31+) |
| **Network** | None | Optional (for updates) |

## Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                      │
├─────────────────────────────────────────────────────────────┤
│  MainActivity.kt                                             │
│  ├── NkuSentinelApp()         (Jetpack Compose)             │
│  ├── LocalizedStrings.kt      (47 languages)                │
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
│  PiperTTS.kt                                                 │
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

The core innovation is the **Nku Cycle** — a memory-efficient orchestration pattern that performs clinical triage within a 2GB RAM budget.

### Flow

1. **Input**: User enters symptoms in local language (e.g., Twi)
2. **Translation**: TranslateGemma converts to English
3. **Reasoning**: MedGemma performs clinical triage
4. **Localization**: TranslateGemma converts result back to local language
5. **Output**: Piper TTS speaks the result

### Memory Management

```kotlin
// Only one model loaded at a time
fun runNkuCycleLocal(patientInput: String, language: String): NkuResult {
    // Step 1: Load TranslateGemma, translate to English
    loadModel(translateGemmaPath)
    val englishSymptoms = runCompletion(translatePrompt)
    unloadModel()  // Free ~0.5GB
    
    // Step 2: Load MedGemma, perform triage
    loadModel(medGemmaPath)
    val triage = runCompletion(triagePrompt)
    unloadModel()  // Free ~0.8GB
    
    // Step 3: Load TranslateGemma, localize result
    loadModel(translateGemmaPath)
    val localizedResult = runCompletion(localizePrompt)
    unloadModel()
    
    return NkuResult(triage, localizedResult)
}
```

## Model Architecture

### MedGemma 4B (IQ1_M)

- **Base**: google/medgemma-4b-it
- **Quantization**: IQ1_M (1-bit with importance matrix)
- **Size**: 0.78 GB
- **Purpose**: Clinical reasoning and symptom triage

### TranslateGemma 4B (IQ1_M)

- **Base**: google/gemma-2-4b (prompt-engineered for translation)
- **Quantization**: IQ1_M
- **Size**: 0.51 GB
- **Purpose**: Bi-directional Pan-African language translation

### Piper TTS

- **Format**: ONNX Runtime Mobile
- **Size**: ~20 MB per voice
- **Voices**: Swahili, English, French, Portuguese (expandable)

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

# 3. Quantize with calibration
./llama-quantize \
    medgemma-4b-f16.gguf \
    medgemma-4b-iq1_m.gguf \
    IQ1_M \
    --imatrix medgemma-medical.imatrix
```

## Directory Structure

```
mobile/android/app/src/main/
├── java/com/nku/app/
│   ├── MainActivity.kt         # UI + Compose (Jetpack Compose)
│   ├── NkuInferenceEngine.kt   # Nku Cycle model orchestration
│   ├── RPPGProcessor.kt        # Heart rate via rPPG
│   ├── PallorDetector.kt       # Anemia via conjunctival pallor
│   ├── EdemaDetector.kt        # Preeclampsia via facial edema
│   ├── SensorFusion.kt         # Vital signs aggregation
│   ├── ClinicalReasoner.kt     # MedGemma prompts + WHO/IMCI fallback
│   ├── ThermalManager.kt       # 42°C auto-throttle
│   ├── LocalizedStrings.kt     # 47-language UI strings
│   ├── PiperTTS.kt             # Voice synthesis
│   └── CloudInferenceClient.kt # Cloud fallback (dev/emulator only)
├── assets/
│   ├── medgemma-4b-iq1_m.gguf  # Clinical model (bundled or downloaded)
│   └── translategemma-4b-iq1_m.gguf # Translation model
└── jniLibs/
    ├── arm64-v8a/libsmollm.so  # ARM64 native library
    └── x86_64/libsmollm.so     # Emulator native library
```

## Performance Characteristics

| Metric | Value | Notes |
|:-------|:------|:------|
| **Model Load** | ~1.4s | Memory-mapped GGUF |
| **Inference** | 4-6 tok/s | Pure ARM CPU |
| **Full Cycle** | 15-30s | End-to-end triage |
| **Peak RAM** | ~1.4 GB | Single model active |
| **APK Size** | 2.7 GB | Debug with bundled models |

## Safety Guardrails

1. **Abstention**: Model abstains if confidence < 75%
2. **Severity Classification**: High/Medium/Low with escalation guidance
3. **Disclaimer**: "Consult a healthcare professional" always shown
4. **Privacy**: All processing on-device, no data transmission
