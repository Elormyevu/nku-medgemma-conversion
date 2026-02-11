# Comprehensive Product Audit Report

- Date: 2026-02-11
- Auditor: Codex (non-invasive audit only; no source changes)
- Scope: Frontend, backend, performance/efficiency, security, prompt-injection posture, build/release readiness, docs/submission alignment, architecture mapping

## Executive Verdict

The product is **not currently in a state where the writeup/submission claims can be considered fully accurate or fully validated**. The largest blockers are:

1. **Android release/store build is failing** at `lintVitalRelease` with 55 fatal errors (`InvalidFragmentVersionForActivityResult`) and therefore cannot be considered store-ready.
2. **"100% offline / zero cloud dependency" claims are overstated** relative to packaged build evidence (network permissions + transport components remain in release artifacts).
3. **Documentation claims around packaging/distribution do not match current artifacts** (APKs include ~2GB model assets, no AAB output found).
4. **End-to-end workflow correctness is not fully proven** (no device/emulator runtime execution in this environment; several logic and alignment risks were identified statically).

---

## What Was Audited

### Static/code audit
- Android app code (Compose screens, inference engine, camera/sensor pipeline, storage, export, localization)
- Backend cloud API (Flask endpoints, auth, validation, rate limiting, prompt protections, config/logging/deploy)
- CI/CD workflows (Android, CI, security scans)
- Documentation/writeup/submission files and architecture docs

### Build/test/runtime artifact audit
- Python tests:
  - `python3 -m pytest tests/test_api.py tests/test_security.py tests/test_integration.py -q` -> `46 passed`
  - `python3 -m pytest tests/test_algos.py -q` -> `2 passed`
- Android tests/build:
  - `:app:testDebugUnitTest` -> `BUILD SUCCESSFUL`
  - `:app:lintVitalRelease` -> `FAILED` (`55 errors`)
  - `:app:assembleDebug` -> built
  - `:app:assembleRelease` path blocked by lint fatal
- APK/manifest/dex inspection:
  - debug + release APK permissions and contents
  - merged debug/release manifests
  - release dependency chain

### Environment limitations affecting "100%" runtime proof
- `adb` unavailable (`command not found`), so connected emulator/device execution could not be performed in this environment.
- No generated `.aab` artifact found in `mobile/android/app/build/outputs` during this pass.

---

## Requested Questions: Direct Answers

### 1) Does the app work exactly as described in writeup/product/submission docs for emulator and APK/store builds?

**Answer: No (not fully).**

- Store/release path is currently blocked by lint fatal errors:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:107`
  - `mobile/android/app/build/intermediates/lint_vital_intermediate_text_report/release/lintVitalReportRelease/lint-results-release.txt`
- Emulator/device runtime validation cannot be claimed from this audit environment because no `adb` runtime execution was possible.
- Several documentation claims (offline, packaging, localization depth) do not match code/artifact reality.

### 2) Is the Android store app build really offline only? What about emulator version?

**Answer: Not strictly provable as "offline-only" in current packaged state.**

- Release and debug manifests both include network permissions and transport components:
  - `INTERNET`, `ACCESS_NETWORK_STATE`
  - datatransport/CCT services in manifest
  - Evidence: `mobile/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml:18`, `:50`, `:154`, `:157`
- Release dex inspection did **not** show active `CloudInferenceClient`/cloud URL strings in current release APK (likely stripped by R8), but network-capable components still exist.
- Debug APK contains explicit cloud endpoint and cloud client code:
  - `https://nku-inference-api-run.app`, `CloudInferenceClient`, `Authorization: Bearer ...`
- Cloud client has no active call site in current app flow (dead code at present), so practical runtime may still behave locally unless library-level egress occurs.

### 3) Does every workflow in the app work as intended without errors?

**Answer: Cannot be fully validated end-to-end; significant partial confidence only.**

- Unit/integration tests pass for many backend and Android unit scenarios.
- No connected emulator/device run in this environment, so camera + full UI workflows are not fully runtime-verified.
- Static logic issues were found that can affect intended behavior.

### 4) Do writeup/submission docs perfectly align with Android app build capabilities without hallucinations/false promises?

**Answer: No.** Multiple explicit mismatches were found (offline claims, packaging model, size, localization depth, release readiness confidence).

---

## Architecture Map (As Implemented)

```mermaid
flowchart LR
  U[CHW / User] --> H[HomeScreen]
  H --> C[CardioScreen]
  H --> A[AnemiaScreen]
  H --> P[PreeclampsiaScreen]
  H --> T[TriageScreen]

  C --> RPPG[RPPGProcessor]
  A --> PAL[PallorDetector]
  P --> FD[FaceDetectorHelper]
  FD --> EDE[EdemaDetector]

  RPPG --> SF[SensorFusion]
  PAL --> SF
  EDE --> SF

  SF --> CR[ClinicalReasoner]
  CR --> MA[MainActivity]
  MA -->|patientInput=generatePrompt(vitals)| NE[NkuInferenceEngine]

  subgraph OnDeviceAI[On-device Inference]
    PS[PromptSanitizer]
    TG[TranslateGemma GGUF]
    MG[MedGemma GGUF]
    NE --> PS --> TG
    TG --> MG
    MG --> TG
  end

  MA --> DB[(Room: nku_screenings.db)]
  MA --> EXP[ScreeningExporter CSV]
  EXP --> SHARE[Android Share Intent]

  subgraph ModelSources[Model Resolution Order]
    INT[filesDir/models]
    PAD[Play Asset Delivery packs]
    SDCARD[/sdcard/Download fallback]
  end
  NE --> INT
  NE --> PAD
  NE --> SDCARD

  MA -. debug-only class present .-> CIC[CloudInferenceClient]
  CIC -. optional/dev path .-> API

  subgraph CloudAPI[cloud/inference_api]
    API[Flask API]
    IV[InputValidator]
    PP[PromptProtector]
    RL[RateLimiter]
    LLM[llama.cpp models]
    API --> IV --> PP --> LLM
    API --> RL
  end

  CI[GitHub Actions] --> AB[android-build.yml]
  CI --> SEC[security-scan.yml]
  CI --> CORE[ci.yml]
```

---

## Findings (Prioritized)

### Critical

1. **Release/store build currently fails (`lintVitalRelease`)**
- Evidence:
  - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:107`
  - `mobile/android/app/build/intermediates/lint_vital_intermediate_text_report/release/lintVitalReportRelease/lint-results-release.txt`
- Build output shows: `55 errors, 0 warnings`, task `:app:lintVitalRelease FAILED`.
- Impact: Store build readiness claim cannot be accepted.

2. **Docs/submission offline claims overstate current package reality**
- Claims:
  - `README.md:42`, `README.md:46`
  - `kaggle_submission_writeup.md:32`, `:259`
  - `technical_overview_3page.md:11`, `:65`
- Contradictory build evidence:
  - release manifest includes `INTERNET`, `ACCESS_NETWORK_STATE` and datatransport/CCT services:
    - `mobile/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml:18`, `:50`, `:154`, `:157`
- Impact: "100% offline / zero cloud dependency" is not defensible as stated.

3. **Packaging/distribution claims do not match actual artifacts**
- Claims:
  - PAD and small base assumptions in `MODEL_DISTRIBUTION.md:9-39`, `docs/ARCHITECTURE.md:167`, `kaggle_submission_writeup.md:105`
- Actual artifact evidence:
  - `app-debug.apk` ~2.7GB and `app-release-unsigned.apk` ~2.6GB
  - both include embedded GGUF assets (`assets/medgemma-4b-iq1_m.gguf`, `assets/translategemma-4b-iq1_m.gguf`)
  - no `.aab` found in outputs during audit pass
- Impact: submission claims about delivered form factor and size are materially misaligned.

### High

4. **Prompt sanitization can mutate structured clinical prompt format**
- Call chain:
  - `MainActivity.kt` passes `clinicalReasoner.generatePrompt(currentVitals)` into `runNkuCycle(...)`:
    - `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:293-296`
  - `runNkuCycle` sanitizes `patientInput`:
    - `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:221-223`
  - sanitizer strips tokens like `SEVERITY:`, `URGENCY:` etc:
    - `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:58-61`, `:152-159`
  - these tokens are intentionally generated in prompt template:
    - `mobile/android/app/src/main/java/com/nku/app/ClinicalReasoner.kt:124-129`
- Impact: structured prompt contract can be degraded before inference.

5. **Backend `/nku-cycle` ignores output-validation boolean**
- In `/nku-cycle`, `validate_output` result boolean is discarded (`_, english`, `_, assessment`, `_, twi_output`):
  - `cloud/inference_api/main.py:538-540`, `:550-552`, `:565-567`
- Other endpoints correctly gate on `is_valid`.
- Impact: potentially invalid/sanitized-empty outputs can propagate silently in the full cycle path.

6. **Cloud auth contract mismatch between Android client and backend**
- Backend expects `X-API-Key` when configured:
  - `cloud/inference_api/main.py:100-106`
- Android cloud client sends `Authorization: Bearer ...`:
  - `mobile/android/app/src/main/java/com/nku/app/CloudInferenceClient.kt:72`, `:128`
- Impact: if cloud fallback is activated with API key auth, requests fail or require weakened auth setup.

7. **Workflow correctness not fully proven by runtime execution**
- No `adb` in environment, no connected instrumentation/device runs.
- Current tests are largely unit-level + mocked integration.
- Impact: camera permission flows, lifecycle edge-cases, sensor runtime behavior, and actual triage UX cannot be claimed error-free from this pass.

### Medium

8. **Database stores potentially sensitive data unencrypted at rest**
- Plain Room DB builder without SQLCipher/encryption layer:
  - `mobile/android/app/src/main/java/com/nku/app/data/NkuDatabase.kt:35-42`
- Stored fields include symptoms/recommendations:
  - `mobile/android/app/src/main/java/com/nku/app/data/ScreeningEntity.kt:29-34`
- Impact: local data extraction risk on rooted/compromised devices.

9. **CSV export can exfiltrate PHI-like content via share intent**
- Exports symptoms/recommendations and shares externally:
  - `mobile/android/app/src/main/java/com/nku/app/data/ScreeningExporter.kt:37-52`, `:64-71`
- Impact: accidental disclosure risk without consent/retention controls.

10. **Camera executor lifecycle risk (potential resource leak)**
- Single-thread executor created in composable with no explicit shutdown path:
  - `mobile/android/app/src/main/java/com/nku/app/CameraPreview.kt:38`
- Impact: unnecessary thread retention/resource churn across compose/lifecycle transitions.

11. **Expensive per-pixel loops still present in edema path**
- `getPixel()` used in nested loops in eye/cheek analysis:
  - `mobile/android/app/src/main/java/com/nku/app/EdemaDetector.kt:251-255`, `:292-295`, `:323-330`
- Impact: CPU cost and battery/thermal pressure on low-end devices.

12. **Localization claim depth mismatch**
- Code explicitly states Tier 2 = English UI fallback:
  - `mobile/android/app/src/main/java/com/nku/app/LocalizedStrings.kt:7-10`, `:35-67`
- Tier 1 override coverage is uneven (e.g., `wolofStrings`/`zuluStrings`/`xhosaStrings`/`oromoStrings`/`tigrinyaStrings` override ~22 fields vs ~96 for Hausa/Yoruba).
- Hardcoded English strings still appear in screens (examples):
  - `mobile/android/app/src/main/java/com/nku/app/screens/TriageScreen.kt:162`, `:178`, `:265-266`
  - `mobile/android/app/src/main/java/com/nku/app/screens/CardioScreen.kt:212`, `:219-220`
  - `mobile/android/app/src/main/java/com/nku/app/screens/PreeclampsiaScreen.kt:277-278`
- Impact: docs suggesting broad multilingual parity are overstated.

13. **CI quality gates for release/security are partially non-blocking**
- Release bundle step is `continue-on-error: true`:
  - `.github/workflows/android-build.yml:67-69`
- Security audit step in scan workflow is non-blocking:
  - `.github/workflows/security-scan.yml:30-32`
- Impact: red conditions can pass without failing overall pipeline.

14. **API key enforcement is optional by environment**
- If `NKU_API_KEY` is unset, endpoints do not enforce key:
  - `cloud/inference_api/main.py:75-77`, `:104-111`
- Impact: operational misconfiguration can unintentionally expose endpoints.

15. **Store-readiness confidence reduced by transitive fragment mismatch**
- Dependency graph includes `androidx.fragment:fragment:1.1.0` via Play asset delivery:
  - release classpath snapshot output (dependency audit)
- Aligns with lint fatal requiring >=1.3.0.
- Impact: release build break until dependency resolution is fixed.

### Low

16. **AAB artifact absent in current local outputs**
- No `.aab` found under `mobile/android/app/build/outputs` during this pass.
- Impact: cannot validate actual Play upload artifact contents.

17. **Local dependency-vuln audit tooling missing (`pip-audit`)**
- `pip-audit` unavailable in this environment (`command not found`).
- Impact: relied on repo CI configuration evidence rather than fresh local vuln scan.

---

## Product/Submission Alignment Matrix

| Claim in docs/submission | Evidence status | Notes |
|---|---|---|
| 100% offline / zero cloud dependency | **Not aligned** | Network permissions and datatransport components present in release manifest (`.../processReleaseManifest/AndroidManifest.xml:18`, `:50`, `:154-167`) |
| Verified on emulator & device | **Not verifiable in this pass** | Environment lacked `adb`; no connected device/emulator runtime tests performed |
| PAD-based delivery / small base app profile | **Not aligned (current artifacts)** | Built APKs contain embedded GGUF models and are >2.6GB |
| 46 language support (production-ready parity) | **Partially aligned** | 46 listed yes, but tier-2 explicit English fallback and uneven tier-1 string completeness |
| Store-ready Android build | **Not aligned** | Release lint fatal blocks build readiness |

---

## Workflow Audit Matrix

| Workflow | Static Code Path | Automated Test Evidence | Runtime Verified in this pass | Status |
|---|---|---|---|---|
| Home navigation/language/export UI | Yes (`HomeScreen.kt`, `MainActivity.kt`) | Android UI/unit tests (HomeScreen) | No device/emulator | Partial |
| Cardio measurement (camera+rPPG) | Yes | No direct connected camera test | No | Partial |
| Anemia capture/analyze | Yes | No direct connected camera test | No | Partial |
| Preeclampsia capture/analyze | Yes | No direct connected camera test | No | Partial |
| Triage run (Nku cycle) | Yes | Unit/integration coverage exists | No full device runtime | Partial |
| Rule-based fallback triage | Yes | Unit tests in `ClinicalReasonerTest.kt` | No full runtime | Partial |
| TTS playback | Yes (`NkuTTS.kt`, `TriageScreen.kt`) | No runtime audio assertions | No | Partial |
| Local DB save/history | Yes | Indirect only | No runtime verification | Partial |
| CSV export/share | Yes | HomeScreen callback tests only | No chooser/runtime verification | Partial |
| Cloud API `/translate`/`/triage`/`/nku-cycle` | Yes | Python tests pass | Live cloud deploy not exercised | Partial-High confidence |

---

## Frontend Audit Summary

### Strengths
- Clear screen decomposition (`screens/*`) and Compose structure.
- Permission gating implemented for camera/mic flows.
- Multi-language framework present with explicit fallback labeling.

### Risks
- Hardcoded English strings in key screens reduce localization fidelity.
- Accessibility inconsistencies (`contentDescription = null` on many icons; likely decorative in some cases, but needs explicit audit).
- Some flow text and severity labels bypass localization objects.

---

## Backend Audit Summary

### Strengths
- Security headers, request validation, rate limiting, CORS controls, structured logging are implemented.
- Tests for injection patterns and API behavior are extensive and passing.
- Dockerfile uses non-root runtime user.

### Risks
- `/nku-cycle` output validation logic is inconsistent with `/translate` and `/triage` handling.
- Auth is environment-optional; risk if deployment variables are incomplete.
- Client-server auth-header contract mismatch for cloud fallback path.

---

## Security Audit Summary (including prompt injection)

### Positive controls verified
- Input validation and injection pattern checks in backend and mobile sanitizers.
- Delimiter-based prompt hardening and leakage checks.
- `allowBackup="false"` in app manifest.
- FileProvider configured non-exported.

### Priority security gaps
- Output validation bypass in `/nku-cycle` flow (boolean ignored).
- Plaintext Room data at rest for potentially sensitive triage data.
- Export/share path can disseminate sensitive records without policy gates.
- Offline claims can create governance/compliance risk if interpreted as zero network surface.

---

## Performance/Efficiency Audit Summary

### Major efficiency concerns
- Large universal APK footprint (embedded GGUF + multi-ABI natives) impacts install/update/storage.
- Startup extraction path can duplicate model storage and add heavy first-run I/O.
- Edema analysis still does per-pixel read loops in hot paths.
- Camera executor lifecycle lacks explicit teardown in composable.

### Positive performance controls
- Model swap orchestration to stay within RAM constraints.
- Some optimized pixel batch reads exist (`estimateImageQuality` path).
- Resource shrinking/minification enabled for release config (although release currently blocked by lint).

---

## CI/CD, QA, and Operational Audit

### Observations
- Core backend tests and Android unit tests pass.
- Security scanning is present but not uniformly blocking.
- Android release bundle step does not fail pipeline by default (`continue-on-error`).

### Operational implication
- CI currently allows "green-enough" states that can still hide release blockers.

---

## Evidence Highlights (key file references)

### Documentation claims
- `README.md:42`, `README.md:46`, `README.md:255`
- `kaggle_submission_writeup.md:32`, `kaggle_submission_writeup.md:105`, `kaggle_submission_writeup.md:218`, `kaggle_submission_writeup.md:259`
- `technical_overview_3page.md:11`, `technical_overview_3page.md:65`
- `MODEL_DISTRIBUTION.md:9-39`
- `docs/ARCHITECTURE.md:7`, `docs/ARCHITECTURE.md:37`, `docs/ARCHITECTURE.md:152`, `docs/ARCHITECTURE.md:167`

### Android implementation/build
- `mobile/android/app/build.gradle:37-47`, `:91`, `:103-104`
- `mobile/android/app/src/main/AndroidManifest.xml:12`, `:15-18`, `:26`
- `mobile/android/app/src/main/java/com/nku/app/MainActivity.kt:103`, `:107`, `:293-300`, `:317-323`
- `mobile/android/app/src/main/java/com/nku/app/NkuInferenceEngine.kt:89-121`, `:221-223`, `:388-404`
- `mobile/android/app/src/main/java/com/nku/app/PromptSanitizer.kt:58-61`, `:152-159`
- `mobile/android/app/src/main/java/com/nku/app/CloudInferenceClient.kt:27`, `:72`, `:128`
- `mobile/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml:18`, `:50`, `:154-168`
- `mobile/android/app/build/intermediates/lint_vital_intermediate_text_report/release/lintVitalReportRelease/lint-results-release.txt`

### Backend
- `cloud/inference_api/main.py:75-77`, `:100-106`, `:407-414`, `:471-478`, `:538-540`, `:550-552`, `:565-567`
- `cloud/inference_api/security.py:85-130`, `:288-307`, `:543-556`
- `cloud/inference_api/deploy.sh:49-50`, `:100`

### CI
- `.github/workflows/android-build.yml:67-69`
- `.github/workflows/security-scan.yml:31`
- `.github/workflows/ci.yml:77-82`, `:101-106`

---

## Overall Coverage Statement

This pass covered all major product surfaces in-repo (frontend, backend, build, security, performance, docs alignment) and identified the principal correctness, release, and trust-risk gaps.

A strict claim of "100% product audit coverage" is **not achievable without**:
- connected emulator/device execution for camera/audio/sensor/TTS workflows,
- generation and inspection of final Play `.aab`,
- deployment-level verification of cloud auth/network egress behavior in production infra.

Given current evidence, the safest conclusion is:
- **Backend code quality and security controls are substantial**, with specific high-risk correctness issues.
- **Android app has meaningful engineering depth**, but **release readiness and submission-claim alignment are currently not at an acceptable confidence level**.

