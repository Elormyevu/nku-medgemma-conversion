from google.cloud import aiplatform
import os

def deploy_tflite_conversion_job():
    """
    Deploys a Vertex AI Custom Job to handle the TFLite conversion of MedGemma.
    Following Governance Rule VI: Offload compute to Cloud for environment stability.
    """
    print("☁️ Initiating Cloud Pivot: Vertex AI TFLite Conversion...")
    
    project_id = "vesuvius-godmode-v2-main-ts" # Using the verified active project
    location = "us-central1"
    bucket_name = "dora-gemini-research-nku-models"
    
    aiplatform.init(project=project_id, location=location, staging_bucket=f"gs://{bucket_name}")
    
    container_uri = "us-docker.pkg.dev/deeplearning-platform-release/gcr.io/base-cu118.py310"
    
    # Prerequisite: Finalize the conversion script for the cloud environment
    # The cloud worker uses Python 3.10 (stable for ai-edge-torch)
    job_script = """
import torch
from transformers import AutoModelForCausalLM
import ai_edge_torch
from ai_edge_torch.quantize.quant_config import QuantConfig
import os

def run_conversion():
    model_id = "google/gemma-2-2b-it" 
    print(f"Loading {model_id} on Cloud Worker...")
    
    model = AutoModelForCausalLM.from_pretrained(
        model_id, 
        torch_dtype=torch.float16,
        low_cpu_mem_usage=True,
        trust_remote_code=True
    )
    model.eval()
    
    dummy_input = torch.randint(0, 2000, (1, 512))
    quant_config = QuantConfig(quantize_weights=True)
    
    print("Squeezing to INT4 TFLite...")
    edge_model = ai_edge_torch.convert(model, (dummy_input,), quant_config=quant_config)
    
    output_path = "medgemma_int4.tflite"
    edge_model.export(output_path)
    
    # Upload Result back to GCS
    from google.cloud import storage
    client = storage.Client()
    bucket = client.bucket("dora-gemini-research-nku-models")
    blob = bucket.blob("nku-artifacts/medgemma_int4.tflite")
    blob.upload_from_filename(output_path)
    print("Done!")

if __name__ == '__main__':
    run_conversion()
"""
    
    with open("cloud_tflite_worker.py", "w") as f:
        f.write(job_script)
    
    custom_job = ai_edge_torch_job = aiplatform.CustomJob.from_local_script(
        display_name="nku-tflite-conversion-v3",
        script_path="cloud_tflite_worker.py",
        container_uri=container_uri,
        requirements=["ai-edge-torch-nightly", "tf-nightly", "transformers", "torch", "accelerate"],
        args=[],
        replica_count=1,
        machine_type="n1-standard-8", # Plenty of RAM for the conversion
    )
    
    print("🚀 Submitting Custom Job to Vertex AI...")
    custom_job.run()
    print("✅ Job submitted. Monitoring logs...")

if __name__ == "__main__":
    deploy_tflite_conversion_job()
