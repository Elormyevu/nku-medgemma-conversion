#!/usr/bin/env python3
"""
MedGemma 1.5 4B TFLite Conversion - V6 (Verified)
Based on KI-documented V5 approach with all architecture patches

Run this in Colab AFTER Cell 1 (environment setup) has restarted runtime.
"""
import os
import sys
import torch

print("=" * 70)
print("MedGemma TFLite Conversion V6 - Verified Approach")
print("=" * 70)

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 0: VERIFY PROTOBUF VERSION
# ═══════════════════════════════════════════════════════════════════════════════
import subprocess
result = subprocess.run(["pip", "show", "protobuf"], capture_output=True, text=True)
for line in result.stdout.split("\n"):
    if line.startswith("Version:"):
        pb_version = line.split(':')[1].strip()
        print(f"[0] Protobuf version: {pb_version}")
        if pb_version.startswith("4."):
            print("    ⚠️ Protobuf 4.x detected! Downgrading...")
            os.system("pip install -q 'protobuf>=3.20.0,<4.0.0'")
        else:
            print("    ✓ Protobuf 3.x confirmed")
        break

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 1: ENVIRONMENT & AUTH
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[1] Setting up environment...")
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"
HF_TOKEN = os.environ.get("HF_TOKEN")
os.environ["HF_TOKEN"] = HF_TOKEN

from huggingface_hub import login
try:
    login(token=HF_TOKEN, add_to_git_credential=False)
    print("    ✓ Hugging Face authentication successful")
except Exception as e:
    print(f"    ⚠ HF login warning: {e}")

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 2: APPLY ARCHITECTURE PATCHES (CRITICAL FOR TRACING)
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[2] Applying architecture patches for TorchDynamo tracing...")

def apply_architecture_patches():
    """Apply patches to make Gemma 3 / PaliGemma 2 traceable by ai-edge-torch"""
    patched = False
    
    # Try Gemma3 module first (MedGemma 1.5 uses this)
    try:
        from transformers.models.gemma3 import modeling_gemma3 as mod
        
        # Patch 1: RoPE (Rotary Position Embedding)
        def patched_rope_forward(self, x, position_ids):
            inv_freq_expanded = self.inv_freq[None, :, None].float().expand(
                position_ids.shape[0], -1, 1
            ).to(x.device)
            position_ids_expanded = position_ids[:, None, :].float()
            freqs = (inv_freq_expanded @ position_ids_expanded).transpose(1, 2)
            emb = torch.cat((freqs, freqs), dim=-1)
            cos, sin = emb.cos(), emb.sin()
            return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
        
        if hasattr(mod, 'Gemma3RotaryEmbedding'):
            mod.Gemma3RotaryEmbedding.forward = patched_rope_forward
            print("    ✓ Gemma3RotaryEmbedding.forward patched")
            patched = True
        
        # Patch 2: rotate_half function
        def patched_rotate_half(x):
            x1, x2 = x.split(x.shape[-1] // 2, dim=-1)
            return torch.cat((-x2, x1), dim=-1)
        
        if hasattr(mod, 'rotate_half'):
            mod.rotate_half = patched_rotate_half
            print("    ✓ rotate_half patched")
            patched = True
        
        # Patch 3: Causal mask (for simpler tracing)
        def patched_update_causal_mask(self, attention_mask, input_tensor, cache_position, past_key_values, output_attentions):
            dtype, device = input_tensor.dtype, input_tensor.device
            sequence_length = input_tensor.shape[1]
            target_length = sequence_length
            causal_mask = torch.full((sequence_length, target_length), float("-inf"), dtype=dtype, device=device)
            causal_mask = torch.triu(causal_mask, diagonal=1)
            causal_mask = causal_mask[None, None, :, :]
            if attention_mask is not None and attention_mask.dim() == 2:
                mask_length = attention_mask.shape[-1]
                padding_mask = causal_mask[..., :mask_length].clone()
                padding_mask = padding_mask.masked_fill(attention_mask[:, None, None, :] == 0, float("-inf"))
                causal_mask = padding_mask
            return causal_mask
        
        if hasattr(mod, 'Gemma3Model'):
            mod.Gemma3Model._update_causal_mask = patched_update_causal_mask
            print("    ✓ Gemma3Model._update_causal_mask patched")
            patched = True
            
    except ImportError:
        print("    ⚠ Gemma3 module not found, trying PaliGemma...")
        
        try:
            from transformers.models.paligemma import modeling_paligemma as mod
            
            def patched_rope_forward(self, x, position_ids):
                inv_freq_expanded = self.inv_freq[None, :, None].float().expand(
                    position_ids.shape[0], -1, 1
                ).to(x.device)
                position_ids_expanded = position_ids[:, None, :].float()
                freqs = (inv_freq_expanded @ position_ids_expanded).transpose(1, 2)
                emb = torch.cat((freqs, freqs), dim=-1)
                cos, sin = emb.cos(), emb.sin()
                return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
            
            if hasattr(mod, 'PaliGemmaRotaryEmbedding'):
                mod.PaliGemmaRotaryEmbedding.forward = patched_rope_forward
                print("    ✓ PaliGemmaRotaryEmbedding.forward patched")
                patched = True
                
            def patched_rotate_half(x):
                x1, x2 = x.split(x.shape[-1] // 2, dim=-1)
                return torch.cat((-x2, x1), dim=-1)
            mod.rotate_half = patched_rotate_half
            print("    ✓ rotate_half patched")
            patched = True
            
        except ImportError:
            print("    ⚠ No compatible modeling module found")
    
    return patched

patches_applied = apply_architecture_patches()
if patches_applied:
    print("    ✓ Architecture patches applied successfully")
else:
    print("    ⚠ Proceeding without patches (may fail)")

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 3: LOAD MODEL
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[3] Loading MedGemma model...")
from transformers import AutoModelForImageTextToText

MODEL_ID = "google/medgemma-1.5-4b-it"
print(f"    Model: {MODEL_ID}")
print("    Loading (this takes 5-10 minutes)...")

model = AutoModelForImageTextToText.from_pretrained(
    MODEL_ID,
    torch_dtype=torch.float32,
    low_cpu_mem_usage=True,
    attn_implementation="eager",
    trust_remote_code=True,
    token=HF_TOKEN
).eval()

param_count = sum(p.numel() for p in model.parameters())
print(f"    ✓ Model loaded: {param_count:,} parameters")

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 4: EXTRACT LANGUAGE MODEL COMPONENT
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[4] Extracting language model component...")

# MedGemma is multimodal - we need just the language model for text generation
if hasattr(model, 'language_model'):
    lm_component = model.language_model
    print("    ✓ Extracted .language_model")
elif hasattr(model, 'model') and hasattr(model.model, 'layers'):
    lm_component = model.model
    print("    ✓ Extracted .model (with layers)")
else:
    lm_component = model
    print("    ✓ Using full model")

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 5: CREATE TRACING WRAPPER
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[5] Creating tracing wrapper...")

class LanguageModelWrapper(torch.nn.Module):
    """Simple wrapper that returns only logits for TFLite export"""
    def __init__(self, language_model):
        super().__init__()
        self.model = language_model
    
    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids)
        if hasattr(outputs, 'logits'):
            return outputs.logits
        elif hasattr(outputs, 'last_hidden_state'):
            return outputs.last_hidden_state
        else:
            return outputs[0]

wrapper = LanguageModelWrapper(lm_component).eval()
print("    ✓ Wrapper created")

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 6: CONVERT TO TFLITE INT4
# ═══════════════════════════════════════════════════════════════════════════════
print("\n[6] Converting to TFLite INT4...")

import ai_edge_torch
from ai_edge_torch.generative import quantize as aqt

# Sample input for tracing
dummy_input = torch.zeros((1, 8), dtype=torch.long)
print(f"    Sample input shape: {dummy_input.shape}")

# INT4 quantization recipe
quant_recipe = aqt.full_linear_int4_dynamic_recipe()
print("    ✓ INT4 quantization recipe prepared")

print("\n    Starting conversion (15-30 minutes)...")
print("    " + "-" * 50)

try:
    edge_model = ai_edge_torch.convert(
        wrapper,
        (dummy_input,),
        quant_config=quant_recipe
    )
    print("    " + "-" * 50)
    print("    ✓ Conversion successful!")
    
    # Export
    output_path = "medgemma_int4.tflite"
    edge_model.export(output_path)
    
    # Verify
    file_size_bytes = os.path.getsize(output_path)
    file_size_gb = file_size_bytes / (1024**3)
    
    print("\n" + "=" * 70)
    print(f"🎉 SUCCESS!")
    print(f"   File: {output_path}")
    print(f"   Size: {file_size_gb:.2f} GB ({file_size_bytes:,} bytes)")
    print("=" * 70)
    
except Exception as e:
    print("\n" + "=" * 70)
    print(f"❌ CONVERSION FAILED: {e}")
    print("=" * 70)
    import traceback
    traceback.print_exc()
    sys.exit(1)
