# Nku Sentinel Physical Sensor Validation Checklist

As per audit finding **F-005**, synthetic emulator tests cannot fully prove real hardware behavior for the Camera and Microphone sensor pipelines. Use this checklist on a physical device.

## Prerequisites
- Minimum target device: Tecno/Infinix smartphone running Android 9 (API 28) or higher with 3GB+ RAM.
- Application installed via APK (non-PAD) with MedGemma model sideloaded or downloaded via first-run experience.

## Lighting Conditions (rPPG & Camera)

### 1. Optimal Clinical Lighting
- [ ] Ensure patient face is well-lit from the front (e.g., facing a window during daytime).
- [ ] Run the **Vitals (rPPG)** scan.
- [ ] **Acceptance:** Heart rate is detected within 15 seconds. Confidence score is >0.75. 

### 2. Low-Resource/Rural Lighting
- [ ] Perform the scan in a dim room with only one weak ambient light source (e.g., a single bulb or kerosene lamp equivalent).
- [ ] Run the **Anemia/Pallor** scan.
- [ ] **Acceptance:** The system should guide the user to move to better lighting OR fallback to a lower confidence score without crashing.

### 3. Backlit Scenario
- [ ] Perform the scan with a strong light source directly behind the patient.
- [ ] **Acceptance:** Face detection must correctly handle the exposure difference and not fail silently.

## Acoustic Noise Conditions (Microphone & HeAR)

### 1. Quiet Room Baseline
- [ ] Have the patient cough deliberately in a quiet room.
- [ ] **Acceptance:** The Respiratory Pipeline (HeAR Event Detector) correctly captures the event, classifies it, and passes it to the MedGemma triage with high confidence.

### 2. Clinic Waiting Area (Moderate Noise)
- [ ] Simulate background chatter and movement. Have the patient cough clearly into the microphone.
- [ ] **Acceptance:** The noise suppression properly isolates the cough transient without the event detector flagging random background noise as respiratory events.

### 3. Outdoor/Wind Noise (High Noise)
- [ ] Test the voice symptom input (STT) and cough detection while outdoors with wind or significant ambient noise (e.g., traffic).
- [ ] **Acceptance:** The STT engine should correctly map the patient's voice or gracefully time out/ask to repeat if the SNR is too low.

## Model Validation (F-001 Verification)
- [ ] **Clean Install Download:** Uninstall the app, reinstall it, and ensure the 2.3GB `medgemma-4b-it-q4_k_m.gguf` model downloads correctly from huggingface over Wi-Fi, validates the `8bcb19d3...` hash, and becomes active for triage.
