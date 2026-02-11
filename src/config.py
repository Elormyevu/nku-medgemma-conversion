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
# MedGemma (D-01: updated from stale PaliGemma reference)
MEDGEMMA_VARIANT = "MedGemma-1.5-4B-PT"
MEDGEMMA_GGUF_FILE = "MedGemma-1.5-4B-PT-Q2_K.gguf"
QUANTIZATION_TYPE = "IQ1_M"  # Aggressive quantization for mobile GGUF
INPUT_RES = (224, 224)

# TranslateGemma (D-01: updated from stale gemma-2b-it reference)
TRANSLATION_MODEL_ID = "TranslateGemma-4B"
TRANSLATION_GGUF_FILE = "TranslateGemma-4B-Q2_K.gguf"
TARGET_LANGUAGES = ["twi", "yor", "hau", "gaa", "ewe", "swa"]

# --- Synthetic Data ---
SYNTH_IMAGE_COUNT = 1000
FITZPATRICK_SCALES = [1, 2, 3, 4, 5, 6]

# --- Feature Flags ---
ENABLE_MOCK_HARDWARE = True  # For testing without camera
