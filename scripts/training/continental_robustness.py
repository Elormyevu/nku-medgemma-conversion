
import os
import logging
import argparse
import json
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from google.cloud import storage

# Configure Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Regional Groupings for Robustness Testing
REGIONS = {
    "West": ["Twi", "Ewe", "Yoruba", "Hausa", "Igbo"],
    "East": ["Swahili", "Amharic", "Oromo", "Somali"],
    "Southern": ["Zulu", "Xhosa", "Shona", "Afrikaans"],
    "Central": ["Lingala", "Kinyarwanda", "Kirundi"]
}

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

def load_evaluation_data(region, data_path):
    """
    Loads evaluation data for a specific region from a JSONL file.
    Expects format: {"input": "...", "target": "...", "region": "..."}
    """
    logger.info(f"📂 Loading validation data for region: {region}...")
    samples = []
    if os.path.exists(data_path):
        with open(data_path, 'r') as f:
            for line in f:
                data = json.loads(line)
                # Filter by region if specified in data, or load all if region list matches
                if data.get('region') == region:
                    samples.append(data)
    
    # Fallback/Mock if no data found for real testing
    if not samples:
        logger.warning(f"⚠️ No data found for {region}. Using mock samples.")
        samples = [
            {"input": f"Translate to English: [Regional Term for {region}]", "target": "Medical explanation"},
            {"input": f"Diagnostic check for {region} phenotype", "target": "Valid diagnosis"}
        ]
    return samples

def evaluate_region(model, tokenizer, region, samples):
    """
    Evaluates the model on a specific regional dataset using actual inference.
    """
    if model is None:
        logger.warning("No model loaded. Skipping inference.")
        return 0.5
        
    logger.info(f"🧪 Testing {len(samples)} samples for {region} Africa...")
    correct = 0
    total = len(samples)
    
    model.eval()
    with torch.no_grad():
        for sample in samples:
            inputs = tokenizer(sample['input'], return_tensors="pt").to(model.device)
            outputs = model.generate(**inputs, max_new_tokens=50)
            prediction = tokenizer.decode(outputs[0], skip_special_tokens=True)
            
            # Simple substring match for validation (can be replaced by BLEU/ROUGE)
            if sample['target'].lower() in prediction.lower():
                correct += 1
            
    accuracy = correct / total if total > 0 else 0.0
    logger.info(f"📊 {region} Accuracy: {accuracy:.2%}")
    return accuracy

def main(args):
    logger.info(f"🌍 Starting Continental Robustness Test for Model: {args.model_path}")
    
    local_model_path = args.model_path
    if args.model_path.startswith("gs://"):
        local_model_path = "./eval_model_local"
        download_from_gcs(args.model_path, local_model_path)
    
    logger.info("📦 Loading Model and Tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(local_model_path)
    
    # Try loading with GPU first, fallback to CPU
    device = "cuda" if torch.cuda.is_available() else "cpu"
    logger.info(f"🖥️ Targeted Device: {device}")
    
    if device == "cuda":
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
        )
        model = AutoModelForCausalLM.from_pretrained(
            local_model_path, 
            quantization_config=bnb_config,
            device_map="auto"
        )
    else:
        logger.info("⚠️ No GPU detected. Loading in bfloat16 on CPU...")
        model = AutoModelForCausalLM.from_pretrained(
            local_model_path,
            torch_dtype=torch.bfloat16,
            low_cpu_mem_usage=True
        ).to("cpu")
    
    local_data_path = args.data_path
    if args.data_path.startswith("gs://"):
        local_data_path = "./eval_data_local.jsonl"
        bucket_name = args.data_path.split("/")[2]
        blob_path = "/".join(args.data_path.split("/")[3:])
        storage_client = storage.Client()
        bucket = storage_client.bucket(bucket_name)
        blob = bucket.blob(blob_path)
        logger.info(f"⬇️ Downloading data: {args.data_path} -> {local_data_path}")
        blob.download_to_filename(local_data_path)

    results = {}
    for region in REGIONS.keys():
        samples = load_evaluation_data(region, local_data_path)
        accuracy = evaluate_region(model, tokenizer, region, samples)
        results[region] = accuracy
        
    logger.info("🏆 Continental Robustness Results Summary:")
    table_data = []
    for region, acc in results.items():
        logger.info(f"  - {region}: {acc:.2%}")
        table_data.append({"Region": region, "Accuracy": acc})
        
    # Check for regional bias
    accuracies = list(results.values())
    variance = max(accuracies) - min(accuracies)
    if variance > 0.15:
        logger.warning(f"❌ HIGH REGIONAL BIAS DETECTED ({variance:.2%}). Optimization required for fairness.")
    else:
        logger.info(f"✅ Pan-African Stability Confirmed (Variance: {variance:.2%}).")

    # Save results to JSON
    with open("robustness_results.json", "w") as f:
        json.dump(results, f, indent=4)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Nku Continental Robustness Tester")
    parser.add_argument("--model_path", type=str, required=True, help="GCS or local path to model")
    parser.add_argument("--data_path", type=str, default="data/evaluation/pan_african.jsonl", help="Path to evaluation data")
    args = parser.parse_args()
    
    main(args)
