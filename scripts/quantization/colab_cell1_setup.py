#!/usr/bin/env python3
"""
MedGemma Colab Conversion - CELL 1: Setup & Restart
Run this first, then after runtime restarts, run Cell 2
"""
import os
import sys

print("=" * 70)
print("STEP 1: Cleaning TensorFlow installations")
print("=" * 70)

# Uninstall ALL tensorflow variants
os.system("pip uninstall -y tensorflow tensorflow-cpu tensorflow-gpu tensorflow-macos tensorflow-metal 2>/dev/null || true")
print("✓ Cleaned existing TensorFlow")

print("\n" + "=" * 70)
print("STEP 2: Installing TensorFlow 2.18.0")
print("=" * 70)
os.system("pip install -q tensorflow==2.18.0")
print("✓ TensorFlow 2.18.0 installed")

print("\n" + "=" * 70)
print("STEP 3: Installing ai-edge-torch and other dependencies")
print("=" * 70)
os.system("pip install -q ai-edge-torch-nightly")
os.system('pip install -q "transformers>=4.48.0" accelerate hf_transfer sentencepiece')
print("✓ All dependencies installed")

print("\n" + "=" * 70)
print("STEP 4: RESTARTING RUNTIME")
print("This is REQUIRED for TensorFlow changes to take effect!")
print("=" * 70)
print("\n⚡ Runtime will restart in 3 seconds...")
print("⚡ After restart, run the CELL 2 script!")
print("=" * 70)

import time
time.sleep(3)

# Force runtime restart
os.kill(os.getpid(), 9)
