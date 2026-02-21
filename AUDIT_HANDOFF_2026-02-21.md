# Nku Sentinel Audit Handoff

Date: 2026-02-21  
Auditor: Codex (GPT-5)  
Repo: `nku-impact-challenge-1335`  
Branch state during audit: `main...origin/main` with pre-existing doc-only local changes

## 1) Executive Summary

This audit covered frontend, backend, model/sensor integration, security (including prompt injection), performance/efficiency, build/release artifacts, and claim-vs-code consistency against submission docs.

Key result:
- The codebase is broadly structured and test-rich, with strong guardrails in many areas.
- Critical claim-path and pipeline correctness issues remain, including one P0 integration flaw in the primary sensor-to-LLM path.
- Unit and backend security suites pass, but physical-device validation could not be completed in this environment due no connected Android device/emulator.

Highest-priority blockers before reviewer-facing validation:
1. P0 prompt pipeline break in main triage path (structured prompt sanitized as raw user text).
2. P1 mismatch between documented and enforced confidence-gating behavior.
3. P1 sideload trust hash mismatch between app constant and documented/bundled model hash.
4. P1 unsupported-language cloud fallback behavior does not match submission claims.

---

## 2) Audit Scope

### Included
- Android app code and build outputs.
- Python/cloud inference API and security middleware.
- Test suites (Android unit, Python unit/integration/security, benchmark tests).
- Submission and README claim cross-check.
- Artifact contents and model distribution paths (APK/AAB/PAD).
- Prompt-injection defense path review (mobile + cloud parity).

### Excluded (environment constraints)
- Real hardware sensor validation (camera/mic/thermal) on physical Android device.
- Connected instrumentation execution requiring attached device.
- Full runtime MedGemma cloud-native inference in this host where `llama_cpp` runtime is unavailable.

---

## 3) Methodology

1. Static architecture and claim cross-check.
2. Build and test execution on latest local code.
3. Security/performance static scans.
4. Packaging and model artifact inspection.
5. Trace-level code review of high-risk runtime paths:
   - Sensor fusion -> prompt generation -> inference engine.
   - Translation/fallback behavior.
   - Thermal and memory gating.
   - Prompt sanitization and output validation.

---

## 4) Runtime Validation Performed

## Android

Executed:
- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- `./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks`
- `./gradlew --no-daemon :app:testReleaseUnitTest`
- `./gradlew --no-daemon :app:lintRelease`
- `./gradlew --no-daemon :app:assembleRelease`
- `./gradlew --no-daemon :app:bundleRelease`
- `./gradlew --no-daemon :app:connectedDebugAndroidTest`

Results:
- Debug unit tests: 193 tests, 0 failures, 0 ignored.
  - Evidence: `mobile/android/app/build/reports/tests/testDebugUnitTest/index.html`
- Release unit tests: 193 tests, 0 failures, 0 ignored.
  - Evidence: `mobile/android/app/build/reports/tests/testReleaseUnitTest/index.html`
- Lint debug/release: 0 errors, 1 warning (`UsableSpace` advisory).
  - Evidence: `mobile/android/app/build/reports/lint-results-release.txt`
- Connected instrumentation: failed due no connected devices.
  - Error: `DeviceException: No connected devices!`

## Python / Backend

Executed:
- `./.audit_venv/bin/pytest -q`
- `./.audit_venv/bin/python -m unittest -v tests.test_config tests.test_security tests.test_api tests.test_integration tests.test_algos`
- `./.audit_venv/bin/python -m pytest -q benchmark/test_sensor_validation.py`
- `cd cloud/inference_api && PYTHONPATH=. ../../.audit_venv/bin/pytest -q security_pytest_suite.py`

Results:
- `pytest -q`: 68 passed, 1 skipped.
- `unittest`: 68 ran, 1 skipped.
- benchmark sensor validation: 39 passed.
- cloud security pytest suite: 74 passed.

Skip context:
- Unmocked model integration is skipped when local environment cannot load `llama_cpp`.

---

## 5) Packaging and Artifact Audit

Generated artifacts:
- `mobile/android/app/build/outputs/apk/debug/app-debug.apk` (~1.2G)
- `mobile/android/app/build/outputs/apk/release/app-release-unsigned.apk` (~1.2G)
- `mobile/android/app/build/outputs/bundle/release/app-release.aab` (~2.6G)

Content checks:
- APKs include TFLite assets and native libs, no MedGemma `.gguf`.
- AAB includes PAD asset pack with MedGemma GGUF:
  - `medgemma/assets/medgemma-4b-it-q4_k_m.gguf`

Hash checks:
- Bundled PAD model hash: `8bcb19d3e363f7d1ab27f364032436fd702e735a6f479d6bb7b1cf066e76b443`
- App constant currently expects: `bff1ff2ed6aebe1b5ecb96b5dc2ee64cd6dfdec3ea4fc2e318d74087119a0ff9`

---

## 6) Findings and Recommendations

## P0 (Critical)

### F-001: Primary triage pipeline sanitizes the structured clinical prompt as if it were raw user text

Evidence:
- Prompt generated in UI flow:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:367`
- Prompt sent to `runNkuCycle(...)`:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:377`
- `runNkuCycle` sanitizes whole input:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:306`
- Sanitizer strips key formatting tokens and delimiters:
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:60`
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:63`
  - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:269`
- But the structured prompt depends on those sections:
  - `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:295`

Risk:
- Degrades or corrupts the intended sensor-to-MedGemma reasoning contract.
- Can suppress required output schema guidance and reduce triage reliability.

Recommendation:
1. Treat `ClinicalReasoner.generatePrompt()` output as trusted system-constructed prompt, not raw user input.
2. Move sanitization to symptom ingress only (`SensorFusion.addSymptom` path or prompt symptom block composition).
3. Route structured prompts through `runMedGemmaOnly(prompt)` or add an explicit non-sanitizing structured mode in engine API.
4. Add regression tests asserting generated prompt structure survives end-to-end invocation.

---

## P1 (High)

### F-002: Claim mismatch - "all sensors <75% and no symptoms => no MedGemma call" not enforced on UI path

Evidence:
- Claim in appendix:
  - `kaggle_submission_appendix.md:930`
- UI gate allows run with lower confidence signals / analyzed flags:
  - `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:61`
  - `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:270`
- Main flow still attempts MedGemma:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:377`
- Abstention exists only in fallback rule-based path:
  - `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:358`

Risk:
- Behavior diverges from submission claim and expected safety threshold semantics.

Recommendation:
1. Add a single pre-LLM eligibility gate in `onRunTriage` using `ClinicalReasoner.CONFIDENCE_THRESHOLD`.
2. If all below threshold and no symptoms: abstain before engine call.
3. Reuse same function for UI button enable/disable and runtime execution to avoid split-brain logic.

---

### F-003: Sideload trust checksum mismatch between code and docs/bundled model

Evidence:
- Code expected hash:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:60`
- Validation use:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:649`
- README expected hash:
  - `README.md:172`
- Bundled PAD file hashes to README value (`8bcb...`), not code constant (`bff1...`).

Risk:
- Reviewer or developer sideload flow may fail despite following official instructions.

Recommendation:
1. Single-source model metadata (filename, size, sha256) in one authoritative config.
2. Validate hash in CI from packaged artifact and fail build on mismatch.
3. Keep README and in-app constants generated from same manifest.

---

### F-004: Unsupported-language cloud fallback in mobile path is currently pass-through stub

Evidence:
- Claims cloud fallback translation behavior:
  - `kaggle_submission_writeup.md:38`
  - `kaggle_submission_writeup.md:48`
- Runtime code for unsupported languages:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:333`
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:338`
- Comment states cloud client is optional/not wired.

Risk:
- Language handling may silently underperform for unsupported local languages.
- Claim-vs-product discrepancy for reviewers.

Recommendation:
1. Either implement actual cloud translation integration for unsupported languages.
2. Or change product/docs/UI messaging to reflect pass-through behavior explicitly.
3. Add integration tests with unsupported language samples and expected branch outcomes.

---

## P2 (Medium)

### F-005: Thermal protection is not consistently enforced across main inference invocation

Evidence:
- `runNkuCycle` thermal checks are optional and only run if manager passed:
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:297`
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:311`
  - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:353`
- Main call omits `thermalManager`:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:377`

Risk:
- Stage-level thermal checks may not run when expected.

Recommendation:
1. Always pass `thermalManager` into inference entrypoints.
2. Consolidate gate logic to avoid stale status reads and duplicate checks.
3. Add tests for thermal hot-state branch before and during inference stages.

---

### F-006: Sensor/model integration tests are mostly synthetic and can skip key reviewer path

Evidence:
- Sideload integration test skips unless file exists (`assumeTrue`):
  - `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:28`
- Camera path uses synthetic bitmap:
  - `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:33`
- Respiratory path uses synthetic waveform:
  - `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:29`

Risk:
- Passing tests may not reflect real camera/mic device behavior.

Recommendation:
1. Add hardware-in-the-loop smoke tests on at least one physical low-end Android device.
2. Add reviewer-path test scenario document with exact expected outcomes.
3. Ensure CI includes emulator/device instrumentation stage where feasible.

---

### F-007: Backend request-size hardening incomplete when `Content-Length` is absent/unreliable

Evidence:
- Header-based size check only:
  - `cloud/inference_api/security.py:679`
- JSON parsed but no robust post-parse byte-size enforcement:
  - `cloud/inference_api/security.py:693`

Risk:
- Large body handling edge cases can bypass intended hard limit.

Recommendation:
1. Enforce `MAX_CONTENT_LENGTH` at Flask/Werkzeug layer.
2. Verify actual raw payload size with `request.get_data(cache=True)` before parse.
3. Keep current decorator check as defense-in-depth.

---

### F-008: Release artifact footprint is very large for target hardware and reviewer UX

Evidence:
- APK size ~1.2G, AAB ~2.6G.
- Large multi-ABI native bundles and TensorFlow Flex libs in APK.

Risk:
- Install friction, storage pressure, slower review setup on budget devices.

Recommendation:
1. Prefer AAB-based distribution and ABI splits for review guidance.
2. Remove unused ABIs/libs for reviewer builds.
3. Create minimal reviewer variant with only required native stacks.

---

## P3 (Low)

### F-009: Lint advisory on storage-space API for large download handling

Evidence:
- `mobile/android/app/build/reports/lint-results-release.txt:1`

Risk:
- Reduced resiliency in low-storage scenarios.

Recommendation:
1. Use `StorageManager#getAllocatableBytes` / `allocateBytes` on API 26+.
2. Keep fallback path for older API levels.

---

## 7) Positive Controls Verified

- Backend has structured auth/rate-limit/headers pipeline and tests passing.
- Prompt injection defenses exist on both cloud and mobile, including homoglyph/base64/leetspeak handling.
- Output validation blocks common leakage patterns and delimiter leakage.
- Cloud security test suite passed fully (74/74).
- Android unit coverage breadth is high (193 tests debug + 193 tests release).

---

## 8) Claim-vs-Code Consistency Summary

Status categories:
- Aligned: many core architecture components, fallback messaging, security layering.
- Partially aligned: confidence gating behavior, thermal gating, reviewer-path integration validation.
- Not aligned: unsupported-language cloud fallback execution semantics, sideload SHA pinning consistency.

---

## 9) Remediation Plan (Prioritized)

## Phase 0 - Blocker fixes (before external review)
1. Fix P0 prompt pipeline handling (F-001).
2. Enforce pre-LLM 75% abstention gate in main triage path (F-002).
3. Resolve checksum single-source-of-truth and regenerate docs/constants (F-003).
4. Align unsupported-language runtime with documented behavior or update claims (F-004).

## Phase 1 - Reliability and safety hardening
1. Pass and enforce thermal manager at inference boundaries (F-005).
2. Add real-device integration validation matrix (F-006).
3. Harden backend payload-size enforcement (F-007).

## Phase 2 - Performance/reviewer-experience optimization
1. Reduce APK footprint via ABI strategy and dependency trimming (F-008).
2. Address storage API lint recommendation (F-009).

---

## 10) Verification Checklist for Closure

Each item should be signed off with evidence artifact paths.

1. Sensor-to-LLM prompt round-trip test confirms no prompt structure corruption.
2. "All sensors <75% and no symptoms" path skips MedGemma call and returns abstain state.
3. Model checksum constants/docs/package outputs all match.
4. Unsupported language path behavior is deterministic and documented.
5. Thermal hot-state test blocks/defers inference as expected.
6. Connected instrumentation passes on at least one physical Android target.
7. Backend rejects oversized payload independent of `Content-Length`.
8. Reviewer install guide validated from clean device state.

---

## 11) Environment Notes and Limitations

- No connected Android device was available in this audit environment.
- `connectedDebugAndroidTest` could not run for that reason.
- Python unmocked model integration tests skip when `llama_cpp` runtime unavailable.
- No source files were modified during the audit itself.

---

## 12) Recommended Immediate Communication to Stakeholders

Use this exact framing:
- "Core architecture is solid and most automated tests pass."
- "There are critical claim-path and integration correctness issues to close before reviewer execution."
- "A focused remediation sprint on P0/P1 items is required prior to final submission confidence."

