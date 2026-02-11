import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from peft import PeftModel
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

def merge_and_quantize(base_model_id, lora_path, output_path):
    """
    1. Loads the base model.
    2. Merges LoRA adapters.
    3. Quantizes result to INT4.
    """
    token = os.environ.get("HF_TOKEN")
    
    # 1. Load LoRA path locally if GCS
    local_lora = lora_path
    if lora_path.startswith("gs://"):
        local_lora = "./medgemma_lora_local"
        download_from_gcs(lora_path, local_lora)
    
    logger.info(f"🔌 Loading Base Model: {base_model_id}")
    # Merge on CPU to avoid OOM if model is large, or use enough GPU
    base_model = AutoModelForCausalLM.from_pretrained(
        base_model_id,
        torch_dtype=torch.bfloat16,
        device_map="cpu", 
        token=token
    )
    
    logger.info(f"🔗 Merging LoRA Adapters from: {local_lora}")
    model = PeftModel.from_pretrained(base_model, local_lora)
    merged_model = model.merge_and_unload()
    
    logger.info("💾 Saving Merged FP16 Model...")
    fp16_staging = "./medgemma_merged_fp16"
    os.makedirs(fp16_staging, exist_ok=True)
    merged_model.save_pretrained(fp16_staging)
    
    tokenizer = AutoTokenizer.from_pretrained(base_model_id, token=token)
    tokenizer.save_pretrained(fp16_staging)
    
    # Upload FP16 for TFLite conversion pipeline
    upload_directory_to_gcs(fp16_staging, output_path + "-merged-fp16")
    
    logger.info("✅ Merging Complete. FP16 Weights Uploaded.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base_model", type=str, default="google/medgemma-1.5-4b-it")
    parser.add_argument("--lora_path", type=str, required=True)
    parser.add_argument("--output_path", type=str, required=True)
    args = parser.parse_args()

    merge_and_quantize(args.base_model, args.lora_path, args.output_path)
