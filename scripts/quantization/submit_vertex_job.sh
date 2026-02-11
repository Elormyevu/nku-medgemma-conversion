#!/bin/bash
set -e

# Configuration
PROJECT_ID=$(gcloud config get-value project)
BUCKET_NAME="nku-impact-outputs-1335"
REGION="us-central1"
JOB_NAME="nku-tflite-conversion-$(date +%s)"
WORKER_SCRIPT="cloud_tflite_worker.py"

echo "🚀 Launching Vertex AI Job: $JOB_NAME"
echo "   Project: $PROJECT_ID"
echo "   Bucket:  $BUCKET_NAME"

# We use a standard PyTorch image and install ai-edge-torch via a startup command
gcloud ai custom-jobs create \
  --region=$REGION \
  --display-name=$JOB_NAME \
  --worker-pool-spec=machine-type=n1-highmem-8,replica-count=1,container-image-uri=us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.2-1.py310:latest \
  --args="-c","pip install ai-edge-torch transformers accelerate sentencepiece google-cloud-storage && python3 $WORKER_SCRIPT" \
  --network="projects/$PROJECT_ID/global/networks/default" || echo "Network config failed, trying without network peering..." && \
gcloud ai custom-jobs create \
  --region=$REGION \
  --display-name=$JOB_NAME \
  --worker-pool-spec=machine-type=n1-highmem-16,replica-count=1,container-image-uri=us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.2-1.py310:latest \
  --args="-c","pip install --upgrade pip && pip install ai-edge-torch transformers accelerate sentencepiece google-cloud-storage && wget https://raw.githubusercontent.com/huggingface/transformers/main/src/transformers/models/gemma/configuration_gemma.py -O configuration_gemma.py && python3 $WORKER_SCRIPT"

echo "✅ Job Submitted! Monitor at: https://console.cloud.google.com/vertex-ai/training/custom-jobs?project=$PROJECT_ID"
