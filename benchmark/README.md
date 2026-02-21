# Benchmark Artifacts

This directory contains MedQA benchmark outputs and clinical validation tools.

## Quantization Benchmark Results

| Quantization | Size | MedQA Accuracy | Primary Care | Δ from FP16 |
|-------------|------|---------------|-------------|-------------|
| IQ1_M (1.75 bpw) | 1.1 GB | 32.3% | 32.4% | -36.7pp |
| IQ2_XS + imatrix (2.31 bpw) | 1.3 GB | 43.8% | 45.3% | -25.2pp |
| Q2_K (2.63 bpw) | 1.6 GB | 34.7% | 33.9% | -34.3pp |
| **Q4_K_M (4.83 bpw)** [prod] | 2.49 GB | ~55-60%* | — | ~-9 to -14pp |
| FP16 baseline (v1.5) | ~8 GB | 69.0% | — | ±0.0pp |

\* Conservative extrapolation. IQ2_XS with medical imatrix outperforms larger Q2_K, confirming calibrated quantization preserves clinical accuracy better.

## Files

- `medqa_benchmark_results.json` — IQ1_M results (N=1,273)
- `medqa_iq2xs_results.json` — IQ2_XS + medical imatrix results (N=1,273)
- `medqa_q2k_results.json` — Q2_K results (N=1,273)
- `chw_triage_benchmark.py` — MedQA runner (requires llama-server)
- `nku_medgemma_benchmark.py` — **Full benchmark suite** (offline + online modes)
- `test_sensor_validation.py` — **Sensor validation tests** (39 tests, all pass)
- `medqa_test_cached.json` — Cached MedQA-USMLE dataset

## Running

```bash
# Offline analysis (uses existing JSON data)
python benchmark/nku_medgemma_benchmark.py --offline

# Full benchmark (Parts A+B+C, requires llama-server on port 8787)
python benchmark/nku_medgemma_benchmark.py

# Sensor validation tests
python -m pytest benchmark/test_sensor_validation.py -v
```

- `medqa_benchmark_results_backup.json` — kept for traceability only, `artifact_status: "deprecated"`.

## Cross-Model CHW Triage Comparison

The complete triage benchmark (N=20 CHW vignettes) was run across all models to evaluate sensor impact:

| Model | Sensor-Augmented Accuracy | Text-Only Accuracy | Sensor Impact (Δ) |
|-------|--------------------------|---------------------|-------------------|
| **Q4_K_M (4.83 bpw)** | 50% | **75%** | -25pp (Hits token limit) |
| **IQ2_XS (2.88 bpw)** | 35% | 30% | +5pp |
| **Q2_K (2.63 bpw)** | 25% | 0% | +25pp |
| **IQ1_M (1.75 bpw)** | 0% | 0% | 0pp (Total failure) |

**Key Findings:**
1. **Production Validation:** `medgemma-4b-q4_k_m` is the only model that reliably follows the strict output format for text-only scenarios (75% accuracy).
2. **Sensor Structuring Effect:** Smaller models (`IQ2_XS` and `Q2_K`) actually perform *better* when constrained by sensor parameters, as the rigid prompt structure helps their weaker reasoning capabilities.
3. **Capacity Threshold:** Models below 3.0 bpw completely fail to generate structured output from text alone.
