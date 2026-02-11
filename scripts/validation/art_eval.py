import torch
from transformers import AutoProcessor, PaliGemmaForConditionalGeneration
from PIL import Image
import numpy as np
import os
import argparse
from datetime import datetime
import json

# Configuration
MODEL_ID = "google/medgemma-1.5-4b-it" 
# ADAPTER_PATH = "gs://nku-impact-outputs-1335/final_lora"

def inject_noise(image, noise_std=0.05):
    """
    Injects Gaussian noise into a PIL image to simulate sensor grain.
    """
    img_array = np.array(image).astype(np.float32) / 255.0
    noise = np.random.normal(0, noise_std, img_array.shape)
    noisy_img = np.clip(img_array + noise, 0, 1)
    return Image.fromarray((noisy_img * 255).astype(np.uint8))

def run_art_eval(image_path, noise_levels=[0.0, 0.02, 0.05, 0.1]):
    print(f"🚀 Starting Tier 3: ART-Eval (Adversarial Robustness Training Evaluation)")
    print(f"Model: {MODEL_ID} | Image: {os.path.basename(image_path)}")
    
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    model = PaliGemmaForConditionalGeneration.from_pretrained(
        MODEL_ID, 
        torch_dtype=torch.bfloat16, 
        device_map="auto"
    )

    base_image = Image.open(image_path).convert("RGB")
    results = []

    for std in noise_levels:
        print(f"Testing Noise Level: {std}...")
        noisy_image = inject_noise(base_image, noise_std=std)
        
        # Save a sample of the noisy image for verification
        noisy_image_path = f"debug_art_std_{std}.png"
        noisy_image.save(noisy_image_path)

        prompt = "detect medical abnormalities in this image" # Generic prompt for eval
        inputs = processor(text=prompt, images=noisy_image, return_tensors="pt").to(model.device)
        
        with torch.no_grad():
            outputs = model.generate(**inputs, max_new_tokens=100)
        
        response = processor.decode(outputs[0], skip_special_tokens=True)
        print(f"Response (std={std}): {response[:100]}...")

        results.append({
            "noise_std": std,
            "response": response,
            "image_saved": noisy_image_path
        })

    # Save Report
    report_name = f"art_eval_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(report_name, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"✅ ART-Eval Complete. Report: {report_name}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", type=str, required=True, help="Path to test medical image")
    args = parser.parse_args()
    
    if os.path.exists(args.image):
        run_art_eval(args.image)
    else:
        print(f"Error: Image {args.image} not found.")
