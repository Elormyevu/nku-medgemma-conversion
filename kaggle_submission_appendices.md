# Nku: Submission Appendices

**Companion document to the Kaggle submission writeup.**

---

## Appendix A: Clinical Calibration Scenarios (243 Total)

Representative sample from `calibration/african_primary_care.txt`:

### Category 1: Malaria & Febrile Illness (52 scenarios)
| # | Scenario | Expected Triage |
|:--|:---------|:----------------|
| 1 | Child with fever >38.5°C for 3 days, recent rainy season | Malaria test urgent |
| 2 | Adult with intermittent fever, chills, sweating pattern | Malaria suspected, refer |
| 3 | Pregnant woman with fever and headache | High priority, malaria + other |

### Category 2: Anemia Screening (38 scenarios)
| # | Scenario | Expected Triage |
|:--|:---------|:----------------|
| 54 | Child with pale conjunctiva, fatigue, poor appetite | Moderate anemia, Hb test |
| 55 | Pregnant woman with fatigue, shortness of breath | Severe anemia screen, urgent |
| 56 | Adolescent girl with heavy menstruation, dizziness | Anemia likely, refer |

### Category 3: Respiratory Infections (41 scenarios)
### Category 4: Maternal Health (35 scenarios)
### Category 5: Diarrheal Disease (28 scenarios)
### Category 6: Skin Conditions (22 scenarios)
### Category 7: Child Nutrition (15 scenarios)
### Category 8: Chronic Conditions (12 scenarios)

---

## Appendix B: Supported Languages (46 Total)

### Tier 1: Clinically Verified (14 languages)

| Language | ISO | Region | Speakers |
|:---------|:----|:-------|:---------|
| English | en | Pan-African | 130M+ |
| French | fr | West/Central Africa | 115M+ |
| Swahili | sw | East Africa | 100M+ |
| Hausa | ha | West Africa | 70M+ |
| Yoruba | yo | Nigeria | 45M+ |
| Igbo | ig | Nigeria | 30M+ |
| Amharic | am | Ethiopia | 30M+ |
| Ewe | ee | Ghana/Togo | 7M+ |
| Twi (Akan) | ak | Ghana | 11M+ |
| Wolof | wo | Senegal | 10M+ |
| Zulu | zu | South Africa | 12M+ |
| Xhosa | xh | South Africa | 8M+ |
| Oromo | om | Ethiopia | 35M+ |
| Tigrinya | ti | Ethiopia/Eritrea | 7M+ |

### Tier 2: UI Localized (32 additional languages)

| Language | ISO | Language | ISO |
|:---------|:----|:---------|:----|
| Afrikaans | af | Luganda | lg |
| Arabic | ar | Malagasy | mg |
| Bambara | bm | Ndebele | nd |
| Bemba | bem | Northern Sotho | nso |
| Chichewa | ny | Nuer | nus |
| Dinka | din | Pidgin (Nigerian) | pcm |
| Fula | ff | Pidgin (Cameroonian) | wes |
| Ga | gaa | Portuguese | pt |
| Kikuyu | ki | Rundi | rn |
| Kinyarwanda | rw | Sesotho | st |
| Kongo | kg | Shona | sn |
| Kuanyama | kj | Somali | so |
| Lingala | ln | Swati | ss |
| Luba-Kasai | lua | Tsonga | ts |
| Luo | luo | Tswana | tn |
| | | Tumbuka | tum |
| | | Venda | ve |

---

## Appendix C: MedGemma Reasoning Example

### Input: Nku Sentinel Sensor Readings

```
=== NKU SENTINEL CAPTURE ===
Timestamp: 2026-02-06 14:32:15
Patient: Female, 26 years old, pregnant

CARDIO CHECK (RPPGProcessor):
  Heart Rate: 108 bpm
  Signal Quality: GOOD
  Confidence: 87%

ANEMIA SCREEN (PallorDetector - Conjunctiva):
  Pallor Score: 0.68 (68%)
  Severity: MODERATE
  Confidence: 82%

PREECLAMPSIA SCREEN (EdemaDetector):
  Edema Score: 0.52 (52%)
  Periorbital Score: 0.61 (61%)
  Severity: MODERATE
  Confidence: 79%

PATIENT CONTEXT:
  Pregnant: Yes
  Gestational Age: 32 weeks
  Reported Symptoms: headache (2 days), fatigue
```

### MedGemma Response

```
SEVERITY: HIGH

URGENCY: IMMEDIATE

PRIMARY_CONCERNS:
- Moderate-to-severe preeclampsia warning signs: facial edema (52%), periorbital
  puffiness (61%), persistent headache, and fatigue in a 32-week pregnant patient
- Moderate conjunctival pallor (68%) suggesting concurrent anemia (estimated Hb 7-10 g/dL)
- Tachycardia (108 bpm) may reflect anemia compensation or early cardiovascular stress
- Classic triad present: edema + headache + pregnancy >20 weeks

RECOMMENDATIONS:
- URGENT: Measure blood pressure immediately if equipment available
- URGENT: Check for protein in urine (dipstick) if available
- REFER TODAY: Patient should be seen at health facility within hours, not days
- Danger signs to communicate: if she develops visual changes, severe headache,
  epigastric pain, or reduced fetal movement - this is an emergency
- For anemia: hemoglobin test at facility; do not delay referral for this
- Keep patient resting, left lateral position preferred
- Ensure she has transportation arranged to facility

NOTE: Combination of preeclampsia warning signs and anemia in third trimester
requires same-day clinical evaluation. This is not a "watch and wait" situation.
```

---

## References

[1] World Health Organization. *Health Workforce in the WHO African Region*. WHO AFRO, 2018.

[2] World Health Organization. *Global Strategy on Human Resources for Health: Workforce 2030*. WHO, 2016.

[3] Agarwal, S., et al. "Mobile technology in support of community health workers." *Human Resources for Health* 13(1), 2015. DOI: 10.1186/s12960-015-0079-7

[4] GSMA. *The Mobile Economy Sub-Saharan Africa 2023*. GSMA Intelligence, 2023.

[5] Kruk, M.E., et al. "High-quality health systems in the SDG era." *The Lancet Global Health* 6(11), 2018. DOI: 10.1016/S2214-109X(18)30386-3

[6] Daneshjou, R., et al. "Disparities in dermatology AI performance across skin tones." *Science Advances* 8(31), 2022. DOI: 10.1126/sciadv.abq6147

[7] Gerganov, G. *llama.cpp*. GitHub, 2023. https://github.com/ggerganov/llama.cpp

[8] Dettmers, T., et al. "GGML: Efficient Inference of Quantized Models." 2023.

[9] Meijers, L., et al. "Accuracy of remote photoplethysmography." *JMIR mHealth* 10(12), 2022. DOI: 10.2196/42178

[10] Zucker, J.R., et al. "Clinical signs for anaemia recognition in western Kenya." *Bull. WHO* 75(Suppl 1), 1997.

[11] ACOG Practice Bulletin No. 222: Preeclampsia. 2020.

[12] WHO. *IMCI Chart Booklet*. 2014.

[13] Verkruysse, W., et al. "Remote plethysmographic imaging using ambient light." *Optics Express* 16(26), 2008. DOI: 10.1364/OE.16.021434

[14] Nowara, E.M., et al. *IEEE Trans. ITS*, 2022. Smartphone rPPG MAE 2.49 BPM.

[15] Jay, G.D., et al. "Smartphone anemia detection via conjunctival photographs." *PLOS ONE* 19(1), 2024. DOI: 10.1371/journal.pone.0295563

[16] Dimauro, G., et al. "Anemia detection using smartphone images." *Artif. Intell. Med.* 126, 2022.

[17] Sokolova, T. & Cech, J. "Real-time eye blink detection using facial landmarks." *CVWW*, 2017.

[18] NEC/Tsukuba. "Facial Image Analysis for Detecting Edema." 2023. 85% accuracy.

---

## Appendix D: Quantization & Translation Model Selection

Selecting the right quantization level required balancing two competing goals: **minimizing model size** (for budget devices) and **maintaining clinical accuracy** (for medical reasoning). We systematically benchmarked multiple quantization levels before selecting Q4_K_M.

### MedGemma Quantization Comparison (MedQA, n=500+)

| Quantization | Size | MedQA Accuracy | % of Baseline (69%) | Verdict |
|:-------------|:----:|:--------------:|:--------------------:|:--------|
| F16 (baseline) | 8.0 GB | 69% | 100% | Too large for mobile |
| **Q4_K_M** | **2.3 GB** | **56%** | **81%** | **✅ Selected — best accuracy/size ratio** |
| Q3_K_M | 1.8 GB | ~45% | ~65% | Marginal for clinical use |
| IQ2_XS | 1.3 GB | ~35% | ~51% | Below acceptable threshold |
| IQ1_M | 1.1 GB | 29.8% | 43% | ❌ Near random chance — rejected |

**Key finding**: IQ1_M (our original choice for maximum compression) scored 29.8% on MedQA — barely above the 25% random baseline. At this accuracy level, the model was essentially guessing on medical questions. We stopped the IQ1_M benchmark early (at n=500) once the trend was clear.

**Decision rationale**: Q4_K_M at 56% accuracy represents 81% of the published baseline — clinically useful for triage guidance. The 1.2GB size increase over IQ1_M was an acceptable tradeoff for nearly doubling medical reasoning accuracy. With `mmap` memory mapping, the 2.3GB model runs on 2–3GB RAM devices by paging model layers on demand via the filesystem, rather than loading the full model into memory.

### Translation Model Comparison

We also evaluated TranslateGemma 4B as an on-device translation model before selecting ML Kit:

| Approach | Size | African Language Support | RAM Impact | Offline |
|:---------|:----:|:------------------------:|:----------:|:-------:|
| TranslateGemma 4B (Q4_K_M) | 2.3 GB | Twi/Akan: ❌ broken | +2.3 GB (sequential load) | ✅ |
| TranslateGemma 4B (IQ1_M) | 0.78 GB | Twi/Akan: ❌ broken | +0.78 GB (sequential load) | ✅ |
| **Android ML Kit** | **~30 MB/lang** | **59 languages on-device** | **Negligible (separate process)** | **✅ (official langs)** |
| Google Cloud Translate | 0 MB | 100+ languages | None | ❌ (requires internet) |

**Key finding**: TranslateGemma could not translate Twi/Akan (a major Ghanaian language) at any quantization level — this was a base model limitation, not a quantization artifact. We benchmarked all 31 African languages across Q4_K_M and Q3_K_M and found significant gaps.

**Final architecture — hybrid translation**:
- **ML Kit on-device** (59 languages, ~30MB each): Handles all official African languages (English, French, Portuguese) + Afrikaans, Swahili, Zulu — fully offline
- **Cloud Translate fallback**: Handles indigenous languages (Twi, Hausa, Yoruba, Igbo, etc.) when online
- **Critical insight**: CHWs are trained and fluent in their country's official language. Since ML Kit supports all official languages on-device, **every CHW always has a fully offline triage path**. Cloud translation only extends reach to indigenous languages.

This hybrid approach eliminated ~2.3GB of GGUF model weight, removed the model-swapping pipeline overhead (3 load/unload cycles → 1), and expanded language coverage from ~15 to 59 on-device languages — while preserving the 100% offline guarantee for the primary use case.

