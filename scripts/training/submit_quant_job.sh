#!/bin/bash
# submit_quant_job.sh - Submits the Nku INT4 Quantization job to Vertex AI (T4 GPU)

PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-training:latest"
JOB_NAME="nku-quant-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

# Load secrets (HF_TOKEN)
if [ -f .env ]; then
    export $(cat .env | xargs)
fi

echo "🚀 Building image and submitting Nku Quantization Job: ${JOB_NAME}"

# 1. Build and Push (Updates image with new quantize_model.py)
# $GCLOUD builds submit scripts/training/ --tag $IMAGE_URI

# 2. Create Custom Job with T4 GPU
$GCLOUD ai custom-jobs create \
    --region=${REGION} \
    --display-name=${JOB_NAME} \
    --config=- <<EOF
workerPoolSpecs:
  machineSpec:
    machineType: n1-standard-16
  replicaCount: 1
  containerSpec:
    imageUri: ${IMAGE_URI}
    command: ["python3", "quantize_model.py"]
    args:
      - "--input_path=gs://nku-impact-outputs-1335/translategemma-4b-squeezed"
      - "--output_path=gs://nku-impact-outputs-1335/translategemma-4b-int4"
    env:
      - name: HF_TOKEN
        value: "${HF_TOKEN}"
EOF

echo "✅ Quantization Job submitted. Monitor progress in Vertex AI Console."
