#!/usr/bin/env python3
"""
MedGemma Colab Conversion - CELL 1: Environment Setup (V2)
Run this FIRST, then wait for runtime restart, then run Cell 2

FIXES:
- TensorFlow ABI mismatch (installs TF 2.18.0)
- Protobuf version mismatch (downgrades to 3.20.x)
"""
import os
import subprocess
import sys

print("=" * 70)
print("MEDGEMMA TFLITE CONVERSION - CELL 1: ENVIRONMENT SETUP V2")
print("=" * 70)

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 1: CLEAN AND INSTALL TENSORFLOW 2.18.0
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[1/4] Cleaning existing TensorFlow...")
os.system("pip uninstall -y tensorflow tensorflow-intel tf-keras tensorflow-cpu tensorflow-text tf-nightly 2>/dev/null")

print("\n[2/4] Installing TensorFlow 2.18.0...")
os.system("pip install -q tensorflow==2.18.0")

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 2: INSTALL AI-EDGE-TORCH AND DEPENDENCIES
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[3/4] Installing ai-edge-torch and dependencies...")
os.system("pip install -q ai-edge-torch-nightly transformers>=4.48.0 accelerate hf_transfer sentencepiece")

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 3: FIX PROTOBUF VERSION (CRITICAL!)
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[4/4] Fixing protobuf version (downgrade to 3.20.x for ai-edge-torch)...")
os.system("pip install -q 'protobuf>=3.20.0,<4.0.0'")

# Verify installations
print("\n" + "=" * 70)
print("VERIFICATION:")
print("=" * 70)
try:
    import tensorflow as tf
    print(f"✓ TensorFlow: {tf.__version__}")
except Exception as e:
    print(f"✗ TensorFlow failed: {e}")

try:
    import protobuf
    print(f"✓ Protobuf: {protobuf.__version__}")
except:
    # protobuf doesn't always have __version__, check with pip
    result = subprocess.run(["pip", "show", "protobuf"], capture_output=True, text=True)
    for line in result.stdout.split("\n"):
        if line.startswith("Version:"):
            print(f"✓ Protobuf: {line.split(':')[1].strip()}")
            break

try:
    import ai_edge_torch
    print(f"✓ ai-edge-torch: installed")
except Exception as e:
    print(f"✗ ai-edge-torch failed: {e}")

print("\n" + "=" * 70)
print("✓ SETUP COMPLETE!")
print("⚠️ RUNTIME WILL RESTART NOW")
print("After restart, run Cell 2 (convert.py)")
print("=" * 70)

# Force runtime restart
os.kill(os.getpid(), 9)
