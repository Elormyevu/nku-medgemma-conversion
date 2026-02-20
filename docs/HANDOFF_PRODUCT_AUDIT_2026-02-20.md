# Nku Product Audit Handoff

Date: 2026-02-20
Auditor: Codex (GPT-5)
Scope: End-to-end frontend/backend/security/performance/model+sensor integration audit, including runtime verification with build/test execution and claim-vs-code validation.

## 1) Executive Summary

Overall state:
- Core codebase is buildable and testable.
- Mobile and backend automated test suites are broadly healthy.
- Prompt-injection defenses are implemented on both mobile and cloud paths.
- Major blocker remains full MedGemma reviewer-style integration verification (real sideloaded trusted model path not exercised in this session).

Most important risks:
1. Reviewer-sideload pathway is fragile/unverified on modern Android storage behavior, despite docs/tests depending on `/sdcard/Download`.
2. End-to-end sensor-to-LLM validation still relies on synthetic inputs in instrumentation tests; real camera/mic acquisition + real MedGemma inference are not fully proven in this environment.
3. Cloud translation language-code mismatch (`om` vs `or`) can silently reroute requests to default language behavior.
4. Known dependency vulnerability remains in cloud requirements (`diskcache==5.6.3`, CVE-2025-69872).

---

## 2) What Was Executed (No Code Changes)

### Backend / Python
- `./.audit_venv/bin/pytest -q -rs`
  - Result: `68 passed, 1 skipped`
  - Skip reason: real MedGemma load unavailable due missing `llama_cpp` native library in current test env.
- `PYTHONPATH=. ../../.audit_venv/bin/pytest -q security_pytest_suite.py` in `cloud/inference_api`
  - Result: `74 passed`

### Android / Frontend + App Runtime
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
  - Result: `BUILD SUCCESSFUL`
- JVM unit report:
  - `mobile/android/app/build/reports/tests/testDebugUnitTest/index.html:23` shows `193` tests, `0` failures.
- Connected instrumentation:
  - `./gradlew :app:connectedDebugAndroidTest`
  - Result: success with 1 skip.
  - `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml:2` shows `tests="14" failures="0" skipped="1"`.
  - Skipped test:
    - `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml:20`
    - `medGemma_sideloadedModel_isDiscoverableAndTrusted` skipped because no sideloaded trusted 1GB+ model was available.
- Lint:
  - `mobile/android/app/build/reports/lint-results-debug.txt` => `No issues found.`

### Packaging / Distribution Validation
- APK contents check:
  - `app-debug.apk` and `app-release-unsigned.apk` include HeAR TFLite assets, not MedGemma GGUF.
- AAB contents check:
  - `app-release.aab` includes `medgemma/assets/medgemma-4b-it-q4_k_m.gguf`.
  - `zipinfo` shows size `2489894304` bytes.

### Security Tooling
- `./.audit_venv/bin/pip-audit -r cloud/inference_api/requirements.txt`
  - Found: `diskcache 5.6.3 CVE-2025-69872`.
- `./.audit_venv/bin/pip-audit -r requirements.txt`
  - Could not complete due dependency resolution failure for `jaxlib==0.4.38` under Python 3.14.
- `./.audit_venv/bin/bandit -q -r cloud src tests`
  - Summary: Medium `7`, Low `119`, High `0`.

---

## 3) Claim-vs-Ground-Truth Highlights

### Confirmed
- AAB model delivery is implemented (MedGemma in asset pack), consistent with production distribution intent.
  - Evidence: AAB contains `medgemma/assets/medgemma-4b-it-q4_k_m.gguf`.
- APK direct install path does not contain MedGemma model payload.
  - Evidence: APK contains HeAR `.tflite` assets and native libs; no `.gguf` model payload.
- Confidence gating at 75% is implemented in `ClinicalReasoner`.
  - `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:79`

### Mismatches / Drift
- Submission writeup still describes cloud translation fallback behavior in stage table.
  - `kaggle_submission_writeup.md:36` and `kaggle_submission_writeup.md:38`
- Mobile runtime currently does not wire cloud translation client for unsupported languages; it passes raw input through.
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:315`
- UI still surfaces an “Internet Required” warning dialog for cloud-only language helper flow.
  - `mobile/android/app/src/main/java/com/nku/app/LocalizedStrings.kt:369`
  - `mobile/android/app/src/main/java/com/nku/app/screens/HomeScreen.kt:116`
- README says Event Detector is “always loaded,” but runtime is lazy-init on first respiratory use.
  - Claim: `README.md:109`
  - Runtime: `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt:170`

---

## 4) Findings (Prioritized)

### P1 — Full reviewer-style MedGemma integration remains unproven in test execution

Evidence:
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:22`
- `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml:20`
- Skip condition requires real sideloaded 1GB+ model in `/sdcard/Download`.

Impact:
- The specific “model discoverable + trusted + loaded in reviewer-style sideload path” was not executed to pass in this run.
- Reviewer confidence on on-device model activation remains partially inferential.

Recommendation:
- Add a mandatory release-gate test job using a preprovisioned real Q4_K_M model file and hardware/emulator image configured for accessible model path.
- Emit explicit pass artifact for this test in CI/release checklist.

---

### P1 — Sensor-to-triage path is validated mostly with synthetic stimuli, not real capture fidelity

Evidence:
- Camera instrumentation uses generated bitmap frames:
  - `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:14`
  - `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:33`
- Respiratory instrumentation uses generated sine-wave audio:
  - `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:28`
- `NkuInferenceEngineTest` (JVM) only covers enum/data-class logic:
  - `mobile/android/app/src/test/java/com/nku/app/NkuInferenceEngineTest.kt:7`

Impact:
- Functional code paths are exercised, but real optical/acoustic capture variance (lighting/noise/device-specific camera/mic behavior) is not fully validated.

Recommendation:
- Add instrumented tests with recorded real-world media fixtures and at least one hardware-device validation matrix for final submission.
- Include end-to-end assertion from sensor capture -> fusion -> prompt -> MedGemma response parsing.

---

### P1 — `/sdcard/Download` sideload assumption is fragile on current Android storage model

Evidence:
- Sideload docs/tests depend on shared Download path:
  - `README.md:174`
  - `MODEL_DISTRIBUTION.md:10`
  - `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:24`
- Runtime search includes Download path:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:154`
- Runtime permission request only asks camera + mic:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:109`
- Manifest declares media/external permissions, but shell validation showed access failure in app context:
  - `mobile/android/app/src/main/AndroidManifest.xml:16`
  - Runtime check during audit: `adb shell run-as com.nku.app ls /sdcard/Download` -> `Permission denied`.

Impact:
- Reviewer/device-specific failures are likely if sideload guidance expects generic shared Download readability.

Recommendation:
- Replace reviewer sideload path with app-specific external directory and/or SAF document picker flow.
- Update docs/test instructions to match enforced path and required permission model.

---

### P2 — Cloud language validation mismatch (`om` vs `or`) can silently degrade translation behavior

Evidence:
- Cloud allows `or` but not `om`:
  - `cloud/inference_api/security.py:44`
- Mobile language list uses `om` for Oromo:
  - `mobile/android/app/src/main/java/com/nku/app/LocalizedStrings.kt:33`
- Invalid cloud language defaults silently to Twi/English:
  - `cloud/inference_api/main.py:479`

Runtime confirmation:
- `validate_language('om')` -> invalid
- `validate_language('or')` -> valid

Impact:
- Potential wrong-language routing or silent fallback for Oromo.

Recommendation:
- Normalize `om`/`or` consistently across mobile + backend + docs.
- Return explicit 400 for unsupported codes rather than silent default substitution in production mode.

---

### P2 — Documentation/spec drift on translation behavior (cloud fallback vs pass-through)

Evidence:
- Writeup stage table still indicates cloud fallback path:
  - `kaggle_submission_writeup.md:36`
  - `kaggle_submission_writeup.md:38`
  - `kaggle_submission_writeup.md:92`
- Runtime path for unsupported ML Kit language does direct pass-through:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:315`
- Current distribution doc already states pass-through in shipped build:
  - `MODEL_DISTRIBUTION.md:4`

Impact:
- Reviewer confusion and credibility risk if docs differ from executable behavior.

Recommendation:
- Align submission writeup with current shipped behavior and clearly mark cloud translation as optional/non-wired extension.

---

### P2 — Known dependency vulnerability unresolved (`diskcache`)

Evidence:
- `cloud/inference_api/requirements.txt:18` pins `diskcache==5.6.3`.
- `pip-audit` reports `CVE-2025-69872` for this version.

Impact:
- Supply-chain/security posture risk (even with runtime mitigations).

Recommendation:
- Upgrade when patched release is available, or vendor hardening workaround with explicit risk acceptance in submission docs.

---

### P3 — Full top-level dependency vulnerability audit incomplete under current Python runtime

Evidence:
- `pip-audit -r requirements.txt` failed resolving `jaxlib==0.4.38` on Python 3.14.
- `requirements.txt:9`

Impact:
- Incomplete vulnerability coverage for root dependency set in this environment.

Recommendation:
- Re-run pip-audit in a supported/pinned CI Python version matching deployment constraints (e.g., 3.11/3.12).

---

### P3 — Transient emulator install instability observed during audit

Observed during one run:
- `INSTALL_FAILED_INSUFFICIENT_STORAGE` occurred in a connected-test attempt, then subsequent clean rerun succeeded.
- Current final state: connected tests passing again.

Impact:
- Reviewer reproducibility risk on small/fragmented emulator storage configurations.

Recommendation:
- Include explicit storage prerequisites and clean-emulator guidance in reviewer runbook.
- Prefer AAB/PAD workflow for realistic reviewer evaluation.

---

## 5) Security Audit Notes (Prompt Injection + API Hardening)

Implemented controls (positive):
- Mobile prompt sanitizer with pattern checks, homoglyph normalization, zero-width stripping, base64 detection, output validation/truncation:
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:47`
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:311`
- Sensor symptom text is sanitized + delimiter-wrapped before prompt embedding:
  - `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:285`
- Cloud input validator and prompt protector include injection defenses + safe templates:
  - `cloud/inference_api/security.py:55`
  - `cloud/inference_api/security.py:304`
- Cloud rate limiting, API key guard, security headers, CORS config are present:
  - `cloud/inference_api/main.py:128`
  - `cloud/inference_api/main.py:116`
  - `cloud/inference_api/security.py:410`
  - `cloud/inference_api/security.py:644`

Test coverage:
- `security_pytest_suite.py` passed (`74` tests), including injection scenarios (regex/leetspeak/homoglyph/base64).

Residual risk:
- No formal red-team corpus replay evidence in this audit run beyond existing automated suites.

---

## 6) Performance / Efficiency Audit Notes

Verified optimizations:
- Model lifecycle unload + explicit GC after large GGUF use:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:258`
- Respiratory models lazy-init to reduce cold-start burden:
  - `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt:179`
- SHA validation cache avoids repeated full-file hash recomputation when unchanged:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:101`

Gaps:
- No reproducible on-device benchmark artifacts generated in this run for end-to-end latency/RAM under real model inference (due unavailable real MedGemma runtime load in Python tests and no trusted sideloaded model execution in Android instrumentation).

Recommendation:
- Add benchmark gate with thresholds (cold start, inference latency, memory peak, thermal behavior) and export artifacts per build.

---

## 7) Frontend (Mobile UI) Audit Notes

Validated:
- Compose UI instrumentation suite passed (Home screen flow tests included):
  - `mobile/android/app/build/reports/androidTests/connected/debug/index.html:22`
- Lint clean:
  - `mobile/android/app/build/reports/lint-results-debug.txt`

Risk notes:
- “Internet Required” translation dialog path may confuse users in a build that currently pass-throughs unsupported languages offline.
- Camera/mic permissions requested at startup rather than strictly contextual flow.

---

## 8) Reviewer Runbook Recommendations (MedGemma Challenge)

1. Prefer `.aab` install path to validate true PAD behavior for MedGemma.
2. If testing APK-only route, use documented fallback path that is guaranteed readable by app (update docs first per P1 finding).
3. Ensure emulator/device has sufficient free storage headroom (>4GB recommended).
4. Explicitly verify these artifacts in review evidence:
   - AAB contains `medgemma/assets/medgemma-4b-it-q4_k_m.gguf`
   - Connected tests pass and model integration test is either:
     - executed with real sideloaded trusted model and passes, or
     - explicitly skipped with rationale and separate manual verification attached.

---

## 9) Final Coverage Statement

What is covered with high confidence:
- Build integrity (Android debug build, JVM tests, connected tests, lint).
- Cloud security test suite and major API security controls.
- Prompt-injection defensive architecture and test coverage.
- Packaging/distribution truth (APK vs AAB/PAD model payload split).

What is not fully covered in this run:
- Real physical-device sensor capture fidelity validation (camera/mic variability).
- Full MedGemma real-model execution proof in instrumentation with trusted sideloaded Q4 model.
- Complete root dependency vulnerability scan under compatible Python for all requirements.

---

## 10) Immediate Action Plan (Recommended)

1. Fix sideload/reviewer path (P1): replace `/sdcard/Download` dependency with app-readable path/SAF and update tests/docs.
2. Align language code handling (P2): unify `om`/`or`, avoid silent defaulting.
3. Reconcile submission docs with runtime behavior (P2): translation fallback narrative and “always loaded” wording.
4. Close supply-chain gap (P2): address `diskcache` CVE strategy.
5. Add release-gate real-model integration artifact (P1): passing proof for `medGemma_sideloadedModel_isDiscoverableAndTrusted` equivalent on reviewer-grade setup.

