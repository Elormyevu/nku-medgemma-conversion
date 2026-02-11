import os
import torch
import sys

# 1. Force update dependencies
os.system("pip install -q ai-edge-torch-nightly transformers>=4.48.0 accelerate hf_transfer")

# 2. Configure HF Transfer
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "1"
hf_token = os.environ.get("HF_TOKEN")

from transformers import PaliGemmaForConditionalGeneration, AutoProcessor
import ai_edge_torch
from ai_edge_torch.generative.quantize import quant_config, quant_recipe

MODEL_ID = "google/medgemma-1.5-4b-it"

def run_conversion():
    print(f"Loading model: {MODEL_ID}")
    
    # Use PaliGemmaForConditionalGeneration for MedGemma 1.5
    model = PaliGemmaForConditionalGeneration.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
        attn_implementation="eager",
        token=hf_token
    ).eval()
    
    # Simple Wrapper for ai-edge-torch
    class LanguageModelWrapper(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model
            
        def forward(self, input_ids):
            # PaliGemma forward typically takes input_ids and pixel_values
            # For conversion to text-only mode (if desired) or simple trace
            return self.model.generate(input_ids, max_new_tokens=1)

    # Prepare dummy input (batch=1, seq=32)
    dummy_tokens = torch.zeros((1, 32), dtype=torch.long)
    
    print("Configuring INT4 Quantization...")
    # Use the static quantization recipe
    from ai_edge_torch.generative.quantize.quant_recipes import full_linear_int4_dynamic_recipe
    quant_cfg = full_linear_int4_dynamic_recipe()

    print("Starting conversion (this may take 20+ mins)...")
    try:
        edge_model = ai_edge_torch.convert(
            model, # Using the raw model first to see if ai-edge-torch handles the PaliGemma structure
            (dummy_tokens,),
            quant_config=quant_cfg
        )
        
        filename = "medgemma_int4.tflite"
        edge_model.export(filename)
        size_gb = os.path.getsize(filename) / (1024**3)
        print(f"SUCCESS! Saved to {filename} ({size_gb:.2f} GB)")
    except Exception as e:
        print(f"Conversion failed: {str(e)}")
        # Fallback to a simpler trace if full model fails
        print("Retrying with simple wrapper...")
        try:
            edge_model = ai_edge_torch.convert(
                LanguageModelWrapper(model),
                (dummy_tokens,),
                quant_config=quant_cfg
            )
            edge_model.export("medgemma_int4_wrapped.tflite")
            print("SUCCESS (Wrapped)!")
        except Exception as e2:
            print(f"Wrapper conversion also failed: {str(e2)}")

if __name__ == "__main__":
    run_conversion()
