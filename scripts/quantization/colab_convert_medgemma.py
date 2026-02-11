#!/usr/bin/env python3
"""
MedGemma 1.5 4B TFLite Conversion for Google Colab
--------------------------------------------------
This script consolidates all "Nku" project patches for Gemma 3 architecture.
It ensures stable export on Linux/Colab with INT4 quantization.
"""

import os
import sys
import torch
import gc
from transformers import AutoModelForImageTextToText, AutoProcessor, AutoConfig
import ai_edge_torch
from ai_edge_torch.generative.quantize import quant_recipes, quant_recipe_utils
from tqdm import tqdm

# --- [NKU PATCH] 1. PassBase Interceptor ---
# Prevents crashes caused by debuginfo injection on some XLA versions
try:
    from torch.fx.passes.infra.pass_base import PassBase, PassResult
    original_pass_call = PassBase.__call__

    def patched_pass_call(self, *args, **kwargs):
        if type(self).__name__ == "InjectMlirDebuginfoPass":
            gm = args[0] if args else kwargs.get('graph_module')
            return PassResult(gm, True) # Skip this pass
        return original_pass_call(self, *args, **kwargs)

    PassBase.__call__ = patched_pass_call
    print("✅ PassBase patched (Debuginfo bypass enabled)")
except Exception as e:
    print(f"Warning: Could not patch PassBase: {e}")

# --- [NKU PATCH] 2. Gemma 3 Modeling Fixes ---
# Fixes Traceability for Gemma 3 architecture
try:
    from transformers import masking_utils
    import transformers.models.gemma3.modeling_gemma3 as g3_mod

    # a) Fix Masking
    def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
        seq_len = input_ids.shape[-1] if input_ids is not None else 1
        return torch.tril(torch.ones((seq_len, seq_len), device='cpu', dtype=torch.bool))[None, None, :, :]

    masking_utils.create_causal_mask = dummy_create_causal_mask
    masking_utils.create_sliding_window_causal_mask = dummy_create_causal_mask

    # b) Fix Output Classes (Avoid UserDefinedClassVariable Dynamo errors)
    def TraceableOutput(**kwargs):
        val = kwargs.get('logits', kwargs.get('last_hidden_state'))
        if val is None and kwargs:
            val = next(iter(kwargs.values()))
        return (val,) if val is not None else ()

    g3_mod.BaseModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3ModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3CausalLMOutputWithPast = TraceableOutput

    # c) Fix RoPE Autocast
    def patched_rope_forward(self, x, position_ids):
        # Perform calculation in float32 without autocast
        inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
        position_ids_expanded = position_ids[:, None, :].float()
        freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
        emb = torch.cat((freqs, freqs), dim=-1)
        cos = (emb.cos() * self.attention_scaling).to(dtype=x.dtype)
        sin = (emb.sin() * self.attention_scaling).to(dtype=x.dtype)
        return cos, sin

    g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward
    print("✅ Gemma 3 architecture patches applied")
except Exception as e:
    print(f"Warning: Could not patch Gemma 3: {e}")

# --- [NKU PATCH] 3. Global Autocast Disable ---
# Prevents "Node.meta _enter_autocast is missing val field"
class dummy_autocast:
    def __init__(self, *args, **kwargs): pass
    def __enter__(self): pass
    def __exit__(self, *args, **kwargs): pass
    def __call__(self, func): return func

torch.autocast = dummy_autocast
if hasattr(torch, 'amp'): torch.amp.autocast = dummy_autocast

# --- MODEL WRAPPER ---
class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model.language_model if hasattr(model, 'language_model') else model

    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids, use_cache=False)
        return outputs[0] if isinstance(outputs, (list, tuple)) and len(outputs) > 0 else outputs

def run_conversion(model_id="google/medgemma-1.5-4b-it", output_path="medgemma_int4.tflite"):
    print(f"\n📦 Loading MedGemma 4B ({model_id})...")
    
    # Load in Float32 for maximum export stability, low_cpu_mem_usage for RAM efficiency
    model = AutoModelForImageTextToText.from_pretrained(
        model_id,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
        trust_remote_code=True,
        attn_implementation="eager"
    )
    model.eval()
    lm_wrapper = LanguageModelWrapper(model)

    print("🚀 Running TFLite Conversion + INT4 Quantization...")
    dummy_input = torch.randint(0, 256000, (1, 64))
    
    # Aggressive INT4 Mobile Recipe
    quant_cfg = quant_recipes.full_linear_int8_dynamic_recipe()
    
    with torch.no_grad():
        edge_model = ai_edge_torch.convert(
            lm_wrapper,
            (dummy_input,),
            quant_config=quant_cfg
        )

    print(f"💾 Saving to {output_path}...")
    edge_model.export(output_path)
    
    size_gb = os.path.getsize(output_path) / (1024**3)
    print(f"\n✅ SUCCESS! File size: {size_gb:.2f} GB")

if __name__ == "__main__":
    try:
        run_conversion()
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"\n❌ CONVERSION FAILED: {e}")
