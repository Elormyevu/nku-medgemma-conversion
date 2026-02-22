# Nku Sentinel — Reviewer Test Scenarios

This document provides explicit, reproducible testing paths for reviewing the Nku Sentinel clinical logic and UI behavior on an Android device or emulator.

## Hardware Requirements
- **Target OS:** Android 9.0 (API 28) or higher
- **Resources:** At least 3GB of free storage for MedGemma 4B download, 4GB+ RAM.

## Scenario 1: All Sensors Healthy (Green Triage)
**Goal:** Verify the system outputs a routine monitoring assessment when all biomarkers are well within the healthy range.
**Steps:**
1. Tap the "Report Symptoms" text box and enter: "I feel fine."
2. Tap the plus button to add the symptom.
3. Wait for all 5 simulated sensors (or real camera/mic pipeline) to hit >75% confidence.
4. Tap **Run Triage**.
**Expected Result:**
- Severity: LOW
- Urgency: ROUTINE
- Triage Category: GREEN
- UI Element: The large triage card should turn Green.

## Scenario 2: Severe Anemia Trigger (Orange/Red Triage)
**Goal:** Verify MedGemma escalates severity when critical biomarkers like pallor drop below the saturation threshold.
**Steps:**
1. (If using simulated inputs) Inject a Pallor Saturation of `0.08` with `0.80` confidence.
2. Tap **Run Triage**.
**Expected Result:**
- Severity: HIGH or CRITICAL
- Urgency: IMMEDIATE or WITHIN_48_HOURS
- Triage Category: RED or ORANGE
- UI Element: TTS should vocalize urgent referral for hemoglobin testing.

## Scenario 3: Sensor Abstention Gate (Rule-Based Fallback)
**Goal:** Verify the system refuses MedGemma inference if biometric confidence is too low and no symptoms are provided.
**Steps:**
1. Clear all symptoms from the input box.
2. Capture a blurry/shaky camera feed or inject sensor confidences of `<0.40`.
3. Tap **Run Triage**.
**Expected Result:**
- The assessment is generated instantaneously (MedGemma is bypassed).
- Triage Source Indicator (blue banner): Explains that MedGemma was bypassed.
- Primary Concern: "Insufficient data confidence for triage — all sensors below 75% threshold"

## Scenario 4: Preeclampsia High Risk (Context-Aware Reasoning)
**Goal:** Verify MedGemma links facial edema with pregnancy context to identify preeclampsia risk.
**Steps:**
1. In the app settings or context toggle, mark the patient as **Pregnant (24 weeks)**.
2. Ensure the Edema score hits `Moderate` or `Significant` (e.g. EAR threshold < 2.2).
3. Tap **Run Triage**.
**Expected Result:**
- Primary Concern: Explicitly mentions "preeclampsia screen recommended".
- Recommendations: Checking blood pressure and urine protein.
- Severity: MEDIUM to HIGH.
