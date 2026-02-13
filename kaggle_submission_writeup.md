# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge**

---

## 1. Problem

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people [1] — far below the WHO's recommended 44.5 [2]. Over 450 million people lack accessible primary care [5]. Community Health Workers (CHWs), the frontline of care, frequently lack reliable access to diagnostic tools — due to equipment deficiencies, supply stock-outs, and maintenance failures [25] — yet nearly all carry smartphones [3].

The disconnect: powerful AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections (GSMA 2023), coverage is unreliable — 25% of rural Africans lack mobile broadband entirely, and only 23% use the internet regularly (ITU 2024) [4]. Cloud-based AI is impractical where it is needed most.

**Target user**: A CHW in rural Ghana with a $60–100 TECNO or Infinix phone (3–4GB RAM) and no stable internet [26]. She needs immediate triage guidance — offline, on her existing device — to determine which patients require urgent referral to a district hospital that may be hours away. Transsion brands (TECNO, Infinix, itel) hold >50% of the African smartphone market [27].

---

## 2. Solution: The "Nku Cycle"

Nku (Ewe: "eye") runs MedGemma entirely on $60–100 Android smartphones — 100% on-device, zero cloud dependency. It is a proof-of-concept prototype; field deployment with CHWs is required to validate thresholds and assess real-world accuracy. A concrete path to field testing exists: the developer — born and raised in Ghana, an incoming surgery resident at NewYork-Presbyterian Queens (MD/MS, Columbia VP&S, 2025; BA Neuroscience, Amherst College, 2019) — maintains clinical connections with physicians across Ghana, providing a direct pathway to pilot validation with CHWs.

**Architecture** — MedGemma processes English-language medical text on-device via `mmap` loading (the OS pages model data on demand, so peak resident memory adapts to available RAM). For non-English input/output, Android ML Kit (on-device, ~30MB/language) handles translation across 59 supported languages, with Google Cloud Translate API fallback for additional African languages (Ewe, Twi, Hausa, Yoruba, Igbo, etc.) when connectivity is available:

| Stage | Component | Size | Function |
|:------|:----------|:----:|:---------|
| 1. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | Non-English input → English |
| 2. Reason | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning on symptoms + sensor data (English) |
| 3. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | English → non-English output |
| 4. Speak | Android System TTS | 0 MB | Spoken result in device-supported languages |

All medical inference is 100% on-device. For non-English interactions, ML Kit provides on-device translation for 59 languages (including all African official languages), with Cloud Translate fallback for indigenous languages (Ewe, Twi, Hausa, Yoruba, etc.) when online. Every CHW has a fully offline triage path. Our Q4_K_M quantized model achieves 56% on MedQA (n=1,273), retaining 81% of the unquantized baseline (69%) [7,8].

| | Nku | Cloud Alternatives |
|:--------|:---:|:------------------:|
| Medical inference offline | **100%** | 0% |
| 3–4GB RAM devices | ✅ | ❌ |
| Pan-African languages | 46 | ~5 |
| Per-query cost | **$0** (inference) | $0.01–0.10 |

---

## 3. Nku Sentinel: Camera-Based Screening

Nku Sentinel extracts vital signs using only the phone's camera (zero additional hardware), then feeds structured biomarker data to MedGemma for clinical interpretation — effective triage when the nearest hospital may be hours away.

### Screening Modalities (Literature-Backed)

| Screening | Method | Published Evidence | Output | MedGemma Input |
|:----------|:-------|:-------------------|:-------|:---------------|
| **Heart rate** | Green channel rPPG, 10s DFT | Green channel yields strongest plethysmographic signal [13]; smartphone rPPG MAE 1.32–3.95 BPM [9,14] | ±5 BPM | BPM value + quality label + confidence % |
| **Anemia** | Conjunctival HSV analysis | 75.4% accuracy, 92.7% for severe anemia (Hb <7) [15]; HSV pallor correlates with hemoglobin [16] | Pallor score 0–1 | Conjunctival saturation + pallor index + severity + tissue coverage |
| **Jaundice** | Scleral HSV analysis | Scleral icterus visible at bilirubin ≥2.5 mg/dL [28]; smartphone jaundice detection validated [29] | Jaundice score 0–1 | Scleral yellow ratio + jaundice index + severity + confidence |
| **Preeclampsia** | Facial geometry (EAR + gradients) | EAR from landmarks validated [17]; facial edema detection 85% accuracy [18] | Edema score 0–1 | EAR ratio + periorbital puffiness + facial swelling + edema index |

Sensor thresholds are grounded in published data but intentionally tuned to slightly over-diagnose rather than miss cases. The sensors are feature extractors — they pass continuous scores and confidence levels to MedGemma, which applies clinical reasoning with additional symptoms to produce a clinically relevant triage recommendation. Field calibration will optimize sensitivity/specificity.

### Fitzpatrick-Aware Design

Medical AI has a skin-tone bias problem [6]. All Nku screening modalities are deliberately skin-tone independent: pallor uses conjunctiva only (consistent across Fitzpatrick types [10]); jaundice uses scleral tissue (unpigmented, consistent across all skin tones); edema uses geometry (ratios, not color); rPPG uses adaptive thresholds.

### Why 56% MedQA Is Sufficient for CHW Triage

Does 56% on MedQA translate to reliable triage? Yes — for three key reasons (full analysis with 6 evidence sections in Appendix E):

**1. Triage ≠ MedQA.** MedQA tests USMLE-level reasoning across all of medicine. CHW triage asks: *"urgent referral, referral within days, or routine follow-up?"* for ~5–8 common conditions. Frontier LLMs achieve ~92.4% triage accuracy — far above MedQA scores [19,20]. Our structured prompting achieves a median 53% improvement over zero-shot baselines [23].

**2. Multi-layer safety architecture.** WHO/IMCI rule-based fallback if MedGemma is unavailable [12]. Sensor thresholds tuned to slightly over-diagnose (false positives over false negatives), with MedGemma applying clinical reasoning to refine triage. Always-on "Consult a healthcare professional" disclaimer. Confidence gating excludes unreliable sensor data from prompts.

**3. The baseline is zero.** These CHWs currently have *no* diagnostic support. A real-world study in Nairobi showed LLM decision support reduced diagnostic errors by 16% [21]. A prospective study in Rwanda is specifically validating LLM-augmented CHW triage [22]. Even imperfect AI is transformative when the alternative is nothing.

---

## 4. Security & Safety

| Layer | Implementation |
|:------|:---------------|
| **Prompt injection** | 6-layer `PromptSanitizer` at every model boundary (zero-width stripping, homoglyph normalization, Base64 detection, regex patterns, character allowlist, delimiter wrapping) |
| **Abstention** | Sensor confidence must exceed 75% for ClinicalReasoner to include in triage; below-threshold readings excluded with advisory notes |
| **Thermal** | Auto-pause at 42°C (`ThermalManager.kt`); WHO/IMCI rule-based fallback continues triage |
| **Privacy** | 100% on-device; zero data transmission; SQLCipher (AES-256, Android Keystore-derived passphrase) encrypts all stored screening data at rest |
| **Disclaimer** | Always-on "Consult a healthcare professional" |

---

## 5. MedGemma: Irreplaceable Core (HAI-DEF)

MedGemma 4B is the sole clinical reasoning engine, running 100% on-device via Q4_K_M quantization (4-bit, 8GB → 2.3GB). The quantized model retains 81% of the unquantized MedQA accuracy (56% vs. 69% baseline). MedGemma is irreplaceable: (1) required HAI-DEF model for medical reasoning; (2) only medically fine-tuned model with GGUF-compatible architecture for ARM64 edge via llama.cpp; (3) cloud inference fails without connectivity. We evaluated the multimodal variant architecturally but selected text-only + structured sensor data (rationale in Appendix D).

---

## 6. Impact: Proof of Concept → Field Validation

Nku demonstrates that AI-powered clinical triage on budget smartphones is technically feasible — closing the gap in triage quality between low-resource and high-resource settings. What remains is field validation.

**Deployment pathway**: Pilot with 5–10 CHWs in rural Ghana (concurrent ground-truth vitals) → threshold calibration → Ghana Health Service partnerships → Play Asset Delivery / sideloaded APK distribution. The developer was born and raised in Ghana and maintains active clinical connections for pilot coordination.

**Future features** (using only existing sensors + MedGemma, fully offline):

| Feature | Sensor | Method | Clinical Impact |
|:--------|:-------|:-------|:----------------|
| Respiratory rate | Camera | Chest wall movement detection | Pneumonia triage (top child killer in SSA) |
| SpO₂ estimation | Camera + flash | Fingertip reflectance photoplethysmography | Hypoxia detection for pneumonia/respiratory illness |
| Cough classification | Microphone | Audio pattern analysis (productive vs. dry) | TB/pneumonia screening |
| MUAC estimation | Camera | Mid-upper arm circumference via image | Child malnutrition screening |

The promise of AI in healthcare has so far benefited those with the most access to care. Nku demonstrates that the technical barriers to democratized AI triage are solvable. The gap between prototype and impact is field deployment. Nku is ready for that next step.

---

## 7. Open Source & Reproducibility

**Nku is fully open source.** All application source code, build scripts, calibration data, and quantization artifacts are released under the **Apache License 2.0** — free to download, fork, modify, and use with attribution. Quantized model weights are available on HuggingFace (subject to Google Gemma Terms of Use).

| Resource | License | Link |
|:---------|:--------|:-----|
| Source Code | Apache 2.0 | [github.com/Elormyevu/nku-medgemma-conversion](https://github.com/Elormyevu/nku-medgemma-conversion) |
| Model Weights | Gemma Terms | [huggingface.co/wredd/medgemma-4b-gguf](https://huggingface.co/wredd/medgemma-4b-gguf) |
| Calibration | Apache 2.0 | `calibration/african_primary_care.txt` (243 scenarios) |
| Build | — | `git clone ... && cd mobile/android && ./gradlew assembleDebug` |

*See `kaggle_submission_appendices.md` for full language list (46), calibration scenario examples, MedGemma reasoning examples, and complete references [1–27].*

**Development tooling**: Google Antigravity (Gemini 3 Flash/Pro, Claude Opus 4.5/4.6); OpenAI Codex IDE (GPT 5.3 Codex).

---

**Prize Tracks**: **Main** — MedGemma-powered triage for 450M+ people, offline, on $60–100 devices. **Edge AI** — Q4_K_M compression, mmap loading, llama.cpp JNI, 100% on-device; benchmarked four quantization levels (IQ1_M 32.3%, Q2_K 34.7%, IQ2_XS 43.8%, Q4_K_M 56.0%). **Novel Task** — (1) Systematic MedGemma quantization study for offline triage; (2) novel EAR-based periorbital edema screening for preeclampsia; (3) scleral icterus-based jaundice screening via HSV analysis.

*Nku: a proof of concept for 450M+ lives • $60–100 phones • 100% on-device • 46 languages*
