# Nku Sentinel — Model Distribution Strategy

## Current State
MedGemma 4B is quantized to Q4_K_M format (~2.3GB). Translation is handled by Android ML Kit (on-device, ~30MB/language pack) with Google Cloud Translate API fallback for indigenous African languages.

Currently MedGemma is loaded from device storage at `/sdcard/Download/` via manual ADB push — this is a **development-only** workflow.

## Production Distribution: Play Asset Delivery (PAD)

For Google Play Store release, MedGemma is delivered via [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), which supports assets up to **2GB per pack**.

### Architecture

```
AAB (Android App Bundle)
├── base module (~8MB)          ← app code, UI, ML heuristics, ML Kit
├── asset-pack: medgemma        ← MedGemma Q4_K_M GGUF (~2.3GB)
│   └── delivery: on-install    ← downloaded with initial install
└── ML Kit language packs       ← ~30MB each, downloaded on demand
```

### Translation Strategy

| Component | Size | Delivery | Offline |
|:----------|:----:|:--------:|:-------:|
| **MedGemma Q4_K_M** | 2.3 GB | PAD (install-time) | ✅ Always |
| **ML Kit (59 languages)** | ~30 MB/lang | Auto-download | ✅ After download |
| **Cloud Translate fallback** | 0 MB | API call | ❌ Requires internet |

**Key point**: All African official languages (English, French, Portuguese) are fully on-device via ML Kit. CHWs trained in official languages always have a fully offline path. Cloud translation is only needed for indigenous/local languages (Twi, Hausa, Yoruba, Igbo).

### Delivery Mode: `install-time`
- MedGemma downloads **with the app** — no separate download step.
- Users on Play Store see total size upfront (~2.3GB + base app).
- No additional code needed in the app to request downloads.
- ML Kit language packs auto-download based on user's selected language.

### Why This Works for Target Users
- **CHWs download once** from Play Store (via clinic Wi-Fi) — no technical setup.
- **No ADB, no sideloading** — standard consumer app behavior.
- **Offline medical inference** — MedGemma persists on device, zero cloud dependency for clinical reasoning.
- **Auto-updates** — Play Store handles model version updates transparently.

### Alternative: Fast-follow Delivery
If total install size is a concern, MedGemma can use `fast-follow` delivery mode:
- App installs immediately (~8MB).
- MedGemma downloads in background automatically.
- App shows loading UI until model is available.

This is **not recommended** because it creates a window where the app can't perform AI triage, which is confusing for CHWs in the field.

## Size Optimization Roadmap

| Strategy | Savings | Status |
|----------|---------|--------|
| Q4_K_M quantization (from 8GB) | ~3.5x from FP16 | ✅ Done (56% MedQA) |
| ML Kit replaces TranslateGemma | ~2GB saved | ✅ Done |
| Future: Q3_K_M quantization | ~350MB more | Requires accuracy validation |
| Future: Distillation to 2B params | ~4x | Requires retraining |
