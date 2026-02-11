#!/bin/bash

# Configuration
PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-east1"
DATA_BUCKET="gs://nku-impact-data-1335"
OUTPUT_BUCKET="gs://nku-impact-outputs-1335"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-medgemma-squeeze-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

echo "------------------------------------------------"
echo "Project: $PROJECT_ID | Account: $ACCOUNT"
echo "Region:  $REGION"
echo "Job:     $JOB_NAME"
echo "Image:   $IMAGE_URI"
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

# 1. Build and Push Docker Image
# echo "[1/2] Building and pushing training image..."
# $GCLOUD builds submit scripts/training/ --tag $IMAGE_URI
#
# if [ $? -ne 0 ]; then
#     echo "Docker build failed."
#     exit 1
# fi

# 2. Submit Vertex AI Training Job
echo "[2/2] Submitting Custom Training Job to A100..."

$GCLOUD ai custom-jobs create \
  --region=$REGION \
  --display-name=$JOB_NAME \
  --worker-pool-spec=machine-type=a2-highgpu-1g,accelerator-type=NVIDIA_TESLA_A100,accelerator-count=1,container-image-uri=$IMAGE_URI \
  --args="--output_dir=${OUTPUT_BUCKET}/checkpoints" \
  --args="--lora_save_path=${OUTPUT_BUCKET}/final_lora" \
  --args="--hf_token=${HF_TOKEN}"

echo "------------------------------------------------"
echo "Success! Job submitted."
echo "Monitor status here: https://console.cloud.google.com/vertex-ai/training/jobs?project=$PROJECT_ID"
