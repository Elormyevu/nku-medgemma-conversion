#!/usr/bin/env python3
"""
HeAR Event Detector — Convert to TFLite and Benchmark.

Converts the HeAR event detector models (MobileNetV3Small/Large) to TFLite
at FP32 and INT8 quantization levels, then benchmarks each variant.

These models detect 8 health sound classes:
  Cough, Snore, Baby Cough, Breathe, Sneeze, Throat Clear, Laugh, Speech

Usage:
    python3 scripts/hear_eval/convert_event_detector.py

Output:
    models/hear/event_detector_small_fp32.tflite  (~3.6MB)
    models/hear/event_detector_small_int8.tflite  (~1MB)
    models/hear/event_detector_large_fp32.tflite  (~11.5MB)
    models/hear/event_detector_large_int8.tflite  (~3MB)
"""

import os
import sys
import time
import json
import numpy as np

# ── Configuration ─────────────────────────────────────────────

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "models", "hear")
RESULTS_FILE = os.path.join(OUTPUT_DIR, "event_detector_results.json")
SAMPLE_RATE = 16000
CLIP_DURATION_S = 2
CLIP_SAMPLES = SAMPLE_RATE * CLIP_DURATION_S  # 32000

LABELS = ['Cough', 'Snore', 'Baby Cough', 'Breathe', 'Sneeze',
          'Throat Clear', 'Laugh', 'Speech']

# HeAR HF cache path
HF_SNAPSHOT = os.path.expanduser(
    "~/.cache/huggingface/hub/models--google--hear/snapshots/"
    "9b2eb2853c426676255cc6ac5804b7f1fe8e563f"
)

os.makedirs(OUTPUT_DIR, exist_ok=True)


def log(msg: str):
    print(f"[HeAR-ED] {msg}", flush=True)


# ── Convert a SavedModel to TFLite ────────────────────────────

def convert_to_tflite(saved_model_dir: str, output_path: str, quantize: str = "fp32"):
    """Convert a TF SavedModel to TFLite.
    
    Args:
        saved_model_dir: Path to SavedModel directory
        output_path: Output .tflite file path
        quantize: "fp32", "int8_dynamic", or "int8_full"
    """
    import tensorflow as tf
    
    if os.path.exists(output_path):
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"  Already exists: {output_path} ({size_mb:.2f} MB)")
        return output_path
    
    log(f"  Converting to TFLite ({quantize})...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    
    if quantize == "int8_dynamic":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif quantize == "int8_full":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        def representative_dataset():
            for _ in range(100):
                choice = np.random.randint(3)
                if choice == 0:
                    data = np.random.normal(0, 0.01, (1, CLIP_SAMPLES)).astype(np.float32)
                elif choice == 1:
                    data = np.random.normal(0, 0.1, (1, CLIP_SAMPLES)).astype(np.float32)
                else:
                    data = np.random.normal(0, 0.05, (1, CLIP_SAMPLES)).astype(np.float32)
                    burst_start = np.random.randint(0, CLIP_SAMPLES - 4000)
                    data[0, burst_start:burst_start + 4000] = np.random.normal(0, 0.5, 4000).astype(np.float32)
                yield [data]
        converter.representative_dataset = representative_dataset
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
            tf.lite.OpsSet.TFLITE_BUILTINS
        ]
        converter.inference_input_type = tf.float32
        converter.inference_output_type = tf.float32
    
    try:
        tflite_model = converter.convert()
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"  ✅ {output_path} ({size_mb:.2f} MB)")
        return output_path
    except Exception as e:
        log(f"  ❌ Conversion failed: {e}")
        # Try with SELECT_TF_OPS fallback
        log(f"  Retrying with SELECT_TF_OPS...")
        converter2 = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
        if quantize != "fp32":
            converter2.optimizations = [tf.lite.Optimize.DEFAULT]
        converter2.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        converter2._experimental_lower_tensor_list_ops = False
        try:
            tflite_model = converter2.convert()
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            size_mb = os.path.getsize(output_path) / 1e6
            log(f"  ✅ {output_path} ({size_mb:.2f} MB) [with SELECT_TF_OPS]")
            return output_path
        except Exception as e2:
            log(f"  ❌ Retry also failed: {e2}")
            return None


# ── Benchmark a TFLite model ─────────────────────────────────

def benchmark_tflite(tflite_path: str, label: str, n_test: int = 50):
    """Load TFLite model, run inference, measure latency and output distribution."""
    import tensorflow as tf
    
    if tflite_path is None:
        return None
    
    size_mb = os.path.getsize(tflite_path) / 1e6
    log(f"\n{'─'*50}")
    log(f"Benchmarking: {label} ({size_mb:.2f} MB)")
    log(f"{'─'*50}")
    
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    log(f"  Input:  {input_details[0]['shape']} {input_details[0]['dtype']}")
    log(f"  Output: {output_details[0]['shape']} {output_details[0]['dtype']}")
    
    # Generate varied test audio
    np.random.seed(42)
    all_outputs = []
    latencies = []
    
    for i in range(n_test):
        # Vary the audio type
        if i % 3 == 0:
            audio = np.random.normal(0, 0.01, (1, CLIP_SAMPLES)).astype(np.float32)  # silence
        elif i % 3 == 1:
            audio = np.random.normal(0, 0.1, (1, CLIP_SAMPLES)).astype(np.float32)   # noise
        else:
            # Cough-like burst
            audio = np.random.normal(0, 0.02, (1, CLIP_SAMPLES)).astype(np.float32)
            for _ in range(3):  # multiple bursts
                start = np.random.randint(0, CLIP_SAMPLES - 2000)
                audio[0, start:start + 2000] = np.random.normal(0, 0.8, 2000).astype(np.float32)
        
        interpreter.set_tensor(input_details[0]['index'], audio)
        
        start_time = time.perf_counter()
        interpreter.invoke()
        elapsed_ms = (time.perf_counter() - start_time) * 1000
        
        output = interpreter.get_tensor(output_details[0]['index'])
        all_outputs.append(output[0])
        latencies.append(elapsed_ms)
    
    outputs = np.array(all_outputs)
    
    # Stats
    mean_latency = np.mean(latencies)
    p50_latency = np.percentile(latencies, 50)
    p95_latency = np.percentile(latencies, 95)
    
    log(f"  Latency: mean={mean_latency:.1f}ms, p50={p50_latency:.1f}ms, p95={p95_latency:.1f}ms")
    log(f"  Output shape: {outputs.shape}")
    log(f"  Output range: [{outputs.min():.4f}, {outputs.max():.4f}]")
    
    # Per-class mean probabilities
    log(f"  Per-class mean probabilities:")
    for j, label_name in enumerate(LABELS):
        log(f"    {label_name:>15}: {outputs[:, j].mean():.4f} ± {outputs[:, j].std():.4f}")
    
    return {
        "label": label,
        "path": tflite_path,
        "size_mb": round(size_mb, 2),
        "mean_latency_ms": round(mean_latency, 1),
        "p50_latency_ms": round(p50_latency, 1),
        "p95_latency_ms": round(p95_latency, 1),
        "output_shape": list(outputs.shape),
        "per_class_means": {LABELS[j]: round(float(outputs[:, j].mean()), 4) for j in range(len(LABELS))},
    }


# ── Also convert full encoder via ONNX ────────────────────────

def convert_encoder_onnx(saved_model_dir: str):
    """Convert full HeAR ViT-L encoder to ONNX format."""
    output_path = os.path.join(OUTPUT_DIR, "hear_encoder.onnx")
    if os.path.exists(output_path):
        size_mb = os.path.getsize(output_path) / 1e6
        log(f"  ONNX encoder already exists: {output_path} ({size_mb:.1f} MB)")
        return output_path
    
    log("Converting full HeAR encoder to ONNX...")
    try:
        import subprocess
        result = subprocess.run([
            sys.executable, "-m", "tf2onnx.convert",
            "--saved-model", saved_model_dir,
            "--output", output_path,
            "--opset", "17",
            "--tag", "serve",
            "--signature_def", "serving_default"
        ], capture_output=True, text=True, timeout=600)
        
        if result.returncode == 0:
            size_mb = os.path.getsize(output_path) / 1e6
            log(f"  ✅ ONNX encoder: {output_path} ({size_mb:.1f} MB)")
            return output_path
        else:
            log(f"  ❌ ONNX conversion failed: {result.stderr[-500:]}")
            return None
    except Exception as e:
        log(f"  ❌ ONNX conversion error: {e}")
        return None


def benchmark_onnx(onnx_path: str, n_test: int = 20):
    """Benchmark ONNX model inference."""
    if onnx_path is None:
        return None
    
    import onnxruntime as ort
    
    size_mb = os.path.getsize(onnx_path) / 1e6
    log(f"\n{'─'*50}")
    log(f"Benchmarking ONNX encoder ({size_mb:.1f} MB)")
    log(f"{'─'*50}")
    
    session = ort.InferenceSession(onnx_path)
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name
    
    log(f"  Input: {input_name} {session.get_inputs()[0].shape}")
    log(f"  Output: {output_name} {session.get_outputs()[0].shape}")
    
    np.random.seed(42)
    embeddings = []
    latencies = []
    
    for i in range(n_test):
        audio = np.random.normal(0, 0.1, (1, CLIP_SAMPLES)).astype(np.float32)
        
        start_time = time.perf_counter()
        result = session.run([output_name], {input_name: audio})
        elapsed_ms = (time.perf_counter() - start_time) * 1000
        
        embeddings.append(result[0][0])
        latencies.append(elapsed_ms)
    
    embeddings = np.array(embeddings)
    log(f"  Embedding shape: {embeddings.shape}")
    log(f"  Latency: mean={np.mean(latencies):.1f}ms, p95={np.percentile(latencies, 95):.1f}ms")
    log(f"  Embedding stats: mean={embeddings.mean():.4f}, std={embeddings.std():.4f}")
    
    return {
        "label": "ONNX encoder (FP32)",
        "size_mb": round(size_mb, 1),
        "mean_latency_ms": round(float(np.mean(latencies)), 1),
        "p95_latency_ms": round(float(np.percentile(latencies, 95)), 1),
        "embedding_dim": embeddings.shape[-1],
    }


# ── Main ──────────────────────────────────────────────────────

def main():
    log("=" * 60)
    log("HeAR Event Detector — TFLite Conversion & Benchmark")
    log("=" * 60)
    
    event_detector_dir = os.path.join(HF_SNAPSHOT, "event_detector")
    small_dir = os.path.join(event_detector_dir, "event_detector_small")
    large_dir = os.path.join(event_detector_dir, "event_detector_large")
    
    if not os.path.exists(small_dir):
        log(f"ERROR: Event detector not found at {small_dir}")
        log("Run convert_hear_tflite.py first to download HeAR from HuggingFace")
        sys.exit(1)
    
    # ── Convert event detectors ───────────────────────────────
    log("\n▶ Converting event_detector_small (MobileNetV3Small, ~3.6MB)")
    small_fp32 = convert_to_tflite(small_dir, os.path.join(OUTPUT_DIR, "event_detector_small_fp32.tflite"), "fp32")
    small_int8 = convert_to_tflite(small_dir, os.path.join(OUTPUT_DIR, "event_detector_small_int8.tflite"), "int8_dynamic")
    small_int8_full = convert_to_tflite(small_dir, os.path.join(OUTPUT_DIR, "event_detector_small_int8_full.tflite"), "int8_full")
    
    log("\n▶ Converting event_detector_large (MobileNetV3Large, ~11.5MB)")
    large_fp32 = convert_to_tflite(large_dir, os.path.join(OUTPUT_DIR, "event_detector_large_fp32.tflite"), "fp32")
    large_int8 = convert_to_tflite(large_dir, os.path.join(OUTPUT_DIR, "event_detector_large_int8.tflite"), "int8_dynamic")
    
    # ── Convert full encoder to ONNX (for comparison/cloud use) ─
    log("\n▶ Converting full HeAR encoder to ONNX")
    onnx_encoder = convert_encoder_onnx(HF_SNAPSHOT)
    
    # ── Benchmark all ─────────────────────────────────────────
    log("\n" + "=" * 60)
    log("BENCHMARKING")
    log("=" * 60)
    
    results = []
    
    for path, label in [
        (small_fp32, "Small FP32"),
        (small_int8, "Small INT8 (dynamic)"),
        (small_int8_full, "Small INT8 (full)"),
        (large_fp32, "Large FP32"),
        (large_int8, "Large INT8 (dynamic)"),
    ]:
        r = benchmark_tflite(path, label)
        if r:
            results.append(r)
    
    # ONNX full encoder
    onnx_result = benchmark_onnx(onnx_encoder)
    if onnx_result:
        results.append(onnx_result)
    
    # ── Summary ───────────────────────────────────────────────
    log("\n" + "=" * 60)
    log("RESULTS SUMMARY")
    log("=" * 60)
    log(f"{'Model':<25} {'Size (MB)':>10} {'Latency (ms)':>13} {'p95 (ms)':>10}")
    log("─" * 60)
    
    for r in results:
        log(f"{r['label']:<25} {r['size_mb']:>10.2f} {r['mean_latency_ms']:>13.1f} {r.get('p95_latency_ms', 0):>10.1f}")
    
    # Save results
    with open(RESULTS_FILE, 'w') as f:
        json.dump(results, f, indent=2)
    log(f"\nResults saved to: {RESULTS_FILE}")
    
    # Recommend
    tflite_results = [r for r in results if r['label'].startswith(('Small', 'Large'))]
    if tflite_results:
        best = min(tflite_results, key=lambda r: r['size_mb'])
        log(f"\n✅ RECOMMENDED FOR ON-DEVICE: {best['label']} ({best['size_mb']} MB)")
        log(f"   Latency: {best['mean_latency_ms']} ms (mean)")
    
    log("\nDone!")


if __name__ == "__main__":
    main()
