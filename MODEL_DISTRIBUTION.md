# Nku Sentinel — Model Distribution Strategy

## Current State
MedGemma 4B is quantized to Q4_K_M format (~2.3GB). The HeAR ViT-L encoder is INT8 quantized (~300MB). Both are delivered via Play Asset Delivery. Translation is handled by Android ML Kit (on-device, ~30MB/language pack) with Google Cloud Translate API fallback for indigenous African languages.

For development/testing, models can also be sideloaded to `/sdcard/Download/` via ADB push.

## Production Distribution: Play Asset Delivery (PAD)

All inference models are delivered via [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), ensuring **100% offline capability from first launch**.

### Architecture

```
AAB (Android App Bundle)
├── base module (~8MB)          ← app code, UI, HeAR Event Detector (1.1MB TFLite)
├── asset-pack: medgemma        ← MedGemma Q4_K_M GGUF (~2.3GB)
│   └── delivery: install-time  ← downloaded with initial install
├── asset-pack: hear_encoder    ← HeAR ViT-L Encoder ONNX INT8 (~300MB)
│   └── delivery: install-time  ← downloaded with initial install
└── ML Kit language packs       ← ~30MB each, downloaded on demand
```

### Model Inventory

| Component | Size | Delivery | Offline |
|:----------|:----:|:--------:|:-------:|
| **MedGemma Q4_K_M** | 2.3 GB | PAD (install-time) | ✅ Always |
| **HeAR ViT-L Encoder (ONNX INT8)** | ~300 MB | PAD (install-time) | ✅ Always |
| **HeAR Event Detector (TFLite INT8)** | 1.1 MB | APK assets | ✅ Always |
| **ML Kit (59 languages)** | ~30 MB/lang | Auto-download | ✅ After download |
| **Cloud Translate fallback** | 0 MB | API call | ❌ Requires internet |

**Key point**: All inference models (MedGemma, HeAR Event Detector, HeAR ViT-L encoder) ship with the app. A CHW downloads once from Play Store and has full offline clinical inference immediately. Cloud Translate is only needed for indigenous/local languages (Twi, Hausa, Yoruba, Igbo).

### Delivery Mode: `install-time`
- Both MedGemma and HeAR ViT-L encoder download **with the app** — no separate download step.
- Users on Play Store see total size upfront (~2.6GB + base app).
- No additional code needed in the app to request downloads.
- ML Kit language packs auto-download based on user's selected language.

### Why This Works for Target Users
- **CHWs download once** from Play Store (via clinic Wi-Fi) — no technical setup.
- **No ADB, no sideloading** — standard consumer app behavior.
- **100% offline inference** — MedGemma + HeAR encoder persist on device, zero cloud dependency for clinical reasoning.
- **Auto-updates** — Play Store handles model version updates transparently.

### Alternative: Fast-follow Delivery
If total install size is a concern, HeAR ViT-L encoder can use `fast-follow` delivery mode:
- App installs with MedGemma immediately (~2.3GB + base).
- HeAR encoder downloads in background automatically (~300MB).
- Respiratory screening shows loading UI until encoder is available; camera-based screenings and MedGemma triage work immediately.

This is **not recommended** for MedGemma because it creates a window where the app can't perform AI triage. HeAR encoder fast-follow is acceptable because respiratory screening is CHW-initiated (not always-on).

## Size Optimization Roadmap

| Strategy | Savings | Status |
|----------|---------|--------|
| Q4_K_M quantization (from 8GB) | ~3.5x from FP16 | ✅ Done (56% MedQA quantized, 81% of 69% unquantized) |
| ML Kit replaces TranslateGemma | ~2GB saved | ✅ Done |
| HeAR ViT-L INT8 quantization | ~4x from FP32 | ✅ Done |
| Future: Q3_K_M quantization | ~350MB more | Requires accuracy validation |
| Future: Distillation to 2B params | ~4x | Requires retraining |
