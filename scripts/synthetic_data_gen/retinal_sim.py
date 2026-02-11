import cv2
import numpy as np
import os
from pathlib import Path
from src.config import GENERATED_DATA_DIR

def apply_smartphone_artifacts(image_path: Path, output_path: Path):
    """
    Augments high-quality fundus images with smartphone-like artifacts:
    - Defocus blur (Lens focus issues)
    - ISO Noise (Low light sensor)
    - Glare (Flash reflection)
    """
    img = cv2.imread(str(image_path))
    if img is None:
        return

    # 1. Defocus Blur
    ksize = np.random.choice([3, 5, 7])
    img = cv2.GaussianBlur(img, (ksize, ksize), 0)

    # 2. ISO Noise (Gaussian)
    row, col, ch = img.shape
    mean = 0
    var = 0.01 * 255
    sigma = var**0.5
    gauss = np.random.normal(mean, sigma, (row, col, ch))
    gauss = gauss.reshape(row, col, ch)
    noisy = img + gauss
    img = np.clip(noisy, 0, 255).astype(np.uint8)

    # 3. Glare/Reflection (Simulated)
    # Simple circular bright spot
    center_x = np.random.randint(0, col)
    center_y = np.random.randint(0, row)
    radius = np.random.randint(20, 100)
    
    overlay = img.copy()
    cv2.circle(overlay, (center_x, center_y), radius, (255, 255, 255), -1)
    alpha = 0.4  # Transparency
    img = cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0)

    # Save
    output_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(output_path), img)
    print(f"Processed: {output_path.name}")

def run_retinal_sim():
    # In a real run, this would iterate over the source dataset
    # For now, we stub the source directory or create a dummy file
    print("Retinal simulation tool loaded.")
    # Logic to iterate DATA_DIR / 'fundus' would go here

if __name__ == "__main__":
    run_retinal_sim()
