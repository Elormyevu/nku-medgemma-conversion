# Nku Sentinel — Model Distribution Strategy

## Current State
MedGemma 4B and TranslateGemma 4B are quantized to IQ1_M format (~780MB each).
Currently loaded from device storage at `/sdcard/Download/` via manual ADB push — this is a **development-only** workflow.

## Production Distribution: Play Asset Delivery (PAD)

For Google Play Store release, models are delivered via [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), which supports assets up to **2GB per pack**.

### Architecture

```
AAB (Android App Bundle)
├── base module (~8MB)          ← app code, UI, ML heuristics
├── asset-pack: medgemma        ← MedGemma IQ1_M GGUF (~780MB)
│   └── delivery: on-install    ← downloaded with initial install
└── asset-pack: translategemma  ← TranslateGemma IQ1_M GGUF (~780MB)
    └── delivery: on-install    ← downloaded with initial install
```

### Delivery Mode: `install-time`
- Models download **with the app** — no separate download step.
- Users on Play Store see total size upfront (~1.6GB).
- No additional code needed in the app to request downloads.
- Models available immediately via `context.assets.open()`.

### Implementation Steps
1. Create `medgemma/` and `translategemma/` asset pack directories.
2. Add `build.gradle` for each with `assetPack { packName = "..." }`.
3. Reference in app `build.gradle`: `assetPacks = [":medgemma", ":translategemma"]`.
4. Update `NkuInferenceEngine.kt` to load from asset packs instead of `/sdcard/`.
5. Build as AAB: `./gradlew bundleRelease`.

### Why This Works for Target Users
- **CHWs download once** from Play Store (via clinic Wi-Fi) — no technical setup.
- **No ADB, no sideloading** — standard consumer app behavior.
- **Offline after install** — models persist on device, zero cloud dependency.
- **Auto-updates** — Play Store handles model version updates transparently.

### Alternative: Fast-follow Delivery
If total install size is a concern, models can use `fast-follow` delivery mode:
- App installs immediately (~8MB).
- Models download in background automatically.
- App shows loading UI until models are available.

This is **not recommended** because it creates a window where the app can't perform AI triage, which is confusing for CHWs in the field.

## Size Optimization Roadmap

| Strategy | Savings | Status |
|----------|---------|--------|
| IQ1_M quantization | ~10x from FP16 | ✅ Done |
| Single model (MedGemma only, skip TranslateGemma) | ~780MB | Possible with system TTS |
| Future: IQ1_S quantization | ~15% more | Requires accuracy validation |
| Future: Distillation to 2B params | ~4x | Requires retraining |
