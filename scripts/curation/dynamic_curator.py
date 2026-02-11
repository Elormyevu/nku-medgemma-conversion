import os
import json
import torch
import numpy as np
from tqdm import tqdm
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity
import logging
import argparse
from google.cloud import storage
import tempfile

# Setup Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class DynamicDataCurator:
    def __init__(self, model_name="BAAI/bge-small-en-v1.5"):
        logger.info(f"Initializing DDC with model: {model_name}")
        self.model = SentenceTransformer(model_name)
        if torch.cuda.is_available():
            self.model = self.model.to("cuda")
        
        self.storage_client = storage.Client()

    def _parse_gcs_path(self, path):
        if not path.startswith("gs://"):
            return None, path
        parts = path.replace("gs://", "").split("/")
        bucket_name = parts[0]
        blob_name = "/".join(parts[1:])
        return bucket_name, blob_name

    def load_jsonl_from_gcs(self, path):
        bucket_name, blob_name = self._parse_gcs_path(path)
        if not bucket_name:
            with open(path, 'r') as f:
                return [json.loads(line) for line in f]
        
        bucket = self.storage_client.bucket(bucket_name)
        blob = bucket.blob(blob_name)
        content = blob.download_as_text()
        return [json.loads(line) for line in content.splitlines()]

    def get_centroid(self, texts):
        """Calculate the semantic centroid of medical records."""
        logger.info(f"Calculating centroid for {len(texts)} gold-standard records...")
        embeddings = self.model.encode(texts, convert_to_tensor=True, show_progress_bar=True)
        centroid = torch.mean(embeddings, dim=0)
        return centroid.cpu().numpy()

    def curate(self, synthetic_path, gold_path, output_path, threshold=0.85):
        """Filter synthetic data based on similarity to gold standard centroid."""
        
        # Load Gold Standard
        gold_samples = self.load_jsonl_from_gcs(gold_path)
        gold_texts = [s.get('text', '') for s in gold_samples]
        
        centroid = self.get_centroid(gold_texts)
        
        # Process Synthetic Data
        logger.info(f"Curating synthetic data from {synthetic_path}...")
        
        # For large files, we should stream. 
        # For simplicity in this v1, we'll download to a temp file if it's GCS.
        synth_bucket, synth_blob_name = self._parse_gcs_path(synthetic_path)
        
        temp_in = None
        if synth_bucket:
            # Download synthetic data to temp file (simulated streaming if large, but here we download)
            # The original code had a placeholder for temp_in, and then directly streamed from blob.
            # This new logic downloads the entire GCS file to a temporary local file first.
            temp_file_obj = tempfile.NamedTemporaryFile(mode='w+', delete=True, encoding='utf-8')
            bucket = self.storage_client.bucket(synth_bucket)
            blob = bucket.blob(synth_blob_name)
            blob.download_to_filename(temp_file_obj.name)
            temp_file_obj.seek(0) # Reset file pointer to beginning
            iterator = temp_file_obj # Use the temporary file object as the iterator
        else:
            iterator = open(synthetic_path, 'r')

        curated_samples = []
        total_count = 0
        
        for line in tqdm(iterator):
            total_count += 1
            sample = json.loads(line)
            text = sample.get('text', '')
            
            embedding = self.model.encode([text])[0]
            similarity = cosine_similarity(centroid.reshape(1, -1), embedding.reshape(1, -1))[0][0]
            
            if similarity >= threshold:
                curated_samples.append(json.dumps(sample))

        # Write result
        out_bucket, out_blob_name = self._parse_gcs_path(output_path)
        output_content = "\n".join(curated_samples) + "\n"
        
        if out_bucket:
            bucket = self.storage_client.bucket(out_bucket)
            blob = bucket.blob(out_blob_name)
            blob.upload_from_string(output_content)
            logger.info(f"Curated dataset uploaded to: {output_path}")
        else:
            os.makedirs(os.path.dirname(output_path), exist_ok=True)
            with open(output_path, 'w') as f:
                f.write(output_content)
            logger.info(f"Curated dataset saved to: {output_path}")

        pruning_pct = ((total_count - len(curated_samples)) / total_count) * 100
        logger.info(f"Curation complete! Included: {len(curated_samples)} | Pruned: {total_count - len(curated_samples)} ({pruning_pct:.2f}%)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Dynamic Data Curator")
    parser.add_argument("--synthetic_path", required=True, help="Path to synthetic jsonl")
    parser.add_argument("--gold_path", required=True, help="Path to gold standard jsonl")
    parser.add_argument("--output_path", required=True, help="Path to save curated jsonl")
    parser.add_argument("--threshold", type=float, default=0.85, help="Similarity threshold")
    
    args = parser.parse_args()
    
    curator = DynamicDataCurator()
    curator.curate(args.synthetic_path, args.gold_path, args.output_path, args.threshold)
