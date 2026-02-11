import os
import sys
import torch
import types
from unittest.mock import MagicMock
import importlib.machinery
from transformers import AutoModelForImageTextToText
import ai_edge_torch
from ai_edge_torch.generative.quantize import quant_recipes, quant_recipe_utils
from tqdm import tqdm

# --- 1. ARCHITECTURE PATCHES (The "Nku Squeeze") ---

def apply_patches():
    print("🩹 Applying Gemma 3 Architecture Patches...")
    
    # Traceable Output Patch
    import transformers.models.gemma3.modeling_gemma3 as g3_mod
    def TraceableOutput(**kwargs):
        val = kwargs.get('logits', kwargs.get('last_hidden_state'))
        if val is None and kwargs:
            val = next(iter(kwargs.values()))
        return (val,) if val is not None else ()
    
    g3_mod.BaseModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3ModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3CausalLMOutputWithPast = TraceableOutput

    # RoPE Scaling Patch for StableHLO
    def patched_rope_forward(self, x, position_ids):
        inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
        position_ids_expanded = position_ids[:, None, :].float()
        freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
        emb = torch.cat((freqs, freqs), dim=-1)
        cos = emb.cos() * self.attention_scaling
        sin = emb.sin() * self.attention_scaling
        return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
    
    g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward

    # Causal Mask Patch
    from transformers import masking_utils
    def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
        device = torch.device('cpu')
        seq_len = input_ids.shape[-1] if input_ids is not None else 1
        return torch.tril(torch.ones((seq_len, seq_len), device=device, dtype=torch.bool))[None, None, :, :]
    
    masking_utils.create_causal_mask = dummy_create_causal_mask
    masking_utils.create_sliding_window_causal_mask = dummy_create_causal_mask
    print("✅ Patches applied successfully.")

# --- 2. WRAPPER ---

class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        # Extract the language model component from MedGemma (ImageTextToText)
        self.model = model.language_model if hasattr(model, 'language_model') else model
    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids, use_cache=False)
        if isinstance(outputs, (list, tuple)) and len(outputs) > 0:
            return outputs[0]
        return outputs

# --- 3. CONVERSION RUNNER ---

def run_conversion(model_id="google/medgemma-1.5-4b-it", output_path="medgemma_int4.tflite"):
    apply_patches()
    
    print(f"\n📦 Loading Model: {model_id}...")
    model = AutoModelForImageTextToText.from_pretrained(
        model_id,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
        trust_remote_code=True,
        attn_implementation="eager"
    )
    model.eval()
    lm_wrapper = LanguageModelWrapper(model)

    print("🚀 Initiating TFLite INT4 Squeeze...")
    dummy_input = torch.randint(0, 256000, (1, 64))
    
    # INT4 recipe for maximum compression (~1.4GB)
    try:
        quant_cfg = quant_recipes.full_linear_int4_dynamic_recipe()
        print("✅ Using INT4 recipes.")
    except:
        print("⚠️ INT4 recipe failed, falling back to INT8...")
        quant_cfg = quant_recipes.full_linear_int8_dynamic_recipe()
    
    with torch.no_grad():
        edge_model = ai_edge_torch.convert(
            lm_wrapper,
            (dummy_input,),
            quant_config=quant_cfg
        )

    print(f"📁 Exporting to {output_path}...")
    edge_model.export(output_path)
    
    size_gb = os.path.getsize(output_path) / (1024**3)
    print(f"\n✨ SUCCESS! Final Model Size: {size_gb:.2f} GB")

if __name__ == "__main__":
    run_conversion()
