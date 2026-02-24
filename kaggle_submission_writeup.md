### Project name
Nku — Offline Medical AI for Pan-African Triage

### Your team
W. Elorm Yevudza Jnr, MD/MS — Solo developer. Born and raised in Ghana. Incoming surgery resident, NewYork-Presbyterian Queens. MD/MS Columbia VP&S (2025); BA Neuroscience (2019). Maintains clinical connections across Ghana for field validation.

### Problem statement
In Sub-Saharan Africa, fewer than 2.3 physicians serve every 10,000 people. Over 450 million people lack accessible primary care screening [1,2,3]. Community Health Workers (CHWs) are the frontline of care, but frequently lack reliable diagnostic tools [4] — yet nearly all carry Android smartphones [5].

Powerful clinical AI models exist but require reliable cloud connectivity. In rural Sub-Saharan Africa, 25% of the population lacks mobile broadband [6]. Cloud-based AI is impractical where it is needed most.

**Target user:** A rural CHW with a budget $60+ TECNO or Infinix phone (3GB+ RAM) and no stable internet. She needs immediate, offline triage guidance to determine which patients require urgent referral. Transsion brands hold >50% of the African smartphone market [8].

**Impact & Deployment:** Distributing a 2.3GB SLM to offline devices is achieved via a multi-tiered infrastructure strategy:
1. **Play Asset Delivery (PAD):** The lightweight base app is installed via the Play Store. It downloads the 2.3GB MedGemma model as an `install-time` asset once the CHW reaches 4G/LTE cellular connectivity in a larger town.
2. **Peer-to-Peer Viral Sharing:** African smartphone culture leverages offline peer-to-peer file transfer. Only one CHW per clinic needs a 4G download; they then beam the 2.3GB `.gguf` file to other CHWs' phones via Native Nearby Share or Xender at ~30MB/s without internet.
3. **Zero-Rated Data:** For Ministry of Health rollouts, the Play Store download URL is "zero-rated" through partnerships with Mobile Network Operators (MNOs), ensuring the download doesn't deduct from personal data balances.

*Note on direct APK installs:* The app auto-downloads the 2.3GB `Q4_K_M` model from HuggingFace on first launch, validates its SHA-256 checksum, and proceeds natively.

### Overall solution
**Every line of clinical reasoning executes strictly on the remote edge device.**

Nku (Ewe: "eye") runs MedGemma natively on $60+ Android smartphones. MedGemma 4B is irreplaceable in this prototype system. It is the sole clinical reasoning engine, transforming structured sensor data and qualitative symptoms into structured triage assessments. Cloud inference fails completely in low-connectivity zones. Only MedGemma, compressed to Q4_K_M (2.3GB) and deployed via `llama.cpp` JNI on ARM64, enables the necessary hybrid of offline inference and clinical accuracy.

The Nku Cycle is a self-adapting, multi-stage orchestration pipeline:

1. **Sense (0MB):** Nku Sentinel extracts structured vital signs via camera (rPPG, edema, jaundice) and microphone (TB/respiratory) (detailed in Appendix F).
2. **Translate (~30MB Base Model):** Android ML Kit executes offline translation from 59 languages to English. Unsupported indigenous languages fall back to the Google Cloud Translate API.
3. **Reason (2.3GB):** MedGemma 4B (Q4_K_M) strictly executes English clinical reasoning on the fused symptom/sensor prompt.
4. **Translate / Speak (~0MB additional):** ML Kit translates back to the local language; Android System TTS vocalizes it.
5. **Fallback (0MB logic-based):** World Health Organization (WHO/IMCI) deterministic guidelines provide triage if MedGemma is constrained by device memory.

*Crucially, because optical health sensors historically exhibit diagnostic bias against darker skin tones (Fitzpatrick Types V/VI), every Nku camera modality is engineered to be "Fitzpatrick-independent". Sensor confidence must exceed 75% for inclusion in MedGemma's prompt; otherwise, an alert prompts the CHW to re-capture data.*

**Adaptive Memory Management:** While 3GB RAM is common, Android OS background tasks consume significant memory dynamically. Before executing MedGemma, Nku queries `ActivityManager.MemoryInfo` for an 800MB resident free buffer. Nku relies on Android's native `mmap` implementation, seamlessly paging the 2.3GB model into the active virtual address space dynamically. By ensuring 800MB of breathing room, `mmap` pages the weights without "thrashing" the CPU. If free RAM is insufficient, or if the Android OOM (Out-of-Memory) killer interrupts the C++ inference thread, Nku gracefully catches the exception and falls back to deterministic WHO/IMCI rules.

**Why Compressed Structured Prompting Matters:** MedGemma was trained on clinical text, not raw matrices. A prompt like *"she looks pale and puffy"* although providing some detail that medgemma 4b can triage on in nku, doesn't provide enough context to extract the model's full potential. Instead, Nku fuses user text with structured sensor metrics (Appendix E), for example:

> `EAR: 2.15 (normal ≈2.8, edema limit ≤2.2), edema index: 0.52. Conjunctiva sat: 0.08 (pallor limit ≤0.10). Patient pregnant.`

MedGemma responds to this structured biomarker input with:
> `SEVERITY: HIGH | URGENCY: IMMEDIATE` — specifically identifying the risk of preeclampsia and concurrent anemia, recommending immediate facility referral (see Appendix C for full inference trace).

**Context Window Bottleneck:** Budget Android 3GB memory constraints restrict the KV-Cache to exactly 2048 tokens. Nku utilizes *Sensor Prompt Compression*, collapsing verbose sensory arrays natively on the Android layer before they hit the LLM prompt. This halves token consumption, unlocking over 1200 free tokens for MedGemma to utilize full Chain-of-Thought (CoT) reasoning, leading to a marked +20pp accuracy improvement in complex triage (detailed in Appendix I).

### Technical details
**Edge AI — Quantization:** We achieved a 71% model size reduction (8GB → 2.3GB) via Q4_K_M quantization while preserving 81% of its original MedQA baseline (56% quantized vs 69% unquantized) via our African clinical calibration dataset (Appendices A and D).

**HeAR Respiratory Screening:** Sub-Saharan Africa carries a massive burden of TB, COPD, and pneumonia. Nku incorporates Google’s HeAR Event Detector (1.1MB TFLite) to screen 2 seconds of cough/breathing audio in ~50ms. The Event Detector yields a structured class distribution output (`Cough Probability: 0.82`, `Risk Score: High`) directly into the prompt. MedGemma reliably reasons over these acoustic probabilities alongside other reported qualitative symptoms (detailed in Appendix F).

**Safety:** The system enforces a 6-layer `PromptSanitizer` at every model boundary (e.g., zero-width stripping, base64 detection, lengths caps), plus delimiter-wrapped injection defense. Inference auto-pauses at 42°C device thermal limits to prevent hardware throttling damage (detailed in Appendix G).

**46 Pan-African Languages:** 14 clinically verified (Appendix B). ML Kit executes locally. Unsupported indigenous languages securely fall back to the Google Cloud Translate API, ensuring seamless coverage while maintaining MedGemma's reasoning firmly on-device in English.

---

**Prize Track: Main Track and Edge AI Prize** 
- **HAI-DEF:** MedGemma 4B is the irreplaceable core of the clinical reasoning engine.
- **Product Feasibility:** Q4_K_M compression (2.3GB), `mmap` loading on $60+ Androids, `llama.cpp` JNI, full Android UI (see Appendix H for end-to-end deployment proof).
- **Novelty:** Integrates Google's HeAR Event Detector (1.1MB FP32/INT8) dynamically with an LLM for respiratory triage on mobile.
- **Open Source:** Fully open source under Apache 2.0. Source code, Python CI pipelines, and calibration tools available on [GitHub](https://github.com/Elormyevu/nku-medgemma-conversion) and [HuggingFace](https://huggingface.co/wredd).

*Refer to the Kaggle Submission Appendix for mathematical algorithms, validation schemas, calibration configurations, benchmarking datasets, and more.*

### References
[1] World Health Organization. *Health Workforce in the WHO African Region*. WHO AFRO, 2018.
[2] World Health Organization. *Global Strategy on Human Resources for Health: Workforce 2030*. WHO, 2016.
[3] Kruk, M.E., et al. "High-quality health systems in the SDG era." *The Lancet Global Health* 6(11), 2018.
[4] WHO Regional Office for Africa. *Regional Strategy on Diagnostic and Laboratory Services and Systems 2023*. WHO AFRO, 2023.
[5] Agarwal, S., et al. "Mobile technology in support of community health workers." *Human Resources for Health* 13(1), 2015.
[6] GSMA. *The Mobile Economy Sub-Saharan Africa 2023*. GSMA Intelligence, 2023.
[8] Canalys. *Africa Smartphone Market 2024*. Canalys Research, 2025.
