import os, sys, gc, torch
from huggingface_hub import login
from transformers import AutoModelForImageTextToText
import ai_edge_torch

# 1. Environment and Auth
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"
os.environ["CUDA_VISIBLE_DEVICES"] = "" # Force CPU
# Token provided by user in previous attempts
login(token=os.environ.get("HF_TOKEN"))

# 2. Gemma 2 Architecture Patches (Critical for ai-edge-torch tracing)
def patch_gemma2():
    try:
        from transformers.models.gemma2 import modeling_gemma2 as m
        
        def patched_rope_forward(self, x, position_ids):
            inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
            position_ids_expanded = position_ids[:, None, :].float()
            freqs = (inv_freq_expanded @ position_ids_expanded).transpose(1, 2)
            emb = torch.cat((freqs, freqs), dim=-1)
            cos, sin = emb.cos(), emb.sin()
            return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)
            
        if hasattr(m, 'Gemma2RotaryEmbedding'):
            m.Gemma2RotaryEmbedding.forward = patched_rope_forward
            
        m.rotate_half = lambda x: torch.cat((-x.split(x.shape[-1]//2, dim=-1)[1], x.split(x.shape[-1]//2, dim=-1)[0]), dim=-1)
        print("[PATCH] Gemma 2 RoPE and RotateHalf patched.")
    except Exception as e:
        print(f"[PATCH] Failed (or not needed): {e}")

patch_gemma2()

# 3. Load Model
print("[LOAD] Loading MedGemma 4B on CPU (Needs 15-18GB RAM for conversion progress)...")
model = AutoModelForImageTextToText.from_pretrained(
    "google/medgemma-1.5-4b-it",
    torch_dtype=torch.float32,
    device_map="cpu",
    low_cpu_mem_usage=True,
    attn_implementation="eager"
).eval()
gc.collect()

lm = model.language_model if hasattr(model, "language_model") else model

# 4. Flat Wrapper using type() factory (Bypasses Indentation Issues in Colab/Kaggle)
w = type("W", (torch.nn.Module,), {
    "__init__": lambda s, m: (torch.nn.Module.__init__(s), setattr(s, "m", m)),
    "forward": lambda s, x: (o := s.m(input_ids=x), o.logits if hasattr(o, "logits") else o[0])[1]
})(lm).eval()

print("[WRAP] Wrapper OK.")
del model
gc.collect()

# 5. Conversion
print("[CONVERT] Starting INT4 Conversion...")
try:
    from ai_edge_torch.generative import quantize as aqt
    q = aqt.full_linear_int4_dynamic_recipe()
    print("[QUANT] Using INT4 dynamic recipe.")
except ImportError:
    q = None
    print("[QUANT] Falling back to FP32.")

d = torch.zeros(1, 64, dtype=torch.long)
edge = ai_edge_torch.convert(w, (d,), quant_config=q)
path = "medgemma_int4.tflite" if q else "medgemma_fp32.tflite"
edge.export(path)
print(f"SUCCESS: {path} ({os.path.getsize(path)/1e9:.2f}GB)")

