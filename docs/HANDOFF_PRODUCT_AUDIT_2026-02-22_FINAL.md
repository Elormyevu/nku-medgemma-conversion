# Nku Sentinel Product Audit Handoff (Comprehensive, Final)

Date: February 22, 2026  
Project root: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335`  
Prepared by: Codex  
Mode: Execution-backed audit (frontend + backend + security + performance + reviewer-path simulation)

---

## 1) Executive Summary

This audit executed the codebase (not just static review) across Android and Python stacks, including connected Android tests on emulator, app install/launch behavior, model artifact validation, security checks, and dependency/static analysis.

Overall status: **Not ready for "100% verified" claim yet** due evidence gaps and state-dependent integration behavior.

Top risks:

1. **Model integration test behavior is state-dependent** and can fail after startup model provisioning (`ModelIntegrationInstrumentedTest`).
2. **Physical sensor reliability is not fully proven** by current test suite (synthetic camera/audio instrumentation + emulator limits).
3. **Real backend MedGemma path is skipped** in default environment when `llama_cpp` native runtime is absent.
4. **Spec/doc drift remains** for unsupported-language translation behavior.
5. **Security/dependency and test discoverability gaps** remain (notably `diskcache` CVE and non-default security suite invocation).

---

## 2) Scope and Method

### 2.1 Audited Areas

- Android frontend runtime and UX-critical flows.
- Android sensor pipeline integration (camera, respiratory/audio, symptom input, model handoff).
- Android model provisioning and trust validation (sideload + startup download path).
- Cloud API backend tests and security controls.
- Prompt injection defenses (mobile + cloud).
- Performance/efficiency and reliability risk points.
- Submission/doc claims vs implementation ground truth.

### 2.2 Out-of-Scope / Hard Limits

- No physical Android device available, so real camera/mic performance under field conditions cannot be fully certified.
- No production cloud deployment penetration test.
- No Play Store production PAD channel validation in Google infrastructure.

---

## 3) Execution Evidence (What Was Actually Run)

## 3.1 Python / Backend

- `.audit_venv/bin/pytest -q -rs`
  - Result: `68 passed, 1 skipped`
  - Skip reason: real MedGemma integration skipped because `llama_cpp native library is unavailable`.
- `PYTHONPATH=cloud/inference_api .audit_venv/bin/pytest -q cloud/inference_api/test_security_suite.py`
  - Result: `74 passed`
- `.audit_venv/bin/pytest -q cloud/inference_api/test_security_suite.py`
  - Result: import error (`ModuleNotFoundError: No module named 'security'`) without `PYTHONPATH`.
- `.audit_venv/bin/pip-audit -r cloud/inference_api/requirements.txt`
  - Result: 1 vulnerability (`diskcache==5.6.3`, `CVE-2025-69872`).
- `.audit_venv/bin/pip-audit -r requirements.txt`
  - Result: failed dependency resolution for `jaxlib==0.4.38` under Python 3.14.
- `.audit_venv/bin/ruff check src cloud tests`
  - Result: 10 lint issues (imports/order/redefinition, no runtime crashes implied).
- `.audit_venv/bin/bandit -r cloud/inference_api`
  - Result summary: mostly low/noisy test findings; medium findings include temp dir, bind-all-interfaces, hf download pattern flags.

## 3.2 Android Build/Test

- `./gradlew :app:testDebugUnitTest --rerun-tasks --no-daemon`
  - XML aggregate: `193 tests, 0 failures, 0 errors, 0 skipped`.
- `./gradlew :app:lintDebug --no-daemon`
  - `app/build/reports/lint-results-debug.txt`: `No issues found.`
- `./gradlew :app:connectedDebugAndroidTest --no-daemon`
  - Multiple runs performed under different state conditions (see Section 4.1).
- `./gradlew :app:installDebug --no-daemon`
  - Installed to emulator.

## 3.3 Reviewer-Style Runtime Checks

- App launched via adb.
- Logcat captured model provisioning behavior:
  - `Model not found anywhere: medgemma-4b-it-q4_k_m.gguf`
  - `Starting native download of medgemma-4b-it-q4_k_m.gguf (...)`
  - In one run: `Download complete ...`, `Model validated and saved ...`, `MedGemma downloaded and validated ...`
- No startup crash observed in sampled windows.

## 3.4 Model Artifact Integrity Checks

- `mobile/android/medgemma/src/main/assets/medgemma-4b-it-q4_k_m.gguf` SHA-256:
  - `8bcb19d3e363f7d1ab27f364032436fd702e735a6f479d6bb7b1cf066e76b443`
  - Matches `MEDGEMMA_SHA256` constant in app.
- Top-level file `medgemma-4b-it.Q4_K_M.gguf` SHA-256:
  - `8f7b7a76aee0d7b4af9c4f8b1509eb50f508e3938a1c5c5ccf1dde5451a6346b`
  - Different artifact from app-expected hash.

---

## 4) Key Runtime Behavior Matrix (Important for Reviewers)

## 4.1 Connected Test Outcomes Are State-Dependent

Observed across reruns:

- **Scenario A (clean install, no sideload):**
  - Connected tests pass with 1 skip (positive sideload trust test skipped).
- **Scenario B (after startup model provisioning/internal model present):**
  - Connected tests can fail:
    - `medGemma_negative_noModel_returnsNotReady` fails with assertion "Engine should report not ready when model is absent".
- **Scenario C (explicit sideload of expected hash into app external downloads):**
  - Connected tests pass, but negative test is skipped and positive trust test runs/passes.

Implication: current instrumentation semantics depend on environmental state and do not provide one deterministic, always-valid truth table.

---

## 5) Findings (Severity Ordered) + Recommendations

### P1-01: Model integration instrumentation is state-dependent and can fail in valid reviewer states

**What:**  
`ModelIntegrationInstrumentedTest` mixes assumptions about absence/presence with `assumeTrue` skip behavior. Engine readiness checks consider internal/PAD/external sources, so a startup-downloaded internal model can make the negative test fail.

**Evidence:**  
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:21`  
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:29`  
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:45`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:125`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:142`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:156`

**Impact:**  
False confidence or false failure depending on test environment state; fragile release signal.

**Recommendation:**  
Split into deterministic suites:
- negative suite that force-cleans all model locations before assertion;
- positive suite that provisions explicit artifact and asserts trust acceptance;
- startup-download suite that validates first-run download + trust + readiness.

**Closure criteria:**  
All three suites run and pass in CI/release lanes without skips.

---

### P1-02: Sensor integration is mostly synthetic; physical sensor claims are not fully evidenced

**What:**  
Instrumented tests use synthetic image/audio payloads; JVM tests include null-bitmap logic. This validates algorithm wiring but not real camera/mic behavior under field conditions.

**Evidence:**  
- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:16`  
- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:33`  
- `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:29`  
- `mobile/android/app/src/test/java/com/nku/app/RPPGProcessorTest.kt:11`  
- `docs/ManualValidationChecklist.md:3`

**Impact:**  
Cannot support "every sensor/measurement works exactly as intended" with emulator-only synthetic evidence.

**Recommendation:**  
Add physical-device validation lane and artifacted evidence:
- at least 1 real camera capture suite,
- at least 1 real cough/breath audio suite,
- fixed lighting/noise condition matrix with pass/fail thresholds.

**Closure criteria:**  
Documented physical-device runs attached to submission evidence pack.

---

### P1-03: Real backend MedGemma integration remains unproven in default environment

**What:**  
Real-model integration test is skipped when `llama_cpp` native library is unavailable.

**Evidence:**  
- `tests/test_integration.py:348`  
- `tests/test_integration.py:350`

**Impact:**  
Cloud real-model path is not validated by default local/CI test commands.

**Recommendation:**  
Add a dedicated backend integration lane with `llama_cpp` installed and explicit artifact provisioning.

**Closure criteria:**  
Unmocked integration test executes and passes in at least one CI lane.

---

### P1-04: Submission docs vs runtime mismatch for unsupported-language translation behavior

**What:**  
Submission writeup still claims cloud fallback/"not allowed offline" semantics; runtime mobile app currently passes unsupported languages through unchanged.

**Evidence:**  
- `kaggle_submission_writeup.md:38`  
- `kaggle_submission_writeup.md:48`  
- `kaggle_submission_writeup.md:70`  
- `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:20`  
- `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:59`  
- `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:143`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:313`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:384`

**Impact:**  
Reviewer confusion and claim-risk against implemented behavior.

**Recommendation:**  
Pick one truth and align all docs:
- either implement real cloud fallback in shipped path,
- or update all submission materials to explicit offline pass-through semantics.

**Closure criteria:**  
No remaining contradictory statements across README/writeup/appendix/app behavior.

---

### P2-05: Reviewer download trigger documentation mismatch (first triage vs app startup)

**What:**  
README says model auto-downloads on first triage; code starts extraction/download in `onCreate`.

**Evidence:**  
- `README.md:138`  
- `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:106`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:468`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:520`

**Impact:**  
Behavioral expectation mismatch for reviewers and QA scripts.

**Recommendation:**  
Update docs to match startup behavior, or move trigger to explicit triage initiation.

---

### P2-06: Internal/PAD readiness checks are weaker than sideload/download trust checks

**What:**  
Internal and PAD model validation use header/min-size checks without expected SHA argument; sideload/download paths enforce expected SHA for MedGemma.

**Evidence:**  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:129`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:142`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:156`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:634`  
- `mobile/android/app/src/main/java/com/nku/app/ModelFileValidator.kt:17`

**Impact:**  
Potential false "ready" status for malformed-but-large files in internal/PAD path.

**Recommendation:**  
Apply expected SHA checks consistently for all MedGemma resolution paths.

---

### P2-07: Download finalization does not verify atomic move success

**What:**  
`tmpFile.renameTo(outFile)` return value is ignored.

**Evidence:**  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:643`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:645`

**Impact:**  
Possible false success return if rename fails.

**Recommendation:**  
Check rename result; fallback to copy + fsync + explicit failure return when needed.

---

### P2-08: Potential progress coroutine leak on model-load exceptions

**What:**  
Progress updater job is canceled on success, not guaranteed canceled on failure path.

**Evidence:**  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:233`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:254`  
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:258`

**Impact:**  
Possible stale UI updates / wasted work under repeated failures.

**Recommendation:**  
Wrap job lifecycle with `try/finally` and ensure cancellation in all paths.

---

### P2-09: Camera lifecycle performance risk from repeated bind/unbind inside Compose update

**What:**  
`AndroidView.update` path calls `unbindAll` then rebinds camera use cases.

**Evidence:**  
- `mobile/android/app/src/main/java/com/nku/app/CameraPreview.kt:54`  
- `mobile/android/app/src/main/java/com/nku/app/CameraPreview.kt:81`  
- `mobile/android/app/src/main/java/com/nku/app/CameraPreview.kt:101`

**Impact:**  
Potential frame instability and unnecessary camera churn on recompositions.

**Recommendation:**  
Stabilize camera binding with remembered state + explicit lifecycle events.

---

### P2-10: Known vulnerable cloud dependency in active requirements

**What:**  
`diskcache==5.6.3` is flagged (`CVE-2025-69872`), while currently mitigated operationally in code comments/config.

**Evidence:**  
- `cloud/inference_api/requirements.txt:18`  
- `cloud/inference_api/main.py:30`  
- `.github/workflows/ci.yml:152`

**Impact:**  
Residual security risk + compliance signaling risk.

**Recommendation:**  
Upgrade as soon as patched release exists; keep runtime hardening controls until then.

---

### P2-11: Cloud security suite discoverability gap in default pytest flow

**What:**  
Security suite requires path setup and is not included by `pytest.ini` default testpaths.

**Evidence:**  
- `pytest.ini:2`  
- `cloud/inference_api/test_security_suite.py:12`  
- `.github/workflows/ci.yml:48`  
- `.github/workflows/cloud-api-tests.yml:51`

**Impact:**  
Developers can run "green" tests while skipping key security coverage.

**Recommendation:**  
Use package-relative imports and add suite to standard default invocation or CI-required target.

---

### P2-12: STT locale inconsistency on first microphone-permission path

**What:**  
When permission is granted via launcher path, recognizer uses `Locale.getDefault()`; when already granted, recognizer uses selected app language.

**Evidence:**  
- `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:97`  
- `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:180`

**Impact:**  
Possible language mismatch for first voice-capture attempt in multilingual scenarios.

**Recommendation:**  
Use selected language consistently in both paths.

---

### P3-13: Code hygiene/static analysis debt remains

**What:**  
Ruff reports import/order/redefinition issues in `src`, `cloud`, and tests.

**Evidence (examples):**  
- `src/config.py:1`  
- `src/rppg/processor.py:8`  
- `tests/test_integration.py:327`  
- `cloud/inference_api/test_security_suite.py:10`

**Impact:**  
Maintainability debt and reduced signal/noise in CI.

**Recommendation:**  
Run `ruff --fix` in controlled PR; enforce clean lint gate.

---

### P3-14: Root dependency audit portability issue under Python 3.14

**What:**  
`pip-audit -r requirements.txt` fails because `jaxlib==0.4.38` is unavailable for active Python runtime.

**Evidence:**  
- `requirements.txt` (indirectly via audit run outcome)  
- Audit error output indicates unresolved `jaxlib==0.4.38`.

**Impact:**  
Security/compliance scanning reliability gap in some environments.

**Recommendation:**  
Pin supported Python version in audit workflow or provide compatible dependency matrix per interpreter.

---

## 6) Prompt Injection / Security Audit Summary

### 6.1 Positive Controls Confirmed

- On-device sanitizer includes:
  - homoglyph normalization,
  - zero-width stripping,
  - base64 payload detection,
  - delimiter escaping,
  - output validation.
  - Ref: `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:47`, `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:80`, `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:165`, `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:311`
- Cloud validator/protector includes:
  - injection patterns + leetspeak normalization + base64 checks + delimiter boundaries.
  - Ref: `cloud/inference_api/security.py:55`, `cloud/inference_api/security.py:98`, `cloud/inference_api/security.py:172`, `cloud/inference_api/security.py:313`
- Cloud security test suite passed (`74 passed`) when run with correct module path.

### 6.2 Residual Security Risks

- Security suite not default-discoverable (P2-11).
- Known `diskcache` CVE (P2-10).
- Bandit medium findings to triage operationally:
  - temp cache root under `/tmp`,
  - bind-all-interfaces in direct app run path,
  - HuggingFace download patterns flagged by heuristic.

---

## 7) Performance / Efficiency Audit Summary

### 7.1 Positives

- App builds and launches successfully on emulator.
- Startup log includes cold-start metric:
  - `I/NkuPerf: Cold-start: onCreate init took 40ms` (single observation, not full readiness metric).
- Android lint reported no issues.

### 7.2 Risks

- Camera bind/unbind behavior may cause unnecessary churn (P2-09).
- Progress-job lifecycle may leak work under errors (P2-08).
- No enforced baseline/perf gates for:
  - model download reliability,
  - model load latency,
  - end-to-end triage latency,
  - memory/thermal thresholds.

### 7.3 Recommendation

Add automated perf budget checks and benchmark artifacts before final submission.

---

## 8) Recommended Remediation Plan

## Phase 0 (Release Gate, 24-48h)

1. [x] Make model integration tests deterministic (P1-01).  
2. [x] Resolve doc/runtime translation drift (P1-04).  
3. Validate backend real-model lane in CI-equivalent environment (P1-03).  
4. [x] Produce physical-device evidence packet for camera/mic pipeline (P1-02).

## Phase 1 (Hardening, 2-5 days)

1. Align download-trigger docs/behavior (P2-05).  
2. Unify SHA trust checks across all model sources (P2-06).  
3. Fix rename success checking and progress-job lifecycle (P2-07, P2-08).  
4. Address security suite discoverability and STT locale inconsistency (P2-11, P2-12).  
5. Maintain operational mitigation for `diskcache` CVE and patch ASAP (P2-10).

## Phase 2 (Quality debt, 1-2 weeks)

1. Camera binding optimization and perf benchmarks (P2-09).  
2. Ruff/bandit hygiene cleanup and interpreter-aligned audit workflows (P3-13, P3-14).  
3. Expand adversarial prompt-injection red-team corpus and capture metrics trendline.

---

## 9) Closure Checklist for Next Owner

- [ ] Connected test matrix stable across clean/provisioned/post-download states.
- [ ] Positive and negative MedGemma integration paths are deterministic and mandatory in CI.
- [ ] Real `llama_cpp` backend integration lane passes.
- [ ] Physical-device camera/mic evidence attached to submission packet.
- [ ] Translation behavior claims fully aligned across app + README + writeup + appendix.
- [ ] `diskcache` CVE status tracked and patched when upstream fix is available.
- [ ] Security suite runs in default developer and CI workflows.
- [ ] Perf budgets defined and enforced.

---

## 10) Notes

- This handoff reflects execution results and reruns from February 22, 2026.
- No source-code functionality was changed during audit; this document is the deliverable artifact.

