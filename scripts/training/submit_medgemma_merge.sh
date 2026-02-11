#!/bin/bash
# Submits the MedGemma Merge Job to Vertex AI

PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-medgemma-merge-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

# Load HF_TOKEN from .env (check current and root dir)
if [ -f .env ]; then
    export $(cat .env | grep HF_TOKEN | xargs)
elif [ -f ../../.env ]; then
    export $(cat ../../.env | grep HF_TOKEN | xargs)
fi

$GCLOUD ai custom-jobs create \
    --region=${REGION} \
    --display-name=${JOB_NAME} \
    --config=- <<EOF
workerPoolSpecs:
  machineSpec:
    machineType: n1-highmem-16
  replicaCount: 1
  containerSpec:
    imageUri: ${IMAGE_URI}
    command: ["python3", "/app/merge_and_quantize_medgemma.py"]
    args:
      - "--lora_path=gs://nku-impact-outputs-1335/final_lora"
      - "--output_path=gs://nku-impact-outputs-1335/medgemma-1.5-4b-merged"
    env:
      - name: HF_TOKEN
        value: "${HF_TOKEN}"
EOF
