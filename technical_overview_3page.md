# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

## 1. Problem & Motivation

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people—far below the WHO's recommended 44.5 per 10,000. Over **450 million people** lack accessible primary care. Community Health Workers (CHWs), the frontline of healthcare delivery, operate in "equipment deserts" with no diagnostic tools.

Yet nearly all CHWs carry smartphones. Powerful clinical AI models like MedGemma exist, but require cloud connectivity. In rural Ghana, Nigeria, and Kenya, <2G connectivity is the norm—making cloud-based AI **medically useless** precisely where it is needed most.

**Nku** (Ewe: "eye") solves this by running MedGemma **entirely on-device** on $50 Android phones with 2GB RAM. No cloud. No internet. No compromise.

## 2. Technical Implementation

### 2.1 The "Nku Cycle" — Edge Inference Orchestration

Our core innovation is a memory-efficient orchestration pattern that runs MedGemma within a 2GB RAM budget via sequential `mmap`-based model swapping:

| Stage | Model | Size | Function |
|:------|:------|:----:|:---------|
| 1 | TranslateGemma 4B (IQ1_M) | 0.51GB | Local language → English |
| 2 | **MedGemma 4B (IQ1_M)** | 0.78GB | Clinical reasoning & triage |
| 3 | TranslateGemma 4B (IQ1_M) | 0.51GB | English → Local language |
| 4 | Android System TTS | ~0MB | Spoken output |

**Peak RAM: ~1.4GB** (well within the 2GB device budget). Total on-disk footprint: **~1.3GB**.

**Ultra-Compression**: We achieve 90% size reduction from MedGemma's original 8GB weights using IQ1_M quantization via llama.cpp, calibrated with a 64-chunk **medical imatrix** derived from 243 African primary care scenarios across 14+ languages—ensuring the quantized model retains diagnostic vocabulary for malaria, anemia, pneumonia, and other regionally prevalent conditions.

### 2.2 Nku Sentinel — Camera-Based Screening (0 MB Additional Weight)

CHWs lack diagnostic equipment. Nku Sentinel extracts vital signs using **only the phone camera** via pure signal processing (no additional ML models), then feeds results to MedGemma for clinical interpretation:

| Screening | Method | Key Evidence |
|:----------|:-------|:-------------|
| **Cardio Check** | rPPG (green channel FFT, 30fps) | Literature reports 96.2% accuracy vs ECG [9] |
| **Anemia Screen** | **Conjunctival** HSV analysis | Clinical pallor assessment achieves 80% sens / 82% spec [10] |
| **Preeclampsia** | Facial geometry ratios | Edema is a key warning sign (ACOG) |

**Fitzpatrick-Aware Design**: Pallor uses conjunctiva-only analysis (skin-tone agnostic). Edema uses geometry ratios (skin-color independent). These explicit design choices ensure diagnostic parity for Fitzpatrick V-VI—the primary target demographic.

**Clinical Reasoning Pipeline**: `SensorFusion.kt` aggregates all sensor outputs → `ClinicalReasoner.kt` generates structured MedGemma prompts with vital signs + patient context → MedGemma returns severity, urgency, and actionable CHW recommendations. If MedGemma is unavailable (device overheating), a WHO/IMCI rule-based fallback ensures continuous safety.

### 2.3 Localization

46 Pan-African languages (14 clinically verified). All UI strings, diagnostic card tooltips, and clinical instructions are localized via `LocalizedStrings.kt`. Offline voice output via Android System TTS.

## 3. Effective Use of MedGemma

MedGemma 4B is **irreplaceable** in this system. It performs the clinical reasoning that transforms raw sensor data and symptoms into structured triage assessments—a capability no smaller model possesses. Cloud inference fails completely in <2G zones. Only MedGemma, quantized to IQ1_M and deployed via llama.cpp JNI on ARM64, enables the **offline + accurate + multilingual** combination Nku requires.

| HAI-DEF Requirement | Implementation |
|:---------------------|:---------------|
| Clinical reasoning | Interprets Nku Sentinel vital signs + symptoms for triage |
| Structured output | Severity, urgency, differential considerations, CHW recommendations |
| Medical accuracy | Preserved via domain-specific imatrix calibration (243 scenarios) |
| Edge deployment | IQ1_M GGUF, mmap loading, 0.78GB footprint |

## 4. Impact

| Metric | Value |
|:-------|:------|
| Target population | **450M+** (rural Sub-Saharan Africa) |
| Device requirement | $50 Android, 2GB RAM |
| Network requirement | **None** (100% on-device inference) |
| Languages | 46 (14 clinically verified) |
| Per-query cost | **$0** |
| Additional hardware | **None** (camera-only screening) |

**Deployment Pathway**: Field testing with 5-10 CHWs → iterative threshold refinement → community health organization partnerships → Play Asset Delivery or APK+model distribution via GitHub.

## 5. Reproducibility

| Resource | Link |
|:---------|:-----|
| GitHub | [github.com/Elormyevu/nku-medgemma-conversion](https://github.com/Elormyevu/nku-medgemma-conversion) |
| HuggingFace | [huggingface.co/wredd/medgemma-4b-gguf](https://huggingface.co/wredd/medgemma-4b-gguf) |
| Calibration | `calibration/african_primary_care.txt` (243 scenarios) |
| Build | `git clone ... && cd mobile/android && ./gradlew assembleDebug` |

---

*Nku: 450M+ lives • $50 phones • 100% on-device inference • 46 languages*
