#!/bin/bash

# Configuration
PROJECT_ID="dora-gemini-research"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
DATA_BUCKET="gs://nku-impact-data-1335"
OUTPUT_BUCKET="gs://nku-impact-outputs-1335"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-medgemma-h100-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

echo "------------------------------------------------"
echo "Project: $PROJECT_ID | Account: $ACCOUNT"
echo "Region:  $REGION"
echo "Job:     $JOB_NAME"
echo "Machine: a3-highgpu-1g (NVIDIA H100 80GB)"
echo "------------------------------------------------"

# Load secrets (HF_TOKEN)
if [ -f .env ]; then
    export $(cat .env | xargs)
fi

# Validation
if [ -z "$HF_TOKEN" ]; then
    echo "ERROR: HF_TOKEN is missing. Please add it to your .env file or export it."
    exit 1
fi

# 1. Build and Push Docker Image (Optional if already built)
echo "[1/2] Building and pushing training image..."
$GCLOUD builds submit scripts/training/ --tag $IMAGE_URI

# 2. Submit Vertex AI Training Job (SPOT instance for ~70% cost savings)
echo "[2/2] Submitting Custom Training Job to H100 (SPOT)..."

$GCLOUD ai custom-jobs create \
  --region=$REGION \
  --display-name=$JOB_NAME \
  --config=scripts/training/h100_job_spec.yaml \
  --args="--output_dir=${OUTPUT_BUCKET}/checkpoints" \
  --args="--lora_save_path=${OUTPUT_BUCKET}/final_lora" \
  --args="--hf_token=${HF_TOKEN}" \
  --args="--batch_size=8" \
  --args="--grad_accum=4"

echo "------------------------------------------------"
echo "Success! H100 Job submitted."
echo "Monitor status here: https://console.cloud.google.com/vertex-ai/training/jobs?project=$PROJECT_ID"
