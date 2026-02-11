# MedGemma TFLite Conversion for Kaggle
# Run each cell in sequence

# === CELL 1: Install dependencies ===
# !pip install ai-edge-torch-nightly transformers accelerate hf_transfer sentencepiece torchao -q

# === CELL 2: Conversion Script ===
import os
import gc
import torch

os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"

from huggingface_hub import login
from transformers import AutoModelForImageTextToText
import ai_edge_torch

# Authenticate with Hugging Face
login(token=os.environ.get("HF_TOKEN"))
print("[1] Auth OK")

print("[2] Loading MedGemma 4B on CPU...")
model = AutoModelForImageTextToText.from_pretrained(
    "google/medgemma-1.5-4b-it",
    torch_dtype=torch.float32,
    device_map="cpu",
    low_cpu_mem_usage=True,
    attn_implementation="eager"
).eval()
print(f"[2] Loaded {sum(p.numel() for p in model.parameters()):,} params")

gc.collect()

# Extract language model
lm = model.language_model if hasattr(model, "language_model") else model

# Wrapper for TFLite conversion
class Wrapper(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m
    def forward(self, x):
        o = self.m(input_ids=x)
        return o.logits if hasattr(o, "logits") else o[0]

w = Wrapper(lm).eval()
print("[3] Wrapper OK")

# Free memory
del model
gc.collect()

# Convert to TFLite
print("[4] Converting to TFLite (this takes 10-20 min)...")
sample_input = torch.zeros(1, 64, dtype=torch.long)
edge_model = ai_edge_torch.convert(w, (sample_input,))

# Export
output_path = "medgemma_fp32.tflite"
edge_model.export(output_path)
size_gb = os.path.getsize(output_path) / 1e9
print(f"SUCCESS: {output_path} ({size_gb:.2f} GB)")
