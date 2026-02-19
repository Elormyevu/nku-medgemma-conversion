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
│  PromptSanitizer.kt           (6-layer prompt injection      │
│                                defense at every boundary)    │
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

The core innovation is the **Nku Cycle** — a memory-efficient orchestration pattern that performs clinical triage on budget devices (3–4GB RAM). MedGemma is loaded via `mmap` (pages loaded on demand by the OS), while ML Kit handles translation in a separate process — eliminating the model-swapping overhead of the earlier TranslateGemma approach.

### Flow

1. **Input**: User enters symptoms in local language (e.g., Twi)
2. **Translation**: Android ML Kit translates to English for supported languages; unsupported languages pass through unchanged in offline mode
3. **Reasoning**: MedGemma performs clinical triage (100% on-device)
4. **Localization**: ML Kit localizes output for supported languages; otherwise the English result is returned
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
- **MedQA Accuracy**: 56% quantized (81% of unquantized 69% baseline)
- **Purpose**: Clinical reasoning and symptom triage (100% on-device)

### Android ML Kit Translation

- **Type**: Google ML Kit on-device translation API
- **Size**: ~30 MB per language pack
- **Supported languages**: 59 (including English, French, Portuguese, Afrikaans, Swahili)
- **Unsupported-language behavior**: Input/output pass-through in the current mobile build (optional cloud translation backend is separate)
- **Purpose**: Bi-directional language translation

### Android System TTS (NkuTTS.kt)

- **Format**: Android platform TextToSpeech API
- **Size**: 0 MB (uses device-installed TTS engine)
- **Languages**: All languages supported by device Google TTS

### HeAR Pipeline (RespiratoryDetector.kt)

#### Event Detector (shipped)
- **Base**: HeAR MobileNetV3-Small
- **Format**: TFLite INT8
- **Size**: 1.1 MB
- **Input**: 1×32000 float32 (2s audio @16kHz)
- **Output**: 1×8 float32 (health sound class probabilities)
- **Purpose**: Rapid cough/breath classification (~50ms). Always loaded. It acts as the primary respiratory triage mechanism, classifying 8 distinct acoustic events (Cough, Snore, Baby Cough, Breathe, etc.) to generate a synthetic respiratory risk score based on abnormal breathing patterns and cough prevalence.

#### ViT-L Encoder (future upgrade — not shipped)
- **Base**: HeAR ViT-L Masked AutoEncoder
- **Status**: ❌ Cannot be converted to mobile format and **IS NOT** shipped in the app.
- **Blocker**: Uses `XlaCallModule` with serialized StableHLO bytecode — no current tool (tf2onnx, TFLite converter) supports conversion to ONNX or TFLite.
- **Codebase support**: Full architectural integration exists (ONNX Runtime Mobile, on-demand loading, sequential RAM management) but the model itself is absent.
- **Activation**: When Google's AI Edge toolchain supports StableHLO-to-TFLite conversion.
- **Reference**: Tobin et al., arXiv 2403.02522, 2024

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

# 3a. IQ2_XS quantization with medical imatrix (benchmarking — see Appendix D)
./llama-quantize \
    medgemma-4b-f16.gguf \
    medgemma-4b-IQ2_XS.gguf \
    IQ2_XS \
    --imatrix medgemma-medical.imatrix

# 3b. Q4_K_M — standard quantization (deployed model)
# Downloaded pre-quantized from mradermacher/medgemma-4b-it-GGUF
huggingface-cli download mradermacher/medgemma-4b-it-GGUF medgemma-4b-it.Q4_K_M.gguf
```

## Directory Structure

```
mobile/android/app/src/main/
├── java/com/nku/app/
│   ├── MainActivity.kt         # UI + Compose (Jetpack Compose)
│   ├── NkuInferenceEngine.kt   # MedGemma orchestration
│   ├── NkuTranslator.kt        # ML Kit translation wrapper
│   ├── RPPGProcessor.kt        # Heart rate via rPPG
│   ├── RespiratoryDetector.kt  # TB/Respiratory (HeAR Event Detector; ViT-L = future upgrade)
│   ├── PallorDetector.kt       # Anemia via conjunctival pallor
│   ├── JaundiceDetector.kt     # Jaundice via scleral icterus
│   ├── EdemaDetector.kt        # Preeclampsia via facial edema
│   ├── SensorFusion.kt         # Vital signs aggregation
│   ├── ClinicalReasoner.kt     # MedGemma prompts + WHO/IMCI fallback
│   ├── PromptSanitizer.kt      # 6-layer prompt injection defense
│   ├── ThermalManager.kt       # 42°C auto-throttle
│   ├── LocalizedStrings.kt     # 46-language UI strings
│   ├── NkuTTS.kt               # Android System TTS wrapper
│   ├── CameraPreview.kt        # Camera2 preview composable
│   ├── FaceDetectorHelper.kt   # MediaPipe face landmark wrapper
│   └── screens/                # CardioScreen, AnemiaScreen, JaundiceScreen, PreeclampsiaScreen, TriageScreen
├── assets/                      # HeAR Event Detector TFLite (1.1MB, bundled in APK)
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
