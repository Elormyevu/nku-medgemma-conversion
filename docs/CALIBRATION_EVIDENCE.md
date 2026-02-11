# Nku Sentinel — Calibration Evidence Table

**Purpose**: Maps every detector threshold in Nku's signal processing layer to published literature justification, identifies which parameters are well-supported vs. which require field calibration, and provides the evidence basis for reviewer questions about sensitivity/specificity/PPV/NPV.

> [!IMPORTANT]
> Nku is a proof-of-concept prototype. All thresholds below are derived from published literature and engineering estimates. **No threshold has been validated against ground-truth clinical data from the target population.** Field calibration with concurrent clinical measurements is the critical next step.

---

## 1. rPPG Processor (`RPPGProcessor.kt`)

### Threshold-to-Literature Mapping

| Parameter | Nku Value | Literature Support | Status |
|:----------|:----------|:-------------------|:------:|
| **Frequency analysis** | Simplified DFT | DFT is standard for rPPG frequency extraction [1,2] | ✅ Well-supported |
| **BPM range** | 40–200 BPM | Standard physiological range; rPPG literature typically uses 0.67–3.33 Hz (≡ 40–200 BPM) [2,3] | ✅ Well-supported |
| **Buffer window** | 10 seconds | 10s windows are commonly used in rPPG studies for balancing temporal resolution and frequency precision [1,4] | ✅ Well-supported |
| **Sampling rate** | 30 fps | Standard smartphone camera rate; sufficient for Nyquist criterion (max freq 3.33 Hz requires ≥6.67 fps) [5] | ✅ Well-supported |
| **Channel** | Green channel | Verkruysse et al. (2008) demonstrated green channel yields strongest plethysmographic signal due to hemoglobin absorption at 520–580 nm [6] | ✅ Well-supported |
| **Windowing** | Hamming window | Standard in FFT/DFT signal processing to reduce spectral leakage [2] | ✅ Well-supported |
| **Min analysis frames** | 5s (150 frames) | Engineering estimate; 5s provides minimum ~3 heart cycles at 40 BPM | ⚠️ Reasonable estimate |
| **Accuracy claim** | ±5 BPM | Smartphone rPPG achieves MAE 2.49 BPM in real-world conditions [7]; ±5 BPM is conservative | ✅ Supported by literature |

### Published Performance Metrics for Smartphone rPPG

| Study | Method | MAE (BPM) | Accuracy | Notes |
|:------|:-------|:---------:|:--------:|:------|
| Nowara et al. (2022) [7] | Green channel, DFT | 2.49 | — | Real-world driving conditions |
| Smartphone app validation [8] | rPPG | — | 97.34% | RMAPE 2.66% vs reference |
| Meijers et al. (2022) [9] | Clinical rPPG | 1.73–3.95 | 96.2% | Clinical settings |

### Assessment
**rPPG parameters are well-supported by literature.** All major design choices (green channel, DFT, 10s window, 40-200 BPM range) align with published methodology. The ±5 BPM accuracy claim is conservative relative to published MAE values. **No threshold changes recommended.**

---

## 2. Pallor Detector (`PallorDetector.kt`)

### Threshold-to-Literature Mapping

| Parameter | Nku Value | Literature Support | Status |
|:----------|:----------|:-------------------|:------:|
| **Color space** | HSV (Hue, Saturation, Value) | HSV/HSI color space analysis of conjunctival images is validated for hemoglobin correlation [10,11] | ✅ Well-supported |
| **Target tissue** | Palpebral conjunctiva | Clinical gold standard — conjunctiva is skin-tone-agnostic [12,13] | ✅ Well-supported |
| **Hue range** | 0–45° + 330–360° (pink/red wrap) | Corresponds to red/pink tissue; physiologically correct for vascularized conjunctiva | ✅ Anatomically sound |
| **Healthy saturation min** | **0.20** | Engineering estimate. Jay et al. (2024) used "high hue ratio" (HHR) rather than raw saturation [10]. No direct published validation of 0.20 as threshold. | ⚠️ **Needs field calibration** |
| **Pallor threshold** | **0.10** | Engineering estimate. Literature confirms low saturation correlates with anemia but exact cutoff varies by camera, lighting, and skin tone [11]. | ⚠️ **Needs field calibration** |
| **Min tissue pixel ratio** | 0.25 (25% of ROI) | Engineering estimate for image quality gating | ⚠️ Reasonable estimate |
| **Conjunctiva sensitivity** | 1.2x boost | Engineering estimate to weight conjunctival signal | ⚠️ Reasonable estimate |
| **Severity mapping** | NORMAL <0.3, MILD 0.3–0.5, MODERATE 0.5–0.7, SEVERE >0.7 | Arbitrary score ranges; no clinical hemoglobin-level correspondence | ⚠️ **Needs field calibration** |

### Published Performance Metrics for Smartphone Conjunctival Pallor

| Study | Method | Sensitivity | Specificity | Accuracy | Population |
|:------|:-------|:-----------:|:-----------:|:--------:|:-----------|
| Jay et al. (2024) [10] | Conjunctival hue ratio | 54.3% | 89.7% | 75.4% | ED patients |
| Jay et al. (2024) — severe (<7 g/dL) | Same | 69.6% | 94.0% | 92.7% | Same |
| Smartphone CNN app [14] | CNN + conjunctiva | 90% | 95% | 92.5% | Research cohort |
| Zucker et al. (1997) [12] | Clinical pallor (physician) | 80% | 82% | — | Kenyan children |
| Dimauro et al. (2022) review [11] | Multiple methods | 54–90% | 73–95% | 72–92% | Systematic review |

### Assessment
**The modality (conjunctival pallor via smartphone) is well-validated.** However, **Nku's specific saturation thresholds (0.10 and 0.20) are engineering estimates without published validation.** The severity score mapping is arbitrary. Field calibration against hemoglobin measurements is essential to determine optimal cutoffs for the target population and device cameras. **No code changes recommended until field data is available.**

---

## 3. Edema Detector (`EdemaDetector.kt`)

### Threshold-to-Literature Mapping

| Parameter | Nku Value | Literature Support | Status |
|:----------|:----------|:-------------------|:------:|
| **Method** | Eye Aspect Ratio (EAR) + cheek fullness | EAR is established in drowsiness detection literature [15]; adapted here for edema | ⚠️ Novel application |
| **EAR formulation** | width / height (inverted from standard) | Standard EAR = height/width ≈ 0.3 open [15]. Nku inverts: width/height ≈ 2.8 | ⚠️ Custom formulation |
| **Normal EAR** | **2.8** (width/height) | Equivalent to standard EAR ≈ 0.36 (1/2.8). Literature: open-eye EAR ≈ 0.30–0.36 [15,16] | ✅ Consistent with literature |
| **Edema EAR** | **2.2** (width/height) | No direct published precedent for using EAR to detect edema. Engineering estimate. | ⚠️ **Needs field calibration** |
| **Weighting** | Periorbital 60%, Facial 40% | Engineering estimate. Periorbital edema is a more specific clinical sign for preeclampsia [17] | ⚠️ Reasonable estimate |
| **Cheek brightness threshold** | 0.15 | Engineering estimate for detecting tissue swelling via skin texture changes | ⚠️ **Needs field calibration** |
| **Severity mapping** | NORMAL <0.25, MILD 0.25–0.5, MODERATE 0.5–0.75, SIGNIFICANT >0.75 | Arbitrary score ranges | ⚠️ **Needs field calibration** |
| **Min face ratio** | 0.20 (20% of frame) | Engineering estimate for image quality gating | ⚠️ Reasonable estimate |

### Published Performance Metrics for CV-Based Edema Detection

| Study | Method | Accuracy | Notes |
|:------|:-------|:--------:|:------|
| NEC/Tsukuba (2023) [18] | AI facial edema from photos | 85% | Dialysis patients; MAE 0.5 kg body weight |
| PeriorbitAI [19] | DL periorbital metrics | Comparable to human graders | 9 periorbital measurements from smartphone video |
| Sokolova & Cech (2017) [15] | EAR from landmarks | EAR ≥0.3 open, ~0.2 closed | Drowsiness detection (not edema) |

### Assessment
**This is the least-supported detector.** The adaptation of EAR from drowsiness detection to edema screening is a **novel application without direct published precedent**. The NEC/Tsukuba study validates that facial edema *can* be detected from photographs (85% accuracy), but uses a different methodology. The 60/40 periorbital-facial weighting is physiologically reasonable (periorbital edema is a more specific preeclampsia sign) but is not derived from clinical data. **Field calibration against physician edema assessment is critical.**

---

## Summary: Calibration Readiness

| Detector | Modality Support | Threshold Support | Overall | Recommended Action |
|:---------|:----------------:|:-----------------:|:-------:|:-------------------|
| **rPPG** | ✅ Strong | ✅ Strong | ✅ **Ready** | No changes; parameters align with literature |
| **Pallor** | ✅ Strong | ⚠️ Weak | ⚠️ **Needs calibration** | Modality validated; sat thresholds need field data |
| **Edema** | ⚠️ Moderate | ⚠️ Weak | ⚠️ **Needs calibration** | Novel EAR application; all thresholds need field data |

### Field Calibration Protocol (Recommended)

| Detector | Ground Truth | Minimum Sample | Calibration Method |
|:---------|:-------------|:--------------:|:-------------------|
| **rPPG** | Pulse oximeter (concurrent) | n=50 | Bland-Altman analysis |
| **Pallor** | Hemoglobin via CBC | n=100 | ROC curve → optimal sat cutoffs |
| **Edema** | Physician edema grading (0-4 scale) | n=80 | ROC curve → optimal EAR/score cutoffs |

---

## References

[1] Verkruysse, W., et al. *Optics Express* 16(26), 2008. DOI: 10.1364/OE.16.021434
[2] Various rPPG DFT implementations reviewed in systematic literature (PubMed, IEEE)
[3] Standard physiological BPM range per AHA guidelines
[4] 10-second analysis windows used in: multiple NIH-indexed rPPG studies
[5] Nyquist theorem: sampling rate must be ≥2× max frequency
[6] Verkruysse 2008 (green channel hemoglobin absorption peak)
[7] Nowara, E.M., et al. *IEEE Trans. ITS*, 2022 (MAE 2.49 BPM)
[8] Smartphone rPPG validation study (97.34% accuracy, medRxiv)
[9] Meijers, L., et al. *JMIR mHealth* 10(12), 2022. DOI: 10.2196/42178
[10] Jay, G.D., et al. *PLOS ONE* 19(1), 2024. DOI: 10.1371/journal.pone.0295563
[11] Dimauro, G., et al. *Artif. Intell. Med.* 126, 2022
[12] Zucker, J.R., et al. *Bull. WHO* 75(Suppl 1), 1997
[13] WHO IMCI conjunctival pallor assessment guidelines
[14] Smartphone CNN conjunctival analysis (90% sens, 95% spec)
[15] Sokolova, T. & Cech, J. *CVWW*, 2017 (EAR from facial landmarks)
[16] Multiple EAR studies: normal open-eye EAR ≈ 0.25–0.36
[17] ACOG Practice Bulletin No. 222 (Preeclampsia, 2020)
[18] NEC/Tsukuba facial edema AI, 2023 (85% accuracy)
[19] PeriorbitAI periorbital measurement system (comparable to human graders)
