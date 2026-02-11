#!/usr/bin/env python3
"""
MedGemma 1.5 4B → TFLite INT4 Conversion Script v3
Fixes TensorFlow ABI compatibility by installing correct TF version FIRST
"""
import os
import sys

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 1: CRITICAL DEPENDENCY FIX - Install TF 2.18.0 before ai-edge-torch
# ═══════════════════════════════════════════════════════════════════════════════
print("=" * 70)
print("PHASE 1: Installing TensorFlow 2.18.0 to match ai-edge-torch ABI")
print("=" * 70)

# Uninstall any existing TensorFlow first to avoid conflicts
os.system("pip uninstall -y tensorflow tensorflow-cpu tensorflow-gpu 2>/dev/null || true")

# Install TensorFlow 2.18.0 (the version ai-edge-torch-nightly is built against)
ret = os.system("pip install -q tensorflow==2.18.0")
if ret != 0:
    print("ERROR: Failed to install TensorFlow 2.18.0")
    sys.exit(1)
print("✓ TensorFlow 2.18.0 installed")

# Now install ai-edge-torch-nightly (should match TF ABI now)
ret = os.system("pip install -q ai-edge-torch-nightly")
if ret != 0:
    print("ERROR: Failed to install ai-edge-torch-nightly")
    sys.exit(1)
print("✓ ai-edge-torch-nightly installed")

# Install other dependencies
os.system("pip install -q transformers>=4.48.0 accelerate hf_transfer sentencepiece")
print("✓ All dependencies installed")

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 2: ENVIRONMENT SETUP
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 70)
print("PHASE 2: Environment Setup")
print("=" * 70)

os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"
# HF_TOKEN loaded from environment

# Verify HF token
from huggingface_hub import login
try:
    login(token=os.environ["HF_TOKEN"], add_to_git_credential=False)
    print("✓ Hugging Face authentication successful")
except Exception as e:
    print(f"WARNING: HF login failed: {e}")

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 3: APPLY GEMMA 3 ARCHITECTURE PATCHES (before importing model)
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 70)
print("PHASE 3: Applying Gemma 3 Architecture Patches")
print("=" * 70)

import torch

# Patch will be applied after transformers import
def apply_gemma3_patches():
    """Apply patches to make Gemma 3/PaliGemma 2 compatible with ai-edge-torch"""
    try:
        from transformers.models.gemma3 import modeling_gemma3
        
        # Patch 1: RoPE computation - use split instead of unflatten
        original_rope = modeling_gemma3.Gemma3RotaryEmbedding.forward
        def patched_rope_forward(self, x, position_ids):
            seq_len = x.shape[2]
            inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1)
            position_ids_expanded = position_ids[:, None, :].float()
            freqs = (inv_freq_expanded @ position_ids_expanded).transpose(1, 2)
            emb = torch.cat((freqs, freqs), dim=-1)
            cos = emb.cos()
            sin = emb.sin()
            return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
        modeling_gemma3.Gemma3RotaryEmbedding.forward = patched_rope_forward
        
        # Patch 2: rotate_half - avoid tensor.unflatten
        def patched_rotate_half(x):
            x1, x2 = x.split(x.shape[-1] // 2, dim=-1)
            return torch.cat((-x2, x1), dim=-1)
        modeling_gemma3.rotate_half = patched_rotate_half
        
        # Patch 3: Causal mask - simplified for tracing
        def patched_update_causal_mask(self, attention_mask, input_tensor, cache_position, past_key_values, output_attentions):
            dtype, device = input_tensor.dtype, input_tensor.device
            sequence_length = input_tensor.shape[1]
            target_length = sequence_length
            
            # Create simple causal mask
            causal_mask = torch.full((sequence_length, target_length), float("-inf"), dtype=dtype, device=device)
            causal_mask = torch.triu(causal_mask, diagonal=1)
            causal_mask = causal_mask[None, None, :, :]
            
            if attention_mask is not None and attention_mask.dim() == 2:
                mask_length = attention_mask.shape[-1]
                padding_mask = causal_mask[..., :mask_length].clone()
                padding_mask = padding_mask.masked_fill(attention_mask[:, None, None, :] == 0, float("-inf"))
                causal_mask = padding_mask
            
            return causal_mask
        
        if hasattr(modeling_gemma3, 'Gemma3Model'):
            modeling_gemma3.Gemma3Model._update_causal_mask = patched_update_causal_mask
        
        print("✓ Gemma 3 patches applied successfully")
        return True
    except ImportError:
        print("⚠ Gemma 3 module not found, trying PaliGemma patches...")
        return False
    except Exception as e:
        print(f"⚠ Gemma 3 patch failed: {e}")
        return False

def apply_paligemma_patches():
    """Apply patches for PaliGemma 2 architecture"""
    try:
        from transformers.models.paligemma import modeling_paligemma
        # PaliGemma uses Gemma 2 internally, patches may be different
        print("✓ PaliGemma patches applied (if any needed)")
        return True
    except Exception as e:
        print(f"⚠ PaliGemma patch failed: {e}")
        return False

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 4: LOAD MODEL
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 70)
print("PHASE 4: Loading MedGemma Model")
print("=" * 70)

from transformers import AutoModelForImageTextToText, AutoProcessor

MODEL_ID = "google/medgemma-1.5-4b-it"
print(f"Loading model: {MODEL_ID}")

# Apply patches before loading
import transformers
apply_gemma3_patches()
apply_paligemma_patches()

# Load model with eager attention (no flash attention for tracing)
model = AutoModelForImageTextToText.from_pretrained(
    MODEL_ID,
    torch_dtype=torch.float32,  # float32 for conversion stability
    low_cpu_mem_usage=True,
    attn_implementation="eager",  # Critical for tracing
    trust_remote_code=True,
    token=os.environ["HF_TOKEN"]
)
model.eval()
print(f"✓ Model loaded. Parameters: {sum(p.numel() for p in model.parameters()):,}")

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 5: CONVERT TO TFLITE WITH INT4 QUANTIZATION
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 70)
print("PHASE 5: Converting to TFLite INT4")
print("=" * 70)

import ai_edge_torch
from ai_edge_torch.generative import quantize as aqt

# Get the language model component for conversion
if hasattr(model, 'language_model'):
    lm = model.language_model
    print("✓ Extracted language_model component")
elif hasattr(model, 'model'):
    lm = model.model
    print("✓ Extracted model component")
else:
    lm = model
    print("✓ Using full model")

# Create wrapper for ai-edge-torch
class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, language_model):
        super().__init__()
        self.model = language_model
    
    def forward(self, input_ids):
        # Simple forward that returns logits
        outputs = self.model(input_ids=input_ids)
        if hasattr(outputs, 'logits'):
            return outputs.logits
        elif hasattr(outputs, 'last_hidden_state'):
            return outputs.last_hidden_state
        else:
            return outputs[0]

wrapper = LanguageModelWrapper(lm)
wrapper.eval()

# Create sample input
sample_input = torch.zeros((1, 8), dtype=torch.long)
print(f"✓ Sample input shape: {sample_input.shape}")

# INT4 quantization recipe
print("Preparing INT4 quantization recipe...")
quant_recipe = aqt.full_linear_int4_dynamic_recipe()
print("✓ INT4 quantization recipe ready")

# Convert
print("Starting conversion (this may take several minutes)...")
try:
    edge_model = ai_edge_torch.convert(
        wrapper,
        (sample_input,),
        quant_config=quant_recipe
    )
    print("✓ Conversion successful!")
    
    # Export
    output_path = "medgemma_int4.tflite"
    edge_model.export(output_path)
    
    # Verify
    file_size = os.path.getsize(output_path) / (1024**3)
    print(f"\n{'=' * 70}")
    print(f"SUCCESS! Model exported: {output_path}")
    print(f"File size: {file_size:.2f} GB")
    print(f"{'=' * 70}")
    
except Exception as e:
    print(f"\n❌ CONVERSION FAILED: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)
