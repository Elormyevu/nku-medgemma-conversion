#!/bin/bash
# submit_squeeze_job.sh - Submits the Nku Squeeze Pruning job to Vertex AI
# Optimized for high-stability via Sentinel Protocol.

PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-squeeze-sentinel-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud"

# Load secrets (HF_TOKEN) locally for submission context
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

echo "🚀 [SENTINEL] Building image and submitting Nku Squeeze Job: ${JOB_NAME}"

# 1. Build and Push (Ensure we have the latest prune_vocab.py)
$GCLOUD builds submit scripts/training/ --tag $IMAGE_URI --project=$PROJECT_ID --account=$ACCOUNT

# 2. Create Custom Job
# Tuning: Disk increased to 200GB SSD to handle model staging overhead
$GCLOUD ai custom-jobs create \
    --region=${REGION} \
    --project=${PROJECT_ID} \
    --account=${ACCOUNT} \
    --display-name=${JOB_NAME} \
    --config=- <<EOF
workerPoolSpecs:
  machineSpec:
    machineType: n1-standard-16
  replicaCount: 1
  diskSpec:
    bootDiskType: pd-ssd
    bootDiskSizeGb: 200
  containerSpec:
    imageUri: ${IMAGE_URI}
    command: ["python3", "prune_vocab.py"]
    args:
      - "--save_path=gs://nku-impact-outputs-1335/translategemma-4b-squeezed"
    env:
      - name: HF_TOKEN
        value: "${HF_TOKEN}"
EOF

echo "✅ [SENTINEL] Job submitted. Monitor progress in Vertex AI Console."
