# Nku Sentinel Product Audit Handoff (Comprehensive)

Date: 2026-02-23 
Project root: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335` 
Commit audited: `7d08f61` (`main`) 
Prepared by: Codex 
Audit mode: Execution-backed frontend + backend + security + performance + sensor/model pipeline audit (no code changes)

---

## 1) Executive Summary

This audit performed both static review and live execution across Android and Python/backend stacks. 
Result: **Not release-ready for a “fully verified latest build” claim yet**.

Primary blockers:
- Latest Android source currently fails to compile (`NkuTranslator.kt` unresolved references).
- Translation fallback path contains emulator-specific networking and hardcoded dev API key behavior.
- Known backend dependency CVEs are present in pinned cloud requirements.
- Full real-model backend integration was not executed in this environment due missing `llama_cpp` native runtime.
- Sensor validation remains predominantly emulator/synthetic; physical-device proof is still incomplete.

---

## 2) Scope Requested vs Scope Achieved

### 2.1 Requested
- Thorough frontend audit.
- Thorough backend audit.
- End-to-end runtime execution (not code-read only).
- Performance/efficiency audit.
- Security and prompt-injection audit.
- Sensor integration validation (especially reviewer path / MedGemma challenge style).
- AI model integration and sensor-to-triage pipeline verification.

### 2.2 Achieved
- Comprehensive static review of key Android, backend, tests, security, CI, and submission-doc files.
- Executed Python tests, backend security suite, benchmark sensor tests.
- Executed dependency and SAST scans.
- Ran Android build tasks and captured compiler failures on latest source.
- Ran app on emulator using existing debug APK artifact and captured runtime logs.

### 2.3 Hard Limits During Audit
- No physical Android device available for real camera/microphone field-condition validation.
- Latest-source Android runtime path blocked by compile error (could not build fresh APK from current source).
- Backend real model integration path skipped in current environment when `llama_cpp` is unavailable.

---

## 3) Test and Execution Evidence

## 3.1 Android Build/Runtime

- Command: `./gradlew :app:compileDebugKotlin --no-daemon --stacktrace`
 - Result: **FAILED**
 - Errors:
  - `NkuTranslator.kt:146:13 Unresolved reference 'sourceLang'`
  - `NkuTranslator.kt:146:27 Unresolved reference 'targetLang'`
  - `NkuTranslator.kt:149:32 Unresolved reference 'sourceLang'`
  - `NkuTranslator.kt:150:32 Unresolved reference 'targetLang'`

- Command: `./gradlew :app:connectedDebugAndroidTest --no-daemon`
 - Result: blocked by same compile error on latest source.

- Emulator runtime (existing APK artifact):
 - Device: `emulator-5554` (Android emulator).
 - Launch command succeeded (`am start -W -n com.nku.app/.MainActivity`).
 - Sample logs:
  - `NkuPerf: Cold-start: onCreate init took 48ms`
  - `NkuTTS: TTS initialized successfully`
  - `NkuEngine: Model already accessible, skipping extraction`
 - Important caveat:
  - APK artifact timestamp: `2026-02-23 10:00:11`
  - Commit audited timestamp: `2026-02-23 16:33:07`
  - Runtime smoke launch was on an existing artifact, not a fresh build from latest failing source.

## 3.2 Python / Backend

- Command: `./.audit_venv/bin/pytest -q`
 - Result: `142 passed, 1 skipped`

- Command: `./.audit_venv/bin/pytest -q -rs`
 - Skip reason:
  - `MedGemma model not available, skipping: Failed to load models: llama_cpp native library is unavailable.`

- Command: `./.audit_venv/bin/pytest -q cloud/inference_api/test_security_suite.py`
 - Result: `74 passed`

- Command: `./.audit_venv/bin/pytest -q benchmark/test_sensor_validation.py -v`
 - Result: `39 passed`

## 3.3 Security and Dependency Scanning

- Command: `./.audit_venv/bin/pip-audit -r cloud/inference_api/requirements.txt`
 - Result: 3 known vulnerabilities:
  - `flask==3.1.1` → `CVE-2026-27205` (fix: `3.1.3`)
  - `Werkzeug==3.1.5` → `CVE-2026-27199` (fix: `3.1.6`)
  - `diskcache==5.6.3` → `CVE-2025-69872` (no fix version listed in output)

- Command: `./.audit_venv/bin/pip-audit -r requirements.txt`
 - Result: dependency resolution failed on `jaxlib==0.4.38` for current Python/platform, so full-root CVE coverage was incomplete from this run.

- Command: `./.audit_venv/bin/bandit -r cloud/inference_api -x cloud/inference_api/test_security_suite.py -ll -f txt`
 - Result: medium findings flagged by tool include:
  - Bind-all-interfaces in `main.py` local dev run path.
  - False-positive style hit in IP validation branch comparing against `0.0.0.0`.

---

## 4) Findings (Severity-Ordered)

## P0 (Release Blocking)

### F-001: Latest Android source fails compilation
- Files:
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:146`
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:149`
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:150`
- Problem:
 - Variables `sourceLang` and `targetLang` are referenced but not defined in current function scope (existing defined names are `sourceLangLabel`, `targetLangLabel`).
- Impact:
 - No fresh APK/AAB can be built from HEAD.
 - Reviewer-path execution on latest source is blocked.
 - Connected Android tests are blocked.

## P1 (High)

### F-002: Translation fallback path is emulator-specific and includes hardcoded dev secret
- Files:
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:222`
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:227`
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:230`
- Problem:
 - Uses `http://10.0.2.2:5000/translate` + fixed `X-API-Key: dev-test-key`.
 - This path is not portable to production devices and is cleartext HTTP.
- Impact:
 - Unsupported-language output translation behavior can diverge between emulator and real devices.
 - Security posture is weaker than expected for production pathing.

### F-003: Documentation/implementation drift around cloud translation fallback
- Files:
 - `/kaggle_submission_appendix.md:608`
 - `/kaggle_submission_writeup.md:48`
 - `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:142`
- Problem:
 - Submission docs state cloud fallback removed and unsupported languages pass through unchanged.
 - Runtime translator still attempts cloud translation in unsupported-language path.
- Impact:
 - Claim risk in reviewer evaluation.
 - Confusion during acceptance testing.

### F-004: Known CVEs present in cloud requirements
- File:
 - `/cloud/inference_api/requirements.txt`
- Problem:
 - `flask` and `Werkzeug` have patched versions available but pinned to vulnerable versions.
 - `diskcache` CVE remains unresolved in output.
- Impact:
 - Security/compliance risk.
 - Preventable findings in external security review.

### F-005: Real backend model path not fully proven in current environment
- Files:
 - `/cloud/inference_api/main.py:318`
 - local test outputs (`pytest -rs`) show skip due missing `llama_cpp`.
- Problem:
 - Unmocked cloud inference integration is not executed when native runtime is unavailable.
- Impact:
 - End-to-end backend model confidence is partial.

## P2 (Medium)

### F-006: Cloud model integrity verification not enforced after download
- Files:
 - `/cloud/inference_api/config.py:26`
 - `/cloud/inference_api/main.py:327`
 - `/cloud/inference_api/main.py:356`
- Problem:
 - Model SHA field defaults to `None`; no mandatory post-download hash verification gate in load path.
- Impact:
 - Supply-chain and artifact-integrity hardening gap.

### F-007: CI security gate allows degraded pass conditions
- File:
 - `/.github/workflows/ci.yml:154`
- Problem:
 - `pip-audit` step currently has fallback behavior that can still pass overall despite failures.
- Impact:
 - Vulnerabilities can persist unnoticed as “green CI”.

### F-008: Physical sensor validation evidence remains emulator-centric
- Files:
 - `/docs/physical_sensor_evidence_pack.md:4`
 - `/docs/ManualValidationChecklist.md:3`
 - `/mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:17`
 - `/mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:29`
- Problem:
 - Tests are valuable for integration sanity but rely heavily on synthetic inputs/emulator execution.
- Impact:
 - Cannot confidently claim “every sensor measurement works exactly as intended” in field conditions.

## P3 (Low/Optimization)

### F-009: Translation of assessment text is sequential and may add latency
- Files:
 - `/mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:732`
 - `/mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:736`
 - `/mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:738`
- Problem:
 - Concern/recommendation/disclaimer translations are executed line-by-line, potentially incurring additive delay.
- Impact:
 - Slower triage UX under non-English paths, especially if fallback translation path is networked.

### F-010: Root dependency CVE scan completeness limited by platform resolver mismatch
- File:
 - `/requirements.txt:9`
- Problem:
 - `jaxlib==0.4.38` could not resolve under current local Python/platform during `pip-audit`.
- Impact:
 - CVE visibility for full root requirements is incomplete from this run context.

---

## 5) Recommendations (Prioritized)

## 5.1 Immediate (Blocker Closure)

1. Fix `NkuTranslator.kt` unresolved references and restore green `:app:compileDebugKotlin`.
2. Re-run full Android build/test chain on latest source:
  - `testDebugUnitTest`
  - `connectedDebugAndroidTest`
  - `assembleDebug` / `bundleRelease`
3. Ensure a fresh APK built from current HEAD is what reviewer-path runtime validation uses.

## 5.2 Security Hardening

1. Remove emulator-specific hardcoded translation endpoint/key from production pathing.
2. Align translation architecture:
  - either strict offline pass-through for unsupported languages, or
  - environment-driven secure cloud fallback with TLS + managed secrets.
3. Upgrade `flask` and `Werkzeug` to patched versions.
4. Add mandatory cloud model hash pinning and verification before model load.
5. Tighten CI so audit/security failures block merges (remove permissive fallback behavior).

## 5.3 Sensor and Model Verification

1. Execute manual physical-device validation matrix in `/docs/ManualValidationChecklist.md`.
2. Capture reproducible evidence (logs/screenshots/videos) for:
  - camera under varied lighting,
  - microphone under varied noise,
  - full triage flow on real device.
3. Add at least one CI lane or scheduled job where real llama-cpp integration tests run in an environment that supports them.

## 5.4 Performance/Efficiency

1. Profile end-to-end triage latency by language and path (supported ML Kit vs unsupported fallback).
2. Consider batched/parallelized translation of concern/recommendation/disclaimer output.
3. Continue collecting cold-start and model-load metrics from real target devices, not emulator only.

---

## 6) Reviewer Risk Forecast (MedGemma Challenge Style)

Likely reviewer-visible issues if shipped as-is:
- Build failure from latest source if reviewers attempt fresh compile.
- Inconsistent behavior for unsupported languages due emulator-specific cloud fallback code.
- Security scanners flagging known backend dependency CVEs.
- Difficulty substantiating “fully verified sensor behavior” without physical-device evidence artifacts.

---

## 7) Release Decision

Current recommendation: **NO-GO** for “fully verified latest build” claim until P0/P1 items are closed.

Minimum go-live gates:
1. Android latest source compiles and runs instrumented tests on latest build artifacts.
2. Translation architecture and docs are consistent and secure.
3. Cloud dependency CVEs addressed (or explicitly risk-accepted with mitigation and sign-off).
4. Physical-device sensor validation evidence added.

---

## 8) Audited File Index (Key Areas)

Android core:
- `/mobile/android/app/src/main/java/com/nku/app/MainActivity.kt`
- `/mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`
- `/mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt`
- `/mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt`
- `/mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt`
- `/mobile/android/app/src/main/java/com/nku/app/SensorFusion.kt`
- `/mobile/android/app/src/main/java/com/nku/app/PulseOximeter.kt`
- `/mobile/android/app/src/main/java/com/nku/app/RespiratoryDetector.kt`
- `/mobile/android/app/src/main/java/com/nku/app/ModelFileValidator.kt`
- `/mobile/android/app/src/main/java/com/nku/app/ThermalManager.kt`
- `/mobile/android/app/src/main/java/com/nku/app/utils/MemoryManager.kt`

Android tests:
- `/mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt`
- `/mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt`
- `/mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt`
- `/mobile/android/app/src/androidTest/java/com/nku/app/RealMedGemmaLiveTest.kt`

Backend and security:
- `/cloud/inference_api/main.py`
- `/cloud/inference_api/security.py`
- `/cloud/inference_api/config.py`
- `/cloud/inference_api/requirements.txt`
- `/cloud/inference_api/test_security_suite.py`

CI and dependencies:
- `/.github/workflows/ci.yml`
- `/requirements.txt`

Submission/docs reviewed:
- `/kaggle_submission_writeup.md`
- `/kaggle_submission_appendix.md`
- `/REVIEWER_TEST_SCENARIOS.md`
- `/docs/ManualValidationChecklist.md`
- `/docs/physical_sensor_evidence_pack.md`

---

## 9) Change Log

- Created this handoff report only.
- No code changes performed.

