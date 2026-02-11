import os, sys, gc, torch

# Force CPU only - no GPU memory usage
os.environ["CUDA_VISIBLE_DEVICES"] = ""
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"

from huggingface_hub import login
from transformers import AutoModelForImageTextToText
import ai_edge_torch

login(token=os.environ.get("HF_TOKEN"))
print("[1] Auth OK")

print("[2] Loading model on CPU...")
model = AutoModelForImageTextToText.from_pretrained(
    "google/medgemma-1.5-4b-it",
    torch_dtype=torch.float32,
    device_map="cpu",
    low_cpu_mem_usage=True,
    attn_implementation="eager"
).eval()
print("[2] Loaded")

# Force garbage collection
gc.collect()

lm = model.language_model if hasattr(model, "language_model") else model

class W(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m
    def forward(self, x):
        o = self.m(input_ids=x)
        return o.logits if hasattr(o, "logits") else o[0]

w = W(lm).eval()
print("[3] Wrapper OK")

# Clear model reference to free memory
del model
gc.collect()

print("[4] Converting on CPU...")
d = torch.zeros(1, 64, dtype=torch.long)
edge = ai_edge_torch.convert(w, (d,))
path = "medgemma_fp32.tflite"
edge.export(path)
print("SUCCESS:", path, os.path.getsize(path)/1e9, "GB")
