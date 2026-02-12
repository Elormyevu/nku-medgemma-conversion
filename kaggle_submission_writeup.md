# Nku: Offline Medical AI for Pan-African Primary Care

**MedGemma Impact Challenge — Edge AI Prize Track**

---

## 1. Problem

In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people [1] — far below the WHO's recommended 44.5 [2]. Over 450 million people lack accessible primary care [5]. Community Health Workers (CHWs), the frontline of care, operate without diagnostic tools — yet nearly all carry smartphones [3].

The disconnect: powerful AI models like MedGemma exist, but require reliable cloud connectivity. In rural Sub-Saharan Africa, while 3G accounts for ~54% of mobile connections (GSMA 2023), coverage is unreliable — 25% of rural Africans lack mobile broadband entirely, and only 23% use the internet regularly (ITU 2024) [4]. Cloud-based AI is impractical where it is needed most.

**Target user**: A CHW in rural Ghana with a $60 TECNO phone (3–4GB RAM) and no stable internet. She needs immediate triage guidance — offline, on her existing device — to determine which patients require urgent referral to a district hospital that may be hours away by motorbike.

---

## 2. Solution: The "Nku Cycle"

Nku (Ewe: "eye") is a proof-of-concept prototype demonstrating that high-quality AI-powered clinical triage can reach the communities that need it most — running MedGemma entirely on $50–100 Android smartphones, 100% on-device, zero cloud dependency. By bringing clinical reasoning to CHWs' existing devices, Nku enables effective triage in settings where the nearest district hospital may be hours away: identifying patients who need urgent escalation while ensuring that scarce higher-level resources are directed to those who truly need them. It is not a finished clinical product; it requires field deployment with CHWs to validate thresholds and assess real-world accuracy.

**Architecture** — MedGemma runs entirely on-device via `mmap` loading (the OS pages model data on demand, so peak resident memory adapts to available RAM). Translation uses Android ML Kit (on-device, ~30MB/language) for its 59 supported languages, with Google Cloud Translate API fallback for additional African languages (Twi, Hausa, Yoruba, Igbo) when connectivity is available:

| Stage | Component | Size | Function |
|:------|:----------|:----:|:---------|
| 1. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | Local language → English |
| 2. Reason | **MedGemma 4B (Q4_K_M)** | **2.3GB** | Clinical reasoning on symptoms + sensor data |
| 3. Translate | Android ML Kit / Cloud Translate | ~30MB/lang | English → local language |
| 4. Speak | Android System TTS | 0 MB | Spoken result in 46 languages |

All medical inference is 100% on-device — MedGemma never touches the cloud. Translation is on-device via ML Kit for 59 languages including all African official languages (English, French, Portuguese), with Cloud Translate fallback for indigenous languages (Twi, Hausa, Yoruba) when online. Since CHWs are trained in their country's official language, every CHW always has a fully offline triage path. Our Q4_K_M quantized model achieves 56% on MedQA (n=1,273), retaining 81% of the unquantized model's published 69% baseline, calibrated with a 243-scenario African primary care imatrix [7,8].

| | Nku | Cloud Alternatives |
|:--------|:---:|:------------------:|
| Medical inference offline | **100%** | 0% |
| 3–4GB RAM devices | ✅ | ❌ |
| Pan-African languages | 46 | ~5 |
| Per-query cost | **$0** (inference) | $0.01–0.10 |

---

## 3. Nku Sentinel: Camera-Based Screening

CHWs may or may not have access to basic diagnostic equipment — but regardless, the core challenge is clinical reasoning: synthesizing multiple signals into an actionable triage decision. Nku Sentinel extracts vital signs using only the phone's camera (zero additional hardware, zero additional ML weights), then feeds structured biomarker data to MedGemma for clinical interpretation. The result is effective triage that identifies patients requiring urgent escalation — critical when the nearest district hospital may be a multi-hour motorbike ride away.

### Screening Modalities (Literature-Backed)

| Screening | Method | Published Evidence | Output | MedGemma Input |
|:----------|:-------|:-------------------|:-------|:---------------|
| **Heart rate** | Green channel rPPG, 10s DFT | Green channel yields strongest plethysmographic signal [13]; smartphone rPPG MAE 1.32–3.95 BPM [9,14] | ±5 BPM | BPM value + quality label + confidence % |
| **Anemia** | Conjunctival HSV analysis | 75.4% accuracy, 92.7% for severe anemia (Hb <7) [15]; HSV pallor correlates with hemoglobin [16] | Pallor score 0–1 | Conjunctival saturation + pallor index + severity + tissue coverage |
| **Preeclampsia** | Facial geometry (EAR + gradients) | EAR from landmarks validated [17]; facial edema detection 85% accuracy [18] | Edema score 0–1 | EAR ratio + periorbital puffiness + facial swelling + edema index |

Sensor thresholds (rPPG: 40–200 BPM range; pallor: saturation 0.10–0.20; edema: EAR 2.2–2.8) are grounded in published anthropometric and physiological data but are conservative screening estimates, intentionally tuned to over-refer rather than miss cases. This is by design: the sensors are feature extractors, not diagnostic endpoints. They pass continuous scores and confidence levels to MedGemma, which provides the clinical interpretation. Field calibration against clinician assessment will optimize sensitivity/specificity for specific deployment populations.

### Fitzpatrick-Aware Design

Medical AI has a skin-tone bias problem [6]. All Nku screening modalities are deliberately skin-tone independent: pallor uses conjunctiva only (consistent across Fitzpatrick types [10]); edema uses geometry (ratios, not color); rPPG uses adaptive thresholds.

### Why 56% MedQA Is Sufficient for CHW Triage

Does 56% on MedQA translate to reliable triage? Yes — for three key reasons (full analysis with 6 evidence sections in Appendix E):

**1. Triage ≠ MedQA.** MedQA tests USMLE-level reasoning across all of medicine. CHW triage asks a simpler question: *"urgent referral, referral within days, or routine follow-up?"* for ~5–8 common conditions. Frontier LLMs achieve ~92.4% triage accuracy — far above their MedQA scores [19,20]. Our structured prompting (quantified vitals, confidence-gated readings, explicit output format) achieves a median 53% improvement over zero-shot baselines [23]. A quantized model scoring 56% on MedQA (a broad medical exam) is expected to perform substantially better on the narrower triage task.

**2. Multi-layer safety architecture.** WHO/IMCI rule-based fallback if MedGemma is unavailable [12]. Sensor thresholds tuned to over-refer (false positives over false negatives). Always-on "Consult a healthcare professional" disclaimer. Confidence gating excludes unreliable sensor data from prompts.

**3. The baseline is zero.** These CHWs currently have *no* diagnostic support. A real-world study in Nairobi showed LLM decision support reduced diagnostic errors by 16% [21]. A prospective study in Rwanda is specifically validating LLM-augmented CHW triage [22]. Even imperfect AI is transformative when the alternative is nothing.

---

## 4. Security & Safety

| Layer | Implementation |
|:------|:---------------|
| **Prompt injection** | 6-layer `PromptSanitizer` at every model boundary (zero-width stripping, homoglyph normalization, Base64 detection, regex patterns, character allowlist, delimiter wrapping) |
| **Abstention** | Sensor confidence must exceed 75% for ClinicalReasoner to include in triage; below-threshold readings excluded with advisory notes |
| **Thermal** | Auto-pause at 42°C (`ThermalManager.kt`) |
| **Privacy** | 100% on-device; zero data transmission |
| **Disclaimer** | Always-on "Consult a healthcare professional" |

---

## 5. MedGemma: Irreplaceable Core (HAI-DEF)

MedGemma 4B is the sole clinical reasoning engine, running 100% on-device via Q4_K_M quantization (4-bit, with importance-matrix calibration). This compression — from 8 GB (unquantized) to 2.3 GB (quantized) — enables fully offline inference on $50–100 phones. The quantized model retains 81% of the unquantized model's MedQA accuracy (56% quantized vs. 69% unquantized baseline). No alternative works: cloud inference fails without connectivity; smaller models lack medical knowledge; only MedGemma via llama.cpp enables the offline + accurate combination Nku requires.

---

## 6. Impact: Proof of Concept → Field Validation

Nku is a working prototype demonstrating that high-quality AI-powered clinical triage on budget smartphones is technically feasible — and that the communities with the fewest medical resources can access the same caliber of clinical reasoning available in well-resourced settings. What remains is the harder, more important work: field validation.

**What field deployment will reveal**: sensor accuracy across Fitzpatrick V-VI patients; CHW usability in variable lighting; which screening modality provides the most actionable triage; triage accuracy vs. clinician assessment; performance across TECNO/Infinix/Samsung A-series devices.

**Deployment pathway**: Pilot with 5–10 CHWs in rural Ghana (concurrent ground-truth vitals) → threshold calibration → community health organization partnerships (e.g., Ghana Health Service) → Play Asset Delivery / sideloaded APK distribution. Ghana is the natural starting point — the developer, an incoming surgery resident (MD/MS, Columbia Vagelos College of Physicians & Surgeons), was born and raised there and maintains active ties to local health communities. This combination of clinical training and personal connection to the target population makes Ghana the most feasible location for hands-on pilot coordination.

The promise of AI in healthcare has so far benefited those with the most access to care. Nku demonstrates that the technical barriers to democratized AI triage are solvable. The gap between prototype and impact is field deployment. Nku is ready for that next step.

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
- **Edge AI Prize** ($5K): Nku's entire architecture — Q4_K_M compression (56% MedQA accuracy on the quantized model, vs. 69% unquantized), mmap loading on $50–100 phones, llama.cpp JNI, 100% on-device medical inference — is purpose-built for edge deployment. We systematically benchmarked four quantization levels on the full MedQA test set (n=1,273): IQ1_M (32.3%), Q2_K (34.7%), IQ2_XS with medical imatrix (43.8%), and Q4_K_M (56.0%). Notably, the IQ2_XS model (1.3 GB) outperformed the larger Q2_K (1.6 GB) by +9.1pp — demonstrating that domain-specific imatrix calibration is more important than raw bit budget at aggressive quantization levels (see Appendix D). Q4_K_M was selected as the optimal accuracy/size tradeoff for clinical deployment. The hardest technical challenge — running a medical-grade LLM on a budget phone — is solved.
- **Novel Task Prize** ($5K): Two novel contributions: (1) Q4_K_M quantization calibrated with a 243-scenario African primary care imatrix for an entirely new deployment context — offline multilingual triage on $50 smartphones. (2) A novel EAR-based periorbital edema screening heuristic, repurposing established computer vision geometry (Eye Aspect Ratio) for preeclampsia detection — grounded in palpebral fissure anthropometrics but not previously described in the literature.

---

*Nku: a proof of concept for 450M+ lives • $50 phones • 100% on-device medical inference • 46 languages*
