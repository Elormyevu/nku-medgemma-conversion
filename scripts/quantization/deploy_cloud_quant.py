import os
from google.cloud import aiplatform

def deploy_quantization_job(
    project_id: str,
    location: str,
    staging_bucket: str,
    model_id: str = "google/gemma-2-2b-it"
):
    aiplatform.init(project=project_id, location=location, staging_bucket=staging_bucket)

    job = aiplatform.CustomPythonPackageTrainingJob(
        display_name="nku-super-squeeze-quantization",
        python_package_gcs_uri=f"{staging_bucket}/train/packages/quant_pkg.tar.gz",
        python_module_name="quantization_module.task",
        container_uri="us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.2-1:py310",
    )

    # Note: Using high-memory instance to ensure safe 4-bit conversion
    job.run(
        args=[f"--model_id={model_id}", "--output_dir=/gcs/nku-impact-models/quantized"],
        replica_count=1,
        machine_type="n1-standard-8",
        accelerator_type="NVIDIA_TESLA_T4",
        accelerator_count=1,
        sync=False
    )
    print("🚀 Vertex AI Super-Squeeze Job Launched.")

if __name__ == "__main__":
    deploy_quantization_job(
        project_id="nku-impact-challenge-1335",
        location="us-central1",
        staging_bucket="gs://nku-impact-challenge-staging"
    )
