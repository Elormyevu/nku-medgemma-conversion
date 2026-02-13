### Project name

**Nku** — Offline Medical AI for Pan-African Primary Care

### Your team

**W. Elorm Yevudza Jnr, MD/MS** — Solo developer. Born and raised in Ghana. Incoming surgery resident, NewYork-Presbyterian Queens. MD/MS Columbia VP&S (2025); BA Neuroscience, Amherst College (2019). Maintains clinical connections with health professionals across Ghana for pilot coordination and field validation.

### Problem statement

In Sub-Saharan Africa, fewer than 2.3 health professionals serve every 10,000 people — far below the WHO's recommended 44.5 [1,2]. Over **450 million people** lack accessible primary care [3]. Community Health Workers (CHWs), the frontline of care, frequently lack reliable diagnostic tools due to equipment deficiencies and supply stock-outs [4] — yet nearly all carry smartphones [5].

Powerful clinical AI models exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, 25% of rural Africans lack mobile broadband entirely (ITU 2024) [6]. **Cloud-based AI is impractical where it is needed most.**

**Target user**: A CHW in rural Ghana with a $60–100 TECNO or Infinix phone (3–4GB RAM) and no stable internet [7]. She needs immediate triage guidance — offline, on her existing device — to determine which patients require urgent referral. Transsion brands (TECNO, Infinix, itel) hold >50% of the African smartphone market [8].

**Impact**: Nku demonstrates that AI-powered clinical triage on budget smartphones is technically feasible. **Deployment pathway**: Pilot with 5–10 CHWs in rural Ghana → threshold calibration → Ghana Health Service partnerships → Play Asset Delivery / sideloaded APK distribution.

### Overall solution

**Nku** (Ewe: "eye") runs MedGemma **entirely on $60–100 Android smartphones** — 100% on-device, zero cloud dependency. It is a proof-of-concept prototype; field validation with CHWs is the critical next step.

**MedGemma 4B is irreplaceable** in this system. It is the sole clinical reasoning engine, performing the interpretation that transforms raw sensor data and symptoms into structured triage assessments — a capability no smaller model possesses. Cloud inference fails completely in low-connectivity zones. Only MedGemma, quantized to Q4_K_M and deployed via llama.cpp JNI on ARM64, enables the **offline + accurate** combination Nku requires.

**The Nku Cycle** is an agentic orchestration pipeline where MedGemma serves as the clinical reasoning agent within a self-adapting multi-stage workflow:

| Stage | Agent | Size | Function |
|:------|:------|:----:|:---------|
| 1. Sense | Nku Sentinel (4 detectors) | 0 MB | Camera → structured vital signs |
| 2. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | Non-English input → English |
| 3. Reason | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning on symptoms + sensor data |
| 4. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | English → non-English output |
| 5. Speak | Android System TTS | 0 MB | Spoken result in local language |
| Fallback | WHO/IMCI rules | 0 MB | Deterministic triage if MedGemma unavailable |

Each stage operates autonomously. Safety agents (confidence gating, thermal management) make autonomous decisions — including rerouting to WHO/IMCI rule-based triage if the device overheats. All medical inference is 100% on-device. ML Kit provides on-device translation for 59 languages; Cloud Translate extends reach to indigenous languages (Ewe, Twi, Hausa, Yoruba, etc.) when online. Every CHW always has a fully offline triage path.

**Before/after — why structured prompting matters**: MedGemma was trained on clinical text, not smartphone sensor data. A naive prompt like *"the patient looks pale and her eyes are puffy"* yields generic advice. Nku's `ClinicalReasoner` instead feeds MedGemma quantified biomarkers with methodology and confidence:

> `Conjunctival saturation: 0.08 (healthy ≥0.20, pallor threshold ≤0.10), pallor index: 0.68, severity: MODERATE. EAR: 2.15 (normal ≈2.8, edema threshold ≤2.2), edema index: 0.52. Patient pregnant, 32 weeks.`

This structured prompting achieves a median 53% improvement over zero-shot baselines [9] — transforming MedGemma from a general medical QA model into a structured sensor data interpreter for CHW triage.

### Technical details

**Edge AI — Quantization & Memory**: We achieve **71% model size reduction** (8GB → 2.3GB) via Q4_K_M quantization while retaining 81% of MedQA accuracy (56% quantized vs. 69% unquantized baseline). The model runs on 3–4GB RAM devices via `mmap` — the OS pages model data on demand, so peak resident memory adapts to available RAM. We systematically benchmarked four quantization levels:

| Quant | Size | MedQA (n=1,273) | Primary Care (n=707) | Verdict |
|:------|:----:|:---------------:|:--------------------:|:--------|
| **Q4_K_M** | **2.3 GB** | **56.0%** | **56.2%** | ✅ Deployed |
| IQ2_XS + imatrix | 1.3 GB | 43.8% | 45.3% | Viable ultra-compact |
| Q2_K | 1.6 GB | 34.7% | 33.9% | ❌ Worse than IQ2_XS |
| IQ1_M | 1.1 GB | 32.3% | 32.4% | ❌ Near random |

**Key finding**: IQ2_XS with medical imatrix calibration outperforms the larger Q2_K by +9.1pp — domain-specific calibration matters more than raw bit budget. We created a 243-scenario African primary care calibration dataset across 14+ languages for imatrix generation.

**Nku Sentinel — Camera-Based Screening (0 MB additional weights)**: CHWs often lack equipment [4]. Nku extracts vital signs using only the phone camera via pure signal processing, then feeds structured biomarkers to MedGemma for clinical interpretation:

| Screening | Method | Output | Fitzpatrick-aware |
|:----------|:-------|:-------|:-----------------:|
| **Heart rate** | Green channel rPPG, 10s DFT [10,11,12] | ±5 BPM | Adaptive thresholds |
| **Anemia** | Conjunctival HSV analysis [13,14] | Pallor score 0–1 | Conjunctiva only |
| **Jaundice** | Scleral HSV analysis [15,16] | Jaundice score 0–1 | Scleral tissue (unpigmented) |
| **Preeclampsia** | Facial geometry EAR [17,18] | Edema score 0–1 | Geometry (color-independent) |

All screening modalities are deliberately **skin-tone independent** — critical for Fitzpatrick V-VI populations. Sensor confidence must exceed 75% for inclusion in MedGemma's prompt; below-threshold readings trigger a localized ⚠ warning prompting the CHW to re-capture in better conditions. When MedGemma is unavailable, the app displays a transparency banner identifying the triage as guideline-based (WHO/IMCI) with actionable recovery steps — all in the CHW's selected language.

**Safety**: 6-layer `PromptSanitizer` at every model boundary (zero-width stripping, homoglyph normalization, base64 detection, regex patterns, character allowlist, delimiter wrapping). Auto-pause at 42°C. SQLCipher AES-256 encryption at rest. Always-on "Consult a healthcare professional" disclaimer.

**46 Pan-African languages** (14 clinically verified) via ML Kit on-device + Cloud Translate fallback.

---

**Prize Track**: **Main** + **Edge AI** — Q4_K_M compression (8GB→2.3GB), mmap loading on $60–100 phones (3–4GB RAM), llama.cpp JNI (NDK 29, ARM64 NEON), systematic 4-level quantization benchmark (IQ2_XS with medical imatrix calibration), 100% on-device inference.

**Open source**: Nku is fully open source under the Apache License 2.0 (compatible with the competition's CC BY 4.0 requirement — Apache 2.0 is strictly more permissive). Source code, scripts, and calibration data on [GitHub](https://github.com/Elormyevu/nku-medgemma-conversion). Quantized model weights on [HuggingFace](https://huggingface.co/wredd/medgemma-4b-gguf) (subject to Google Gemma Terms of Use).

*See `kaggle_submission_appendices.md` for full references [1–29], language list (46), calibration scenarios, MedGemma reasoning examples, sensor pipeline details, and safety architecture.*

*Development tooling: Google Antigravity (Gemini 3 Flash/Pro, Claude Opus 4.5/4.6); OpenAI Codex IDE (GPT 5.3 Codex).*
