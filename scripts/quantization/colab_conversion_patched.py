import sys
import types
import os
import torch
import importlib

print("🔧 INIT: Starting Environment Patching & Conversion...")

# --- PATCH 1: Transformers Flash Attention ---
# Fixes: ImportError: cannot import name 'flash_attention_2_jit'
try:
    import transformers.modeling_flash_attention_utils as mfa
    if not hasattr(mfa, "flash_attention_2_jit"):
        print("🩹 Patching transformers.modeling_flash_attention_utils.flash_attention_2_jit...")
        mfa.flash_attention_2_jit = None
except ImportError:
    print("⚠️ Could not import transformers.modeling_flash_attention_utils (non-fatal).")
except Exception as e:
    print(f"⚠️ Error patching transformers: {e}")

# --- PATCH 2: Gemma 3 Architecture (The "Nku Squeeze") ---
import transformers.models.gemma3.modeling_gemma3 as g3_mod
from transformers import masking_utils

def apply_gemma3_patches():
    print("🩹 Applying Gemma 3 Architecture Patches...")
    
    def TraceableOutput(**kwargs):
        val = kwargs.get('logits', kwargs.get('last_hidden_state'))
        if val is None and kwargs:
            val = next(iter(kwargs.values()))
        return (val,) if val is not None else ()
    
    g3_mod.BaseModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3ModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3CausalLMOutputWithPast = TraceableOutput

    def patched_rope_forward(self, x, position_ids):
        inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
        position_ids_expanded = position_ids[:, None, :].float()
        freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
        emb = torch.cat((freqs, freqs), dim=-1)
        cos = emb.cos() * self.attention_scaling
        sin = emb.sin() * self.attention_scaling
        return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
    
    g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward

    def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
        device = torch.device('cpu')
        seq_len = input_ids.shape[-1] if input_ids is not None else 1
        return torch.tril(torch.ones((seq_len, seq_len), device=device, dtype=torch.bool))[None, None, :, :]
    
    masking_utils.create_causal_mask = dummy_create_causal_mask
    masking_utils.create_sliding_window_causal_mask = dummy_create_causal_mask
    print("✅ Gemma 3 Patches applied.")

# --- MAIN: Conversion ---
from transformers import AutoModelForImageTextToText
# Attempt to import ai_edge_torch and recipes safely
try:
    import ai_edge_torch
    from ai_edge_torch.generative.quantize import quant_recipes
except ImportError as e:
    print(f"❌ CRITICAL: ai_edge_torch not installed or import error: {e}")
    sys.exit(1)

class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model.language_model if hasattr(model, 'language_model') else model
    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids, use_cache=False)
        if isinstance(outputs, (list, tuple)) and len(outputs) > 0:
            return outputs[0]
        return outputs

def run_conversion():
    apply_gemma3_patches()
    
    model_id = "google/medgemma-1.5-4b-it"
    print(f"\n📦 Loading Model: {model_id}...")
    try:
        model = AutoModelForImageTextToText.from_pretrained(
            model_id,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True,
            trust_remote_code=True,
            attn_implementation="eager" 
        )
    except Exception as e:
        print(f"❌ Error loading model: {e}")
        # Sometimes flash attention patch needs to happen earlier?
        sys.exit(1)

    model.eval()
    lm_wrapper = LanguageModelWrapper(model)
    
    print("🚀 Initiating TFLite Conversion...")
    dummy_input = torch.randint(0, 256000, (1, 64))
    
    # Dynamic Recipe Discovery
    print(f"🔍 Checking available recipes in ai_edge_torch {ai_edge_torch.__version__}...")
    available = [r for r in dir(quant_recipes) if not r.startswith('_')]
    print(f"   Available recipes: {available}")
    
    quant_cfg = None
    # Try preferred recipes in order
    preferences = [
        'full_linear_int4_dynamic_recipe',
        'full_int4_dynamic_recipe', # Alternate name?
        'full_linear_int8_dynamic_recipe',
        'int8_dynamic_recipe'
    ]
    
    for recipe_name in preferences:
        if hasattr(quant_recipes, recipe_name):
            print(f"✅ Found preferred recipe: {recipe_name}")
            try:
                quant_cfg = getattr(quant_recipes, recipe_name)()
                break
            except Exception as e:
                print(f"⚠️ Failed to instantiate {recipe_name}: {e}")
    
    if quant_cfg is None:
        print("⚠️ No preferred recipe found. Trying *any* 'dynamic' recipe...")
        for r in available:
            if 'dynamic' in r:
                print(f"✅ Trying fallback recipe: {r}")
                try:
                    quant_cfg = getattr(quant_recipes, r)()
                    break
                except:
                    pass

    if quant_cfg is None:
        print("❌ No valid quantization recipe found. Aborting.")
        return

    print("⚙️  Using Quantization Config:", quant_cfg)
    
    output_path = "medgemma_int4.tflite"
    try:
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
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    run_conversion()
