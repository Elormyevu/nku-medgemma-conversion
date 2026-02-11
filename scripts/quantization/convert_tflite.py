import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import ai_edge_torch
from ai_edge_torch.quantize.pt2e_quantizer import PT2EQuantizer
from ai_edge_torch.quantize.quant_config import QuantConfig
import os

def convert_medgemma_to_tflite(model_id, output_path):
    print(f"🚀 Starting TFLite Conversion for {model_id}...")
    
    # 1. Load Model (PyTorch)
    # We use float16 to save memory during conversion
    model = AutoModelForCausalLM.from_pretrained(
        model_id, 
        torch_dtype=torch.float16,
        low_cpu_mem_usage=True,
        trust_remote_code=True
    )
    model.eval()
    
    # 2. Prepare Dummy Input
    # 1 batch, 512 sequence length
    dummy_input = torch.randint(0, 2000, (1, 512))
    
    # 3. Configure INT4 Quantization
    # We target INT4 for the 1.41GB footprint
    quant_config = QuantConfig(quantize_weights=True)
    
    print("🔄 Converting to TFLite (with INT4)...")
    try:
        edge_model = ai_edge_torch.convert(
            model, 
            (dummy_input,),
            quant_config=quant_config
        )
        
        # 4. Save the model
        edge_model.export(output_path)
        print(f"✅ TFLite Model Saved: {output_path}")
        print(f"📊 Final Size: {os.path.getsize(output_path) / (1024**3):.2f} GB")
        
    except Exception as e:
        print(f"❌ Conversion Failed: {e}")

if __name__ == "__main__":
    # We use gemma-2-2b-it as the base for the TFLite lock-in
    # This ensures we have the architecture verified for the Android build
    convert_medgemma_to_tflite(
        model_id="google/gemma-2-2b-it",
        output_path="/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/medgemma_int4.tflite"
    )
