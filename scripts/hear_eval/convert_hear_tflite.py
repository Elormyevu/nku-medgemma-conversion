#!/usr/bin/env python3
"""
HeAR Model — Download, Convert to TFLite (FP32/INT8/INT4), and Evaluate.

This script:
1. Downloads HeAR from HuggingFace (Keras SavedModel)
2. Converts to TFLite at three quantization levels
3. Validates each variant produces sensible embeddings
4. Reports size, inference time, and embedding quality metrics

Usage:
    python3 scripts/hear_eval/convert_hear_tflite.py

Output:
    models/hear/hear_fp32.tflite    (~1.2GB)
    models/hear/hear_int8.tflite    (~300MB)
    models/hear/hear_int4.tflite    (~150MB)  [experimental]
"""

import os
import sys
import time
import json
import numpy as np

# ── Configuration ─────────────────────────────────────────────

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "models", "hear")
RESULTS_FILE = os.path.join(OUTPUT_DIR, "conversion_results.json")
SAMPLE_RATE = 16000
CLIP_DURATION_S = 2
CLIP_SAMPLES = SAMPLE_RATE * CLIP_DURATION_S  # 32000

os.makedirs(OUTPUT_DIR, exist_ok=True)


def log(msg: str):
    print(f"[HeAR] {msg}", flush=True)


# ── Step 1: Download HeAR from HuggingFace ────────────────────

def download_hear():
    """Download HeAR model from HuggingFace and load as TF SavedModel.
    
    HeAR is a legacy TF SavedModel — Keras 3's load_model() doesn't support it.
    We use snapshot_download + tf.saved_model.load() instead.
    """
    import tensorflow as tf
    from huggingface_hub import snapshot_download
    
    # Download model files to HF cache
    log("Downloading HeAR from HuggingFace (google/hear)...")
    model_dir = snapshot_download("google/hear")
    log(f"Model downloaded to: {model_dir}")
    
    # Load as TF SavedModel (legacy format)
    log("Loading HeAR as TF SavedModel...")
    model = tf.saved_model.load(model_dir)
    log(f"Model type: {type(model)}")
    
    # Inspect signatures
    if hasattr(model, 'signatures'):
        sig_keys = list(model.signatures.keys())
        log(f"Available signatures: {sig_keys}")
        serving_fn = model.signatures.get('serving_default')
        if serving_fn:
            log(f"Serving signature inputs: {serving_fn.structured_input_signature}")
            log(f"Serving signature outputs: {serving_fn.structured_outputs}")
    
    # Test with random audio
    test_audio = np.random.normal(size=(1, CLIP_SAMPLES)).astype(np.float32)
    
    if hasattr(model, 'signatures') and 'serving_default' in model.signatures:
        result = model.signatures['serving_default'](x=tf.constant(test_audio))
        embedding = list(result.values())[0].numpy()
    elif hasattr(model, '__call__'):
        embedding = model(tf.constant(test_audio)).numpy()
    else:
        raise RuntimeError("Cannot determine how to call the HeAR model")
    
    log(f"Test embedding shape: {embedding.shape}")
    log(f"Embedding dim: {embedding.shape[-1]}")
    assert embedding.shape[-1] == 512, f"Expected 512-dim embedding, got {embedding.shape[-1]}"
    
    # Store the model_dir for TFLite conversion (we already have a SavedModel!)
    model._saved_model_dir = model_dir
    
    return model


# ── Step 2: Export to SavedModel ──────────────────────────────

def export_saved_model(model):
    """Get SavedModel path — HeAR from HuggingFace is already in SavedModel format."""
    import tensorflow as tf
    
    # HeAR from HF is already a SavedModel — just use the cached path
    if hasattr(model, '_saved_model_dir'):
        saved_model_dir = model._saved_model_dir
        log(f"Using existing SavedModel from HF cache: {saved_model_dir}")
        return saved_model_dir
    
    # Fallback: export to our output dir
    saved_model_dir = os.path.join(OUTPUT_DIR, "hear_saved_model")
    if os.path.exists(saved_model_dir):
        log(f"SavedModel already exists at {saved_model_dir}, reusing...")
        return saved_model_dir
    
    log("Exporting to SavedModel format...")
    tf.saved_model.save(model, saved_model_dir)
    log(f"SavedModel saved to: {saved_model_dir}")
    
    return saved_model_dir


# ── Step 3: Convert to TFLite ─────────────────────────────────

def convert_tflite_fp32(saved_model_dir: str) -> str:
    """Convert to FP32 TFLite (baseline)."""
    import tensorflow as tf
    
    output_path = os.path.join(OUTPUT_DIR, "hear_fp32.tflite")
    if os.path.exists(output_path):
        log(f"FP32 TFLite already exists ({os.path.getsize(output_path) / 1e6:.1f} MB)")
        return output_path
    
    log("Converting to TFLite FP32...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    # HeAR uses SimpleRNN (PCEN layer) with dynamic TensorList ops
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    converter._experimental_lower_tensor_list_ops = False
    tflite_model = converter.convert()
    
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(output_path) / 1e6
    log(f"FP32 TFLite: {size_mb:.1f} MB → {output_path}")
    return output_path


def convert_tflite_int8(saved_model_dir: str) -> str:
    """Convert to INT8 dynamic range quantization."""
    import tensorflow as tf
    
    output_path = os.path.join(OUTPUT_DIR, "hear_int8.tflite")
    if os.path.exists(output_path):
        log(f"INT8 TFLite already exists ({os.path.getsize(output_path) / 1e6:.1f} MB)")
        return output_path
    
    log("Converting to TFLite INT8 (dynamic range PTQ)...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    converter._experimental_lower_tensor_list_ops = False
    tflite_model = converter.convert()
    
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(output_path) / 1e6
    log(f"INT8 TFLite: {size_mb:.1f} MB → {output_path}")
    return output_path


def convert_tflite_int8_full(saved_model_dir: str) -> str:
    """Convert to INT8 full integer quantization with representative dataset."""
    import tensorflow as tf
    
    output_path = os.path.join(OUTPUT_DIR, "hear_int8_full.tflite")
    if os.path.exists(output_path):
        log(f"INT8-full TFLite already exists ({os.path.getsize(output_path) / 1e6:.1f} MB)")
        return output_path
    
    log("Converting to TFLite INT8 (full integer, representative dataset)...")
    
    def representative_dataset():
        """Generate representative audio samples for calibration."""
        for _ in range(100):
            # Simulate varied audio: silence, noise, burst patterns
            choice = np.random.randint(3)
            if choice == 0:
                # Near-silence
                data = np.random.normal(0, 0.01, (1, CLIP_SAMPLES)).astype(np.float32)
            elif choice == 1:
                # Normal ambient noise
                data = np.random.normal(0, 0.1, (1, CLIP_SAMPLES)).astype(np.float32)
            else:
                # Burst pattern (cough-like)
                data = np.random.normal(0, 0.05, (1, CLIP_SAMPLES)).astype(np.float32)
                burst_start = np.random.randint(0, CLIP_SAMPLES - 4000)
                data[0, burst_start:burst_start + 4000] = np.random.normal(0, 0.5, 4000).astype(np.float32)
            yield [data]
    
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    # HeAR needs SELECT_TF_OPS for RNN/TensorList + INT8 builtins
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    converter._experimental_lower_tensor_list_ops = False
    converter.inference_input_type = tf.float32  # Keep float I/O for ease of use
    converter.inference_output_type = tf.float32
    
    try:
        tflite_model = converter.convert()
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"INT8-full TFLite: {size_mb:.1f} MB → {output_path}")
        return output_path
    except Exception as e:
        log(f"⚠ INT8-full conversion failed: {e}")
        return None


def convert_tflite_int4(saved_model_dir: str) -> str:
    """Attempt INT4 weight-only quantization (experimental)."""
    import tensorflow as tf
    
    output_path = os.path.join(OUTPUT_DIR, "hear_int4.tflite")
    if os.path.exists(output_path):
        log(f"INT4 TFLite already exists ({os.path.getsize(output_path) / 1e6:.1f} MB)")
        return output_path
    
    log("Attempting TFLite INT4 (experimental weight-only)...")
    
    try:
        converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter._experimental_lower_tensor_list_ops = False
        # Try experimental 4-bit quantization with TF ops fallback
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.EXPERIMENTAL_TFLITE_BUILTINS_ACTIVATIONS_INT16_WEIGHTS_INT4,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        
        tflite_model = converter.convert()
        
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"INT4 TFLite: {size_mb:.1f} MB → {output_path}")
        return output_path
    except Exception as e:
        log(f"⚠ INT4 conversion failed (expected for ViT): {e}")
        log("Falling back to INT8 as deployment target.")
        return None


# ── Step 4: Validate & Benchmark TFLite Models ───────────────

def benchmark_tflite(tflite_path: str, label: str, reference_embeddings=None):
    """Load TFLite model, run inference, measure quality and latency."""
    import tensorflow as tf
    
    if tflite_path is None:
        return None
    
    size_mb = os.path.getsize(tflite_path) / 1e6
    log(f"\n{'='*50}")
    log(f"Benchmarking: {label} ({size_mb:.1f} MB)")
    log(f"{'='*50}")
    
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    log(f"  Input: {input_details[0]['shape']} {input_details[0]['dtype']}")
    log(f"  Output: {output_details[0]['shape']} {output_details[0]['dtype']}")
    
    # Generate test audio (100 random 2s clips)
    n_test = 20
    test_audio = np.random.normal(size=(n_test, CLIP_SAMPLES)).astype(np.float32)
    
    embeddings = []
    latencies = []
    
    for i in range(n_test):
        audio_input = test_audio[i:i+1]
        interpreter.set_tensor(input_details[0]['index'], audio_input)
        
        start_time = time.perf_counter()
        interpreter.invoke()
        elapsed_ms = (time.perf_counter() - start_time) * 1000
        
        output = interpreter.get_tensor(output_details[0]['index'])
        embeddings.append(output[0])
        latencies.append(elapsed_ms)
    
    embeddings = np.array(embeddings)
    
    # Stats
    mean_latency = np.mean(latencies)
    p95_latency = np.percentile(latencies, 95)
    
    log(f"  Embedding shape: {embeddings.shape}")
    log(f"  Latency: mean={mean_latency:.1f}ms, p95={p95_latency:.1f}ms")
    log(f"  Embedding stats: mean={embeddings.mean():.4f}, std={embeddings.std():.4f}")
    log(f"  Embedding range: [{embeddings.min():.4f}, {embeddings.max():.4f}]")
    
    # Cosine similarity with FP32 reference
    cosine_sim = None
    if reference_embeddings is not None:
        from sklearn.metrics.pairwise import cosine_similarity
        sims = [cosine_similarity([a], [b])[0][0] 
                for a, b in zip(reference_embeddings, embeddings)]
        cosine_sim = float(np.mean(sims))
        log(f"  Cosine sim vs FP32: {cosine_sim:.6f}")
    
    return {
        "label": label,
        "path": tflite_path,
        "size_mb": round(size_mb, 1),
        "mean_latency_ms": round(mean_latency, 1),
        "p95_latency_ms": round(p95_latency, 1),
        "embedding_mean": round(float(embeddings.mean()), 6),
        "embedding_std": round(float(embeddings.std()), 6),
        "cosine_sim_vs_fp32": cosine_sim,
        "embeddings": embeddings
    }


# ── Main Pipeline ─────────────────────────────────────────────

def main():
    log("=" * 60)
    log("HeAR TFLite Conversion Pipeline")
    log("=" * 60)
    
    # Step 1: Download
    model = download_hear()
    
    # Step 2: Export SavedModel
    saved_model_dir = export_saved_model(model)
    
    # Free memory
    del model
    
    # Step 3: Convert to TFLite variants
    fp32_path = convert_tflite_fp32(saved_model_dir)
    int8_path = convert_tflite_int8(saved_model_dir)
    int8_full_path = convert_tflite_int8_full(saved_model_dir)
    int4_path = convert_tflite_int4(saved_model_dir)
    
    # Step 4: Benchmark
    log("\n" + "=" * 60)
    log("BENCHMARKING ALL VARIANTS")
    log("=" * 60)
    
    fp32_result = benchmark_tflite(fp32_path, "FP32 (baseline)")
    reference_emb = fp32_result["embeddings"] if fp32_result else None
    
    int8_result = benchmark_tflite(int8_path, "INT8 (dynamic range)", reference_emb)
    int8_full_result = benchmark_tflite(int8_full_path, "INT8 (full integer)", reference_emb)
    int4_result = benchmark_tflite(int4_path, "INT4 (experimental)", reference_emb)
    
    # Summary
    log("\n" + "=" * 60)
    log("CONVERSION RESULTS SUMMARY")
    log("=" * 60)
    log(f"{'Variant':<25} {'Size (MB)':>10} {'Latency (ms)':>13} {'Cosine Sim':>12}")
    log("-" * 60)
    
    results = []
    for r in [fp32_result, int8_result, int8_full_result, int4_result]:
        if r:
            cos = f"{r['cosine_sim_vs_fp32']:.6f}" if r['cosine_sim_vs_fp32'] else "baseline"
            log(f"{r['label']:<25} {r['size_mb']:>10.1f} {r['mean_latency_ms']:>13.1f} {cos:>12}")
            # Remove embeddings before serializing
            r_clean = {k: v for k, v in r.items() if k != 'embeddings'}
            results.append(r_clean)
        else:
            log(f"{'(failed)':<25}")
    
    # Save results
    with open(RESULTS_FILE, 'w') as f:
        json.dump(results, f, indent=2)
    log(f"\nResults saved to: {RESULTS_FILE}")
    
    # Recommend best variant
    viable = [r for r in results if r['cosine_sim_vs_fp32'] is None or r['cosine_sim_vs_fp32'] > 0.95]
    if len(viable) > 1:
        best = min(viable, key=lambda r: r['size_mb'])
        log(f"\n✅ RECOMMENDED: {best['label']} ({best['size_mb']} MB)")
    
    log("\nDone!")


if __name__ == "__main__":
    main()
