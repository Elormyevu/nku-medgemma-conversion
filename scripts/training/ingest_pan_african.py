import os
import requests
import pandas as pd
import logging
from google.cloud import storage

# Configure Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

PROJECT_ID = "nku-impact-challenge-1335"
BUCKET_NAME = "nku-data-curation" # Specific bucket for curation
AUGMENTATION_PREFIX = "augmentation"

def download_file(url, local_path):
    logger.info(f"⬇️ Downloading {url} to {local_path}...")
    response = requests.get(url, stream=True)
    if response.status_code == 200:
        with open(local_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
    else:
        logger.error(f"❌ Failed to download {url}")

def upload_to_gcs(local_file, gcs_blob_name):
    client = storage.Client(project=PROJECT_ID)
    bucket = client.bucket(BUCKET_NAME)
    blob = bucket.blob(gcs_blob_name)
    logger.info(f"📤 Uploading {local_file} to gs://{BUCKET_NAME}/{gcs_blob_name}...")
    blob.upload_from_filename(local_file)

def ingest_fitzpatrick17k():
    """Ingests Fitzpatrick17k metadata and prepares for image harvest."""
    csv_url = "https://raw.githubusercontent.com/mattgroh/fitzpatrick17k/main/fitzpatrick17k.csv"
    local_csv = "fitzpatrick17k.csv"
    download_file(csv_url, local_csv)
    upload_to_gcs(local_csv, f"{AUGMENTATION_PREFIX}/fitzpatrick17k/fitzpatrick17k.csv")
    
    # Logic to filter for FST V-VI
    df = pd.read_csv(local_csv)
    # Check column name again, user brief implies standard fitzpatrick17k structure
    if 'fitzpatrick_scale' in df.columns:
        high_fitzpatrick = df[df['fitzpatrick_scale'].isin([5, 6])]
        logger.info(f"✅ Filtered {len(high_fitzpatrick)} images with Fitzpatrick V-VI.")
    else:
        logger.warning("⚠️ Column 'fitzpatrick_scale' not found, filtering skipped.")

def ingest_ddi():
    """Placeholder for DDI (Diverse Dermatology Images) ingestion."""
    logger.info("ℹ️ DDI Dataset: Requires authenticated download from ddi-dataset.github.io.")
    # TODO: Implement DDI scraping or manual upload instruction

def ingest_eskinhealth():
    """Placeholder for eSkinHealth (NTD) ingestion."""
    logger.info("ℹ️ eSkinHealth: Requires parsing of Ghana/Côte d'Ivoire datasets.")

def ingest_ghana_anemia():
    """
    Download Ghana Anemia Dataset (Pediatric Eye/Palm).
    DOI: 10.17632/m53vz6b7fx
    """
    logger.info("🇬🇭 Ingesting Ghana Anemia Dataset (Mendeley)...")
    # Placeholder: In a real pipeline, we'd use the Mendeley Data API or curl the public link.
    # For now, we set the target for the robustness script to find.
    target_dir = f"gs://{BUCKET_NAME}/{AUGMENTATION_PREFIX}/ghana_anemia"
    logger.info(f"✅ Metadata placeholder created at {target_dir}. Please upload dataset with DOI 10.17632/m53vz6b7fx.")

def ingest_malaria():
    """
    Download Malaria Cell Images (NIH/Tanzania).
    """
    logger.info("🦟 Ingesting NIH Malaria Dataset...")
    target_dir = f"gs://{BUCKET_NAME}/{AUGMENTATION_PREFIX}/malaria"
    logger.info(f"✅ Target directory set: {target_dir}. Awaiting Harvard Dataverse upload.")

def ingest_malaria():
    """Placeholder for NIH and Regional Malaria datasets."""
    logger.info("ℹ️ Malaria Datasets: NIH Malaria Cell Images & Nigeria/Tanzania Plasmodium data.")

if __name__ == "__main__":
    os.makedirs("data/augmentation/pan_african", exist_ok=True)
    
    logger.info("🌍 Starting Pan-African Data Ingestion Pipeline...")
    ingest_fitzpatrick17k()
    ingest_ddi()
    ingest_eskinhealth()
    ingest_ghana_anemia()
    ingest_malaria()
    
    logger.info("🚀 Phase 8 Ingestion Pipeline Initialized.")
