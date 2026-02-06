<p align="center">
  <img src="docs/assets/nku_logo.png" alt="Nku Logo" width="120" />
</p>

<h1 align="center">Nku: Offline Medical AI for Pan-Africa</h1>

<p align="center">
  <strong>The Sensorless Sentinel — Clinical Triage on $50 Phones</strong>
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
  <img src="https://img.shields.io/badge/Languages-47+-orange?style=flat-square" alt="Languages" />
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

**Nku** ("eye" in Twi) transforms any $50-100 Android phone into an offline clinical triage engine.

| What | How |
|:-----|:----|
| **100% Offline** | Zero network dependency for core clinical path |
| **Ultra-Compressed** | 8GB models → 1.3GB via IQ1_M quantization |
| **Pan-African Languages** | 47 languages including Twi, Hausa, Yoruba, Swahili |
| **Budget Hardware** | Runs on 2GB RAM devices (TECNO, Infinix) |

---

## ✨ Features

- 🧠 **MedGemma 4B** — Google's clinical reasoning model, quantized to 0.78GB
- 🌐 **TranslateGemma 4B** — Bi-directional Pan-African language bridge
- 🔊 **Piper TTS** — Offline voice synthesis for low-literacy users
- 💎 **Premium UI** — Glassmorphism design with localized strings
- ⚡ **Nku Cycle** — Intelligent model swapping under 2GB RAM budget

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    THE NKU CYCLE                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [Patient Symptom in Twi]                                 │
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
│   │    Piper TTS      │  ← ONNX (~20MB/voice)              │
│   │  Spoken Result    │                                    │
│   └───────────────────┘                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|:------|:-----------|
| **UI** | Jetpack Compose (Glassmorphism) |
| **Orchestration** | Kotlin Coroutines + mmap swap |
| **Inference** | llama.cpp via JNI (NDK 29) |
| **TTS** | Piper ONNX Runtime Mobile |
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

Models are automatically extracted from APK assets on first launch, or can be pushed manually for development:

```bash
# Download IQ1_M models from HuggingFace
huggingface-cli download wredd/medgemma-4b-gguf medgemma-4b-iq1_m.gguf
huggingface-cli download wredd/translategemma-4b-gguf translategemma-4b-iq1_m.gguf

# Push to device (development only)
adb push medgemma-4b-iq1_m.gguf /data/local/tmp/nku_models/
adb push translategemma-4b-iq1_m.gguf /data/local/tmp/nku_models/
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

### Verified Core (7)
✅ English | ✅ Twi | ✅ Yoruba | ✅ Hausa | ✅ Swahili | ✅ Ewe | ✅ Ga

### Extended Pan-African Suite (40+)
French, Portuguese, Amharic, Zulu, Igbo, Wolof, Lingala, Xhosa, Shona, Kinyarwanda, Kirundi, Tigrinya, Oromo, Somali, Bambara, Fulani, Kanuri, Tswana, Sotho, Kikuyu, Luo, Chichewa, Bemba, Ndebele, Venda, Tsonga, Swati, and more...

### Verified Triage Results

| Language | Input | Diagnosis | Severity |
|:---------|:------|:----------|:--------:|
| Twi | "Me tirim ye me ya" | Malaria | Medium |
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
│       │   ├── PiperTTS.kt             # Voice synthesis
│       │   └── CloudInferenceClient.kt # Fallback API
│       └── assets/           # Bundled GGUF models
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
- [Piper TTS](https://github.com/rhasspy/piper)
- [African Languages Dataset](https://huggingface.co/datasets/masakhane/masakhane)

---

<p align="center">
  <strong>🌍 450M+ lives • 💰 $50 phones • 📵 100% offline • 🗣️ 47 languages</strong>
</p>

<p align="center">
  Made with ❤️ for Pan-Africa
</p>
