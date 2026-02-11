#!/usr/bin/env python3
"""
Automation script to move generated TFLite models and pruned tokenizers
into the Android assets directory for Project Nku.
"""

import os
import shutil
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Paths
BASE_DIR = "/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335"
TFLITE_MODEL = os.path.join(BASE_DIR, "medgemma_int4.tflite")
ASSETS_DIR = os.path.join(BASE_DIR, "mobile/app/src/main/assets")
TOKENIZER_DIR = os.path.join(BASE_DIR, "scripts/pruning/tokenizer_pruned") # Potential location

def deploy_assets():
    logger.info("🚀 Starting Android Asset Deployment...")
    
    # 1. Create assets directory if missing
    os.makedirs(ASSETS_DIR, exist_ok=True)
    
    # 2. Deploy TFLite Model
    if os.path.exists(TFLITE_MODEL):
        target_path = os.path.join(ASSETS_DIR, "medgemma_int4.tflite")
        logger.info(f"📦 Moving TFLite model to {target_path}...")
        shutil.copy2(TFLITE_MODEL, target_path)
        logger.info("✅ TFLite model deployed.")
    else:
        logger.warning(f"⚠️  TFLite model not found at {TFLITE_MODEL}. Skip deployment.")
        
    # 3. Deploy Tokenizer Assets
    # Note: Android typically needs tokenizer.json or processed config
    # I will look for standard tokenizer locations
    tokenizer_sources = [
        os.path.join(BASE_DIR, "tokenizer.json"),
        os.path.join(BASE_DIR, "tokenizer_config.json"),
        os.path.join(BASE_DIR, "scripts/quantization/tokenizer_pruned/tokenizer.json")
    ]
    
    for src in tokenizer_sources:
        if os.path.exists(src):
            target_name = os.path.basename(src)
            target_path = os.path.join(ASSETS_DIR, target_name)
            logger.info(f"🏷️  Deploying tokenizer asset {target_name}...")
            shutil.copy2(src, target_path)
    
    logger.info("🏁 Asset deployment complete.")

if __name__ == "__main__":
    deploy_assets()
