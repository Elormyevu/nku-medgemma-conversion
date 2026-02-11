# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

---

## 1. Problem

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people [1] — far below the WHO's recommended 44.5 [2]. Over **450 million people** lack accessible primary care [5]. Community Health Workers (CHWs), the frontline of care, operate without diagnostic tools — yet **nearly all carry smartphones** [3].

The disconnect: powerful AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections (GSMA 2023), coverage is unreliable — 25% of rural Africans lack mobile broadband entirely, and only 23% use the internet regularly (ITU 2024) [4]. Cloud-based AI is **impractical** where it is needed most.

**Target user**: A CHW in rural Ghana with a $60 TECNO phone (2–3GB RAM), no stable internet, speaking Ewe. She needs immediate triage guidance — in her language, offline, on her existing device.

---

## 2. Solution: The "Nku Cycle"

**Nku** (Ewe: "eye") is a **proof-of-concept prototype** demonstrating that MedGemma can run entirely on $50–100 Android smartphones — 100% on-device, zero cloud dependency. It is not a finished clinical product; it requires field deployment with CHWs to validate thresholds and assess real-world accuracy.

**Architecture** — MedGemma runs entirely on-device via `mmap` loading (the OS pages model data on demand, so peak resident memory adapts to available RAM). Translation uses Android ML Kit (on-device, ~30MB/language) for its 59 supported languages, with Google Cloud Translate API fallback for additional African languages (Twi, Hausa, Yoruba, Igbo) when connectivity is available:

| Stage | Component | Size | Function |
|:------|:----------|:----:|:---------|
| 1. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | Local language → English |
| 2. Reason | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning on symptoms + sensor data |
| 3. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | English → local language |
| 4. Speak | Android System TTS | 0 MB | Spoken result in 46 languages |

**🔑 Offline guarantee for CHWs**: All African official languages (English, French, Portuguese) are fully on-device via ML Kit. Since CHWs are trained and fluent in their country's official language, **every CHW always has a fully offline triage path** — no internet required at any stage. Cloud translation is only needed for indigenous/local languages like Twi, Hausa, and Yoruba, extending reach beyond the offline baseline.

**Key distinction**: All **medical inference is 100% on-device** — MedGemma never touches the cloud. Translation is on-device for ML Kit's 59 languages (including Afrikaans and Swahili); for additional African languages, translation uses Google Cloud Translate when online. MedGemma's Q4_K_M quantization achieves 56% on MedQA (81% of published baseline), calibrated with a 243-scenario African primary care imatrix [7,8].

| | Nku | Cloud Alternatives |
|:--------|:---:|:------------------:|
| Medical inference offline | **100%** | 0% |
| 2–3GB RAM devices | ✅ | ❌ |
| Pan-African languages | 46 | ~5 |
| Per-query cost | **$0** (inference) | $0.01–0.10 |

---

## 3. Nku Sentinel: Camera-Based Screening

CHWs lack even basic equipment. Nku Sentinel extracts vital signs using **only the phone's camera** — zero additional hardware, zero additional ML weights.

### Screening Modalities (Literature-Backed)

| Screening | Method | Published Evidence | Output |
|:----------|:-------|:-------------------|:-------|
| **Heart rate** | Green channel rPPG, 10s DFT | Green channel yields strongest plethysmographic signal [13]; smartphone rPPG MAE 2.49 BPM [14] | ±5 BPM |
| **Anemia** | Conjunctival HSV analysis | 75.4% accuracy, 92.7% for severe anemia (Hb <7) [15]; HSV pallor correlates with hemoglobin [16] | Pallor score 0–1 |
| **Preeclampsia** | Facial geometry (EAR + gradients) | EAR from landmarks validated [17]; facial edema detection 85% accuracy [18] | Edema score 0–1 |

Sensor thresholds (rPPG: 40–200 BPM range; pallor: saturation 0.10–0.20; edema: EAR 2.2–2.8) are grounded in published anthropometric and physiological data but are **conservative screening estimates**, intentionally tuned to over-refer rather than miss cases. This is by design: the sensors are **feature extractors, not diagnostic endpoints**. They pass continuous scores + confidence levels to MedGemma, which provides the clinical interpretation. Field calibration against clinician assessment will optimize sensitivity/specificity for specific deployment populations.

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

MedGemma 4B is not optional — it is the **sole clinical reasoning engine**, running 100% on-device. It interprets sensor data + symptoms, generates structured triage output (severity, urgency, recommendations), and maintains medical accuracy via domain-specific imatrix calibration (56% MedQA, 81% of published baseline). No alternative works: cloud inference fails without reliable connectivity; smaller models lack medical knowledge. Only MedGemma, quantized to Q4_K_M via llama.cpp, enables the offline + accurate + multilingual combination Nku requires. Translation is handled separately by Android ML Kit (on-device) with Google Cloud Translate fallback — keeping medical reasoning fully offline while extending language access.

---

## 6. Impact: Proof of Concept → Field Validation

Nku is a **working prototype** proving that offline, multilingual clinical triage on budget smartphones is technically feasible. What remains is the harder, more important work: field validation.

**What field deployment will reveal**: sensor accuracy across Fitzpatrick V-VI patients; CHW usability in variable lighting; which screening modality provides the most actionable triage; ML Kit translation accuracy for dialectal variation; cloud translation fallback reliability in low-connectivity areas; performance across TECNO/Infinix/Samsung A-series devices.

**Deployment pathway**: Pilot with 5–10 CHWs in rural Ghana (concurrent ground-truth vitals) → threshold calibration → community health organization partnerships (e.g., Ghana Health Service) → Play Asset Delivery / sideloaded APK distribution. Ghana is the natural starting point — the developer was born and raised there and maintains active ties to local health communities, making it the most feasible location for hands-on pilot coordination.

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

**Development tooling**: Google Antigravity (Gemini 3 Flash/Pro, Claude Opus 4.5/4.6); OpenAI Codex IDE (GPT 5.3 Codex).

---

**Prize Tracks**:
- **Main Track** (1st–4th, $10K–$30K): Nku addresses a real, urgent healthcare gap for 450M+ people by putting MedGemma-powered clinical reasoning directly in CHWs' hands — offline, multilingual, on their existing $50 devices.
- **Edge AI Prize** ($5K): Nku's entire architecture — Q4_K_M compression (56% MedQA), mmap loading on $50–100 phones, llama.cpp JNI, 100% on-device medical inference — is purpose-built for edge deployment. We deliberately chose Q4_K_M over the smaller IQ1_M after benchmarking showed IQ1_M's 29.8% MedQA accuracy was near random chance — unacceptable for medical reasoning (see Appendix D). The hardest technical challenge — running a medical-grade LLM on a budget phone — is solved.
- **Novel Task Prize** ($5K): Two novel contributions: (1) Q4_K_M quantization calibrated with a 243-scenario African primary care imatrix for an entirely new deployment context — offline multilingual triage on $50 smartphones. (2) A novel EAR-based periorbital edema screening heuristic, repurposing established computer vision geometry (Eye Aspect Ratio) for preeclampsia detection — grounded in palpebral fissure anthropometrics but not previously described in the literature.

---

*Nku: a proof of concept for 450M+ lives • $50 phones • 100% on-device medical inference • 46 languages*
