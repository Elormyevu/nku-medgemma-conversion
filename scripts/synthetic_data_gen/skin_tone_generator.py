import os
import torch
from diffusers import StableDiffusionPipeline
from src.config import GENERATED_DATA_DIR, FITZPATRICK_SCALES, SYNTH_IMAGE_COUNT

def generate_skin_tone_data():
    """Generates synthetic medical images across Fitzpatrick skin scales."""
    device = "cuda" if torch.cuda.is_available() else "cpu"
    # Using a reliable biomed/realistic checkpoint if available, else standard SD
    model_id = "runwayml/stable-diffusion-v1-5" 
    
    print(f"Loading model {model_id} on {device}...")
    pipe = StableDiffusionPipeline.from_pretrained(model_id, torch_dtype=torch.float16 if device == "cuda" else torch.float32)
    pipe = pipe.to(device)

    # Prompt templates emphasizing the medical condition and skin tone
    prompts = {
        "anemia_conjunctiva": "close up medical photo of a human eye conjunctiva, showing {tone} skin tone, {pallor} pallor, high resolution, clinical lighting",
        "anemia_palm": "close up clinical photo of a human palm hand, {tone} skin tone, {pallor} pallor, medical diagnosis quality"
    }
    
    # Mapping scales to descriptive words for the prompt
    scale_desc = {
        1: "very fair",
        2: "fair",
        3: "medium",
        4: "olive",
        5: "brown",
        6: "dark brown/black"
    }

    output_dir = GENERATED_DATA_DIR / "synthetic_skin"
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Starting generation of {SYNTH_IMAGE_COUNT} images...")
    
    # Simple loop for demonstration - in production would be more extensive
    for scale in FITZPATRICK_SCALES:
        desc = scale_desc.get(scale, "medium")
        for condition_name, prompt_template in prompts.items():
            for severity in ["no", "severe"]:
                prompt = prompt_template.format(tone=desc, pallor=severity)
                
                # Generate a small batch for prototype
                images = pipe(prompt, num_inference_steps=30).images
                
                for i, img in enumerate(images):
                    fname = f"{condition_name}_fitz{scale}_{severity}_{i}.png"
                    img.save(output_dir / fname)
                    print(f"Saved {fname}")

if __name__ == "__main__":
    generate_skin_tone_data()
