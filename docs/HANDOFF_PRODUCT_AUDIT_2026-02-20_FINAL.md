# Nku Comprehensive Product Audit Handoff

Date: 2026-02-20
Auditor: Codex (GPT-5)
Repository: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335`
Branch: `main`
Audited commit: `442270e`
Audit mode: Read-only audit of code and runtime behavior (no product code changes)

## 1) Executive Summary

Overall assessment: **conditionally stable, not submission-ready without targeted fixes**.

What is strong:
- Build and static quality gates are healthy (assemble/lint pass).
- Automated backend and Android unit/instrumentation tests are mostly green.
- Prompt-injection protections are implemented on both mobile and cloud paths.
- Core sensor-to-prompt architecture is present and internally coherent.

What blocks high-confidence handoff to reviewers:
- Multiple **documentation-vs-runtime mismatches** in critical behavior claims.
- Full reviewer-path MedGemma runtime proof is incomplete (skipped model integration gate; later install failure due storage constraints).
- Dependency security risk remains (`diskcache` CVE).
- Several safety/UX assertions in docs are not fully enforced by current execution paths.

## 2) Audit Scope

Requested coverage included:
- Frontend audit
- Backend audit
- Runtime execution and verification against product documents/specs
- Performance/efficiency audit
- Security audit (including prompt injection)
- Sensor integration and model integration audit
- Reviewer-experience simulation (MedGemma challenge-style run path)

## 3) What Was Executed

### 3.1 Backend and Python
- Command: `./.audit_venv/bin/pytest -q`
- Result: `68 passed, 1 skipped in 2.66s`

- Command: `PYTHONPATH=. ../../.audit_venv/bin/pytest -q security_pytest_suite.py` (in `cloud/inference_api`)
- Result: `74 passed in 0.12s`

- Command: `./.audit_venv/bin/pytest --cov=cloud/inference_api --cov-report=term-missing -q`
- Result:
  - `68 passed, 1 skipped`
  - Coverage highlights:
    - `cloud/inference_api/main.py`: 67%
    - `cloud/inference_api/security.py`: 67%

### 3.2 Android Build/Test/Lint
- Command: `./gradlew :app:assembleDebug :app:assembleRelease --rerun-tasks --no-daemon`
- Result: BUILD SUCCESSFUL

- Command: `./gradlew :app:testDebugUnitTest --rerun-tasks --no-daemon`
- Result: BUILD SUCCESSFUL
- XML aggregate (`mobile/android/app/build/test-results/testDebugUnitTest`):
  - `tests=193 failures=0 errors=0 skipped=0`

- Command: `./gradlew :app:lintDebug :app:lintRelease --rerun-tasks --no-daemon`
- Result: BUILD SUCCESSFUL
- Reports:
  - `mobile/android/app/build/reports/lint-results-debug.txt`: `No issues found.`
  - `mobile/android/app/build/reports/lint-results-release.txt`: `No issues found.`

- Command (initial connected run): `./gradlew :app:connectedDebugAndroidTest --no-daemon`
- Result artifact:
  - `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml`
  - `tests=14 failures=0 errors=0 skipped=1`
  - Skipped test: `medGemma_sideloadedModel_isDiscoverableAndTrusted`

- Command (later rerun): `./gradlew :app:connectedDebugAndroidTest --no-daemon`
- Result: FAILED before test execution with `INSTALL_FAILED_INSUFFICIENT_STORAGE` while installing `app-debug.apk`.

### 3.3 Security Tooling
- Command: `./.audit_venv/bin/bandit -r cloud/inference_api src -x tests,cloud/inference_api/security_pytest_suite.py -f txt`
- Result summary:
  - High: 0
  - Medium: 6
  - Low: 3

- Command: `./.audit_venv/bin/pip-audit -r cloud/inference_api/requirements.txt`
- Result: 1 known vulnerability
  - `diskcache 5.6.3` -> `CVE-2025-69872`

- Command: `./.audit_venv/bin/pip-audit -r requirements.txt`
- Result: scan could not complete in this environment due resolver failure for `jaxlib==0.4.38` under Python 3.14.

## 4) Findings and Recommendations (Severity-Ranked)

## [P1] F-01: Submission docs claim unsupported-language cloud translation fallback, but mobile runtime currently passes unsupported language text through untranslated

Evidence:
- Docs claim fallback: `kaggle_submission_writeup.md:36`, `kaggle_submission_writeup.md:46`
- Runtime behavior: `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:313`, `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:319`
- Translator behavior: `mobile/android/app/src/main/java/com/nku/app/NkuTranslator.kt:20`

Impact:
- High reviewer risk from claim-vs-code mismatch.
- Clinical quality risk for unsupported languages.

Recommendation:
- Choose one and align all docs/code immediately:
  - Implement real cloud fallback translation path; or
  - Update all submission docs to explicit pass-through behavior.
- Add an integration test proving the chosen behavior for one unsupported language online and offline.

## [P1] F-02: “All sensors <75% and no symptoms -> no MedGemma call” claim is not fully enforced in current triage UI execution path

Evidence:
- Claim: `kaggle_submission_appendix.md:930`, `kaggle_submission_appendix.md:953`
- MedGemma path in UI: `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:371`
- Triage button enable logic: `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:254`
- Abstention exists only in rule-based function: `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:347`

Impact:
- Safety-policy drift from submission claim.
- Potential unnecessary LLM invocation under low-confidence/no-symptom conditions.

Recommendation:
- Enforce abstention gate before `runMedGemmaOnly()` call in primary triage path.
- Add an explicit instrumentation test for this gate.

## [P1] F-03: Thermal fallback claim is only partially enforced in the main triage flow

Evidence:
- Claim: `kaggle_submission_appendix.md:956`
- Thermal manager exists: `mobile/android/app/src/main/java/com/nku/app/ThermalManager.kt:25`
- Main triage path calls MedGemma without thermal parameter/guard: `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:371`, `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:418`

Impact:
- Reviewer may observe behavior that conflicts with safety narrative.

Recommendation:
- Gate MedGemma launch in triage with thermal checks consistently.
- Add test case for `>42°C` route to deterministic fallback and transparency banner.

## [P1] F-04: Reviewer install path is brittle; connected test rerun failed with insufficient storage

Evidence:
- Failure: `INSTALL_FAILED_INSUFFICIENT_STORAGE` during `connectedDebugAndroidTest`
- Artifact sizes:
  - `mobile/android/app/build/outputs/apk/debug/app-debug.apk` (~1.2G)
  - `mobile/android/app/build/outputs/apk/release/app-release-unsigned.apk` (~1.2G)
  - `mobile/android/app/build/outputs/bundle/release/app-release.aab` (~2.6G)

Impact:
- Reviewers can fail before feature evaluation.

Recommendation:
- Provide reviewer build profile with smaller install target (arm64-only APK for testing devices/emulator profile guidance).
- Ship explicit reviewer setup instructions including storage prerequisites.

## [P1] F-05: Known backend dependency vulnerability remains (`diskcache`)

Evidence:
- Pin: `cloud/inference_api/requirements.txt:18`
- Mitigation context exists in code: `cloud/inference_api/main.py:30`
- Tool finding: `CVE-2025-69872`

Impact:
- Security posture risk remains open.

Recommendation:
- Upgrade when patched version is available.
- Until then, document risk acceptance and enforce runtime mitigation checks in deployment validation.

## [P2] F-06: Full on-device MedGemma model integration remains unproven in this audit environment

Evidence:
- Skip gate in instrumented test: `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:15`, `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:28`
- Skipped in run artifact: `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_pixel7(AVD) - 15-_app-.xml:20`

Impact:
- Cannot claim complete reviewer-equivalent model runtime verification from this run alone.

Recommendation:
- Add mandatory pre-submission hardware test lane with trusted sideloaded GGUF present and recorded pass artifact.

## [P2] F-07: Sensor integration tests are runtime-valid but synthetic; physical sensor fidelity is still unverified

Evidence:
- Synthetic camera bitmap test: `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:17`, `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:33`
- Synthetic audio signal test: `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:28`

Impact:
- Real-world lighting/noise/hardware variability not fully covered.

Recommendation:
- Add physical-device acceptance protocol with real capture fixtures and fixed pass criteria.

## [P2] F-08: Backend payload-size defense can be bypassed when `Content-Length` is absent

Evidence:
- Guard checks `Content-Length` header path: `cloud/inference_api/security.py:671`

Impact:
- Potential memory pressure / oversized JSON handling risk.

Recommendation:
- Enforce request size limits at WSGI/server boundary and verify actual body length after parse.

## [P2] F-09: Potential PHI leakage in injection-detection log lines

Evidence:
- Logs include sanitized input snippets: `cloud/inference_api/security.py:159`, `cloud/inference_api/security.py:164`

Impact:
- Sensitive symptom text may enter logs.

Recommendation:
- Redact user medical content in warning logs and keep only metadata/hashes.

## [P2] F-10: Symptoms-only triage appears blocked by current run-button gating

Evidence:
- `hasAnyData` uses sensor-derived values only: `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:60`
- Run button enabled only when `hasAnyData`: `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:254`

Impact:
- User may be unable to run triage with symptom text alone.

Recommendation:
- Include symptom presence in run-enable logic or provide explicit “symptoms-only triage” path.

## [P2] F-11: Network behavior and permissions commentary drift in app manifests/docs

Evidence:
- Source manifest comment says no app network usage: `mobile/android/app/src/main/AndroidManifest.xml:11`
- Runtime includes network download fallback: `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:505`
- Merged debug manifest includes `INTERNET`: `mobile/android/app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:47`

Impact:
- Reviewer confusion and trust erosion from conflicting statements.

Recommendation:
- Harmonize comments/docs with actual runtime behavior and merged-manifest reality.

## [P3] F-12: Backend high-risk paths are still partially untested

Evidence:
- Coverage on `main.py` and `security.py` is 67% each.
- Many integration tests mock model loading/inference: `tests/test_integration.py:45`, `tests/test_integration.py:71`, `tests/test_integration.py:117`
- Real-model integration test is skippable: `tests/test_integration.py:343`, `tests/test_integration.py:350`

Impact:
- Regression risk remains in unexecuted branches.

Recommendation:
- Increase branch coverage for auth/rate-limit/error paths and require at least one non-mocked model test in release CI.

## 5) Prompt Injection and Security Posture Assessment

Status: **good foundational controls, but not yet complete hardening**.

Implemented controls confirmed:
- Mobile sanitization and output validation:
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:47`
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:311`
- Cloud validator/protector/rate limit:
  - `cloud/inference_api/security.py:155`
  - `cloud/inference_api/security.py:304`
  - `cloud/inference_api/security.py:410`
- Endpoint auth + security headers:
  - `cloud/inference_api/main.py:128`
  - `cloud/inference_api/main.py:116`

Residual concerns:
- PHI logging risk (F-09).
- Request-size handling nuance (F-08).
- Dependency CVE remains (F-05).

## 6) Performance and Efficiency Audit Summary

Confirmed positives:
- rPPG performance optimizations in place (buffer/deque, throttled DFT):
  - `mobile/android/app/src/main/java/com/nku/app/RPPGProcessor.kt:52`, `mobile/android/app/src/main/java/com/nku/app/RPPGProcessor.kt:96`
- Thermal read caching exists:
  - `mobile/android/app/src/main/java/com/nku/app/ThermalManager.kt:34`
- Memory-aware MedGemma mmap loading/retry path exists:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:231`

Efficiency risks observed:
- Very large install artifacts can block reviewer runtime validation (F-04).
- Some safety gates are documented but not consistently applied in top-level execution (F-02, F-03).

## 7) Frontend Audit Summary

Passes:
- Compose UI smoke tests and localization checks passed in instrumentation artifact.
- Lint found no issues.

Risks:
- Symptoms-only triage UX gate (F-10).
- Reviewer install failure path (F-04).

## 8) Backend Audit Summary

Passes:
- Core API tests and dedicated security test suite passed.
- Security architecture is materially stronger than baseline.

Risks:
- Open CVE in pinned deps (F-05).
- Partial coverage in critical modules (F-12).
- Content-length-only size guard (F-08).

## 9) Sensor + Model Integration Verdict

Current status:
- **Partially verified**.

Verified in this audit:
- Synthetic sensor pipelines execute on Android runtime.
- Sensor fusion to prompt generation flow executes.
- Non-model portions are stable in automated runs.

Not fully verified in this audit environment:
- Real sideloaded MedGemma model discovery/trust/inference path on reviewer-equivalent hardware setup.
- Physical camera/microphone variability under field-like conditions.

## 10) Prioritized Recommendation Plan

### Immediate (before submission/reviewer handoff)
1. Resolve claim-vs-runtime mismatches (F-01, F-02, F-03, F-11).
2. Address reviewer install reliability (F-04).
3. Resolve or formally mitigate/accept CVE with signed risk note (F-05).

### Near-term (high confidence hardening)
1. Add mandatory non-skippable model integration lane with preloaded trusted GGUF (F-06).
2. Add physical sensor validation checklist and evidence capture (F-07).
3. Fix symptoms-only triage gating (F-10).

### Hardening backlog
1. Improve payload-size enforcement and PHI-safe logging (F-08, F-09).
2. Raise backend branch coverage in `main.py` and `security.py` and reduce mock-only reliance (F-12).

## 11) Release Readiness Conclusion

Conclusion: **Not yet ready for “works exactly as claimed” assertion across full reviewer flow**.

Reason:
- The codebase is generally healthy and many controls are present.
- However, key behavioral claims in submission docs are not fully aligned with currently executed runtime behavior.
- Full reviewer-path MedGemma runtime validation remains incomplete in this environment.

## 12) Audit Integrity Notes

- No source code changes were made as part of this audit.
- This file is the only artifact produced in response to the handoff-document request.
- All findings are grounded in inspected files and command outputs captured during this session.

