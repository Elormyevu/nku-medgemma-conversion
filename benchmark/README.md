# Benchmark Artifacts

This directory contains MedQA benchmark outputs for quantized MedGemma variants.

Canonical artifacts for submission evidence:

- `benchmark/medqa_benchmark_results.json` (IQ1_M)
- `benchmark/medqa_iq2xs_results.json` (IQ2_XS + medical imatrix)
- `benchmark/medqa_q2k_results.json` (Q2_K)

Deprecated artifact:

- `benchmark/medqa_benchmark_results_backup.json` is kept only for traceability and is marked `artifact_status: "deprecated"`.
- Do **not** use it for reported metrics or model-quality claims.
