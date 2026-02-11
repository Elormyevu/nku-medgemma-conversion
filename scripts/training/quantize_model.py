import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
import os
import logging
import argparse
from google.cloud import storage

# Configure Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def upload_directory_to_gcs(local_path, gcs_path):
    """Uploads a local directory to a GCS path."""
    if not gcs_path.startswith("gs://"):
        logger.info(f"⏭️ Skipping GCS upload: {gcs_path} is not a GCS path.")
        return

    bucket_name = gcs_path.split("/")[2]
    prefix = "/".join(gcs_path.split("/")[3:])
    
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)

    logger.info(f"📤 Uploading {local_path} to gs://{bucket_name}/{prefix}...")
    for root, dirs, files in os.walk(local_path):
        for file in files:
            local_file = os.path.join(root, file)
            relative_path = os.path.relpath(local_file, local_path)
            blob_path = os.path.join(prefix, relative_path)
            blob = bucket.blob(blob_path)
            blob.upload_from_filename(local_file)
    logger.info("✅ GCS Upload Complete.")

def download_from_gcs(gcs_path, local_path):
    """Downloads a GCS directory to a local path."""
    bucket_name = gcs_path.split("/")[2]
    prefix = "/".join(gcs_path.split("/")[3:])
    
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    
    logger.info(f"⬇️ Downloading gs://{bucket_name}/{prefix} to {local_path}...")
    os.makedirs(local_path, exist_ok=True)
    
    blobs = bucket.list_blobs(prefix=prefix)
    for blob in blobs:
        relative_path = os.path.relpath(blob.name, prefix)
        local_file = os.path.join(local_path, relative_path)
        os.makedirs(os.path.dirname(local_file), exist_ok=True)
        blob.download_to_filename(local_file)
    logger.info("✅ GCS Download Complete.")

def quantize_model(input_path, output_path):
    """
    Loads the Pruned (Squeezed) TranslateGemma model and quantizes it to INT4 (NF4).
    """
    logger.info(f"🔌 Loading Squeezed Model from: {input_path}")
    
    # 1. Define Quantization Config (INT4 NF4)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )

    # If it's a GCS path, we download it first. 
    model_source = input_path
    if input_path.startswith("gs://"):
        local_dir = "./squeezed_model_local"
        download_from_gcs(input_path, local_dir)
        model_source = local_dir
    
    token = os.environ.get("HF_TOKEN")
    
    model = AutoModelForCausalLM.from_pretrained(
        model_source,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True,
        token=token
    )
    
    tokenizer = AutoTokenizer.from_pretrained(model_source, token=token)

    logger.info("❄️ Model Quantized to INT4.")

    # 3. Save Quantized Model
    local_staging = "./quantized_model_staging"
    os.makedirs(local_staging, exist_ok=True)
    
    logger.info(f"💾 Saving Quantized model to staging: {local_staging}")
    model.save_pretrained(local_staging)
    tokenizer.save_pretrained(local_staging)
    
    # 4. Upload to GCS
    upload_directory_to_gcs(local_staging, output_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input_path", type=str, required=True, help="Path to the Squeezed (Pruned) model")
    parser.add_argument("--output_path", type=str, required=True, help="GCS path for the final INT4 model")
    args = parser.parse_args()

    if not os.environ.get("HF_TOKEN") and not os.path.exists(args.input_path):
         logger.warning("⚠️ HF_TOKEN not found. Ensure you are authenticated if loading from Hub.")

    quantize_model(args.input_path, args.output_path)
