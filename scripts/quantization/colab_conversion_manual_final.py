import sys, os, torch, importlib
from transformers import AutoModelForImageTextToText

print("🔧 INIT: Starting Environment Patching & Conversion...")

# --- 1. Patch Transformers Flash Attention ---
# Some versions of transformers have a bug with flash_attention_2_jit
try:
    import transformers.modeling_flash_attention_utils as mfa
    if not hasattr(mfa, "flash_attention_2_jit"):
        mfa.flash_attention_2_jit = None
        print("🩹 Patched flash_attention_2_jit")
except ImportError:
    pass

# --- 2. Patch Gemma 3 Architecture (Required for MedGemma 1.5) ---
# MedGemma 1.5 4B uses Gemma 3 architecture features that might not be fully
# supported in the eager mode of some transformers versions.
try:
    import transformers.models.gemma3.modeling_gemma3 as g3_mod
    from transformers import masking_utils

    def apply_gemma3_patches():
        print("🩹 Applying architecture patches for Gemma 3 compatibility...")
        
        # Patch Output classes to be traceable
        def TraceableOutput(**kwargs):
            val = kwargs.get('logits', kwargs.get('last_hidden_state'))
            if val is None and kwargs: val = next(iter(kwargs.values()))
            return (val,) if val is not None else ()
        
        g3_mod.BaseModelOutputWithPast = TraceableOutput
        g3_mod.Gemma3ModelOutputWithPast = TraceableOutput
        g3_mod.Gemma3CausalLMOutputWithPast = TraceableOutput

        # Patch Rotary Embedding for better tracing
        def patched_rope_forward(self, x, position_ids):
            inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
            position_ids_expanded = position_ids[:, None, :].float()
            freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
            emb = torch.cat((freqs, freqs), dim=-1)
            cos, sin = emb.cos() * self.attention_scaling, emb.sin() * self.attention_scaling
            return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
        
        g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward

        # Patch Causal Masking (Standardize for tracing)
        def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
            seq_len = input_ids.shape[-1] if input_ids is not None else 1
            return torch.tril(torch.ones((seq_len, seq_len), device='cpu', dtype=torch.bool))[None, None, :, :]
        
        masking_utils.create_causal_mask = dummy_create_causal_mask
        masking_utils.create_sliding_window_causal_mask = dummy_create_causal_mask
        print("✅ Gemma 3 Patches applied.")
    
    HAS_GEMMA3 = True
except ImportError:
    print("⚠️ Gemma 3 model not found in transformers. Ensure you installed from git.")
    HAS_GEMMA3 = False

# --- 3. Wrapper ---
class LanguageModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        # Extract the language model part if it's a multimodal wrapper
        self.model = model.language_model if hasattr(model, 'language_model') else model
    
    def forward(self, input_ids):
        # MedGemma is a Causal LM wrapper
        outputs = self.model(input_ids=input_ids, use_cache=False)
        # Return only the logits for quantization
        if isinstance(outputs, (list, tuple)):
            return outputs[0]
        return outputs

# --- 4. Main Conversion Logic ---
def run_conversion():
    if HAS_GEMMA3:
        apply_gemma3_patches()
    
    import ai_edge_torch
    from ai_edge_torch.generative.quantize import quant_recipes
    
    # Dynamic Recipe Discovery
    print(f"🔍 Checking recipes in ai_edge_torch {ai_edge_torch.__version__}...")
    quant_cfg = None
    possible_recipes = [
        'full_linear_int4_dynamic_recipe',
        'full_int4_dynamic_recipe',
        'full_linear_int8_dynamic_recipe'
    ]
    
    for r in possible_recipes:
        if hasattr(quant_recipes, r):
            print(f"✅ Found recipe: {r}")
            try:
                quant_cfg = getattr(quant_recipes, r)()
                break
            except Exception as e:
                print(f"⚠️ Failed to init {r}: {e}")
    
    if not quant_cfg:
        available = [r for r in dir(quant_recipes) if 'dynamic' in r]
        if available:
            print(f"⚠️ Using fallback recipe: {available[0]}")
            quant_cfg = getattr(quant_recipes, available[0])()

    if not quant_cfg:
        return print("❌ CRITICAL: No quantization recipe found.")

    model_id = "google/medgemma-1.5-4b-it"
    print(f"📦 Loading Model: {model_id}...")
    
    # Load model in FP32 for conversion (AI Edge Torch handles quantization)
    try:
        model = AutoModelForImageTextToText.from_pretrained(
            model_id,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True,
            trust_remote_code=True,
            attn_implementation="eager"
        )
        model.eval()
    except Exception as e:
        return print(f"❌ Failed to load model: {e}")
    
    print("🚀 Starting Conversion (this may take 5-10 minutes)...")
    try:
        # Dummy input for tracing (vocabulary size 256,000 for Gemma)
        dummy_input = torch.randint(0, 256000, (1, 64))
        
        edge_model = ai_edge_torch.convert(
            LanguageModelWrapper(model),
            (dummy_input,),
            quant_config=quant_cfg
        )
        
        output_path = "medgemma_int4.tflite"
        edge_model.export(output_path)
        
        file_size = os.path.getsize(output_path) / (1024**3)
        print(f"✨ SUCCESS! Final Model: {output_path}")
        print(f"📏 Size: {file_size:.2f} GB")
    except Exception as e:
        print(f"❌ Conversion failed: {e}")

if __name__ == "__main__":
    run_conversion()
