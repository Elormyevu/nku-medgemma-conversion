#!/usr/bin/env python3
"""
Local Gemma to TFLite Conversion Script for macOS

This script converts Gemma-2-2b-it to TFLite format for Android deployment,
using the macOS compatibility patches.

Usage:
    python convert_gemma_tflite_local.py
"""

import sys
import os

# Add the scripts directory to path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

# MUST apply patches before importing litert_torch
print("=" * 60)
print("Gemma → TFLite Local Conversion (macOS)")
print("=" * 60)

import macos_tflite_patch
macos_tflite_patch.apply_patches()

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import litert_torch

# Configuration
# Use local MedGemma cache or HF token for gated models
HF_CACHE = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/.hf_cache"
MODEL_PATH = f"{HF_CACHE}/hub/models--google--medgemma-1.5-4b-it/snapshots/e9792da5fb8ee651083d345ec4bce07c3c9f1641"
OUTPUT_DIR = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/mobile/app/src/main/assets"
SEQUENCE_LENGTH = 256  # Fixed sequence length for TFLite

# Set HF cache to use local models
os.environ["HF_HOME"] = HF_CACHE
os.environ["TRANSFORMERS_CACHE"] = f"{HF_CACHE}/hub"
os.environ["TRANSFORMERS_OFFLINE"] = "1"  # Use offline mode with cached models


def convert_gemma_to_tflite():
    """Convert MedGemma to TFLite format using local cache."""

    print(f"\n📦 Loading model from local cache: {MODEL_PATH}")
    print("   This may take a few minutes...")

    # Check if local model exists
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model not found at {MODEL_PATH}")
        print("   Please ensure MedGemma is cached locally.")
        return None

    # Load model in float16 for memory efficiency
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_PATH,
        torch_dtype=torch.float16,
        device_map="cpu",
        low_cpu_mem_usage=True,
        trust_remote_code=True,
        local_files_only=True,
    )
    model.eval()

    # Try to load tokenizer from cache or use a generic one
    try:
        tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH, local_files_only=True)
    except Exception:
        print("   Using generic tokenizer (model tokenizer not cached)")
        from transformers import GemmaTokenizerFast
        tokenizer = GemmaTokenizerFast.from_pretrained(
            "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/nku_mlx_optimized",
            local_files_only=True
        )

    param_count = sum(p.numel() for p in model.parameters())
    print(f"✅ Model loaded: {param_count:,} parameters")

    # Create sample input
    print("\n🔧 Preparing sample inputs...")
    sample_text = "Analyze this medical image for signs of anemia:"
    inputs = tokenizer(
        sample_text,
        return_tensors="pt",
        padding="max_length",
        max_length=SEQUENCE_LENGTH,
        truncation=True,
    )

    sample_input_ids = inputs["input_ids"]
    sample_attention_mask = inputs["attention_mask"]

    print(f"   Input shape: {sample_input_ids.shape}")

    # Convert to TFLite
    print("\n🚀 Converting to TFLite...")
    print("   This will take several minutes on CPU...")

    try:
        # Use litert_torch to convert
        edge_model = litert_torch.convert(
            model,
            sample_args=(sample_input_ids,),
        )

        # Export the TFLite model
        output_path = os.path.join(OUTPUT_DIR, "gemma_2b_quant.tflite")
        edge_model.export(output_path)

        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        print(f"\n✅ TFLite model saved: {output_path}")
        print(f"   Size: {size_mb:.2f} MB")

        return output_path

    except Exception as e:
        print(f"\n❌ Conversion failed: {e}")
        import traceback
        traceback.print_exc()
        return None


def verify_tflite_model(model_path):
    """Verify the TFLite model can be loaded."""
    print("\n🧪 Verifying TFLite model...")

    try:
        import tensorflow as tf
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()

        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"   Inputs: {len(input_details)}")
        for i, inp in enumerate(input_details):
            print(f"     [{i}] {inp['name']}: {inp['shape']} {inp['dtype']}")

        print(f"   Outputs: {len(output_details)}")
        for i, out in enumerate(output_details):
            print(f"     [{i}] {out['name']}: {out['shape']} {out['dtype']}")

        print("✅ Model verification passed!")
        return True

    except Exception as e:
        print(f"❌ Verification failed: {e}")
        return False


if __name__ == "__main__":
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    model_path = convert_gemma_to_tflite()

    if model_path:
        verify_tflite_model(model_path)

    print("\n" + "=" * 60)
    print("Conversion complete!")
    print("=" * 60)
