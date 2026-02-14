# Nku Sentinel — Model Distribution Strategy

## Current State
MedGemma 4B is quantized to Q4_K_M format (~2.3GB) and delivered via Play Asset Delivery. The HeAR Event Detector (1.1MB INT8 TFLite) ships in the APK's `assets/` directory. Translation is handled by Android ML Kit (on-device, ~30MB/language pack) with Google Cloud Translate API fallback for indigenous African languages.

> **HeAR ViT-L Encoder — not shipped**: The HeAR ViT-L encoder (~1.2GB FP32) uses `XlaCallModule` nodes with serialized StableHLO bytecode — an XLA-specific format that no current conversion tool (tf2onnx, TFLite converter) can process into ONNX or TFLite. We attempted 7 conversion approaches across 3 toolchains (tf2onnx CLI, TFLite INT8, TFLite StableHLO-only) before documenting this as a technical limitation. The codebase includes full architectural support for the ViT-L encoder as a documented upgrade path.

For development/testing, models can also be sideloaded to `/sdcard/Download/` via ADB push.

## Production Distribution: Play Asset Delivery (PAD)

MedGemma is delivered via [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), ensuring offline capability from first launch. The HeAR Event Detector ships in the APK itself.

### Architecture

```
AAB (Android App Bundle)
├── base module (~8MB)          ← app code, UI, HeAR Event Detector (1.1MB TFLite in assets/)
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
| **Cloud Translate fallback** | 0 MB | API call | ❌ Requires internet |

**Key point**: MedGemma and the HeAR Event Detector ship with the app. A CHW downloads once from Play Store and has offline clinical triage + respiratory screening immediately. Cloud Translate is only needed for indigenous/local languages (Twi, Hausa, Yoruba, Igbo).

### Delivery Mode: `install-time`
- MedGemma downloads **with the app** — no separate download step.
- Users on Play Store see total size upfront (~2.3GB + base app).
- No additional code needed in the app to request downloads.
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
