### Project name

**Nku** — Offline Medical AI for Pan-African Primary Care

*MedGemma Impact Challenge — Main Track + Edge AI Prize*

### Your team

**Elorm Yevudza, MD/MS** — Solo developer. Born and raised in Ghana. Incoming surgery resident, NewYork-Presbyterian Queens. MD/MS Columbia VP&S (2025); BA Neuroscience, Amherst College (2019). Maintains clinical connections with physicians across Ghana for pilot coordination and field validation.

### Problem statement

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people — far below the WHO's recommended 44.5 [1,2]. Over **450 million people** lack accessible primary care. CHWs frequently lack diagnostic tools due to equipment deficiencies and supply stock-outs [25] — yet nearly all carry smartphones [3]. Cloud-based AI requires connectivity that 25% of rural Africans lack entirely (ITU 2024) [4].

**Target user**: A CHW in rural Ghana with a $60–100 TECNO/Infinix phone (3–4GB RAM, no stable internet) [26] who needs immediate offline triage guidance. Transsion brands hold >50% African smartphone market share [27].

**Impact**: Nku demonstrates that AI-powered triage on budget smartphones is technically feasible. **Next step**: Pilot with 5–10 CHWs in rural Ghana → threshold calibration → Ghana Health Service partnerships → Play Asset Delivery distribution.

### Overall solution

**Nku** (Ewe: "eye") runs MedGemma **entirely on-device** on $60–100 Android smartphones — zero cloud dependency for clinical reasoning. It is a proof-of-concept prototype; field validation is the critical next step.

**MedGemma 4B is irreplaceable**: the sole clinical reasoning engine, interpreting raw sensor data and symptoms into structured triage. Cloud inference fails without connectivity. Only MedGemma, quantized to Q4_K_M (2.3GB) and deployed via llama.cpp JNI on ARM64, enables the **offline + accurate** combination required.

**The Nku Cycle** — an agentic orchestration pipeline deploying MedGemma as the clinical reasoning agent:

| Stage | Agent | Function |
|:------|:------|:---------|
| 1. Sense | Nku Sentinel (4 detectors) | Camera → structured vital signs (0 MB) |
| 2. Translate | ML Kit / Cloud Translate | Non-English → English (~30MB/lang) |
| 3. Reason | **MedGemma 4B (Q4_K_M)** | **Clinical reasoning (2.3GB)** |
| 4. Translate | ML Kit / Cloud Translate | English → non-English |
| 5. Speak | Android System TTS | Spoken result in local language |
| Fallback | WHO/IMCI rules | Deterministic triage if MedGemma unavailable |

Safety agents (confidence gating, thermal management) make autonomous decisions — rerouting to WHO/IMCI rule-based fallback if the device overheats. ML Kit provides translation for 59 languages on-device; Cloud Translate extends to indigenous African languages when online. **Every CHW always has a fully offline triage path.**

### Technical details

**Edge AI — Quantization**: 71% model size reduction (8GB → 2.3GB) via Q4_K_M, retaining 81% of MedQA accuracy (56% vs. 69% baseline). Runs on 3–4GB RAM via `mmap` paging. We benchmarked four quantization levels:

| Quant | Size | MedQA | Verdict |
|:------|:----:|:-----:|:--------|
| **Q4_K_M** | **2.3 GB** | **56.0%** | ✅ Deployed |
| IQ2_XS + imatrix | 1.3 GB | 43.8% | Viable ultra-compact |
| Q2_K | 1.6 GB | 34.7% | ❌ Worse than IQ2_XS |
| IQ1_M | 1.1 GB | 32.3% | ❌ Near random |

**Key finding**: IQ2_XS with medical imatrix outperforms the larger Q2_K by +9.1pp — domain-specific calibration > raw bit budget.

**Nku Sentinel** — camera-based screening (0 MB additional weights):

| Screening | Method | Fitzpatrick-aware |
|:----------|:-------|:-----------------:|
| Heart rate | Green channel rPPG, DFT [9,13] | Adaptive thresholds |
| Anemia | Conjunctival HSV [15,16] | Conjunctiva only |
| Jaundice | Scleral HSV [28,29] | Unpigmented tissue |
| Preeclampsia | Facial geometry EAR [17,18] | Color-independent |

All screening is deliberately **skin-tone independent** for Fitzpatrick V–VI populations. Sensor confidence >75% required for MedGemma prompt inclusion.

**Safety**: 6-layer PromptSanitizer at every model boundary. Thermal auto-pause at 42°C. SQLCipher AES-256 encryption at rest. Always-on "Consult a healthcare professional."

**46 Pan-African languages** (14 clinically verified). Fully open source: Apache 2.0 (compatible with CC BY 4.0) on [GitHub](https://github.com/Elormyevu/nku-medgemma-conversion); model weights on [HuggingFace](https://huggingface.co/wredd/medgemma-4b-gguf) (Gemma Terms).

---

*See `kaggle_submission_appendices.md` for references [1–29], calibration scenarios, MedGemma reasoning examples, sensor pipeline details, and safety architecture.*
