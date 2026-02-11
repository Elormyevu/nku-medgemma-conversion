#!/usr/bin/env python3
"""
Create Demo TFLite Models for NKU Android App

This script creates functional TFLite models matching the expected
I/O shapes for the Android NkuBrain inference engine:

1. Vision Encoder: [1, 3, 224, 224] → [1, 256, 2048]
   - Simulates SigLIP vision encoder

2. Language Decoder: [1, 256, 2048] → [1, 4]
   - Outputs 4 classes: Healthy, Anemia, Jaundice, Cyanosis

These are placeholder models for testing the Android build pipeline.
Replace with actual MedGemma-converted models for production.
"""

import tensorflow as tf
import os

OUTPUT_DIR = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/mobile/app/src/main/assets"


def create_vision_encoder():
    """
    Vision Encoder: Input [batch, channels, height, width] → Output [batch, 256, 2048]

    Optimized for mobile: ~50MB target with FP16 quantization.
    Uses efficient MobileNet-style depthwise separable convolutions.
    """
    print("Creating Vision Encoder model (mobile-optimized)...")

    # Input: [1, 3, 224, 224] in NCHW format (PyTorch convention)
    inputs = tf.keras.Input(shape=(3, 224, 224), name="pixel_values")

    # Transpose from NCHW to NHWC for TF operations
    x = tf.keras.layers.Permute((2, 3, 1))(inputs)  # [B, 224, 224, 3]

    # Efficient backbone using depthwise separable convolutions
    # Stage 1: 224 -> 112
    x = tf.keras.layers.Conv2D(32, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # Stage 2: 112 -> 56
    x = tf.keras.layers.SeparableConv2D(64, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # Stage 3: 56 -> 28
    x = tf.keras.layers.SeparableConv2D(128, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # Stage 4: 28 -> 14
    x = tf.keras.layers.SeparableConv2D(256, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # Stage 5: 14 -> 7 (extra reduction for efficiency)
    x = tf.keras.layers.SeparableConv2D(512, 3, strides=2, padding='same', use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation('gelu')(x)

    # From [B, 7, 7, 512] to [B, 49, 512]
    x = tf.keras.layers.Reshape((7*7, 512))(x)

    # Project each patch token: 512 -> 2048
    x = tf.keras.layers.Dense(2048, use_bias=False)(x)  # [B, 49, 2048]

    # Upsample from 49 tokens to 256 tokens using learned interpolation
    # First, tile and then use 1D conv to blend
    x = tf.keras.layers.UpSampling1D(size=6)(x)  # [B, 294, 2048]
    x = tf.keras.layers.Conv1D(2048, 3, padding='same', use_bias=False)(x)  # Blend
    x = tf.keras.layers.Cropping1D(cropping=(19, 19))(x)  # [B, 256, 2048]

    outputs = x
    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="vision_encoder")
    return model


def create_language_decoder():
    """
    Language Decoder: Input [batch, 256, 2048] → Output [batch, 4]

    Simulates Gemma decoder outputting classification logits.
    4 classes: Healthy, Anemia, Jaundice, Cyanosis
    """
    print("Creating Language Decoder model...")

    # Input: Image tokens [1, 256, 2048]
    inputs = tf.keras.Input(shape=(256, 2048), name="image_tokens")

    # Simplified transformer-style processing
    x = tf.keras.layers.LayerNormalization()(inputs)

    # Multi-head attention (simplified)
    attention = tf.keras.layers.MultiHeadAttention(
        num_heads=8,
        key_dim=256,
        dropout=0.1
    )(x, x)
    x = tf.keras.layers.Add()([x, attention])
    x = tf.keras.layers.LayerNormalization()(x)

    # Feed-forward
    ff = tf.keras.layers.Dense(4096, activation='gelu')(x)
    ff = tf.keras.layers.Dense(2048)(ff)
    x = tf.keras.layers.Add()([x, ff])

    # Pool and classify
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    x = tf.keras.layers.Dense(512, activation='gelu')(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(4, name="logits")(x)  # 4 classes

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="language_decoder")
    return model


def convert_to_tflite(model, output_path, quantize=False):
    """Convert Keras model to TFLite format."""
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    if quantize:
        # Dynamic range quantization for smaller size
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"  Saved: {output_path} ({size_mb:.2f} MB)")
    return size_mb


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("=" * 60)
    print("NKU Demo TFLite Model Generator")
    print("=" * 60)

    # Create Vision Encoder
    vision_model = create_vision_encoder()
    vision_model.summary()
    vision_path = os.path.join(OUTPUT_DIR, "vision_encoder.tflite")
    vision_size = convert_to_tflite(vision_model, vision_path, quantize=True)

    print()

    # Create Language Decoder
    language_model = create_language_decoder()
    language_model.summary()
    language_path = os.path.join(OUTPUT_DIR, "language_model_quant.tflite")
    language_size = convert_to_tflite(language_model, language_path, quantize=True)

    print()
    print("=" * 60)
    print("Summary:")
    print(f"  Vision Encoder: {vision_size:.2f} MB")
    print(f"  Language Decoder: {language_size:.2f} MB")
    print(f"  Total: {vision_size + language_size:.2f} MB")
    print()
    print("Models saved to:", OUTPUT_DIR)
    print("=" * 60)

    # Verify models work
    print("\nVerifying models...")

    # Test vision encoder
    vision_interpreter = tf.lite.Interpreter(model_path=vision_path)
    vision_interpreter.allocate_tensors()
    vision_input = vision_interpreter.get_input_details()
    vision_output = vision_interpreter.get_output_details()
    print(f"Vision Input: {vision_input[0]['shape']} {vision_input[0]['dtype']}")
    print(f"Vision Output: {vision_output[0]['shape']} {vision_output[0]['dtype']}")

    # Test language decoder
    lang_interpreter = tf.lite.Interpreter(model_path=language_path)
    lang_interpreter.allocate_tensors()
    lang_input = lang_interpreter.get_input_details()
    lang_output = lang_interpreter.get_output_details()
    print(f"Language Input: {lang_input[0]['shape']} {lang_input[0]['dtype']}")
    print(f"Language Output: {lang_output[0]['shape']} {lang_output[0]['dtype']}")

    print("\n✅ All models verified successfully!")


if __name__ == "__main__":
    main()
