import torch
import os
import argparse
import logging
import ai_edge_torch
from transformers import PaliGemmaForConditionalGeneration, PaliGemmaProcessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VisionEncoderWrapper(torch.nn.Module):
    """
    Wraps the SigLIP vision encoder and projector from MedGemma 1.5 4B.
    Input: [batch, 3, 224, 224] (Pixel Values)
    Output: [batch, 256, 2048] (Projected Image Tokens)
    """
    def __init__(self, model):
        super().__init__()
        self.vision_tower = model.vision_tower
        self.multi_modal_projector = model.multi_modal_projector

    def forward(self, pixel_values):
        # 1. Vision Encoder (SigLIP)
        vision_outputs = self.vision_tower(pixel_values)
        image_features = vision_outputs.last_hidden_state
        
        # 2. Projector (Linear + GELU for MedGemma 1.5)
        image_tokens = self.multi_modal_projector(image_features)
        return image_tokens

class LanguageModelWrapper(torch.nn.Module):
    """
    Wraps the Gemma 2 based decoder for MedGemma.
    This wrapper is designed for the prefill / first-token phase.
    """
    def __init__(self, model):
        super().__init__()
        self.language_model = model.language_model
        
    def forward(self, input_ids, attention_mask=None):
        # Prefill / Autoregressive step
        # To handle image tokens on-device, the mobile app will:
        # 1. Run VisionEncoder.tflite
        # 2. Concatenate image tokens with text prompt embeddings
        # 3. Call this LanguageModel.tflite
        
        outputs = self.language_model(
            input_ids=input_ids,
            attention_mask=attention_mask
        )
        return outputs.logits

def convert_to_tflite(model_path, output_dir):
    logger.info(f"Loading MedGemma 4B from {model_path}...")
    
    try:
        model = PaliGemmaForConditionalGeneration.from_pretrained(
            model_path, 
            torch_dtype=torch.float32,
            device_map="cpu"
        ).eval()
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        return

    os.makedirs(output_dir, exist_ok=True)

    # ---------------------------------------------------------
    # 1. Convert Vision Encoder (Float32 or FP16)
    # ---------------------------------------------------------
    logger.info("Converting Vision Encoder...")
    vision_wrapper = VisionEncoderWrapper(model)
    
    # Dummy Input: [1, 3, 224, 224]
    sample_img = torch.randn(1, 3, 224, 224)
    
    try:
        edge_vision = ai_edge_torch.convert(
            vision_wrapper, 
            (sample_img,)
        )
        edge_vision.export(os.path.join(output_dir, "vision_encoder.tflite"))
        logger.info("✅ Vision Encoder exported successfully.")
    except Exception as e:
        logger.error(f"Failed to export Vision Encoder: {e}")

    # ---------------------------------------------------------
    # 2. Convert Language Model (INT4 Quantized)
    # ---------------------------------------------------------
    logger.info("Converting Language Model (INT4 Quantization)...")
    lm_wrapper = LanguageModelWrapper(model)
    
    # Dummy Input: [1, 20] (Sequence length of 20 for test)
    # In reality, this needs to handle dynamic shapes or fixed context window
    sample_ids = torch.randint(0, 1000, (1, 20), dtype=torch.long)
    
    try:
        # Define Quantization Config
        quant_config = ai_edge_torch.quantize.QuantConfig(
            pt2e_quantizer=ai_edge_torch.quantize.pt2e_quantizer.PT2EQuantizer().set_global(
                ai_edge_torch.quantize.pt2e_quantizer.get_symmetric_quantization_config(
                    is_per_channel=True,
                    is_dynamic=True  # Dynamic Range Quantization often better for mobile CPU/NPU
                )
            )
        )
        
        # Convert with Quantization
        # Note: For strict INT4 weight-only, we might use a different config depending on ai_edge_torch version
        # This uses PT2E dynamic quantization as a safe default for size reduction
        edge_lm = ai_edge_torch.convert(
            lm_wrapper,
            (sample_ids,),
            quant_config=quant_config
        )
        
        edge_lm.export(os.path.join(output_dir, "language_model_quant.tflite"))
        logger.info("✅ Language Model (Quantized) exported successfully.")
        
    except Exception as e:
        logger.error(f"Failed to export Language Model: {e}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=str, required=True, help="Path to HuggingFace model")
    parser.add_argument("--output-dir", type=str, default="tflite_models", help="Output directory")
    args = parser.parse_args()
    
    convert_to_tflite(args.model_dir, args.output_dir)

if __name__ == "__main__":
    main()
