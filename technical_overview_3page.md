# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

## 1. Problem & Motivation

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people—far below the WHO's recommended 44.5 per 10,000. Over **450 million people** lack accessible primary care. Community Health Workers (CHWs), the frontline of healthcare delivery, operate in "equipment deserts" with no diagnostic tools.

Yet nearly all CHWs carry smartphones. Powerful clinical AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections, network coverage is unreliable and intermittent — 25% of rural Africans lack mobile broadband entirely (ITU 2024). This makes cloud-based AI **impractical** precisely where it is needed most.

**Nku** (Ewe: "eye") is a **proof-of-concept prototype** demonstrating that MedGemma can run **entirely on-device** on $50–100 Android phones (2–3GB RAM). No cloud. No internet. No compromise. Nku's sensor thresholds and clinical workflows require field validation with real CHWs before deployment, but the core technical challenge — fitting a medical-grade LLM on a budget phone — is solved.

## 2. Technical Implementation

### 2.1 The "Nku Cycle" — Edge Inference Orchestration

Our core innovation is a memory-efficient orchestration pattern that runs MedGemma on budget devices (2–3GB RAM) using `mmap` — the OS pages model data on demand, so peak resident memory adapts to available RAM. Translation uses Android ML Kit (on-device, ~30MB/language) for 59 supported languages, with Google Cloud Translate API fallback for additional African languages when online:

| Stage | Component | Size | Function |
|:------|:----------|:----:|:---------|
| 1 | Android ML Kit / Cloud Translate | ~30MB/lang | Local language → English |
| 2 | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning & triage |
| 3 | Android ML Kit / Cloud Translate | ~30MB/lang | English → Local language |
| 4 | Android System TTS | ~0MB | Spoken output |

**Key distinction**: All **medical inference is 100% on-device** — MedGemma never touches the cloud. Translation is on-device for ML Kit's 59 languages (including Afrikaans and Swahili); for additional African languages like Twi, Hausa, and Yoruba, translation falls back to Google Cloud Translate when connectivity is available.

**🔑 Offline guarantee for CHWs**: All African official languages (English, French, Portuguese) are fully on-device via ML Kit. Since CHWs are trained in their country's official language, **every CHW always has a fully offline triage path** — no internet required at any stage. Cloud translation only needed for indigenous languages, extending reach beyond the offline baseline. Total on-disk footprint: **~2.3GB** (MedGemma) + **~150MB** (ML Kit language packs).

**Quantization**: MedGemma Q4_K_M achieves **56% on MedQA** (81% of published 69% baseline), calibrated with a 64-chunk **medical imatrix** derived from 243 African primary care scenarios across 14+ languages — ensuring the quantized model retains diagnostic vocabulary for malaria, anemia, pneumonia, and other regionally prevalent conditions.

### 2.2 Nku Sentinel — Camera-Based Screening (0 MB Additional Weight)

CHWs lack diagnostic equipment. Nku Sentinel extracts vital signs using **only the phone camera** via pure signal processing (no additional ML models), then feeds results to MedGemma for clinical interpretation:

| Screening | Method | Key Evidence |
|:----------|:-------|:-------------|
| **Cardio Check** | rPPG (green channel DFT, 30fps) | Verkruysse 2008: green channel strongest signal; smartphone rPPG MAE 2.49 BPM |
| **Anemia Screen** | **Conjunctival** HSV analysis | Jay 2024: 75.4% accuracy, 92.7% for severe anemia; thresholds pending field calibration |
| **Preeclampsia** | Facial geometry (EAR) | NEC/Tsukuba: 85% edema detection accuracy; EAR thresholds pending field calibration |

**Fitzpatrick-Aware Design**: Pallor uses conjunctiva-only analysis (skin-tone agnostic). Edema uses geometry ratios (skin-color independent). These explicit design choices ensure diagnostic parity for Fitzpatrick V-VI—the primary target demographic.

**Clinical Reasoning Pipeline**: `SensorFusion.kt` aggregates all sensor outputs → `ClinicalReasoner.kt` generates structured MedGemma prompts with vital signs + patient context → MedGemma returns severity, urgency, and actionable CHW recommendations. If MedGemma is unavailable (device overheating), a WHO/IMCI rule-based fallback ensures continuous safety.

**Prompt Injection Protection**: All user input passes through a 6-layer `PromptSanitizer` (zero-width stripping, homoglyph normalization, base64 detection, regex pattern matching, character allowlist, delimiter wrapping) at every model boundary—input, output validation at each stage—preventing prompt injection across the multi-model pipeline.

### 2.3 Localization

46 Pan-African languages (14 clinically verified). All UI strings, diagnostic card tooltips, and clinical instructions are localized via `LocalizedStrings.kt`. Offline voice output via Android System TTS.

## 3. Effective Use of MedGemma

MedGemma 4B is **irreplaceable** in this system. It performs the clinical reasoning that transforms raw sensor data and symptoms into structured triage assessments — a capability no smaller model possesses. Cloud inference fails completely in low-connectivity zones. Only MedGemma, quantized to Q4_K_M (56% MedQA accuracy) and deployed via llama.cpp JNI on ARM64, enables the **offline + accurate** clinical reasoning Nku requires. Translation is handled separately via Android ML Kit (on-device) with Google Cloud Translate fallback — keeping medical inference fully offline while extending language access.

| HAI-DEF Requirement | Implementation |
|:---------------------|:---------------|
| Clinical reasoning | Interprets Nku Sentinel vital signs + symptoms for triage |
| Structured output | Severity, urgency, differential considerations, CHW recommendations |
| Medical accuracy | 56% MedQA (81% of baseline) via domain-specific imatrix calibration |
| Edge deployment | Q4_K_M GGUF, mmap loading, ~2.3GB footprint |

## 4. Impact

Nku is a **working prototype** that proves the technical feasibility of offline, multilingual clinical triage on budget smartphones. Sensor processing thresholds are derived from published literature but require field calibration with ground-truth clinical data from the target population.

| Metric | Value |
|:-------|:------|
| Target population | **450M+** (rural Sub-Saharan Africa) |
| Device requirement | $50–100 Android, 2–3GB RAM |
| Medical inference | **100% on-device** (zero cloud dependency) |
| Translation | On-device (ML Kit, 59 langs) + cloud fallback (Twi, Hausa, Yoruba) |
| Languages | 46 (14 clinically verified) |
| MedGemma footprint | **~2.3GB** (Q4_K_M) |
| Per-query cost | **$0** (medical inference) |
| Additional hardware | **None** (camera-only screening) |

**Deployment Pathway**: Pilot with 5-10 CHWs in rural Ghana (concurrent ground-truth vital sign collection) → threshold calibration and UX refinement → community health organization partnerships → Play Asset Delivery or APK+model distribution via GitHub.

The promise of AI in healthcare has so far benefited those with the most access to medical care. Nku demonstrates that the **technical barriers to democratized AI triage are solvable** — what remains is the harder, more important work of field validation and community partnership.

## 5. Reproducibility

| Resource | Link |
|:---------|:-----|
| GitHub | [github.com/Elormyevu/nku-medgemma-conversion](https://github.com/Elormyevu/nku-medgemma-conversion) |
| HuggingFace | [huggingface.co/wredd/medgemma-4b-gguf](https://huggingface.co/wredd/medgemma-4b-gguf) |
| Calibration | `calibration/african_primary_care.txt` (243 scenarios) |
| Build | `git clone ... && cd mobile/android && ./gradlew assembleDebug` |

---

*Nku: a proof of concept for 450M+ lives • $50 phones • 100% on-device medical inference • 46 languages*
