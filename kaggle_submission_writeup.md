# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

---

## 1. Problem

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people [1] — far below the WHO's recommended 44.5 [2]. Over **450 million people** lack accessible primary care [5]. Community Health Workers (CHWs), the frontline of care, operate without diagnostic tools — yet **nearly all carry smartphones** [3].

The disconnect: powerful AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections (GSMA 2023), coverage is unreliable — 25% of rural Africans lack mobile broadband entirely, and only 23% use the internet regularly (ITU 2024) [4]. Cloud-based AI is **impractical** where it is needed most.

**Target user**: A CHW in rural Ghana with a $60 TECNO phone (2GB RAM), no stable internet, speaking Ewe. She needs immediate triage guidance — in her language, offline, on her existing device.

---

## 2. Solution: The "Nku Cycle"

**Nku** (Ewe: "eye") is a **proof-of-concept prototype** demonstrating that MedGemma can run entirely on $50–100 Android smartphones — 100% on-device, zero cloud dependency. It is not a finished clinical product; it requires field deployment with CHWs to validate thresholds and assess real-world accuracy.

**Architecture** — models are sequentially loaded/unloaded via `mmap`, keeping peak RAM at ~1.4GB within the 2GB budget:

| Stage | Model | Size | Function |
|:------|:------|:----:|:---------|
| 1. Translate | TranslateGemma 4B (IQ1_M) | 0.76GB | Local language → English |
| 2. Reason | MedGemma 4B (IQ1_M) | 1.1GB | Clinical reasoning on symptoms + sensor data |
| 3. Translate | TranslateGemma 4B (reloaded) | 0.76GB | English → local language |
| 4. Speak | Android System TTS | 0 MB | Spoken result in 46 languages |

**Ultra-compression**: 90% size reduction via IQ1_M quantization. A custom imatrix calibrated on 243 African primary care scenarios preserves diagnostic vocabulary for malaria, cholera, typhoid, and maternal health. Models served via llama.cpp JNI (NDK 29, ARM64 NEON) [7,8].

| | Nku | Cloud Alternatives |
|:--------|:---:|:------------------:|
| Offline | 100% | 0% |
| 2GB RAM devices | ✅ | ❌ |
| Pan-African languages | 46 | ~5 |
| Per-query cost | $0 | $0.01–0.10 |

---

## 3. Nku Sentinel: Camera-Based Screening

CHWs lack even basic equipment. Nku Sentinel extracts vital signs using **only the phone's camera** — zero additional hardware, zero additional ML weights.

### Screening Modalities (Literature-Backed)

| Screening | Method | Published Evidence | Output |
|:----------|:-------|:-------------------|:-------|
| **Heart rate** | Green channel rPPG, 10s DFT | Green channel yields strongest plethysmographic signal [13]; smartphone rPPG MAE 2.49 BPM [14] | ±5 BPM |
| **Anemia** | Conjunctival HSV analysis | 75.4% accuracy, 92.7% for severe anemia (Hb <7) [15]; HSV pallor correlates with hemoglobin [16] | Pallor score 0–1 |
| **Preeclampsia** | Facial geometry (EAR + gradients) | EAR from landmarks validated [17]; facial edema detection 85% accuracy [18] | Edema score 0–1 |

Sensor thresholds (rPPG: 40–200 BPM range; pallor: saturation 0.10–0.20; edema: EAR 2.2–2.8) are derived from published literature but are **engineering estimates pending field calibration** with ground-truth clinical data from the target population.

### Fitzpatrick-Aware Design

Medical AI has a skin-tone bias problem [6]. All Nku screening modalities are deliberately skin-tone independent: pallor uses **conjunctiva only** (consistent across Fitzpatrick types [10]); edema uses **geometry** (ratios, not color); rPPG uses **adaptive thresholds**.

### Why Continuous Scores + MedGemma, Not Binary Classification

The sensors are **signal producers, not diagnostic endpoints**. Unlike a standalone app outputting "anemia: yes/no," Nku feeds continuous scores to MedGemma, which cross-correlates them in context:

> HR 110 + Pallor 70% + Edema 45% + pregnant 28wk → "Moderate anemia with preeclampsia risk. Tachycardia may be compensatory. Hemoglobin test within 3 days; BP check today; return immediately if headache develops."

A heart rate of 110 BPM means one thing in a febrile child and something entirely different in a 28-week pregnant woman with facial edema. The sensors provide data; **MedGemma provides clinical judgment**.

**Fallback**: If MedGemma is unavailable (thermal throttling, memory pressure), rule-based WHO/IMCI decision trees provide triage [12].

---

## 4. Security & Safety

| Layer | Implementation |
|:------|:---------------|
| **Prompt injection** | 6-layer `PromptSanitizer` at every model boundary (zero-width stripping, homoglyph normalization, Base64 detection, regex patterns, character allowlist, delimiter wrapping) |
| **Abstention** | Sensor confidence must exceed 0.4 to display results; MedGemma reasons only on quality data |
| **Thermal** | Auto-pause at 42°C (`ThermalManager.kt`) |
| **Privacy** | 100% on-device; zero data transmission |
| **Disclaimer** | Always-on "Consult a healthcare professional" |

---

## 5. MedGemma: Irreplaceable Core (HAI-DEF)

MedGemma 4B is not optional — it is the **sole clinical reasoning engine**. It interprets sensor data + symptoms, generates structured triage output (severity, urgency, recommendations), and maintains medical accuracy via domain-specific imatrix calibration. No alternative works: cloud inference fails without reliable connectivity; smaller models lack medical knowledge. Only MedGemma, quantized to IQ1_M via llama.cpp, enables the offline + accurate + multilingual combination Nku requires.

---

## 6. Impact: Proof of Concept → Field Validation

Nku is a **working prototype** proving that offline, multilingual clinical triage on budget smartphones is technically feasible. What remains is the harder, more important work: field validation.

**What field deployment will reveal**: sensor accuracy across Fitzpatrick V-VI patients; CHW usability in variable lighting; which screening modality provides the most actionable triage; TranslateGemma's handling of dialectal variation; performance across TECNO/Infinix/Samsung A-series devices.

**Deployment pathway**: Pilot with 5–10 CHWs in rural Ghana (concurrent ground-truth vitals) → threshold calibration → community health organization partnerships (e.g., Ghana Health Service) → Play Asset Delivery / sideloaded APK distribution.

The promise of AI in healthcare has so far benefited those with the most access to care. Nku demonstrates that the **technical barriers to democratized AI triage are solvable**. The gap between prototype and impact is field deployment. Nku is ready for that next step.

---

## 7. Reproducibility

| Resource | Link |
|:---------|:-----|
| GitHub | [github.com/Elormyevu/nku-medgemma-conversion](https://github.com/Elormyevu/nku-medgemma-conversion) |
| HuggingFace | [huggingface.co/wredd/medgemma-4b-gguf](https://huggingface.co/wredd/medgemma-4b-gguf) |
| Calibration | `calibration/african_primary_care.txt` (243 scenarios) |
| Build | `git clone ... && cd mobile/android && ./gradlew assembleDebug` |

*See `kaggle_submission_appendices.md` for full language list (46), calibration scenario examples, MedGemma reasoning examples, and complete references [1–18].*

**Development tooling**: Google Antigravity (Gemini 3 Flash/Pro, Claude Opus 4.5/4.6, GPT 5.3 Codex).

---

**Prize Track**: Edge AI Prize ($5,000)
**Why Edge AI**: Nku's entire architecture — IQ1_M compression, 2GB RAM target, llama.cpp JNI, 100% on-device inference — is purpose-built for edge deployment. The hardest technical challenge — running a medical-grade LLM on a $50 phone — is solved. The next challenge is human.

---

*Nku: a proof of concept for 450M+ lives • $50 phones • 100% on-device inference • 46 languages*
