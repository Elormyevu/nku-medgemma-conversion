# Nku Comprehensive Audit Handoff (2026-02-20)

## 1) Snapshot

- Repository: `nku-impact-challenge-1335`
- Branch: `main`
- Commit audited: `170e3edc69b5eca3a5ceabb3d6599c0222e2e13a` (commit date `2026-02-19T21:59:59-05:00`)
- Audit intent: Frontend + backend code audit, runtime verification, performance/efficiency review, security review (including prompt-injection surface), and claim-to-ground-truth cross-check.
- Constraint reminder: No source changes made in this audit pass.

## 2) Scope and Method

This handoff combines:

1. Static code/document inspection (Android app, cloud backend, tests, manifests, build config, submission docs).
2. Executed runtime checks (Python tests, Android unit tests, Android instrumented tests, lint, debug/release builds, ADB launch smoke).
3. Security tooling (`bandit`; dependency audit attempt via `pip-audit`).
4. Packaging and performance artifact inspection (APK/AAB size and content composition).
5. Claim parity validation against submission docs.

## 3) Executed Verification Summary

### 3.1 Backend / Python

- `./.audit_venv/bin/python -m unittest discover -s tests -v` -> `Ran 68 tests ... OK`
- `./.audit_venv/bin/python -m pytest -q` -> `68 passed in 13.35s`
- `./.audit_venv/bin/python -m coverage run -m unittest discover -s tests && coverage report -m` -> `81%` total coverage.
  - Lower-coverage hotspots:
    - `cloud/inference_api/main.py` -> `73%`
    - `cloud/inference_api/security.py` -> `67%`
    - `src/rppg/processor.py` -> `43%`
- `./.audit_venv/bin/python -m bandit -r cloud src -x cloud/inference_api/security_pytest_suite.py -f txt`
  - Result: `4 medium`, `2 low`, `0 high`.
- `./.audit_venv/bin/pip-audit`
  - Could not complete in current sandboxed network context (DNS failure to `pypi.org`), so no current-turn dependency CVE assertion can be made.

### 3.2 Android build/test

- `./gradlew :app:testDebugUnitTest --no-daemon` -> success.
  - Unit XML aggregate: `191 tests`, `0 failures`, `0 errors`.
- `./gradlew :app:lintDebug --no-daemon` -> success.
  - Lint report: `35 warnings`, `0 errors`, `0 fatals`.
- `./gradlew :app:assembleDebug --no-daemon` -> success.
- `./gradlew :app:connectedDebugAndroidTest --no-daemon` -> success.
  - Connected XML: `12 tests`, `0 failures`, `0 errors`, timestamp `2026-02-20T03:32:56`.
- `./gradlew :app:assembleRelease :app:bundleRelease --no-daemon` -> success.

### 3.3 Runtime smoke (ADB)

- Emulator detected: `emulator-5554` (Pixel 7 AVD, Android 15).
- Debug APK install: success.
- `am start -W -n com.nku.app/.MainActivity`:
  - Status `ok`, cold launch.
  - First-visible activity was permission controller (`GrantPermissionsActivity`), indicating first-run permission gate before normal flow.
- App log evidence captured:
  - HeAR INT8 load failure -> FP32 fallback loaded.
  - ViT-L encoder not found (Event Detector-only mode).
  - MedGemma GGUF not found in this direct-APK install path, so full on-device LLM path not active in this runtime scenario.

## 4) Findings (ordered by severity)

## P1 High

### F1. Submission/docs claim cloud translation fallback in mobile path, but shipped mobile code does not implement cloud translation client.

- Claim evidence:
  - `kaggle_submission_writeup.md:36`
  - `kaggle_submission_writeup.md:44`
  - `kaggle_submission_writeup.md:90`
  - `MODEL_DISTRIBUTION.md:4`
  - `MODEL_DISTRIBUTION.md:34`
- Code evidence:
  - `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:60-65` (future cloud client note, not wired)
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:260-265` (unsupported language -> raw input passthrough)
  - `cloud/inference_api/main.py:7-10` (backend explicitly marked optional extension, not mobile default)
- Impact:
  - Documentation/compliance risk for reviewer expectations.
  - User experience risk for unsupported languages (no true fallback translation in mobile runtime).

### F2. HeAR INT8 runtime incompatibility observed in instrumented execution; app falls back to larger FP32 model.

- Evidence:
  - `mobile/android/app/build/outputs/androidTest-results/connected/debug/nku_pixel7(AVD) - 15/logcat-com.nku.app.RespiratoryPipelineInstrumentedTest-respiratoryDetector_processAudio_runsOnDeviceRuntime.txt:12`
    - INT8 candidate failed (`FULLY_CONNECTED` op version mismatch).
  - Same log `:31`
    - FP32 model loaded successfully.
  - Code fallback logic: `mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt:194-219`.
- Impact:
  - Performance/memory profile differs from INT8 claims.
  - Device compatibility risk for low-end targets if FP32 fallback becomes default behavior.

### F3. End-to-end sensor -> MedGemma -> localized output path is not truly validated by current automated tests.

- Evidence:
  - Respiratory instrumented test uses synthetic sine-wave audio and checks bounded fields only:
    - `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:26-43`
  - Camera instrumented test uses synthetic bitmap and does not run real camera capture stack:
    - `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:36-80`
  - `NkuInferenceEngine` unit tests are data-class/enum-only and explicitly exclude real inference:
    - `mobile/android/app/src/test/java/com/nku/app/NkuInferenceEngineTest.kt:7-14`
  - `SensorFusion` tests explicitly avoid live sensor pipeline:
    - `mobile/android/app/src/test/java/com/nku/app/SensorFusionTest.kt:16-19`
- Impact:
  - High confidence in component correctness, but not in full clinical pipeline behavior under real sensor inputs and real model responses.

### F4. Reviewer execution risk: direct APK installs do not include MedGemma asset pack, so real MedGemma reasoning path is unavailable unless PAD flow is used.

- Evidence:
  - No GGUF inside debug or release APK:
    - `app/build/outputs/apk/debug/app-debug.apk` (no `.gguf` entries)
    - `app/build/outputs/apk/release/app-release-unsigned.apk` (no `.gguf` entries)
  - Runtime log after direct APK install:
    - `NkuEngine: Model not found anywhere` and fallback behavior in logs.
  - Code fallback path:
    - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:327-330`.
- Impact:
  - If reviewers use APK-only install flow instead of AAB/PAD install path, MedGemma-enabled behavior will not match submission claims.

### F5. Artifact size/packaging claims materially diverge from produced artifacts.

- Claim evidence:
  - `kaggle_submission_writeup.md:19` (50MB core app claim)
  - `MODEL_DISTRIBUTION.md:20` (`~8MB` base module claim)
  - `docs/ARCHITECTURE.md:196` (`~60 MB base` claim)
- Produced artifacts:
  - `app-debug.apk` ~`1.2G`
  - `app-release-unsigned.apk` ~`1.2G`
  - `app-release.aab` ~`2.6G`
  - AAB compressed bytes:
    - `base`: `357,274,381` (~`340.72 MB`)
    - `medgemma`: `2,449,298,834` (~`2335.83 MB`)
- Additional contributor evidence:
  - Multiple large native variants and TF Lite Flex libs included:
    - `mobile/android/smollm/src/main/cpp/CMakeLists.txt:118-133`
    - `mobile/android/smollm/src/main/java/io/shubham0204/smollm/SmolLM.kt:62-99`
    - `app/build/outputs/apk/debug/app-debug.apk` largest entries include multiple `libsmollm_*` and `libtensorflowlite_flex_jni.so` variants.
- Impact:
  - Reviewer expectation mismatch.
  - Distribution and install friction risk.

## P2 Medium

### F6. Cloud backend model load path degrades opaquely when `llama_cpp` is unavailable (`Llama = None`), surfacing generic 503s.

- Evidence:
  - Optional import: `cloud/inference_api/main.py:30-33`
  - Constructor call assumes callable `Llama`: `cloud/inference_api/main.py:296-320`
  - Error handling wraps as generic model load failure: `cloud/inference_api/main.py:325-347`
  - Observed in test logs: `'NoneType' object is not callable`.
- Impact:
  - Operational debugging friction and misleading failure semantics for deployments.

### F7. Rate limiter trusts `X-Forwarded-For` directly from client headers.

- Evidence:
  - `cloud/inference_api/security.py:455-462`
- Impact:
  - Header spoofing can weaken client identity integrity and reduce effectiveness of throttling controls in some deployment topologies.

### F8. JSON validation layer has no explicit request body size cap before parsing.

- Evidence:
  - `cloud/inference_api/security.py:659-699`
  - Parses JSON (`request.get_json`) without content-length guard.
- Impact:
  - Avoidable memory/CPU pressure exposure from oversized payloads.

### F9. Local GGUF integrity checks are minimal (header + min size), no cryptographic verification.

- Evidence:
  - `mobile/android/app/src/main/java/com/nku/app/ModelFileValidator.kt:13-35`
  - Sideload path used in resolver:
    - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:119-127`
- Impact:
  - Tampered-but-well-formed model files are not detected by integrity policy.

### F10. Merged manifests include network permissions despite "no data transmission" claims.

- Claim:
  - `docs/ARCHITECTURE.md:203` ("no data transmission")
- Manifest evidence:
  - Source manifest removes explicit INTERNET:
    - `mobile/android/app/src/main/AndroidManifest.xml:11-13`
  - Merged manifests still include:
    - `android.permission.ACCESS_NETWORK_STATE`
    - `android.permission.INTERNET`
    - Seen in:
      - `mobile/android/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`
      - `mobile/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
- Impact:
  - Compliance/perception risk unless explicitly documented as transitive/asset-delivery necessity.

### F11. Language-code mismatch between cloud and mobile for Ewe may break cross-surface interoperability.

- Evidence:
  - Cloud validator allows `ewe`:
    - `cloud/inference_api/security.py:43`
  - Mobile uses ISO `ee`:
    - `mobile/android/app/src/main/java/com/nku/app/LocalizedStrings.kt:28`
    - `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:72`
- Impact:
  - Integration bugs if cloud fallback/extension is later wired to mobile without normalization.

## P3 Low

### F12. Lint warning backlog remains.

- Evidence:
  - `mobile/android/app/build/reports/lint-results-debug.xml`
  - Counts: `35 warnings`, `0 errors`, `0 fatals`.
- Impact:
  - Technical debt; potential future regressions if warnings hide real defects.

### F13. Dependency CVE scan could not be fully verified in this run due network restriction.

- Evidence:
  - `pip-audit` failed DNS resolution to `pypi.org` in this environment.
- Impact:
  - Dependency risk posture not fully validated for this exact audit timestamp.

## 5) Sensor + Model Integration Deep Assessment (requested focus)

### What was verified successfully

1. Android instrumented tests run on emulator and pass (12/12), including respiratory detector runtime path.
2. Runtime logs prove actual HeAR model loading path executes on-device.
3. Clinical/fusion classes compile and pass unit suites.
4. Backend endpoints/security paths pass Python suites.

### What is still not proven end-to-end

1. Real camera capture -> real patient signal quality -> MedGemma reasoning under production model availability in the same run.
2. Real microphone cough recordings and clinical output quality under varied noise and device thermals.
3. Real unsupported-language translation fallback in mobile (current code path is passthrough, not cloud call).
4. Physical-device validation on low-cost target hardware (3GB RAM class, thermal stress, battery constraints).

### Reviewer-mode observations

1. First cold launch can surface permission gate before app flow.
2. Direct APK install path lacks MedGemma GGUF (asset pack model not available there), causing model-unavailable behavior.
3. Monkey automation produced non-zero exits in this environment; explicit `am start` + logcat provided more reliable runtime evidence.

## 6) Security Audit Summary

### Strengths

1. Prompt injection defenses exist both cloud and mobile.
2. Extensive regex/homoglyph/base64 controls are present.
3. Security headers and API-key gates exist in cloud path.
4. Prompt-injection related test suites are present and currently passing.

### Priority weaknesses

1. Trust boundary issue in rate-limiter client identity (`X-Forwarded-For` trust).
2. No hard cap on raw request body size before JSON parse.
3. GGUF file validation is not cryptographically strong.
4. End-to-end red-team style prompt-injection tests against live model outputs are limited.

## 7) Performance and Efficiency Summary

1. Build/test execution health is good (all executed suites green).
2. Artifact size is significantly larger than some documented claims.
3. Large binary contributors include:
   - Multi-ABI and multi-variant `smollm` native libraries.
   - `tensorflow-lite-select-tf-ops` flex delegates across ABIs.
4. Runtime signal:
   - Cold-start log observed around `263ms` (`NkuPerf`) in emulator run.
5. Respiratory path currently degrades to FP32 in observed run due INT8 op mismatch.

## 8) Claim vs Ground Truth Matrix (selected)

| Claim | Ground truth from audited build/code | Status |
|---|---|---|
| Unsupported mobile languages use cloud fallback | Mobile code path is passthrough/no cloud client in current build | Mismatch |
| Base app around 8MB/50MB/60MB (depending doc) | Produced debug/release APK about 1.2GB; AAB base compressed about 340.7MB | Mismatch |
| HeAR Event Detector INT8 baseline | Observed INT8 failure, FP32 fallback loaded on test device | Partial mismatch |
| 100% offline core triage path | True for on-device components when models present, but direct APK installs may lack MedGemma artifact | Conditional |

## 9) Recommended Remediation Priority

1. Align all submission/docs claims with current shipped behavior (translation fallback and size/distribution details).
2. Resolve HeAR INT8 compatibility (model or runtime version alignment) and lock regression test around it.
3. Add true end-to-end integration tests:
   - Real camera frame acquisition path.
   - Real mic capture path.
   - Sensor fusion to MedGemma response assertion.
4. Add reviewer-proof installation instructions for AAB/PAD so MedGemma is guaranteed available.
5. Harden cloud rate limiter identity trust model (proxy-aware header trust policy).
6. Add request-body size guardrails in cloud middleware.
7. Add signed-hash verification for GGUF assets (especially sideload paths).
8. Normalize language codes (`ee`/`ewe`) across cloud/mobile interfaces.
9. Reduce binary bloat by target ABI strategy and native library packaging discipline.
10. Close lint warnings and rerun full validation matrix.

## 10) Evidence Index (high-value artifacts)

- Connected test XML:
  - `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml`
- Respiratory runtime log:
  - `mobile/android/app/build/outputs/androidTest-results/connected/debug/nku_pixel7(AVD) - 15/logcat-com.nku.app.RespiratoryPipelineInstrumentedTest-respiratoryDetector_processAudio_runsOnDeviceRuntime.txt`
- Lint report:
  - `mobile/android/app/build/reports/lint-results-debug.xml`
- Unit test XMLs:
  - `mobile/android/app/build/test-results/testDebugUnitTest/`
- Release artifacts:
  - `mobile/android/app/build/outputs/apk/debug/app-debug.apk`
  - `mobile/android/app/build/outputs/apk/release/app-release-unsigned.apk`
  - `mobile/android/app/build/outputs/bundle/release/app-release.aab`

## 11) Limitations (important)

1. No physical low-end Android device was available in this run, so hardware-specific thermal/camera/mic variance is not fully represented.
2. `pip-audit` could not reach `pypi.org` in this environment, so dependency CVE status is not fully revalidated at this timestamp.
3. Automated test pass status should not be interpreted as complete clinical correctness validation; key gaps are listed in Section 5.

