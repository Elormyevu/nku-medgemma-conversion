# Nku Sentinel — Model Distribution Strategy

## Current State
MedGemma 4B is quantized to Q4_K_M format (~2.3GB) and delivered via Play Asset Delivery. The HeAR Event Detector (1.1MB INT8 TFLite) ships in the APK's `assets/` directory. Translation is handled by Android ML Kit (on-device, ~30MB/language pack). In the current shipped mobile app, unsupported languages pass through unchanged in offline mode; the cloud translation backend remains an optional extension path.

> **HeAR ViT-L Encoder — not shipped**: The HeAR ViT-L encoder (~1.2GB FP32) uses `XlaCallModule` nodes with serialized StableHLO bytecode — an XLA-specific format that no current conversion tool (tf2onnx, TFLite converter) can process into ONNX or TFLite. We attempted 7 conversion approaches across 3 toolchains (tf2onnx CLI, TFLite INT8, TFLite StableHLO-only) before documenting this as a technical limitation. The codebase includes full architectural support for the ViT-L encoder as a documented upgrade path.

**Why the Event Detector still ships**: The Event Detector paired with MedGemma delivers real clinical triage value. It classifies 8 health sound events and produces structured respiratory signals (cough probability, risk score, event distribution) that MedGemma interprets alongside other vitals for TB/COPD/pneumonia triage. In Sub-Saharan Africa — where 44% of MDR-TB goes undiagnosed, COPD prevalence is projected to rise 59% by 2050, and pneumonia kills ~500,000 children annually — even event-level cough detection with clinical LLM reasoning provides a screening signal CHWs currently lack entirely.

For development/testing, models can also be sideloaded to `/sdcard/Download/` via ADB push. The mobile app enforces SHA-256 trust validation for sideloaded MedGemma files.

## Production Distribution: Play Asset Delivery (PAD)

MedGemma is delivered via [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), ensuring offline capability from first launch. The HeAR Event Detector ships in the APK itself.

### Architecture

```
AAB (Android App Bundle)
├── base module (~140MB delivered on arm64; ~340MB compressed in bundle)
│                               ← app code, UI, JNI runtimes, HeAR Event Detector (1.1MB TFLite in assets)
├── asset-pack: medgemma        ← MedGemma Q4_K_M GGUF (~2.3GB)
│   └── delivery: install-time  ← downloaded with initial install
└── ML Kit language packs       ← ~30MB each, downloaded on demand
```

### Model Inventory

| Component | Size | Delivery | Offline |
|:----------|:----:|:--------:|:-------:|
| **MedGemma Q4_K_M** | 2.3 GB | PAD (install-time) | ✅ Always |
| **HeAR Event Detector (TFLite INT8)** | 1.1 MB | APK assets | ✅ Always |
| **HeAR ViT-L Encoder** | ~1.2 GB | ❌ Not shipped | ❌ XLA/StableHLO conversion blocked |
| **ML Kit (59 languages)** | ~30 MB/lang | Auto-download | ✅ After download |
| **Cloud translation extension** | 0 MB | Optional backend integration | ❌ Not wired in current mobile build |

**Key point**: MedGemma and the HeAR Event Detector ship with the app via AAB/PAD. For direct APK reviewer installs without PAD, the app supports two recovery paths: trusted sideloading or native first-run model download.

### Delivery Mode: `install-time`
- MedGemma downloads **with the app** — no separate download step.
- Users on Play Store see total size upfront (~2.3GB + base app).
- No additional user action needed in the app for PAD deployments.
- HeAR Event Detector (1.1MB) is included in the base APK assets.
- ML Kit language packs auto-download based on user's selected language.

### Why This Works for Target Users
- **CHWs download once** from Play Store (via clinic Wi-Fi) — no technical setup.
- **No ADB, no sideloading** — standard consumer app behavior.
- **Offline clinical inference** — MedGemma + HeAR Event Detector persist on device, zero cloud dependency.
- **Auto-updates** — Play Store handles model version updates transparently.

### HeAR ViT-L Encoder — Future Upgrade Path
The ViT-L encoder is architecturally supported but cannot be shipped:

| Conversion Attempt | Result | Root Cause |
|:-------------------|:-------|:-----------|
| tf2onnx CLI (4 CI runs) | `ValueError: attribute type` | XlaCallModule ops have no ONNX equivalent |
| TFLite INT8 (local) | Segfault | StableHLO bytecode → FlatBuffer crash |
| TFLite StableHLO-only (local) | Segfault | `Identity` node with `Truncate` attr |

Once Google's AI Edge toolchain supports StableHLO-to-TFLite conversion, the ViT-L upgrade path in the codebase will activate via Play Asset Delivery (install-time or fast-follow).

## Size Optimization Roadmap

| Strategy | Savings | Status |
|----------|---------|--------|
| Q4_K_M quantization (from 8GB) | ~3.5x from FP16 | ✅ Done (56% MedQA quantized, 81% of 69% unquantized) |
| ML Kit replaces TranslateGemma | ~2GB saved | ✅ Done |
| HeAR Event Detector INT8 | Smallest possible (1.1MB) | ✅ Done |
| HeAR ViT-L INT8 quantization | Blocked | ❌ XLA/StableHLO conversion required first |
| Future: Q3_K_M quantization | ~350MB more | Requires accuracy validation |
| Future: Distillation to 2B params | ~4x | Requires retraining |
