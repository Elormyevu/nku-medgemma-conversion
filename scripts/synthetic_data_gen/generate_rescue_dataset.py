import os
import json
import random
from PIL import Image
from pathology_prompter import PathologyPrompter

OUTPUT_DIR = "rescue_data"
IMG_DIR = os.path.join(OUTPUT_DIR, "images")
os.makedirs(IMG_DIR, exist_ok=True)

prompter = PathologyPrompter()
data_entries = []

print("Generating rescue dataset...")
categories = ["anemia", "jaundice"]

for i in range(50):
    condition = random.choice(categories)
    severity = random.randint(0, 3)
    
    if condition == "anemia":
        prompt = prompter.generate_anemia_prompt(severity)
    else:
        prompt = prompter.generate_jaundice_prompt(severity)
        
    # Generate dummy image
    # Random color based on severity (just for visual difference)
    # 224x224 is standard for PaliGemma
    color = (random.randint(50, 255), random.randint(50, 255), random.randint(50, 255))
    img = Image.new('RGB', (224, 224), color=color)
    img_filename = f"{condition}_{severity}_{i}.jpg"
    img_path = os.path.join(IMG_DIR, img_filename)
    img.save(img_path)
    
    # Create JSONL entry
    # We use a structure: {"image": relative_path, "text": prompt}
    # This assumes the training script or processor handles this format.
    entry = {
        "image": os.path.join("images", img_filename),
        "text": prompt
    }
    data_entries.append(entry)

with open(os.path.join(OUTPUT_DIR, "train.jsonl"), "w") as f:
    for entry in data_entries:
        f.write(json.dumps(entry) + "\n")

print(f"Generated {len(data_entries)} samples in {OUTPUT_DIR}")
