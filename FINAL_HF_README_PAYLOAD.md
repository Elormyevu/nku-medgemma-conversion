---
license: gemma
base_model: google/medgemma-1.5-4b-it
tags:
- medical
- gguf
- quantized
- llama.cpp
- african-healthcare
---

# MedGemma 4B GGUF - Quantized for African Healthcare

Quantized versions of [google/medgemma-1.5-4b-it](https://huggingface.co/google/medgemma-1.5-4b-it) optimized for on-device medical AI in resource-constrained settings.

## Available Models

| File | Quantization | Size | RAM | MedQA (vs Unquantized) | Use Case |
|------|--------------|------|-----|------------------------|----------|
| `medgemma-4b-iq2_xs.gguf` | IQ2_XS (2-bit) + Medical imatrix | **~0.85GB** | ~1.9GB | 43.8% (63% retained) | Ultra-budget phones |
| `medgemma-4b-q2_k.gguf` | Q2_K (2-bit) | ~1.3GB | ~2.3GB | 34.7% (50% retained) | Budget phones |
| `medgemma-4b-q4_k_m.gguf` | Q4_K_M (4-bit) | ~2.3GB | ~3GB | **56.0% (81% retained)** | Budget phones |

## Medical Importance Matrix

The IQ2_XS model was quantized using a custom importance matrix (imatrix) calibrated on:
- **African primary care scenarios** (malaria, typhoid, cholera, respiratory infections)
- **Maternal and child health** (pregnancy complications, childhood diarrhea, nutrition)
- **Emergency triage** (snake bites, severe dehydration, trauma)
- **Multi-language symptoms** (Twi, Hausa, Yoruba, English)

This preserves medical diagnostic accuracy while aggressively compressing general knowledge.

## Usage with llama.cpp

```bash
./llama-cli -m medgemma-4b-iq2_xs.gguf -p "Patient has fever, chills, and headache for 3 days. What could this be?"
```

## License

Subject to [Gemma Terms of Use](https://ai.google.dev/gemma/terms).

## Part of the Nku Project

Built for the [Google MedGemma Impact Challenge](https://ai.google.dev/gemma/docs/medgemma) - bringing AI-powered healthcare to underserved African communities.
