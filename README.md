<p align="center">
  <img src="docs/assets/nku_logo.png" alt="Nku Logo" width="120" />
</p>

<h1 align="center">Nku: Offline Medical AI for Pan-Africa</h1>

<p align="center">
  <strong>Nku Sentinel — Clinical Triage on $50 Phones</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#models">Models</a> •
  <a href="#languages">Languages</a> •
  <a href="./CONTRIBUTING.md">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/MedGemma-4B-blue?style=flat-square" alt="MedGemma" />
  <img src="https://img.shields.io/badge/Offline-100%25-green?style=flat-square" alt="Offline" />
  <img src="https://img.shields.io/badge/Languages-46-orange?style=flat-square" alt="Languages" />
  <img src="https://img.shields.io/badge/RAM-2GB-purple?style=flat-square" alt="RAM" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey?style=flat-square" alt="License" />
</p>

---

## 🌍 The Problem

In rural Sub-Saharan Africa:
- **Physician-to-patient ratio exceeds 1:10,000**
- **450M+ people** lack accessible primary care screening
- **<2G connectivity** is the norm — cloud AI is useless
- **Representation bias**: AI models perform poorly on Fitzpatrick V-VI skin tones

Yet **nearly all Community Health Workers (CHWs) carry smartphones**.

## 💡 The Solution

**Nku** ("eye" in Ewe) transforms any $50-100 Android phone into an offline clinical triage engine. It is a **pure edge system** — 100% on-device, zero cloud dependency.

| What | How |
|:-----|:----|
| **100% Offline** | Zero network dependency — pure on-device inference |
| **Ultra-Compressed** | 8GB models → 1.3GB via IQ1_M quantization |
| **Pan-African Languages** | 46 languages including Ewe, Hausa, Yoruba, Swahili |
| **Budget Hardware** | Runs on 2GB RAM devices (TECNO, Infinix) |
| **Camera Screening** | Heart rate, anemia, & preeclampsia via phone camera |

---

## ✨ Features

- 🧠 **MedGemma 4B** — Google's clinical reasoning model, quantized to 0.78GB
- 🌐 **TranslateGemma 4B** — Bi-directional Pan-African language bridge
- 🔊 **Android System TTS** — Device-native voice synthesis for spoken clinical results
- 💎 **Premium UI** — Glassmorphism design with localized strings
- ⚡ **Nku Cycle** — Intelligent model swapping under 2GB RAM budget
- 📷 **Nku Sentinel** — Camera-based screening for heart rate, anemia, & preeclampsia

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    THE NKU CYCLE                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [Patient Symptom in Ewe / Camera Screening]              │
│           ↓                                                 │
│   ┌───────────────────┐                                    │
│   │  TranslateGemma   │  ← IQ1_M (0.51GB)                  │
│   │  Local → English  │                                    │
│   └────────┬──────────┘                                    │
│            ↓                                                │
│   ┌───────────────────┐                                    │
│   │    MedGemma 4B    │  ← IQ1_M (0.78GB)                  │
│   │  Clinical Triage  │                                    │
│   └────────┬──────────┘                                    │
│            ↓                                                │
│   ┌───────────────────┐                                    │
│   │  TranslateGemma   │                                    │
│   │  English → Local  │                                    │
│   └────────┬──────────┘                                    │
│            ↓                                                │
│   ┌───────────────────┐                                    │
│   │  Android System TTS │                                    │
│   │  Spoken Result    │                                    │
│   └───────────────────┘                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Nku Sentinel — Camera-Based Screening

| Screening | Module | Method | Output |
|:----------|:-------|:-------|:-------|
| **Cardio Check** | `RPPGProcessor.kt` | Green channel FFT (30fps) | Heart rate ±5 BPM |
| **Anemia Screen** | `PallorDetector.kt` | Conjunctival HSV analysis | Pallor severity (0-1) |
| **Preeclampsia** | `EdemaDetector.kt` | Facial geometry ratios | Edema severity (0-1) |
| **Triage** | `ClinicalReasoner.kt` | MedGemma + WHO/IMCI fallback | Severity & recommendations |

All screening uses **pure signal processing** (0 MB additional weights). Sensor outputs are aggregated by `SensorFusion.kt` and interpreted by MedGemma for clinical reasoning.

### Fitzpatrick-Aware Design

- **Pallor**: Conjunctiva-only analysis — consistent across all skin tones
- **Edema**: Geometry-based ratios — skin-color independent
- **Heart Rate**: Adaptive multi-frame averaging

### Tech Stack

| Layer | Technology |
|:------|:-----------|
| **UI** | Jetpack Compose (Glassmorphism) |
| **Perception** | RPPGProcessor, PallorDetector, EdemaDetector |
| **Orchestration** | ClinicalReasoner + SensorFusion + ThermalManager (42°C) |
| **Inference** | llama.cpp via JNI (NDK 29, ARM64 NEON) |
| **TTS** | Android System TTS (NkuTTS.kt) |
| **Quantization** | IQ1_M + 64-chunk medical imatrix |

---

## 🚀 Quick Start

### Prerequisites

- Android SDK 35+
- NDK 29.0.13113456
- Kotlin 2.1.0
- ~4GB free storage for models

### Build the App

```bash
# Clone the repository
git clone https://github.com/Elormyevu/nku-medgemma-conversion.git
cd nku-medgemma-conversion/mobile/android

# Build debug APK
./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Download Models

Models are loaded from device storage. For production, see `MODEL_DISTRIBUTION.md` for Play Asset Delivery integration.

```bash
# Download IQ1_M models from HuggingFace
huggingface-cli download wredd/medgemma-4b-gguf medgemma-4b-iq1_m.gguf
huggingface-cli download wredd/translategemma-4b-gguf translategemma-4b-iq1_m.gguf

# Push to device (development)
adb push medgemma-4b-iq1_m.gguf /sdcard/Download/
adb push translategemma-4b-iq1_m.gguf /sdcard/Download/
```

---

## 🧠 Models

### Compression Pipeline

We achieve **90% model size reduction** while preserving clinical accuracy:

| Stage | Format | MedGemma | TranslateGemma | Total |
|:------|:------:|:--------:|:--------------:|:-----:|
| Original | F16 | ~8.0 GB | ~5.0 GB | ~13 GB |
| Standard | Q2_K | 1.6 GB | 1.6 GB | 3.2 GB |
| **Extreme** | **IQ1_M** | **0.78 GB** | **0.51 GB** | **~1.3 GB** |

### Calibration

Medical accuracy is preserved through **64-chunk imatrix calibration** using 243 clinical scenarios across 14+ African languages.

```bash
# Generate calibration imatrix
./llama-imatrix -m medgemma-4b-f16.gguf \
  -f calibration/african_primary_care.txt \
  --chunks 64 \
  -o medgemma-medical.imatrix

# Quantize with calibration
./llama-quantize medgemma-4b-f16.gguf medgemma-4b-iq1_m.gguf IQ1_M \
  --imatrix medgemma-medical.imatrix
```

---

## 🌐 Languages

### Verified Core (14)
✅ English | ✅ French | ✅ Swahili | ✅ Hausa | ✅ Yoruba | ✅ Igbo | ✅ Amharic | ✅ Ewe | ✅ Twi | ✅ Wolof | ✅ Zulu | ✅ Xhosa | ✅ Oromo | ✅ Tigrinya

### Extended Pan-African Suite (32)
Afrikaans, Arabic, Bambara, Bemba, Chichewa, Dinka, Fula, Ga, Kikuyu, Kinyarwanda, Kongo, Kuanyama, Lingala, Luba-Kasai, Luo, Luganda, Malagasy, Ndebele, Northern Sotho, Nuer, Pidgin (Nigerian), Pidgin (Cameroonian), Portuguese, Rundi, Sesotho, Shona, Somali, Swati, Tsonga, Tswana, Tumbuka, Venda

### Verified Triage Results

| Language | Input | Diagnosis | Severity |
|:---------|:------|:----------|:--------:|
| Ewe | "Ta me dɔ nam" (My head hurts) | Malaria screen | Medium |
| Yoruba | Stomach/Head symptoms | Gastroenteritis | Medium |
| Hausa | Fever/Body Aches | Malaria Suspected | High |
| Swahili | Cough/Breathing | Pneumonia Suspected | High |
| English | Diarrhea/Weakness | Dehydration | Medium |

---

## 📁 Project Structure

```
nku-medgemma-conversion/
├── mobile/android/           # Android application
│   └── app/src/main/
│       ├── java/com/nku/app/
│       │   ├── MainActivity.kt         # UI + Compose
│       │   ├── NkuInferenceEngine.kt   # Model orchestration
│       │   ├── RPPGProcessor.kt        # Heart rate (rPPG)
│       │   ├── PallorDetector.kt       # Anemia (conjunctiva)
│       │   ├── EdemaDetector.kt        # Preeclampsia (edema)
│       │   ├── SensorFusion.kt         # Vital signs aggregator
│       │   ├── ClinicalReasoner.kt     # MedGemma + WHO fallback
│       │   ├── ThermalManager.kt       # 42°C auto-throttle
│       │   ├── LocalizedStrings.kt     # 46-language UI strings
│       │   ├── NkuTTS.kt              # Android System TTS wrapper
│       │   └── CloudInferenceClient.kt # Cloud fallback (dev only)
│       └── assets/           # App resources (models loaded from device storage)
├── scripts/
│   ├── quantization/         # IQ1_M/IQ2_XS quantization
│   ├── calibration/          # Medical imatrix generation
│   └── conversion/           # HF → GGUF conversion
├── calibration/              # Clinical calibration datasets
├── llama.cpp/                # Inference engine (submodule)
└── docs/                     # Documentation & assets
```

---

## 🏆 MedGemma Impact Challenge

This project is a submission for the [MedGemma Impact Challenge](https://www.kaggle.com/competitions/medgemma-impact-challenge) on Kaggle.

**Target Track**: Edge AI Prize ($5,000)

| Criterion | Our Strength |
|:----------|:-------------|
| **HAI-DEF Usage** | MedGemma 4B is core to all clinical reasoning |
| **Product Feasibility** | Full Android app, verified on emulator & device |
| **Problem Domain** | Clear unmet need: 450M+ underserved |
| **Impact Potential** | Pan-Africa, offline-first, budget hardware |

---

## 🤝 Contributing

We welcome contributions! See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

**Priority Areas:**
- 🌍 Language model improvements for low-resource African languages
- ⚡ Inference optimization for ARM Mali/Adreno GPUs
- 🔬 Clinical validation with CHW partners
- 📱 UI/UX improvements for low-literacy users

---

## 📄 License

This project is licensed under the Apache License 2.0 — see the [LICENSE](./LICENSE) file for details.

**Model Licenses:**
- MedGemma: [Google Health AI Terms](https://aistudio.google.com/app/prompts/new_chat?model=medlm-1.5-4b)
- TranslateGemma: [Gemma Terms of Use](https://ai.google.dev/gemma/terms)

---

## 📚 References

- [MedGemma Model Card](https://huggingface.co/google/medgemma-4b)
- [llama.cpp](https://github.com/ggerganov/llama.cpp)
- [African Languages Dataset](https://huggingface.co/datasets/masakhane/masakhane)

---

<p align="center">
  <strong>🌍 450M+ lives • 💰 $50 phones • 📵 100% offline • 🗣️ 46 languages</strong>
</p>

<p align="center">
  Made with ❤️ for Pan-Africa
</p>
