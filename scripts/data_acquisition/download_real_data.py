from datasets import load_dataset
import os
import json
from PIL import Image

# Configuration
DATASET_ID = "electricsheepafrica/Africa-skin-cancer-images-EHR"
OUTPUT_DIR = "rescue_data/real/african_skin"
JSONL_PATH = "rescue_data/real/skin_cancer.jsonl"
SAMPLE_SIZE = 50

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

print(f"⬇️ Downloading sample from {DATASET_ID}...")
try:
    # Load streaming to avoid downloading full dataset if huge
    dataset = load_dataset(DATASET_ID, split="train", streaming=True)
    
    saved_count = 0
    jsonl_data = []

    for i, example in enumerate(dataset):
        if saved_count >= SAMPLE_SIZE:
            break
            
        # Get image and metadata
        image = example.get('image')
        # Some datasets use 'img' or other keys, checking...
        if not image and 'img' in example: image = example['img']
        
        if image:
            # Save Image
            filename = f"real_skin_{i:04d}.jpg"
            filepath = os.path.join(OUTPUT_DIR, filename)
            
            # Convert to RGB if needed
            if image.mode != "RGB":
                image = image.convert("RGB")
            
            image.save(filepath)
            
            # Create Description/Prompt
            # Extract useful metadata for the prompt
            diagnosis = example.get('diagnosis', 'skin lesion')
            age = example.get('age', 'unknown age')
            sex = example.get('sex', 'unknown sex')
            history = example.get('history', '')
            
            # Synthesis of a clinical prompt
            text_prompt = f"Dermatoscopic image of {diagnosis} in a patient ({age}, {sex}). {history} <loc_en>"
            
            # Append to JSONL list
            # We use absolute path for now or relative to execution? 
            # cloud_train.py expects relative prompt input format usually: 
            # {"image": path, "text": prompt}
            # Note: The training script seems to handle /gcs prefixes. 
            # We will provide relative path from the bucket root in the final upload, 
            # but for local generation we keep it relative.
            
            entry = {
                "image": f"real/african_skin/{filename}", 
                "text": text_prompt
            }
            jsonl_data.append(entry)
            
            saved_count += 1
            print(f"✅ Saved {filename}")

    # Write JSONL
    # We write it locally; the user will need to upload this folder
    # Or we can treat this as the artifact to upload
    with open(JSONL_PATH, 'w') as f:
        for entry in jsonl_data:
            f.write(json.dumps(entry) + "\n")
            
    print(f"🎉 Successfully saved {saved_count} real-world samples to {OUTPUT_DIR}")
    print(f"📝 Metadata saved to {JSONL_PATH}")

except Exception as e:
    print(f"❌ Error downloading dataset: {e}")
