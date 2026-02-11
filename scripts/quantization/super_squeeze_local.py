import mlx.core as mx
from mlx_lm import load, generate
import os
import argparse

def local_mlx_quantize(model_id, output_dir, q_bits=4):
    print(f"🚀 Starting Local Super-Squeeze (MLX 4-bit) for {model_id}...")
    
    # 1. Quantize and save the model using MLX
    # Note: MLX-LM handle the downloading and quantization in a memory-optimized way for Apple Silicon
    os.system(f"python3 -m mlx_lm.convert --hf-path {model_id} -q --q-bits {q_bits} --mlx-path {output_dir}")
    
    # 2. Verify footprint
    if os.path.exists(output_dir):
        size_bytes = sum(os.path.getsize(os.path.join(output_dir, f)) for f in os.listdir(output_dir) if os.path.isfile(os.path.join(output_dir, f)))
        print(f"✅ Local Quantization Complete.")
        print(f"📊 Final Footprint: {size_bytes / (1024**3):.2f} GB")
    else:
        print("❌ Quantization failed to produce output.")

if __name__ == "__main__":
    # Target Gemma-2-2b for the 2GB RAM budget
    local_mlx_quantize("google/gemma-2-2b-it", "./nku_mlx_optimized")
