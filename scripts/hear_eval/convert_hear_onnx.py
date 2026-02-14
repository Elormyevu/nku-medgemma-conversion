#!/usr/bin/env python3
"""
HeAR ViT-L Encoder — Convert to ONNX + INT8 Quantize for On-Device Use.

This script:
1. Downloads HeAR from HuggingFace (google/hear TF SavedModel)
2. Converts the full ViT-L encoder to ONNX format (opset 17)
3. Applies INT8 dynamic range quantization via onnxruntime
4. Validates: input [1, 32000] float32 → output [1, 512] float32
5. Reports file sizes, latency, and cosine similarity FP32 vs INT8

Output:
    models/hear/hear_encoder_fp32.onnx  (~1.2GB)
    models/hear/hear_encoder_int8.onnx  (~300MB)

Prerequisites:
    pip install tensorflow tf2onnx onnxruntime onnx numpy huggingface_hub

Usage:
    python3 scripts/hear_eval/convert_hear_onnx.py

The INT8 model (hear_encoder_int8.onnx) is placed in the hear_encoder PAD
asset pack for on-device inference via ONNX Runtime Mobile in the Nku Android app.
"""

import os
import sys
import time
import json
import subprocess
import numpy as np

# ── Configuration ─────────────────────────────────────────────

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "models", "hear")
RESULTS_FILE = os.path.join(OUTPUT_DIR, "onnx_conversion_results.json")
SAMPLE_RATE = 16000
CLIP_DURATION_S = 2
CLIP_SAMPLES = SAMPLE_RATE * CLIP_DURATION_S  # 32000
EMBEDDING_DIM = 512

os.makedirs(OUTPUT_DIR, exist_ok=True)


def log(msg: str):
    print(f"[HeAR-ONNX] {msg}", flush=True)


# ── Step 1: Download HeAR ──────────────────────────────────────

def download_hear() -> str:
    """Download HeAR from HuggingFace and return SavedModel path."""
    from huggingface_hub import snapshot_download
    
    log("Downloading HeAR from HuggingFace (google/hear)...")
    model_dir = snapshot_download("google/hear")
    log(f"Model downloaded to: {model_dir}")
    
    return model_dir


# ── Step 2: Validate TF SavedModel ────────────────────────────

def validate_saved_model(saved_model_dir: str):
    """Verify the SavedModel produces correct 512-dim embeddings."""
    import tensorflow as tf
    
    log("Validating TF SavedModel...")
    model = tf.saved_model.load(saved_model_dir)
    
    # Test with random audio
    test_audio = np.random.normal(size=(1, CLIP_SAMPLES)).astype(np.float32)
    
    if hasattr(model, 'signatures') and 'serving_default' in model.signatures:
        serving_fn = model.signatures['serving_default']
        log(f"Serving signature inputs: {serving_fn.structured_input_signature}")
        log(f"Serving signature outputs: {serving_fn.structured_outputs}")
        result = serving_fn(x=tf.constant(test_audio))
        embedding = list(result.values())[0].numpy()
    elif hasattr(model, '__call__'):
        embedding = model(tf.constant(test_audio)).numpy()
    else:
        raise RuntimeError("Cannot determine how to call the HeAR model")
    
    log(f"Test embedding shape: {embedding.shape}")
    assert embedding.shape[-1] == EMBEDDING_DIM, \
        f"Expected {EMBEDDING_DIM}-dim embedding, got {embedding.shape[-1]}"
    log(f"✅ SavedModel validated: input [1, {CLIP_SAMPLES}] → output {embedding.shape}")
    
    return model


# ── Step 3: Convert to ONNX ───────────────────────────────────

def convert_to_onnx_fp32(saved_model_dir: str) -> str:
    """Convert TF SavedModel to ONNX FP32 using tf2onnx."""
    output_path = os.path.join(OUTPUT_DIR, "hear_encoder_fp32.onnx")
    
    if os.path.exists(output_path):
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"FP32 ONNX already exists: {output_path} ({size_mb:.1f} MB)")
        return output_path
    
    log("Converting SavedModel → ONNX FP32 (opset 17)...")
    log("This may take 5-10 minutes for the ViT-L encoder...")
    
    result = subprocess.run([
        sys.executable, "-m", "tf2onnx.convert",
        "--saved-model", saved_model_dir,
        "--output", output_path,
        "--opset", "17",
        "--tag", "serve",
        "--signature_def", "serving_default"
    ], capture_output=True, text=True, timeout=1200)
    
    if result.returncode != 0:
        log(f"❌ ONNX conversion failed:")
        log(result.stderr[-1000:] if result.stderr else "No stderr")
        raise RuntimeError("tf2onnx conversion failed")
    
    size_mb = os.path.getsize(output_path) / 1e6
    log(f"✅ FP32 ONNX: {output_path} ({size_mb:.1f} MB)")
    return output_path


# ── Step 4: Quantize to INT8 ──────────────────────────────────

def quantize_to_int8(fp32_path: str) -> str:
    """Apply INT8 dynamic range quantization via onnxruntime."""
    from onnxruntime.quantization import quantize_dynamic, QuantType
    
    output_path = os.path.join(OUTPUT_DIR, "hear_encoder_int8.onnx")
    
    if os.path.exists(output_path):
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"INT8 ONNX already exists: {output_path} ({size_mb:.1f} MB)")
        return output_path
    
    log("Quantizing to INT8 (dynamic range)...")
    quantize_dynamic(
        model_input=fp32_path,
        model_output=output_path,
        weight_type=QuantType.QInt8,
        optimize_model=True
    )
    
    size_mb = os.path.getsize(output_path) / 1e6
    log(f"✅ INT8 ONNX: {output_path} ({size_mb:.1f} MB)")
    return output_path


# ── Step 5: Validate & Benchmark ONNX Models ─────────────────

def benchmark_onnx(onnx_path: str, label: str, n_test: int = 20) -> dict:
    """Benchmark ONNX model for correctness and performance."""
    import onnxruntime as ort
    
    if onnx_path is None or not os.path.exists(onnx_path):
        return None
    
    size_mb = os.path.getsize(onnx_path) / 1e6
    log(f"\n{'─'*50}")
    log(f"Benchmarking: {label} ({size_mb:.1f} MB)")
    log(f"{'─'*50}")
    
    session = ort.InferenceSession(onnx_path)
    input_name = session.get_inputs()[0].name
    input_shape = session.get_inputs()[0].shape
    output_name = session.get_outputs()[0].name
    output_shape = session.get_outputs()[0].shape
    
    log(f"  Input: {input_name} {input_shape}")
    log(f"  Output: {output_name} {output_shape}")
    
    np.random.seed(42)
    embeddings = []
    latencies = []
    
    for i in range(n_test):
        # Generate varied test audio
        if i % 3 == 0:
            audio = np.random.normal(0, 0.01, (1, CLIP_SAMPLES)).astype(np.float32)  # silence
        elif i % 3 == 1:
            audio = np.random.normal(0, 0.1, (1, CLIP_SAMPLES)).astype(np.float32)   # noise
        else:
            # Cough-like burst
            audio = np.random.normal(0, 0.02, (1, CLIP_SAMPLES)).astype(np.float32)
            burst_start = np.random.randint(0, CLIP_SAMPLES - 4000)
            audio[0, burst_start:burst_start + 4000] = np.random.normal(0, 0.5, 4000).astype(np.float32)
        
        start_time = time.perf_counter()
        result = session.run([output_name], {input_name: audio})
        elapsed_ms = (time.perf_counter() - start_time) * 1000
        
        embeddings.append(result[0][0])
        latencies.append(elapsed_ms)
    
    embeddings = np.array(embeddings)
    
    # Validate embedding dimension
    assert embeddings.shape[-1] == EMBEDDING_DIM, \
        f"Expected {EMBEDDING_DIM}-dim, got {embeddings.shape[-1]}"
    
    # Stats
    mean_latency = np.mean(latencies)
    p95_latency = np.percentile(latencies, 95)
    
    log(f"  Embedding shape: {embeddings.shape}")
    log(f"  Latency: mean={mean_latency:.1f}ms, p95={p95_latency:.1f}ms")
    log(f"  Embedding stats: mean={embeddings.mean():.4f}, std={embeddings.std():.4f}")
    log(f"  Embedding range: [{embeddings.min():.4f}, {embeddings.max():.4f}]")
    log(f"  L2 norms: mean={np.linalg.norm(embeddings, axis=1).mean():.2f}")
    
    return {
        "label": label,
        "path": onnx_path,
        "size_mb": round(size_mb, 1),
        "mean_latency_ms": round(mean_latency, 1),
        "p95_latency_ms": round(p95_latency, 1),
        "embedding_dim": int(embeddings.shape[-1]),
        "embedding_mean": round(float(embeddings.mean()), 6),
        "embedding_std": round(float(embeddings.std()), 6),
        "embeddings": embeddings  # For cosine sim comparison
    }


def compare_embeddings(fp32_result: dict, int8_result: dict):
    """Compute cosine similarity between FP32 and INT8 embeddings."""
    if fp32_result is None or int8_result is None:
        return None
    
    fp32_emb = fp32_result["embeddings"]
    int8_emb = int8_result["embeddings"]
    
    # Cosine similarity
    sims = []
    for a, b in zip(fp32_emb, int8_emb):
        cos = np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-8)
        sims.append(cos)
    
    mean_sim = float(np.mean(sims))
    min_sim = float(np.min(sims))
    
    log(f"\n  Cosine similarity FP32 vs INT8:")
    log(f"    Mean: {mean_sim:.6f}")
    log(f"    Min:  {min_sim:.6f}")
    
    return mean_sim


# ── Main Pipeline ─────────────────────────────────────────────

def main():
    log("=" * 60)
    log("HeAR ViT-L Encoder — ONNX Conversion Pipeline")
    log("=" * 60)
    
    # Step 1: Download
    saved_model_dir = download_hear()
    
    # Step 2: Validate  
    validate_saved_model(saved_model_dir)
    
    # Step 3: Convert to ONNX FP32
    fp32_path = convert_to_onnx_fp32(saved_model_dir)
    
    # Step 4: Quantize to INT8
    int8_path = quantize_to_int8(fp32_path)
    
    # Step 5: Benchmark both
    log("\n" + "=" * 60)
    log("BENCHMARKING")
    log("=" * 60)
    
    fp32_result = benchmark_onnx(fp32_path, "FP32 (baseline)")
    int8_result = benchmark_onnx(int8_path, "INT8 (dynamic range)")
    
    # Step 6: Compare
    cosine_sim = compare_embeddings(fp32_result, int8_result)
    
    # Results summary
    log("\n" + "=" * 60)
    log("RESULTS SUMMARY")
    log("=" * 60)
    log(f"{'Variant':<25} {'Size (MB)':>10} {'Latency (ms)':>13} {'Cosine Sim':>12}")
    log("─" * 60)
    
    results = []
    for r in [fp32_result, int8_result]:
        if r:
            r_clean = {k: v for k, v in r.items() if k != 'embeddings'}
            if r == int8_result and cosine_sim is not None:
                r_clean['cosine_sim_vs_fp32'] = round(cosine_sim, 6)
            cos_str = f"{cosine_sim:.6f}" if (r == int8_result and cosine_sim) else "baseline"
            log(f"{r['label']:<25} {r['size_mb']:>10.1f} {r['mean_latency_ms']:>13.1f} {cos_str:>12}")
            results.append(r_clean)
    
    # Save results
    with open(RESULTS_FILE, 'w') as f:
        json.dump(results, f, indent=2)
    log(f"\nResults saved to: {RESULTS_FILE}")
    
    # Recommendation
    if cosine_sim and cosine_sim > 0.95:
        log(f"\n✅ RECOMMENDED FOR ON-DEVICE: INT8 ({int8_result['size_mb']} MB)")
        log(f"   Cosine similarity: {cosine_sim:.6f} (excellent quality preservation)")
        log(f"   Deploy as: hear_encoder_int8.onnx → device storage")
    elif cosine_sim and cosine_sim > 0.90:
        log(f"\n⚠ INT8 quality acceptable but degraded (cosine sim: {cosine_sim:.6f})")
        log(f"   Consider FP32 if accuracy is critical.")
    else:
        log(f"\n❌ INT8 quality too low — use FP32 for deployment")
    
    log("\nDone!")


if __name__ == "__main__":
    main()
