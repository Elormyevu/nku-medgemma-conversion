# Nku Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive README with architecture diagrams
- Contributing guidelines
- Apache 2.0 License
- Architecture documentation
- Generated project logo

### Changed
- **Replaced TranslateGemma with Android ML Kit** for on-device translation (59 languages, ~30MB/lang)
- Added Google Cloud Translate API fallback for indigenous African languages (Twi, Hausa, Yoruba)
- Upgraded MedGemma from IQ1_M to **Q4_K_M** quantization (56% MedQA accuracy, 81% of baseline)
- **Bundled HeAR ViT-L encoder via Play Asset Delivery** (`hear_encoder` asset pack, install-time)
  - `RespiratoryDetector.discoverViTLEncoder()` now checks PAD path first, local storage as fallback
  - `NkuInferenceEngine.MODEL_PACK_MAP` updated to include HeAR encoder
  - All inference models (MedGemma, HeAR Event Detector, HeAR ViT-L) now ship with app — validates 100% offline claim
- Updated .gitignore to exclude large model files and agent directories

---

## [1.0.0] - 2026-02-04

### Added - Phase IV: Deployment & Scale
- **IQ1_M Extreme Compression**: Reduced model footprint to ~1.3GB total
- **Model Distribution**: Models delivered via Play Asset Delivery or ADB sideloading
- **46-Language Support**: Full Pan-African linguistic coverage
- **Cloud Fallback**: Automatic Cloud Run fallback for emulator testing
- **Thin APK**: Base APK with native libs; models loaded from device storage

### Changed
- Transitioned from IQ2_XS to IQ1_M for better RAM efficiency
- Updated inference engine to support unified single-model approach
- Expanded LocalizedStrings from 6 to 46 languages

### Fixed
- Resolved `limitedParallelism` crash via kotlinx-coroutines 1.9.0 upgrade
- Fixed keyboard occlusion race condition with Enter event forcing
- Resolved Gradle OOM with 8GB heap and `noCompress 'gguf'`

---

## [0.9.0] - 2026-02-01

### Added - Phase III: Field Readiness
- **Emulator Verification**: `nku_pixel7` AVD with 8GB data partition
- **1:1 Production Lockdown**: GPRS/EDGE throttling, webcam bridge
- **Thin APK Strategy**: 810MB development build with sideloaded models

### Changed
- Migrated from AAR to SmolLM JNI module for emulator compatibility
- Implemented `Build.FINGERPRINT` detection for cloud fallback

### Fixed
- Resolved `UnsupportedArchitectureException` on ARM64 emulators
- Fixed storage overflow with 8GB data partition configuration

---

## [0.8.0] - 2026-01-28

### Added - Phase II: Localized Voice & UI
- **Android System TTS**: Device-native voice synthesis for spoken results
- **Glassmorphism UI**: Premium dark theme with glass cards
- **Localized UI**: Multi-language interface (Twi, Yoruba, Hausa, Swahili, English)
- **Language Picker**: Hierarchical 46-language selector

### Changed
- Upgraded to SDK 35 and Kotlin 2.1.0
- Implemented 22050Hz PCM audio playback

---

## [0.7.0] - 2026-01-25

### Added - Phase I: Ultra-Compression
- **IQ2_XS Quantization**: 89% size reduction from F16
- **64-chunk Medical Imatrix**: Double-resolution calibration
- **GitHub Actions Workflow**: Automated quantization pipeline
- **HuggingFace Upload**: Model hosting at wredd/medgemma-4b-gguf

---

## [0.5.0] - 2026-01-15

### Added - Initial Development
- **MedGemma 4B Integration**: Clinical reasoning via llama.cpp
- **TranslateGemma 4B**: Pan-African language translation
- **Nku Cycle Architecture**: Memory-efficient mmap-based model orchestration
- **Basic Android App**: Symptom input and triage display

---

## [0.1.0] - 2026-01-01

### Added
- Initial project setup
- HuggingFace model access configuration
- Basic project structure
