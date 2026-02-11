#!/usr/bin/env python3
"""
MedGemma 1.5 4B TFLite Conversion - V7 (INT8 Quantization)
Uses the well-supported INT8 quantization API.

Run in Colab with GPU runtime.
"""
import os
import sys
import torch

print("=" * 70)
print("MedGemma TFLite Conversion V7 - INT8 Quantization")
print("=" * 70)

# STEP 0: VERIFY PROTOBUF VERSION
import subprocess
result = subprocess.run(["pip", "show", "protobuf"], capture_output=True, text=True)
for line in result.stdout.split("\n"):
    if line.startswith("Version:"):
        pb_version = line.split(':')[1].strip()
        print(f"[0] Protobuf version: {pb_version}")
        if pb_version.startswith("4.") or pb_version.startswith("5."):
            print("    Protobuf 4.x/5.x detected! Downgrading...")
            os.system("pip install -q 'protobuf>=3.20.0,<4.0.0'")
            print("    Please restart runtime and re-run this script!")
            sys.exit(0)
        else:
            print("    Protobuf 3.x confirmed")
        break

# STEP 1: ENVIRONMENT & AUTH
print("\n[1] Setting up environment...")
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"
HF_TOKEN = os.environ.get("HF_TOKEN")
os.environ["HF_TOKEN"] = HF_TOKEN

from huggingface_hub import login
try:
    login(token=HF_TOKEN, add_to_git_credential=False)
    print("    Hugging Face authentication successful")
except Exception as e:
    print(f"    HF login warning: {e}")

# STEP 2: APPLY ARCHITECTURE PATCHES
print("\n[2] Applying architecture patches for TorchDynamo tracing...")

def apply_architecture_patches():
    patched = False
    try:
        from transformers.models.gemma3 import modeling_gemma3 as mod
        
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
            print("    Gemma3RotaryEmbedding.forward patched")
            patched = True
        
        def patched_rotate_half(x):
            x1, x2 = x.split(x.shape[-1] // 2, dim=-1)
            return torch.cat((-x2, x1), dim=-1)
        
        if hasattr(mod, 'rotate_half'):
            mod.rotate_half = patched_rotate_half
            print("    rotate_half patched")
            patched = True
        
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
            print("    Gemma3Model._update_causal_mask patched")
            patched = True
            
    except ImportError:
        print("    Gemma3 module not found, trying PaliGemma...")
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
                print("    PaliGemmaRotaryEmbedding.forward patched")
                patched = True
                
            def patched_rotate_half(x):
                x1, x2 = x.split(x.shape[-1] // 2, dim=-1)
                return torch.cat((-x2, x1), dim=-1)
            mod.rotate_half = patched_rotate_half
            print("    rotate_half patched")
            patched = True
        except ImportError:
            print("    No compatible modeling module found")
    
    return patched

patches_applied = apply_architecture_patches()
if patches_applied:
    print("    Architecture patches applied successfully")
else:
    print("    Proceeding without patches")

# STEP 3: LOAD MODEL
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
print(f"    Model loaded: {param_count:,} parameters")

# STEP 4: EXTRACT LANGUAGE MODEL COMPONENT
print("\n[4] Extracting language model component...")

if hasattr(model, 'language_model'):
    lm_component = model.language_model
    print("    Extracted .language_model")
elif hasattr(model, 'model') and hasattr(model.model, 'layers'):
    lm_component = model.model
    print("    Extracted .model (has layers)")
else:
    lm_component = model
    print("    Using full model")

# Wrapper for clean output
class LanguageModelWrapper(torch.nn.Module):
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
print(f"    Wrapper created")

# STEP 5: IMPORT AI-EDGE-TORCH AND DISCOVER QUANTIZATION API
print("\n[5] Setting up quantization...")
import ai_edge_torch

# Discover available quantization recipes
print("    Exploring ai_edge_torch.quantize module...")
try:
    from ai_edge_torch import quantize as aqt
    print(f"    Available in ai_edge_torch.quantize: {[x for x in dir(aqt) if not x.startswith('_')]}")
except ImportError:
    aqt = None
    print("    ai_edge_torch.quantize not found")

# Try the generative quantize module
quant_recipe = None
try:
    from ai_edge_torch.generative import quantize as gen_quant
    print(f"    Available in generative.quantize: {[x for x in dir(gen_quant) if not x.startswith('_')]}")
    
    # Try different recipe names that might exist
    if hasattr(gen_quant, 'full_int8_dynamic_recipe'):
        quant_recipe = gen_quant.full_int8_dynamic_recipe()
        print("    Using full_int8_dynamic_recipe()")
    elif hasattr(gen_quant, 'full_linear_int8_dynamic_recipe'):
        quant_recipe = gen_quant.full_linear_int8_dynamic_recipe()
        print("    Using full_linear_int8_dynamic_recipe()")
    elif hasattr(gen_quant, 'quant_recipes'):
        print(f"    quant_recipes: {[x for x in dir(gen_quant.quant_recipes) if not x.startswith('_')]}")
except ImportError as e:
    print(f"    generative.quantize not available: {e}")

# Try quant_recipes module
if quant_recipe is None:
    try:
        from ai_edge_torch.generative.quantize import quant_recipes
        print(f"    quant_recipes module: {[x for x in dir(quant_recipes) if not x.startswith('_')]}")
        
        if hasattr(quant_recipes, 'full_int8_dynamic_recipe'):
            quant_recipe = quant_recipes.full_int8_dynamic_recipe()
            print("    Using quant_recipes.full_int8_dynamic_recipe()")
        elif hasattr(quant_recipes, 'full_linear_int8_dynamic_recipe'):
            quant_recipe = quant_recipes.full_linear_int8_dynamic_recipe()
            print("    Using quant_recipes.full_linear_int8_dynamic_recipe()")
    except ImportError as e:
        print(f"    quant_recipes not available: {e}")

# STEP 6: CONVERT MODEL
print("\n[6] Converting to TFLite...")
dummy_input = torch.zeros((1, 64), dtype=torch.long)

if quant_recipe is not None:
    print("    Starting INT8 conversion...")
    try:
        edge_model = ai_edge_torch.convert(
            wrapper,
            (dummy_input,),
            quant_config=quant_recipe
        )
        output_path = 'medgemma_int8.tflite'
    except Exception as e:
        print(f"    Quantized conversion failed: {e}")
        print("    Falling back to FP32...")
        edge_model = ai_edge_torch.convert(wrapper, (dummy_input,))
        output_path = 'medgemma_fp32.tflite'
else:
    print("    No quantization recipe available, using FP32...")
    edge_model = ai_edge_torch.convert(wrapper, (dummy_input,))
    output_path = 'medgemma_fp32.tflite'

# STEP 7: EXPORT
print(f"\n[7] Exporting to {output_path}...")
edge_model.export(output_path)

file_size_bytes = os.path.getsize(output_path)
file_size_gb = file_size_bytes / (1024**3)
print("\n" + "=" * 70)
print(f"✅ SUCCESS! File: {output_path}")
print(f"   Size: {file_size_gb:.2f} GB ({file_size_bytes:,} bytes)")
print("=" * 70)
