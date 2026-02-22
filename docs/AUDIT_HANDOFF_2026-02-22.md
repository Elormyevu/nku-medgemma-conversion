# Nku Sentinel Audit Handoff

Date: February 22, 2026  
Project root: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335`  
Prepared by: Codex (read-only audit, no functional code changes requested)

## 1) Executive Summary

This audit covered Android frontend/mobile runtime, backend/cloud API, model integration, sensor-to-triage path, performance signals, and security posture (including prompt-injection defenses). The most important result is:

- There is a **release-blocking MedGemma trust-chain mismatch** in reviewer paths (sideload + first-run download fallback), causing model readiness failures in instrumented testing and logcat.

Additional high-impact findings:

- Runtime Nku cycle currently bypasses stage-1 translation in code despite docs claiming strict English-reasoning pipeline.
- Voice symptom capture uses device locale, not selected in-app language.
- Real-model backend integration is skipped in default test execution when `llama_cpp` is unavailable.
- Sensor integration instrumentation is mostly synthetic and does not fully prove real camera/mic quality behavior.

## 2) Scope and Method

Audit included:

- Static code review across mobile (`mobile/android/app`), cloud (`cloud/inference_api`), and supporting docs/specs.
- Build, lint, unit tests, Android instrumented tests, and targeted model-integration instrumentation on emulator.
- Backend pytest + coverage + targeted security test suite execution.
- Dependency vulnerability check (`pip-audit`) for backend requirements.
- Cross-check between submission claims and implementation behavior.

Not performed:

- Physical-device validation with real camera/mic/acoustic capture.
- Production Play-distributed PAD install validation from Play Store infrastructure.
- Live cloud deployment penetration testing.

## 3) Environment and Commands Executed

### Android

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug`
- `./gradlew connectedDebugAndroidTest --rerun-tasks`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nku.app.ModelIntegrationInstrumentedTest#medGemma_sideloadedModel_isDiscoverableAndTrusted --rerun-tasks`

### Python/Backend

- `.venv/bin/python3.13 -m pytest -q`
- `.venv/bin/python3.13 -m pytest --cov=cloud/inference_api --cov=src --cov-report=term-missing -q`
- `PYTHONPATH=cloud/inference_api .venv/bin/python3.13 -m pytest cloud/inference_api/security_pytest_suite.py -q`
- `.venv/bin/pip-audit -r cloud/inference_api/requirements.txt`

### Model/hash verification

- `shasum -a 256 medgemma-4b-it.Q4_K_M.gguf`
- `shasum -a 256 models/medgemma-4b-q4_k_m.gguf`
- `shasum -a 256 mobile/android/medgemma/src/main/assets/medgemma-4b-it-q4_k_m.gguf`
- HuggingFace metadata checks via `huggingface_hub` for model etag/size/commit.

## 4) Results Snapshot

### Test and build status

- Android unit tests: **193 passed, 0 failed**
- Android lint: **1 warning** (`ChromeOsAbiSupport`)
- Android full instrumentation run: **14 executed, 1 skipped, 0 failed** (skip condition on sideload test)
- Targeted model integration instrumentation (with sideloaded GGUF): **failed**
- Backend pytest: **68 passed, 1 skipped**
- Backend coverage: **44% total**
- Security suite (`security_pytest_suite.py`) with proper `PYTHONPATH`: **74 passed**
- Dependency audit (backend requirements): **1 known CVE** (`diskcache` 5.6.3)

### Key artifact locations

- Android lint XML: `mobile/android/app/build/reports/lint-results-debug.xml`
- Android connected tests XML: `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_tecno_3gb(AVD) - 15-_app-.xml`
- Android connected report HTML: `mobile/android/app/build/reports/androidTests/connected/debug/index.html`

## 5) Severity-Ranked Findings and Recommendations

## P0 (Blocker)

### F-001: MedGemma trust-chain mismatch breaks reviewer model readiness

Issue:

- App expects MedGemma hash `8bcb19...`.
- Reviewer/developer artifacts and fallback download URLs resolve to other hashes (`bff1ff...` and `8f7b7a...` observed).
- Instrumented sideload test fails when model hash does not match expected constant.

Evidence:

- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:61`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:69`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:617`
- `mobile/android/app/src/main/java/com/nku/app/ModelFileValidator.kt:51`
- `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_tecno_3gb(AVD) - 15-_app-.xml:2`
- `mobile/android/app/build/outputs/androidTest-results/connected/debug/TEST-nku_tecno_3gb(AVD) - 15-_app-.xml:12`
- `README.md:168`
- `README.md:172`

Observed runtime symptom:

- Logcat shows `SHA-256 mismatch` and `Invalid/corrupt/untrusted GGUF`.

Recommendations:

- Define one canonical production artifact (repo + filename + size + SHA-256 + commit).
- Align all of: Kotlin constants, fallback URL, README commands, and reviewer instructions.
- Add CI assertion test that computes and checks expected hash against release model manifest.
- Add a dedicated instrumentation test that validates first-run download path hash acceptance.

Acceptance criteria:

- Sideloaded model integration test passes with documented reviewer artifact.
- First-run download model validates hash and loads successfully on clean emulator install.

## P1 (High)

### F-002: Runtime translation stage is bypassed in Nku cycle

Issue:

- `runNkuCycle` assigns `englishText = structuredPrompt` and bypasses translation stage.
- Documentation claims explicit stage-1 local language to English translation gate.

Evidence:

- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:304`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:306`
- `kaggle_submission_writeup.md:38`
- `kaggle_submission_writeup.md:48`

Risk:

- Non-English symptom data can flow directly into MedGemma prompt context.
- Submission claims and runtime behavior diverge.

Recommendations:

- Decide intended architecture and enforce one truth:
  - If strict English-only reasoning is required, implement/restore pre-reasoning translation for supported languages.
  - If passthrough is intended, update docs/submission language to match implementation.
- Add integration tests for multilingual symptom input verifying expected transform behavior.

### F-003: Voice symptom capture uses device locale instead of selected app language

Issue:

- Speech recognizer intent uses `Locale.getDefault()` instead of selected language preference.

Evidence:

- `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:97`
- `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:180`

Risk:

- In multilingual workflows, STT language may not match user-selected clinical language.

Recommendations:

- Map selected app language code to speech-recognizer locale explicitly.
- Add instrumentation test for language switching + voice input.

### F-004: Model integration test can silently skip in normal CI/emulator runs

Issue:

- `ModelIntegrationInstrumentedTest` is intentionally skipped when large sideload file is absent.

Evidence:

- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:15`
- `mobile/android/app/src/androidTest/java/com/nku/app/ModelIntegrationInstrumentedTest.kt:28`

Risk:

- Core trust-path regressions are hidden in “green” connected test runs.

Recommendations:

- Split into two tests:
  - deterministic negative test (no model, assert explicit fallback state),
  - deterministic positive test in pre-release lane with provisioned known-good artifact.
- Fail release gate if positive path test not executed.

## P2 (Medium)

### F-005: Sensor integration instrumentation is mostly synthetic

Issue:

- Camera and respiratory tests use synthetic bitmaps/audio arrays rather than real capture pipelines.

Evidence:

- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:16`
- `mobile/android/app/src/androidTest/java/com/nku/app/CameraTriageInstrumentedTest.kt:37`
- `mobile/android/app/src/androidTest/java/com/nku/app/RespiratoryPipelineInstrumentedTest.kt:28`

Risk:

- Real-world sensor behavior (lighting, motion blur, microphone noise, cough cadence) not fully validated.

Recommendations:

- Add scripted instrumentation workflows using CameraX test harness and recorded real sample bundles.
- Add on-device field-validation checklist for at least 3 representative lighting/noise conditions.

### F-006: Potential coroutine leak/performance drift in inference progress updater

Issue:

- A `CoroutineScope(Dispatchers.Main).launch` timer is canceled on success but not guaranteed on all exception paths.

Evidence:

- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:340`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:349`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:391`

Risk:

- UI progress updates can outlive failed inference path, causing unnecessary work/state churn.

Recommendations:

- Move timer job lifecycle into `try/finally` and always cancel in `finally`.
- Add regression test for thrown inference exception and verify no further progress emissions.

### F-007: Over-broad media permissions in manifest

Issue:

- Manifest requests `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED`, but model search uses app-specific external dirs (`getExternalFilesDir`) which generally do not require these broad media reads.

Evidence:

- `mobile/android/app/src/main/AndroidManifest.xml:19`
- `mobile/android/app/src/main/AndroidManifest.xml:20`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:158`

Risk:

- Expanded privacy/security attack surface and reviewer concern.

Recommendations:

- Re-validate minimal permission set and remove unnecessary media read permissions.
- Keep only permissions strictly required for runtime behavior.

### F-008: Backend security test suite not discoverable in default root test run

Issue:

- `cloud/inference_api/security_pytest_suite.py` imports `from security import ...`; requires `PYTHONPATH=cloud/inference_api`.
- Root `pytest` command does not include this file automatically due naming.

Evidence:

- `cloud/inference_api/security_pytest_suite.py:12`

Risk:

- Critical security regression suite may be skipped by developers/CI unintentionally.

Recommendations:

- Convert imports to package-relative imports and rename file to `test_security_suite.py`.
- Add CI target explicitly invoking this suite.

### F-009: Known vulnerable dependency remains in backend stack

Issue:

- `diskcache==5.6.3` flagged by `pip-audit` with `CVE-2025-69872`.

Evidence:

- `cloud/inference_api/requirements.txt:18`
- Audit output: `Found 1 known vulnerability in 1 package`

Current mitigation noted in code:

- Process-private cache directory hardening in `cloud/inference_api/main.py:30`.

Recommendations:

- Track upstream fix availability and pin patched version immediately when available.
- Add operational hardening controls:
  - run service as non-root,
  - read-only filesystem except required cache dir,
  - strict container image provenance/scanning in CI.

## P3 (Low)

### F-010: Timeout handling uses best-effort async thread exception fallback

Issue:

- Timeout wrapper relies on `PyThreadState_SetAsyncExc` fallback for non-main thread contexts.

Evidence:

- `cloud/inference_api/main.py:259`

Risk:

- C-extension blocking calls may not terminate predictably.

Recommendations:

- Prefer process-level timeout isolation (worker-per-request pool or subprocess model) for hard guarantees.
- Add chaos tests for forced model stall behavior.

### F-011: Lint/deprecation maintenance debt

Issue:

- Deprecated Compose/Camera APIs and ChromeOS ABI warning.

Evidence:

- `mobile/android/app/build.gradle:39`
- `mobile/android/app/build/reports/lint-results-debug.xml`

Recommendations:

- Triage and update deprecated calls.
- Decide explicitly whether ChromeOS x86_64 support is in scope for submission.

## 6) Security Posture Summary (Prompt Injection + API)

Strengths:

- Cloud input validation has multi-layer pattern checks and unicode normalization:
  - `cloud/inference_api/security.py:54`
  - `cloud/inference_api/security.py:151`
  - `cloud/inference_api/security.py:172`
- Prompt boundary delimiter model and output leakage checks:
  - `cloud/inference_api/security.py:313`
  - `cloud/inference_api/security.py:392`
- API key gate and rate limiting exist by default:
  - `cloud/inference_api/main.py:131`
  - `cloud/inference_api/main.py:451`
  - `cloud/inference_api/security.py:559`

Residual risks:

- Security suite discoverability/import-path issue (F-008).
- Dependency CVE (F-009).
- Deployment misconfig possibility if `DEBUG=true` in production:
  - `cloud/inference_api/config.py:87`
  - `cloud/inference_api/main.py:147`

## 7) Performance and Reliability Summary

Observed positives:

- Android app compiles and runs on emulator without startup crash.
- Thermal/memory fallback path is implemented:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:375`
  - `mobile/android/app/src/main/java/com/nku/app/ThermalManager.kt:132`

Gaps:

- Limited automated performance thresholds (latency/memory regression gates).
- Coroutine lifecycle issue (F-006) can degrade UI stability under failure.

Recommended performance controls:

- Add benchmark baselines for:
  - cold start,
  - model load latency,
  - median/95p inference latency,
  - memory peak during model load and inference,
  - thermal throttling incidence.

## 8) Claim-to-Code Alignment Risks

Key inconsistencies to resolve before external review:

- Translation/fallback behavior claims vary across docs versus runtime.
- Model source/hash claims across docs and code are inconsistent.

Relevant docs:

- `README.md:138`
- `MODEL_DISTRIBUTION.md:4`
- `kaggle_submission_writeup.md:38`
- `kaggle_submission_appendix.md:597`

## 9) Prioritized Remediation Plan

### Phase 0 (Submission Blockers, 24-48 hours)

1. Resolve model provenance/hash mismatch across all paths (F-001).
2. Ensure reviewer path model test is deterministic and mandatory (F-004).
3. Reconcile translation-stage implementation vs claim, update code or docs accordingly (F-002).
4. Fix STT locale mapping to selected language (F-003).

### Phase 1 (Hardening, 2-5 days)

1. Fix coroutine timer lifecycle robustness (F-006).
2. Minimize Android permissions to least privilege (F-007).
3. Make security test suite always discoverable in standard CI (F-008).
4. Add operational controls around `diskcache` CVE until patched upstream (F-009).

### Phase 2 (Quality and Evidence Strength, 1-2 weeks)

1. Add real sensor capture validation harnesses and curated on-device fixtures (F-005).
2. Add enforceable performance regression gates and dashboards.
3. Upgrade deprecated APIs and resolve lint debt (F-011).

## 10) Validation Checklist for Next Owner

- Hash/provenance:
  - `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nku.app.ModelIntegrationInstrumentedTest#medGemma_sideloadedModel_isDiscoverableAndTrusted --rerun-tasks`
- Mobile build/lint/unit:
  - `./gradlew clean testDebugUnitTest lintDebug assembleDebug`
- Full connected tests:
  - `./gradlew connectedDebugAndroidTest --rerun-tasks`
- Backend tests:
  - `.venv/bin/python3.13 -m pytest -q`
  - `PYTHONPATH=cloud/inference_api .venv/bin/python3.13 -m pytest cloud/inference_api/security_pytest_suite.py -q`
- Vulnerability scan:
  - `.venv/bin/pip-audit -r cloud/inference_api/requirements.txt`

## 11) Audit Limitations and Assumptions

- Emulator-only validation cannot fully substitute for real hardware camera/microphone conditions.
- Cloud inference real-model path remains partially unverified without installed `llama_cpp` native runtime in test context.
- Play Store delivery mechanics were not validated end-to-end from production channel.

## 12) Final Recommendation

Treat F-001, F-002, F-003, and F-004 as release gates before reviewer submission. These are the issues most likely to produce visible reviewer failures or claim-vs-behavior discrepancies. The remaining items are hardening and evidence-quality work that will materially improve reliability, trust, and security posture.

