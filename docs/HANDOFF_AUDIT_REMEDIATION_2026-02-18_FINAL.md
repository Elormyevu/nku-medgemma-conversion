# Nku Audit Remediation Handoff (Final)

Date: 2026-02-18
Workspace: `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335`
Branch: `main`
Current HEAD: `fd8831a`

## 1) Scope And Intent

This handoff consolidates the full audit/remediation cycle completed across the latest five remediation commits:
- `b08d7b6`
- `574d79d`
- `84b1d44`
- `3fb3dd0`
- `fd8831a`

It covers:
- Backend/cloud inference API security, config, deployability, and tests
- Android app sensor/model pipeline behavior and robustness
- Prompt-injection hardening (cloud + mobile)
- Frontend UX correctness fixes tied to reviewer-visible behavior
- Verification evidence and remaining risks

## 2) Commit Timeline (What Was Changed)

1. `b08d7b6` `fix: resolve 7 audit findings (P1-P3)`
- Respiratory stop/cancellation reliability
- Better cough-segment selection for respiratory inference
- Replaced non-public cloud model defaults with real public HF defaults
- Corrected misleading translation fallback docs/claims
- Submission/docs correctness fixes

2. `574d79d` `fix: harden sensor-model pipeline and add audit handoff`
- Added broad hardening across cloud and Android sensor/model pipeline
- Added benchmark artifact policy, output validation, lifecycle safety improvements
- Added and updated security tests + handoff documentation

3. `84b1d44` `fix: audit remediation — pin model rev, camera test, nav bar, triage UX`
- Pinned MedGemma revision for reproducibility
- Added camera-to-triage instrumentation smoke test
- Improved reviewer-facing tab and triage text UX
- Added benchmark policy enforcement notes

4. `3fb3dd0` `fix: audit remediation — add cloud test suite, translator test, TranslateGemma warning`
- Added large cloud security pytest suite
- Added Android translator behavior tests
- Added startup warning when translator defaults to MedGemma

5. `fd8831a` `fix: harden cloud auth/config and Android model validation`
- Fixed production auth/deploy mismatch (`NKU_API_KEY` provisioning)
- Fixed env-loading regression dropping pinned model revisions
- Hardened prompt-injection detection against paraphrased override attacks
- Added strict GGUF model-file validation in Android readiness/loading path
- Fixed Python test discovery collision by renaming duplicate `test_security.py`

## 3) Findings -> Fixes Matrix

| ID | Finding | Severity | Fix Implemented | Commit(s) | Status |
|---|---|---|---|---|---|
| F-01 | Cloud production requests fail when `NKU_API_KEY` is unset but auth is required | P1 | Deploy script now enforces/provisions `NKU_API_KEY` (secret-first, env fallback); API auth behavior covered by tests | `fd8831a` | Fixed |
| F-02 | `AppConfig.from_env()` dropped pinned revisions when env vars were unset | P1 | Env loader now falls back to `ModelConfig()` defaults for revisions and full model runtime params | `fd8831a` | Fixed |
| F-03 | Prompt injection defense missed paraphrased instruction-override attempts | P1 | Added paraphrase-resistant patterns and intent heuristics in cloud and mobile sanitizers; added regression tests | `fd8831a` | Fixed |
| F-04 | Android model readiness accepted tiny/corrupt GGUF files (existence-only check) | P1 | Added `ModelFileValidator` (header + minimum-size checks) and integrated into model resolution path | `fd8831a` | Fixed |
| F-05 | `unittest discover` instability from duplicate `test_security.py` namespace collision | P2 | Renamed cloud pytest suite file to avoid collision with root tests | `fd8831a` | Fixed |
| F-06 | Cloud translator defaults pointed to non-deployable placeholder repo/file | P1 | Replaced with deployable defaults and added test coverage | `b08d7b6`, `574d79d`, `tests/test_config.py` updates through `fd8831a` | Fixed |
| F-07 | Mobile/docs implied cloud fallback translation that was not in shipped runtime path | P1 | Corrected code paths, comments, and submission/docs wording to match actual behavior | `b08d7b6`, `574d79d` | Fixed |
| F-08 | Respiratory recording stop/trim behavior risked wrong audio segment inference | P1 | Added proper coroutine/AudioRecord cancellation and energy-based segment selection | `b08d7b6` | Fixed |
| F-09 | Sensor/model pipeline lacked enough runtime evidence in Android instrumentation | P1 | Added respiratory runtime smoke test and camera-triage test; ensured instrumentation runner and reports | `574d79d`, `84b1d44` | Improved |
| F-10 | Prompt defense parity gaps between cloud and mobile sanitizer behavior | P1 | Added leetspeak/homoglyph/base64 protections and expanded tests in both stacks | `574d79d`, `fd8831a` | Fixed |
| F-11 | `/nku-cycle` could pass invalid model outputs without strict validation gates | P1 | Added `is_valid` gating across translation, triage, and back-translation stages | `574d79d` | Fixed |
| F-12 | Cloud model loading could force unnecessary dual-model dependency per endpoint | P2 | Introduced endpoint-scoped model requirements (`require_models`) | `574d79d` | Fixed |
| F-13 | Reviewer-visible UX issues (tab label wrapping, triage wording clarity) | P3 | Navigation label and triage copy adjusted for clarity and stable layout | `84b1d44` | Fixed |
| F-14 | Benchmark artifact ambiguity risked credibility of canonical results | P2 | Added benchmark artifact policy and deprecation metadata on backup artifact | `574d79d`, `84b1d44` | Fixed |

## 4) Detailed Changes By Subsystem

## A. Cloud Backend (`cloud/inference_api`)

1. `config.py`
- Preserved pinned default revisions when env vars are absent.
- Added env support for `MODEL_BATCH` and `MODEL_GPU_LAYERS` fallback defaults.

2. `deploy.sh`
- Added hard fail when neither `NKU_API_KEY` env var nor secret exists.
- Added secret-first injection for `NKU_API_KEY` (`nku-api-key` by default).
- Maintains secure auth posture while preventing silent production misconfiguration.

3. `security.py`
- Expanded injection regex set with paraphrase-resistant override/prompt-leak patterns.
- Added token-intent heuristic (`override verb` + `control target`) for non-literal attacks.
- Extended base64 decoded-content checks to include heuristic detection.
- Added output-side rejection for system/developer leakage markers.

4. `main.py` (prior remediation commits)
- Endpoint-scoped model requirements.
- Output validation gating in `/nku-cycle`.
- Startup warning when translation model config is still MedGemma fallback.

5. Test hygiene
- Renamed cloud pytest suite file:
  - from `cloud/inference_api/test_security.py`
  - to `cloud/inference_api/security_pytest_suite.py`
- Prevents import collisions under generic `unittest discover`.

## B. Android App (`mobile/android/app`)

1. New model integrity guard
- Added `ModelFileValidator.kt` with GGUF signature + minimum-size checks.
- Added `ModelFileValidatorTest.kt` coverage for null/tiny/invalid/valid cases.
- Wired validation into `NkuInferenceEngine.resolveModelFile()` for:
  - internal extracted model
  - Play Asset Delivery model
  - sdcard fallback model

2. Prompt injection hardening parity
- Extended `PromptSanitizer.kt` with paraphrase-resistant checks and intent heuristic.
- Added/updated tests in `PromptSanitizerTest.kt` for paraphrased attacks and output leakage.

3. Prior sensor/model reliability fixes (from earlier commits in this cycle)
- Respiratory recording cancel/segment logic hardening.
- Deep-path respiratory processing fallback behavior safeguards.
- Lifecycle cleanup improvements and startup blocking reductions.
- Added runtime instrumentation tests for respiratory and camera->triage handoff.

4. Frontend UX corrections
- Stabilized bottom navigation label wrapping.
- Clarified triage section and mic guidance language.

## C. Tests And Quality Gates (`tests/`)

1. New/expanded backend tests
- `tests/test_api.py`: API key enforcement and production misconfiguration behavior.
- `tests/test_config.py`: regression guards for revision fallback and overrides.
- `tests/test_security.py`: paraphrased injection + output leakage rejection tests.
- `tests/test_integration.py`: paraphrased override injection coverage.

2. Android JVM tests
- Added `ModelFileValidatorTest`.
- Expanded `PromptSanitizerTest` for new security behavior.

## 5) Verification Evidence

## A. Latest verification run in this session (after `fd8831a`)

1. Backend test suite (project venv)
- Command: `./.audit_venv/bin/python -m unittest -v tests.test_config tests.test_security tests.test_api tests.test_integration`
- Result: `Ran 58 tests ... OK`

2. Backend discovery test (collision regression check)
- Command: `./.audit_venv/bin/python -m unittest discover -s tests -v`
- Result: `Ran 60 tests ... OK`

3. Android JVM unit tests
- Command: `./gradlew :app:testDebugUnitTest`
- Result: `BUILD SUCCESSFUL` (`191 tests completed, 0 failed`)

4. Sanity checks executed during fix validation
- Verified paraphrased injection probes now rejected by `InputValidator`.
- Verified config fallback now preserves pinned revisions without env overrides.

## B. Earlier audit execution evidence available in workspace reports

1. Android instrumentation report path
- `mobile/android/app/build/reports/androidTests/connected/debug/index.html`

2. Android unit report path
- `mobile/android/app/build/reports/tests/testDebugUnitTest/index.html`

3. Lint report path
- `mobile/android/app/build/reports/lint-results-debug.txt`

4. Prior audit run highlights (from earlier pass)
- Connected tests passed on emulator
- Lint had warnings but zero errors
- Debug/release builds succeeded
- Runtime logs captured model fallback and startup jank observations

## 6) Known Remaining Risks / Open Items

1. Physical-device sensor validation gap remains
- Emulator/instrumented tests are improved but not a substitute for real device field conditions.

2. Performance debt remains
- Startup frame-drop and large APK footprint are not fully solved in this pass.

3. Prompt defense remains heuristic
- Stronger than before, but no regex/heuristic filter guarantees complete resistance.

4. Security/dependency scanning completeness
- Dependency CVE scanning can still be environment/network dependent; re-run in release CI.

5. Translation quality caveat for unsupported on-device languages
- Runtime behavior is now truthful/documented, but medical quality for pass-through cases remains constrained.

## 7) Reviewer/Release Runbook (Recommended)

1. Backend verification
- Run: `./.audit_venv/bin/python -m unittest discover -s tests -v`
- Confirm API key flow in staging:
  - no key in production mode -> `503 misconfigured`
  - wrong key -> `401`
  - correct key -> request reaches model stage

2. Android verification
- Run: `./gradlew :app:testDebugUnitTest`
- Run: `./gradlew :app:connectedDebugAndroidTest` (emulator/device)
- Inspect reports at:
  - `mobile/android/app/build/reports/tests/testDebugUnitTest/index.html`
  - `mobile/android/app/build/reports/androidTests/connected/debug/index.html`

3. Deployment readiness
- Ensure one of:
  - secret `nku-api-key` exists (preferred)
  - or `NKU_API_KEY` env var is explicitly set
- Ensure `HF_TOKEN` provisioning path is configured (secret preferred)

## 8) File-Level Reference (Latest Commit `fd8831a`)

- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/cloud/inference_api/config.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/cloud/inference_api/deploy.sh`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/cloud/inference_api/security.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/cloud/inference_api/security_pytest_suite.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/app/src/main/java/com/nku/app/ModelFileValidator.kt`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/app/src/test/java/com/nku/app/ModelFileValidatorTest.kt`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/app/src/test/java/com/nku/app/PromptSanitizerTest.kt`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/tests/test_api.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/tests/test_config.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/tests/test_integration.py`
- `/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/tests/test_security.py`

## 9) Handoff Closeout

The remediation cycle materially improved deployment safety, testability, and trustworthiness of runtime claims. The highest remaining blocker for challenge-level confidence is still physical-device validation depth across all sensor modalities.
