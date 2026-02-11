
import argparse
import os
import logging
import sys

# Configure Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def check_dependencies():
    """Verifies that the required MediaPipe and AI Edge Torch libraries are installed."""
    try:
        import mediapipe
        from mediapipe.tasks.python.genai import converter
        logger.info("✅ MediaPipe library found.")
        return True
    except ImportError:
        logger.error("❌ MediaPipe not found. Please run: pip install mediapipe")
        return False

def bundle_model(input_ckpt, output_dir, model_type="GEMMA_2B", backend="cpu"):
    """
    Bundles the quantized/pruned Checkpoint into MediaPipe binary format.
    
    Args:
        input_ckpt (str): Path to the input checkpoint (safetensors or pytorch bin).
        output_dir (str): Directory to save the bundled .bin file.
        model_type (str): The specific model variant (e.g., GEMMA_2B, GEMMA_7B).
                         Note: Custom 4B models might need to alias to the closest supported config 
                         or require a custom config. 
        backend (str): 'cpu' or 'gpu'.
    """
    if not check_dependencies():
        sys.exit(1)
        
    from mediapipe.tasks.python.genai import converter

    output_tflite_file = os.path.join(output_dir, "translategemma_mobile.bin")
    
    logger.info(f"📦 Bundling model...")
    logger.info(f"   Input: {input_ckpt}")
    logger.info(f"   Output: {output_tflite_file}")
    logger.info(f"   Type: {model_type}")
    logger.info(f"   Backend: {backend}")

    config = converter.ConversionConfig(
        input_ckpt=input_ckpt,
        ckpt_format="safetensors", # Assuming defaults from recent runs
        model_type=model_type,
        backend=backend,
        output_dir=output_dir,
        combine_file_type="qc",
        vocab_model_file=input_ckpt, # Often expected to be in the same dir or passed explicitly.
                                     # For convert_checkpoint, it often looks for tokenizer.model if passed as vocab_model_file
                                     # or if the tokenizer is part of the checkpoint path logic.
        output_tflite_file=output_tflite_file,
    )
    
    try:
        converter.convert_checkpoint(config)
        logger.info("✅ Conversion Complete! Model ready for Android.")
        logger.info(f"   Saved to: {output_tflite_file}")
    except Exception as e:
        logger.error(f"❌ Conversion Failed: {str(e)}")
        # Fallback guidance
        logger.warning("💡 Hint: Ensure input path points to the directory containing safetensors & tokenizer.model if passing directory.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input_ckpt", type=str, required=True, help="Path to input checkpoint directory")
    parser.add_argument("--output_dir", type=str, required=True, help="Path to output directory")
    parser.add_argument("--backend", type=str, default="cpu", choices=["cpu", "gpu"], help="Target backend")
    
    # Defaulting to GEMMA_2B as base since 4B isn't a standard Google config token in MediaPipe Converter yet.
    # It usually supports GEMMA_2B, GEMMA_7B. 
    # If 4B is a pruned version of 7B or a specific student model, we need to match the structural expectations.
    # Given 'medgemma-1.5-4b-it' likely mirrors 2B or 7B architecture, we'll allow override.
    parser.add_argument("--model_type", type=str, default="GEMMA_2B", help="MediaPipe Model Type string")

    args = parser.parse_args()
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    bundle_model(args.input_ckpt, args.output_dir, args.model_type, args.backend)
