# Handoff - Remediation Implementation and Validation (2026-02-20)

## Scope
This handoff documents the implementation of the previously identified frontend/backend/security/performance/testability issues, plus full validation runs performed after the fixes.

Coverage includes:
- Android app runtime and build pipeline (including reviewer-facing connected tests).
- Cloud inference API reliability, security, and memory efficiency.
- Sensor-to-triage pipeline test-path hardening.
- Model trust and loading path hardening.
- Spec/documentation alignment to current shipped behavior.

## Executive Summary
All identified code-level issues targeted in this remediation pass were fixed and validated locally.

Validation status:
- Python unit/integration tests: PASS.
- Cloud security pytest suite: PASS.
- Dependency vulnerability audit (`pip-audit`): PASS (no known vulnerabilities found).
- Android unit tests: PASS.
- Android lint + debug/release assemble: PASS.
- Android connected instrumentation tests: PASS after emulator storage cleanup (one intentional skip for missing optional sideload model artifact).

## Findings and Fixes

### F-01 Critical model trust gap for sideloaded MedGemma
Severity: P0
Status: Fixed

Problem:
- Sideloaded model trust logic used placeholder validation behavior.
- Downloaded model path did not enforce trust verification post-download.

Fixes:
- Pinned MedGemma SHA-256 in inference engine.
- Added real SHA-256 verification for expected model.
- Added reusable validation cache to avoid repeated heavy hash operations.
- Enforced trust validation after download; invalid file is deleted.
- Expanded sideload search locations for reviewer usability.

Files:
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`

### F-02 MedGemma path unreachable in normal triage flow
Severity: P0
Status: Fixed

Problem:
- Triage path checked readiness before attempting model load, preventing automatic fallback/download path from being exercised in normal usage.

Fix:
- Triage now always attempts `runMedGemmaOnly(prompt)` first.
- If unavailable/failing, controlled fallback to rules-based triage remains.

Files:
- `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt`

### F-03 Prompt-injection hardening gap in patient free-text ingestion
Severity: P1
Status: Fixed

Problem:
- Patient free text could bypass sanitizer path in one critical cycle.

Fix:
- Applied `PromptSanitizer.sanitize(patientInput)` prior to use in run cycle.

Files:
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`

### F-04 Cold-start regression from eager respiratory model initialization
Severity: P1
Status: Fixed

Problem:
- Respiratory detector initialized models in constructor, adding startup overhead whether or not respiratory flow was used.

Fix:
- Deferred initialization to first call via synchronized lazy init gate.

Files:
- `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt`

### F-05 Cloud memory inefficiency due duplicate model residency
Severity: P1
Status: Fixed

Problem:
- Shared repo/file config for MedGemma and TranslateGemma could load same artifact twice.

Fix:
- Added shared-load optimization: if repo/file/revision are identical, load once and alias references.

Files:
- `cloud/inference_api/main.py`

### F-06 Rate-limiter config not fully respected
Severity: P1
Status: Fixed

Problem:
- Rate limiter object creation did not align with disable flag handling path.

Fix:
- Instantiate limiter conditionally from config.
- Security decorator now accepts optional limiter and no-ops when disabled.

Files:
- `cloud/inference_api/main.py`
- `cloud/inference_api/security.py`

### F-07 Inference cache storage hardening
Severity: P2
Status: Fixed

Problem:
- Cache directory defaults could leave broader-than-needed filesystem permissions.

Fixes:
- Created secure cache root setup before `llama_cpp` import.
- Set `XDG_CACHE_HOME`, `HF_HOME`, `HUGGINGFACE_HUB_CACHE`.
- Added permission hardening (`0700` best effort).
- Mirrored secure defaults in Docker runtime image for non-root user.

Files:
- `cloud/inference_api/main.py`
- `cloud/inference_api/Dockerfile`

### F-08 Test coverage mismatch with production pathways
Severity: P1
Status: Fixed

Problem:
- Some instrumentation smoke tests validated approximations instead of production detector invocation.
- Missing model checksum tests for validator path.

Fixes:
- Updated camera triage instrumentation to call real `PallorDetector` and `JaundiceDetector` APIs.
- Added prompt-handoff flow assertion via `SensorFusion` + `ClinicalReasoner`.
- Added checksum acceptance/rejection unit tests in model validator tests.
- Added reviewer-oriented model integration instrumentation test for sideload discovery.

Files:
- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt`
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt`
- `mobile/android/app/src/test/java/com/nku/app/ModelFileValidatorTest.kt`

### F-09 Build hygiene and tooling stability
Severity: P2
Status: Fixed

Problem:
- Unregistered pytest marker warning risk.
- Requirements parse issue from inline index-url syntax in `requirements.txt`.
- Gradle deprecation warnings from legacy assignment syntax.

Fixes:
- Registered `integration` marker in `pytest.ini`.
- Normalized `torch` requirement line to valid pip spec format.
- Updated key Gradle DSL assignments to modern `prop = value` syntax.

Files:
- `pytest.ini`
- `requirements.txt`
- `mobile/android/build.gradle`
- `mobile/android/app/build.gradle`
- `mobile/android/smollm/build.gradle`

### F-10 Documentation/spec drift from shipped behavior
Severity: P1
Status: Fixed

Problem:
- Some submission/docs language overstated currently wired translation behavior and size claims.

Fixes:
- Updated docs to reflect current implementation truthfully.
- Clarified cloud translation as optional extension (not wired in shipped offline mobile flow).
- Updated model distribution/reviewer guidance including checksum verification.

Files:
- `README.md`
- `MODEL_DISTRIBUTION.md`
- `kaggle_submission_writeup.md`
- `kaggle_submission_appendix.md`

### F-11 Dependency vulnerability tracking completeness
Severity: P2
Status: Fixed/mitigated

Problem:
- Audit dependency set did not explicitly include `diskcache` pin in cloud requirements.

Fix:
- Added explicit `diskcache==5.6.3` with note.

Files:
- `cloud/inference_api/requirements.txt`

## Validation Evidence

### Python and Security
1. `./.audit_venv/bin/python -m pytest -q`
- Result: `68 passed, 1 skipped in 3.50s`

2. `../../.audit_venv/bin/python -m pytest security_pytest_suite.py -q` (run from `cloud/inference_api`)
- Result: `74 passed in 0.10s`

3. `./.audit_venv/bin/pip-audit`
- Result: `No known vulnerabilities found`

### Android Build and Quality
1. `./gradlew :app:help --warning-mode all`
- Result: BUILD SUCCESSFUL

2. `./gradlew :app:testDebugUnitTest`
- Result: BUILD SUCCESSFUL

3. `./gradlew :app:lintDebug :app:assembleDebug :app:assembleRelease`
- Result: BUILD SUCCESSFUL

### Android Connected Instrumentation (Reviewer Simulation Path)
1. Initial run:
- `./gradlew :app:connectedDebugAndroidTest`
- Failure cause: emulator storage (`Requested internal only, but not enough space`) during APK install.

2. Environment remediation:
- Cleared emulator `/storage/emulated/0/Download/*`.

3. Rerun:
- `./gradlew :app:connectedDebugAndroidTest`
- Result: BUILD SUCCESSFUL
- Runtime summary: 15 tests completed, with 1 intentional skip:
  - `ModelIntegrationInstrumentedTest.medGemma_sideloadedModel_isDiscoverableAndTrusted` skipped because optional large sideload artifact was not present.

## Reviewer/Challenge Readiness Notes
- App compiles and packages in both debug and release modes.
- Connected instrumentation path executes successfully on emulator after ensuring adequate device storage.
- Model trust checks now validate both sideload and network-downloaded model paths.
- Sensor-to-triage prompt handoff is now covered by production-path instrumentation assertions.
- Unsupported-language behavior in offline mobile flow is now documented accurately as pass-through unless optional cloud extension is integrated.

## Residual Risks and Follow-ups
1. Full on-device MedGemma sideload test remains environment-dependent on presence of a large GGUF file; test currently skips safely when missing.
2. Real physical-device sensor behavior (camera/audio hardware variance, thermal constraints) should still be sampled on at least one Android device before final submission lock.
3. If CI infrastructure is added, include periodic connected-test runs on a dedicated AVD profile with guaranteed storage budget.

## Files Changed in This Remediation Set
- `MODEL_DISTRIBUTION.md`
- `README.md`
- `cloud/inference_api/Dockerfile`
- `cloud/inference_api/main.py`
- `cloud/inference_api/requirements.txt`
- `cloud/inference_api/security.py`
- `kaggle_submission_appendix.md`
- `kaggle_submission_writeup.md`
- `mobile/android/app/build.gradle`
- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt`
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt`
- `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`
- `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt`
- `mobile/android/app/src/test/java/com/nku/app/ModelFileValidatorTest.kt`
- `mobile/android/build.gradle`
- `mobile/android/smollm/build.gradle`
- `pytest.ini`
- `requirements.txt`
