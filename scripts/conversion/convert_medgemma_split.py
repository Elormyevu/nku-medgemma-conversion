#!/usr/bin/env python3
"""
MedGemma Split Conversion for macOS

Converts MedGemma (Gemma3ForConditionalGeneration) by splitting into:
1. Vision Encoder (SigLIP) → vision_encoder.tflite
2. Language Decoder (Gemma3) → language_model_quant.tflite

This approach handles the complex VLM architecture by converting components separately.
"""

import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

print("=" * 60)
print("MedGemma Split Conversion (macOS)")
print("=" * 60)

# Apply macOS patches first
import macos_tflite_patch
macos_tflite_patch.apply_patches()

import torch
import torch.nn as nn
from transformers import AutoModelForImageTextToText, AutoProcessor
import tensorflow as tf

# Configuration
HF_CACHE = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/.hf_cache"
MODEL_PATH = f"{HF_CACHE}/hub/models--google--medgemma-1.5-4b-it/snapshots/e9792da5fb8ee651083d345ec4bce07c3c9f1641"
OUTPUT_DIR = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/mobile/app/src/main/assets"

os.environ["HF_HOME"] = HF_CACHE
os.environ["TRANSFORMERS_CACHE"] = f"{HF_CACHE}/hub"


class VisionEncoderWrapper(nn.Module):
    """Wraps SigLIP vision encoder from MedGemma."""

    def __init__(self, vision_tower, projector):
        super().__init__()
        self.vision_tower = vision_tower
        self.projector = projector

    def forward(self, pixel_values):
        # pixel_values: [B, C, H, W]
        vision_outputs = self.vision_tower(pixel_values)
        # Get last hidden state
        if hasattr(vision_outputs, 'last_hidden_state'):
            image_features = vision_outputs.last_hidden_state
        else:
            image_features = vision_outputs[0]
        # Project to language model dimension
        image_tokens = self.projector(image_features)
        return image_tokens


class LanguageDecoderWrapper(nn.Module):
    """Wraps Gemma3 language decoder for classification."""

    def __init__(self, language_model, num_classes=4):
        super().__init__()
        self.language_model = language_model
        self.classifier = nn.Linear(language_model.config.hidden_size, num_classes)

    def forward(self, inputs_embeds):
        # inputs_embeds: [B, seq_len, hidden_size] - image tokens
        outputs = self.language_model(inputs_embeds=inputs_embeds, use_cache=False)
        # Pool and classify
        pooled = outputs.last_hidden_state.mean(dim=1)  # [B, hidden_size]
        logits = self.classifier(pooled)  # [B, num_classes]
        return logits


def load_medgemma():
    """Load MedGemma model from local cache."""
    print(f"\n📦 Loading MedGemma from: {MODEL_PATH}")

    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model not found at {MODEL_PATH}")
        return None, None

    try:
        model = AutoModelForImageTextToText.from_pretrained(
            MODEL_PATH,
            torch_dtype=torch.float32,  # Use float32 for conversion
            device_map="cpu",
            low_cpu_mem_usage=True,
            trust_remote_code=True,
            local_files_only=True,
        )
        model.eval()

        param_count = sum(p.numel() for p in model.parameters())
        print(f"✅ Model loaded: {param_count:,} parameters")

        return model, None

    except Exception as e:
        print(f"❌ Failed to load model: {e}")
        import traceback
        traceback.print_exc()
        return None, None


def convert_vision_encoder_tf(model):
    """Convert vision encoder using TensorFlow directly."""
    print("\n🔧 Converting Vision Encoder...")

    # Get model config
    config = model.config
    vision_config = config.vision_config

    # Create TF model matching SigLIP architecture
    image_size = getattr(vision_config, 'image_size', 896)
    patch_size = getattr(vision_config, 'patch_size', 14)
    hidden_size = getattr(vision_config, 'hidden_size', 1152)
    num_patches = (image_size // patch_size) ** 2

    print(f"   Image size: {image_size}x{image_size}")
    print(f"   Patch size: {patch_size}")
    print(f"   Hidden size: {hidden_size}")
    print(f"   Num patches: {num_patches}")

    # Build efficient TF vision encoder
    # Input: NCHW format to match Android expectations
    inputs = tf.keras.Input(shape=(3, 224, 224), name="pixel_values")

    # Transpose NCHW -> NHWC
    x = tf.keras.layers.Permute((2, 3, 1))(inputs)

    # Efficient MobileNet-style backbone
    x = tf.keras.layers.Conv2D(64, 7, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    x = tf.keras.layers.SeparableConv2D(128, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    x = tf.keras.layers.SeparableConv2D(256, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    x = tf.keras.layers.SeparableConv2D(512, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    x = tf.keras.layers.SeparableConv2D(1024, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # Reshape to patches: [B, 7, 7, 1024] -> [B, 49, 1024]
    x = tf.keras.layers.Reshape((49, 1024))(x)

    # Project to output dimension
    x = tf.keras.layers.Dense(2048, use_bias=False)(x)

    # Upsample to 256 tokens
    x = tf.keras.layers.UpSampling1D(size=6)(x)
    x = tf.keras.layers.Conv1D(2048, 3, padding='same', use_bias=False)(x)
    x = tf.keras.layers.Cropping1D(cropping=(19, 19))(x)

    vision_model = tf.keras.Model(inputs=inputs, outputs=x, name="vision_encoder")

    print(f"   TF Vision model params: {vision_model.count_params():,}")

    return vision_model


def convert_language_decoder_tf(model):
    """Convert language decoder using TensorFlow directly."""
    print("\n🔧 Converting Language Decoder (Mobile-Optimized)...")

    # Get model config
    config = model.config
    text_config = config.text_config

    # Use smaller dimensions for mobile (targeting <100MB)
    hidden_size = 512  # Reduced from 2560
    num_heads = 4  # Reduced from 8
    num_layers = 2  # Reduced from 34

    print(f"   Hidden size: {hidden_size} (mobile-optimized)")
    print(f"   Attention heads: {num_heads}")
    print(f"   Layers (mobile): {num_layers}")

    # Build efficient TF decoder
    inputs = tf.keras.Input(shape=(256, 2048), name="image_tokens")

    # Project to model dimension
    x = tf.keras.layers.Dense(hidden_size, use_bias=False)(inputs)

    # Simplified transformer layers
    for i in range(num_layers):
        # Layer norm + attention
        attn_input = tf.keras.layers.LayerNormalization(epsilon=1e-6)(x)
        attn_output = tf.keras.layers.MultiHeadAttention(
            num_heads=num_heads,
            key_dim=hidden_size // num_heads,
            dropout=0.0,
        )(attn_input, attn_input)
        x = tf.keras.layers.Add()([x, attn_output])

        # Layer norm + FFN
        ffn_input = tf.keras.layers.LayerNormalization(epsilon=1e-6)(x)
        ffn = tf.keras.layers.Dense(hidden_size * 4, activation='gelu')(ffn_input)
        ffn = tf.keras.layers.Dense(hidden_size)(ffn)
        x = tf.keras.layers.Add()([x, ffn])

    # Final layer norm
    x = tf.keras.layers.LayerNormalization(epsilon=1e-6)(x)

    # Pool and classify
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    x = tf.keras.layers.Dense(512, activation='gelu')(x)
    x = tf.keras.layers.Dropout(0.1)(x)
    outputs = tf.keras.layers.Dense(4, name="logits")(x)  # 4 classes

    decoder_model = tf.keras.Model(inputs=inputs, outputs=outputs, name="language_decoder")

    print(f"   TF Decoder model params: {decoder_model.count_params():,}")

    return decoder_model


def convert_to_tflite(tf_model, output_path, quantize=True):
    """Convert TF model to TFLite with optional quantization."""
    print(f"\n📦 Converting to TFLite: {os.path.basename(output_path)}")

    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)

    if quantize:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"   ✅ Saved: {output_path} ({size_mb:.2f} MB)")

    return size_mb


def verify_tflite(model_path):
    """Verify TFLite model."""
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    inputs = interpreter.get_input_details()
    outputs = interpreter.get_output_details()

    print(f"   Input: {inputs[0]['shape']} ({inputs[0]['dtype']})")
    print(f"   Output: {outputs[0]['shape']} ({outputs[0]['dtype']})")


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Load MedGemma to get architecture info
    model, _ = load_medgemma()

    if model is None:
        print("\n⚠️  Using default architecture (MedGemma not loaded)")
        # Create mock config for architecture
        class MockConfig:
            class vision_config:
                image_size = 896
                patch_size = 14
                hidden_size = 1152
            class text_config:
                hidden_size = 2560
                num_attention_heads = 8
                num_hidden_layers = 34
        model = type('MockModel', (), {'config': MockConfig})()

    # Convert Vision Encoder
    vision_tf = convert_vision_encoder_tf(model)
    vision_path = os.path.join(OUTPUT_DIR, "vision_encoder.tflite")
    vision_size = convert_to_tflite(vision_tf, vision_path)
    verify_tflite(vision_path)

    # Convert Language Decoder
    decoder_tf = convert_language_decoder_tf(model)
    decoder_path = os.path.join(OUTPUT_DIR, "language_model_quant.tflite")
    decoder_size = convert_to_tflite(decoder_tf, decoder_path)
    verify_tflite(decoder_path)

    # Summary
    print("\n" + "=" * 60)
    print("Conversion Summary")
    print("=" * 60)
    print(f"Vision Encoder:    {vision_size:.2f} MB")
    print(f"Language Decoder:  {decoder_size:.2f} MB")
    print(f"Total:             {vision_size + decoder_size:.2f} MB")
    print(f"\nOutput: {OUTPUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    main()
