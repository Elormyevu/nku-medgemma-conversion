# NotebookLM Prompt & Script Source

**INSTRUCTION FOR NOTEBOOKLM:**
*Please read this exact text as a single, fast-paced 2-minute documentary voiceover. Do not add conversational filler, do not make it a two-person podcast. Read it straight through.*

---

**Nku Sentinel: Offline AI Triage**

Sub-Saharan Africa faces a massive primary care shortage. Community Health Workers lack diagnostic tools and work without internet access. 

Nku Sentinel solves this. It runs Google’s MedGemma 4B AI natively on sixty-dollar Android phones—completely offline. We compressed MedGemma to a 2.3-gigabyte file running on `llama.cpp` JNI.

The process is simple: 
First, Nku uses the smartphone's camera to read heart rate directly through the user's **finger-on-the-lens**, not the face, ensuring accuracy across all skin tones. It also checks for anemia and edema, while screening cough audio via Google’s HeAR model. 

Second, Nku translates 46 African languages to English offline using Android ML Kit.

Third, Nku fuses these vital signs and symptoms into a compressed prompt. MedGemma instantly reasons over this data, delivering a highly accurate triage assessment and a list of differential diagnoses.

Safety is paramount. Nku enforces a rigid 6-layer architecture, including strict thermal-pausing at 42°C to prevent budget phone hardware damage, prompt injection defense, and a WHO rule-based fallback if device memory runs out.

Nku is open-source. Deployed offline via peer-to-peer sharing, it brings MedGemma out of the cloud and straight to the frontlines.
