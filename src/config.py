import os
from pathlib import Path

# --- Project Paths ---
PROJECT_ROOT = Path(__file__).parent.parent
DATA_DIR = PROJECT_ROOT / "rescue_data"
MODELS_DIR = PROJECT_ROOT / "models"
GENERATED_DATA_DIR = PROJECT_ROOT / "generated_data"

# --- Edge Constraints ---
MAX_RAM_USAGE_MB = 1800  # < 2GB target
THERMAL_THROTTLE_TEMP_C = 42.0  # Start cooling down
CRITICAL_BATTERY_LEVEL = 0.15   # 15%

# --- Model Parameters ---
# MedGemma (PaliGemma)
PALIGEMMA_VARIANT = "paligemma-3b-mix-224"
QUANTIZATION_TYPE = "int4"  # Aggressive quantization for mobile
INPUT_RES = (224, 224)

# TranslateGemma
TRANSLATION_MODEL_ID = "google/gemma-2b-it"
TARGET_LANGUAGES = ["twi", "yor", "hau", "gaa", "ewe", "swa"]

# --- Synthetic Data ---
SYNTH_IMAGE_COUNT = 1000
FITZPATRICK_SCALES = [1, 2, 3, 4, 5, 6]

# --- Feature Flags ---
ENABLE_MOCK_HARDWARE = True  # For testing without camera
