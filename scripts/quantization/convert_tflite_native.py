#!/usr/bin/env python3
"""
MedGemma TFLite Conversion - Native TensorFlow approach
Works on macOS/ARM without torch_xla dependency
"""
import os
import sys

def main():
    print("🚀 MedGemma TFLite Conversion (Native TF Approach)")
    print("=" * 50)

    # Step 1: Install/check dependencies
    print("\n📦 Checking dependencies...")
    try:
        import tensorflow as tf
        print(f"   TensorFlow: {tf.__version__}")
    except ImportError:
        print("   Installing TensorFlow...")
        os.system("pip install -q tensorflow")
        import tensorflow as tf

    try:
        import transformers
        print(f"   Transformers: {transformers.__version__}")
    except ImportError:
        print("   Installing transformers...")
        os.system("pip install -q transformers accelerate")
        import transformers

    from transformers import AutoTokenizer, TFAutoModelForCausalLM, AutoConfig
    from tqdm import tqdm
    import numpy as np

    MODEL_ID = "google/medgemma-1.5-4b-it"
    OUTPUT_DIR = "medgemma_savedmodel"
    TFLITE_PATH = "medgemma_int8.tflite"

    # Step 2: Check if TF model exists or try to load
    print(f"\n📥 Loading {MODEL_ID}...")
    print("   (This requires HuggingFace authentication for gated models)")

    try:
        # Try loading as TF model directly
        config = AutoConfig.from_pretrained(MODEL_ID, trust_remote_code=True)

        # MedGemma is based on PaliGemma/Gemma - check if TF weights exist
        try:
            model = TFAutoModelForCausalLM.from_pretrained(
                MODEL_ID,
                config=config,
                from_pt=True,  # Convert from PyTorch weights
            )
            print("   ✅ Model loaded (converted from PyTorch)")
        except Exception as e:
            print(f"   ⚠️ TF conversion failed: {e}")
            print("\n   Trying alternative: PyTorch → ONNX → TFLite pipeline...")
            return convert_via_onnx(MODEL_ID, TFLITE_PATH)

    except Exception as e:
        print(f"   ❌ Failed to load model: {e}")
        print("\n   Note: MedGemma requires HuggingFace authentication.")
        print("   Run: huggingface-cli login")
        return False

    # Step 3: Export to SavedModel
    print(f"\n💾 Exporting to SavedModel format...")
    model.save_pretrained(OUTPUT_DIR, saved_model=True)
    saved_model_path = os.path.join(OUTPUT_DIR, "saved_model", "1")
    print(f"   ✅ SavedModel exported to {saved_model_path}")

    # Step 4: Convert to TFLite with quantization
    print(f"\n🔧 Converting to TFLite with INT8 quantization...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)

    # Enable INT8 quantization
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.int8]

    # For dynamic range quantization (no calibration data needed)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]

    print("   Converting (this may take a while)...")
    tflite_model = converter.convert()

    # Step 5: Save TFLite model
    with open(TFLITE_PATH, 'wb') as f:
        f.write(tflite_model)

    size_gb = os.path.getsize(TFLITE_PATH) / (1024**3)
    print(f"\n✅ Conversion complete!")
    print(f"📁 Output: {TFLITE_PATH}")
    print(f"📦 Size: {size_gb:.2f} GB")
    return True


def convert_via_onnx(model_id, output_path):
    """Alternative: PyTorch → ONNX → TFLite pipeline"""
    print("\n🔄 Using ONNX intermediate format...")

    try:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
    except ImportError:
        os.system("pip install -q torch transformers")
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

    try:
        import onnx
        from onnx_tf.backend import prepare
    except ImportError:
        os.system("pip install -q onnx onnx-tf")
        import onnx
        from onnx_tf.backend import prepare

    import tensorflow as tf

    ONNX_PATH = "medgemma.onnx"
    SAVEDMODEL_PATH = "medgemma_from_onnx"

    # Load PyTorch model
    print(f"   Loading PyTorch model...")
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        torch_dtype=torch.float32,
        trust_remote_code=True
    )
    model.eval()

    # Export to ONNX
    print(f"   Exporting to ONNX...")
    dummy_input = torch.randint(0, 1000, (1, 64))

    torch.onnx.export(
        model,
        dummy_input,
        ONNX_PATH,
        input_names=['input_ids'],
        output_names=['logits'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'sequence'},
            'logits': {0: 'batch', 1: 'sequence'}
        },
        opset_version=14
    )
    print(f"   ✅ ONNX exported")

    # Convert ONNX to TF SavedModel
    print(f"   Converting ONNX → TensorFlow...")
    onnx_model = onnx.load(ONNX_PATH)
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph(SAVEDMODEL_PATH)
    print(f"   ✅ TensorFlow SavedModel created")

    # Convert to TFLite
    print(f"   Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(SAVEDMODEL_PATH)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]

    tflite_model = converter.convert()

    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_gb = os.path.getsize(output_path) / (1024**3)
    print(f"\n✅ Conversion complete!")
    print(f"📁 Output: {output_path}")
    print(f"📦 Size: {size_gb:.2f} GB")

    # Cleanup
    if os.path.exists(ONNX_PATH):
        os.remove(ONNX_PATH)

    return True


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
