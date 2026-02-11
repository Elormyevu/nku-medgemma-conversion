#!/bin/bash
# Submits the Continental Robustness Test Job to Vertex AI

PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-robustness-test-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

MODEL_PATH="gs://nku-impact-outputs-1335/translategemma-4b-int4"
DATA_PATH="gs://nku-impact-data-1335/real/pan_african.jsonl"

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
    command: ["python3", "/app/continental_robustness.py"]
    args:
      - "--model_path=${MODEL_PATH}"
      - "--data_path=${DATA_PATH}"
EOF
