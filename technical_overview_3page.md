# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

## 1. Problem & Motivation

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people—far below the WHO's recommended 44.5 per 10,000. Over **450 million people** lack accessible primary care. Community Health Workers (CHWs), the frontline of healthcare delivery, frequently lack reliable access to diagnostic tools due to equipment deficiencies, supply stock-outs, and maintenance failures [25].

Yet nearly all CHWs carry smartphones. Powerful clinical AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections, network coverage is unreliable and intermittent — 25% of rural Africans lack mobile broadband entirely (ITU 2024). This makes cloud-based AI **impractical** precisely where it is needed most.

**Nku** (Ewe: "eye") is a **proof-of-concept prototype** demonstrating that MedGemma can run **entirely on-device** on $60–100 Android phones (3–4GB RAM). No cloud. No internet required. Nku's sensor thresholds and clinical workflows require field validation with real CHWs before deployment, but the core technical challenge — fitting a medical-grade LLM on a budget phone — is addressed.

## 2. Technical Implementation

### 2.1 The "Nku Cycle" — Edge Inference Orchestration

Our core innovation is a memory-efficient orchestration pattern that runs MedGemma on budget devices (3–4GB RAM) using `mmap` — the OS pages model data on demand, so peak resident memory adapts to available RAM. Translation uses Android ML Kit (on-device, ~30MB/language) for 59 supported languages, with Google Cloud Translate API fallback for additional African languages when online:

| Stage | Component | Size | Function |
|:------|:----------|:----:|:---------|
| 1 | Android ML Kit / Cloud Translate | ~30MB/lang | Local language → English |
| 2 | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning & triage |
| 3 | Android ML Kit / Cloud Translate | ~30MB/lang | English → Local language |
| 4 | Android System TTS | ~0MB | Spoken output |

**Key distinction**: All **medical inference is 100% on-device** — MedGemma never touches the cloud. Translation is on-device for ML Kit's 59 languages (including Afrikaans and Swahili); for additional African languages like Twi, Hausa, and Yoruba, translation falls back to Google Cloud Translate when connectivity is available.

**🔑 Offline guarantee for CHWs**: All African official languages (English, French, Portuguese) are fully on-device via ML Kit. Since CHWs are trained in their country's official language, **every CHW always has a fully offline triage path** — no internet required at any stage. Cloud translation only needed for indigenous languages, extending reach beyond the offline baseline. Total on-disk footprint: **~2.3GB** (MedGemma) + **~150MB** (ML Kit language packs).

**Quantization**: Our Q4_K_M quantized model achieves **56% on MedQA** (n=1,273, single-pass evaluation†), retaining 81% of the unquantized model's published 69% baseline. To validate this selection, we systematically benchmarked four quantization levels — IQ1_M (32.3%), Q2_K (34.7%), IQ2_XS with medical imatrix (43.8%), and Q4_K_M (56.0%) — confirming that Q4_K_M provides the best accuracy for reliable clinical deployment and that domain-specific imatrix calibration (applied to IQ2_XS) is more important than raw bit budget at lower quantization levels. **Only Q4_K_M is deployed in the Nku application.** We also created a 243-scenario **African primary care calibration dataset** across 14+ languages, used to generate an importance matrix for aggressive quantization experiments (IQ2_XS). This dataset covers malaria, anemia, pneumonia, and other regionally prevalent conditions.

> †Each quantized model was evaluated once through the full MedQA test set — no repeated runs or best-of-N selection — to mirror Nku's real-world single-attempt triage use case.

### 2.2 Nku Sentinel — Camera-Based Screening (0 MB Additional Weight)

CHWs frequently lack reliable diagnostic equipment [25]. Nku Sentinel extracts vital signs using **only the phone camera** via pure signal processing (no additional ML models), then feeds results to MedGemma for clinical interpretation:

| Screening | Method | Key Evidence |
|:----------|:-------|:-------------|
| **Cardio Check** | rPPG (green channel DFT, 30fps) | Verkruysse 2008: green channel strongest signal; smartphone rPPG MAE 1.32–3.95 BPM |
| **Anemia Screen** | **Conjunctival** HSV analysis | Jay 2024: 75.4% accuracy, 92.7% for severe anemia; thresholds pending field calibration |
| **Jaundice Screen** | **Scleral** HSV analysis | Scleral icterus visible at bilirubin ≥2.5 mg/dL; skin-tone agnostic (unpigmented scleral tissue) |
| **Preeclampsia** | Facial geometry (EAR) | NEC/Tsukuba: 85% edema detection accuracy; EAR thresholds pending field calibration |

**Fitzpatrick-Aware Design**: Pallor uses conjunctiva-only analysis (skin-tone agnostic). Jaundice uses scleral tissue (unpigmented, consistent across all skin tones). Edema uses geometry ratios (skin-color independent). These explicit design choices are intended to support consistent performance across Fitzpatrick V-VI skin tones — the primary target demographic — though field validation across diverse populations is needed.

**Clinical Reasoning Pipeline**: `SensorFusion.kt` aggregates all four sensor outputs → `ClinicalReasoner.kt` generates structured MedGemma prompts with vital signs + patient context → MedGemma returns severity, urgency, and actionable CHW recommendations. If MedGemma is unavailable (device overheating), a WHO/IMCI rule-based fallback provides continued triage support.

**Prompt Injection Protection**: All user input passes through a 6-layer `PromptSanitizer` (zero-width stripping, homoglyph normalization, base64 detection, regex pattern matching, character allowlist, delimiter wrapping) at every model boundary—input, output validation at each stage—preventing prompt injection across the multi-model pipeline.

**Multi-Layer Safety Architecture**: (1) Confidence gating — sensors below 75% excluded from prompts; (2) WHO/IMCI rule-based fallback when MedGemma is unavailable; (3) over-referral bias — thresholds tuned to flag liberally; (4) always-on disclaimer with every result; (5) thermal protection (auto-pause at 42°C). All screening data encrypted at rest via SQLCipher with Android Keystore-derived AES-256 passphrase.

### 2.3 Localization

46 Pan-African languages (14 clinically verified). All UI strings, diagnostic card tooltips, and clinical instructions are localized via `LocalizedStrings.kt`. Offline voice output via Android System TTS.

## 3. Effective Use of MedGemma

MedGemma 4B is **irreplaceable** in this system. It performs the clinical reasoning that transforms raw sensor data and symptoms into structured triage assessments — a capability no smaller model possesses. Cloud inference fails completely in low-connectivity zones. Only MedGemma, quantized to Q4_K_M (56% MedQA accuracy on the quantized model, vs. 69% unquantized) and deployed via llama.cpp JNI on ARM64, enables the **offline + accurate** combination Nku requires. Translation is handled separately via Android ML Kit (on-device) with Google Cloud Translate fallback — keeping medical inference fully offline while extending language access.

| HAI-DEF Requirement | Implementation |
|:---------------------|:---------------|
| Clinical reasoning | Interprets Nku Sentinel vital signs + symptoms for triage |
| Structured output | Severity, urgency, differential considerations, CHW recommendations |
| Medical accuracy | 56% MedQA (81% of baseline); quantization study validated via imatrix experiments |
| Edge deployment | Q4_K_M GGUF, mmap loading, ~2.3GB footprint |

## 4. Impact

Nku is a **working prototype** demonstrating the technical feasibility of offline, multilingual clinical triage on budget smartphones. Sensor processing thresholds are derived from published literature but require field calibration with ground-truth clinical data from the target population.

| Metric | Value |
|:-------|:------|
| Target population | **450M+** (rural Sub-Saharan Africa) |
| Device requirement | $60–100 Android, 3–4GB RAM [26] |
| Medical inference | **100% on-device** (zero cloud dependency) |
| Translation | On-device (ML Kit, 59 langs) + cloud fallback (Twi, Hausa, Yoruba) |
| Languages | 46 (14 clinically verified) |
| MedGemma footprint | **~2.3GB** (Q4_K_M) |
| Per-query cost | **$0** (medical inference) |
| Additional hardware | **None** (camera-only screening) |

**Deployment Pathway**: Pilot with 5-10 CHWs in rural Ghana (concurrent ground-truth vital sign collection) → threshold calibration and UX refinement → community health organization partnerships → Play Asset Delivery or APK+model distribution via GitHub.

The promise of AI in healthcare has so far benefited those with the most access to medical care. Nku demonstrates that the **technical barriers to democratized AI triage are solvable** — what remains is the harder, more important work of field validation and community partnership.

## 5. Open Source & Reproducibility

**Nku is fully open source.** All application source code, build scripts, calibration data, and quantization artifacts are released under the **Apache License 2.0** — free to download, fork, modify, and use with attribution. Quantized model weights are available on HuggingFace (subject to Google Gemma Terms of Use).

| Resource | License | Link |
|:---------|:--------|:-----|
| Source Code | Apache 2.0 | [github.com/Elormyevu/nku-medgemma-conversion](https://github.com/Elormyevu/nku-medgemma-conversion) |
| Model Weights | Gemma Terms | [huggingface.co/wredd/medgemma-4b-gguf](https://huggingface.co/wredd/medgemma-4b-gguf) |
| Calibration | Apache 2.0 | `calibration/african_primary_care.txt` (243 scenarios) |
| Build | — | `git clone ... && cd mobile/android && ./gradlew assembleDebug` |

---

*Nku: a proof of concept for 450M+ lives • $60–100 phones • 100% on-device medical inference • 46 languages*
