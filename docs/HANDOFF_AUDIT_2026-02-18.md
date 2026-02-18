# Nku Audit + Fix Handoff (MedGemma Impact Challenge)

Date: 2026-02-18 (EST)  
Workspace: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335`  
Branch: `main`  
Scope: full review handoff for sensor integration, model integration, frontend/backend behavior, security, and performance signals.

## 1) Executive Summary

This pass addressed all five reported findings and added additional hardening work across cloud and Android paths.  
The project now has stronger auditability, safer prompt-injection handling, more truthful translation behavior claims, and improved Android runtime test coverage for respiratory model execution.

Important caveat: this environment still cannot prove full physical-device camera/microphone behavior under real field conditions. Emulator/instrumented coverage improved, but physical hardware validation remains an open risk before final challenge submission.

## 2) Findings -> Fixes Implemented

## Finding 1 [P2]
Benchmark artifact mismatch could undermine submission credibility.

### Fix
- Added explicit deprecation/provenance metadata to backup artifact:
  - `benchmark/medqa_benchmark_results_backup.json` (`artifact_status: "deprecated"`, provenance note, canonical artifact pointer)
- Added benchmark artifact policy:
  - `benchmark/README.md`

### Verification
- Backup file now self-labels as non-canonical.
- Canonical files are documented as:
  - `benchmark/medqa_benchmark_results.json`
  - `benchmark/medqa_iq2xs_results.json`
  - `benchmark/medqa_q2k_results.json`

---

## Finding 2 [P1]
Mobile app previously implied cloud translation fallback but shipped behavior passed raw unsupported-language input.

### Fix
- Runtime behavior made explicit and truthful:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`
    - unsupported language: on-device unavailable -> raw pass-through with explicit UI/log message
    - removed misleading "cloud" progress paths in shipped mobile flow
- Translator docs/comments aligned to actual shipped behavior:
  - `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt`
- Public docs claims aligned:
  - `README.md`
  - `docs/ARCHITECTURE.md`
  - `kaggle_submission_writeup.md`

### Verification
- Code path now consistently routes unsupported languages to offline pass-through.
- No runtime text claims cloud translation client exists in mobile build.

---

## Finding 3 [P1]
Cloud TranslateGemma defaults were placeholders and non-resolvable in default deployments.

### Fix
- Replaced placeholder defaults with deployable defaults in:
  - `cloud/inference_api/config.py`
    - `translategemma_repo` default -> `mradermacher/medgemma-4b-it-GGUF`
    - `translategemma_file` default -> `medgemma-4b-it.Q2_K.gguf`
- Added regression test:
  - `tests/test_config.py`

### Verification
- `tests/test_config.py` asserts defaults are not placeholder strings and are non-empty.
- Full Python test suite passes (see Section 4).

---

## Finding 4 [P1]
Regex-only prompt protection allowed common leetspeak obfuscation bypasses.

### Fix
- Added leetspeak normalization for detection in cloud validator:
  - `cloud/inference_api/security.py`
    - `LEETSPEAK_MAP`
    - `_normalize_leetspeak`
    - dual-check in `_check_injection_patterns` on raw + leet-normalized text
- Added cloud tests:
  - `tests/test_security.py`
    - `test_leetspeak_injection_blocked`
    - legitimate numeric medical text explicitly preserved
- Added mirrored protection on mobile:
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt`
  - `mobile/android/app/src/test/java/com/nku/app/PromptSanitizerTest.kt`

### Verification
- Attack strings like `ign0re all previous instructions` and `ignore all pr3vious instructions` are blocked in tests.
- Legitimate text like `BP 105/70 for 3 days with fever 39.2C` remains allowed.

---

## Finding 5 [P2]
Sensor-to-model pipeline lacked hardware-backed end-to-end evidence.

### Fix
- Added Android instrumented respiratory runtime smoke test:
  - `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt`
  - validates detector execution with app runtime + packaged assets and asserts bounded outputs
- Ensured instrumentation runner is properly configured:
  - `mobile/android/app/build.gradle`
    - `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"`

### Verification
- Connected Android tests now run and pass with this class included (9/9 pass).
- Respiratory runtime smoke test appears in connected test report:
  - `RespiratoryPipelineInstrumentedTest.respiratoryDetector_processAudio_runsOnDeviceRuntime`

## 3) Additional Fixes Applied In This Pass

These were not in the five new findings list but were implemented during this remediation cycle.

1. Cloud endpoint-scoped model loading
- File: `cloud/inference_api/main.py`
- `require_models` now accepts per-endpoint requirements:
  - `/translate` requires translation model only
  - `/triage` requires MedGemma only
  - `/nku-cycle` requires both
- Reduces avoidable endpoint downtime when only one model is needed.

2. Cloud output validation gating in `/nku-cycle`
- File: `cloud/inference_api/main.py`
- Translation, triage, and back-translation outputs are now checked for `is_valid` before returning.
- Prevents unsafe/invalid model outputs from silently passing through.

3. Respiratory path correctness and lifecycle hardening
- Files:
  - `mobile/android/app/src/main/java/com/nku/app/screens/RespiratoryScreen.kt`
  - `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt`
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt`
- Improvements:
  - uses deep path (`processAudioDeep`) when ViT-L is available, else event detector path
  - safer model candidate load loop with input/output verification
  - safer file resource handling with `.use`
  - explicit `respiratoryDetector.close()` in lifecycle cleanup

4. Startup/perf guardrails
- File: `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt`
- Face detector/landmarker initialization switched to lazy-first-use to reduce cold-start blocking.

5. Locale-safe formatting in clinical embedding text
- File: `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt`
- Numeric formatting now uses `Locale.US` to avoid locale-specific decimal formatting drift.

6. UI test correction
- File: `mobile/android/app/src/androidTest/java/com/nku/app/HomeScreenTest.kt`
- Fixed preeclampsia tab index assertion (`3` -> `4`) to match actual navigation index.

## 4) Verification Runs (Latest)

All commands run on 2026-02-18 EST from this workspace.

1. Cloud/Python tests
- Command: `./.audit_venv/bin/python -m unittest discover -s tests -v`
- Result: `Ran 52 tests ... OK`

2. Coverage
- Command:
  - `./.audit_venv/bin/python -m coverage run -m unittest discover -s tests`
  - `./.audit_venv/bin/python -m coverage report -m`
- Result: total coverage `79%`

3. Android unit tests
- Command: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --no-daemon --stacktrace`
- Result: `BUILD SUCCESSFUL`
- JUnit XML totals: `160 tests, 0 failures, 0 errors` (`testDebugUnitTest`)

4. Android connected instrumentation tests
- Command: `JAVA_HOME=... ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace`
- Result: `Finished 9 tests ... BUILD SUCCESSFUL`
- Includes respiratory runtime test:
  - `com.nku.app.RespiratoryPipelineInstrumentedTest.respiratoryDetector_processAudio_runsOnDeviceRuntime`

5. Android assemble
- Command: `JAVA_HOME=... ./gradlew :app:assembleDebug --no-daemon --stacktrace`
- Result: `BUILD SUCCESSFUL`

6. Android lint
- Command: `JAVA_HOME=... ./gradlew :app:lintDebug --no-daemon --stacktrace`
- Result: `BUILD SUCCESSFUL`
- Lint summary: `35 warnings, 0 errors`

7. Static security scan
- Command: `./.audit_venv/bin/python -m bandit -r cloud src -f txt`
- Result: `6 findings` (4 medium, 2 low), listed in Section 6.

8. Dependency vulnerability scan
- Command: `./.audit_venv/bin/pip-audit`
- Result: failed in this environment due DNS/network resolution to `pypi.org`.

## 5) Frontend + Backend Audit Summary

## Frontend (Android/Compose)

Validated:
- App builds (`assembleDebug`)
- UI instrumentation suite runs (9/9)
- Home screen flow assertions pass after tab index fix
- Respiratory detector runtime executes on Android instrumentation target

Not fully validated:
- Real camera signal behavior (rPPG, pallor, jaundice, edema) on physical hardware/lighting
- Real microphone quality variability on diverse Android devices
- Thermal behavior on prolonged physical-device runs

## Backend (Cloud Flask API)

Validated:
- Route-level validation logic, injection handling, and error behavior via test suite
- Config regression guard for non-placeholder translation model defaults
- Endpoint-scoped model requirement logic in code

Not fully validated in this environment:
- Real llama-cpp model loading/inference quality on cloud instance with actual weights
- End-to-end live HuggingFace download behavior under production network/auth conditions

## 6) Security Audit Summary

## Fixed/Improved
- Prompt injection defenses now include leetspeak normalization (cloud + mobile parity)
- Existing zero-width + homoglyph normalization retained
- Base64 payload checks retained
- Delimiter leakage checks retained and enforced

## Remaining Security Debt (from Bandit)

1. HuggingFace downloads not revision-pinned (supply-chain integrity risk)
- `cloud/inference_api/main.py`
- Recommendation: set and require immutable revisions (`MEDGEMMA_REVISION`, `TRANSLATEGEMMA_REVISION`) in production.

2. `0.0.0.0` bind warning
- `cloud/inference_api/main.py`
- Typical for containerized deployment; keep restricted by network perimeter/API auth.

3. Broad `except` with `continue` in base64 decode loop
- `cloud/inference_api/security.py`
- Low severity, but could tighten error handling/metrics.

4. Non-crypto RNG in thermal simulation utility
- `src/edge/thermal_manager.py`
- Low severity and not auth/crypto-facing.

5. `pip-audit` unresolved
- Could not complete due network constraints; no dependency CVE verdict from this run.

## 7) Performance/Efficiency Audit Notes

Improvements made:
- Lazy MediaPipe startup in `MainActivity` reduces cold-start overhead.
- Respiratory model loader now tries INT8 then FP32 with explicit validation and cleaner resource lifecycle.
- Endpoint-scoped cloud model loading avoids unnecessary model downloads/initialization for unrelated endpoints.

Remaining performance validation gap:
- No fresh physical-device latency/thermal benchmark set captured in this pass; emulator timing is not a substitute for low-end Android hardware.

## 8) Sensor -> Triage Pipeline Assessment

What is now better evidenced:
- Respiratory detector executes in Android runtime and returns bounded outputs.
- Sensor-related unit tests remain green (`RespiratoryDetectorTest`, `SensorFusionTest`, etc.).

What still needs physical validation before final claim confidence:
- Camera acquisition quality and physiological metric stability on actual devices
- End-to-end "capture -> sensor fusion -> MedGemma triage -> localized output -> TTS" under field-like constraints
- Multi-device variability (Transsion-class hardware, low light, noise)

## 9) Challenge Alignment (Merits, Demerits, Win Signal)

Note: direct Kaggle rules pages were not machine-readable from this environment due anti-forgery/session gating, so alignment assessment is based on repository submission docs and the public competition entry point.

## Merits
- Strong Edge AI narrative: quantization, mmap inference, on-device workflow, budget-phone target.
- Clear real-world problem framing and CHW use case.
- Broad engineering depth: sensors + LLM triage + multilingual UX + offline-first constraints.
- Open-source packaging and reproducibility artifacts are present.

## Demerits / judge-risk
- Evidence gap on physical-device validation for all sensor modalities.
- Some docs still make strong deployment/verification claims that require hard field evidence.
- Security debt remains (revision pinning, unresolved dependency CVE scan).
- Translation pass-through for unsupported languages can reduce triage quality for certain locales.
- Lint warning backlog (non-blocking, but quality signal for reviewers).

## Realistic win signal (engineering-only view)
- Edge AI/Novel-task competitiveness: **medium-high** if physical-device evidence is strengthened and security/provenance cleanup is completed.
- Main track competitiveness: **medium**; strongest if you tighten claim-evidence coupling and provide robust field/clinical validation traces.

## 10) Recommended Final Pre-Submission Actions (Priority Order)

1. Capture physical-device validation runs (at least 2 target budget phones) with logged metrics and reproducible scripts.
2. Pin model revisions in production env and document exact model digests used for submission.
3. Resolve `pip-audit` in network-enabled environment and patch any high/critical CVEs.
4. Reduce/triage Android lint warnings; close high-signal warning categories.
5. Add at least one Android instrumented test that exercises camera acquisition path to confidence-gated triage handoff.
6. Add explicit "unsupported language quality caveat" in final submission video/demo narration.
7. Freeze benchmark artifact policy in CONTRIBUTING/release notes so deprecated artifacts cannot be misused in future claims.

---

If needed, this handoff can be converted into:
- a one-page reviewer memo,
- a PR checklist template,
- or a "submission readiness gate" markdown checklist for final Kaggle upload.
