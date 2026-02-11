!pip install -q -U ai-edge-torch-nightly transformers accelerate hf_transfer

import os
# HF_TOKEN loaded from environment
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"

import sys
import torch
import importlib
from transformers import AutoModelForImageTextToText

# --- 1. Patch Transformers Flash Attention ---
try:
    import transformers.modeling_flash_attention_utils as mfa
    if not hasattr(mfa, "flash_attention_2_jit"):
        mfa.flash_attention_2_jit = None
        print("🩹 Patched flash_attention_2_jit")
except ImportError:
    pass

# --- 2. Dynamic Gemma 3 Discovery & Patching ---
MODEL_ID = "google/medgemma-1.5-4b-it"

def patch_gemma3():
    print("🔍 Locating Gemma 3 module...")
    possible_paths = [
        "transformers.models.gemma3.modeling_gemma3",
        "transformers.models.gemma_3.modeling_gemma_3",
        "transformers.models.paligemma3.modeling_paligemma3"
    ]
    g3_mod = None
    for path in possible_paths:
        try:
            g3_mod = importlib.import_module(path)
            print(f"✅ Found Gemma 3 module at: {path}")
            break
        except ImportError:
            continue
    
    if not g3_mod:
        print("⚠️ Warning: Gemma 3 module NOT found. Tracing may fail.")
        return

    from transformers import masking_utils
    print("🩹 Applying architecture patches...")

    # Patch Output classes
    def TraceableOutput(**kwargs):
        val = kwargs.get('logits', kwargs.get('last_hidden_state'))
        if val is None and kwargs:
            val = next(iter(kwargs.values()))
        return (val,) if val is not None else ()

    for attr in dir(g3_mod):
        if 'Output' in attr and 'WithPast' in attr:
            setattr(g3_mod, attr, TraceableOutput)

    # Patch Rotary Embedding
    if hasattr(g3_mod, 'Gemma3RotaryEmbedding'):
        def patched_rope_forward(self, x, position_ids):
            inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
            position_ids_expanded = position_ids[:, None, :].float()
            freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
            emb = torch.cat((freqs, freqs), dim=-1)
            cos, sin = emb.cos() * self.attention_scaling, emb.sin() * self.attention_scaling
            return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
        g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward

    # Patch Causal Masking
    def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
        if input_ids is not None:
             device = input_ids.device
             seq_len = input_ids.shape[-1]
        elif 'input_embeds' in kwargs and kwargs['input_embeds'] is not None:
             device = kwargs['input_embeds'].device
             seq_len = kwargs['input_embeds'].shape[1]
        else:
             device = 'cpu'
             seq_len = 1
        return torch.tril(torch.ones((seq_len, seq_len), device=device, dtype=torch.bool))[None, None, :, :]

    masking_utils.create_causal_mask = dummy_create_causal_mask
    masking_utils.create_sliding_window_causal_mask = dummy_create_causal_mask
    print("✅ Patches applied.")

# --- 3. Multimodal Wrapper ---
class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        if hasattr(model, 'language_model'):
            self.model = model.language_model
            print("📦 Using model.language_model")
        elif hasattr(model, 'model') and hasattr(model.model, 'layers'):
            self.model = model.model
            print("📦 Using model.model")
        else:
            self.model = model
            print("📦 Using base model")

    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids, use_cache=False)
        if isinstance(outputs, (list, tuple)):
            return outputs[0]
        return outputs.logits if hasattr(outputs, 'logits') else outputs

# --- 4. Main Conversion ---
def run_conversion():
    patch_gemma3()
    import ai_edge_torch
    from ai_edge_torch.generative.quantize import quant_recipes
    
    # Discovery Quantization Recipe
    quant_cfg = None
    for r in ['full_linear_int4_dynamic_recipe', 'full_int4_dynamic_recipe', 'full_linear_int8_dynamic_recipe']:
        if hasattr(quant_recipes, r):
            print(f"✅ Selected recipe: {r}")
            try:
                quant_cfg = getattr(quant_recipes, r)()
                break
            except Exception as e:
                print(f"⚠️ Recipe init error: {e}")
    if not quant_cfg:
        print("❌ CRITICAL: No valid quantization recipe found.")
        return

    print(f"📦 Loading Model: {MODEL_ID}...")
    try:
        hf_token = os.environ.get("HF_TOKEN")
        model = AutoModelForImageTextToText.from_pretrained(
            MODEL_ID,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True,
            trust_remote_code=True,
            attn_implementation="eager",
            token=hf_token
        )
        model.eval()
    except Exception as e:
        print(f"❌ Model Load Error: {e}")
        return

    print("🚀 Converting to TFLite...")
    try:
        dummy_tokens = torch.randint(0, 256000, (1, 64))
        edge_model = ai_edge_torch.convert(
            LanguageModelWrapper(model),
            (dummy_tokens,),
            quant_config=quant_cfg
        )
        filename = "medgemma_int4.tflite"
        edge_model.export(filename)
        size_gb = os.path.getsize(filename) / (1024**3)
        print(f"✨ SUCCESS! Saved to {filename} ({size_gb:.2f} GB)")
    except Exception as e:
        print(f"❌ Conversion failed: {e}")

if __name__ == "__main__":
    run_conversion()
