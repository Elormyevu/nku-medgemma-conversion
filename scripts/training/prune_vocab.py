import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import logging
import os
import sys
import argparse
import time
from google.cloud import storage

# --- SENTINEL MISSION CONTROL ---
# Rule 3: Nuclear Pathing - Vertex AI environments can be finicky with paths
sys.path.append("/app")
sys.path.append("/app/src")

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - [SENTINEL] - %(message)s'
)
logger = logging.getLogger("Sentinel-Squeeze")

def print_sentinel_heartbeat():
    """Rule 1 (Copper Debug): Log everything about the environment."""
    logger.info("💓 SENTINEL HEARTBEAT START")
    logger.info(f"Python Version: {sys.version}")
    logger.info(f"Working Directory: {os.getcwd()}")
    logger.info(f"Cuda Available: {torch.cuda.is_available()}")
    if torch.cuda.is_available():
        logger.info(f"GPU: {torch.cuda.get_device_name(0)}")
        logger.info(f"GPU Memory: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.2f} GB")
    
    # Check for sensitive env vars (masked)
    hf_token = os.environ.get("HF_TOKEN")
    logger.info(f"HF_TOKEN present: {bool(hf_token)}")
    logger.info("💓 SENTINEL HEARTBEAT END")

def check_sentinel_resume(gcs_path):
    """Rule 2: Sentinel Stability - Check for existing outputs to avoid redundant cost."""
    if not gcs_path.startswith("gs://"):
        return False
    
    try:
        bucket_name = gcs_path.split("/")[2]
        prefix = "/".join(gcs_path.split("/")[3:])
        
        storage_client = storage.Client()
        bucket = storage_client.bucket(bucket_name)
        # Check if config.json exists (indicator of success)
        blob = bucket.blob(f"{prefix}/config.json")
        if blob.exists():
            logger.info(f"🛡️  Sentinel found existing output at {gcs_path}. SKIPPING job to save quota.")
            return True
    except Exception as e:
        logger.warning(f"⚠️ Sentinel Resume check failed: {e}. Proceeding with fresh run.")
    return False

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

# PAN-AFRICAN CLINICAL SEED LIST (TranslateGemma Integration)
DIALECT_AFRICAN_FULL = [
    # --- WEST AFRICA ---
    "Mmepe kɔkɔɔ no.", "Wo ho te sɛn?", "Yɛfrɛ wo sɛn?", "Mepawokyɛw, boa me.", # Twi (Akan)
    "Ele agbe.", "Nuteƒe le.", "Miawoe zɔ.", "Ewe kɔ.", # Ewe
    "Minɛ o.", "Te tɛŋ tɛŋ.", "Nuumo.", "Ga samai.", # Ga
    "Sannu da zuwa.", "Ina kwana?", "Lafiya lau.", "Na gode.", # Hausa
    "Bawo ni?", "Se daadaa ni?", "E ku otutu.", "O dabo.", # Yoruba
    "Kedu ka i mere?", "O nwere ike?", "Biko.", "Daalu.", # Igbo
    "Jamm nga am?", "Naka nga def?", "Mangi fi rekk.", "Jerejef.", # Wolof
    # --- EAST AFRICA ---
    "Habari yako?", "Ninaenda sokoni.", "Nataka maji.", "Asante sana.", # Swahili
    "Indet neh?", "Selam new?", "Ameseginalehu.", # Amharic
    "Akkam?", "Fayyaa?", "Galatoomi.", # Oromo
    "Is ka warran?", "Ma nabad baa?", "Waad mahadsan tahay.", # Somali
    "Kemey 'lekhu?", "Dehan do?", "Yekenyeley.", # Tigrinya
    # --- SOUTHERN AFRICA ---
    "Sawubona.", "Unjani?", "Ngiyabonga.", "Hamba kahle.", # Zulu
    "Molo.", "Unjani?", "Enkosi.", # Xhosa
    "Mhoro.", "Ndatenda.", "Urare zvakanaka.", # Shona
    "Goeie more.", "Hoe gaan dit?", "Dankie.", # Afrikaans
    # --- CENTRAL AFRICA ---
    "Mbote.", "Sango nini?", "Matondo.", # Lingala
    "Amakuru?", "Ni meza.", "Murakoze.", # Kinyarwanda
    # --- COLONIAL / LINK LANGUAGES ---
    "Bonjour, comment allez-vous?", "J'ai mal à la tête.", "Merci beaucoup.", # French
    "Olá, como vai?", "Preciso de ajuda.", "Obrigado.", # Portuguese
    "Hello, how are you?", "I need a doctor.", "Thank you.", # English
    # --- MEDICAL VOCABULARY ---
    "Malaria", "Fever", "Pain", "Headache", "Stomach", "Chest", "Heart", "Blood",
    "Pressure", "Diabetes", "Hypertension", "Cough", "Flu", "Virus", "Bacteria",
    "Infection", "Wound", "Injury", "Emergency", "Doctor", "Nurse", "Hospital",
    "Clinic", "Medicine", "Tablet", "Injection", "Vaccine", "Surgery", "Pregnant",
    "Baby", "Child", "Man", "Woman", "Elderly", "Dizziness", "Vomiting", "Diarrhea",
    "Anemia", "Asthma", "Cancer", "HIV", "AIDS", "Tuberculosis", "Cholera",
    "Typhoid", "Meningitis", "Pneumonia", "Symptom", "Treatment", "Diagnosis",
    "Prescription", "Dosage", "Referral", "Ambulance", "Ward", "Laboratory", "Test",
    # --- MATH & LOGIC ---
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "+", "-", "*", "/", "=", "%", "$", "#", "@"
]

def prune_translategemma(model_id, save_path):
    """Surgically prunes the TranslateGemma 4B vocabulary."""
    print_sentinel_heartbeat()
    
    if check_sentinel_resume(save_path):
        return

    token = os.environ.get("HF_TOKEN")
    if not token:
        logger.error("❌ CRITICAL: HF_TOKEN is missing. Aborting.")
        sys.exit(1)

    # Rule 5: Environment Guards - Use the chmod'ed directory from Dockerfile
    local_staging = "/app/checkpoints/pruned_model_staging"
    os.makedirs(local_staging, exist_ok=True)

    try:
        logger.info(f"🔍 Loading base model/tokenizer: {model_id}")
        start_time = time.time()
        
        tokenizer = AutoTokenizer.from_pretrained(model_id, token=token)
        # Use low_cpu_mem_usage=True and load in bfloat16 to stay under 60GB
        model = AutoModelForCausalLM.from_pretrained(
            model_id, 
            torch_dtype=torch.bfloat16, 
            token=token,
            low_cpu_mem_usage=True
        )
        logger.info(f"⏱️ Model loaded in {time.time() - start_time:.2f}s")

        # 1. Identify Target Tokens
        kept_token_ids = set(tokenizer.all_special_ids)
        for i in range(128):
            char = chr(i)
            token_ids = tokenizer.encode(char, add_special_tokens=False)
            kept_token_ids.update(token_ids)

        logger.info("✂️ Harvesting tokens from Pan-African seed list...")
        for phrase in DIALECT_AFRICAN_FULL:
            token_ids = tokenizer.encode(phrase, add_special_tokens=False)
            kept_token_ids.update(token_ids)

        logger.info("📚 Adding top 30,000 base tokens for stability...")
        kept_token_ids.update(range(min(30000, tokenizer.vocab_size)))

        sorted_kept_ids = sorted(list(kept_token_ids))
        new_vocab_size = len(sorted_kept_ids)
        logger.info(f"📉 Squeezing vocabulary: {tokenizer.vocab_size} -> {new_vocab_size}")

        # 2. Matrix Surgery
        original_embeddings = model.get_input_embeddings().weight.data
        original_lm_head = model.get_output_embeddings().weight.data
        embedding_dim = original_embeddings.shape[1]
        
        new_embeddings = torch.zeros((new_vocab_size, embedding_dim), dtype=torch.bfloat16)
        new_lm_head = torch.zeros((new_vocab_size, embedding_dim), dtype=torch.bfloat16)

        for new_id, old_id in enumerate(sorted_kept_ids):
            new_embeddings[new_id] = original_embeddings[old_id]
            new_lm_head[new_id] = original_lm_head[old_id]

        model.get_input_embeddings().weight.data = new_embeddings
        model.get_output_embeddings().weight.data = new_lm_head
        model.config.vocab_size = new_vocab_size
        
        logger.info(f"✅ Surgery complete. NEW Vocab Size: {new_vocab_size}")
        
        # Rule 2 (Pre-Flight): Run 1 forward pass to ensure shape integrity
        logger.info("🧪 Running Sentinel Sanity Check (Forward Pass)...")
        model.eval()
        # Move dummy input to same device as model weights
        device = next(model.parameters()).device
        dummy_input = torch.zeros((1, 10), dtype=torch.long).to(device)
        with torch.no_grad():
            outputs = model(dummy_input)
        logger.info("✅ Sanity Check Passed (logits generated).")
        
        # 3. Save Pruned Model
        logger.info(f"💾 Saving Squeezed model to {local_staging}")
        model.save_pretrained(local_staging)
        tokenizer.save_pretrained(local_staging)
        
        # 4. Persistence to GCS
        upload_directory_to_gcs(local_staging, save_path)
        logger.info("🛡️ Sentinel Job SUCCESS.")

    except Exception as e:
        logger.error(f"💥 SENTINEL CRASH: {e}", exc_info=True)
        sys.exit(1)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_id", type=str, default="google/translategemma-4b-it")
    parser.add_argument("--save_path", type=str, required=True)
    args = parser.parse_args()

    prune_translategemma(args.model_id, args.save_path)
